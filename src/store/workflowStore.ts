// src/store/workflowStore.ts
import { create } from 'zustand';
import { workflowApi } from '../lib/api';

// ── Types ─────────────────────────────────────────────────────────────

export type StepStatus = 'pending' | 'active' | 'success' | 'failed';
export type WorkflowPhase = 'input' | 'executing' | 'awaiting_merge' | 'complete' | 'error';

export interface LiveStep {
  index: number;          // 0-based index matching stepIndex in SSE events
  tool: string;           // "github" | "jira" | "slack" | "llm"
  action: string;         // "createPR" | "sendMessage" | etc.
  status: StepStatus;
  error?: string;         // populated if status === 'failed'
}

export interface WorkflowScore {
  overall: number;
  taskCompletion: number;
  decisionAccuracy: number;
  executionEfficiency: number;
  contextRelevance: number;
  summary: string;
}

export interface HistoryItem {
  id: string;
  goal: string;
  status: string;
  score: number | null;
  createdAt: string;
  steps: Array<{ tool: string; action: string; status: string }>;
}

export interface FileTreeItem {
  path: string;
  language: string;
  size?: number;
}

interface WorkflowState {
  // Current execution state
  phase: WorkflowPhase;
  goal: string;
  workflowId: string | null;
  liveSteps: LiveStep[];
  logs: Array<{ msg: string; cls: string }>;
  score: WorkflowScore | null;
  overallStatus: 'SUCCESS' | 'FAIL' | null;
  error: string | null;

  // History
  history: HistoryItem[];
  historyLoading: boolean;

  // Code Review (Human in the loop)
  codeReviewActive: boolean;
  pendingCode: string | null;
  pendingFilePath: string | null;
  pendingLanguage: string | null;
  isIterating: boolean;
  isApproving: boolean;
  iterationChat: Array<{
    id: string;
    role: 'user' | 'ai';
    content: string;
    timestamp: Date;
  }>;
  lastIterationAt: number;

  // Multi-file IDE state
  repoFileTree: FileTreeItem[];
  repoOwner: string | null;
  repoName: string | null;
  openFiles: Record<string, string>;      // filePath → fetched content (original)
  modifiedFiles: Record<string, string>;  // filePath → user's edited content
  activeEditorFile: string | null;
  isFetchingFile: boolean;

  // Actions
  setGoal: (goal: string) => void;
  executeWorkflow: (goal: string) => Promise<void>;
  fetchHistory: () => Promise<void>;
  reset: () => void;
  activateCodeReview: (code: string, filePath: string, language: string) => void;
  iterateCode: (workflowId: string, prompt: string, targetFilePath?: string) => Promise<void>;
  approveCode: (workflowId: string, finalCode: string) => Promise<void>;

  // Multi-file actions
  setRepoTree: (owner: string, repo: string, fileTree: FileTreeItem[], targetFile: string) => void;
  openRepoFile: (workflowId: string, filePath: string) => Promise<void>;
  updateFileContent: (filePath: string, content: string) => void;
  setActiveEditorFile: (filePath: string) => void;
  getAdditionalFiles: () => Record<string, string>;
}

// ── Store ─────────────────────────────────────────────────────────────

let activeEventSource: EventSource | null = null;

export const useWorkflowStore = create<WorkflowState>((set, get) => ({
  phase: 'input',
  goal: '',
  workflowId: null,
  liveSteps: [],
  logs: [],
  score: null,
  overallStatus: null,
  error: null,
  history: [],
  historyLoading: false,

  codeReviewActive: false,
  pendingCode: null,
  pendingFilePath: null,
  pendingLanguage: null,
  isIterating: false,
  isApproving: false,
  iterationChat: [],
  lastIterationAt: 0,

  // Multi-file IDE initial state
  repoFileTree: [],
  repoOwner: null,
  repoName: null,
  openFiles: {},
  modifiedFiles: {},
  activeEditorFile: null,
  isFetchingFile: false,

  setGoal: (goal) => set({ goal }),

  reset: () => {
    // Close any active SSE connection before resetting
    if (activeEventSource) {
      activeEventSource.close();
      activeEventSource = null;
    }
    set({
      phase: 'input',
      goal: '',
      workflowId: null,
      liveSteps: [],
      logs: [],
      score: null,
      overallStatus: null,
      error: null,
      codeReviewActive: false,
      pendingCode: null,
      pendingFilePath: null,
      pendingLanguage: null,
      isIterating: false,
      isApproving: false,
      iterationChat: [],
      lastIterationAt: 0,
      repoFileTree: [],
      repoOwner: null,
      repoName: null,
      openFiles: {},
      modifiedFiles: {},
      activeEditorFile: null,
      isFetchingFile: false,
    });
  },

  executeWorkflow: async (goal: string) => {
    if (!goal || !goal.trim()) return;

    // Close any existing SSE connection
    if (activeEventSource) {
      activeEventSource.close();
      activeEventSource = null;
    }

    set({ phase: 'executing', goal, liveSteps: [], logs: [], score: null,
          overallStatus: null, error: null, workflowId: null });

    // Step 1: Trigger the workflow
    let workflowId: string;
    try {
      const res = await workflowApi.execute(goal);
      workflowId = res.workflowId;
      set({ workflowId });
    } catch (err: any) {
      set({ phase: 'error', error: err.message || 'Failed to start workflow' });
      return;
    }

    // Step 2: Open SSE stream
    const connectSSE = (wId: string) => {
      const es = workflowApi.stream(wId);
      activeEventSource = es;

      // ── code_review_ready: human in the loop code review ──────────────
      es.addEventListener('code_review_ready', (e) => {
        const data = JSON.parse(e.data);
        get().activateCodeReview(data.code, data.filePath, data.language);
      });

      // ── repo_tree_ready: file tree + pre-fetched target file ──────────
      es.addEventListener('repo_tree_ready', (e) => {
        const data = JSON.parse(e.data);
        const { owner, repo, fileTree, targetFile } = data;
        get().setRepoTree(owner, repo, fileTree ?? [], targetFile ?? null);
      });

      // ── heartbeat: keep connection alive ──────────────────────────────
      es.addEventListener('heartbeat', () => {
        // just keeping the connection alive — no action needed
      });

      // ── plan_ready: build initial step list from AI plan ─────────────
      es.addEventListener('plan_ready', (e) => {
        const data = JSON.parse(e.data);
        const steps: LiveStep[] = (data.steps || []).map(
          (step: { tool: string; action: string }, idx: number) => ({
            index: idx,
            tool: step.tool,
            action: step.action,
            status: 'pending' as StepStatus,
          })
        );
        set({ liveSteps: steps });
      });

      // ── log: append to live log feed ──────────────────────────────────
      es.addEventListener('log', (e) => {
        const data = JSON.parse(e.data);
        set((state) => ({
          logs: [...state.logs, { msg: data.msg, cls: data.cls || 'ok' }],
        }));
      });

      // ── substep_complete: update individual step status ───────────────
      es.addEventListener('substep_complete', (e) => {
        const data = JSON.parse(e.data);
        const { stepIndex, status, error } = data;
        set((state) => ({
          liveSteps: state.liveSteps.map((step) =>
            step.index === stepIndex
              ? {
                  ...step,
                  status: status === 'SUCCESS' ? 'success' : 'failed',
                  error: error,
                }
              : step
          ),
        }));
      });

      // ── workflow_complete: final verdict + scores ──────────────────────
      es.addEventListener('workflow_complete', (e) => {
        const data = JSON.parse(e.data);

        const score: WorkflowScore | null = data.score != null ? {
          overall: data.score,
          taskCompletion: data.taskCompletion ?? data.score,
          decisionAccuracy: data.decisionAccuracy ?? data.score,
          executionEfficiency: data.executionEfficiency ?? data.score,
          contextRelevance: data.contextRelevance ?? data.score,
          summary: data.summary ?? '',
        } : null;

        // Handle AWAITING_MERGE state from Steps 9/10
        const overallStatus = data.overallStatus as string;
        const phase: WorkflowPhase =
          overallStatus === 'AWAITING_MERGE' ? 'awaiting_merge' : 'complete';

        set({
          phase,
          score,
          overallStatus: overallStatus === 'SUCCESS' ? 'SUCCESS' : 'FAIL',
          codeReviewActive: false,
          pendingCode: null,
        });

        es.close();
        activeEventSource = null;
      });

      // ── SSE error handling ────────────────────────────────────────────
      es.onerror = () => {
        const { codeReviewActive } = get();
        if (codeReviewActive) {
          console.warn('SSE dropped during code review — reconnecting in 3s');
          es.close();
          setTimeout(() => {
            connectSSE(wId);
          }, 3000);
        } else {
          set({ phase: 'error', error: 'Lost connection to workflow stream' });
          es.close();
          activeEventSource = null;
        }
      };
    };

    connectSSE(workflowId);
  },

  fetchHistory: async () => {
    set({ historyLoading: true });
    try {
      const history = await workflowApi.history();
      set({ history, historyLoading: false });
    } catch {
      set({ historyLoading: false });
    }
  },

  activateCodeReview: (code: string, filePath: string, language: string) => {
    set((state) => ({
      codeReviewActive: true,
      pendingCode: code,
      pendingFilePath: filePath,
      pendingLanguage: language,
      iterationChat: [],
      // Pre-populate the main file in openFiles + modifiedFiles so it
      // shows up in the tab bar and tree immediately
      openFiles: { ...state.openFiles, [filePath]: code },
      modifiedFiles: { ...state.modifiedFiles, [filePath]: code },
      activeEditorFile: filePath,
    }));
  },

  // ── Multi-file actions ────────────────────────────────────────────

  setRepoTree: (owner, repo, fileTree, targetFile) => {
    set({
      repoOwner: owner,
      repoName: repo,
      repoFileTree: fileTree,
      activeEditorFile: targetFile,
    });
  },

  openRepoFile: async (workflowId: string, filePath: string) => {
    // If already fetched, just switch to it
    if (get().openFiles[filePath] !== undefined) {
      set({ activeEditorFile: filePath });
      return;
    }

    set({ isFetchingFile: true });
    try {
      const res = await fetch(
        `/api/workflow/${workflowId}/repo-file?filePath=${encodeURIComponent(filePath)}`,
        { credentials: 'include' }
      );
      if (!res.ok) {
        console.error('openRepoFile failed:', res.status);
        set({ isFetchingFile: false });
        return;
      }
      const data = await res.json();
      const content: string = data.content ?? '';
      set((state) => ({
        openFiles: { ...state.openFiles, [filePath]: content },
        modifiedFiles: { ...state.modifiedFiles, [filePath]: content },
        activeEditorFile: filePath,
        isFetchingFile: false,
      }));
    } catch (err) {
      console.error('openRepoFile error:', err);
      set({ isFetchingFile: false });
    }
  },

  updateFileContent: (filePath: string, content: string) => {
    set((state) => ({
      modifiedFiles: { ...state.modifiedFiles, [filePath]: content },
    }));
  },

  setActiveEditorFile: (filePath: string) => {
    set({ activeEditorFile: filePath });
  },

  /**
   * Returns every modified file EXCEPT the primary pendingFilePath.
   * Only includes files where the content actually changed (diff check).
   */
  getAdditionalFiles: () => {
    const { openFiles, modifiedFiles, pendingFilePath } = get();
    const additional: Record<string, string> = {};
    for (const filePath of Object.keys(modifiedFiles)) {
      if (filePath === pendingFilePath) continue; // main file handled separately
      if (modifiedFiles[filePath] !== openFiles[filePath]) {
        additional[filePath] = modifiedFiles[filePath];
      }
    }
    return additional;
  },

  iterateCode: async (workflowId: string, prompt: string, targetFilePath?: string) => {
    const { pendingFilePath } = get();
    const target = targetFilePath ?? pendingFilePath; // default to primary file

    const userMsg = { 
      id: crypto.randomUUID(), 
      role: 'user' as const, 
      content: target !== pendingFilePath 
        ? `[${target?.split('/').pop()}] ${prompt}` 
        : prompt, 
      timestamp: new Date() 
    };
    
    set((state) => ({
      iterationChat: [...state.iterationChat, userMsg],
      isIterating: true,
    }));

    try {
      const response = await fetch(`/api/workflow/${workflowId}/iterate-code`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ 
          prompt,
          targetFilePath: target
        }),
      });

      if (response.ok) {
        const data = await response.json();
        
        const aiMsg = {
          id: crypto.randomUUID(),
          role: 'ai' as const,
          content: data.message,
          timestamp: new Date()
        };

        const updatedCode: string = data.updatedCode;
        set((state) => {
          return {
            pendingCode: target === pendingFilePath ? updatedCode : state.pendingCode,
            modifiedFiles: { ...state.modifiedFiles, [target!]: updatedCode },
            iterationChat: [...state.iterationChat, aiMsg],
            isIterating: false,
            lastIterationAt: Date.now(),
          };
        });
      } else {
        const aiMsg = {
          id: crypto.randomUUID(),
          role: 'ai' as const,
          content: `⚠️ ${response.status === 422 
            ? 'Could not apply change. Try rephrasing.' 
            : 'Request failed. Try again.'}`,
          timestamp: new Date()
        };
        set((state) => ({
          iterationChat: [...state.iterationChat, aiMsg],
          isIterating: false,
        }));
      }
    } catch (err) {
      const aiMsg = {
        id: crypto.randomUUID(),
        role: 'ai' as const,
        content: '⚠️ Network error. Check connection.',
        timestamp: new Date()
      };
      set((state) => ({
        iterationChat: [...state.iterationChat, aiMsg],
        isIterating: false,
      }));
    }
  },

  approveCode: async (workflowId: string, finalCode: string) => {
    set({ isApproving: true });
    try {
      // Collect additional file edits (files other than the primary one)
      const additionalFiles = get().getAdditionalFiles();

      const response = await fetch(`/api/workflow/${workflowId}/approve-code`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          approvedCode: finalCode,
          additionalFiles,   // { "src/utils.py": "updated content", ... }
        }),
      });

      if (!response.ok) {
        const text = await response.text();
        console.error('approveCode failed:', response.status, text);
        alert('Failed to approve code: ' + text);
        set({ isApproving: false });
        return;
      }

      set({
        codeReviewActive: false,
        pendingCode: null,
        isApproving: false,
        openFiles: {},
        modifiedFiles: {},
        activeEditorFile: null,
        repoFileTree: [],
      });
    } catch (err) {
      console.error('approveCode network error:', err);
      set({ isApproving: false });
      alert('Network error while approving code.');
    }
  },
}));

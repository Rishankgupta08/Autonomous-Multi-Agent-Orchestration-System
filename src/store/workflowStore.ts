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

  // Actions
  setGoal: (goal: string) => void;
  executeWorkflow: (goal: string) => Promise<void>;
  fetchHistory: () => Promise<void>;
  reset: () => void;
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
    const es = workflowApi.stream(workflowId);
    activeEventSource = es;

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
      // If backend sends overallStatus "AWAITING_MERGE", show a waiting phase
      const overallStatus = data.overallStatus as string;
      const phase: WorkflowPhase =
        overallStatus === 'AWAITING_MERGE' ? 'awaiting_merge' : 'complete';

      set({
        phase,
        score,
        overallStatus: overallStatus === 'SUCCESS' ? 'SUCCESS' : 'FAIL',
      });

      es.close();
      activeEventSource = null;
    });

    // ── SSE error handling ────────────────────────────────────────────
    es.onerror = () => {
      set({ phase: 'error', error: 'Lost connection to workflow stream' });
      es.close();
      activeEventSource = null;
    };
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
}));

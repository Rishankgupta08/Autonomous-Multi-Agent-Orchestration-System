import { create } from 'zustand';

interface WorkflowStep {
  id: string;
  service: 'github' | 'jira' | 'slack';
  name: string;
  description: string;
  status: 'pending' | 'active' | 'complete' | 'error';
  completionDetail?: string;
  errorMessage?: string;
  timeTaken?: number;
  rawRequest?: object;
  rawResponse?: object;
  aiReasoning?: string;
}

interface WorkflowScore {
  overall: number;
  taskCompletion: number;
  decisionAccuracy: number;
  executionEfficiency: number;
  contextRelevance: number;
  summary: string;
}

interface WorkflowRecord {
  id: string;
  command: string;
  status: 'success' | 'failed';
  createdAt: Date;
  score: WorkflowScore;
  steps: WorkflowStep[];
  errorMessage?: string;
}

interface WorkflowState {
  phase: 'input' | 'executing' | 'complete' | 'error';
  command: string;
  steps: WorkflowStep[];
  score: WorkflowScore | null;
  history: WorkflowRecord[];
  setPhase: (phase: WorkflowState['phase']) => void;
  setCommand: (cmd: string) => void;
  updateStep: (id: string, update: Partial<WorkflowStep>) => void;
  setScore: (score: WorkflowScore) => void;
  addToHistory: (record: WorkflowRecord) => void;
  reset: () => void;
}

export const useWorkflowStore = create<WorkflowState>((set) => ({
  phase: 'input',
  command: '',
  steps: [],
  score: null,
  history: [],
  setPhase: (phase) => set({ phase }),
  setCommand: (cmd) => set({ command: cmd }),
  updateStep: (id, update) =>
    set((state) => ({
      steps: state.steps.map((step) =>
        step.id === id ? { ...step, ...update } : step
      ),
    })),
  setScore: (score) => set({ score }),
  addToHistory: (record) =>
    set((state) => ({ history: [record, ...state.history] })),
  reset: () =>
    set({ phase: 'input', command: '', steps: [], score: null }),
}));

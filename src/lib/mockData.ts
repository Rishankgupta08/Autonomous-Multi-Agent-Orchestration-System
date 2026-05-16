export const MOCK_USER = {
  id: "usr_a4f9b2",
  name: "Rishank Gupta",
  username: "rishankgupta",
  email: "rishank@example.com",
  company: "Galgotias University",
  avatarUrl: "https://avatars.githubusercontent.com/u/583231"
};

export const MOCK_INTEGRATIONS = {
  github: { connected: true, username: "rishankgupta" },
  jira: { connected: true, domain: "rishank.atlassian.net", displayName: "Rishank Gupta", accountId: "acc_5f92b" },
  slack: { connected: true, teamName: "Galgotias Dev", botName: "moae-bot" }
};

interface WorkflowStep {
  id: string;
  service: 'github' | 'jira' | 'slack';
  name: string;
  status: 'pending' | 'active' | 'complete' | 'error';
  completionDetail?: string;
  errorMessage?: string;
  timeTaken?: number;
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

export const MOCK_WORKFLOWS: WorkflowRecord[] = [
  {
    id: "wf_001",
    command: "Create a PR for the auth feature, update Jira JIR-420, and notify #dev-team on Slack",
    status: "success",
    createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000),
    score: {
      overall: 94,
      taskCompletion: 87,
      decisionAccuracy: 91,
      executionEfficiency: 89,
      contextRelevance: 96,
      summary: "All 5 steps executed successfully. The agent correctly identified the target branch, generated contextually accurate PR description, and routed notifications to the correct Slack channel."
    },
    steps: [
      { id: "s1", service: "github", name: "Initializing Connection", status: "complete", timeTaken: 0.9 },
      { id: "s2", service: "github", name: "Branch Operations", status: "complete", completionDetail: "feature/auth-login created", timeTaken: 1.4 },
      { id: "s3", service: "github", name: "Pull Request", status: "complete", completionDetail: "PR #47 opened", timeTaken: 2.1 },
      { id: "s4", service: "jira", name: "Ticket Update", status: "complete", completionDetail: "JIR-420 → In Review", timeTaken: 1.2 },
      { id: "s5", service: "slack", name: "Team Notification", status: "complete", completionDetail: "Sent to #dev-team", timeTaken: 0.7 }
    ]
  },
  {
    id: "wf_002",
    command: "Fix login bug, push to staging, move JIR-389 to In Review",
    status: "failed",
    createdAt: new Date(Date.now() - 26 * 60 * 60 * 1000),
    score: {
      overall: 61,
      taskCompletion: 100,
      decisionAccuracy: 66,
      executionEfficiency: 30,
      contextRelevance: 88,
      summary: "Partial execution — GitHub rate limit hit at Step 3."
    },
    steps: [
      { id: "s1", service: "github", name: "Initializing Connection", status: "complete", timeTaken: 0.8 },
      { id: "s2", service: "github", name: "Branch Operations", status: "complete", timeTaken: 1.2 },
      { id: "s3", service: "github", name: "Pull Request", status: "error", errorMessage: "GitHub API rate limit exceeded", timeTaken: 0 },
      { id: "s4", service: "jira", name: "Ticket Update", status: "pending" },
      { id: "s5", service: "slack", name: "Team Notification", status: "pending" }
    ],
    errorMessage: "GitHub API rate limit exceeded"
  }
];

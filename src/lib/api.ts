// src/lib/api.ts

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

// ─────────────────────────────────────────────────────────────────────
// Core fetch wrapper
// ─────────────────────────────────────────────────────────────────────

async function apiFetch<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    credentials: 'include',           // CRITICAL: sends JSESSIONID cookie
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }));
    throw { status: response.status, message: error.message || response.statusText };
  }

  // Handle 204 No Content
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

// ─────────────────────────────────────────────────────────────────────
// Auth
// ─────────────────────────────────────────────────────────────────────

export const authApi = {
  me: () => apiFetch<{
    id: string;
    githubLogin: string;
    name: string;
    email: string | null;
    avatarUrl: string;
    jiraConnected: boolean;
    slackConnected: boolean;
  }>('/api/auth/me'),

  logout: () => apiFetch<{ status: string }>('/api/auth/logout', {
    method: 'POST',
  }),

  loginUrl: () => `${BASE_URL}/oauth2/authorization/github`,
};

// ─────────────────────────────────────────────────────────────────────
// Integrations
// ─────────────────────────────────────────────────────────────────────

export const integrationApi = {
  connectJira: (data: { domain: string; email: string; apiToken: string }) =>
    apiFetch<{ status: string; displayName?: string; message?: string }>(
      '/api/integrations/jira',
      { method: 'POST', body: JSON.stringify(data) }
    ),

  disconnectJira: () =>
    apiFetch<{ status: string }>('/api/integrations/jira', { method: 'DELETE' }),

  connectSlack: (data: { botToken: string }) =>
    apiFetch<{ status: string; teamName?: string; message?: string }>(
      '/api/integrations/slack',
      { method: 'POST', body: JSON.stringify(data) }
    ),

  disconnectSlack: () =>
    apiFetch<{ status: string }>('/api/integrations/slack', { method: 'DELETE' }),
};

// ─────────────────────────────────────────────────────────────────────
// Workflow
// ─────────────────────────────────────────────────────────────────────

export const workflowApi = {
  execute: (message: string) =>
    apiFetch<{ workflowId: string }>('/api/workflow/execute', {
      method: 'POST',
      body: JSON.stringify({ message }),
    }),

  history: () =>
    apiFetch<Array<{
      id: string;
      goal: string;
      status: string;
      score: number | null;
      createdAt: string;
      steps: Array<{ tool: string; action: string; status: string }>;
    }>>('/api/workflow/history'),

  detail: (workflowId: string) =>
    apiFetch<{
      id: string;
      goal: string;
      status: string;
      score: {
        overall: number;
        taskCompletion: number;
        decisionAccuracy: number;
        executionEfficiency: number;
        contextRelevance: number;
        summary: string;
      } | null;
      steps: Array<{
        stepId: number;
        tool: string;
        action: string;
        status: string;
        failureReason?: string;
        timeTakenMs?: number;
        resultJson?: string;
      }>;
      createdAt: string;
      completedAt: string | null;
    }>(`/api/workflow/${workflowId}`),

  // SSE stream — returns EventSource, NOT a fetch promise
  // Caller is responsible for closing it
  stream: (workflowId: string): EventSource => {
    return new EventSource(
      `${BASE_URL}/api/workflow/stream/${workflowId}`,
      { withCredentials: true }   // CRITICAL: sends JSESSIONID cookie with SSE
    );
  },
};

import { create } from 'zustand';

interface GitHubIntegration {
  connected: boolean;
  username: string;
}

interface JiraIntegration {
  connected: boolean;
  domain: string;
  displayName: string;
  accountId: string;
}

interface SlackIntegration {
  connected: boolean;
  teamName: string;
  botName: string;
}

interface IntegrationState {
  github: GitHubIntegration;
  jira: JiraIntegration;
  slack: SlackIntegration;
  setGitHub: (data: GitHubIntegration) => void;
  setJira: (data: JiraIntegration) => void;
  setSlack: (data: SlackIntegration) => void;
}

export const useIntegrationStore = create<IntegrationState>((set) => ({
  github: { connected: false, username: '' },
  jira: { connected: false, domain: '', displayName: '', accountId: '' },
  slack: { connected: false, teamName: '', botName: '' },
  setGitHub: (data) => set({ github: data }),
  setJira: (data) => set({ jira: data }),
  setSlack: (data) => set({ slack: data }),
}));

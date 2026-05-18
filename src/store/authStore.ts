// src/store/authStore.ts
import { create } from 'zustand';
import { authApi } from '../lib/api';

interface User {
  id: string;
  githubLogin: string;
  name: string;
  email: string | null;
  avatarUrl: string;
  jiraConnected: boolean;
  slackConnected: boolean;
}

interface AuthState {
  isAuthenticated: boolean;
  isLoading: boolean;          // true while /api/auth/me is in flight
  user: User | null;
  fetchUser: () => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;  // call after connecting integrations
}

export const useAuthStore = create<AuthState>((set) => ({
  isAuthenticated: false,
  isLoading: true,             // start true — app checks session on mount
  user: null,

  fetchUser: async () => {
    set({ isLoading: true });
    try {
      const user = await authApi.me();
      set({ isAuthenticated: true, user, isLoading: false });
    } catch (err: any) {
      // 401 means no session — not an error, just unauthenticated
      set({ isAuthenticated: false, user: null, isLoading: false });
    }
  },

  refreshUser: async () => {
    // Same as fetchUser but does NOT set isLoading
    // Used after connecting Jira/Slack to update jiraConnected/slackConnected flags
    try {
      const user = await authApi.me();
      set({ isAuthenticated: true, user });
    } catch {
      // Ignore — if this fails, user still stays logged in
    }
  },

  logout: async () => {
    try {
      await authApi.logout();
    } finally {
      // Always clear local state even if the API call fails
      set({ isAuthenticated: false, user: null, isLoading: false });
      // Redirect to landing page
      window.location.href = '/';
    }
  },
}));

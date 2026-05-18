// src/store/integrationStore.ts
import { create } from 'zustand';
import { integrationApi } from '../lib/api';
import { useAuthStore } from './authStore';

interface IntegrationState {
  // Loading states per action
  jiraLoading: boolean;
  slackLoading: boolean;
  // Error messages per integration
  jiraError: string | null;
  slackError: string | null;

  // Actions
  connectJira: (data: {
    domain: string;
    email: string;
    apiToken: string;
  }) => Promise<boolean>;

  disconnectJira: () => Promise<void>;

  connectSlack: (data: { botToken: string }) => Promise<boolean>;

  disconnectSlack: () => Promise<void>;

  clearErrors: () => void;
}

export const useIntegrationStore = create<IntegrationState>((set) => ({
  jiraLoading: false,
  slackLoading: false,
  jiraError: null,
  slackError: null,

  connectJira: async (data) => {
    set({ jiraLoading: true, jiraError: null });
    try {
      const res = await integrationApi.connectJira(data);
      if (res.status === 'error') {
        set({ jiraError: res.message || 'Invalid credentials', jiraLoading: false });
        return false;
      }
      // Refresh user so jiraConnected flag updates in the navbar/profile
      await useAuthStore.getState().refreshUser();
      set({ jiraLoading: false });
      return true;
    } catch (err: any) {
      set({ jiraError: err.message || 'Connection failed', jiraLoading: false });
      return false;
    }
  },

  disconnectJira: async () => {
    set({ jiraLoading: true, jiraError: null });
    try {
      await integrationApi.disconnectJira();
      await useAuthStore.getState().refreshUser();
    } finally {
      set({ jiraLoading: false });
    }
  },

  connectSlack: async (data) => {
    set({ slackLoading: true, slackError: null });
    try {
      const res = await integrationApi.connectSlack(data);
      if (res.status === 'error') {
        set({ slackError: res.message || 'Invalid token', slackLoading: false });
        return false;
      }
      await useAuthStore.getState().refreshUser();
      set({ slackLoading: false });
      return true;
    } catch (err: any) {
      set({ slackError: err.message || 'Connection failed', slackLoading: false });
      return false;
    }
  },

  disconnectSlack: async () => {
    set({ slackLoading: true, slackError: null });
    try {
      await integrationApi.disconnectSlack();
      await useAuthStore.getState().refreshUser();
    } finally {
      set({ slackLoading: false });
    }
  },

  clearErrors: () => set({ jiraError: null, slackError: null }),
}));

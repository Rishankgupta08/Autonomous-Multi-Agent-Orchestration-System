import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface DefaultsState {
  githubOwner: string
  githubDefaultRepo: string
  jiraProjectKey: string
  slackDefaultChannel: string
  isLoaded: boolean
  isSaving: boolean
  lastSavedAt: Date | null
}

interface DefaultsActions {
  fetchDefaults: () => Promise<void>
  saveDefaults: (data: Partial<DefaultsState>) => Promise<void>
  setField: (field: keyof DefaultsState, value: string) => void
}

export const useDefaultsStore = create<DefaultsState & DefaultsActions>()(
  persist(
    (set) => ({
      githubOwner: '',
      githubDefaultRepo: '',
      jiraProjectKey: '',
      slackDefaultChannel: '',
      isLoaded: false,
      isSaving: false,
      lastSavedAt: null,

      setField: (field, value) => set({ [field]: value }),

      fetchDefaults: async () => {
        try {
          const response = await fetch('/api/defaults', {
            credentials: 'include'
          })

          if (response.status === 401) {
            // Silently return if not logged in
            return
          }

          if (response.ok) {
            const data = await response.json()
            set({
              githubOwner: data.githubOwner || '',
              githubDefaultRepo: data.githubDefaultRepo || '',
              jiraProjectKey: data.jiraProjectKey || '',
              slackDefaultChannel: data.slackDefaultChannel || '',
              isLoaded: true
            })
          }
        } catch (error) {
            console.error('Failed to fetch defaults:', error)
        }
      },

      saveDefaults: async (data: Partial<DefaultsState>) => {
        set({ isSaving: true });
        try {
          const response = await fetch('/api/defaults', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify(data)
          });

          if (!response.ok) {
            console.error('saveDefaults failed:', response.status, await response.text());
            return;
          }

          const result = await response.json();
          set({
            githubOwner: result.githubOwner || '',
            githubDefaultRepo: result.githubDefaultRepo || '',
            jiraProjectKey: result.jiraProjectKey || '',
            slackDefaultChannel: result.slackDefaultChannel || '',
            lastSavedAt: new Date()
          });
        } catch (error) {
          console.error('saveDefaults network error:', error);
        } finally {
          set({ isSaving: false });
        }
      }
    }),
    {
      name: 'moae-defaults',
      partialize: (state) => ({
        githubOwner: state.githubOwner,
        githubDefaultRepo: state.githubDefaultRepo,
        jiraProjectKey: state.jiraProjectKey,
        slackDefaultChannel: state.slackDefaultChannel,
        isLoaded: state.isLoaded,
        // Exclude isSaving and lastSavedAt
      })
    }
  )
)

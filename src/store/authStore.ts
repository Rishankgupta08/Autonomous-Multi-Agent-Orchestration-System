import { create } from 'zustand';

interface User {
  id: string;
  name: string;
  username: string;
  email: string;
  company: string;
  avatarUrl: string;
}

interface AuthState {
  isAuthenticated: boolean;
  user: User | null;
  token: string | null;
  setUser: (user: User, token: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  isAuthenticated: false,
  user: null,
  token: null,
  setUser: (user, token) => set({ isAuthenticated: true, user, token }),
  logout: () => set({ isAuthenticated: false, user: null, token: null }),
}));

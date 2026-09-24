import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, getToken, setToken, setUnauthorizedHandler } from './api';
import type { Me, Privilege } from './types';

interface AuthState {
  me: Me | null;
  loading: boolean;
  signIn: (token: string) => Promise<void>;
  signOut: () => void;
  refresh: () => Promise<void>;
  can: (...privileges: Privilege[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    if (!getToken()) {
      setMe(null);
      setLoading(false);
      return;
    }
    try {
      setMe(await api.get<Me>('/api/auth/me'));
    } catch {
      setToken(null);
      setMe(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => setMe(null));
    refresh();
  }, [refresh]);

  const signIn = useCallback(async (token: string) => {
    setToken(token);
    setLoading(true);
    await refresh();
  }, [refresh]);

  const signOut = useCallback(() => {
    setToken(null);
    setMe(null);
  }, []);

  const can = useCallback(
    (...privileges: Privilege[]) => !!me && privileges.some((p) => me.privileges.includes(p)),
    [me],
  );

  return <AuthContext.Provider value={{ me, loading, signIn, signOut, refresh, can }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth outside AuthProvider');
  return ctx;
}

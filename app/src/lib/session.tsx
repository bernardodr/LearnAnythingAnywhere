import * as SecureStore from 'expo-secure-store';
import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { Platform } from 'react-native';

import { apiPost, setUnauthorizedHandler } from '@/lib/api';
import type { DevLoginResponse, Role, User } from '@/lib/types';

const KEY = 'laa.session';

type Stored = { token: string; user: User; expiresAt: number };

type Session = {
  /** False until the stored session has been read; render nothing session-dependent before then. */
  ready: boolean;
  token: string | null;
  user: User | null;
  signIn: (email: string, role: Exclude<Role, 'OWNER'>) => Promise<User>;
  signOut: () => Promise<void>;
};

const SessionContext = createContext<Session | null>(null);

// The token is kept in the Keychain / Keystore on a phone (expo-secure-store) and in localStorage on web,
// where expo-secure-store isn't available.
const storage = {
  async get(): Promise<string | null> {
    if (Platform.OS === 'web') return globalThis.localStorage?.getItem(KEY) ?? null;
    return SecureStore.getItemAsync(KEY);
  },
  async set(value: string) {
    if (Platform.OS === 'web') globalThis.localStorage?.setItem(KEY, value);
    else await SecureStore.setItemAsync(KEY, value);
  },
  async clear() {
    if (Platform.OS === 'web') globalThis.localStorage?.removeItem(KEY);
    else await SecureStore.deleteItemAsync(KEY);
  },
};

export function SessionProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [stored, setStored] = useState<Stored | null>(null);

  useEffect(() => {
    storage
      .get()
      .then((raw) => {
        if (!raw) return;
        const parsed = JSON.parse(raw) as Stored;
        if (parsed.expiresAt > Date.now()) setStored(parsed);
        else void storage.clear();
      })
      .catch(() => storage.clear())
      .finally(() => setReady(true));
  }, []);

  const signOut = useCallback(async () => {
    setStored(null);
    await storage.clear();
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => void signOut());
    return () => setUnauthorizedHandler(null);
  }, [signOut]);

  const signIn = useCallback(async (email: string, role: Exclude<Role, 'OWNER'>) => {
    const res = await apiPost<DevLoginResponse>('/auth/dev-login', { email, role });
    const next: Stored = { token: res.token, user: res.user, expiresAt: Date.now() + res.expiresIn * 1000 };
    await storage.set(JSON.stringify(next));
    setStored(next);
    return res.user;
  }, []);

  const value = useMemo<Session>(
    () => ({ ready, token: stored?.token ?? null, user: stored?.user ?? null, signIn, signOut }),
    [ready, stored, signIn, signOut],
  );
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) throw new Error('useSession must be used inside <SessionProvider>');
  return session;
}

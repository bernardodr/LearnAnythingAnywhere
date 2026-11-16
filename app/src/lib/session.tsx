import * as SecureStore from 'expo-secure-store';
import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { Platform } from 'react-native';

import { apiPost, setUnauthorizedHandler } from '@/lib/api';
import type { Role, SignInResponse, User } from '@/lib/types';

const KEY = 'laa.session';

type Stored = { token: string; user: User; expiresAt: number };

export type SignUp = {
  email: string;
  password: string;
  displayName: string | null;
  role: Exclude<Role, 'OWNER'>;
  ownerSetupCode: string | null;
};

type Session = {
  /** False until the stored session has been read; render nothing session-dependent before then. */
  ready: boolean;
  token: string | null;
  user: User | null;
  signIn: (email: string, password: string) => Promise<User>;
  signUp: (details: SignUp) => Promise<User>;
  /** Email only, when the server has dev sign-in on (local development). */
  devSignIn: (email: string, role: Exclude<Role, 'OWNER'>) => Promise<User>;
  /** A new sign-in from the server (after a password change) replaces this one. */
  renew: (res: SignInResponse) => Promise<void>;
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

  const renew = useCallback(async (res: SignInResponse) => {
    const next: Stored = { token: res.token, user: res.user, expiresAt: Date.now() + res.expiresIn * 1000 };
    await storage.set(JSON.stringify(next));
    setStored(next);
  }, []);

  const signIn = useCallback(
    async (email: string, password: string) => {
      const res = await apiPost<SignInResponse>('/auth/sign-in', { email, password });
      await renew(res);
      return res.user;
    },
    [renew],
  );

  const signUp = useCallback(
    async (details: SignUp) => {
      const res = await apiPost<SignInResponse>('/auth/sign-up', details);
      await renew(res);
      return res.user;
    },
    [renew],
  );

  const devSignIn = useCallback(
    async (email: string, role: Exclude<Role, 'OWNER'>) => {
      const res = await apiPost<SignInResponse>('/auth/dev-login', { email, role });
      await renew(res);
      return res.user;
    },
    [renew],
  );

  const value = useMemo<Session>(
    () => ({ ready, token: stored?.token ?? null, user: stored?.user ?? null, signIn, signUp, devSignIn, renew, signOut }),
    [ready, stored, signIn, signUp, devSignIn, renew, signOut],
  );
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) throw new Error('useSession must be used inside <SessionProvider>');
  return session;
}

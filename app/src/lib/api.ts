// On a phone in Expo Go, localhost is the phone itself: set EXPO_PUBLIC_API_URL to the laptop's LAN IP.
export const API_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080';

/** An error response from the server. `message` is safe to show the learner. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly errors: string[] = [],
  ) {
    super(message);
  }
}

type Options = { token?: string | null; body?: unknown; signal?: AbortSignal };

/** Called on any 401 so the session can sign out (an expired or revoked token). */
let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

export async function api<T>(method: string, path: string, { token, body, signal }: Options = {}): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`${API_URL}${path}`, {
      method,
      signal,
      headers: {
        Accept: 'application/json',
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'Can’t reach the server. Check it’s running and that you’re on the same network.');
  }
  return parse<T>(res);
}

export async function parse<T>(res: Response): Promise<T> {
  const text = await res.text();
  const data = text ? safeJson(text) : null;
  if (res.status === 401 && onUnauthorized) onUnauthorized();
  if (!res.ok) {
    const problem = (data ?? {}) as { detail?: string; title?: string; errors?: string[] };
    throw new ApiError(res.status, problem.detail ?? problem.title ?? `Request failed (${res.status})`, problem.errors ?? []);
  }
  return data as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

export function apiGet<T>(path: string, token?: string | null): Promise<T> {
  return api<T>('GET', path, { token });
}

export function apiPost<T>(path: string, body?: unknown, token?: string | null): Promise<T> {
  return api<T>('POST', path, { token, body });
}

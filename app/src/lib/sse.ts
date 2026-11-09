import { fetch as expoFetch } from 'expo/fetch';

import { API_URL, ApiError, parse } from '@/lib/api';

export type SseEvent = { event: string; data: unknown };

/**
 * POSTs and reads a server-sent-events response as it arrives. `expo/fetch` streams the body on iOS and
 * Android (React Native's built-in fetch doesn't), and is the browser's own fetch on web. EventSource can't
 * POST or send an Authorization header, hence this instead.
 *
 * Resolves when the stream ends. If the server answers with JSON instead (a 4xx before streaming started),
 * rejects with an ApiError carrying its message.
 */
type StreamOptions = { token: string; signal?: AbortSignal; onEvent: (e: SseEvent) => void };

export function postSse(path: string, options: StreamOptions & { body?: unknown }): Promise<void> {
  return stream('POST', path, options);
}

/** Follows a server-sent-events stream with GET (job progress: GET /jobs/{id}/events). */
export function getSse(path: string, options: StreamOptions): Promise<void> {
  return stream('GET', path, options);
}

async function stream(
  method: 'GET' | 'POST',
  path: string,
  { token, body, signal, onEvent }: StreamOptions & { body?: unknown },
): Promise<void> {
  let res: Awaited<ReturnType<typeof expoFetch>>;
  try {
    res = await expoFetch(`${API_URL}${path}`, {
      method,
      signal,
      headers: {
        Accept: 'text/event-stream, application/json',
        ...(method === 'POST' ? { 'Content-Type': 'application/json' } : {}),
        Authorization: `Bearer ${token}`,
      },
      body: method === 'POST' ? JSON.stringify(body ?? {}) : undefined,
    });
  } catch {
    if (signal?.aborted) return;
    throw new ApiError(0, 'Can’t reach the server. Check it’s running and that you’re on the same network.');
  }

  const type = res.headers.get('content-type') ?? '';
  if (!res.ok || !type.includes('text/event-stream')) {
    await parse(res as unknown as Response); // throws ApiError with the server's message
    return;
  }
  if (!res.body) throw new ApiError(0, 'This device can’t read streamed replies.');

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      buffer = drain(buffer, onEvent);
    }
    buffer += decoder.decode();
    drain(buffer + '\n\n', onEvent);
  } catch {
    if (signal?.aborted) return;
    throw new ApiError(0, 'The connection dropped while the reply was streaming.');
  }
}

/** Dispatches every complete event in `buffer` and returns the unfinished remainder. */
function drain(buffer: string, onEvent: (e: SseEvent) => void): string {
  const blocks = buffer.split(/\r?\n\r?\n/);
  const rest = blocks.pop() ?? '';
  for (const block of blocks) {
    let event = 'message';
    const data: string[] = [];
    for (const line of block.split(/\r?\n/)) {
      if (line.startsWith('event:')) event = line.slice(6).trim();
      else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
    }
    if (data.length === 0) continue;
    const raw = data.join('\n');
    let parsed: unknown = raw;
    try {
      parsed = JSON.parse(raw);
    } catch {
      // not JSON: pass the text through
    }
    onEvent({ event, data: parsed });
  }
  return rest;
}

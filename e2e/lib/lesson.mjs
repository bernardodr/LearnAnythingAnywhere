// Sets up what a walk-through needs through the API alone: an account (dev sign-in), a textbook whose preface is
// finished, and lesson 1, written in practice mode (which always draws one diagram). Used by diagrams.mjs and
// production.mjs; both need the server started with LAA_DEV_LOGIN and LAA_PRACTICE_PROVIDERS on, so it costs nothing.
import assert from 'node:assert/strict';

/**
 * Makes the account, the textbook and lesson 1.
 *
 * @param {string} api the server's address, e.g. http://localhost:8090
 * @param {string} email a new address for this run (dev sign-in creates the account, verified)
 * @returns {{ token: string, id: string, events: string }} the sign-in token, the textbook's id, and the lesson job's
 *   whole event stream (so a caller can check its stages)
 */
export async function textbookWithLesson(api, email) {
  const json = (r) => r.json();
  const token = (await fetch(`${api}/auth/dev-login`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email }) }).then(json)).token;
  assert.ok(token, 'dev sign-in gives a token');
  const call = (method, path, body, accept = 'application/json') => fetch(api + path, { method,
    headers: { Authorization: `Bearer ${token}`, Accept: accept, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined });
  const stream = (path, body) => call('POST', path, body, 'text/event-stream, application/json').then((r) => r.text());

  const id = (await call('POST', '/textbooks', { topic: 'Bouldering' }).then(json)).textbook.id;
  // The preface interview: the tutor's opening, then two answers, then the learner confirms the draft.
  await stream(`/textbooks/${id}/mission/messages`);
  await stream(`/textbooks/${id}/mission/messages`, { content: 'I want to climb a V3 with my friends by spring.' });
  await stream(`/textbooks/${id}/mission/messages`, { content: 'Twice a week at my local gym.' });
  const draft = (await call('GET', `/textbooks/${id}/mission`).then(json)).draft;
  await call('POST', `/textbooks/${id}/mission/confirm`, { draft });
  const job = (await call('POST', `/textbooks/${id}/lessons/next`).then(json)).jobId;
  const events = await call('GET', `/jobs/${job}/events`, null, 'text/event-stream').then((r) => r.text());
  assert.match(events, /event:done/, 'the lesson job finishes');
  return { token, id, events };
}

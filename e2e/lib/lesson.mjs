// Sets up what a walk-through needs through the API alone: an account (dev sign-in), textbooks whose preface is
// finished, and lesson 1, written in practice mode (which always draws one diagram). Used by diagrams.mjs,
// library.mjs and production.mjs; all need the server started with LAA_DEV_LOGIN and LAA_PRACTICE_PROVIDERS on, so
// it costs nothing.
import assert from 'node:assert/strict';

const json = (r) => r.json();

/**
 * Signs in with dev sign-in (which creates the account, verified) and returns a way to call the API as that learner.
 *
 * @param {string} api the server's address, e.g. http://localhost:8090
 * @param {string} email a new address for this run
 * @returns {Promise<{ token: string, call: Function, stream: Function }>} the token; `call(method, path, body, accept)`
 *   for JSON; `stream(path, body)` for a streamed reply, read whole
 */
export async function signIn(api, email) {
  const token = (await fetch(`${api}/auth/dev-login`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email }) }).then(json)).token;
  assert.ok(token, 'dev sign-in gives a token');
  const call = (method, path, body, accept = 'application/json') => fetch(api + path, { method,
    headers: { Authorization: `Bearer ${token}`, Accept: accept, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined });
  const stream = (path, body) => call('POST', path, body, 'text/event-stream, application/json').then((r) => r.text());
  return { token, call, stream };
}

/**
 * A new textbook with its preface finished: the opening question, two answers, then the learner confirms the draft
 * (which is when the cover's motif is chosen).
 *
 * @param {{ call: Function, stream: Function }} learner from `signIn`
 * @param {string} topic what the book is about, as the learner would type it
 * @returns {Promise<string>} the textbook's id
 */
export async function bookWithMission({ call, stream }, topic) {
  const id = (await call('POST', '/textbooks', { topic }).then(json)).textbook.id;
  await stream(`/textbooks/${id}/mission/messages`);
  await stream(`/textbooks/${id}/mission/messages`, { content: 'I want to understand it well enough to enjoy it with friends.' });
  await stream(`/textbooks/${id}/mission/messages`, { content: 'Twenty minutes, a few evenings a week.' });
  const draft = (await call('GET', `/textbooks/${id}/mission`).then(json)).draft;
  const confirmed = await call('POST', `/textbooks/${id}/mission/confirm`, { draft });
  assert.equal(confirmed.status, 200, 'the mission is confirmed');
  return id;
}

/**
 * Makes the account, a Bouldering textbook with its preface done, and lesson 1.
 *
 * @param {string} api the server's address, e.g. http://localhost:8090
 * @param {string} email a new address for this run (dev sign-in creates the account, verified)
 * @returns {Promise<{ token: string, id: string, events: string }>} the sign-in token, the textbook's id, and the
 *   lesson job's whole event stream (so a caller can check its stages)
 */
export async function textbookWithLesson(api, email) {
  const learner = await signIn(api, email);
  const id = await bookWithMission(learner, 'Bouldering');
  const job = (await learner.call('POST', `/textbooks/${id}/lessons/next`).then(json)).jobId;
  const events = await learner.call('GET', `/jobs/${job}/events`, null, 'text/event-stream').then((r) => r.text());
  assert.match(events, /event:done/, 'the lesson job finishes');
  return { token: learner.token, id, events };
}

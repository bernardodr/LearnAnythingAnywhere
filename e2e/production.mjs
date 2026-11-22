// The production stack (deploy/compose.yml: Caddy, the server, Postgres), checked from outside as a visitor sees it.
// Run it with `./e2e/production.sh`, which builds the real images, starts them with deploy.sh on local addresses, and
// then runs this. Exits non-zero on any failure.
//
// What it checks:
// - the API through Caddy: /health, security headers, streamed replies (the preface interview and the lesson job);
// - the web app: its headers, caching (hashed scripts for a year, the page itself always fresh), and that any app
//   address (a bookmark, a reload) gets the app;
// - a learner signs in and opens a lesson with a diagram, with no console errors: so CORS, the baked-in API address
//   and the headers all work together, and the sealed frame still runs behind them;
// - a visitor can't forge the forwarded address to dodge the sign-in limit (Caddy replaces the header).
import assert from 'node:assert/strict';

import { connect } from './lib/cdp.mjs';
import { textbookWithLesson } from './lib/lesson.mjs';

const API = process.env.API ?? 'http://api.localhost:8180';
const APP = process.env.APP ?? 'http://localhost:8180/';
const OUT = process.argv[2];
const EMAIL = `production-${Date.now()}@laa.dev`;
const ok = (s) => console.log('✓ ' + s);

// ---- The API, through Caddy.
const health = await fetch(`${API}/health`);
assert.equal(health.status, 200);
assert.equal((await health.json()).status, 'UP');
assert.equal(health.headers.get('x-content-type-options'), 'nosniff');
assert.match(health.headers.get('strict-transport-security') ?? '', /max-age=31536000/);
assert.equal(health.headers.get('server'), null, 'no Server header');
ok('the API answers through Caddy, with its security headers');

// Streamed replies come through the proxy whole (the preface interview and the lesson job are both streams).
const { id, events } = await textbookWithLesson(API, EMAIL);
assert.match(events, /"stage":"draw"/, 'the lesson job streamed its stages');
ok('streamed replies (the preface interview, the lesson job) pass through Caddy');

// ---- The web app's files.
const page = await fetch(APP);
assert.equal(page.status, 200);
const html = await page.text();
assert.match(html, /<div id="root">/, 'the app’s page');
assert.equal(page.headers.get('x-frame-options'), 'DENY');
assert.equal(page.headers.get('cache-control'), 'no-cache', 'the page is checked on every visit, so a deploy shows at once');
const scripts = [...html.matchAll(/src="(\/_expo\/static\/js\/web\/[^"]+\.js)"/g)].map((m) => m[1]);
assert.ok(scripts.length > 0, 'the page loads hashed scripts');
let code = '';
for (const script of scripts) {
  const js = await fetch(new URL(script, APP));
  assert.equal(js.status, 200);
  assert.equal(js.headers.get('cache-control'), 'public, max-age=31536000, immutable');
  assert.match(js.headers.get('content-encoding') ?? '', /zstd|gzip/, 'compressed');
  code += await js.text();
}
assert.ok(code.includes(API), 'the web app was built to talk to this API');
const deep = await fetch(new URL(`textbooks/${id}/lessons/1`, APP));
assert.equal(deep.status, 200);
assert.equal(await deep.text(), html, 'an app address (a bookmark or a reload) gets the app');
ok('the web app: headers, a year’s caching for hashed scripts, none for the page, and app addresses work');

// ---- A learner, in the browser.
const b = await connect({ outDir: OUT });
const { send, evaluate, hasText, waitFor, click, frame, problems, shot } = b;
try {
  await send('Storage.clearDataForOrigin', { origin: new URL(APP).origin, storageTypes: 'all' });
  await send('Page.navigate', { url: APP });
  await waitFor('title page', `!!document.querySelector('[aria-label="Developer sign-in"]')`, 20000);
  await click('Developer sign-in');
  await b.type('Email', EMAIL);
  await click('Sign in without a password');
  await waitFor('shelf', hasText('Bouldering'));
  await shot('production-shelf');

  // A reload on a deep address: the app comes back where it was, still signed in.
  await send('Page.navigate', { url: `${APP}textbooks/${id}/lessons/1` });
  await waitFor('lesson page', hasText('Your win today'), 20000);
  await waitFor('the diagram frame', `!!document.querySelector('iframe[sandbox]')`);
  await evaluate(`document.querySelector('iframe[sandbox]').scrollIntoView({ block: 'start' })`);
  const inside = await frame();
  await waitFor('the diagram ran', `document.querySelector('iframe[sandbox]').getBoundingClientRect().height > 330`);
  assert.equal(await inside(`document.getElementById('count').textContent`), '1 of 4');
  await shot('production-lesson');
  assert.deepEqual(problems, [], 'no console errors or warnings');
  ok('a learner signs in, reloads a lesson, and its diagram runs; no console errors');
} catch (e) {
  await shot('FAILED');
  throw e;
} finally {
  b.ws.close();
}

// ---- The forwarded address can't be forged. 20 failed sign-ins pause an address for 15 minutes. Each try here
// claims a different address in X-Forwarded-For (and a different email, so the per-email pause doesn't kick in).
// Caddy replaces the header with the real one, so the 21st try is still paused. This runs last: it pauses this
// machine's sign-ins for the rest of the trial.
const signIn = (n) => fetch(`${API}/auth/sign-in`, { method: 'POST',
  headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': `203.0.113.${n}` },
  body: JSON.stringify({ email: `nobody-${n}@laa.dev`, password: 'not the password at all' }) });
for (let n = 1; n <= 20; n++) assert.equal((await signIn(n)).status, 401);
assert.equal((await signIn(21)).status, 429, 'a forged X-Forwarded-For doesn’t dodge the sign-in limit');
ok('a forged forwarded address doesn’t get round the sign-in limit');

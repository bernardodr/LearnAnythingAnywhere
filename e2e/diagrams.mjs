// Interactive diagrams in the real web app (issue #60): a learner opens a lesson with a diagram and uses it, and then
// the diagram's sealed frame is attacked from the inside, as a hostile AI-written page would. Run it with
// `./e2e/run.sh` (it starts everything on a scratch database in practice mode, with no AI keys). Exits non-zero on
// any failure.
//
// What the frame must hold, whatever the page's script does:
// - no network: fetch, XHR, images, styles, scripts, fonts, frames, beacons, sockets, event streams;
// - no forms, pop-ups, top-level navigation or eval;
// - no access to the app's storage, cookies or page;
// - messages to the app: only its own frame's, only height (clamped) and error;
// - a page that fails, or navigates itself away (the one thing the web can't forbid), is swapped for its text version.
import assert from 'node:assert/strict';

import { connect } from './lib/cdp.mjs';
import { textbookWithLesson } from './lib/lesson.mjs';

const API = process.env.API ?? 'http://localhost:8090';
const APP = process.env.APP ?? 'http://localhost:8099/';
const CANARY = new URL('/__canary/', APP).href;
const OUT = process.argv[2];
const EMAIL = `diagram-${Date.now()}@laa.dev`;
const json = (r) => r.json();
const ok = (s) => console.log('✓ ' + s);

// ---- A textbook with lesson 1 (written in practice mode, which always draws one diagram), through the API.
const { id, events } = await textbookWithLesson(API, EMAIL);
assert.match(events, /"stage":"draw"/, 'the lesson job draws its diagrams');
const lessonUrl = `${APP}textbooks/${id}/lessons/1`;
ok('lesson 1 written with a diagram');

const b = await connect({ outDir: OUT });
const { send, evaluate, hasText, waitFor, click, clickAt, frame, forgetFrame, problems, sleep, shot } = b;
const iframe = `document.querySelector('iframe[sandbox]')`;
const frameBox = () => evaluate(`(() => { const r = ${iframe}.getBoundingClientRect(); return { x: r.x, y: r.y, h: r.height }; })()`);
const canaryHits = () => fetch(new URL('/__canary-hits', APP)).then(json);

async function openLesson() {
  forgetFrame();
  await send('Page.navigate', { url: lessonUrl });
  await waitFor('lesson page', hasText('Your win today'), 20000);
  await waitFor('the diagram frame', `!!${iframe}`);
  await evaluate(`${iframe}.scrollIntoView({ block: 'start' })`);
  const inside = await frame();
  await waitFor('the diagram ran', `${iframe}.getBoundingClientRect().height > 330`);
  return inside;
}

try {
  // ---- Signing in and using the diagram, as a learner would.
  await send('Storage.clearDataForOrigin', { origin: new URL(APP).origin, storageTypes: 'all' });
  await send('Page.navigate', { url: APP });
  await waitFor('title page', `!!document.querySelector('[aria-label="Developer sign-in"]')`);
  await click('Developer sign-in');
  await b.type('Email', EMAIL);
  await click('Sign in without a password');
  await waitFor('shelf', hasText('Bouldering'));

  let inside = await openLesson();
  assert.equal(await evaluate(`${iframe}.getAttribute('sandbox')`), 'allow-scripts', 'sandboxed with scripts only');
  assert.equal(await inside(`document.getElementById('count').textContent`), '1 of 4');
  for (const expected of ['2 of 4', '3 of 4']) {
    const at = await inside(`(() => { const r = document.getElementById('next').getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; })()`);
    const f = await frameBox();
    await clickAt(f.x + at.x, f.y + at.y);
    let now = '';
    for (let t = 0; t < 20 && now !== expected; t++, await sleep(100)) now = await inside(`document.getElementById('count').textContent`);
    assert.equal(now, expected, 'a real click on Next moves one step');
    await sleep(400); // a second press on the same spot too soon would read as a double-click
  }
  assert.equal(await inside(`document.querySelector('.stage.on text').textContent`), 'Notice', 'the current stage is highlighted');
  await shot('diagram-stepped');
  ok('the diagram draws, fits its page, and steps with real clicks');

  // ---- The contrast guard: a page's own CSS gives a chosen button a pale fill while LAA's style keeps its text
  // ivory (what the owner saw). The guard sets the text to ink; when the fill turns dark, back to ivory.
  const INK = 'rgb(41, 40, 35)', PAPER = 'rgb(247, 244, 236)';
  await inside(`(() => {
    const css = document.createElement('style');
    css.textContent = '#guard-test[aria-pressed="true"] { background: var(--note); }';
    document.head.append(css);
    const b = document.createElement('button');
    b.id = 'guard-test'; b.className = 'laa-button'; b.textContent = 'Extend the Belt line'; b.setAttribute('aria-pressed', 'true');
    document.body.append(b);
  })()`);
  let colour = '';
  for (let t = 0; t < 20 && colour !== INK; t++, await sleep(100)) colour = await inside(`getComputedStyle(document.getElementById('guard-test')).color`);
  assert.equal(colour, INK, 'ivory text on a pale fill becomes ink: ' + JSON.stringify(await inside(`(() => {
    const g = document.getElementById('guard-test'); const s = getComputedStyle(g);
    return { mark: g.getAttribute('data-laa-text'), colour: s.color, fill: s.backgroundColor, ns: g.namespaceURI,
      guard: [...document.scripts].some((x) => x.textContent.includes('data-laa-text')), ready: document.readyState };
  })()`)));
  await inside(`document.getElementById('guard-test').style.background = 'var(--accent)'`);
  for (let t = 0; t < 20 && colour !== PAPER; t++, await sleep(100)) colour = await inside(`getComputedStyle(document.getElementById('guard-test')).color`);
  assert.equal(colour, PAPER, 'and ivory again on a dark fill');
  assert.equal(await inside(`getComputedStyle(document.getElementById('next')).color`), 'rgb(56, 78, 65)', 'readable text is left as the page set it');
  await inside(`document.getElementById('guard-test').remove()`);
  ok('the contrast guard: unreadable text on a button turns ink or ivory to suit its fill; readable text is left alone');

  const title = await evaluate(`document.querySelector('[aria-label^="Show "][aria-label$=" as text"]').getAttribute('aria-label')`);
  await click(title);
  await waitFor('the text version', hasText('Repeat the step, changing one thing'));
  assert.equal(await evaluate(`!${iframe}`), true, 'the frame is gone while the text shows');
  await click(title.replace(/^Show (.*) as text$/, 'Show the diagram: $1'));
  await waitFor('the diagram again', `!!${iframe}`);
  ok('Show as text, and back');

  // ---- The seal, attacked from inside the page.
  inside = await openLesson();
  const seal = await inside(`(async () => {
    const out = {};
    const tryIt = (name, f) => { try { out[name] = f(); } catch (e) { out[name] = 'blocked'; } };
    tryIt('ownStorage', () => String(localStorage.length));
    tryIt('appStorage', () => String(window.parent.localStorage.length));
    tryIt('appPage', () => window.parent.document.title);
    tryIt('cookie', () => document.cookie = 'x=1');
    tryIt('topNavigation', () => { top.location = '${CANARY}top-navigation'; return 'navigated'; });
    tryIt('eval', () => eval('1 + 1'));
    out.popup = window.open('${CANARY}popup') === null ? 'blocked' : 'OPENED';
    const a = document.createElement('a'); a.href = '${CANARY}link-popup'; a.target = '_blank'; document.body.append(a); a.click();
    out.origin = String(window.origin);
    return out; })()`);
  assert.deepEqual(seal, { ownStorage: 'blocked', appStorage: 'blocked', appPage: 'blocked', cookie: 'blocked',
    topNavigation: 'blocked', eval: 'blocked', popup: 'blocked', origin: 'null' }, 'the frame reaches nothing of the app');

  // Every way to send a request. None may arrive.
  const vectors = await inside(`(async () => {
    const c = (name) => '${CANARY}' + name;
    const out = {};
    const attempt = async (name, f) => { try { await f(c(name)); out[name] = 'sent?'; } catch (e) { out[name] = 'refused'; } };
    await attempt('fetch', (u) => fetch(u));
    await attempt('xhr', (u) => new Promise((ok, no) => { const x = new XMLHttpRequest(); x.onload = ok; x.onerror = no; x.open('GET', u); x.send(); }));
    await attempt('beacon', (u) => { if (!navigator.sendBeacon(u, 'x')) throw new Error('refused'); });
    await attempt('websocket', (u) => new Promise((ok, no) => { const s = new WebSocket(u.replace('http', 'ws')); s.onopen = ok; s.onerror = no; }));
    await attempt('eventsource', (u) => new Promise((ok, no) => { const s = new EventSource(u); s.onopen = ok; s.onerror = () => { s.close(); no(); }; }));
    const add = (html) => { const d = document.createElement('div'); d.innerHTML = html; document.body.append(d); };
    add('<img src="' + c('img') + '">');
    add('<link rel="stylesheet" href="' + c('stylesheet') + '">');
    add('<link rel="prefetch" href="' + c('prefetch') + '">');
    add('<iframe src="' + c('iframe') + '"></iframe>');
    add('<div style="width:9px;height:9px;background:url(' + c('css-background') + ')"></div>');
    add('<style>@font-face { font-family: canary; src: url(' + c('font') + '); } body { font-family: canary; }</style>');
    add('<video src="' + c('video') + '" autoplay muted></video>');
    add('<object data="' + c('object') + '"></object>');
    const s = document.createElement('script'); s.src = c('script'); document.body.append(s);
    const f = document.createElement('form'); f.action = c('form'); f.method = 'post'; document.body.append(f);
    try { f.submit(); } catch (e) {}
    await new Promise((r) => setTimeout(r, 1500));
    return out; })()`);
  await sleep(500);
  const hits = await canaryHits();
  assert.deepEqual(hits, {}, `no request may leave the frame, but these arrived: ${JSON.stringify(hits)}`);
  // What the page itself saw is only a hint (a beacon can report "queued" and still be stopped): the canary decides.
  console.log('  as the page saw it:', JSON.stringify(vectors));
  ok('nothing left the frame: 15 kinds of request tried, 0 arrived; no storage, cookies, page, pop-ups, eval or navigation');

  // ---- Messages: only the frame's own, only height (clamped) and error.
  await inside(`window.parent.postMessage({ laa: 'diagram', kind: 'height', value: 999999 }, '*')`);
  await waitFor('height capped at 1600', `${iframe}.getBoundingClientRect().height === 1600`);
  await inside(`window.parent.postMessage({ laa: 'diagram', kind: 'height', value: -5 }, '*')`);
  await waitFor('height at least 120', `${iframe}.getBoundingClientRect().height === 120`);
  await inside(`window.parent.postMessage({ laa: 'diagram', kind: 'navigate', value: '/owner' }, '*')`);
  await evaluate(`window.postMessage({ laa: 'diagram', kind: 'error' }, '*')`); // from the app's own page, not the frame
  await sleep(400);
  assert.equal(await evaluate(`!!${iframe}`), true, 'messages that are not the frame’s own height or error change nothing');
  ok('the frame can only report its height (kept between 120 and 1600) and errors');

  // ---- A page whose script fails falls back to the text version.
  await inside(`setTimeout(() => { throw new Error('a broken diagram'); }, 0)`);
  await waitFor('the text version after an error', hasText('couldn’t be shown here'));
  assert.equal(await evaluate(`!${iframe}`), true);
  await shot('diagram-fell-back');
  ok('a page whose script fails is replaced by its text version');

  // ---- A page that navigates itself away (allowed by every web sandbox) is replaced at once.
  inside = await openLesson();
  await inside(`location.href = '${CANARY}self-navigation'`);
  await waitFor('the text version after navigating away', hasText('couldn’t be shown here'));
  const after = await canaryHits();
  assert.deepEqual(Object.keys(after), ['self-navigation'], 'only the self-navigation itself reached anything');
  ok('a page that navigates itself away is replaced by its text version (the web allows that one request; phones refuse it)');

  assert.deepEqual(problems, [], 'no console errors or warnings on the lesson page');
  ok('no console errors or warnings');
} catch (e) {
  await shot('FAILED');
  console.error('✗ ' + (e.stack ?? e));
  process.exitCode = 1;
} finally {
  b.ws.close();
}

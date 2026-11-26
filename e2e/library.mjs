// The library after the facelift (docs/DESIGN.md, issues #68 and #78), in the real web app: the mockup's four books,
// each a drawn cover in leather or linen with its motif; the spines view; arranging by dragging; the settings icon; and
// the empty library. Run it with
// `./e2e/run.sh library [screenshot dir]` (a scratch database in practice mode, no AI keys). Exits non-zero on any
// failure.
import assert from 'node:assert/strict';

import { connect } from './lib/cdp.mjs';
import { bookWithMission, signIn } from './lib/lesson.mjs';

const API = process.env.API ?? 'http://localhost:8090';
const APP = process.env.APP ?? 'http://localhost:8099/';
const OUT = process.argv[2];
const EMAIL = `library-${Date.now()}@laa.dev`;
const ok = (s) => console.log('✓ ' + s);

// ---- The mockup's library, through the API: four books with their prefaces done, so the AI (here, practice mode)
// has picked each one's motif. The last is left mid-preface, to show the ribbon.
const learner = await signIn(API, EMAIL);
const BOOKS = { 'The Night Sky': 'stars', 'A Little History of Art': 'shapes', 'How Computers Think': 'dots', 'The World of Plants': 'leaf' };
for (const topic of Object.keys(BOOKS)) await bookWithMission(learner, topic);
await learner.call('POST', '/textbooks', { topic: 'Sourdough bread' });
const shelf = await learner.call('GET', '/textbooks').then((r) => r.json());
for (const [title, motif] of Object.entries(BOOKS)) {
  assert.equal(shelf.find((b) => b.title === title)?.coverMotif, motif, `${title} has the ${motif} motif`);
}
assert.equal(shelf.find((b) => b.title === 'Sourdough bread')?.coverMotif, 'wheat', 'a preface in progress gets a motif from its title');
ok('four books with their prefaces done, each with the motif picked for it, and one still in its preface');

const b = await connect({ outDir: OUT });
const { send, evaluate, hasText, waitFor, click, problems, shot, sleep } = b;
const MATERIAL_OF = `(e) => { const t = [...e.querySelectorAll('div')].map((d) => getComputedStyle(d).backgroundImage).find((u) => /leather|linen/.test(u));
  return t ? (t.includes('leather') ? 'leather' : 'linen') : null; }`;
const coverOf = (title) => `[...document.querySelectorAll('[role="button"]')].find((e) => (e.getAttribute('aria-label') ?? '').startsWith(${JSON.stringify(title + ',')}))`;
const order = () => evaluate(`[...document.querySelectorAll('[role="button"][aria-label*=", volume "]')]
  .map((e) => ({ t: e.getAttribute('aria-label').split(',')[0], r: e.getBoundingClientRect() }))
  .sort((a, b) => (Math.round(a.r.y / 40) - Math.round(b.r.y / 40)) || (a.r.x - b.r.x)).map((x) => x.t)`); // rows first (a cover's
// lift or glide can leave it a pixel higher than its neighbour), then left to right

try {
  await send('Storage.clearDataForOrigin', { origin: new URL(APP).origin, storageTypes: 'all' });
  await send('Page.navigate', { url: APP });
  await waitFor('title page', `!!document.querySelector('[aria-label="Developer sign-in"]')`, 20000);
  await click('Developer sign-in');
  await b.type('Email', EMAIL);
  await click('Sign in without a password');
  await waitFor('the library', hasText('Your library'));
  await waitFor('the covers', `!!(${coverOf('The Night Sky')})`);

  // The mockup's page: the masthead, the title and its line, the settings icon, and one dominant action.
  assert.ok(await evaluate(hasText('Learn Anything Anywhere')), 'the masthead');
  assert.ok(await evaluate(hasText('A little more curious, every day.')), 'the line under the title');
  assert.ok(await evaluate(`!!document.querySelector('[aria-label="Settings and account"]')`), 'the settings icon');
  assert.ok(await evaluate(`!!document.querySelector('[aria-label="Create a textbook"]')`), 'Create a textbook');
  // The type is the brief's: Source Serif 4 for the title, Inter for the controls.
  const fontsUsed = await evaluate(`(() => {
    const title = [...document.querySelectorAll('[role="heading"]')].find((e) => e.textContent === 'Your library');
    const button = document.querySelector('[aria-label="Create a textbook"] div');
    return { title: getComputedStyle(title).fontFamily, button: getComputedStyle(button).fontFamily, ready: document.fonts.status };
  })()`);
  assert.match(fontsUsed.title, /SourceSerif4/, 'the title is in Source Serif 4');
  assert.match(fontsUsed.button, /Inter/, 'controls are in Inter');
  ok('the library page: masthead, "Your library", the settings icon, "Create a textbook", Source Serif 4 and Inter');

  // Each cover is drawn: cloth, texture, title and a motif drawing; two to a row on a phone.
  const covers = await evaluate(`[...document.querySelectorAll('[role="button"][aria-label*=", volume "]')].map((e) => ({
    label: e.getAttribute('aria-label'),
    svgs: e.querySelectorAll('svg').length,
    shapes: e.querySelector('svg[viewBox="0 0 100 100"]')?.querySelectorAll('path, circle, ellipse, line, rect, polyline, polygon').length ?? 0,
    w: Math.round(e.getBoundingClientRect().width),
  }))`);
  assert.equal(covers.length, 5);
  for (const c of covers) {
    assert.ok(c.svgs >= 2, `${c.label}: the cloth and the motif are drawn`);
    assert.ok(c.shapes >= 2, `${c.label}: the motif has its lines`);
  }
  assert.ok(covers[0].w > 150 && covers[0].w < 200, `two covers to a row at phone width (cover ${covers[0].w}px)`);
  // Bound by colour: forest green and terracotta in leather, ochre and dusty blue in linen (volumes I to IV, in turn).
  const bound = await evaluate(`(() => { const of = ${MATERIAL_OF};
    return Object.fromEntries([...document.querySelectorAll('[role="button"][aria-label*=", volume "]')].map((e) => [e.getAttribute('aria-label').split(',')[0], of(e)])); })()`);
  assert.deepEqual(bound, { 'The Night Sky': 'leather', 'A Little History of Art': 'linen', 'How Computers Think': 'linen',
    'The World of Plants': 'leather', 'Sourdough bread': 'leather' }, 'each cover is bound in its material');
  assert.ok(await evaluate(hasText('Preface in progress')), 'the book mid-preface says so');
  // No word in a title is split across two lines ("Computer / s Think"): each word's characters share one line.
  const split = await evaluate(`[...document.querySelectorAll('[role="button"][aria-label*=", volume "]')].flatMap((e) => {
    const t = [...e.querySelectorAll('div')].find((d) => d.childElementCount === 0 && e.getAttribute('aria-label').startsWith(d.textContent + ','));
    if (!t) return [];
    const range = document.createRange(); const node = t.firstChild; const out = [];
    let start = 0;
    for (const word of t.textContent.split(' ')) {
      range.setStart(node, start); range.setEnd(node, start + word.length);
      if (range.getClientRects().length > 1) out.push(word);
      start += word.length + 1;
    }
    return out; })`);
  assert.deepEqual(split, [], 'no title word breaks across lines');
  await shot('library-covers');
  ok('five drawn covers in leather or linen, two to a row, each with its motif; the one mid-preface marked');

  // Spines: the same books standing on a fine ledge.
  await click('Spines');
  await waitFor('spines', `[...document.querySelectorAll('[role="button"][aria-label*=", volume "]')].every((e) => e.getBoundingClientRect().width < 70)`);
  await sleep(700); // let the books finish gliding into place
  await shot('library-spines');
  await click('Covers');
  await waitFor('covers again', `[...document.querySelectorAll('[role="button"][aria-label*=", volume "]')].every((e) => e.getBoundingClientRect().width > 100)`);
  ok('spines and back to covers');

  // There's no Arrange mode any more (the owner's choice): a book is dragged to its place, and the server keeps it.
  assert.equal(await evaluate(`!!document.querySelector('[aria-label="Arrange your library"]')`), false, 'no Arrange button');
  const before = await order();
  const centre = (title) => evaluate(`(() => { const r = ${coverOf(title)}.getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height * 0.4 }; })()`);
  const from = await centre(before[0]);
  const to = await centre(before[1]);
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: from.x, y: from.y });
  await send('Input.dispatchMouseEvent', { type: 'mousePressed', x: from.x, y: from.y, button: 'left', clickCount: 1 });
  for (let i = 1; i <= 12; i++) {
    await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: from.x + ((to.x - from.x) * i) / 12, y: from.y + ((to.y - from.y) * i) / 12, button: 'left', buttons: 1 });
    await sleep(30);
  }
  await send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: to.x, y: to.y, button: 'left', clickCount: 1 });
  let after = await order();
  for (let t = 0; t < 30 && after[0] === before[0]; t++, await sleep(100)) after = await order();
  assert.deepEqual(after.slice(0, 2), [before[1], before[0]], `the first two books swapped: before ${before}, after ${after}`);
  let saved = [];
  for (let t = 0; t < 30 && saved[0] !== before[1]; t++, await sleep(100)) saved = (await learner.call('GET', '/textbooks').then((r) => r.json())).map((x) => x.title);
  assert.deepEqual(saved.slice(0, 2), [before[1], before[0]], 'the server kept the new order');
  assert.ok(await evaluate(hasText('Your library')) && !(await evaluate(hasText('Inside this book'))), 'a drag never opens the book');
  ok('dragging a book moves it, the order is kept, and no Arrange button is needed');

  // A book opens its page; the settings icon opens settings, where signing out now lives.
  await click('Settings and account');
  await waitFor('settings', hasText('Sign out'));
  ok('the settings icon leads to settings, with Sign out');

  assert.deepEqual(problems, [], 'no console errors or warnings');
  ok('no console errors or warnings');

  // Signing out from settings, then a new learner's empty library, which invites a first topic.
  await click(`Sign out ${EMAIL}`);
  await waitFor('title page again', `!!document.querySelector('[aria-label="Developer sign-in"]')`, 20000);
  await click('Developer sign-in');
  await b.type('Email', `library-empty-${Date.now()}@laa.dev`);
  await click('Sign in without a password');
  await waitFor('empty library', hasText('waiting for its first book'), 20000);
  assert.ok(await evaluate(`!!document.querySelector('[aria-label="Start a textbook about Bouldering"]')`), 'a starter topic');
  await shot('library-empty');
  ok('an empty library invites a first topic');
} catch (e) {
  await shot('FAILED');
  console.error('✗ ' + (e.stack ?? e));
  process.exitCode = 1;
} finally {
  b.ws.close();
}

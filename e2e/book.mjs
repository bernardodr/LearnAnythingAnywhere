// Starting a book and its page, after facelift milestone 2 (docs/DESIGN.md, issue #69), in the real web app: the new
// book's cover taking shape as the topic is typed; the preface (an opening, answers as notes in the margin, the mission
// card, editing it, "Write my textbook" pinned at the bottom); then the book's page (the drawn cover, the contents with
// the lesson up next, the why in the margin, the ••• menu moving the book in the library, the back of the book
// folding open, adding a source), reading lesson 1, and writing lesson 2. Run it with `./e2e/run.sh book [screenshot dir]` (a scratch database in practice mode, no AI
// keys). Exits non-zero on any failure.
import assert from 'node:assert/strict';

import { connect } from './lib/cdp.mjs';
import { bookWithMission, signIn } from './lib/lesson.mjs';

const API = process.env.API ?? 'http://localhost:8090';
const APP = process.env.APP ?? 'http://localhost:8099/';
const OUT = process.argv[2];
const EMAIL = `book-${Date.now()}@laa.dev`;
const ok = (s) => console.log('✓ ' + s);

// Two books already in the library, so the new one is volume III and gets the third cloth (dusty blue).
const learner = await signIn(API, EMAIL);
await bookWithMission(learner, 'The Night Sky');
await bookWithMission(learner, 'A Little History of Art');

const b = await connect({ outDir: OUT });
const { send, evaluate, hasText, waitFor, click, problems, shot, sleep } = b;
const exists = (label) => `!!document.querySelector('[aria-label=${JSON.stringify(label)}]')`;
// The pinned footer holds the screen's one dominant action: a button outside the page's scrolling area. Only the
// screen on top counts (the web app keeps earlier screens of the stack mounted underneath).
const footerButton = () => evaluate(`(() => {
  const pinned = [...document.querySelectorAll('[role="button"]')].filter((e) => {
    for (let p = e.parentElement; p; p = p.parentElement) if (/(auto|scroll)/.test(getComputedStyle(p).overflowY)) return false;
    const r = e.getBoundingClientRect();
    return r.width > 0 && e.contains(document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2));
  });
  return pinned.length === 1 ? { label: pinned[0].getAttribute('aria-label'),
    bottom: Math.round(innerHeight - pinned[0].getBoundingClientRect().bottom) } : pinned.length ? 'several' : null; })()`);

try {
  await send('Storage.clearDataForOrigin', { origin: new URL(APP).origin, storageTypes: 'all' });
  await send('Page.navigate', { url: APP });
  await waitFor('title page', exists('Developer sign-in'), 20000);
  await click('Developer sign-in');
  await b.type('Email', EMAIL);
  await click('Sign in without a password');
  await waitFor('the library', hasText('Your library'));

  // ---- Starting a book: an empty place for the cover, which takes shape as the topic is typed.
  await click('Create a textbook');
  await waitFor('new book', hasText('What would you like to learn?'));
  await waitFor('the volume', hasText('Volume III'));
  assert.ok(await evaluate(hasText('Your new book’s cover appears here as you type.')), 'an empty place for the cover');
  await b.type('Topic', 'the world of plants');
  await waitFor('the preview cover', hasText('The world of plants'));
  const preview = await evaluate(`(() => {
    const title = [...document.querySelectorAll('div')].find((d) => d.childElementCount === 0 && d.textContent === 'The world of plants');
    let face = title; while (face && getComputedStyle(face).backgroundColor === 'rgba(0, 0, 0, 0)') face = face.parentElement;
    return { cloth: getComputedStyle(face).backgroundColor, drawn: face.querySelectorAll('svg').length };
  })()`);
  assert.equal(preview.cloth, 'rgb(70, 102, 138)', 'volume III previews in dusty blue, the cloth the server will give it');
  assert.ok(preview.drawn >= 2, 'the preview is a drawn cover (cloth and the ornament)');
  assert.ok(await evaluate(hasText('Its cover drawing is chosen at the end of the preface.')), 'the drawing comes later');
  assert.equal((await footerButton())?.label, 'Begin the preface', '"Begin the preface" is pinned at the bottom');
  await sleep(300);
  await shot('new-book');
  ok('a new book: volume III, its cover previewed in its own cloth as the topic is typed');

  // ---- The preface: an opening, the tutor's questions, the learner's answers as notes in the margin.
  await click('Begin the preface');
  await waitFor('the preface', hasText('Before we begin'));
  await waitFor('the first question', `document.querySelectorAll('[aria-label="Your answer"]').length === 1`, 20000);
  assert.ok(await evaluate(hasText('The world of plants')), 'the book’s title is in the bar');
  assert.equal((await footerButton())?.label, 'Send your answer', 'Send is pinned at the bottom');
  await sleep(300);
  await shot('preface');
  await b.type('Your answer', 'I want to keep the plants on my balcony alive and know what they need.');
  await click('Send your answer');
  await waitFor('the second question', `document.querySelectorAll('[aria-label="Your answer"]').length === 1 && ${hasText('Question 2 of up to 5')}`, 20000);
  // The answer sits in the margin: a green rule on its left, a "You" kicker, no box.
  const margin = await evaluate(`(() => {
    const t = [...document.querySelectorAll('div')].find((d) => d.childElementCount === 0 && d.textContent.startsWith('I want to keep the plants'));
    const note = t.parentElement; const s = getComputedStyle(note);
    return { rule: s.borderLeftWidth, colour: s.borderLeftColor, fill: s.backgroundColor, kicker: note.firstElementChild.textContent };
  })()`);
  assert.deepEqual(margin, { rule: '2px', colour: 'rgb(56, 78, 65)', fill: 'rgba(0, 0, 0, 0)', kicker: 'You' }, 'an answer is a note in the margin');
  await b.type('Your answer', 'Twenty minutes at weekends.');
  await click('Send your answer');
  await waitFor('the mission', exists('Your mission'), 20000);
  assert.equal((await footerButton())?.label, 'Write my textbook', '"Write my textbook" is pinned at the bottom');
  await sleep(400);
  await shot('preface-mission');
  ok('the preface: an opening, answers in the margin, then the mission with "Write my textbook" at the bottom');

  // Editing the mission: the form replaces the card (and the pinned action steps aside until it's done).
  await click('Edit your mission');
  await waitFor('the edit form', exists('Your goal in a line'));
  assert.equal(await footerButton(), null, 'no pinned action while editing');
  await evaluate(`(() => { const i = document.querySelector('[aria-label="Your goal in a line"]');
    i.focus(); i.select(); })()`);
  await send('Input.insertText', { text: 'Keep my balcony plants thriving' });
  await click('Done');
  await waitFor('the edited mission', hasText('Keep my balcony plants thriving'));
  await click('Write my textbook');

  // ---- The book's page: the cover, the title, the goal, the facts; lesson 1 written straight away.
  await waitFor('the book page', hasText('Inside this book'), 20000);
  const id = await evaluate(`location.pathname.split('/')[2]`);
  await waitFor('lesson 1 written', exists('Start reading'), 30000);
  assert.ok(await evaluate(hasText('Keep my balcony plants thriving')), 'the edited goal under the title');
  assert.ok(await evaluate(hasText('Volume III · 1 lesson · 0 read')), 'the facts line');
  assert.ok(await evaluate(`!![...document.querySelectorAll('[role="button"]')].find((e) => /^Lesson 1: .*Up next$/.test(e.getAttribute('aria-label') ?? ''))`),
    'lesson 1 is up next');
  assert.ok(await evaluate(hasText('Written after lesson 1')), 'the next lesson waits for this one');
  assert.ok(await evaluate(hasText('Why you’re learning this')), 'the why, in the margin');
  const cover = await evaluate(`[...document.querySelectorAll('svg')].length`);
  assert.ok(cover >= 3, 'the cover is drawn on the page');
  const contents = await learner.call('GET', `/textbooks/${id}/contents`).then((r) => r.json());
  assert.equal(contents.missionHeadline, 'Keep my balcony plants thriving', 'the server kept the edited mission');
  assert.equal(contents.coverMotif, 'leaf', 'the AI picked the leaf for plants');
  assert.equal(contents.coverColor, '#46668A', 'and the cloth previewed');
  assert.equal((await footerButton())?.label, 'Start reading', '"Start reading" is pinned at the bottom');
  await sleep(400);
  await shot('book-page');
  ok('the book page: drawn cover, edited goal, facts, lesson 1 up next, the why in the margin, "Start reading"');

  // ---- The book's ••• menu moves it in the library without dragging. It's the third of three, so it can't go later.
  await click('This book’s menu');
  await waitFor('the menu', hasText('Book 3 of 3 in your library'));
  assert.equal(await evaluate(`document.querySelector('[aria-label="Move later in your library"]').getAttribute('aria-disabled')`), 'true',
    'the last book can’t move later');
  await click('Move earlier in your library');
  await waitFor('moved', hasText('Book 2 of 3 in your library'));
  let order = [];
  for (let t = 0; t < 30 && order[1] !== 'The world of plants'; t++, await sleep(100)) {
    order = (await learner.call('GET', '/textbooks').then((r) => r.json())).map((x) => x.title);
  }
  assert.deepEqual(order, ['The Night Sky', 'The world of plants', 'A Little History of Art'], 'the server kept the move');
  await shot('book-menu');
  await click('Close this book’s menu');
  ok('the book’s ••• menu moves it earlier in the library (and knows it can’t go later from the end)');

  // ---- The back of the book folds open; sources can be added from inside.
  await click('A, Reference sheets: 1');
  await waitFor('a reference sheet', `!!document.querySelector('[aria-label^="Reference sheet from lesson 1"]')`);
  await click('B, Glossary: none yet');
  await waitFor('the empty glossary', hasText('Terms appear here once you’ve shown you understand them.'));
  await click('D, Sources: none yet');
  await click('Add a source');
  await b.type('Title', 'RHS: Houseplant care');
  await b.type('Web address', 'https://www.rhs.org.uk/plants/types/houseplants');
  await click('Add source');
  await waitFor('the source added', exists('D, Sources: 1'));
  await sleep(300);
  await shot('book-page-back');
  ok('the back of the book: sections fold open with their counts, and a source can be added');

  // ---- Reading lesson 1, then back to the page, which now offers lesson 2.
  await click('Start reading');
  await waitFor('lesson 1', hasText('Your win today'), 20000);
  await click('I’ve finished this lesson');
  await waitFor('back on the book page', exists('Write lesson 2'), 20000);
  assert.ok(await evaluate(hasText('Volume III · 1 lesson · all read')), 'the facts line counts it read');
  assert.ok(await evaluate(`!![...document.querySelectorAll('[role="button"]')].find((e) => /^Lesson 1: .*Read$/.test(e.getAttribute('aria-label') ?? ''))`),
    'lesson 1 is marked read');
  await click('Write lesson 2');
  await waitFor('lesson 2 written', exists('Continue reading'), 30000);
  ok('lesson 1 read and ticked; "Write lesson 2" at the bottom writes it; then "Continue reading"');

  // ---- Back to the library, where the new book stands with its leaf.
  await click('Back to your library');
  await waitFor('the library', `!!document.querySelector('[aria-label^="The world of plants, volume 3"]')`);
  ok('back in the library, the new book is on it, in the place the menu moved it to');

  assert.deepEqual(problems, [], 'no console errors or warnings');
  ok('no console errors or warnings');
} catch (e) {
  await shot('FAILED');
  console.error('✗ ' + (e.stack ?? e));
  process.exitCode = 1;
} finally {
  b.ws.close();
}

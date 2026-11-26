import { colors, scheme } from '@/theme';

/**
 * The sealed document an interactive diagram runs inside, shared by the web frame (`DiagramFrame.web.tsx`) and the
 * phone frame (`DiagramFrame.tsx`).
 *
 * A diagram's page is written by an AI (server: `lesson/DiagramDraft`, checked by `DiagramRules` and the safety check
 * before it's stored). This document is what makes it safe to run whatever it contains:
 *
 * - **No network.** The Content-Security-Policy allows only the page's own inline script and style, and images or
 *   fonts written into it as `data:`. Nothing can be loaded or sent anywhere.
 * - **No access to the app.** The frame around it (a sandboxed iframe on the web, a web view on phones) gives the page
 *   an origin of its own, so it can't read the sign-in token, cookies or anything else of LAA's.
 * - **No leaving.** No links, pop-ups, forms or navigation: the frames refuse them, and `window.open` does nothing.
 *
 * It also gives the page LAA's look (the CSS variables and `laa-button` the diagram prompt promises), a contrast guard
 * that keeps every word readable whatever colours the page's own CSS picks, and a tiny bridge that tells the app two
 * things only: how tall the page is, and whether its script failed (then the app shows the text version instead). The
 * app accepts nothing else from it.
 */

/** A message from a diagram's frame. Anything that isn't exactly one of these is ignored. */
export type DiagramMessage = { laa: 'diagram'; kind: 'height'; value: number } | { laa: 'diagram'; kind: 'error' };

/** Reads a message from a frame, or null if it isn't one the app understands. */
export function readDiagramMessage(data: unknown): DiagramMessage | null {
  const m = typeof data === 'string' ? safeParse(data) : data;
  if (!m || typeof m !== 'object' || (m as { laa?: unknown }).laa !== 'diagram') return null;
  const { kind, value } = m as { kind?: unknown; value?: unknown };
  if (kind === 'error') return { laa: 'diagram', kind: 'error' };
  if (kind === 'height' && typeof value === 'number' && Number.isFinite(value)) {
    return { laa: 'diagram', kind: 'height', value };
  }
  return null;
}

/** The frame's height, kept between a sensible minimum and a maximum so a page can't take over the lesson. */
export function frameHeight(reported: number) {
  return Math.min(Math.max(Math.ceil(reported), 120), 1600);
}

const CSP = [
  "default-src 'none'",
  "script-src 'unsafe-inline'",
  "style-src 'unsafe-inline'",
  'img-src data:',
  'font-src data:',
  "connect-src 'none'",
  "media-src 'none'",
  "frame-src 'none'",
  "form-action 'none'",
  "base-uri 'none'",
].join('; ');

// The bridge runs before the diagram's own script. It reports the page's height whenever it changes, and any error.
// `ReactNativeWebView` exists only inside the phone's web view; on the web, the parent window listens instead.
const BRIDGE = `
(function () {
  function send(message) {
    try {
      if (window.ReactNativeWebView) window.ReactNativeWebView.postMessage(JSON.stringify(message));
      else window.parent.postMessage(message, '*');
    } catch (e) {}
  }
  window.addEventListener('error', function () { send({ laa: 'diagram', kind: 'error' }); });
  function report() { send({ laa: 'diagram', kind: 'height', value: document.documentElement.scrollHeight }); }
  window.addEventListener('load', report);
  if (window.ResizeObserver) new ResizeObserver(report).observe(document.documentElement);
  window.open = function () { return null; };
})();
`;

/** "#292823" → "41, 40, 35", for the guard's colour sums. */
function rgb(hex: string) {
  return [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16)).join(', ');
}

// The contrast guard (issue #78). A diagram's own CSS can undo half of a pair: the owner saw a chosen button whose
// page had given it a pale fill while LAA's style still gave it ivory text. So whenever the page changes (a class, a
// style, a pressed state, new text; checked at most every 120ms, and again when a transition ends), every element with
// text of its own is measured against the first real fill behind it. Below 4.5:1 (WCAG AA), it's marked to take ink
// or ivory, whichever reads better there; a mark is switched again if its fill changes later. HTML text only: text
// drawn in SVG sits on shapes the guard can't see behind. It runs inside the sealed frame and changes nothing about
// the seal (it sends nothing, loads nothing).
/** The guard's script, for the palette in use (its ink and ivory are the current ones). */
function guard() {
  return `
(function () {
  var INK = [${rgb(colors.ink)}], PAPER = [${rgb(colors.paper)}];
  function parse(s) {
    var m = /rgba?\\(([^)]+)\\)/.exec(s || '');
    if (!m) return null;
    var p = m[1].split(/[\\s,\\/]+/).filter(Boolean).map(parseFloat);
    return [p[0], p[1], p[2], p.length > 3 ? p[3] : 1];
  }
  function lum(c) {
    function ch(v) { v = v / 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }
    return 0.2126 * ch(c[0]) + 0.7152 * ch(c[1]) + 0.0722 * ch(c[2]);
  }
  function contrast(a, b) { var x = lum(a), y = lum(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); }
  function behind(el) {
    for (var e = el; e && e.nodeType === 1; e = e.parentElement) {
      var c = parse(getComputedStyle(e).backgroundColor);
      if (c && c[3] >= 0.5) return c;
    }
    return PAPER;
  }
  function ownText(el) {
    for (var n = el.firstChild; n; n = n.nextSibling) if (n.nodeType === 3 && n.nodeValue.trim()) return true;
    return false;
  }
  function sweep() {
    if (!document.body) return;
    var all = document.body.getElementsByTagName('*');
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (el.namespaceURI !== 'http://www.w3.org/1999/xhtml' || !ownText(el)) continue;
      var fg = parse(getComputedStyle(el).color);
      if (!fg) continue;
      var bg = behind(el);
      if (contrast(fg, bg) >= 4.5) continue;
      el.setAttribute('data-laa-text', contrast(INK, bg) >= contrast(PAPER, bg) ? 'ink' : 'paper');
    }
  }
  var timer = null;
  function soon() { if (!timer) timer = setTimeout(function () { timer = null; sweep(); }, 120); }
  // Its own mark (data-laa-text) isn't watched, so a sweep never sets off another.
  var watch = new MutationObserver(soon);
  document.addEventListener('DOMContentLoaded', function () {
    sweep();
    watch.observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true,
      attributeFilter: ['class', 'style', 'hidden', 'disabled', 'aria-pressed', 'aria-selected', 'aria-checked', 'aria-current'] });
  });
  window.addEventListener('load', sweep);
  document.addEventListener('transitionend', soon, true);
})();
`;
}

// LAA's design tokens (theme.ts), as the CSS variables the diagram prompt names. The older names (--slate, --olive,
// --navy) stay, because diagrams already written use them; they now draw in DESIGN.md's palette. Fonts are system
// ones: the page can't load LAA's own (no network), and these sit closest to Source Serif 4 and Inter.
/** LAA's look for the frame, in the palette in use: in dark mode the variables are the dark ones. */
function style() {
  return `
:root {
  color-scheme: ${scheme()};
  --paper: ${colors.paper}; --ink: ${colors.ink}; --muted: ${colors.muted}; --rule: ${colors.rule};
  --note: ${colors.noteFill}; --accent: ${colors.accent}; --ribbon: ${colors.ribbon};
  --forest: ${colors.covers.forest}; --ochre: ${colors.covers.ochre}; --blue: ${colors.covers.blue}; --terracotta: ${colors.covers.terracotta};
  --slate: ${colors.covers.blue}; --olive: ${colors.covers.forest}; --navy: ${colors.covers.blue};
  --serif: 'Iowan Old Style', 'Palatino Linotype', Palatino, Georgia, serif;
  --sans: system-ui, -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
}
*, *::before, *::after { box-sizing: border-box; }
html, body { margin: 0; padding: 0; background: var(--paper); color: var(--ink); }
body { font: 16px/1.5 var(--serif); padding: 4px 2px 8px; overflow-wrap: anywhere; -webkit-text-size-adjust: 100%; }
svg { max-width: 100%; height: auto; }
.laa-button {
  min-height: 44px; min-width: 44px; padding: 8px 16px; border-radius: 10px; cursor: pointer;
  font: 600 15px var(--sans); color: var(--accent); background: var(--paper); border: 1.5px solid var(--accent);
}
.laa-button[aria-pressed="true"], .laa-button:active { color: var(--paper); background: var(--accent); }
.laa-button:focus-visible, [tabindex]:focus-visible { outline: 3px solid var(--ribbon); outline-offset: 2px; }
[data-laa-text="ink"] { color: var(--ink) !important; }
[data-laa-text="paper"] { color: var(--paper) !important; }
@media (prefers-reduced-motion: reduce) { * { transition: none !important; animation: none !important; } }
`;
}

/** The whole document for one diagram: the seal, LAA's look, the bridge and the contrast guard, around the page. */
export function diagramDocument(html: string) {
  return (
    '<!DOCTYPE html><html lang="en"><head><meta charset="utf-8">' +
    `<meta http-equiv="Content-Security-Policy" content="${CSP}">` +
    '<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">' +
    `<style>${style()}</style><script>${BRIDGE}${guard()}</script></head><body>${html}</body></html>`
  );
}

function safeParse(s: string): unknown {
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
}

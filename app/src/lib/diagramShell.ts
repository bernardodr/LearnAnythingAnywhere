import { colors } from '@/theme';

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
 * It also gives the page LAA's look (the CSS variables and `laa-button` the diagram prompt promises), and a tiny
 * bridge that tells the app two things only: how tall the page is, and whether its script failed (then the app shows
 * the text version instead). The app accepts nothing else from it.
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

// LAA's design tokens (theme.ts), as the CSS variables the diagram prompt names. The older names (--slate, --olive,
// --navy) stay, because diagrams already written use them; they now draw in DESIGN.md's palette. Fonts are system
// ones: the page can't load LAA's own (no network), and these sit closest to Source Serif 4 and Inter.
const STYLE = `
:root {
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
@media (prefers-reduced-motion: reduce) { * { transition: none !important; animation: none !important; } }
`;

/** The whole document for one diagram: the seal, LAA's look and the bridge, around the diagram's own page. */
export function diagramDocument(html: string) {
  return (
    '<!DOCTYPE html><html lang="en"><head><meta charset="utf-8">' +
    `<meta http-equiv="Content-Security-Policy" content="${CSP}">` +
    '<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">' +
    `<style>${STYLE}</style><script>${BRIDGE}</script></head><body>${html}</body></html>`
  );
}

function safeParse(s: string): unknown {
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
}

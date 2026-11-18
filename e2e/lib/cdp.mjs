// Drives headless Chrome over the DevTools protocol, with Node's built-in WebSocket: no test framework, no packages.
// Besides the page, it attaches to sandboxed frames (each runs in a process of its own, so it's its own target) and
// can run code inside the newest one: that's how a test plays a hostile diagram page.
import { writeFileSync } from 'node:fs';

export async function connect({ port = 9333, outDir }) {
  const targets = await (await fetch(`http://localhost:${port}/json/list`)).json();
  const page = targets.find((t) => t.type === 'page');
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((r) => ws.addEventListener('open', r, { once: true }));

  let nextId = 1;
  const pending = new Map();
  const problems = [];
  let frameSession = null;
  ws.addEventListener('message', (ev) => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id);
      pending.delete(msg.id);
      msg.error ? reject(new Error(msg.error.message)) : resolve(msg.result);
    } else if (msg.method === 'Target.attachedToTarget' && msg.params.targetInfo.type === 'iframe') {
      frameSession = msg.params.sessionId;
    } else if (!msg.sessionId && msg.method === 'Runtime.consoleAPICalled' && ['error', 'warning'].includes(msg.params.type)) {
      problems.push(`console.${msg.params.type}: ` + msg.params.args.map((a) => a.value ?? a.description).join(' '));
    } else if (!msg.sessionId && msg.method === 'Runtime.exceptionThrown') {
      problems.push('exception: ' + msg.params.exceptionDetails.exception?.description);
    }
  });
  const command = (method, params = {}, sessionId) =>
    new Promise((resolve, reject) => {
      const id = nextId++;
      pending.set(id, { resolve, reject });
      ws.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }));
    });
  const send = (method, params) => command(method, params);
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const evaluate = async (expr) => (await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })).result.value;
  const hasText = (t) => `document.body.innerText.toLowerCase().includes(${JSON.stringify(t.toLowerCase())})`;
  const byLabel = (label) => `document.querySelector('[aria-label=${JSON.stringify(label)}]')`;

  async function shot(name) {
    if (!outDir) return;
    const { data } = await send('Page.captureScreenshot', { format: 'png' });
    writeFileSync(`${outDir}/${name}.png`, Buffer.from(data, 'base64'));
  }
  async function waitFor(desc, expr, timeout = 15000) {
    const end = Date.now() + timeout;
    while (Date.now() < end) {
      if (await evaluate(expr)) return;
      await sleep(100);
    }
    await shot('FAILED-' + desc.replace(/\W+/g, '-'));
    throw new Error(`Timed out waiting for: ${desc}`);
  }
  async function clickAt(x, y) {
    // Move first and give Chrome a moment to route the mouse: over a frame, that's another process.
    await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y });
    await sleep(80);
    for (const type of ['mousePressed', 'mouseReleased']) {
      await send('Input.dispatchMouseEvent', { type, x, y, button: 'left', clickCount: 1 });
    }
    await sleep(150);
  }
  async function click(label) {
    await waitFor(`element "${label}"`, `!!${byLabel(label)}`);
    const box = await evaluate(`(() => { const e = ${byLabel(label)}; e.scrollIntoView({block: 'center'});
      const r = e.getBoundingClientRect(); return { x: r.x + r.width / 2, y: r.y + r.height / 2 }; })()`);
    // After a press inside a frame (another process), Chrome keeps routing the mouse there until it moves over the page.
    await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: 1, y: 1 });
    await clickAt(box.x, box.y);
  }
  async function type(label, text) {
    await click(label);
    await send('Input.insertText', { text });
    await sleep(100);
  }

  /** Waits for a sandboxed frame to attach (a new one after a reload), then returns a way to run code inside it. */
  async function frame(timeout = 10000) {
    const end = Date.now() + timeout;
    while (!frameSession && Date.now() < end) await sleep(50);
    if (!frameSession) throw new Error('no sandboxed frame attached');
    const session = frameSession;
    return async (expr) => {
      // allowUnsafeEvalBlockedByCSP defaults to true, which would let this code use eval where the page itself can't.
      const r = await command('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true,
        allowUnsafeEvalBlockedByCSP: false }, session);
      if (r.exceptionDetails) return 'threw: ' + (r.exceptionDetails.exception?.description ?? r.exceptionDetails.text).split('\n')[0];
      return r.result.value;
    };
  }
  const forgetFrame = () => { frameSession = null; };

  await send('Runtime.enable');
  await send('Page.enable');
  await send('Target.setAutoAttach', { autoAttach: true, waitForDebuggerOnStart: false, flatten: true });
  await send('Emulation.setDeviceMetricsOverride', { width: 420, height: 900, deviceScaleFactor: 2, mobile: true });
  return { ws, send, evaluate, hasText, byLabel, shot, waitFor, click, clickAt, type, frame, forgetFrame, problems, sleep };
}

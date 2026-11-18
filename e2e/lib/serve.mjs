// Serves the exported web app (a single page: real files as they are, every other path as index.html), plus a
// "canary": any request to /__canary/<name> is counted, so a test can prove nothing reached it.
//   node e2e/lib/serve.mjs <dist dir> <port>
// GET /__canary-hits answers the counts as JSON (without counting itself).
import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';

const [root, port] = [process.argv[2], Number(process.argv[3])];
const types = {
  '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png',
  '.ttf': 'font/ttf', '.ico': 'image/x-icon', '.svg': 'image/svg+xml',
};
const hits = {};

createServer(async (req, res) => {
  const path = normalize(decodeURIComponent(new URL(req.url, 'http://x').pathname)).replace(/^(\.\.[/\\])+/, '');
  if (path === '/__canary-hits') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify(hits));
  }
  if (path.startsWith('/__canary/')) {
    const name = path.slice('/__canary/'.length);
    hits[name] = (hits[name] ?? 0) + 1;
    res.writeHead(200, { 'Content-Type': 'text/plain', 'Access-Control-Allow-Origin': '*' });
    return res.end('reached');
  }
  let file = join(root, path);
  try {
    if (!(await stat(file)).isFile()) throw new Error('not a file');
  } catch {
    file = join(root, 'index.html');
  }
  res.writeHead(200, { 'Content-Type': types[extname(file)] ?? 'application/octet-stream' });
  res.end(await readFile(file));
}).listen(port, () => console.log(`serving ${root} on ${port}`));

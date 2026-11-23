// Static server for the e2e run: serves the production build with an SPA fallback to index.html.
// Angular CLI has no `preview` command, and this keeps the e2e setup free of extra dependencies.
// Usage: node e2e/serve.mjs --port 4300 (run from frontend/, after `npm run build`).
import { createReadStream, existsSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, resolve, sep } from 'node:path';

const root = resolve('dist/itcardbrawl/browser');
const portFlag = process.argv.indexOf('--port');
const port = Number(portFlag === -1 ? (process.env.E2E_PORT ?? 4300) : process.argv[portFlag + 1]);

if (!existsSync(join(root, 'index.html'))) {
  console.error(`No production build in ${root} - run "npm run build" first.`);
  process.exit(1);
}

const types = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json',
  '.svg': 'image/svg+xml',
  '.webp': 'image/webp',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain; charset=utf-8',
};

function send(res, status, file) {
  res.writeHead(status, { 'Content-Type': types[extname(file)] ?? 'application/octet-stream' });
  createReadStream(file).pipe(res);
}

createServer((req, res) => {
  const path = decodeURIComponent(new URL(req.url ?? '/', 'http://localhost').pathname);
  // The API is mocked in the browser (page.route); a request that reaches this server was not mocked.
  if (path === '/api' || path.startsWith('/api/') || path.startsWith('/oauth2/')) {
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end('{"error":"not mocked in e2e"}');
    return;
  }
  const file = resolve(root, `.${path}`);
  if (file !== root && !file.startsWith(root + sep)) {
    res.writeHead(403);
    res.end();
    return;
  }
  if (existsSync(file) && statSync(file).isFile()) {
    send(res, 200, file);
  } else if (extname(path) === '') {
    // An Angular route (e.g. /match): the SPA handles it.
    send(res, 200, join(root, 'index.html'));
  } else {
    res.writeHead(404);
    res.end();
  }
}).listen(port, () => console.log(`e2e: serving ${root} on http://localhost:${port}`));

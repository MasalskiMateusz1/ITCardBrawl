// Renders selected card WebPs at their full 440x252 size, two per row, into one PNG for inspection.
// Usage (from frontend/): node tools/card-art/preview.mjs [--out file.png] id id ...
// Default output: <os tmpdir>/card-art/preview.png (the path is printed).
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const FRONTEND = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const require = createRequire(join(FRONTEND, 'package.json'));
const { chromium } = require('@playwright/test');

const args = process.argv.slice(2);
const outIndex = args.indexOf('--out');
const out = resolve(outIndex >= 0 ? args[outIndex + 1] : join(tmpdir(), 'card-art', 'preview.png'));
const ids = outIndex >= 0 ? args.filter((_, i) => i !== outIndex && i !== outIndex + 1) : args;
if (!ids.length) throw new Error('usage: preview.mjs [--out file.png] id id ...');
mkdirSync(dirname(out), { recursive: true });

const img = (id) =>
  `<img src="data:image/webp;base64,${readFileSync(join(FRONTEND, 'public/media/cards', `${id}.webp`)).toString('base64')}">`;
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 900, height: 600 } });
await page.setContent(
  `<body style="margin:0;background:#000;display:grid;grid-template-columns:440px 440px;gap:4px">${ids.map(img).join('')}</body>`,
);
await page.screenshot({ path: out, fullPage: true });
await browser.close();
console.log('preview:', out);

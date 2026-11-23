// Card art importer for ItCardBrawl.
//
// The illustrations are generated outside the repository with an image model (see prompts.md for
// the style brief and the per-card scenes). This script takes a folder of generated images named
// <cardId>.png (or .jpg / .webp), crops each one to the art window's aspect ratio (cover, centred
// horizontally, vertical focus slightly above the middle where the faces usually are) and writes a
// 440x252 WebP of at most 40 kB to public/media/cards/<cardId>.webp, using the Playwright Chromium
// that the e2e suite already installs. Card ids come from src/main/resources/game/factions.json; an
// image whose name is not a card id fails the run. Afterwards it writes a contact sheet (full art
// plus the compact card's centre crop) outside the repository and prints its path.
//
// Usage (from frontend/): npm run card-art -- --from <dir> [--only id,id] [--focus 0.4] [--out <dir>]
//   --from <dir>  folder with the generated images (required)
//   --only id,id  import just these cards (the contact sheet still shows all of them)
//   --focus <0-1> vertical crop focus, 0 = top, 1 = bottom (default 0.4)
//   --out <dir>   where the contact sheet goes (default: <os tmpdir>/card-art)
// Full-size previews of single cards: node tools/card-art/preview.mjs id id ...
import { createRequire } from 'node:module';
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, extname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const FRONTEND = resolve(HERE, '../..');
const REPO = resolve(FRONTEND, '..');
const require = createRequire(join(FRONTEND, 'package.json'));
const { chromium } = require('@playwright/test');

const OUT = join(FRONTEND, 'public/media/cards');
const W = 440;
const H = 252;
const LIMIT = 40 * 1024;
const MIME = {
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
};

const args = process.argv.slice(2);
const argValue = (name) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : undefined;
};
const from = argValue('--from');
if (!from) throw new Error('usage: npm run card-art -- --from <dir> [--only id,id]');
const srcDir = resolve(from);
const only = argValue('--only')?.split(',').filter(Boolean);
const focus = Number(argValue('--focus') ?? 0.4);
const sheetDir = resolve(argValue('--out') ?? join(tmpdir(), 'card-art'));
mkdirSync(sheetDir, { recursive: true });

const catalog = JSON.parse(
  readFileSync(join(REPO, 'src/main/resources/game/factions.json'), 'utf8'),
);
const cards = catalog.cards;
const ids = new Set(cards.map((c) => c.id));
const sources = new Map(
  readdirSync(srcDir)
    .filter((f) => MIME[extname(f).toLowerCase()])
    .map((f) => [f.slice(0, -extname(f).length), join(srcDir, f)]),
);
const strays = [...sources.keys()].filter((id) => !ids.has(id));
if (strays.length) throw new Error(`images without a card: ${strays}`);
const unknown = (only ?? []).filter((id) => !ids.has(id));
if (unknown.length) throw new Error(`unknown card ids: ${unknown}`);
const selected = (only ?? cards.map((c) => c.id)).filter((id) => sources.has(id));
const skipped = (only ?? cards.map((c) => c.id)).filter((id) => !sources.has(id));

const browser = await chromium.launch();
const page = await browser.newPage();
await page.setContent('<canvas></canvas>');

for (const id of selected) {
  const file = sources.get(id);
  const src = `data:${MIME[extname(file).toLowerCase()]};base64,${readFileSync(file).toString('base64')}`;
  const b64 = await page.evaluate(
    async ({ src, W, H, LIMIT, focus }) => {
      const img = new Image();
      img.src = src;
      await img.decode();
      // Cover crop: the largest W:H window of the source, centred horizontally, placed vertically
      // by `focus`.
      const scale = Math.max(W / img.naturalWidth, H / img.naturalHeight);
      const sw = W / scale;
      const sh = H / scale;
      const sx = (img.naturalWidth - sw) / 2;
      const sy = (img.naturalHeight - sh) * focus;
      const c = document.querySelector('canvas');
      c.width = W;
      c.height = H;
      const ctx = c.getContext('2d');
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, sx, sy, sw, sh, 0, 0, W, H);
      let q = 0.85;
      let url;
      for (;;) {
        url = c.toDataURL('image/webp', q);
        const bytes = Math.floor(((url.length - url.indexOf(',') - 1) * 3) / 4);
        if (bytes <= LIMIT || q <= 0.4) break;
        q -= 0.05;
      }
      if (!url.startsWith('data:image/webp')) throw new Error('no webp encoder');
      return url.slice(url.indexOf(',') + 1);
    },
    { src, W, H, LIMIT, focus },
  );
  const buf = Buffer.from(b64, 'base64');
  if (buf.length > LIMIT) throw new Error(`${id}.webp is ${buf.length} B, over ${LIMIT} B`);
  writeFileSync(join(OUT, `${id}.webp`), buf);
}

// Contact sheet: full art plus the compact card's centre crop, for every card that has art.
const withArt = cards.filter((c) => existsSync(join(OUT, `${c.id}.webp`)));
const dataUrl = (id) =>
  'data:image/webp;base64,' + readFileSync(join(OUT, `${id}.webp`)).toString('base64');
const tiles = withArt
  .map(
    (c) => `<figure><img src="${dataUrl(c.id)}" width="220" height="126">
  <div class="compact"><img src="${dataUrl(c.id)}"></div><figcaption>${c.id}</figcaption></figure>`,
  )
  .join('');
await page.setViewportSize({ width: 1400, height: 900 });
await page.setContent(
  `<style>body{margin:8px;background:#0b0d11;color:#9ba7b4;font:11px monospace;display:grid;grid-template-columns:repeat(4,340px);gap:8px}
  figure{margin:0;display:flex;gap:6px;align-items:end;flex-wrap:wrap;width:340px}img{border-radius:6px}
  .compact{width:66px;height:64px;overflow:hidden;border-radius:6px}.compact img{width:100%;height:100%;object-fit:cover}
  figcaption{width:100%;line-height:1.35}</style>${tiles}`,
);
await page.waitForLoadState('load');
const sheet = join(sheetDir, 'contact-sheet.png');
await page.screenshot({ path: sheet, fullPage: true });
await browser.close();

const sizes = withArt.map((c) => [c.id, readFileSync(join(OUT, `${c.id}.webp`)).length]);
const total = sizes.reduce((a, [, b]) => a + b, 0);
sizes.sort((a, b) => a[1] - b[1]);
console.log(`imported ${selected.length} of ${cards.length} cards`);
if (skipped.length) console.log('no source image for:', skipped.join(', '));
console.log('min', sizes[0], 'max', sizes.at(-1), 'total', total);
console.log('contact sheet:', sheet);

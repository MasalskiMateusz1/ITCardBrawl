// Risk: a card-art file that matches no card (typo, renamed card) ships unused, and a card without
// art silently shows the placeholder. The Card component builds the URL from the cardId
// (`/media/cards/<cardId>.webp`), so the file names must match the catalog's card ids exactly.

/** The Node bits the check uses: the spec runs in Node (Vitest), the project has no @types/node. */
interface NodeFs {
  readdirSync(path: string): string[];
  readFileSync(path: string, encoding: 'utf8'): string;
}
declare const process: { cwd(): string };

/** Card ids allowed to have no art yet (each shows the placeholder). Empty: all art is in. */
const allowedMissing: string[] = [];

/** Paths from frontend/, where `ng test` runs. */
const CATALOG = '../src/main/resources/game/factions.json';
const ART_DIR = 'public/media/cards';

describe('card art', () => {
  let cardIds: string[];
  let artIds: string[];

  beforeAll(async () => {
    // A computed specifier keeps the bundler from resolving a Node built-in for the browser.
    const specifier = 'node:fs';
    const fs = (await import(/* @vite-ignore */ specifier)) as NodeFs;
    const root = process.cwd();
    const catalog = JSON.parse(fs.readFileSync(`${root}/${CATALOG}`, 'utf8')) as {
      cards: { id: string }[];
    };
    cardIds = catalog.cards.map((card) => card.id);
    artIds = fs
      .readdirSync(`${root}/${ART_DIR}`)
      .filter((file) => file.endsWith('.webp'))
      .map((file) => file.slice(0, -'.webp'.length));
  });

  it('każdy plik grafiki odpowiada karcie z katalogu', () => {
    const unknown = artIds.filter((id) => !cardIds.includes(id));

    expect(unknown, `pliki bez karty: ${unknown.join(', ')}`).toEqual([]);
  });

  it('każda karta ma grafikę (poza listą allowedMissing)', () => {
    const missing = cardIds.filter((id) => !artIds.includes(id) && !allowedMissing.includes(id));

    expect(missing, `karty bez grafiki: ${missing.join(', ')}`).toEqual([]);
  });

  it('allowedMissing wymienia tylko karty, które naprawdę nie mają grafiki', () => {
    const stale = allowedMissing.filter((id) => !cardIds.includes(id) || artIds.includes(id));

    expect(stale, `zbędne wpisy allowedMissing: ${stale.join(', ')}`).toEqual([]);
  });

  it('katalog nie jest pusty', () => {
    expect(cardIds.length).toBeGreaterThan(0);
  });
});

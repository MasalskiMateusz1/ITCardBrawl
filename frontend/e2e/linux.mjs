// Runs the e2e suite on Linux chromium, the environment the screenshot baselines are pinned in (CI
// runs ubuntu-latest), from any machine with Docker running.
// Usage (from frontend/): npm run e2e:linux [-- <playwright test args>], e.g. -- --update-snapshots
//
// frontend/ is mounted at /work, so baselines, test-results/ and playwright-report/ land on the
// host. node_modules, dist and the Angular cache are container-only volumes: `npm ci` installs the
// Linux dependencies there and never touches the host's.
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';

// Must match the @playwright/test version in package.json.
const IMAGE = 'mcr.microsoft.com/playwright:v1.65.1-noble';
const NPM = 'npm@12.1.0'; // "packageManager" in package.json, as in CI

const quote = (arg) => `'${arg.replaceAll("'", `'\\''`)}'`;
const args = process.argv.slice(2).map(quote).join(' ');
const script = `npm install -g ${NPM} && npm ci && npm run e2e -- ${args}`;

const result = spawnSync(
  'docker',
  [
    'run',
    '--rm',
    '--ipc=host',
    '-v',
    `${resolve('.')}:/work`,
    '-v',
    '/work/node_modules',
    '-v',
    '/work/dist',
    '-v',
    '/work/.angular',
    '-w',
    '/work',
    IMAGE,
    '/bin/bash',
    '-c',
    script,
  ],
  { stdio: 'inherit' },
);

if (result.error) {
  console.error(`Nie udało się uruchomić Dockera: ${result.error.message}`);
  process.exit(1);
}
process.exit(result.status ?? 1);

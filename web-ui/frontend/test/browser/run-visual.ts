import { existsSync, mkdirSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const frontend = fileURLToPath(new URL('../../', import.meta.url));
const storybook = resolve(frontend, '../build/storybook');
const snapshots = resolve(frontend, 'test/browser/snapshots');
const reports = resolve(frontend, '../build/reports/playwright');
const engine =
    process.env.VRT_CONTAINER_ENGINE || (process.platform === 'win32' ? 'podman' : 'docker');
const image = 'jftn-vrt:playwright-1.64.1';
const args = process.argv.slice(2);
const update = args.some((arg) => arg.startsWith('--update-snapshots') || arg === '-u');

if (process.env.CI && update)
    throw new Error('CI must compare committed snapshots, not update them.');
if (!existsSync(join(storybook, 'iframe.html'))) {
    throw new Error('Build the catalogue first: ./gradlew :web-ui:buildStorybook');
}
mkdirSync(snapshots, { recursive: true });
mkdirSync(reports, { recursive: true });

function run(command: string[]) {
    const result = spawnSync(engine, command, {
        cwd: frontend,
        stdio: 'inherit',
        windowsHide: true
    });
    if (result.error) throw result.error;
    if (result.status !== 0) process.exit(result.status || 1);
}

run([
    'build',
    '--platform',
    'linux/amd64',
    '--file',
    'test/browser/visual.Dockerfile',
    '--tag',
    image,
    '.'
]);
run([
    'run',
    '--rm',
    '--init',
    '--platform',
    'linux/amd64',
    '--network',
    'none',
    '--shm-size',
    '512m',
    '--volume',
    `${storybook}:/work/build/storybook:ro`,
    '--volume',
    `${snapshots}:/work/frontend/test/browser/snapshots:${update ? 'rw' : 'ro'}`,
    '--volume',
    `${reports}:/work/build/reports/playwright`,
    '--env',
    `CI=${process.env.CI || ''}`,
    image,
    'node',
    'node_modules/@playwright/test/cli.js',
    'test',
    '--config',
    'playwright.visual.config.ts',
    ...args
]);

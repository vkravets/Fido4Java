import { defineConfig, devices } from '@playwright/test';

const scenario = process.env.UI_SCENARIO;
if (!process.env.UI_BASE_URL || !['application', 'setup'].includes(scenario || '')) {
    throw new Error('Run the application browser tests through :web-ui:playwrightTest.');
}

export default defineConfig({
    testDir: './test/browser',
    testMatch: `${scenario}.spec.ts`,
    workers: 1,
    forbidOnly: !!process.env.CI,
    retries: 0,
    timeout: 60000,
    reporter: [
        ['list'],
        ['html', { outputFolder: `../build/reports/playwright/${scenario}`, open: 'never' }],
        ['junit', { outputFile: `../build/test-results/playwright/${scenario}.xml` }]
    ],
    outputDir: `../build/reports/playwright/${scenario}-results`,
    use: {
        actionTimeout: 10000,
        navigationTimeout: 15000,
        baseURL: process.env.UI_BASE_URL,
        trace: 'retain-on-failure',
        screenshot: 'only-on-failure',
        video: 'retain-on-failure'
    },
    projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }]
});

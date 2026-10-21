import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
    testDir: './test/browser',
    testMatch: [
        'components.spec.ts',
        'workspace.spec.ts',
        'shortcuts.spec.ts',
        'dos-graphics.spec.ts',
        'typography.spec.ts',
        'directory-settings.spec.ts',
        'plugin-localization.spec.ts'
    ],
    fullyParallel: true,
    forbidOnly: !!process.env.CI,
    workers: 2,
    retries: 0,
    timeout: 30000,
    reporter: [
        ['list'],
        ['html', { outputFolder: '../build/reports/playwright/components', open: 'never' }],
        ['junit', { outputFile: '../build/test-results/playwright/components.xml' }]
    ],
    outputDir: '../build/reports/playwright/components-results',
    use: {
        actionTimeout: 10000,
        navigationTimeout: 15000,
        baseURL: 'http://127.0.0.1:6007',
        trace: 'retain-on-failure',
        screenshot: 'only-on-failure',
        video: 'retain-on-failure'
    },
    projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
    webServer: {
        command: `"${process.execPath.replaceAll('\\', '/')}" node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 6007 --strictPort --outDir ../build/storybook`,
        url: 'http://127.0.0.1:6007/iframe.html',
        reuseExistingServer: false,
        timeout: 30000
    }
});

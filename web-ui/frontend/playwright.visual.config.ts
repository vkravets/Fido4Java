import { defineConfig } from '@playwright/test';
import componentConfig from './playwright.config.ts';

if (
    process.env.JFTN_VRT_CONTAINER !== '1' ||
    process.platform !== 'linux' ||
    process.arch !== 'x64'
) {
    throw new Error('Use npm run test:vrt to compare screenshots in the pinned Linux container.');
}

export default defineConfig({
    ...componentConfig,
    testMatch: 'visual.spec.ts',
    workers: 2,
    updateSnapshots: 'none',
    snapshotPathTemplate: '{testDir}/snapshots/{projectName}/{arg}{ext}',
    expect: {
        toHaveScreenshot: {
            animations: 'disabled',
            caret: 'hide',
            scale: 'css',
            threshold: 0.1,
            maxDiffPixels: 0
        }
    },
    reporter: [
        ['list'],
        ['html', { outputFolder: '../build/reports/playwright/visual', open: 'never' }],
        ['junit', { outputFile: '../build/reports/playwright/visual.xml' }]
    ],
    outputDir: '../build/reports/playwright/visual-results',
    use: {
        ...componentConfig.use,
        locale: 'en-GB',
        timezoneId: 'UTC',
        deviceScaleFactor: 1,
        reducedMotion: 'reduce',
        colorScheme: 'light',
        serviceWorkers: 'block'
    },
    projects: [
        {
            name: 'desktop',
            use: { browserName: 'chromium', viewport: { width: 1280, height: 900 } }
        },
        {
            name: 'mobile',
            use: {
                browserName: 'chromium',
                viewport: { width: 375, height: 812 },
                isMobile: true,
                hasTouch: true
            }
        }
    ]
});

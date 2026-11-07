FROM mcr.microsoft.com/playwright:v1.65.0-noble@sha256:6e3fb709361521eb27161606e6da6d2674e5e89fa16516cb37d6fdd91372273d
WORKDIR /work/frontend
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY playwright.config.ts playwright.visual.config.ts vite.config.ts ./
COPY test/browser/visual.spec.ts test/browser/story.ts ./test/browser/
COPY test/browser/visual/ ./test/browser/visual/
ENV JFTN_VRT_CONTAINER=1
CMD ["node", "node_modules/@playwright/test/cli.js", "test", "--config", "playwright.visual.config.ts"]

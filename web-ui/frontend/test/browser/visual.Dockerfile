FROM mcr.microsoft.com/playwright:v1.64.1-noble@sha256:bdfda2a8fdf48707c5e50a49bd3e06d8d0c759153dc8ee057d8a81c3450bb3e9
WORKDIR /work/frontend
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY playwright.config.ts playwright.visual.config.ts vite.config.ts ./
COPY test/browser/visual.spec.ts test/browser/story.ts ./test/browser/
ENV JFTN_VRT_CONTAINER=1
CMD ["node", "node_modules/@playwright/test/cli.js", "test", "--config", "playwright.visual.config.ts"]

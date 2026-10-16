import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath } from 'node:url';

export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: { '@rpc': fileURLToPath(new URL('../build/frontend-generated', import.meta.url)) }
    },
    build: { outDir: '../build/frontend-dist', emptyOutDir: true }
});

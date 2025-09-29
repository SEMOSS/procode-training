import { resolve } from 'path';
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), '');
    return {
        root: 'src',
        base: './',
        envDir: '../',
        envPrefix: 'CLIENT_',
        resolve: {
            alias: {
                '@': resolve(__dirname, './src'),
            },
        },
        server: {
            proxy: {
                [env.CLIENT_MODULE]: {
                    target: env.ENDPOINT,
                    changeOrigin: true,
                    secure: false,
                },
            },
        },
        build: {
            outDir: '../../portals',
            emptyOutDir: true,
        },
        plugins: [react()],
    };
});

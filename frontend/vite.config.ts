import { defineConfig } from 'vitest/config';
import adapter from '@sveltejs/adapter-static';
import tailwindcss from '@tailwindcss/vite';
import { sveltekit } from '@sveltejs/kit/vite';

export default defineConfig({
    plugins: [
        tailwindcss(),
        sveltekit({
            compilerOptions: {
                // Force runes mode for the project, except for libraries. Can be removed in svelte 6.
                runes: ({ filename }) =>
                    filename.split(/[/\\]/).includes('node_modules')
                        ? undefined
                        : true,
            },
            adapter: adapter({ fallback: '200.html' }),
        }),
    ],
    server: {
        proxy: {
            '/api': 'http://127.0.0.1:8080',
            '/v3': 'http://127.0.0.1:8080',
            '/swagger-ui': 'http://127.0.0.1:8080',
        },
    },
    test: {
        expect: { requireAssertions: true },
        projects: [
            {
                extends: './vite.config.ts',
                test: {
                    name: 'server',
                    environment: 'node',
                    include: ['src/**/*.{test,spec}.{js,ts}'],
                    exclude: ['src/**/*.svelte.{test,spec}.{js,ts}'],
                },
            },
        ],
    },
});

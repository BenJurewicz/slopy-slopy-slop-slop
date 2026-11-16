import js from '@eslint/js';
import { loadConfig } from '@sveltejs/load-config';
import { defineConfig, globalIgnores } from 'eslint/config';
import prettier from 'eslint-config-prettier';
import svelte from 'eslint-plugin-svelte';
import globals from 'globals';
import ts from 'typescript-eslint';

const svelteConfig = (await loadConfig('./', { traverse: false }))?.config;

export default defineConfig(
    globalIgnores([
        '.svelte-kit/**',
        'build/**',
        'test-results/**',
        'playwright-report/**',
        'src/lib/generated/**',
        'openapi-ts-error-*.log',
    ]),
    {
        files: ['**/*.{js,mjs,cjs,ts,tsx,svelte}'],
        extends: [
            js.configs.recommended,
            ts.configs.recommended,
            svelte.configs.recommended,
        ],
        rules: {
            '@typescript-eslint/no-unused-vars': [
                'error',
                {
                    argsIgnorePattern: '^_',
                    varsIgnorePattern: '^_',
                    caughtErrorsIgnorePattern: '^_',
                },
            ],
        },
    },
    {
        files: ['src/**/*.{js,ts,tsx,svelte}'],
        languageOptions: { globals: globals.browser },
    },
    {
        files: ['*.{js,mjs,cjs,ts}', 'e2e/**/*.ts'],
        languageOptions: { globals: globals.node },
    },
    {
        files: ['**/*.svelte', '**/*.svelte.ts', '**/*.svelte.js'],
        languageOptions: {
            parserOptions: {
                parser: ts.parser,
                svelteConfig,
                extraFileExtensions: ['.svelte'],
            },
        },
    },
    svelte.configs.prettier,
    prettier,
);

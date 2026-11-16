import { createRequire } from 'node:module';

const require = createRequire(
    new URL('./frontend/package.json', import.meta.url),
);

export default {
    plugins: [
        require.resolve('prettier-plugin-svelte'),
        require.resolve('@prettier/plugin-xml'),
    ],
    useTabs: false,
    tabWidth: 4,
    printWidth: 80,
    endOfLine: 'lf',
    singleQuote: true,
    semi: true,
    trailingComma: 'all',
    xmlWhitespaceSensitivity: 'strict',
    overrides: [{ files: '*.xsd', options: { parser: 'xml' } }],
};

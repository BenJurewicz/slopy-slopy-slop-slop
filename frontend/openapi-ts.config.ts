import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
	input: 'http://127.0.0.1:8080/v3/api-docs',
	output: 'src/lib/generated/api',
	plugins: [
		'@hey-api/typescript',
		'@hey-api/sdk',
		{ name: '@hey-api/client-fetch', baseUrl: false }
	]
});

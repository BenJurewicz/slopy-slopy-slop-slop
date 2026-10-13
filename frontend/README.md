# Frontend

SvelteKit, TypeScript and pnpm.

## Development

```sh
pnpm install --frozen-lockfile
pnpm dev
```

## Checks

```sh
pnpm check
pnpm test
```

## Build

```sh
pnpm build
pnpm preview
```

The build produces static files in `build/`. Configure the web server to serve
`200.html` for frontend routes that do not match a file.

## Generated API client

The frontend uses `@hey-api/openapi-ts` to generate types and Fetch endpoint
functions from the Spring Boot OpenAPI specification. TypeScript 6 is used
without dependency overrides.

Start the backend on `http://127.0.0.1:8080`, then run:

```sh
pnpm generate:api
pnpm check
pnpm test
```

Configuration is in `openapi-ts.config.ts`. Generated files are in
`src/lib/generated/api/`; do not edit them by hand. Regenerate them after
changing the backend API. Controllers declare stable operation IDs so generated
function names do not depend on endpoint discovery order.

`src/lib/api.ts` uses generated endpoint functions and centralizes UI error
handling. The dynamic document editor converts its form data to generated request
models at this boundary; the backend validates the submitted documents. Generated
TypeScript types provide compile-time checks, not runtime response validation.

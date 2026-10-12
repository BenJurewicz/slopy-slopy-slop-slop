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

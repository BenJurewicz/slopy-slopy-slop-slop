# BaseX

## Setup

If `.env` does not exist, create the local configuration:

```sh
cp .env.example .env
```

Set `BASEX_PASSWORD` in `.env`, then start the database:

```sh
docker compose pull
docker compose up -d --wait
```

BaseX is available at `http://localhost:8984`. Sign in as `admin` using the
password from `.env`.

## Status and shutdown

```sh
docker compose ps
docker compose down
```

Database files and users are stored in a named Docker volume. Stopping the
container preserves them; `docker compose down -v` deletes them.

`BASEX_PASSWORD` sets the admin password on first startup only. To change an
existing password, use BaseX's user management interface.

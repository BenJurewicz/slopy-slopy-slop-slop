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

## Application database

From the project root, run `python3 infra/scripts/init-db.py`. This non-destructive,
idempotent setup creates the application and isolated test databases and a
restricted account. It generates `backend/.env` with private credentials; do not
commit or share that file. The backend does not use the administrator account.

See `../docs/walkthrough.md` for the explicit destructive demo reset procedure.

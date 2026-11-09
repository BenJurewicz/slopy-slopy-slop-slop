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

## Uzupełnienie demonstracyjnych danych do XSD v2

Zatrzymaj aplikację, pozostawiając BaseX uruchomiony. Skrypt dotyczy wyłącznie
fikcyjnych danych demonstracyjnych; nie tworzy kopii bazy:

```sh
python3 infra/scripts/enrich-demo-v2.py          # sprawdzenie bez zapisu
python3 infra/scripts/enrich-demo-v2.py --apply  # jeden atomowy zapis
```

Uruchamiaj z katalogu projektu, z JDK w `JAVA_HOME` lub `PATH`. Skrypt dodaje
adres, REGON, reprezentanta, numery dokumentów/projektów, okres realizacji, koszt,
wkład własny i stan dostępności budżetu. Przed zapisem sprawdza XSD v2 i zachowanie
wszystkich dotychczasowych pól. Zachowuje identyfikatory, powiązania, rewizje,
kwoty, oceny i statusy; przerywa przy równoległej zmianie danych. Ponowne
uruchomienie na v2 nie zapisuje niczego. Nie uruchamia resetu ani nowego demo.

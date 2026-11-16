# Backend

Spring Boot with Java 23 and Gradle.

## Development

Set `JAVA_HOME` to your JDK 23 installation. On macOS:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 23)
```

Start the server at `http://localhost:8080`:

```sh
./gradlew bootRun
```

## Tests and build

```sh
./gradlew test
./gradlew bootJar
```

The executable JAR is written to `build/libs/`.

Build the frontend with `cd ../frontend && pnpm build` before `bootJar`.
The executable JAR includes its static assets. Start it from `backend/` so that
`.env` is loaded. Initialize the database with `python3 ../infra/scripts/init-db.py`.

`./gradlew --offline integrationTest` exercises real BaseX and resets only
`cloudmigration_tests`. `generateExamples` writes XML/DOCX fixtures to `../examples`.
Use `../scripts/build.sh` for a clean combined build with formatting, lint and
frontend checks. Stop any running JAR before replacing it.

Java formatting lives in the separate root Gradle project. Run `make format`
or `make format-check` from `Project/`, not a backend Spotless task.
The Eclipse profile is shared with Neovim; see
[formatting.md](../docs/formatting.md).

## Source layout

- `workflow/Workflow` coordinates document metadata and lifecycle transitions;
  `Rules` validates business constraints.
- `workflow/Funding` separates ranking, draft preparation and atomic issuance.
- `documents/DocumentCodec` handles XML conversion and record traversal;
  `storage/` owns BaseX transport and persistence.
- `docx/DocxDocuments` coordinates document import/export, `WordUpload` checks
  uploaded archives, and `WordTemplates` handles Word structure and controls.
- `resources/xquery/` contains named storage transactions and reports.

The readability refactor preserved validation order and messages. Subsequent
fixes address the documented issues, including redundant BaseX list requests.
See [known-issues.md](../docs/known-issues.md).

## CloudMigration settings

`GET /api/competition` returns the single configuration, creating defaults only
when none exists. `PUT /api/competition` uses the existing ID and revision.
There are no competition creation/deletion or collection endpoints. Existing XML
IDs and document links are preserved. Multiple legacy configurations cause HTTP
409 without data removal. Applications inherit the configuration ID; imports,
reports and funding use it internally rather than accepting a competition choice.

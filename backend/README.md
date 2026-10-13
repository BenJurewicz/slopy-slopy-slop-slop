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
Use `../scripts/build.sh` for a clean combined build with frontend checks.

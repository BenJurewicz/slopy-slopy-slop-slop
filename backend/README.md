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

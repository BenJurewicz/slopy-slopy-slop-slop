# CloudMigration — Chmura dla Lokalnych Biznesów

Jednoosobowa aplikacja do obsługi konkursu finansującego migracje mikro- i małych
przedsiębiorstw do chmury. Obsługuje wnioski, oceny, ranking oraz decyzje o dotacji.
Nie wykonuje migracji i nie wymaga logowania. Jest przeznaczona do pracy lokalnej.

## Wymagania

- JDK 23 i Gradle Wrapper z repozytorium.
- Node.js i pnpm 12.11.2; frontend korzysta z TypeScript 6.
- Docker z Compose; baza BaseX 12.4 jest przypięta do digestu obrazu.
- Python 3 do inicjalizacji bazy i pomocniczych poleceń.

Pierwsza instalacja zależności i pobranie obrazu wymagają dostępu do Internetu.
Kolejne buildy backendu mogą działać z `--offline`. Nie jest używany Maven.

## Pierwsze uruchomienie

1. W `infra/` skopiuj `.env.example` do `.env` i ustaw własne `BASEX_PASSWORD`.
2. Uruchom bazę:

   ```sh
   cd infra
   docker compose pull
   docker compose up -d --wait
   cd ..
   python3 infra/scripts/init-db.py
   ```

   Inicjalizacja tworzy bazy `cloudmigration` i `cloudmigration_tests` oraz konto
   aplikacji z uprawnieniami tylko do tych baz. Zapisuje prywatne dane w
   `backend/.env`. Nie usuwa istniejących dokumentów; można ją powtórzyć.

3. Zainstaluj frontend i zbuduj aplikację:

   ```sh
   cd frontend
   pnpm install --frozen-lockfile
   cd ..
   export JAVA_HOME=$(/usr/libexec/java_home -v 23) # macOS; gdzie indziej ustaw ścieżkę do JDK 23
   ./scripts/build.sh
   ```

   Przy pierwszym buildzie backendu użyj `cd backend && ./gradlew test bootJar`
   bez `--offline`, aby pobrać zależności Gradle. Frontend musi być wcześniej
   zbudowany. Po pobraniu zależności `scripts/build.sh` działa offline dla Gradle.

4. Uruchom pakiet z katalogu `backend` (stąd odczytywany jest `.env`):

   ```sh
   cd backend
   java -jar build/libs/cloudmigration-backend-0.0.1-SNAPSHOT.jar
   ```

   Aplikacja: <http://127.0.0.1:8080/>. Swagger: <http://127.0.0.1:8080/swagger-ui.html>.
   Node.js nie jest potrzebny do serwowania gotowej aplikacji.

5. Konfiguracja jedynego konkursu **CloudMigration** powstaje automatycznie.
   W **Ustawienia** zmień budżet, limit, próg, kryteria i etap. Po dodaniu pierwszego
   wniosku można zmieniać tylko etap. Nie ma wyboru, tworzenia ani usuwania konkursów.
   Jeśli nie ma jeszcze wniosków, **Przegląd: Wczytaj przykład** dodaje dane demonstracyjne.

## Rozwój i testy

Backend: `cd backend && ./gradlew --offline bootRun`. Frontend:
`cd frontend && pnpm dev`; żądania `/api` są przekazywane do portu 8080.

```sh
cd backend
./gradlew --offline test                 # XML/JAXB, DOCX, reguły, kontrakty HTTP
./gradlew --offline integrationTest      # rzeczywista BaseX; czyści WYŁĄCZNIE bazę testową
./gradlew --offline generateExamples     # odtwarza XML i DOCX w examples/
cd ../frontend
pnpm generate:api                       # backend na porcie 8080 musi działać
pnpm check
pnpm test
pnpm test:e2e                           # po zbudowaniu JAR; BaseX musi działać
```

Testy przeglądarkowe startują osobny JAR na porcie 8081, serwują frontend z tego
JAR i czyszczą tylko bazę testową. Używają istniejącego Chrome na macOS; na innym
systemie ustaw `BROWSER_EXECUTABLE` lub użyj wcześniej zainstalowanego Chromium
Playwright. Polecenie nie pobiera przeglądarki. Ślady nieudanych testów i zrzuty
są w ignorowanym `frontend/test-results/`.

## Dokumenty i architektura

- Jeden stały konkurs CloudMigration: `GET/PUT /api/competition`; identyfikator
  konfiguracji pozostaje technicznym powiązaniem w XML. Istniejąca konfiguracja
  i jej dokumenty są zachowywane bez resetu ani migracji schematu. Baza z wieloma
  konfiguracjami zwraca konflikt zamiast automatycznie wybierać lub usuwać dane.
- XML jest źródłem prawdy w BaseX: `competitions/`, `applications/`, `evaluations/`, `decisions/`.
- XSD: `backend/src/main/resources/schemas/`; klasy JAXB powstają automatycznie
  w `backend/build/generated/sources/xjc/`.
- XQuery: `backend/src/main/resources/xquery/`; interfejs udostępnia tylko katalog
  nazwanych zapytań z wiązanymi parametrami, nie dowolne polecenia.
- Hey API generuje typy i klienta Fetch z OpenAPI do `frontend/src/lib/generated/api/`.
- Przykłady XML, błędne dane i DOCX: `examples/`.
- [Instrukcja demonstracji i reguły](docs/walkthrough.md).
- [Plan](docs/plan.md).

Baza i aplikacja są związane z adresem loopback. Hasła nie trafiają do frontendu.
Brak uwierzytelniania jest świadomym ograniczeniem demonstracji, nie rozwiązaniem
przeznaczonym do publicznego wdrożenia.

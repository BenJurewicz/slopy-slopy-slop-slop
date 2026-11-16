# CloudMigration - Chmura dla Lokalnych Biznesów

Jednoosobowa aplikacja do obsługi konkursu finansującego migracje mikro- i małych
przedsiębiorstw do chmury. Obsługuje wnioski, oceny, ranking oraz decyzje o dotacji.
Nie wykonuje migracji i nie wymaga logowania. Jest przeznaczona do pracy lokalnej.

## Wymagania

- JDK 23 i Gradle Wrapper z repozytorium.
- Node.js i pnpm 12.11.2; frontend korzysta z TypeScript 6.
- Docker z Compose; baza BaseX 12.4 jest przypięta do digestu obrazu.
- Python 3 do inicjalizacji bazy i pomocniczych poleceń.
- Do kontroli źródeł: Ruff 0.15.8, shfmt 3.13.0 i StyLua 2.3.1,
  dostępne w `PATH` lub istniejącej instalacji Mason Neovim.

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
    pnpm build
    cd ..
    export JAVA_HOME=$(/usr/libexec/java_home -v 23) # macOS; gdzie indziej ustaw ścieżkę do JDK 23
    (cd backend && ./gradlew test bootJar --console=plain)
    ./backend/gradlew spotlessCheck --console=plain
    ./scripts/build.sh
    ```

    Pierwsze dwa wywołania Wrappera pobierają Gradle, zależności backendu oraz
    osobnego projektu formatowania w katalogu głównym. Kolejne wywołania
    `scripts/build.sh` sprawdzają formatowanie i lint oraz używają wyłącznie
    zapisanej dystrybucji Gradle i zależności offline. Brak narzędzia powoduje
    błąd, nie automatyczną instalację. Przed pakowaniem zatrzymaj uruchomiony JAR.

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
   **Ustawienia > Dane testowe > Wczytaj dane demonstracyjne** po potwierdzeniu
   usuwa obecne dane i ustawienia, a następnie odtwarza domyślny konkurs oraz dane demo.

## Rozwój i testy

Po pierwszej konfiguracji, z katalogu `Project/`:

```sh
make run
```

Polecenie używa Makefile i Bash: uruchamia BaseX, backend na porcie 8080 oraz
frontend na <http://127.0.0.1:5173>. Logi trafiają do jednego terminala.
**Ctrl+C** zatrzymuje oba serwery wraz z procesami potomnymi i kontener BaseX,
bez usuwania wolumenu ani danych. Zatrzymuje także BaseX uruchomiony wcześniej
dla tego projektu. Błąd uruchomienia lub zakończenie jednego serwera również
zatrzymuje pozostałe procesy. Zajęte porty 8080 lub 5173 powodują błąd bez
zatrzymywania istniejących serwerów.

Frontend odświeża się po zmianach; po zmianach backendu zatrzymaj polecenie
przez Ctrl+C i uruchom `make run` ponownie. Żądania `/api` z frontendu są
przekazywane do backendu. Na macOS skrypt wybiera JDK 23, jeśli `JAVA_HOME`
nie jest ustawione. Na innych systemach ustaw je samodzielnie.

Potrzebne są Bash, Make, `lsof` i zależności z pierwszej konfiguracji.
Polecenie nie instaluje pakietów ani nie pobiera obrazów. Korzysta z zapisanej
lokalnie dystrybucji Gradle wskazanej przez Wrapper i zależności offline;
brakujące zależności trzeba wcześniej przygotować zgodnie z instrukcją
pierwszego uruchomienia.

Test samego uruchamiania i sprzątania, z atrapami procesów i bez dostępu do bazy:

```sh
bash scripts/test-dev.sh
```

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

## Formatowanie i lint

```sh
make format          # zapis obsługiwanych źródeł
make format-check    # bez zmiany plików
make lint            # ESLint: TypeScript i Svelte
make check           # format, lint, typy i testy jednostkowe
make editor-check    # lokalna integracja z istniejącym Neovim/LazyVim
```

Wspólne ustawienia: cztery spacje, LF, UTF-8 i docelowo 80 kolumn; Makefile używa
Tab. Generowane SDK/JAXB, fixtures, DOCX i dane bazy nie są formatowane. XQuery
pozostaje bez zmian. [Profile, narzędzia i uruchamianie Neovim](docs/formatting.md).

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

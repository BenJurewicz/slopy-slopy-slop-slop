# Weryfikacja wydania

## Wyniki

- Backend: **55 testów jednostkowych/HTTP** i **18 testów integracyjnych z BaseX**, bez błędów.
- Frontend: **36 testów**, sprawdzanie Svelte/TypeScript bez błędów i ostrzeżeń.
- Przeglądarka: **16 testów** na pakiecie JAR, bez błędów. Dodatkowy test
  sprawdza zachowanie fokusu i wartości podczas edycji oraz usuwania
  powtarzalnych wierszy formularza.
- Dane testowe: obie operacje wymagają potwierdzenia. Anulowanie nie wysyła
  żądania zmiany danych. Wczytanie dwukrotnie zastępuje dokumenty zamiast je
  dopisywać, przywraca domyślne ustawienia i usuwa poprzednie decyzje.
  Reset usuwa również dokumenty końcowe, zachowując ustawienia. Niepoprawny
  zestaw zastępczy i brak potwierdzenia nie usuwają istniejących danych.
- `make run`: pięć testów cyklu życia na Bash 3.2, bez dostępu do bazy:
  Ctrl+C po starcie, Ctrl+C podczas startu BaseX, zakończenie serwera, błąd
  startu bazy i zajęte porty. Procesy potomne nie pozostają uruchomione.
  Sprawdzony także rzeczywisty start frontendu i backendu oraz zatrzymanie
  obu serwerów i BaseX przez Ctrl+C; skróty danych pozostały identyczne.
- Formatowanie: `make format`, następnie `make format-check` bez dalszych
  zmian. Generowane SDK/JAXB, fixtures i DOCX pozostały nietknięte.
  XQuery uporządkowano ręcznie; automatyczne formattery go nie zmieniają.
  Niepoprawne tymczasowe pliki Python/Bash/Lua w katalogu fixtures zostały
  prawidłowo pominięte również przez pomocnicze formattery.
- `make check`, `pnpm format:check` i ESLint przechodzą bez błędów.
  Konfiguracja ESLint nie zawiera reguł kolidujących z Prettier.
- `make editor-check`: rzeczywiste formatowanie w buforach Neovim przez
  Prettier, ktlint, Ruff, shfmt i StyLua; diagnostyka ESLint, zakres projektu,
  ochrona plików prywatnych/generowanych/XQuery i powiadomienie z profilem Java.
  Globalny `init.lua` nie jest ładowany, bufory nie są zapisywane.
  Porównanie 41 klas JDT 3.45.0 potwierdza identyczny kod formatowania
  w zainstalowanym JDTLS oraz Eclipse 4.39 używanym przez Spotless.
- Po pełnym buildzie, integracji i E2E wszystkie **57 dokumentów bazy użytkowej**
  zachowały identyczny zbiorczy SHA-256. Porównanie wykonano w pamięci,
  bez tworzenia kopii bazy i bez resetowania danych użytkownika.
- `pnpm peers check`: brak konfliktów; TypeScript 6.0.3, Hey API 0.99.0.
- Czysty build przez `scripts/build.sh`: frontend i wykonywalny JAR z zasobami.
- Ponowna inicjalizacja bazy: dwa udane wywołania bez utraty dokumentów.
- Restart kontenera BaseX: dokumenty testowe zachowały identyczny skrót treści.
- Microsoft Word **16.92**: dostarczony pusty formularz, wypełniony przykład oraz
  przykład z tytułem i opisem wielowierszowym zmienionymi w Wordzie zostały
  ponownie zapisane do DOCX. Testy sprawdzają wszystkie dane i zachowanie
  kontrolek. Fixtures: `backend/src/test/resources/docx/word-blank-application.docx`,
  `word-filled-application.docx`, `word-edited-application.docx`.
- Jeden konkurs CloudMigration: automatyczna inicjalizacja, zachowanie istniejącego
  ID, równoległe odczyty bez duplikatów, blokada tworzenia/usuwania drugiego
  konkursu i odrzucenie obcego ID we wniosku. Ustawienia są edytowalne przed
  wnioskami; później wyłącznie etap. Wiele starych konfiguracji jest odrzucane
  bez usuwania danych. Import, finansowanie i raporty nie wymagają wyboru konkursu.
- Istniejące 20 dokumentów: zachowane wszystkie dane, identyfikatory, powiązania,
  rewizje i stany; porównanie eksportów XML z uwzględnieniem równoważnych prefiksów
  przestrzeni nazw. Bez resetu i kopii bazy.
- XSD v2: adres, NIP/REGON i reprezentant beneficjenta, numery dokumentów/projektu,
  okres realizacji, całkowity koszt i wkład własny. Terminy zadań muszą mieścić
  się w okresie projektu. Serwer oblicza kwoty bez arytmetyki zmiennoprzecinkowej.
- Dane demonstracyjne: 20 dokumentów uzupełnionych atomowo bez kopii bazy.
  Sprawdzenie XSD przed zapisem; porównanie wszystkich dotychczasowych pól
  po zapisie. Zachowane ID, relacje, rewizje, kwoty, punkty i statusy.
  Powtórne uruchomienie skryptu nie zmienia bazy.
- Układ wydruku: aplikacyjny formularz i trzy eksporty otwarte w Wordzie i zapisane
  do PDF do sprawdzenia czytelności. Domyślny formularz jest pusty, przykład osobny;
  ocena i decyzja pobierają rzeczywiste powiązane dane z BaseX.
- Pages: wyłączone z zakresu weryfikacji zgodnie z ustalonym zakresem.

## Refaktoryzacja czytelności

Punkt odniesienia: `d30e28f`, z 86 przechodzącymi testami. Po refaktoryzacji
zestaw obejmuje 117 testów. Nowe testy charakteryzują dotychczasowe zachowanie,
nie wprowadzają napraw błędów opisanych w [known-issues.md](known-issues.md).

- Backend rozdziela walidację, metadane, ranking, przygotowanie decyzji,
  wydanie oraz odczyt struktury XML/DOCX. Zachowano kolejność sprawdzeń,
  komunikaty, arytmetykę dziesiętną i granice transakcji.
- Porównano 27 odpowiedzi dziewięciu raportów przed i po zmianach XQuery,
  z różnymi progami i stronicowaniem. Znormalizowane skróty są identyczne.
- Frontend rozdziela powłokę strony, stan i akcje `Workspace`, widoki oraz
  edytor formularzy. Wydzielenie widoków sprawdzono przez 16 porównań DOM,
  wartości kontrolek i zrzutów. Uporządkowanie markup i formularzy sprawdzono
  przez 28 takich porównań, na ekranach 1280x720 i 390x844; skróty są identyczne.
- Dla startu serwera E2E porównano sześć scenariuszy systemu i `JAVA_HOME`:
  identyczne bajty żądania oraz argumenty uruchamiania procesu.
- Dla migracji porównano 26 przebiegów z atrapami sieci i procesów: identyczne
  XML, kolejność pól, flagi budżetu, polecenia, komunikaty oraz błędy.
  Nie uruchamiano migracji na bazie użytkowej.
- Pomocniczy walidator Java zachowuje wyniki siedmiu scenariuszy, w tym
  dostarczonych XML, pustych danych, błędów schematu i odrzucenia DTD.

## Naprawy znanych błędów

Po refaktoryzacji naprawiono wszystkie siedem pozycji z `known-issues.md`.
Bieżący zestaw obejmuje 125 testów: 55 backendu, 18 integracyjnych, 36 frontendu
oraz 16 przeglądarkowych. Regresje sprawdzają błędy odświeżenia, pozostawienie
edytora po odrzuconym złożeniu, blokadę niezapisanych zmian, korektę strony po
usunięciu wniosku, dostęp do 501. wiersza raportu oraz jeden odczyt BaseX na listę.
Instrukcja pustego DOCX opisuje automatyczne przypisanie do CloudMigration.

## Pokrycie wymagań

| Obszar                                       | Dowód                                                             |
| -------------------------------------------- | ----------------------------------------------------------------- |
| Cztery XML/XSD i powiązania                  | `schemas/`, `examples/xml/`, `DocumentCodecTest`                  |
| Automatycznie wygenerowane klasy             | Gradle `generateXmlModels`, test odczytu/edycji/utworzenia JAXB   |
| Walidacja struktury, kwot i unikalnych kodów | XSD oraz testy negatywne i `RulesTest`                            |
| DOCX <-> XML                                 | `DocxDocumentsTest`, fixture Word, pełny test przeglądarkowy      |
| CRUD, rewizje i odwołania w BaseX            | `WorkflowIntegrationTest`                                         |
| Złożenie i ocena końcowa                     | Test przeglądarkowy i zablokowane edycje w integracji             |
| Ranking i remisy                             | Test rzeczywistego porównania dat, następnie ID                   |
| Zapytania łączące typy dokumentów            | Ranking, podsumowanie, decyzje, finansowanie w BaseX              |
| Dotacje i atomowość                          | Odrzucony cały zestaw ponad budżetem lub z nieaktualną rewizją    |
| Spójność Java/XQuery finansowania            | Porównanie propozycji na zestawie demonstracyjnym                 |
| Kontrakty i awarie HTTP                      | `ApiContractTest`, błędny import, HTTP 404/503, stare rewizje     |
| Pełny proces w pakiecie                      | `frontend/e2e/workflow.spec.ts`                                   |
| Responsywność i klawiatura                   | 1280x720, 390x844, kontrola przepełnienia, Tab/Enter w formularzu |
| Trwałość                                     | Porównanie danych przed i po restarcie BaseX                      |

Testy nie używają zastępczej bazy w pamięci. Testy HTTP awarii celowo wskazują
nieaktywny adres BaseX i nie wymagają serwera bazy. Testy integracyjne i E2E
czyszczą wyłącznie wydzieloną bazę testową; nie uruchamiają resetu bazy użytkowej.

## Obrona danych wejściowych

Sprawdzone: błędne XML, DTD/XXE, zła liczba miejsc dziesiętnych, ujemne kwoty,
nieprawidłowe sumy, punkty poza zakresem, duplikaty kryteriów, brak oceny końcowej,
niekwalifikowalna firma, błędny DOCX, limit pliku i limit rozpakowanego ZIP,
zewnętrzne relacje, makra, nadmierna głębokość XML, niejednoznaczne ścieżki ZIP,
podzielone fragmenty tekstu Word, powtórzone bloki, brak lub duplikaty kontrolek,
pusty formularz, rozbieżne powtórzenia tytułu oraz niezgodne sumy wydatków,
kosztu i wkładu własnego. Duże wykładniki i nadmiernie długie liczby są odrzucane
przed obliczeniami. Testy sprawdzają także przypisanie do
CloudMigration bez wyboru konkursu, niezależnie od nazwy naboru w pliku oraz odrzucenie starego
formatu z technicznymi oznaczeniami sekcji.
Parametry XQuery są wiązane; interfejs nie przyjmuje dowolnego kodu zapytania.

## Ograniczenia

Aplikacja jest lokalna, bez logowania. Nie obsługuje dowolnych szablonów Word,
makr, zewnętrznych relacji, odwołań, ponownego otwierania złożonych wniosków ani
zmiany wydanych decyzji. Nie deklaruje odwzorowania DOCX piksel w piksel.
Wygenerowane typy API sprawdzają kod podczas kompilacji, nie stanowią walidatora
odpowiedzi JSON w przeglądarce. Dane do zapisu są walidowane przez backend.

Zrzuty i ślady E2E są lokalne, w ignorowanym `frontend/test-results/`; można je
odtworzyć poleceniem `pnpm test:e2e`. Nie jest dodawana telemetria ani publikowane
żadne dane, zmiany lub artefakty poza lokalnym repozytorium.

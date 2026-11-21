# Testy i weryfikacja

Polecenia uruchamiania testów i wymagania środowiska opisuje
[README](../README.md#rozwój-i-testy).

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

Testy nie używają zastępczej bazy w pamięci. Testy HTTP awarii celowo wskazują
nieaktywny adres BaseX i nie wymagają serwera bazy. Testy integracyjne i E2E
czyszczą wyłącznie wydzieloną bazę testową; nie uruchamiają resetu bazy użytkowej.

## Obrona danych wejściowych

Testy obejmują: błędne XML, DTD/XXE, zła liczba miejsc dziesiętnych, ujemne kwoty,
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
odtworzyć poleceniem `pnpm test:e2e`.

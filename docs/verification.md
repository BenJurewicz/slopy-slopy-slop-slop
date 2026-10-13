# Weryfikacja wydania

## Wyniki

- Backend: **23 testy jednostkowe/HTTP** i **7 testów integracyjnych z BaseX**, bez błędów.
- Frontend: **9 testów**, sprawdzanie Svelte/TypeScript bez błędów i ostrzeżeń.
- Przeglądarka: **5 testów** na pakiecie JAR, bez błędów.
- `pnpm peers check`: brak konfliktów; TypeScript 6.0.3, Hey API 0.99.0.
- Czysty build przez `scripts/build.sh`: frontend i wykonywalny JAR z zasobami.
- Ponowna inicjalizacja bazy: dwa udane wywołania bez utraty dokumentów.
- Restart kontenera BaseX: dokumenty testowe zachowały identyczny skrót treści.
- Microsoft Word **16.92**: szablon otwarty, tytuł zmieniony, plik zapisany do DOCX;
  test importera sprawdził nowy tytuł, polskie znaki, opis wielowierszowy, budżet
  i harmonogram. Fixture: `examples/docx/word-edited-application.docx`.
- Pages: wyłączone z zakresu weryfikacji zgodnie z ustalonym zakresem.

## Pokrycie wymagań

| Obszar | Dowód |
|---|---|
| Cztery XML/XSD i powiązania | `schemas/`, `examples/xml/`, `DocumentCodecTest` |
| Automatycznie wygenerowane klasy | Gradle `generateXmlModels`, test odczytu/edycji/utworzenia JAXB |
| Walidacja struktury, kwot i unikalnych kodów | XSD oraz testy negatywne i `RulesTest` |
| DOCX ↔ XML | `DocxDocumentsTest`, fixture Word, pełny test przeglądarkowy |
| CRUD, rewizje i odwołania w BaseX | `WorkflowIntegrationTest` |
| Złożenie i ocena końcowa | Test przeglądarkowy i zablokowane edycje w integracji |
| Ranking i remisy | Test rzeczywistego porównania dat, następnie ID |
| Zapytania łączące typy dokumentów | Ranking, podsumowanie, decyzje, finansowanie w BaseX |
| Dotacje i atomowość | Odrzucony cały zestaw ponad budżetem lub z nieaktualną rewizją |
| Spójność Java/XQuery finansowania | Porównanie propozycji na zestawie demonstracyjnym |
| Kontrakty i awarie HTTP | `ApiContractTest`, błędny import, HTTP 404/503, stare rewizje |
| Pełny proces w pakiecie | `frontend/e2e/workflow.spec.ts` |
| Responsywność i klawiatura | 1280×720, 390×844, kontrola przepełnienia, Tab/Enter w formularzu |
| Trwałość | Porównanie danych przed i po restarcie BaseX |

Testy nie używają zastępczej bazy w pamięci. Testy HTTP awarii celowo wskazują
nieaktywny adres BaseX i nie wymagają serwera bazy. Testy integracyjne i E2E
czyszczą wyłącznie wydzieloną bazę testową; nie uruchamiają resetu bazy użytkowej.

## Obrona danych wejściowych

Sprawdzone: błędne XML, DTD/XXE, zła liczba miejsc dziesiętnych, ujemne kwoty,
nieprawidłowe sumy, punkty poza zakresem, duplikaty kryteriów, brak oceny końcowej,
niekwalifikowalna firma, błędny DOCX, limit pliku i limit rozpakowanego ZIP,
zewnętrzne relacje, podzielone fragmenty tekstu Word oraz powtórzone wiersze.
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

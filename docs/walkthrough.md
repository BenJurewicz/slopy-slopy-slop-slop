# Demonstracja CloudMigration

## Reguły

Kwalifikują się mikro- i małe firmy (`MICRO`, `SMALL`); rozmiar `OTHER` można
zapisać, ale nie oznaczyć jako kwalifikowalny. Kwoty są netto w PLN. Kryteria,
próg i limity konfiguruje konkurs; domyślnie jest 100 punktów i próg 60.
Warunki kwalifikacji programu są zapisane w XML konkursu jako `eligibilityRules`
i są stałe w tym wydaniu. Po pierwszym wniosku można zmieniać etap konkursu,
nie jego budżet ani kryteria.

Ranking: punkty malejąco, rzeczywisty czas złożenia rosnąco, następnie ID.
Przyznawana jest cała wnioskowana kwota. Projekt niemieszczący się w pozostałym
budżecie jest pomijany; rozpatrywane są kolejne projekty. Szkice decyzji nie
obciążają środków. Wydanie sprawdza ponownie wersje, powiązania i budżet w jednym
aktualizującym XQuery. Błąd dowolnej decyzji wycofuje całą transakcję.

## Pełna ścieżka w interfejsie

1. W **Ustawienia** sprawdź konfigurację CloudMigration (domyślnie budżet
   250 000 PLN, limit 150 000 PLN, próg 60). Konkurs jest tworzony automatycznie.
2. W **Wnioski** pobierz **pusty formularz DOCX**; osobny **wypełniony przykład**
   pokazuje sposób udzielenia odpowiedzi. W Wordzie zastąp wykropkowane pola
   własnymi danymi; kopiuj całe oznaczone bloki, aby dodać pozycje infrastruktury,
   zadania, wydatki i zespół. Uzupełnij adres, NIP, REGON i reprezentanta oraz okres
   realizacji projektu. Oba pola tytułu powinny zawierać ten sam tekst.
   Wielkość firmy: `Mikroprzedsiębiorstwo` lub `Małe przedsiębiorstwo`. Kwoty netto
   można zapisywać jako `1 000,00`, daty jako `2026-11-01` lub `01.11.2026`.
   Wartości wydatków można zostawić puste — aplikacja je wyliczy. Nie usuwaj
   kontrolek odpowiedzi, nie wstawiaj makr ani zewnętrznych odwołań.
3. Zaimportuj DOCX. Wniosek zawsze należy do CloudMigration;
   nie trzeba wybierać konkursu ani wpisywać identyfikatorów w Wordzie.
   Sprawdź komunikat, pola firmy, projekt, harmonogram i budżet. Podgląd nie jest
   jeszcze zapisany. Kliknij **Zapisz szkic**.
4. Otwórz szkic, zmień tytuł lub koszty. Po zmianie kosztów kliknij **Przelicz
   budżet** i zapisz. Pobierz XML i DOCX.
5. Kliknij **Złóż wniosek** i potwierdź nieodwracalność. Wniosek staje się tylko
   do odczytu. Wybierz **Oceń**, wypełnij nazwisko, uzasadnienie i wszystkie
   kryteria. Zapisz, następnie **Zatwierdź ocenę**.
6. W **Ranking i dotacje** kliknij **Przelicz ranking** i **Przygotuj decyzje**.
   Sprawdź kwalifikację, kwoty i uzasadnienia. W **Decyzje** szkice można zmieniać
   lub usuwać. **Wydaj decyzje** zatwierdza cały widoczny zestaw szkiców konkursu.
7. Pobierz decyzje do XML i DOCX. Powiązana ocena i decyzja są dostępne z wniosku.
8. W **Raporty XQuery** wykonaj ranking, podsumowanie i propozycję finansowania.
   Rozwiń kod zapytania; zmień parametry. Kwoty i sumy finansowe liczy backend
   lub XQuery, nie JavaScript.

## Zestaw demonstracyjny

**Przegląd: Wczytaj przykład** działa, gdy nie ma wniosków, ocen ani decyzji.
Korzysta z istniejących ustawień, bez tworzenia kolejnego konkursu. Wymaga
otwartego naboru, limitu dotacji co najmniej 140 000 PLN, sumy kryteriów 100
punktów i progu 60. Tworzy siedem wniosków i sześć ocen końcowych.
Przy domyślnym budżecie 250 000 PLN oczekiwane finansowanie:

| Projekt | Punkty | Wniosek PLN | Wynik |
|---|---:|---:|---|
| Migracja księgowości | 90 | 140 000 | Dotacja |
| E-commerce bezserwerowy | 85 | 130 000 | Brak środków; pominięty |
| Backup małej firmy | 85 | 50 000 | Dotacja |
| Chmurowy system magazynowy | 84 | 65 000 | Brak środków |
| Duże przedsiębiorstwo | 80 | 20 000 | Niekwalifikowalne |
| Projekt poniżej progu | 55 | 30 000 | Poniżej progu |
| Wniosek oczekujący na ocenę | — | 45 000 | Nie uczestniczy w rankingu |

Po wydaniu decyzji: przyznane **190 000 PLN**, pozostałe **60 000 PLN**.
Remis rozstrzyga wcześniejsze złożenie, a przy identycznym czasie ID.

## Błędy do pokazania

- Nie-DOCX lub brak sekcji szablonu: import odrzucony; brak zapisu.
- Ponad 5 MB lub ponad 20 MB rozpakowanych danych: plik odrzucony.
- Zła suma budżetu, koniec zadania przed początkiem lub punkty ponad maksimum:
  błąd walidacji; stary dokument pozostaje w bazie.
- Dwie karty edytują ten sam szkic: zapis ze starą rewizją kończy się HTTP 409.
  Zamknij edytor i otwórz aktualny dokument; zmiany nie są nadpisywane automatycznie.
- Złożonego wniosku, końcowej oceny ani wydanej decyzji nie można edytować/usunąć.
- Usunięcie dokumentu z odwołaniami jest zabronione.
- Baza niedostępna: HTTP 503; interfejs umożliwia odświeżenie.

## Katalog XQuery

`applications`, `over-budget`, `missing-evaluations`, `ranking`, `qualified`,
`summary`, `decisions`, `providers`, `allocation`. Parametry: `threshold` (próg wnioskowanej kwoty), `offset`, `limit` (1-500).
Raport `allocation` realizuje politykę pełnych dotacji również w XQuery;
zgodność z podglądem finansowania jest testowana na rzeczywistej BaseX.

## Przykłady i modele wygenerowane

`examples/xml/` zawiera cztery XSD-poprawne dokumenty. Ich stałe identyfikatory
ilustrują powiązania; fixture wniosku jest szkicem, więc nie jest gotowym stanem
procesu do wydania decyzji. `examples/invalid/` pokazuje ujemny budżet, brak pól i powtórzone kody kryteriów.
W `examples/new-docx/` są oryginały trzech szablonów; `examples/generated-docx/`
zawiera pusty formularz, osobny przykład oraz eksporty oceny i decyzji. Konkurs
ma eksport XML. DOCX nie wymaga identyfikatora konkursu: przypisanie następuje
automatycznie przy imporcie do CloudMigration. Ocena i decyzja pokazują rzeczywiste
nazwy projektu i firmy oraz kontekst powiązanej oceny, odczytane z bazy.

`generateXmlModels` wywołuje XJC przy kompilacji. Testy `DocumentCodecTest`
sprawdzają odczyt, edycję i utworzenie nowego obiektu JAXB oraz ponowną serializację.
Import DOCX rozpoznaje pola po niewidocznych znacznikach kontrolek Word i odczytuje
ich widoczne odpowiedzi. Nie korzysta z ukrytej kopii danych. Stary format z
technicznymi oznaczeniami sekcji nie jest obsługiwany. Ochrona obejmuje DTD/XXE,
zewnętrzne relacje, makra, limity ZIP, głębokość XML i powtórzone ścieżki.

## Reset i trwałość

Zatrzymanie aplikacji ani `docker compose down` nie usuwa dokumentów. Dane i
użytkownicy są w nazwanym wolumenie. Nie używaj `docker compose down -v`, jeżeli
chcesz je zachować.

**Reset usuwa wszystkie dokumenty aplikacji.** Używaj go wyłącznie wtedy, gdy
chcesz odtworzyć fikcyjne dane demonstracyjne. Z katalogu projektu:

```sh
python3 infra/scripts/reset-demo.py --confirm cloudmigration
```

Następnie odśwież aplikację i ponownie wczytaj przykład. Skrypt nie usuwa
wolumenu, bazy ani użytkowników. Bazy testowe nie są danymi demonstracyjnymi.

## Kontrakty HTTP

Ustawienia: `GET /api/competition`, `PUT /api/competition`. Nie ma operacji
utworzenia/usunięcia ani kolekcji konkursów. W pustej bazie pierwszy odczyt tworzy
ustawienia domyślne. Istniejący identyfikator i powiązania pozostają bez zmian.
CRUD dokumentów: `/api/applications`, `/api/evaluations`, `/api/decisions`.
Tworzenie: POST z `revision: 0`; ID nadaje serwer. PUT zawiera ID i aktualną
rewizję; DELETE przekazuje `?revision=N`. Przejścia: `.../submit`, `.../finalize`.
Dokumenty: `/api/documents/template`, `/example`, `/import`, `/{type}/{id}/xml`, `/{type}/{id}/docx`.
Finansowanie: `GET /api/funding`, `POST /api/funding/prepare`,
`POST /api/funding/issue`. Import, ranking i raporty nie przyjmują wyboru konkursu;
backend używa konfiguracji CloudMigration. Wniosek może pominąć `competitionId`
przy tworzeniu; obcy identyfikator jest odrzucany.
Raporty: `/api/queries`, `/api/queries/{id}/execute`. Budżet: `/api/budget/calculate`.

Odpowiedzi błędów: `code`, `message`, `details`. Statusy: 400 niepoprawne żądanie,
404 brak zasobu, 409 konflikt/nieaktualna wersja, 413 rozmiar pliku, 422 walidacja,
503 BaseX niedostępna. Publiczne wdrożenie wymaga dodatkowej ochrony i logowania.

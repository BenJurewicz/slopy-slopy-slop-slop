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
   Wartości wydatków można zostawić puste - aplikacja je wyliczy. Nie usuwaj
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
8. W **Raporty XQuery** wybierz raport i przeczytaj krótki opis, następnie kliknij
   **Pokaż wyniki**. Pole **Dotacja większa niż (PLN)** pojawia się tylko dla
   wniosków powyżej podanej kwoty (porównanie ścisłe, bez równej kwoty).
   Raporty nie zmieniają danych; kod jest dostępny pod **Pokaż kod XQuery**.
   Kwoty i sumy finansowe liczy backend lub XQuery, nie JavaScript.

## Zestaw demonstracyjny

Na dole **Ustawienia**, w sekcji **Dane testowe**, dostępne są dwie operacje.
Obie wymagają potwierdzenia; anulowanie nie zmienia danych.

**Wczytaj dane demonstracyjne** działa również na niepustej bazie. Pyta:
"Czy napewno chcesz usunąć obecne dane i wczytać dane demonstracyjne?".
Po potwierdzeniu zastępuje wszystkie dokumenty i ustawienia świeżym zestawem:
CloudMigration z domyślnym budżetem 250 000 PLN, limitem 150 000 PLN, progiem 60
oraz 20 prostymi projektami testowymi. Dokumenty są walidowane przed zapisem,
a usunięcie starych i zapis nowych odbywają się w jednej transakcji XQuery.
Błąd nie pozostawia częściowo wyczyszczonej bazy. Ponowne wczytanie nie dodaje
duplikatów. Nie powstaje kopia zapasowa.

**Usuń wszystkie wnioski, oceny i decyzje** usuwa także złożone wnioski,
zatwierdzone oceny i wydane decyzje. Zachowuje ustawienia CloudMigration,
które po usunięciu wniosków można ponownie edytować. Operacji nie można cofnąć.

Zestaw demonstracyjny zawiera:

- 19 złożonych wniosków: 17 z oceną końcową, jeden z oceną do zatwierdzenia
  i jeden bez oceny.
- 1 szkic wniosku do edycji i złożenia.
- Przy domyślnym budżecie 250 000 PLN: 13 pełnych dotacji i 4 odmowy.

| Projekty testowe | Wynik przy domyślnym budżecie |
|---|---|
| 01-10, 12, 13, 15 | Dotacja |
| 11, 14 | Brak środków; kolejne mniejsze projekty otrzymują dotację |
| 16 | Poniżej progu punktowego |
| 17 | Niekwalifikowalna firma |
| 18 | Wypełniony szkic oceny, gotowy do zatwierdzenia |
| 19 | Złożony, bez oceny |
| 20 | Szkic wniosku |

Projekt 18 wymaga tylko **Zatwierdź ocenę** w widoku **Oceny**.
Projekt 19 pozostaje bez oceny i można go ocenić przyciskiem **Oceń**.
**Zobacz wypełniony przykład** pobiera osobny projekt **Bezpieczna księgowość w chmurze**:
zaimportuj DOCX, zapisz szkic, złóż wniosek i oceń. Nie jest on częścią 20 projektów
ładowanych automatycznie.

Projekty 02 i 03 mają po 96 punktów, aby sprawdzić rozstrzyganie remisów.
Loader nie tworzy decyzji. Bez zatwierdzania dodatkowych ocen, po przygotowaniu
i wydaniu decyzji przyznane jest
**245 000 PLN**, a pozostałe środki to **5 000 PLN**.

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

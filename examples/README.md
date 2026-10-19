# Przykładowe dokumenty

- `xml/`: dokumenty poprawne względem XSD, ze stałymi przykładowymi powiązaniami.
- `invalid/`: celowo błędne XML do demonstracji walidacji.
- `docx/application-template.docx`: **pusty formularz** wniosku z polskimi podpowiedziami.
- `docx/application.docx` i `docx/wniosek-przyklad-czytelny.docx`: osobny, **wypełniony przykład** wniosku.
- `docx/evaluation.docx`, `docx/decision.docx` oraz odpowiedniki `*-przyklad-czytelny.docx`:
  czytelne przykłady karty oceny i decyzji, z nazwą projektu, firmy i powiązanej oceny.
  Przykłady są szkicami; eksport z aplikacji odzwierciedla rzeczywisty status dokumentu.
- `docx/competition.docx`: informacja o konkursie i kryteriach oceny.

Odtworzenie: `cd ../backend && ./gradlew --offline generateExamples`.
Pliki DOCX używają tego samego generatora co aplikacja, nie są oddzielnymi makietami.

## Wypełnianie i import

W aplikacji wybierz konkurs i pobierz **pusty formularz DOCX**. W Wordzie zastąp
podpowiedzi własnymi odpowiedziami. Aby dodać pozycję, skopiuj cały wiersz tabeli;
nie twórz nowej tabeli ani nie usuwaj kontrolek odpowiedzi. Kwoty netto w PLN można
zapisywać jako `1 000,00`, daty jako `2026-11-01` lub `01.11.2026`. Wielkość firmy:
`Mikroprzedsiębiorstwo` albo `Małe przedsiębiorstwo`. Kolumnę wartości wydatku można
pozostawić pustą — aplikacja przeliczy ją z ilości i ceny. Wpisana wartość musi się zgadzać.

Import przypisuje wniosek do **konkursu aktywnego w aplikacji**, niezależnie od nazwy
naboru wydrukowanej w pliku. Identyfikatory, rewizję i status szkicu nadaje aplikacja.
Zapis następuje dopiero po sprawdzeniu podglądu i wybraniu **Zapisz szkic**.

Formularz nie pokazuje kluczy XML. Niewidoczne znaczniki kontrolek Word służą wyłącznie
do rozpoznania pól; importer odczytuje widoczne odpowiedzi, nie ukrytą kopię danych.
Stare formularze z technicznymi oznaczeniami sekcji nie są już obsługiwane.

## Microsoft Word

`word-edited-application.docx` został otwarty w Microsoft Word 16.92, jego tytuł
zmieniono w Wordzie, a dokument ponownie zapisano. `word-filled-template.docx`
powstał przez wypełnienie pustego formularza w Wordzie. Testy porównują odpowiedzi,
polskie znaki, opis wielowierszowy, budżet, harmonogram i zespół z oczekiwanym modelem.
Metadane autora usunięto; pliki te nie są nadpisywane przez `generateExamples`.
Pages nie jest częścią weryfikacji.

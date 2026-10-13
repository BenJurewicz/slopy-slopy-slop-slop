# Przykładowe dokumenty

`xml/`: dokumenty poprawne względem XSD, ze stałymi przykładowymi powiązaniami.
`invalid/`: celowo błędne XML do demonstracji walidacji.
`docx/`: szablon wniosku oraz eksporty wniosku, oceny, decyzji i konkursu.

Odtworzenie: `cd ../backend && ./gradlew --offline generateExamples`.
Do importu w aplikacji pobierz szablon dla istniejącego konkursu; fixture używa
stałego ID konkursu, który nie jest automatycznie tworzony w bazie.

Przed użytkowaniem w konkretnym edytorze warto otworzyć szablon, zmienić wartości,
zapisać DOCX i sprawdzić import. Nie zmieniaj pierwszej kolumny pól, nagłówków
kolumn ani oznaczeń sekcji w nawiasach kwadratowych.

`word-edited-application.docx` został otwarty, zmieniony i zapisany w Microsoft
Word 16.92. Test importera sprawdza nowy tytuł, polskie znaki, budżet, harmonogram
i opis wielowierszowy. Metadane autora usunięto z fixture; treść dokumentu pozostała
niezmieniona. Ten plik nie jest nadpisywany przez `generateExamples`.

Zgodność z Pages nie jest częścią weryfikacji tego wydania.

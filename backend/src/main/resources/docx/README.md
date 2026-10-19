# Style dokumentów Word

`readable-style.docx` przechowuje style akapitów, znaków i tabel. Część główna jest
pusta: plik nie zawiera przykładowego wniosku ani danych użytkownika. Docx4j ładuje
ten zaufany zasób, a generator tworzy treść dokumentu i ustawia format A4.

`WordFile` buduje nagłówki, tabele i kontrolki odpowiedzi. Podpowiedzi kontrolek
są zapisane jako natywne elementy zastępcze Worda w części glossary; nie stanowią
ukrytej kopii odpowiedzi. `DocxDocuments` odpowiada za polskie etykiety, mapowanie
widocznych odpowiedzi i kontekst powiązanych dokumentów.

Przesłane pliki nie są ładowane do docx4j. Importer skanuje ograniczone archiwum,
bezpiecznie parsuje XML i odczytuje widoczny tekst kontrolek w części głównej.
Nie wykonuje makr, nie pobiera zewnętrznych odwołań i nie odczytuje odpowiedzi
z metadanych ani części customXml.

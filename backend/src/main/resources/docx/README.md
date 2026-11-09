# Szablony Word

Trzy pliki są kopiami dostarczonych dokumentów z `examples/new-docx/`:
`application.docx` — wniosek, `evaluation.docx` — karta oceny,
`decision.docx` — decyzja. Oryginały pozostają niezmienione.

`WordTemplates` wypełnia ich natywne kontrolki, zachowuje układ, style i treść
stałą, powiela bloki pozycji oraz wiersze faktycznych kryteriów konkursu.
`DocxDocuments` mapuje dane XML i powiązane dokumenty na znaczniki szablonów.
Wniosek zawiera adres, NIP, REGON, reprezentanta, numery dokumentu i projektu,
okres realizacji oraz koszt projektu, dotację i wkład własny. Kontrolki tekstowe
obsługują wiele wierszy. Numery kontrolek są unikalne również po powieleniu bloku.

Formularz do pobrania jest pusty; przykład jest oddzielnym dokumentem. Przy
imporcie powtarzające się pola tytułu muszą zawierać ten sam tekst. Aplikacja
przelicza wydatki, koszt i wkład własny; wpisane podsumowania muszą się zgadzać.
Numery nowych dokumentów nadaje system, natomiast numer projektu można podać.

Ocena używa rzeczywistych kryteriów, punktów i komentarzy. Decyzja zapisuje
pełną dotację albo odmowę, bez finansowania częściowego. Dostępność budżetu
odnosi się do chwili przygotowania propozycji; wydanie ponownie sprawdza środki
atomowo w BaseX. Odmowa nie zawiera sekcji obowiązków i wypłat dla beneficjenta.
Szkic i dokument zatwierdzony mają jednoznaczny status.

Przesłane pliki nie są ładowane do docx4j. Importer skanuje ograniczone archiwum,
bezpiecznie parsuje XML i odczytuje widoczny tekst kontrolek części głównej.
Nie odczytuje odpowiedzi z metadanych ani customXml. Makra, zewnętrzne relacje,
DTD/XXE, niejednoznaczne ścieżki i nadmiernie duże lub głębokie pliki są odrzucane.
Wcześniejsze formaty formularzy nie mają parsera zastępczego.

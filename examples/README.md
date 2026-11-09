# Przykładowe dokumenty

- `new-docx/`: trzy dostarczone oryginały Word i lista znaczników.
- `generated-docx/application-template.docx`: pusty formularz na podstawie oryginału.
- `generated-docx/application.docx`: osobny wypełniony przykład.
- `generated-docx/evaluation.docx`, `generated-docx/decision.docx`: przykładowa
  karta oceny i decyzja. To szkice; eksport z aplikacji pokazuje rzeczywisty status.
- `xml/`: cztery dokumenty zgodne z XSD v2, z przykładowymi powiązaniami.
- `invalid/`: celowo błędne XML do demonstracji walidacji.

Odtworzenie: `cd ../backend && ./gradlew --offline generateExamples`.
Generator nie nadpisuje oryginałów ani plików zapisanych w Wordzie do testów.
Nie ma osobnego formularza DOCX konkursu; konkurs eksportuje się do XML.

## Wypełnianie i import

Wybierz konkurs w aplikacji i pobierz **pusty formularz DOCX**. W Wordzie zastąp
wykropkowane pola odpowiedziami, nie usuwając kontrolek. Uzupełnij oba wystąpienia
tytułu projektu jednakowo. Wpisz adres, dziesięciocyfrowy NIP, dziewięcio- lub
czternastocyfrowy REGON oraz osobę reprezentującą beneficjenta. Wielkość firmy:
`Mikroprzedsiębiorstwo` albo `Małe przedsiębiorstwo`.

Aby dodać infrastrukturę, zadanie, wydatek lub osobę, skopiuj cały oznaczony blok
razem z kontrolkami, nie pojedynczą linię tekstu. Co najmniej jeden blok każdej
sekcji musi być wypełniony. Kwoty netto w PLN można zapisywać jako `1 000,00`,
daty jako `2026-11-01` lub `01.11.2026`. Zadania muszą mieścić się w okresie
realizacji projektu. Wartości wydatków, koszt całkowity i wkład własny można
pozostawić puste do obliczenia. Wpisane podsumowania muszą się zgadzać.

Import przypisuje wniosek do **konkursu wybranego w aplikacji**, nie nazwy naboru
wydrukowanej w pliku. System nadaje nową tożsamość, numer wniosku, rewizję i stan
szkicu. Numer projektu można podać; pusty numer również zostanie nadany.
Po imporcie sprawdź podgląd i wybierz **Zapisz szkic**.

## Microsoft Word

W `backend/src/test/resources/docx/` są aktualne formularze otwarte i zapisane
w Microsoft Word 16.92: pusty, wypełniony oraz wypełniony z tytułem zmienionym
w Wordzie. Testy sprawdzają zachowanie kontrolek, odpowiedzi, polskich znaków,
opisu wielowierszowego, budżetu, harmonogramu i danych beneficjenta. Metadane
autora usunięto. Pages nie jest częścią weryfikacji.

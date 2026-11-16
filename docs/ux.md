# Interfejs i weryfikacja

Użytkownik: osoba obsługująca lokalny konkurs; bez logowania. Główne zadanie:
przeprowadzić poprawny wniosek od pliku DOCX do wydanej decyzji. Sukces jest
widoczny jako zapis dokumentu, końcowa ocena, wydana decyzja i możliwy eksport.
Nie jest zbierana telemetria; kompletność ścieżki sprawdzają testy przeglądarkowe.

Prosta nawigacja, natywne kontrolki, tabele i polskie etykiety. Bez dekoracyjnych
kart i opisów procesu. Aplikacja obsługuje tylko CloudMigration: nie ma selektora
konkursu ani tworzenia/usuwania konkursów. W **Ustawienia** można zmienić budżet,
kryteria i etap; po dodaniu wniosków edytowalny jest wyłącznie etap.
Widoki oddzielają szkice od operacji nieodwracalnych.

Pokryte stany: pusta baza, pusty wynik filtrów, ładowanie, poprawny zapis, błędny
import, walidacja, konflikt rewizji, baza niedostępna, tylko odczyt po zatwierdzeniu.
Formularze mają etykiety i natywne kontrolki; nawigacja i akcje działają klawiaturą.
Tabele przewijają się we własnym kontenerze na małych ekranach.

Test `frontend/e2e/workflow.spec.ts` przechodzi pełną ścieżkę w zbudowanym JAR,
pobiera XML/DOCX, wykonuje raport i zapisuje obrazy 1280x720 oraz 390x844.
Brak poziomego przepełnienia całej strony jest sprawdzany automatycznie.

## Formularze Word

Domyślny plik do pobrania jest pustym formularzem, nie gotowym przykładem. Obok
znajduje się osobny wypełniony wzór. Dokumenty mają polskie etykiety, czytelne
tabele i format A4; identyfikatory XML nie są częścią treści do wypełnienia.
Import zawsze przypisuje wniosek do CloudMigration. Brak odpowiedzi wskazuje konkretne polskie pole.
Oceny i decyzje prezentują dane rzeczywiście powiązanych dokumentów oraz
rozróżniają szkic, ocenę końcową i wydaną decyzję. Podgląd wydruku sprawdzono
w Microsoft Word, razem z wypełnieniem pustego formularza i ponownym importem.

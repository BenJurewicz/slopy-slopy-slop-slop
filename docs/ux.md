# Interfejs i weryfikacja

Użytkownik: osoba obsługująca lokalny konkurs; bez logowania. Główne zadanie:
przeprowadzić poprawny wniosek od pliku DOCX do wydanej decyzji. Sukces jest
widoczny jako zapis dokumentu, końcowa ocena, wydana decyzja i możliwy eksport.
Nie jest zbierana telemetria; kompletność ścieżki sprawdzają testy przeglądarkowe.

Priorytety: operacyjność, czytelność, następnie charakter programu. Kierunek:
ciemnozielona nawigacja, jasne powierzchnie, miedziany akcent; polskie etykiety,
statusy i kwoty. Widoki oddzielają szkice od operacji nieodwracalnych.

Pokryte stany: pusta baza, pusty wynik filtrów, ładowanie, poprawny zapis, błędny
import, walidacja, konflikt rewizji, baza niedostępna, tylko odczyt po zatwierdzeniu.
Formularze mają etykiety i natywne kontrolki; nawigacja i akcje działają klawiaturą.
Tabele przewijają się we własnym kontenerze na małych ekranach.

Test `frontend/e2e/workflow.spec.ts` przechodzi pełną ścieżkę w zbudowanym JAR,
pobiera XML/DOCX, wykonuje raport i zapisuje obrazy 1280×720 oraz 390×844.
Brak poziomego przepełnienia całej strony jest sprawdzany automatycznie.

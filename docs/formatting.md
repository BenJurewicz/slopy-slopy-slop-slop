# Formatowanie i lint

## Wspólne ustawienia

`.editorconfig` ustala UTF-8, LF, końcowy znak nowej linii, cztery spacje i cel
80 kolumn. Makefile zachowuje Tab. Limit szerokości jest celem formatterów,
nie limitem tekstu użytkownika ani zakazem dłuższych literałów.

| Źródła                                    | Narzędzie                            | Konfiguracja                        |
| ----------------------------------------- | ------------------------------------ | ----------------------------------- |
| Java                                      | Spotless 8.11.0, Eclipse 4.39        | `backend/eclipse-formatter.xml`     |
| Gradle Kotlin                             | ktlint 1.8.0 przez Spotless          | `.editorconfig`, `build.gradle.kts` |
| TS, JS, Svelte, CSS, JSON, YAML, Markdown | Prettier 3.10.1, plugin Svelte 4.1.1 | `prettier.config.mjs`               |
| XML i XSD                                 | Prettier, plugin XML 3.4.2           | `prettier.config.mjs`               |
| Python                                    | Ruff 0.15.8                          | `pyproject.toml`                    |
| Bash                                      | shfmt 3.13.0                         | `.editorconfig`                     |
| Lua                                       | StyLua 2.3.1                         | `.stylua.toml`                      |

Java i Kotlin są obsługiwane przez osobny projekt Gradle w katalogu głównym,
bez zmiany konfiguracji wykonawczej aplikacji. Profil Eclipse obejmuje także
`infra/scripts/ValidateXml.java`.

Szablony Svelte rozdzielają znaczniki i bloki sterujące na osobne wiersze.
Nadpisanie `htmlWhitespaceSensitivity: 'ignore'` zapobiega sklejaniu znaczników
przez Prettier. Po zmianie białych znaków sprawdzaj również renderowany widok,
zwłaszcza tekst przy elementach liniowych.

ESLint używa zalecanych reguł JavaScript, TypeScript i Svelte. Konfiguracja
Prettier wyłącza kolidujące reguły formatowania. Jedyny lokalny wyjątek
`prefer-writable-derived` w edytorze zachowuje głęboko reaktywną, edytowalną
kopię formularza i aktualizowane na żywo sumy punktów. Powód jest przy deklaracji.

## Polecenia

Z `Project/`:

```sh
make format
make format-check
make lint
make check
```

`make check` nie wymaga BaseX: sprawdza styl, lint, typy frontendu, Vitest oraz
testy jednostkowe/HTTP backendu. Testy integracyjne i przeglądarkowe uruchamia
się osobno zgodnie z README. `scripts/build.sh` wymaga przejścia kontroli
formatowania i lint przed budowaniem zasobów oraz JAR.

Z `frontend/` można użyć `pnpm format`, `pnpm format:check`, `pnpm lint` oraz
świadomie `pnpm lint:fix`. Automatyczne zapisy edytora nie uruchamiają ESLint
z `--fix`. Zmiany stylu i naprawy lint należy rozdzielać w commitach.

Skrypty nie instalują zależności ani nie pobierają Gradle. `scripts/gradle.sh`
wybiera JDK 23 na macOS, jeśli `JAVA_HOME` nie jest ustawione, i uruchamia
zapisaną dystrybucję Wrappera z `--offline`. Pierwsze przygotowanie cache
opisano w README. Ruff, shfmt i StyLua są wyszukiwane w `PATH`, następnie
w istniejącym `nvim/mason/bin`. Można wskazać istniejące pliki przez
`RUFF_BIN`, `SHFMT_BIN`, `STYLUA_BIN`. Nie jest potrzebny globalny pakiet npm.

## Zakres i wyłączenia

Skrypt obejmuje istniejące pliki śledzone oraz nowe pliki nieignorowane przez
Git. `.prettierignore` i cele Spotless wykluczają wygenerowany klient API,
JAXB, katalogi build/cache, zależności, lockfile, dane prywatne i fixtures.
Przykłady w `examples/`, zasoby testowe i dokumenty DOCX nie są zmieniane.
Wyłączenia fixtures obowiązują również pomocnicze formattery Python/Bash/Lua.
XQuery nie ma w tym zestawie uzgodnionego formattera; powyższe polecenia go nie
zmieniają.

Polskie litery są zwykłą treścią. Kosmetyczne cudzysłowy i myślniki zastąpiono
ASCII. Znaki wymagane przez rzeczywiste fixtures Word i testy regresyjne
pozostają jako jawne sekwencje Java `\u00A0`, `\u2013`, `\u2014`.

## Neovim

Globalna konfiguracja i lista pluginów pozostają bez zmian. Plik `.nvim.lua`
działa tylko w bieżącej sesji i dla plików tego projektu. Korzysta z już
zainstalowanych Conform, nvim-lint, formatterów oraz Java LSP `jdtls`.
Dla skryptów Gradle potrzebny jest istniejący ktlint 1.8.0 w `PATH`/Mason.

Z katalogu `Project/`:

```sh
nvim --cmd 'set exrc' .
```

Przeczytaj lokalny plik i potwierdź zaufanie, jeśli Neovim o nie poprosi.
Alternatywnie w otwartej sesji z tego katalogu wykonaj `:luafile .nvim.lua`.
Nie trzeba zmieniać `~/.config/nvim` ani instalować nowego pluginu.

Conform wybiera lokalny Prettier oraz wspólne konfiguracje pozostałych
formatterów zamiast formatowania TypeScript przez LSP. ESLint korzysta
z lokalnego pliku wykonywalnego i publikuje diagnostykę po wejściu do bufora
oraz zapisie pliku frontendu. `jdtls` dostaje profil `CloudMigration`
z tego samego XML co Spotless. Obecna instalacja JDTLS i Eclipse 4.39 używają
JDT 3.45.0; po aktualizacji JDTLS należy ponownie sprawdzić zgodność silnika.
Automatyczne formatowanie wygenerowanych plików, fixtures, prywatnych plików
`.env` i nieobsługiwanego XQuery jest wyłączone.
Pliki spoza projektu zachowują dotychczasowe ustawienia.

```sh
make editor-check
```

Test korzysta z istniejących modułów LazyVim/Mason, ale nie ładuje globalnego
`init.lua`, nie startuje serwerów LSP i nie zapisuje buforów. Sprawdza wybór
formatterów, zgodność ustawień, wyłączenia, zakres projektu, powiadomienie
z profilem Java i rzeczywistą diagnostykę ESLint.

#!/usr/bin/env bash
# Format Git-visible source only; never install tools or touch generated fixtures.
set -euo pipefail
cd "$(dirname "$0")/.."
mode=${1:-check}
[[ "$mode" == check || "$mode" == write ]] || {
    printf 'Usage: bash scripts/format.sh [check|write]\n' >&2
    exit 2
}
resolve_tool() {
    local configured=$1 name=$2
    if [[ -n "$configured" ]]; then
        command -v "$configured"
    elif command -v "$name" >/dev/null; then
        command -v "$name"
    else
        local mason="${XDG_DATA_HOME:-$HOME/.local/share}/nvim/mason/bin/$name"
        if [[ -x "$mason" ]]; then
            printf '%s\n' "$mason"
        else
            printf 'Missing %s. Use an existing installation; no automatic installs are performed.\n' "$name" >&2
            return 1
        fi
    fi
}
prettier=frontend/node_modules/.bin/prettier
[[ -x "$prettier" ]] || {
    printf 'Install the approved frontend dependencies first.\n' >&2
    exit 1
}
ruff=$(resolve_tool "${RUFF_BIN:-}" ruff)
shfmt=$(resolve_tool "${SHFMT_BIN:-}" shfmt)
stylua=$(resolve_tool "${STYLUA_BIN:-}" stylua)
[[ "$("$shfmt" --version)" == v3.13.0 ]] || {
    printf 'Use shfmt v3.13.0.\n' >&2
    exit 1
}
[[ "$("$stylua" --version)" == 'stylua 2.3.1' ]] || {
    printf 'Use StyLua 2.3.1.\n' >&2
    exit 1
}
collect() {
    files=()
    count=0
    while IFS= read -r -d '' file; do
        case "$file" in
        examples/* | */src/test/resources/* | frontend/src/lib/generated/* | */build/* | */node_modules/* | */.svelte-kit/* | .gradle/* | */.gradle/*) continue ;;
        esac
        if [[ -f "$file" ]]; then
            files+=("$file")
            count=$((count + 1))
        fi
    done < <(git ls-files -z --cached --others --exclude-standard -- "$@")
}
collect
"$prettier" "--$mode" --ignore-unknown "${files[@]}"
if [[ "$mode" == write ]]; then
    bash scripts/gradle.sh spotlessApply --console=plain
else
    bash scripts/gradle.sh spotlessCheck --console=plain
fi
collect '*.py'
if [[ "$count" != 0 ]]; then
    if [[ "$mode" == write ]]; then "$ruff" format "${files[@]}"; else "$ruff" format --check "${files[@]}"; fi
fi
collect '*.sh'
if [[ "$count" != 0 ]]; then
    if [[ "$mode" == write ]]; then "$shfmt" -w "${files[@]}"; else "$shfmt" -d "${files[@]}"; fi
fi
collect '*.lua'
if [[ "$count" != 0 ]]; then
    if [[ "$mode" == write ]]; then "$stylua" "${files[@]}"; else "$stylua" --check "${files[@]}"; fi
fi

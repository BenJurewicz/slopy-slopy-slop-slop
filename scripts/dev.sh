#!/usr/bin/env bash
set -euo pipefail
# Job control gives each launcher its own process group, including its children.
set -m
cd "$(dirname "$0")/.."

fail() { printf 'make run: %s\n' "$*" >&2; exit 1; }
for command in docker pnpm node lsof; do
    command -v "$command" >/dev/null || fail "Missing $command; complete the first-time setup in README.md."
done
for file in backend/.env infra/.env frontend/node_modules/.bin/vite; do
    test -e "$file" || fail "Missing $file; complete the first-time setup in README.md."
done
if [[ -z "${JAVA_HOME:-}" && "$(uname -s)" == Darwin ]]; then
    export JAVA_HOME
    JAVA_HOME=$(/usr/libexec/java_home -v 23)
fi
java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
"$java" -version 2>&1 | grep -Eq 'version "23(\.|\")' || fail 'Set JAVA_HOME to JDK 23.'

# --offline does not stop the wrapper from downloading Gradle itself. Use only
# the already cached distribution pinned by gradle-wrapper.properties.
distribution=$(sed -n 's|^distributionUrl=.*/\([^/]*\)\.zip$|\1|p' backend/gradle/wrapper/gradle-wrapper.properties)
version=${distribution%-bin}; version=${version%-all}
gradle=''
for candidate in "${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/$distribution"/*/"$version/bin/gradle"; do
    if [[ -x "$candidate" ]]; then gradle=$candidate; break; fi
done
[[ -n "$gradle" ]] || fail 'Gradle is not cached; complete the first-time setup in README.md.'
gradle="$(cd "$(dirname "$gradle")" && pwd)/gradle"
for port in 8080 5173; do
    if lsof -nP -iTCP:"$port" -sTCP:LISTEN -t >/dev/null; then
        fail "Port $port is occupied. Stop its existing server before make run."
    fi
done
export COREPACK_ENABLE_NETWORK=0 npm_config_manage_package_manager_versions=false

compose=(docker compose -f infra/compose.yaml)
pids=()
database_started=0
cleanup() {
    status=$?
    trap - EXIT
    trap '' INT TERM
    set +eu # Bash 3.2 treats an empty array as unset under nounset.
    printf '\nStopping development servers...\n'
    for pid in "${pids[@]}"; do kill -TERM -- "-$pid" 2>/dev/null; done
    for ((i=0; i<50; i++)); do
        alive=0
        for pid in "${pids[@]}"; do kill -0 -- "-$pid" 2>/dev/null && alive=1; done
        [[ "$alive" == 0 ]] && break
        sleep 0.2
    done
    for pid in "${pids[@]}"; do
        kill -KILL -- "-$pid" 2>/dev/null
        wait "$pid" 2>/dev/null
    done
    if [[ "$database_started" == 1 ]]; then
        "${compose[@]}" stop --timeout 10 basex || status=1
    fi
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

database_started=1
"${compose[@]}" up -d --pull never --no-build --wait basex &
pids+=("$!")
wait "${pids[0]}"
pids=()
(cd backend && exec "$gradle" --offline --no-daemon --console=plain bootRun) &
pids+=("$!")
(cd frontend && exec pnpm dev --host 127.0.0.1 --port 5173 --strictPort) &
pids+=("$!")
printf '\nFrontend: http://127.0.0.1:5173\nBackend:  http://127.0.0.1:8080\nCtrl+C stops both servers and BaseX; database data is preserved.\n\n'
while true; do
    for pid in "${pids[@]}"; do
        if ! kill -0 "$pid" 2>/dev/null; then
            if wait "$pid"; then status=1; else status=$?; fi
            printf 'Development server exited; stopping the stack.\n' >&2
            exit "$status"
        fi
    done
    sleep 0.2
done

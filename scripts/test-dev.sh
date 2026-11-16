#!/usr/bin/env bash
# Lifecycle checks with fake launchers: never contact Docker or the application DB.
set -euo pipefail
set -m
cd "$(dirname "$0")/.."
fixture=$(mktemp -d)
runner=''
cleanup() {
    if [[ -n "$runner" ]]; then kill -TERM -- "-$runner" 2>/dev/null || true; wait "$runner" 2>/dev/null || true; fi
    rm -rf "$fixture"
}
trap cleanup EXIT
mkdir -p "$fixture/bin" "$fixture/jdk/bin" "$fixture/log"
export TEST_LOG="$fixture/log"
export JAVA_HOME="$fixture/jdk" GRADLE_USER_HOME="$fixture/gradle"
export PATH="$fixture/bin:$PATH"
cat > "$fixture/bin/bash" <<'EOF'
#!/bin/sh
exec /bin/bash "$@"
EOF
cat > "$JAVA_HOME/bin/java" <<'EOF'
#!/bin/sh
echo 'openjdk version "23.0.1"' >&2
EOF
cat > "$fixture/bin/lsof" <<'EOF'
#!/bin/sh
[ "${TEST_BUSY:-0}" = 1 ] && exit 0
exit 1
EOF
cat > "$fixture/bin/docker" <<'EOF'
#!/bin/bash
printf '%s\n' "$*" >> "$TEST_LOG/docker"
case " $* " in
    *' up '*)
        if [[ "${TEST_STARTUP_FAIL:-0}" == 1 ]]; then exit 3; fi
        if [[ "${TEST_BLOCK_STARTUP:-0}" == 1 ]]; then
            trap 'wait; exit 0' TERM
            sleep 3600 & echo $! > "$TEST_LOG/startup.child"
            wait
        fi ;;
esac
EOF
cat > "$fixture/bin/pnpm" <<'EOF'
#!/bin/bash
name=frontend
[[ "$0" == */gradle ]] && name=backend
echo $$ > "$TEST_LOG/$name.pid"
printf '%s\n' "$*" > "$TEST_LOG/$name.args"
if [[ "$name" == frontend && "${TEST_FAIL_FRONTEND:-0}" == 1 ]]; then exit 7; fi
trap 'wait; exit 0' TERM
sleep 3600 & echo $! > "$TEST_LOG/$name.child"
wait
EOF
distribution=$(sed -n 's|^distributionUrl=.*/\([^/]*\)\.zip$|\1|p' backend/gradle/wrapper/gradle-wrapper.properties)
version=${distribution%-bin}; version=${version%-all}
cache="$GRADLE_USER_HOME/wrapper/dists/$distribution/cached/$version/bin"
mkdir -p "$cache"
cp "$fixture/bin/pnpm" "$cache/gradle"
chmod +x "$fixture/bin/"* "$JAVA_HOME/bin/java" "$cache/gradle"
wait_file() {
    for ((i=0; i<100; i++)); do [[ -s "$1" ]] && return; sleep 0.05; done
    cat "$TEST_LOG/output"; echo "Timed out waiting for $1" >&2; exit 1
}
finish() {
    for ((i=0; i<200; i++)); do kill -0 "$runner" 2>/dev/null || break; sleep 0.05; done
    if kill -0 "$runner" 2>/dev/null; then cat "$TEST_LOG/output"; echo 'Runner did not stop' >&2; exit 1; fi
    wait "$runner" 2>/dev/null || true
    runner=''
    grep -q 'stop --timeout 10 basex' "$TEST_LOG/docker"
    for file in "$TEST_LOG/"*.pid "$TEST_LOG/"*.child; do
        [[ -f "$file" ]] || continue
        if kill -0 "$(cat "$file")" 2>/dev/null; then echo "Process survived: $file" >&2; exit 1; fi
    done
}

make run > "$TEST_LOG/output" 2>&1 & runner=$!
wait_file "$TEST_LOG/backend.child"; wait_file "$TEST_LOG/frontend.child"
grep -q -- '--offline --no-daemon --console=plain bootRun' "$TEST_LOG/backend.args"
grep -q -- '--strictPort' "$TEST_LOG/frontend.args"
grep -q -- '--pull never --no-build --wait basex' "$TEST_LOG/docker"
kill -INT -- "-$runner"
finish
printf 'PASS: make run + Ctrl+C stops launchers, descendants and BaseX.\n'

rm -f "$TEST_LOG/"*
TEST_BLOCK_STARTUP=1 make run > "$TEST_LOG/output" 2>&1 & runner=$!
wait_file "$TEST_LOG/startup.child"
kill -INT -- "-$runner"
finish
printf 'PASS: Ctrl+C during database startup cleans up.\n'

rm -f "$TEST_LOG/"*
TEST_FAIL_FRONTEND=1 make run > "$TEST_LOG/output" 2>&1 & runner=$!
finish
printf 'PASS: server failure stops the other server and BaseX.\n'

rm -f "$TEST_LOG/"*
if TEST_STARTUP_FAIL=1 make run > "$TEST_LOG/output" 2>&1; then echo 'Expected startup failure' >&2; exit 1; fi
grep -q 'stop --timeout 10 basex' "$TEST_LOG/docker"
[[ ! -e "$TEST_LOG/backend.pid" ]]
printf 'PASS: failed database startup is cleaned up.\n'

rm -f "$TEST_LOG/"*
if TEST_BUSY=1 make run > "$TEST_LOG/output" 2>&1; then echo 'Expected port conflict' >&2; exit 1; fi
[[ ! -e "$TEST_LOG/docker" ]]
printf 'PASS: occupied ports fail before any database action.\n'

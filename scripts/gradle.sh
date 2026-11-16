#!/usr/bin/env bash
# Run the pinned, already-cached Gradle distribution without wrapper downloads.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
if [[ -z "${JAVA_HOME:-}" && "$(uname -s)" == Darwin ]]; then
    export JAVA_HOME
    JAVA_HOME=$(/usr/libexec/java_home -v 23)
fi
java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
"$java" -version 2>&1 | grep -Eq 'version "23(\.|\")' || {
    printf 'Set JAVA_HOME to JDK 23.\n' >&2
    exit 1
}
distribution=$(sed -n 's|^distributionUrl=.*/\([^/]*\)\.zip$|\1|p' "$root/backend/gradle/wrapper/gradle-wrapper.properties")
version=${distribution%-bin}
version=${version%-all}
for candidate in "${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/$distribution"/*/"$version/bin/gradle"; do
    if [[ -x "$candidate" ]]; then
        exec "$candidate" --offline "$@"
    fi
done
printf 'Pinned Gradle is not cached; complete the first-time setup in README.md.\n' >&2
exit 1

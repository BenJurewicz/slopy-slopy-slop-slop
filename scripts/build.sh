#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
(cd frontend && pnpm check && pnpm test && pnpm build)
(cd backend && ./gradlew --offline clean test bootJar --console=plain)
printf '\nStart: cd backend && java -jar build/libs/cloudmigration-backend-0.0.1-SNAPSHOT.jar\n'

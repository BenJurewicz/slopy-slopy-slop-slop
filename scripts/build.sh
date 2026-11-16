#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
make format-check lint
(cd frontend && pnpm check && pnpm test && pnpm build)
(cd backend && bash ../scripts/gradle.sh clean test bootJar --console=plain)
printf '\nStart: cd backend && java -jar build/libs/cloudmigration-backend-0.0.1-SNAPSHOT.jar\n'

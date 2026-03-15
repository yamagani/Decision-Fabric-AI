#!/usr/bin/env bash
# run-local.sh — build, test, and start Decision Fabric AI via Docker Compose
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
JAVA_HOME_21="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"

##############################################################################
# Helpers
##############################################################################
info()    { echo -e "\033[1;34m[INFO]\033[0m  $*"; }
success() { echo -e "\033[1;32m[OK]\033[0m    $*"; }
warn()    { echo -e "\033[1;33m[WARN]\033[0m  $*"; }
error()   { echo -e "\033[1;31m[ERROR]\033[0m $*" >&2; exit 1; }

usage() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Options:
  --skip-tests       Skip Gradle test phase
  --build-only       Build & test but do NOT start docker-compose
  --keycloak         Also start local Keycloak (docker-compose.dev.yml overlay)
                     Use when you have no organisation SSO to point the app at.
                     Omit this flag in production-like setups — set JWT_ISSUER_URI,
                     JWT_JWKS_URI and JWT_ROLES_CLAIM env vars instead.
  --down             Tear down running docker-compose stack and exit
  --logs             Tail logs of the running stack (no build)
  -h, --help         Show this help
EOF
  exit 0
}

##############################################################################
# Arg parsing
##############################################################################
SKIP_TESTS=false
BUILD_ONLY=false
DO_DOWN=false
DO_LOGS=false
USE_KEYCLOAK=false

for arg in "$@"; do
  case "$arg" in
    --skip-tests)  SKIP_TESTS=true ;;
    --build-only)  BUILD_ONLY=true ;;
    --keycloak)    USE_KEYCLOAK=true ;;
    --down)        DO_DOWN=true ;;
    --logs)        DO_LOGS=true ;;
    -h|--help)     usage ;;
    *) warn "Unknown argument: $arg" ;;
  esac
done

# Build the compose file list: base always included; dev overlay only with --keycloak
COMPOSE_FILES="-f $ROOT/docker-compose.yml"
if $USE_KEYCLOAK; then
  COMPOSE_FILES="$COMPOSE_FILES -f $ROOT/docker-compose.dev.yml"
fi

##############################################################################
# Shortcuts: --down / --logs
##############################################################################
if $DO_DOWN; then
  info "Tearing down docker-compose stack…"
  # shellcheck disable=SC2086
  docker compose $COMPOSE_FILES down -v
  success "Stack stopped and volumes removed."
  exit 0
fi

if $DO_LOGS; then
  # shellcheck disable=SC2086
  docker compose $COMPOSE_FILES logs -f app
  exit 0
fi

##############################################################################
# Prerequisites
##############################################################################
command -v docker >/dev/null 2>&1 || error "docker is not installed or not in PATH"
docker info >/dev/null 2>&1      || error "Docker daemon is not running"
command -v docker compose >/dev/null 2>&1 || \
  docker-compose version >/dev/null 2>&1  || error "docker compose (v2) is required"

# Prefer JAVA_HOME_21 if it exists, else fall back to whatever JAVA_HOME is set
if [[ -d "$JAVA_HOME_21" ]]; then
  export JAVA_HOME="$JAVA_HOME_21"
fi
export PATH="$JAVA_HOME/bin:$PATH"
java -version 2>&1 | head -1

##############################################################################
# Gradle build + tests
##############################################################################
cd "$ROOT"

if $SKIP_TESTS; then
  info "Running Gradle build (tests skipped)…"
  ./gradlew clean build -x test --no-daemon
else
  info "Running Gradle build + unit/slice tests…"
  # Exclude Testcontainers-based integration tests that need a live DB
  ./gradlew clean build --no-daemon \
    -x test                                         # skip during assemble

  info "Running unit + slice tests (no Docker DB required)…"
  ./gradlew test --no-daemon \
    --tests "com.decisionfabric.domain.*" \
    --tests "com.decisionfabric.application.*" \
    --tests "com.decisionfabric.adapter.inbound.*" \
    --tests "com.decisionfabric.adapter.outbound.cache.*" \
    && success "All unit/slice tests passed." \
    || warn "Some tests failed — check build/reports/tests/test/index.html"
fi

if $BUILD_ONLY; then
  success "Build complete. Skipping docker-compose (--build-only)."
  exit 0
fi

##############################################################################
# Docker image build
##############################################################################
info "Building Docker image decision-fabric-ai:local…"
docker build --target runtime -t decision-fabric-ai:local "$ROOT"
success "Docker image built."

##############################################################################
# Docker Compose up
##############################################################################
if $USE_KEYCLOAK; then
  info "Starting stack (postgres + keycloak + app)…"
else
  info "Starting stack (postgres + app)…"
  info "No --keycloak flag: set JWT_ISSUER_URI, JWT_JWKS_URI and JWT_ROLES_CLAIM"
  info "env vars (or a .env file) to point the app at your organisation's SSO."
fi
# shellcheck disable=SC2086
docker compose $COMPOSE_FILES up -d --remove-orphans

info "Waiting for app to become healthy…"
max_wait=120
elapsed=0
# shellcheck disable=SC2086
until docker compose $COMPOSE_FILES ps app \
      | grep -qE "healthy|(running)"; do
  sleep 3
  elapsed=$((elapsed + 3))
  if [[ $elapsed -ge $max_wait ]]; then
    warn "App did not become healthy within ${max_wait}s — showing recent logs:"
    # shellcheck disable=SC2086
    docker compose $COMPOSE_FILES logs --tail 40 app
    exit 1
  fi
done

success "Stack is up!"
echo ""
echo "  App:       http://localhost:8080"
echo "  Health:    http://localhost:8080/actuator/health"
if $USE_KEYCLOAK; then
  echo "  Keycloak:  http://localhost:9090  (admin / admin)"
  echo "  Postgres:  localhost:5432  (decisionfabric / decisionfabric)"
  echo ""
  echo "  Get a token:"
  echo "    TOKEN=\$(curl -s -X POST \\"
  echo "      http://localhost:9090/realms/decision-fabric-ai/protocol/openid-connect/token \\"
  echo "      -d 'client_id=decision-fabric-ai-client&client_secret=local-secret' \\"
  echo "      -d 'grant_type=password&username=rule-admin&password=password' \\"
  echo "      | jq -r .access_token)"
else
  echo "  Postgres:  localhost:5432  (decisionfabric / decisionfabric)"
  echo ""
  echo "  Obtain a token from your SSO provider and pass it as:"
  echo "    -H 'Authorization: Bearer <token>'"
fi
echo ""
echo "  Tail logs: ./run-local.sh --logs"
echo "  Stop:      ./run-local.sh --down"

#!/usr/bin/env bash
# Deploy tacticl-api to platform-apps (Hetzner)
# Usage: ./scripts/deploy.sh [prod|qa|both]
# Default: both
#
# NOTE: tacticl-core Dockerfile expects a pre-built JAR (COPY application-api/build/libs/*.jar)
# This script runs Gradle locally, transfers the JAR, builds the image on the host, restarts containers.
set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

ENV=${1:-both}
HOST="platform-apps"            # SSH alias from ~/.ssh/config (178.156.253.208)
IMAGE_TAG="tacticl-api:patched" # docker-compose.yml on the host pins this tag for prod and qa
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo -e "${BLUE}=== Building tacticl-api ===${NC}"
cd "$REPO_ROOT"

if [[ "$ENV" != "prod" && "$ENV" != "qa" && "$ENV" != "both" ]]; then
    echo -e "${RED}Usage: $0 [prod|qa|both]${NC}"
    exit 1
fi

if [[ "$ENV" == "prod" || "$ENV" == "both" ]]; then
    echo -e "${RED}WARNING: Deploying tacticl-api to PRODUCTION ($HOST).${NC}"
    read -p "Continue? (y/N) " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 0
    fi
fi

if ! ssh -o ConnectTimeout=5 "$HOST" "echo ok" &>/dev/null; then
    echo -e "${RED}Error: Cannot reach $HOST${NC}"
    exit 1
fi

echo -e "${YELLOW}Building JAR with Gradle...${NC}"
chmod +x "$REPO_ROOT/gradlew"
"$REPO_ROOT/gradlew" -p "$REPO_ROOT" clean :application-api:bootJar -x test --no-daemon
echo -e "${GREEN}Gradle build succeeded${NC}"

echo -e "${YELLOW}Transferring JAR to $HOST...${NC}"
ssh "$HOST" "mkdir -p /opt/cidadel/tacticl-core/application-api/build/libs"
rsync -az --checksum \
    "$REPO_ROOT/application-api/build/libs/application-api.jar" \
    "$HOST:/opt/cidadel/tacticl-core/application-api/build/libs/application-api.jar"

rsync -az --checksum \
    "$REPO_ROOT/Dockerfile" \
    "$HOST:/opt/cidadel/tacticl-core/Dockerfile"

echo -e "${YELLOW}Building Docker image on $HOST as $IMAGE_TAG...${NC}"
ssh "$HOST" "cd /opt/cidadel/tacticl-core && docker build --no-cache -t $IMAGE_TAG ."
# docker-compose.yml on the host pins :latest for both prod and qa — tag both so the
# compose `up` doesn't try to docker-hub-pull a non-existent image.
ssh "$HOST" "docker tag $IMAGE_TAG tacticl-api:latest"

if [[ "$ENV" == "prod" || "$ENV" == "both" ]]; then
    echo -e "${YELLOW}Restarting tacticl-api-prod...${NC}"
    ssh "$HOST" "cd /opt/cidadel && docker compose up -d --no-deps tacticl-api-prod"
    echo -e "${GREEN}tacticl-api-prod restarted${NC}"
fi

if [[ "$ENV" == "qa" || "$ENV" == "both" ]]; then
    echo -e "${YELLOW}Restarting tacticl-api-qa...${NC}"
    ssh "$HOST" "cd /opt/cidadel && docker compose up -d --no-deps tacticl-api-qa"
    echo -e "${GREEN}tacticl-api-qa restarted${NC}"
fi

# Each --no-cache build leaves the prior tacticl-api image dangling (the new
# build overwrites :patched / :latest tags). Prune to keep host disk in check.
# May 2026 incident on platform-apps: unbounded image accumulation took
# everything down. Tacticl doesn't have SHA tag bloat (only :patched/:latest),
# but dangling layers and build cache still need housekeeping.
echo -e "${YELLOW}Pruning dangling images + >7d build cache on ${HOST}...${NC}"
ssh "$HOST" "
    docker image prune -f 2>&1 | grep 'Total reclaimed' || true
    docker builder prune -f --filter 'until=168h' 2>&1 | grep Total || true
" || true

echo -e "${GREEN}=== tacticl-api deployed (${ENV}) ===${NC}"

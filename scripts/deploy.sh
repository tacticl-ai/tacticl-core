#!/usr/bin/env bash
# Deploy tacticl-api to Hetzner
# Usage: ./scripts/deploy.sh [prod|qa|both]
# Default: both
#
# NOTE: tacticl-core Dockerfile expects a pre-built JAR (COPY application-api/build/libs/*.jar)
# This script runs Gradle first, then builds the Docker image.
set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

ENV=${1:-both}
HETZNER="root@178.156.141.55"
SSH_OPTS="-p 443"
IMAGE=tacticl-api
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo -e "${BLUE}=== Building tacticl-api ===${NC}"
cd "$REPO_ROOT"

if [[ "$ENV" != "prod" && "$ENV" != "qa" && "$ENV" != "both" ]]; then
    echo -e "${RED}Usage: $0 [prod|qa|both]${NC}"
    exit 1
fi

if [[ "$ENV" == "prod" || "$ENV" == "both" ]]; then
    echo -e "${RED}WARNING: Deploying tacticl-api to PRODUCTION.${NC}"
    read -p "Continue? (y/N) " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 0
    fi
fi

# Verify SSH access
if ! ssh $SSH_OPTS -o ConnectTimeout=5 "$HETZNER" "echo ok" &>/dev/null; then
    echo -e "${RED}Error: Cannot reach Hetzner at $HETZNER${NC}"
    exit 1
fi

# Step 1: Build JAR with Gradle locally
# tacticl reads gpr.user/gpr.key from ~/.gradle/gradle.properties automatically
echo -e "${YELLOW}Building JAR with Gradle (this runs locally)...${NC}"
chmod +x "$REPO_ROOT/gradlew"
"$REPO_ROOT/gradlew" -p "$REPO_ROOT" clean :application-api:bootJar -x test --no-daemon
echo -e "${GREEN}Gradle build succeeded${NC}"

# Step 2: Build Docker image (JAR already built — just COPY)
echo -e "${YELLOW}Building Docker image...${NC}"
docker build -t "${IMAGE}:latest" -f "$REPO_ROOT/Dockerfile" "$REPO_ROOT"

# Step 3: Transfer JAR to Hetzner and build image there (avoids Docker dependency locally)
echo -e "${YELLOW}Transferring JAR to Hetzner...${NC}"
ssh $SSH_OPTS "$HETZNER" "mkdir -p /opt/cidadel/tacticl-core/application-api/build/libs"
rsync -az --checksum -e "ssh $SSH_OPTS" \
    "$REPO_ROOT/application-api/build/libs/application-api.jar" \
    "$HETZNER:/opt/cidadel/tacticl-core/application-api/build/libs/application-api.jar"

# Sync Dockerfile in case it changed
rsync -az --checksum -e "ssh $SSH_OPTS" \
    "$REPO_ROOT/Dockerfile" \
    "$HETZNER:/opt/cidadel/tacticl-core/Dockerfile"

echo -e "${YELLOW}Building Docker image on Hetzner...${NC}"
ssh $SSH_OPTS "$HETZNER" "cd /opt/cidadel/tacticl-core && docker build --no-cache -t tacticl-api:latest ."

# Step 4: Restart containers
if [[ "$ENV" == "prod" || "$ENV" == "both" ]]; then
    echo -e "${YELLOW}Restarting tacticl-api-prod...${NC}"
    ssh $SSH_OPTS "$HETZNER" "cd /opt/cidadel && docker compose up -d --no-deps tacticl-api-prod"
    echo -e "${GREEN}tacticl-api-prod restarted${NC}"
fi

if [[ "$ENV" == "qa" || "$ENV" == "both" ]]; then
    echo -e "${YELLOW}Restarting tacticl-api-qa...${NC}"
    ssh $SSH_OPTS "$HETZNER" "cd /opt/cidadel && docker compose up -d --no-deps tacticl-api-qa"
    echo -e "${GREEN}tacticl-api-qa restarted${NC}"
fi

echo -e "${GREEN}=== tacticl-api deployed (${ENV}) ===${NC}"

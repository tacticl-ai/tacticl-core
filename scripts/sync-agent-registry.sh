#!/bin/bash
set -euo pipefail

# Syncs the tacticl agent registry to the Hetzner arbiter host.
# Run after deploying tacticl-core when registry files change.
#
# Usage:
#   bash scripts/sync-agent-registry.sh
#   HETZNER_HOST=root@other-ip bash scripts/sync-agent-registry.sh

HETZNER_HOST="${HETZNER_HOST:-root@178.156.141.55}"
REGISTRY_SRC="$(dirname "$0")/../agent-registry"
REGISTRY_DEST="/opt/cidadel/tacticl-agent-registry"

echo "Syncing agent registry to ${HETZNER_HOST}:${REGISTRY_DEST}..."
ssh "$HETZNER_HOST" "mkdir -p ${REGISTRY_DEST}"
rsync -avz --delete \
  --exclude='.gitkeep' \
  "${REGISTRY_SRC}/" \
  "${HETZNER_HOST}:${REGISTRY_DEST}/"

echo "Done. Registry synced:"
ssh "$HETZNER_HOST" "find ${REGISTRY_DEST} -type f | sort"

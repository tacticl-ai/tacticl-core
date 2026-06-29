#!/usr/bin/env bash
# Install an ORG-LEVEL self-hosted GitHub Actions runner ("agent") on platform-apps,
# so every repo in that org can deploy via `runs-on: [self-hosted, platform-apps]`
# with no inbound SSH (the runner dials OUT to GitHub).
#
# Run ON platform-apps as root, once per org.
#
# Usage:
#   ORG=cidadel-platform REG_TOKEN=<org-registration-token> ./setup-org-runner.sh
#
# Mint REG_TOKEN (needs admin:org / manage_runners:org) from a machine with gh:
#   gh api -X POST /orgs/<ORG>/actions/runners/registration-token -q .token
# or GitHub UI: Org → Settings → Actions → Runners → New runner.
set -euo pipefail

: "${ORG:?Set ORG=<github-org>}"
: "${REG_TOKEN:?Set REG_TOKEN=<org registration token> (see header)}"

LABELS="${LABELS:-platform-apps}"
RUNNER_NAME="${RUNNER_NAME:-platform-apps-${ORG}}"
RUNNER_DIR="${RUNNER_DIR:-/opt/cidadel/actions-runner-${ORG}}"
ORG_URL="https://github.com/${ORG}"

# Auto-detect the latest runner release (override with RUNNER_VERSION=x.y.z).
# Capture curl output FIRST, then parse — `curl | grep -m1` SIGPIPEs curl, which
# under `set -o pipefail` aborts the script (curl exit 23).
if [[ -z "${RUNNER_VERSION:-}" ]]; then
  _rel="$(curl -fsSL https://api.github.com/repos/actions/runner/releases/latest)"
  _tag="$(printf '%s\n' "$_rel" | grep '"tag_name"')"; _tag="${_tag%%$'\n'*}"
  RUNNER_VERSION="$(printf '%s' "$_tag" | sed -E 's/.*"v?([^"]+)".*/\1/')"
fi

if [[ "$(id -u)" -ne 0 ]]; then echo "Run as root." >&2; exit 1; fi
command -v docker >/dev/null || { echo "docker not found on this host" >&2; exit 1; }

# Safety: must be the APP host where the compose + containers live.
if [[ "${SKIP_HOST_CHECK:-0}" != "1" && ! -f /opt/cidadel/docker-compose.yml ]]; then
  echo "ERROR: /opt/cidadel/docker-compose.yml not found — run on platform-apps. (SKIP_HOST_CHECK=1 to override.)" >&2
  exit 1
fi

mkdir -p "$RUNNER_DIR"; cd "$RUNNER_DIR"

if [[ ! -x ./run.sh ]]; then
  arch="$(uname -m)"; case "$arch" in
    x86_64) rarch=x64 ;; aarch64|arm64) rarch=arm64 ;;
    *) echo "unsupported arch: $arch" >&2; exit 1 ;;
  esac
  tarball="actions-runner-linux-${rarch}-${RUNNER_VERSION}.tar.gz"
  echo "==> [$ORG] downloading $tarball"
  curl -fsSL -o "$tarball" \
    "https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/${tarball}"
  tar xzf "$tarball"; rm -f "$tarball"
fi

export RUNNER_ALLOW_RUNASROOT=1
echo "==> [$ORG] registering runner '$RUNNER_NAME' (labels: $LABELS)"
./config.sh \
  --url "$ORG_URL" \
  --token "$REG_TOKEN" \
  --name "$RUNNER_NAME" \
  --labels "$LABELS" \
  --work _work \
  --unattended \
  --replace

echo "==> [$ORG] installing + starting systemd service"
./svc.sh install root
./svc.sh start
echo "==> [$ORG] done — verify online at ${ORG_URL%/}/settings/actions/runners"

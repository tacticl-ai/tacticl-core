#!/usr/bin/env bash
# One-time: register a self-hosted GitHub Actions runner ON platform-apps so the
# `deploy-tacticl-api` workflow can pull + restart tacticl-api locally — no inbound
# SSH required (the runner talks OUTBOUND to GitHub).
#
# Run this ON platform-apps (the app host), as root.
#
# Usage:
#   REG_TOKEN=<registration-token> ./setup-github-runner.sh
#
# Get a fresh REG_TOKEN (valid ~1h) from a machine with `gh` + repo admin:
#   gh api -X POST /repos/tacticl-ai/tacticl-core/actions/runners/registration-token -q .token
# or in the GitHub UI: Settings → Actions → Runners → New self-hosted runner.
set -euo pipefail

REPO_URL="https://github.com/tacticl-ai/tacticl-core"
# Auto-detect the latest runner release (override with RUNNER_VERSION=x.y.z).
# Capture curl output FIRST, then parse — piping curl straight into `grep -m1`
# SIGPIPEs curl, which under `set -o pipefail` aborts the script (curl exit 23).
if [[ -z "${RUNNER_VERSION:-}" ]]; then
  _rel="$(curl -fsSL https://api.github.com/repos/actions/runner/releases/latest)"
  _tag="$(printf '%s\n' "$_rel" | grep '"tag_name"')"; _tag="${_tag%%$'\n'*}"
  RUNNER_VERSION="$(printf '%s' "$_tag" | sed -E 's/.*"v?([^"]+)".*/\1/')"
fi
LABELS="tacticl-deploy"
RUNNER_NAME="platform-apps"
RUNNER_DIR="/opt/cidadel/actions-runner"

: "${REG_TOKEN:?Set REG_TOKEN=<registration-token> (see header for how to mint one)}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root (the runner manages docker + /opt/cidadel)." >&2
  exit 1
fi

command -v docker >/dev/null || { echo "docker not found on this host" >&2; exit 1; }

# Safety: make sure we're on the APP host (platform-apps) where tacticl-api + the
# compose file live — NOT platform-infra. Override with SKIP_HOST_CHECK=1.
if [[ "${SKIP_HOST_CHECK:-0}" != "1" && ! -f /opt/cidadel/docker-compose.yml ]]; then
  echo "ERROR: /opt/cidadel/docker-compose.yml not found." >&2
  echo "       This must run on platform-apps (the app host). Set SKIP_HOST_CHECK=1 to override." >&2
  exit 1
fi

mkdir -p "$RUNNER_DIR"
cd "$RUNNER_DIR"

if [[ ! -x ./run.sh ]]; then
  arch="$(uname -m)"; case "$arch" in
    x86_64) rarch=x64 ;; aarch64|arm64) rarch=arm64 ;;
    *) echo "unsupported arch: $arch" >&2; exit 1 ;;
  esac
  tarball="actions-runner-linux-${rarch}-${RUNNER_VERSION}.tar.gz"
  echo "==> downloading $tarball"
  curl -fsSL -o "$tarball" \
    "https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/${tarball}"
  tar xzf "$tarball"
  rm -f "$tarball"
fi

# --replace so re-running this re-registers cleanly. Allow root (single-tenant host).
export RUNNER_ALLOW_RUNASROOT=1
echo "==> configuring runner ($RUNNER_NAME, labels: $LABELS)"
./config.sh \
  --url "$REPO_URL" \
  --token "$REG_TOKEN" \
  --name "$RUNNER_NAME" \
  --labels "$LABELS" \
  --work _work \
  --unattended \
  --replace

echo "==> installing + starting as a systemd service"
./svc.sh install root
./svc.sh start
./svc.sh status || true

echo "Done. Verify it shows online under:"
echo "  ${REPO_URL}/settings/actions/runners"

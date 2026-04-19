#!/usr/bin/env bash
# End-to-end smoke test for the PDLC v2 / arbiter pipeline integration.
# Usage: QA_TOKEN=<paseto-token> USER_ID=<uid> SPARK_ID=<sid> bash docs/testing/pipeline-e2e-smoke.sh
#
# What this does:
#   1. Submits a pipeline run (POST /v1/sparks/{sparkId}/pipeline)
#   2. Polls for status until RUNNING, COMPLETED, FAILED, or CANCELLED
#   3. Fetches the event timeline and prints it
#
# Prerequisites:
#   - curl, jq installed
#   - A valid PASETO token for a real user in the QA environment
#   - A real sparkId that belongs to that user
#   - The arbiter must be reachable from tacticl-core-qa (pdlc.v2.arbiter.host set)

set -euo pipefail

BASE_URL="${BASE_URL:-https://api-qa.tacticl.ai}"
TOKEN="${QA_TOKEN:?Set QA_TOKEN to a valid PASETO token}"
USER_ID="${USER_ID:?Set USER_ID}"
SPARK_ID="${SPARK_ID:?Set SPARK_ID}"
PLAYBOOK="${PLAYBOOK:-BUG_FIX}"
MAX_POLLS="${MAX_POLLS:-60}"
POLL_INTERVAL="${POLL_INTERVAL:-5}"

echo "=== PDLC v2 E2E Smoke Test ==="
echo "  BASE_URL  : $BASE_URL"
echo "  SPARK_ID  : $SPARK_ID"
echo "  PLAYBOOK  : $PLAYBOOK"
echo ""

auth_header="Authorization: Bearer $TOKEN"

# ── 1. Submit pipeline ──────────────────────────────────────────────────────
echo "▶ Submitting pipeline..."
submit_resp=$(curl -sf -X POST "$BASE_URL/v1/sparks/$SPARK_ID/pipeline" \
  -H "$auth_header" \
  -H "Content-Type: application/json" \
  -d "{\"playbook\": \"$PLAYBOOK\", \"costCeilingUsd\": 5.0}")

echo "  Submit response: $submit_resp"
pipeline_run_id=$(echo "$submit_resp" | jq -r '.id // empty')
arbiter_id=$(echo "$submit_resp" | jq -r '.arbiterPipelineId // empty')

if [[ -z "$pipeline_run_id" ]]; then
  echo "✗ Submit failed — no pipeline run ID in response"
  exit 1
fi
echo "  ✓ pipelineRunId=$pipeline_run_id  arbiterPipelineId=$arbiter_id"
echo ""

# ── 2. Poll status ──────────────────────────────────────────────────────────
echo "▶ Polling status (max ${MAX_POLLS}x every ${POLL_INTERVAL}s)..."
terminal_statuses="COMPLETED FAILED CANCELLED"
polls=0
while [[ $polls -lt $MAX_POLLS ]]; do
  status_resp=$(curl -sf "$BASE_URL/v1/sparks/$SPARK_ID/pipeline" \
    -H "$auth_header")
  status=$(echo "$status_resp" | jq -r '.status // "UNKNOWN"')
  cost=$(echo "$status_resp" | jq -r '.totalCostUsd // 0')
  current_role=$(echo "$status_resp" | jq -r '.currentRole // "-"')

  echo "  [poll $((polls+1))] status=$status  currentRole=$current_role  cost=\$$cost"

  if echo "$terminal_statuses" | grep -qw "$status"; then
    echo ""
    echo "  ✓ Pipeline reached terminal state: $status"
    break
  fi

  polls=$((polls+1))
  sleep "$POLL_INTERVAL"
done

if [[ $polls -ge $MAX_POLLS ]]; then
  echo "✗ Timed out waiting for terminal state after $((MAX_POLLS * POLL_INTERVAL))s"
  exit 1
fi

# ── 3. Fetch events ─────────────────────────────────────────────────────────
echo ""
echo "▶ Fetching event timeline..."
events_resp=$(curl -sf "$BASE_URL/v1/sparks/$SPARK_ID/pipeline/events/history?page=0&size=50" \
  -H "$auth_header")

event_count=$(echo "$events_resp" | jq '.totalElements // (.content | length) // 0')
echo "  Total events: $event_count"
echo ""
echo "$events_resp" | jq -r '.content[]? | "  [\(.timestamp)] \(.eventType) role=\(.role // "-") phase=\(.phase // "-")"'

echo ""
if [[ "$status" == "COMPLETED" ]]; then
  echo "✅ Smoke test PASSED — pipeline completed successfully"
else
  echo "⚠️  Smoke test ended with status: $status"
  failure_reason=$(echo "$status_resp" | jq -r '.failureReason // empty')
  [[ -n "$failure_reason" ]] && echo "   Failure reason: $failure_reason"
fi

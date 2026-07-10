#!/usr/bin/env bash
#
# CuteGoals 2.0 — Local Admin Recovery Script
# Task 2.7: Generate a one-time recovery token for the first admin account.
#
# Usage:
#   ./deploy/recover-admin.sh
#
# This script must be run on the instance host (not via remote API).
# It generates a one-time recovery token, displays it, and then the admin
# can use it at POST /api/auth/recover within 15 minutes.
#
# Dependencies: curl, jq (optional, falls back to grep)
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_URL="${CUTEGOALS_API_URL:-http://127.0.0.1:8080}"

echo "========================================"
echo "  CuteGoals 2.0 — Local Admin Recovery"
echo "========================================"
echo ""
echo "This will generate a one-time recovery token."
echo "The token expires in 15 minutes."
echo "Use it at: POST ${BASE_URL}/api/auth/recover"
echo ""

# Call the server to initiate recovery (must be localhost)
# The server checks that the request comes from 127.0.0.1
RECOVERY_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/recover/initiate" \
  -H "Content-Type: application/json" \
  -d '{}' 2>&1) || {
  echo "ERROR: Failed to connect to server at ${BASE_URL}"
  echo "Make sure the CuteGoals server is running."
  exit 1
}

# Extract the recovery token from the response
if command -v jq &> /dev/null; then
  RECOVERY_TOKEN=$(echo "$RECOVERY_RESPONSE" | jq -r '.data.recoveryToken // empty')
else
  RECOVERY_TOKEN=$(echo "$RECOVERY_RESPONSE" | grep -o '"recoveryToken":"[^"]*"' | cut -d'"' -f4)
fi

if [ -z "$RECOVERY_TOKEN" ]; then
  echo "ERROR: Could not extract recovery token from response."
  echo "Response: $RECOVERY_RESPONSE"
  exit 1
fi

echo ""
echo "========================================"
echo "  Recovery Token Generated!"
echo "========================================"
echo ""
echo "  Token: ${RECOVERY_TOKEN}"
echo ""
echo "  Expires: 15 minutes"
echo ""
echo "  To complete recovery, run:"
echo ""
echo "    curl -X POST ${BASE_URL}/api/auth/recover \\"
echo "      -H \"Content-Type: application/json\" \\"
echo "      -d '{\"recoveryToken\": \"${RECOVERY_TOKEN}\", \"newPassword\": \"YOUR_NEW_PASSWORD\"}'"
echo ""
echo "========================================"
echo ""
echo "IMPORTANT:"
echo "- This token is a SECRET. Treat it like a password."
echo "- The token will expire in 15 minutes."
echo "- After successful recovery, all existing sessions will be revoked."
echo "- The token cannot be reused."
echo ""

exit 0

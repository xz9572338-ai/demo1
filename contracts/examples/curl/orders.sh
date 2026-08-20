#!/usr/bin/env sh
set -eu
# Requires cURL and Python 3. AppSecret remains in an environment variable and is never printed.
: "${OPEN_PLATFORM_APP_ID:?required}" "${OPEN_PLATFORM_APP_SECRET:?required}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
BASE_URL="${OPEN_PLATFORM_BASE_URL:-https://sandbox.example.invalid/sandbox/v1}"
while [ "${BASE_URL%/}" != "$BASE_URL" ]; do BASE_URL="${BASE_URL%/}"; done
QUERY='endTime=2026-08-18T02%3A00%3A00Z&page=1&pageSize=20&startTime=2026-08-18T01%3A00%3A00Z'
OPEN_PLATFORM_TIMESTAMP="$($PYTHON_BIN -c "import time;print(int(time.time()))")"
OPEN_PLATFORM_NONCE="$($PYTHON_BIN -c "import secrets;print(secrets.token_hex(16))")"
export OPEN_PLATFORM_TIMESTAMP OPEN_PLATFORM_NONCE QUERY
OPEN_PLATFORM_SIGNATURE="$($PYTHON_BIN -c "import hashlib,hmac,os; body=hashlib.sha256(b'').hexdigest(); canonical='\\n'.join(('GET','/orders',os.environ['QUERY'],body,os.environ['OPEN_PLATFORM_APP_ID'],os.environ['OPEN_PLATFORM_TIMESTAMP'],os.environ['OPEN_PLATFORM_NONCE'])); print(hmac.new(os.environ['OPEN_PLATFORM_APP_SECRET'].encode(),canonical.encode(),hashlib.sha256).hexdigest())")"
if [ "${OPEN_PLATFORM_DRY_RUN:-0}" = "1" ]; then printf '%s\n' 'cURL request prepared with X-App-ID, X-Timestamp, X-Nonce and X-Signature'; exit 0; fi
curl --connect-timeout 10 --max-time 30 --fail-with-body "$BASE_URL/orders?$QUERY" \
  -H "X-App-ID: $OPEN_PLATFORM_APP_ID" \
  -H "X-Timestamp: $OPEN_PLATFORM_TIMESTAMP" \
  -H "X-Nonce: $OPEN_PLATFORM_NONCE" \
  -H "X-Signature: $OPEN_PLATFORM_SIGNATURE"

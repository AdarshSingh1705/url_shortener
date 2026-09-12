#!/bin/bash
# ShortLink API — end-to-end smoke test.
# Usage: ./test.sh [base_url]   (defaults to http://localhost:8080)
# Requires: curl. Uses grep/sed for JSON parsing so it works with no extra dependencies.

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0

green() { printf "\033[32m%s\033[0m\n" "$1"; }
red()   { printf "\033[31m%s\033[0m\n" "$1"; }

check() {
  local description="$1"
  local actual="$2"
  local expected="$3"
  if [ "$actual" == "$expected" ]; then
    green "  PASS: $description (got $actual)"
    PASS=$((PASS + 1))
  else
    red "  FAIL: $description (expected $expected, got $actual)"
    FAIL=$((FAIL + 1))
  fi
}

extract_json_field() {
  # crude but dependency-free JSON field extractor: extract_json_field '"shortCode":"abc"' shortCode
  echo "$1" | grep -o "\"$2\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | head -1 | sed -E "s/\"$2\"[[:space:]]*:[[:space:]]*\"//" | sed 's/"$//'
}

echo "=== ShortLink API smoke test against $BASE_URL ==="
echo

# 1. Create a short link
echo "[1] Create a short link"
CREATE_BODY='{"longUrl": "https://www.autodesk.com/products/fusion-360", "expiresInDays": 30}'
CREATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/urls" \
  -H "Content-Type: application/json" -d "$CREATE_BODY")
CREATE_STATUS=$(echo "$CREATE_RESPONSE" | tail -1)
CREATE_JSON=$(echo "$CREATE_RESPONSE" | sed '$d')
SHORT_CODE=$(extract_json_field "$CREATE_JSON" shortCode)
check "POST /api/urls returns 201" "$CREATE_STATUS" "201"
if [ -n "$SHORT_CODE" ]; then
  green "  Got shortCode: $SHORT_CODE"
  PASS=$((PASS + 1))
else
  red "  FAIL: no shortCode in response: $CREATE_JSON"
  FAIL=$((FAIL + 1))
fi
echo

# 2. Reject an invalid URL
echo "[2] Reject an invalid longUrl"
INVALID_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/urls" \
  -H "Content-Type: application/json" -d '{"longUrl": "not-a-real-url"}')
check "POST with bad longUrl returns 400" "$INVALID_STATUS" "400"
echo

# 3. Follow the redirect
echo "[3] Follow the short link"
if [ -n "$SHORT_CODE" ]; then
  REDIRECT_HEADERS=$(curl -s -i -o - -w "\n%{http_code}" "$BASE_URL/$SHORT_CODE" | head -20)
  REDIRECT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/$SHORT_CODE")
  LOCATION=$(curl -s -i "$BASE_URL/$SHORT_CODE" | grep -i "^location:" | sed 's/[Ll]ocation: //' | tr -d '\r')
  check "GET /$SHORT_CODE returns 302" "$REDIRECT_STATUS" "302"
  if [[ "$LOCATION" == *"autodesk.com"* ]]; then
    green "  PASS: redirects to the original URL ($LOCATION)"
    PASS=$((PASS + 1))
  else
    red "  FAIL: unexpected redirect target: $LOCATION"
    FAIL=$((FAIL + 1))
  fi
else
  red "  SKIPPED: no shortCode from step 1"
fi
echo

# 4. Analytics
echo "[4] Check analytics"
if [ -n "$SHORT_CODE" ]; then
  ANALYTICS_RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/api/urls/$SHORT_CODE/analytics")
  ANALYTICS_STATUS=$(echo "$ANALYTICS_RESPONSE" | tail -1)
  ANALYTICS_JSON=$(echo "$ANALYTICS_RESPONSE" | sed '$d')
  check "GET analytics returns 200" "$ANALYTICS_STATUS" "200"
  echo "  Response: $ANALYTICS_JSON"
else
  red "  SKIPPED: no shortCode from step 1"
fi
echo

# 5. Unknown short code
echo "[5] Look up a nonexistent short code"
NOT_FOUND_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/zzzzzzz")
check "GET /zzzzzzz returns 404" "$NOT_FOUND_STATUS" "404"
echo

# 6. Rate limiting — fire 12 requests fast and expect at least one 429 near the end.
echo "[6] Rate limit test (default: 10 requests/minute/client)"
RATE_LIMIT_HIT="no"
for i in $(seq 1 12); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/urls" \
    -H "Content-Type: application/json" -d "{\"longUrl\": \"https://example.com/rl-$i\"}")
  echo "  request #$i -> $STATUS"
  if [ "$STATUS" == "429" ]; then
    RATE_LIMIT_HIT="yes"
  fi
done
if [ "$RATE_LIMIT_HIT" == "yes" ]; then
  green "  PASS: rate limiter kicked in (got a 429)"
  PASS=$((PASS + 1))
else
  red "  FAIL: never got a 429 — check requests-per-window / Redis connectivity"
  FAIL=$((FAIL + 1))
fi
echo

echo "=== Summary: $PASS passed, $FAIL failed ==="
if [ "$FAIL" -gt 0 ]; then
  exit 1
fi

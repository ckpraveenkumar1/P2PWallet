#!/usr/bin/env bash
#
# burst_test.sh — One-command burst test for all P2P Wallet invariants
#
# Usage:
#   ./burst_test.sh [BASE_URL]
#
# Defaults to http://localhost:8080 if no URL is provided.
# Requires: curl, jq, bash 4+
#

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
TOKEN_ALICE="tok_alice"
TOKEN_BOB="tok_bob"
TOKEN_CHARLIE="tok_charlie"
TOKEN_DAVE="tok_dave"
PASS=0
FAIL=0

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_pass() { PASS=$((PASS+1)); echo -e "${GREEN}✓ PASS${NC}: $1"; }
log_fail() { FAIL=$((FAIL+1)); echo -e "${RED}✗ FAIL${NC}: $1"; }
log_info() { echo -e "${YELLOW}→${NC} $1"; }

# Warm up the service (free tier cold start)
log_info "Warming up service at ${BASE_URL}..."
for i in $(seq 1 3); do
    curl -sf "${BASE_URL}/actuator/health" > /dev/null 2>&1 && break
    sleep 5
done

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║          P2P WALLET BURST TEST SUITE                 ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

# ============================================================
# TEST 1: Concurrent Get-or-Create
# ============================================================
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "TEST 1: Concurrent get-or-create (20 parallel requests)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

RANDOM_USER="burst_user_$(date +%s)_${RANDOM}"
TMPDIR_TEST=$(mktemp -d)

for i in $(seq 1 20); do
    curl -sf -X POST "${BASE_URL}/wallets" \
        -H "Authorization: Bearer ${TOKEN_ALICE}" \
        -H "Content-Type: application/json" \
        -d "{\"user_id\": \"${RANDOM_USER}\"}" \
        -o "${TMPDIR_TEST}/wallet_${i}.json" &
done
wait

WALLET_IDS=$(cat "${TMPDIR_TEST}"/wallet_*.json 2>/dev/null | jq -r '.id' | sort -u)
UNIQUE_COUNT=$(echo "$WALLET_IDS" | wc -l | tr -d ' ')

if [ "$UNIQUE_COUNT" -eq 1 ]; then
    log_pass "20 concurrent POST /wallets → exactly 1 unique wallet ID: $(echo $WALLET_IDS | head -1)"
else
    log_fail "Expected 1 unique wallet, got ${UNIQUE_COUNT}"
fi

rm -rf "${TMPDIR_TEST}"

echo ""

# ============================================================
# TEST 2: Idempotent Retry Storm
# ============================================================
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "TEST 2: Idempotent retry storm (20 parallel, same key)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Create two wallets for transfer
SENDER=$(curl -sf -X POST "${BASE_URL}/wallets" \
    -H "Authorization: Bearer ${TOKEN_ALICE}" \
    -H "Content-Type: application/json" \
    -d "{\"user_id\": \"sender_$(date +%s)_${RANDOM}\"}" | jq -r '.id')

RECEIVER=$(curl -sf -X POST "${BASE_URL}/wallets" \
    -H "Authorization: Bearer ${TOKEN_BOB}" \
    -H "Content-Type: application/json" \
    -d "{\"user_id\": \"receiver_$(date +%s)_${RANDOM}\"}" | jq -r '.id')

# Deposit funds into sender
curl -sf -X POST "${BASE_URL}/wallets/${SENDER}/deposit" \
    -H "Authorization: Bearer ${TOKEN_ALICE}" \
    -H "Content-Type: application/json" \
    -d '{"amount_paise": 100000}' > /dev/null

log_info "Sender wallet: ${SENDER} (balance: 100000 paise)"
log_info "Receiver wallet: ${RECEIVER} (balance: 0 paise)"

IDEMPOTENCY_KEY="idem_$(date +%s)_${RANDOM}"
TMPDIR_TEST=$(mktemp -d)

for i in $(seq 1 20); do
    curl -s -X POST "${BASE_URL}/transfers" \
        -H "Authorization: Bearer ${TOKEN_ALICE}" \
        -H "Content-Type: application/json" \
        -d "{\"from\": \"${SENDER}\", \"to\": \"${RECEIVER}\", \"amount_paise\": 1000, \"transaction_id\": \"${IDEMPOTENCY_KEY}\"}" \
        -o "${TMPDIR_TEST}/transfer_${i}.json" &
done
wait

# Check that all successful responses have the same transfer ID
TRANSFER_IDS=$(cat "${TMPDIR_TEST}"/transfer_*.json 2>/dev/null | jq -r '.id // empty' | sort -u)
UNIQUE_TRANSFER_COUNT=$(echo "$TRANSFER_IDS" | grep -c . || true)

# Check sender balance — should be debited exactly once (99000)
SENDER_BALANCE=$(curl -sf "${BASE_URL}/wallets/${SENDER}" \
    -H "Authorization: Bearer ${TOKEN_ALICE}" | jq -r '.balance')

if [ "$UNIQUE_TRANSFER_COUNT" -eq 1 ]; then
    log_pass "20 concurrent transfers with same key → exactly 1 transfer ID"
else
    log_fail "Expected 1 unique transfer ID, got ${UNIQUE_TRANSFER_COUNT}"
fi

if [ "$SENDER_BALANCE" -eq 99000 ]; then
    log_pass "Sender debited exactly once: balance = ${SENDER_BALANCE} paise"
else
    log_fail "Expected sender balance 99000, got ${SENDER_BALANCE}"
fi

# Test same key with different body → 409
CONFLICT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/transfers" \
    -H "Authorization: Bearer ${TOKEN_ALICE}" \
    -H "Content-Type: application/json" \
    -d "{\"from\": \"${SENDER}\", \"to\": \"${RECEIVER}\", \"amount_paise\": 9999, \"transaction_id\": \"${IDEMPOTENCY_KEY}\"}")

if [ "$CONFLICT_STATUS" -eq 409 ]; then
    log_pass "Same key + different body → HTTP 409 Conflict"
else
    log_fail "Expected 409 for same key + different body, got ${CONFLICT_STATUS}"
fi

rm -rf "${TMPDIR_TEST}"

echo ""

# ============================================================
# TEST 3: Conservation Under Contention
# ============================================================
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "TEST 3: Conservation under contention (50 concurrent)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Create 4 wallets
W=()
USERS=("alpha_$(date +%s)" "beta_$(date +%s)" "gamma_$(date +%s)" "delta_$(date +%s)")
TOKENS=("${TOKEN_ALICE}" "${TOKEN_BOB}" "${TOKEN_CHARLIE}" "${TOKEN_DAVE}")
INITIAL_EACH=100000
TOTAL_INITIAL=$((INITIAL_EACH * 4))

for i in 0 1 2 3; do
    WID=$(curl -sf -X POST "${BASE_URL}/wallets" \
        -H "Authorization: Bearer ${TOKENS[$i]}" \
        -H "Content-Type: application/json" \
        -d "{\"user_id\": \"${USERS[$i]}\"}" | jq -r '.id')
    
    curl -sf -X POST "${BASE_URL}/wallets/${WID}/deposit" \
        -H "Authorization: Bearer ${TOKENS[$i]}" \
        -H "Content-Type: application/json" \
        -d "{\"amount_paise\": ${INITIAL_EACH}}" > /dev/null
    
    W+=("$WID")
done

log_info "Created 4 wallets, each with ${INITIAL_EACH} paise (total: ${TOTAL_INITIAL})"

# Fire 50 random transfers including bidirectional (A→B and B→A)
TMPDIR_TEST=$(mktemp -d)
for i in $(seq 1 50); do
    FROM_IDX=$((RANDOM % 4))
    TO_IDX=$(( (FROM_IDX + 1 + RANDOM % 3) % 4 ))
    AMOUNT=$((RANDOM % 5000 + 100))
    KEY="conservation_${i}_$(date +%s)_${RANDOM}"
    
    curl -s -X POST "${BASE_URL}/transfers" \
        -H "Authorization: Bearer ${TOKEN_ALICE}" \
        -H "Content-Type: application/json" \
        -d "{\"from\": \"${W[$FROM_IDX]}\", \"to\": \"${W[$TO_IDX]}\", \"amount_paise\": ${AMOUNT}, \"transaction_id\": \"${KEY}\"}" \
        -o "${TMPDIR_TEST}/t_${i}.json" &
done
wait

# Check conservation: sum of all balances should equal TOTAL_INITIAL
TOTAL_BALANCE=0
ALL_NON_NEGATIVE=true
for i in 0 1 2 3; do
    BAL=$(curl -sf "${BASE_URL}/wallets/${W[$i]}" \
        -H "Authorization: Bearer ${TOKEN_ALICE}" | jq -r '.balance')
    log_info "Wallet ${i} balance: ${BAL} paise"
    TOTAL_BALANCE=$((TOTAL_BALANCE + BAL))
    if [ "$BAL" -lt 0 ]; then
        ALL_NON_NEGATIVE=false
    fi
done

# Count completed vs declined transfers
COMPLETED=$(cat "${TMPDIR_TEST}"/t_*.json 2>/dev/null | jq -r '.status // empty' | grep -c "COMPLETED" || true)
DECLINED=$(cat "${TMPDIR_TEST}"/t_*.json 2>/dev/null | jq -r 'select(.error == "INSUFFICIENT_FUNDS") | .error' | wc -l | tr -d ' ')
ERRORS=$(cat "${TMPDIR_TEST}"/t_*.json 2>/dev/null | jq -r 'select(.status != null and .status != "COMPLETED") | .status' | wc -l | tr -d ' ')

log_info "Transfers completed: ${COMPLETED}, declined/errors: $((50 - COMPLETED))"

if [ "$TOTAL_BALANCE" -eq "$TOTAL_INITIAL" ]; then
    log_pass "Conservation: total balance ${TOTAL_BALANCE} == initial ${TOTAL_INITIAL}"
else
    log_fail "Conservation violated: total balance ${TOTAL_BALANCE} != initial ${TOTAL_INITIAL}"
fi

if [ "$ALL_NON_NEGATIVE" = true ]; then
    log_pass "No overdraft: all balances are non-negative"
else
    log_fail "Overdraft detected: at least one balance is negative"
fi

rm -rf "${TMPDIR_TEST}"

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║                   RESULTS                           ║"
echo "╠══════════════════════════════════════════════════════╣"
echo -e "║  ${GREEN}Passed: ${PASS}${NC}                                        ║"
echo -e "║  ${RED}Failed: ${FAIL}${NC}                                        ║"
echo "╚══════════════════════════════════════════════════════╝"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi

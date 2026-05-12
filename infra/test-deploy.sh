#!/bin/bash
# Test script for deploy.sh (Phase 1 Foundation)
# Location: /infra/test-deploy.sh (per D-20)
# Coverage: Syntax validation, state persistence, color codes (per D-21)

set -euo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_SCRIPT="${TEST_DIR}/deploy.sh"
TEST_STATE_FILE="${TEST_DIR}/.deploy-state"
TEST_CREDS_FILE="${TEST_DIR}/.deploy-state.creds"

# Test counter
TESTS_PASSED=0
TESTS_FAILED=0

# Helper functions
test_pass() {
  echo -e "✓ PASS: $1"
  TESTS_PASSED=$((TESTS_PASSED + 1))
}

test_fail() {
  echo -e "✗ FAIL: $1"
  TESTS_FAILED=$((TESTS_FAILED + 1))
}

# Test 1: Bash syntax validation (per D-21)
echo "Running Phase 1 tests..."
echo ""
echo "Test 1: Bash Syntax Validation"
if bash -n "$DEPLOY_SCRIPT" 2>/dev/null; then
  test_pass "deploy.sh has valid bash syntax"
else
  test_fail "deploy.sh has syntax errors"
fi

# Test 2: Script is executable
echo ""
echo "Test 2: Script Permissions"
if [[ -x "$DEPLOY_SCRIPT" ]]; then
  test_pass "deploy.sh is executable"
else
  test_fail "deploy.sh is not executable (chmod +x needed)"
fi

# Test 3: Help flag works
echo ""
echo "Test 3: Help Flag"
if "$DEPLOY_SCRIPT" --help 2>&1 | grep -q "Usage:"; then
  test_pass "--help flag displays usage text"
else
  test_fail "--help flag does not display usage"
fi

# Test 4: Color codes present in helper functions
echo ""
echo "Test 4: Color Code Definitions"
color_tests=(
  "RED='\\\\033\\[0;31m'"
  "GREEN='\\\\033\\[0;32m'"
  "YELLOW='\\\\033\\[0;33m'"
  "BLUE='\\\\033\\[0;34m'"
)

color_found=true
for color_code in "${color_tests[@]}"; do
  if ! grep -q "$color_code" "$DEPLOY_SCRIPT"; then
    test_fail "Missing or incorrect color code for pattern: $color_code"
    color_found=false
  fi
done

if [[ "$color_found" == true ]]; then
  test_pass "All color codes defined (RED, GREEN, YELLOW, BLUE)"
fi

# Test 5: Color helper functions exist
echo ""
echo "Test 5: Color Helper Functions"
helpers=("show_success" "show_error" "show_warning" "show_info")
helpers_found=true
for helper in "${helpers[@]}"; do
  if grep -q "^${helper}()" "$DEPLOY_SCRIPT"; then
    test_pass "Helper function exists: $helper"
  else
    test_fail "Missing helper function: $helper"
    helpers_found=false
  fi
done

# Test 6: State management functions exist
echo ""
echo "Test 6: State Management Functions"
state_funcs=("load_state" "save_state" "reset_state" "mark_step_complete")
state_found=true
for func in "${state_funcs[@]}"; do
  if grep -q "^${func}()" "$DEPLOY_SCRIPT"; then
    test_pass "State function exists: $func"
  else
    test_fail "Missing state function: $func"
    state_found=false
  fi
done

# Test 7: Checkpoint recovery function exists
echo ""
echo "Test 7: Checkpoint Recovery"
if grep -q "^prompt_recovery_action()" "$DEPLOY_SCRIPT"; then
  test_pass "prompt_recovery_action() function exists"
else
  test_fail "Missing prompt_recovery_action() function"
fi

# Test 8: Pre-flight validation function exists
echo ""
echo "Test 8: Pre-Flight Validation"
if grep -q "^check_prerequisites()" "$DEPLOY_SCRIPT"; then
  test_pass "check_prerequisites() function exists"
else
  test_fail "Missing check_prerequisites() function"
fi

# Test 9: Trap handlers defined
echo ""
echo "Test 9: Trap Handlers"
if grep -q "trap.*cleanup_on_exit" "$DEPLOY_SCRIPT"; then
  test_pass "Trap handlers registered for EXIT/INT/TERM"
else
  test_fail "Trap handlers not found"
fi

# Test 10: Strict mode set
echo ""
echo "Test 10: Bash Strict Mode"
if grep -q "set -euo pipefail" "$DEPLOY_SCRIPT"; then
  test_pass "Bash strict mode (set -euo pipefail) enabled"
else
  test_fail "Strict mode not found"
fi

# Test 11: Root check present
echo ""
echo "Test 11: Root Access Check"
if grep -q 'UID -ne 0' "$DEPLOY_SCRIPT"; then
  test_pass "Root check implemented (sudo requirement)"
else
  test_fail "Root check not found"
fi

# Test 12: State file paths defined
echo ""
echo "Test 12: State File Paths"
if grep -q "STATE_FILE=" "$DEPLOY_SCRIPT" && grep -q "CREDS_FILE=" "$DEPLOY_SCRIPT"; then
  test_pass "State file paths defined (.deploy-state and .deploy-state.creds)"
else
  test_fail "State file paths not defined"
fi

# Test 13: Main function exists
echo ""
echo "Test 13: Main Function"
if grep -q "^main()" "$DEPLOY_SCRIPT"; then
  test_pass "main() function exists"
else
  test_fail "main() function not found"
fi

# Summary
echo ""
echo "═════════════════════════════════════════"
echo "Test Summary: Phase 1 Foundation"
echo "═════════════════════════════════════════"
echo "Passed: $TESTS_PASSED"
echo "Failed: $TESTS_FAILED"
echo ""

if [[ $TESTS_FAILED -eq 0 ]]; then
  echo "✓ All tests passed!"
  exit 0
else
  echo "✗ $TESTS_FAILED test(s) failed. Review deploy.sh for issues."
  exit 1
fi

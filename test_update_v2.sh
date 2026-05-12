# Mock FD 3 for sourcing
exec 3>&1
source infra/deploy.sh
# Mock show_info to avoid printing to FD 3 during test
show_info() { :; }
update_env_var test.env TEST_KEY "value/with/slashes"
if grep -q "TEST_KEY='value/with/slashes'" test.env; then
    echo "update_env_var PASS"
else
    echo "update_env_var FAIL"
    cat test.env
fi

# Test HCL escaping
# jq -Rs . escapes " as \" and \ as \\
VAL=$(escape_hcl 'pass"word\with\backslash')
EXPECTED='"pass\"word\\with\\backslash"'
if [[ "$VAL" == "$EXPECTED" ]]; then
    echo "escape_hcl PASS"
else
    echo "escape_hcl FAIL"
    echo "Got:      $VAL"
    echo "Expected: $EXPECTED"
fi

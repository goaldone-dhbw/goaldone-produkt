source infra/deploy.sh
# Mock NC since it might be needed by show_info
NC=''
BLUE=''
get_timestamp() { date; }
show_info() { echo "INFO: $1"; }
update_env_var test.env TEST_KEY "value/with/slashes"
grep "TEST_KEY='value/with/slashes'" test.env || exit 1
VAL=$(escape_hcl 'pass"word\with\backslash')
[[ "$VAL" == '"pass\"word\\with\\backslash"' ]] || exit 1
echo "SEC-01 PASS"

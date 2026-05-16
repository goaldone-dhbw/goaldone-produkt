#!/bin/sh
# GoalDone Bootstrap Installer
# Downloads and runs the GoalDone installation scripts.
# Usage: curl -sSL https://install.goaldone.de/install.sh | sh

set -e

# ==============================================================================
# Variables
# ==============================================================================
BASE_URL="https://raw.githubusercontent.com/goaldone-dhbw/goaldone-produkt/refs/heads/master/infra"
DEPS_URL="${BASE_URL}/install-deps.sh"
DEPLOY_URL="${BASE_URL}/deploy.sh"
DEPS_SCRIPT="$HOME/install-deps.sh"
DEPLOY_SCRIPT="$HOME/deploy.sh"

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# ==============================================================================
# Helper Functions
# ==============================================================================
get_timestamp() {
    date +"[%H:%M:%S]"
}

show_header() {
    title="$1"
    width=80
    title_len=$(printf '%s' "$title" | wc -c | tr -d ' ')
    padding=$(( (width - title_len - 4) / 2 ))

    printf '\n'
    if [ "$padding" -gt 0 ]; then
        printf '%*s' "$padding" '' | tr ' ' '='
    fi
    printf '  %s  ' "$title"
    if [ "$padding" -gt 0 ]; then
        printf '%*s' "$padding" '' | tr ' ' '='
    fi
    printf '\n'
}

show_success() {
    printf '%b\n' "$(get_timestamp) ${GREEN}✓ $1${NC}"
}

show_error() {
    printf '%b\n' "$(get_timestamp) ${RED}✗ Error: $1${NC}"
}

show_warning() {
    printf '%b\n' "$(get_timestamp) ${YELLOW}⚠ Warning: $1${NC}"
}

show_info() {
    printf '%b\n' "$(get_timestamp) ${BLUE}⏳ $1${NC}"
}

cleanup() {
    if [ $? -ne 0 ]; then
        printf '\n'
        show_error "Installation aborted."
    fi
}
trap cleanup EXIT

# ==============================================================================
# Preflight Checks
# ==============================================================================
show_header "GoalDone Bootstrap Installer"

if ! command -v curl >/dev/null 2>&1; then
    show_error "curl is required but not installed."
    exit 1
fi

if [ "$(id -u)" -eq 0 ]; then
    show_error "Do not run this script as root."
    show_info "Run as your normal user. The script will prompt for sudo when needed."
    exit 1
fi

show_info "Starting GoalDone installation... (Ctrl+C to cancel)"

# ==============================================================================
# Download Scripts
# ==============================================================================
show_header "Downloading Scripts"

show_info "Downloading install-deps.sh..."
if ! curl -fsSL -o "$DEPS_SCRIPT" "$DEPS_URL"; then
    show_error "Failed to download install-deps.sh"
    exit 1
fi
chmod +x "$DEPS_SCRIPT"
show_success "Downloaded install-deps.sh to $DEPS_SCRIPT"

show_info "Downloading deploy.sh..."
if ! curl -fsSL -o "$DEPLOY_SCRIPT" "$DEPLOY_URL"; then
    show_error "Failed to download deploy.sh"
    exit 1
fi
chmod +x "$DEPLOY_SCRIPT"
show_success "Downloaded deploy.sh to $DEPLOY_SCRIPT"

# ==============================================================================
# Run Dependency Installation
# ==============================================================================
show_header "Running Dependency Installation"
show_info "This will prompt for your sudo password."
printf '\n'

sudo bash "$DEPS_SCRIPT"

# ==============================================================================
# Next Steps
# ==============================================================================
printf '\n'
show_header "Next Steps"
show_success "Dependencies installed successfully!"
printf '\n'
show_info "Docker group membership requires a session refresh."
show_info "You have two options:"
printf '\n'
printf '  Option A (recommended):\n'
printf '    1. Log out of this session (type '"'"'exit'"'"' or close terminal)\n'
printf '    2. Log back in\n'
printf '    3. Run:  bash ~/deploy.sh\n'
printf '\n'
printf '  Option B (quick, but read carefully):\n'
printf '    1. Run:  bash ~/deploy.sh && exec su - %s\n' "$USER"
printf '\n'
show_warning "'exec su -' replaces your current shell session."
show_warning "deploy.sh is placed BEFORE the exec command so it runs first."
printf '\n'
show_info "To verify Docker access after re-login: docker ps"
printf '\n'

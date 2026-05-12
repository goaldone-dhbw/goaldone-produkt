#!/bin/bash
# GoalDone Automated Installation Script
# Foundation, State Management, and Checkpoint Recovery

set -euo pipefail
IFS=$'\n\t'

# ==============================================================================
# Global Variables
# ==============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE_FILE="${SCRIPT_DIR}/.deploy-state"
CREDS_FILE="${SCRIPT_DIR}/.deploy-state.creds"
LOG_FILE="${SCRIPT_DIR}/deploy.log"
TOTAL_STEPS=12
START_STEP=1
LAST_COMPLETED_STEP=0
SCRIPT_START_TIME=$(date +%s)

# Color codes (per D-10)
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Trap variables
CLEANUP_CALLED=false
SKIP_HEALTH_CHECK=false

# Initialize terminal output FD (per D-11)
# This must be done before any helper functions using >&3 are called
exec 3>&1

# ==============================================================================
# Helper Functions - Output (per D-11, UX-02)
# ==============================================================================
get_timestamp() {
    date +"[%H:%M:%S]"
}

get_duration() {
    local start=$1
    local end
    end=$(date +%s)
    local diff=$((end - start))
    printf "%dm %ds" $((diff / 60)) $((diff % 60))
}

show_header() {
    local title=$1
    local width
    width=$(tput cols 2>/dev/null || echo 80)
    local title_len=${#title}
    local padding=$(( (width - title_len - 4) / 2 ))
    
    echo "" >&3
    if [[ $padding -gt 0 ]]; then
        printf "%${padding}s" "" | tr ' ' '=' >&3
    fi
    printf "  %s  " "$title" >&3
    if [[ $padding -gt 0 ]]; then
        printf "%${padding}s" "" | tr ' ' '=' >&3
    fi
    echo "" >&3
}

show_success() {
    echo -e "$(get_timestamp) ${GREEN}✓ $1${NC}" >&3
}

show_error() {
    echo -e "$(get_timestamp) ${RED}✗ Error: $1${NC}" >&3
}

show_warning() {
    echo -e "$(get_timestamp) ${YELLOW}⚠ Warning: $1${NC}" >&3
}

show_info() {
    echo -e "$(get_timestamp) ${BLUE}⏳ $1${NC}" >&3
}

show_spinner() {
    local pid=$1
    local delay=0.1
    local spinstr='|/-\'
    
    # Hide cursor
    tput civis >&3 2>/dev/null || true
    
    while kill -0 "$pid" 2>/dev/null; do
        local temp=${spinstr#?}
        printf " [%c]  " "$spinstr" >&3
        spinstr=$temp${spinstr%"$temp"}
        sleep $delay
        printf "\b\b\b\b\b\b" >&3
    done
    
    # Clear spinner and show cursor
    printf "    \b\b\b\b" >&3
    tput cnorm >&3 2>/dev/null || true
}

show_troubleshooting_hint() {
    local service=$1
    echo "" >&3
    echo -e "${YELLOW}💡 Troubleshooting Hint for $service:${NC}" >&3
    case "$service" in
        apt)
            echo "  - Check your internet connection." >&3
            echo "  - Ensure no other package manager is running (check for /var/lib/dpkg/lock-frontend)." >&3
            echo "  - Try running 'sudo apt-get update' manually." >&3
            ;;
        git)
            echo "  - Check your internet connection." >&3
            echo "  - Verify GitHub access (https://github.com/goaldone-dhbw/goaldone-produkt)." >&3
            echo "  - Ensure you have enough disk space." >&3
            ;;
        docker)
            echo "  - Check if Docker service is running: 'sudo systemctl status docker'." >&3
            echo "  - Inspect Zitadel logs: 'docker compose logs zitadel'." >&3
            echo "  - Verify ZITADEL_MASTERKEY is exactly 32 characters." >&3
            echo "  - Ensure ports 8080 and 5432 are not already in use." >&3
            ;;
        terraform)
            echo "  - Verify your ZITADEL_DOMAIN is reachable." >&3
            echo "  - Check if the TERRAFORM_TOKEN is still valid." >&3
            echo "  - Look for state locks in the terraform directory." >&3
            ;;
        *)
            echo "  - Check the log file for detailed error messages: $LOG_FILE" >&3
            ;;
    esac
    echo "" >&3
}

error_exit() {
    local msg=$1
    local service=${2:-}
    
    show_error "$msg"
    if [[ -n "$service" ]]; then
        show_troubleshooting_hint "$service"
    fi
    exit 1
}

# ==============================================================================
# Helper Functions - Input & Caching (per D-01, D-02)
# ==============================================================================
prompt_input() {
    local prompt_text=$1
    local validation_regex=${2:-}
    local is_hidden=${3:-false}
    local attempts=0
    local max_attempts=3
    local input=""

    while [[ $attempts -lt $max_attempts ]]; do
        attempts=$((attempts + 1))
        
        if [[ "$is_hidden" == "true" ]]; then
            # Use read -s for hidden input
            echo -n -e "$(get_timestamp) ${BLUE}⏳ $prompt_text ${NC}" >&3
            read -rs input
            echo "" >&3 # Add newline after hidden input
        else
            echo -n -e "$(get_timestamp) ${BLUE}⏳ $prompt_text ${NC}" >&3
            read -r input
        fi

        # Adaptive correction: trim whitespace
        input="${input#"${input%%[![:space:]]*}"}"
        input="${input%"${input##*[![:space:]]}"}"

        # Adaptive correction: lowercase for domains/URLs
        if [[ "$prompt_text" == *"domain"* ]] || [[ "$prompt_text" == *"URL"* ]] || [[ "$validation_regex" == *"a-z0-9.-"* ]]; then
            local original_input=$input
            input=$(echo "$input" | tr '[:upper:]' '[:lower:]')
            if [[ "$input" != "$original_input" ]]; then
                show_info "Corrected input to lowercase: $input"
            fi
        fi

        # Adaptive correction: strip spaces from passwords
        if [[ "$is_hidden" == "true" ]]; then
            if [[ "$input" == *" "* ]]; then
                input="${input// /}"
                show_info "Stripped spaces from password."
            fi
        fi

        # Validation
        if [[ -n "$validation_regex" ]]; then
            if [[ ! "$input" =~ $validation_regex ]]; then
                show_error "Invalid input format. (Attempt $attempts/$max_attempts)"
                continue
            fi
        fi

        echo "$input"
        return 0
    done

    show_error "Max attempts reached for input: $prompt_text"
    return 1
}

cache_credential() {
    local key=$1
    local value=$2
    
    # Update memory variable
    eval "$key=\"$value\""
    
    # Persistent save
    save_credentials
}

load_credential() {
    local key=$1
    echo "${!key}"
}

# ==============================================================================
# Helper Functions - Configuration Updates (per Plan 04-03, SEC-01)
# ==============================================================================
update_env_var() {
    local file=$1
    local key=$2
    local value=$3
    local temp_file="${file}.tmp"

    # Robust update using grep/tmp-file pattern (no sed delimiter issues)
    if [[ -f "$file" ]]; then
        grep -v "^${key}=" "$file" > "$temp_file" || true
    else
        touch "$temp_file"
    fi
    
    # Quote the value to handle special characters and prevent shell injection
    # We use single quotes and escape any single quotes within the value
    local escaped_value
    escaped_value=$(echo -n "$value" | sed "s/'/'\\\\''/g")
    echo "${key}='${escaped_value}'" >> "$temp_file"
    mv "$temp_file" "$file"
}

escape_hcl() {
    local value=$1
    # Use jq to safely escape string for HCL
    echo -n "$value" | jq -Rs .
}

# ==============================================================================
# Helper Functions - State Management (per D-04, D-05, D-06)
# ==============================================================================
load_state() {
    # Initialize default state if file doesn't exist
    if [[ -f "$STATE_FILE" ]]; then
        # shellcheck disable=SC1090
        source "$STATE_FILE"
    else
        # Initialize empty state
        LAST_COMPLETED_STEP=0
        ZITADEL_DOMAIN=""
        GOALDONE_URL=""
        TERRAFORM_TOKEN=""
        BACKEND_PAT=""
        APP_CLIENT_ID=""
        DEPLOY_WORK_DIR=""
    fi

    # Load cached credentials if they exist (per D-06)
    if [[ -f "$CREDS_FILE" ]]; then
        # shellcheck disable=SC1090
        source "$CREDS_FILE" 2>/dev/null || true
    fi
}

save_state() {
    local temp_file="${STATE_FILE}.tmp"

    # Write to temporary file first (atomic write per D-04)
    cat > "$temp_file" <<EOF
# Generated by deploy.sh at $(date)
LAST_COMPLETED_STEP=$LAST_COMPLETED_STEP
STEP_1_VERIFY_PREREQUISITES=${STEP_1_VERIFY_PREREQUISITES:-pending}
STEP_2_INSTALL_PACKAGES=${STEP_2_INSTALL_PACKAGES:-pending}
STEP_3_CONFIGURE_UFW=${STEP_3_CONFIGURE_UFW:-pending}
STEP_4_CLONE_REPOSITORY=${STEP_4_CLONE_REPOSITORY:-pending}
STEP_5_SETUP_ENV=${STEP_5_SETUP_ENV:-pending}
STEP_6_DOCKER_COMPOSE=${STEP_6_DOCKER_COMPOSE:-pending}
STEP_7_EXTRACT_TOKEN=${STEP_7_EXTRACT_TOKEN:-pending}
STEP_8_CREATE_TFVARS=${STEP_8_CREATE_TFVARS:-pending}
STEP_9_TERRAFORM_INIT=${STEP_9_TERRAFORM_INIT:-pending}
STEP_10_TERRAFORM_PLAN=${STEP_10_TERRAFORM_PLAN:-pending}
STEP_11_TERRAFORM_APPLY=${STEP_11_TERRAFORM_APPLY:-pending}
STEP_12_OUTPUT_SUMMARY=${STEP_12_OUTPUT_SUMMARY:-pending}
ZITADEL_DOMAIN=${ZITADEL_DOMAIN:-}
GOALDONE_URL=${GOALDONE_URL:-}
TERRAFORM_TOKEN=${TERRAFORM_TOKEN:-}
BACKEND_PAT=${BACKEND_PAT:-}
APP_CLIENT_ID=${APP_CLIENT_ID:-}
DEPLOY_WORK_DIR=${DEPLOY_WORK_DIR:-}
EOF

    # Atomic move
    if mv "$temp_file" "$STATE_FILE"; then
        show_success "State saved."
    else
        show_error "Failed to save state."
        rm -f "$temp_file"
        return 1
    fi
}

save_credentials() {
    # Save plaintext credentials for recovery (per D-05 security trade-off)
    # File permissions: 600 (user read-only)
    local temp_creds="${CREDS_FILE}.tmp"

    cat > "$temp_creds" <<EOF
# Plaintext credentials for session recovery (security trade-off: convenience during multi-step recovery)
# File permissions: 600 (chmod 600 enforced below)
ZITADEL_MASTERKEY="${ZITADEL_MASTERKEY:-}"
ZITADEL_ADMIN_PASSWORD="${ZITADEL_ADMIN_PASSWORD:-}"
POSTGRES_ADMIN_PASSWORD="${POSTGRES_ADMIN_PASSWORD:-}"
POSTGRES_ZITADEL_PASSWORD="${POSTGRES_ZITADEL_PASSWORD:-}"
ZITADEL_DOMAIN="${ZITADEL_DOMAIN:-}"
GOALDONE_URL="${GOALDONE_URL:-}"
TERRAFORM_TOKEN="${TERRAFORM_TOKEN:-}"
BACKEND_PAT="${BACKEND_PAT:-}"
APP_CLIENT_ID="${APP_CLIENT_ID:-}"
SMTP_HOST="${SMTP_HOST:-}"
SMTP_USER="${SMTP_USER:-}"
SMTP_PASSWORD="${SMTP_PASSWORD:-}"
SMTP_SENDER_ADDRESS="${SMTP_SENDER_ADDRESS:-}"
EOF

    if mv "$temp_creds" "$CREDS_FILE"; then
        chmod 600 "$CREDS_FILE"
        show_success "Credentials cached (file permissions: 600)."
    else
        show_error "Failed to save credentials."
        rm -f "$temp_creds"
        return 1
    fi
}

reset_state() {
    show_info "Resetting deployment state..."
    rm -f "$STATE_FILE" "$CREDS_FILE"
    LAST_COMPLETED_STEP=0
    show_success "State reset complete. Next run will start from step 1."
}

mark_step_complete() {
    local step_number=$1
    local step_name=$2

    # Update step status
    LAST_COMPLETED_STEP=$step_number
    
    # Update status variables in memory so they are saved correctly
    case $step_number in
        1) STEP_1_VERIFY_PREREQUISITES="completed" ;;
        2) STEP_2_INSTALL_PACKAGES="completed" ;;
        3) STEP_3_CONFIGURE_UFW="completed" ;;
        4) STEP_4_CLONE_REPOSITORY="completed" ;;
        5) STEP_5_SETUP_ENV="completed" ;;
        6) STEP_6_DOCKER_COMPOSE="completed" ;;
        7) STEP_7_EXTRACT_TOKEN="completed" ;;
        8) STEP_8_CREATE_TFVARS="completed" ;;
        9) STEP_9_TERRAFORM_INIT="completed" ;;
        10) STEP_10_TERRAFORM_PLAN="completed" ;;
        11) STEP_11_TERRAFORM_APPLY="completed" ;;
        12) STEP_12_OUTPUT_SUMMARY="completed" ;;
    esac

    show_success "Completed: Step $step_number ($step_name)"
    save_state
}

# ==============================================================================
# Trap Handlers (per D-17)
# ==============================================================================
cleanup_on_exit() {
    local exit_code=$?

    # Prevent double execution
    if [[ "$CLEANUP_CALLED" == true ]]; then
        return
    fi
    CLEANUP_CALLED=true

    if [[ $exit_code -ne 0 ]]; then
        echo ""
        show_warning "Script interrupted or exiting with code $exit_code"
        
        # Only prompt if we are in an interactive shell
        if [[ -t 0 ]]; then
            echo ""
            read -p "Save current state before exiting? (yes/no): " -r save_choice
            if [[ "$save_choice" == "yes" ]]; then
                save_state
                show_success "State saved. Next run will allow resume."
            fi
        fi
    fi

    # Clean up any temporary files
    rm -f "${STATE_FILE}.tmp" "${CREDS_FILE}.tmp"
}

# Register trap handlers
trap 'cleanup_on_exit' EXIT INT TERM

# ==============================================================================
# Pre-flight Validation (per D-12, D-13, D-14, D-15, D-16)
# ==============================================================================
check_prerequisites() {
    local validation_failed=false

    show_info "Validating prerequisites..."

    # D-12: Ubuntu 24.04 version check
    if [[ -f /etc/os-release ]]; then
        # shellcheck disable=SC1091
        source /etc/os-release
        if [[ "$PRETTY_NAME" != "Ubuntu 24.04"* ]]; then
            show_error "This script requires Ubuntu 24.04. Detected: $PRETTY_NAME. Aborting."
            validation_failed=true
        else
            show_success "Ubuntu version: $PRETTY_NAME"
        fi
    else
        show_error "Cannot determine OS version (/etc/os-release not found)."
        validation_failed=true
    fi

    # D-13: Required binaries check
    local required_binaries=("sudo" "curl" "git" "apt-get" "jq")
    for binary in "${required_binaries[@]}"; do
        if command -v "$binary" &>/dev/null; then
            show_success "Binary found: $binary"
        else
            show_error "Missing required binary: $binary. Install with: sudo apt-get install $binary"
            validation_failed=true
        fi
    done

    # D-14: Disk space validation
    local available_disk_gb
    available_disk_gb=$(df -BG / | tail -1 | awk '{print $4}' | sed 's/G//')
    if [[ $available_disk_gb -lt 10 ]]; then
        show_error "Insufficient disk space. Available: ${available_disk_gb}GB, Minimum required: 10GB"
        validation_failed=true
    elif [[ $available_disk_gb -lt 20 ]]; then
        show_warning "Low disk space. Available: ${available_disk_gb}GB (recommended minimum: 20GB)"
    else
        show_success "Disk space: ${available_disk_gb}GB available (≥20GB)"
    fi

    # D-15: RAM validation
    local available_ram_kb
    available_ram_kb=$(free | awk 'NR==2 {print $7}' 2>/dev/null || echo 0) # Available column
    local available_ram_gb=$((available_ram_kb / 1024 / 1024))
    if [[ $available_ram_gb -lt 4 ]]; then
        show_error "Insufficient RAM. Available: ${available_ram_gb}GB, Minimum required: 4GB"
        validation_failed=true
    elif [[ $available_ram_gb -lt 8 ]]; then
        show_warning "Low RAM. Available: ${available_ram_gb}GB (recommended minimum: 8GB)"
    else
        show_success "RAM: ${available_ram_gb}GB available (≥8GB)"
    fi

    # D-16: Sudo access test
    if sudo -n true 2>/dev/null; then
        show_success "Sudo access verified (no password required)"
    else
        show_error "This script requires passwordless sudo access. Configure with: visudo"
        show_error "Add the following line (replace USER with your username): USER ALL=(ALL) NOPASSWD: ALL"
        validation_failed=true
    fi

    if [[ "$validation_failed" == true ]]; then
        return 1
    fi
    return 0
}

# ==============================================================================
# Checkpoint Recovery (per D-07, D-08)
# ==============================================================================
prompt_recovery_action() {
    if [[ ! -f "$STATE_FILE" ]]; then
        return 0
    fi

    local last_step=$LAST_COMPLETED_STEP
    local next_step=$((last_step + 1))

    if [[ $last_step -eq 0 ]]; then
        return 0
    fi
    
    if [[ $last_step -ge $TOTAL_STEPS ]]; then
        show_info "Deployment already completed. Use --reset-state to start over."
        exit 0
    fi

    local step_names=("" "Verify Prerequisites" "Install Packages" "Configure UFW" "Clone Repository" "Setup .env" "Start Docker Compose" "Extract Terraform Token" "Create .tfvars" "Terraform Init" "Terraform Plan" "Terraform Apply" "Output Summary")
    local last_step_name="${step_names[$last_step]:-Step $last_step}"

    echo "" >&3
    echo "Previous deployment found (last completed: Step $last_step - $last_step_name)" >&3
    echo "" >&3
    echo "Choose recovery action:" >&3
    echo "  1) Resume from Step $next_step (continue from last completed)" >&3
    echo "  2) Retry Step $last_step (re-execute the last step)" >&3
    echo "  3) Start fresh (delete state and begin from step 1)" >&3
    echo "" >&3

    echo -n "Enter choice (1, 2, or 3): " >&3
    read -r choice
    case "$choice" in
        1)
            show_info "Resuming from Step $next_step..."
            START_STEP=$next_step
            ;;
        2)
            show_info "Retrying Step $last_step..."
            START_STEP=$last_step
            ;;
        3)
            echo -n "Are you sure? This will delete all progress. (yes/no): " >&3
            read -r confirm
            if [[ "$confirm" == "yes" ]]; then
                reset_state
                # Clear log on Start Fresh
                echo "--- Fresh Start: $(date) ---" > "$LOG_FILE"
                START_STEP=1
            else
                echo "Cancelled. Exiting." >&3
                exit 0
            fi
            ;;
        *)
            show_error "Invalid choice. Exiting."
            exit 1
            ;;
    esac
}

# ==============================================================================
# Step Implementations (Phase 2: Steps 2-6)
# ==============================================================================
step_install_packages() {
    show_info "⏳ Installing packages (Docker, Git, Terraform, jq)..."
    
    # Update apt cache
    sudo apt-get update >/dev/null 2>&1 &
    show_spinner "$!"
    wait "$!" || error_exit "apt-get update failed" "apt"
    
    # Install packages
    sudo apt-get install -y docker.io git terraform jq >/dev/null 2>&1 &
    show_spinner "$!"
    wait "$!" || error_exit "Package installation failed" "apt"
    
    # Verify and show versions
    docker --version >/dev/null 2>&1 || error_exit "Docker not found" "apt"
    show_success "Docker $(docker --version | awk '{print $3}' | tr -d ',') installed"
    
    git --version >/dev/null 2>&1 || error_exit "Git not found" "apt"
    show_success "Git $(git --version | awk '{print $3}') installed"
    
    terraform --version >/dev/null 2>&1 || error_exit "Terraform not found" "apt"
    show_success "Terraform $(terraform --version | head -1 | awk '{print $2}') installed"

    jq --version >/dev/null 2>&1 || error_exit "jq not found" "apt"
    show_success "jq $(jq --version | head -1) installed"
    
    mark_step_complete 2 "Install Packages"
    return 0
}

step_configure_ufw() {
    show_info "Checking UFW status..."
    
    if sudo ufw status | grep -q "Status: active"; then
        show_info "UFW firewall already enabled."
        mark_step_complete 3 "Configure UFW"
        return 0
    fi
    
    show_warning "Firewall Configuration"
    echo "" >&3
    echo "UFW will be enabled with these rules:" >&3
    echo "  • Incoming: Deny (default)" >&3
    echo "  • Outgoing: Allow (default)" >&3
    echo "  • SSH (port 22): Allow" >&3
    echo "  • HTTP (port 80): Allow" >&3
    echo "  • HTTPS (port 443): Allow" >&3
    echo "" >&3
    echo -n "$(get_timestamp) ${BLUE}⏳ SSH access will remain available. Continue? (y/n): ${NC}" >&3
    read -r ufw_choice
    
    if [[ "$ufw_choice" == "y" ]]; then
        show_info "Applying UFW rules..."
        sudo ufw default deny incoming
        sudo ufw allow 22/tcp
        sudo ufw allow 80/tcp
        sudo ufw allow 443/tcp
        
        echo y | sudo ufw enable
        show_success "UFW firewall configured and enabled."
        sudo ufw status
    else
        show_warning "Skipping UFW configuration."
    fi
    
    mark_step_complete 3 "Configure UFW"
    return 0
}

step_clone_repository() {
    local repo_url="https://github.com/goaldone-dhbw/goaldone-produkt.git"
    local repo_dir="goaldone-produkt"
    
    if [[ -d "$repo_dir" ]]; then
        show_info "Repository directory found: ./${repo_dir}/"
        echo "" >&3
        echo "Options:" >&3
        echo "  1) Reuse existing (skip clone)" >&3
        echo "  2) Re-clone (delete and fetch fresh)" >&3
        echo "  3) Cancel" >&3
        echo "" >&3
        echo -n "$(get_timestamp) ${BLUE}⏳ Choose option (1-3): ${NC}" >&3
        read -r clone_choice
        
        case "$clone_choice" in
            1)
                show_info "Using existing repository."
                ;;
            2)
                show_warning "Removing existing directory..."
                rm -rf "$repo_dir"
                show_info "⏳ Cloning repository from GitHub..."
                git clone "$repo_url" >/dev/null 2>&1 &
                show_spinner "$!"
                wait "$!" || error_exit "Clone failed" "git"
                ;;
            3)
                show_error "Clone cancelled."
                return 1
                ;;
            *)
                error_exit "Invalid choice."
                ;;
        esac
    else
        show_info "⏳ Cloning repository from GitHub..."
        git clone "$repo_url" >/dev/null 2>&1 &
        show_spinner "$!"
        wait "$!" || error_exit "Clone failed" "git"
    fi
    
    # Verify clone
    [[ -d "$repo_dir/.git" ]] || error_exit "Clone verification failed: .git directory not found" "git"
    
    # Detect partial/corrupted clone
    if ! git -C "$repo_dir" fsck --quiet >/dev/null 2>&1; then
        show_warning "Detected corrupted clone; removing and re-cloning..."
        rm -rf "$repo_dir"
        git clone "$repo_url" >/dev/null 2>&1 &
        show_spinner "$!"
        wait "$!" || error_exit "Retry clone failed" "git"
    fi
    
    # Set deployment work directory
    DEPLOY_WORK_DIR="$(pwd)/${repo_dir}/infra"
    show_success "Repository ready at $DEPLOY_WORK_DIR"
    
    mark_step_complete 4 "Clone Repository"
    return 0
}

step_setup_env() {
    [[ -n "${DEPLOY_WORK_DIR:-}" ]] || error_exit "DEPLOY_WORK_DIR not set. Run step 4 first."
    cd "$DEPLOY_WORK_DIR" || error_exit "Could not enter directory: $DEPLOY_WORK_DIR"
    
    local template=".env-example"
    local target=".env"
    
    [[ -f "$template" ]] || error_exit "Template $template not found in $DEPLOY_WORK_DIR"
    
    if [[ -f "$target" ]]; then
        show_info ".env file already exists."
        echo "" >&3
        echo "Options:" >&3
        echo "  1) Reuse existing (skip collection)" >&3
        echo "  2) Create new (overwrite)" >&3
        echo "  3) Backup and create new" >&3
        echo "" >&3
        echo -n "$(get_timestamp) ${BLUE}⏳ Choose option (1-3): ${NC}" >&3
        read -r env_choice
        
        case "$env_choice" in
            1)
                show_info "Reusing existing .env file."
                mark_step_complete 5 "Setup .env"
                return 0
                ;;
            2)
                show_warning "Overwriting existing .env..."
                ;;
            3)
                show_info "Backing up existing .env to .env.backup..."
                cp "$target" "${target}.backup"
                ;;
            *)
                error_exit "Invalid choice."
                ;;
        esac
    fi
    
    # Auto-generate Masterkey
    ZITADEL_MASTERKEY=$(tr -dc A-Za-z0-9 </dev/urandom | head -c 32)
    show_info "Generated Zitadel Master Key: $ZITADEL_MASTERKEY"
    cache_credential "ZITADEL_MASTERKEY" "$ZITADEL_MASTERKEY"
    
    # Collect other credentials
    ZITADEL_ADMIN_PASSWORD=$(prompt_input "Zitadel admin password (min 8 chars):" ".{8,}" true) || return 1
    cache_credential "ZITADEL_ADMIN_PASSWORD" "$ZITADEL_ADMIN_PASSWORD"
    
    POSTGRES_ADMIN_PASSWORD=$(prompt_input "PostgreSQL admin password (min 8 chars):" ".{8,}" true) || return 1
    cache_credential "POSTGRES_ADMIN_PASSWORD" "$POSTGRES_ADMIN_PASSWORD"
    
    POSTGRES_ZITADEL_PASSWORD=$(prompt_input "PostgreSQL Zitadel password (min 8 chars):" ".{8,}" true) || return 1
    cache_credential "POSTGRES_ZITADEL_PASSWORD" "$POSTGRES_ZITADEL_PASSWORD"
    
    # Stricter domain validation (SEC-01)
    ZITADEL_DOMAIN=$(prompt_input "Zitadel domain (e.g., sso.example.com):" "^[a-z0-9.-]+\.[a-z]{2,}$") || return 1
    cache_credential "ZITADEL_DOMAIN" "$ZITADEL_DOMAIN"
    
    GOALDONE_URL=$(prompt_input "GoalDone URL (e.g., app.example.com):" "^[a-z0-9.-]+\.[a-z]{2,}$") || return 1
    cache_credential "GOALDONE_URL" "$GOALDONE_URL"
    
    # Create .env from template
    show_info "Creating .env from template..."
    cp "$template" "$target"
    chmod 600 "$target"
    
    # Substitutions using update_env_var (SEC-01)
    update_env_var "$target" "ZITADEL_DOMAIN" "$ZITADEL_DOMAIN"
    update_env_var "$target" "ZITADEL_MASTERKEY" "$ZITADEL_MASTERKEY"
    update_env_var "$target" "ZITADEL_ADMIN_PASSWORD" "$ZITADEL_ADMIN_PASSWORD"
    update_env_var "$target" "POSTGRES_ADMIN_PASSWORD" "$POSTGRES_ADMIN_PASSWORD"
    update_env_var "$target" "POSTGRES_ZITADEL_PASSWORD" "$POSTGRES_ZITADEL_PASSWORD"
    
    test -r "$target" || error_exit "Failed to create $target"
    
    show_success ".env file created and configured."
    
    mark_step_complete 5 "Setup .env"
    return 0
}

step_docker_compose_up() {
    [[ -n "${DEPLOY_WORK_DIR:-}" ]] || error_exit "DEPLOY_WORK_DIR not set. Run step 4 first."
    cd "$DEPLOY_WORK_DIR" || error_exit "Could not enter directory: $DEPLOY_WORK_DIR"
    
    show_info "⏳ Starting Docker Compose services..."
    
    local cmd="docker compose"
    if ! $cmd version >/dev/null 2>&1; then
        cmd="docker-compose"
    fi
    
    $cmd up -d >/dev/null 2>&1 &
    show_spinner "$!"
    wait "$!" || error_exit "Failed to start Docker Compose" "docker"
    
    show_info "Waiting for containers to stabilize..."
    sleep 3
    
    # Validate services running
    $cmd ps | tee /tmp/docker-compose-ps.txt >/dev/null 2>&1
    if ! grep -E 'zitadel|postgres' /tmp/docker-compose-ps.txt | grep -q 'Up'; then
        error_exit "Services not running. Check logs with: $cmd logs" "docker"
    fi
    
    if [[ "$SKIP_HEALTH_CHECK" == "true" ]]; then
        show_warning "Skipping health check validation as requested."
        mark_step_complete 6 "Docker Compose"
        return 0
    fi
    
    show_info "Running Zitadel health check (30s timeout)..."
    local timeout=30
    local i=0
    while [[ $i -lt $timeout ]]; do
        if curl -s http://localhost:8080/health >/dev/null 2>&1; then
            echo "" >&3
            show_success "Zitadel health check passed."
            mark_step_complete 6 "Docker Compose"
            return 0
        fi
        
        if [[ $((i % 3)) -eq 0 ]]; then
            echo -n "." >&3
        fi
        
        sleep 1
        i=$((i + 1))
    done
    
    echo "" >&3
    error_exit "Zitadel did not respond after 30 seconds." "docker"
}

step_extract_token() {
    [[ -n "${DEPLOY_WORK_DIR:-}" ]] || error_exit "DEPLOY_WORK_DIR not set. Run step 4 first."
    cd "$DEPLOY_WORK_DIR" || error_exit "Could not enter directory: $DEPLOY_WORK_DIR"

    show_info "⏳ Extracting Terraform token (Step 7)..."
    
    local key_file="infra-setup/machinekey/terraform-sa.token"
    local timeout=60
    local interval=2
    local elapsed=0

    show_info "Waiting for $key_file to be generated by Zitadel..."
    
    while [[ ! -f "$key_file" ]]; do
        if [[ $elapsed -ge $timeout ]]; then
            error_exit "Timeout waiting for $key_file after ${timeout}s. Ensure Zitadel setup finished correctly."
        fi
        sleep $interval
        elapsed=$((elapsed + interval))
        echo -n "." >&2
    done
    echo "" >&2

    # Extract token (raw PAT)
    TERRAFORM_TOKEN=$(cat "$key_file")
    
    if [[ -z "$TERRAFORM_TOKEN" ]]; then
        error_exit "Failed to read token from $key_file or token is empty."
    fi

    show_success "Terraform token extracted successfully."
    cache_credential "TERRAFORM_TOKEN" "$TERRAFORM_TOKEN"
    
    mark_step_complete 7 "Extract Token"
    return 0
}

step_create_tfvars() {
    [[ -n "${DEPLOY_WORK_DIR:-}" ]] || error_exit "DEPLOY_WORK_DIR not set. Run step 4 first."
    cd "$DEPLOY_WORK_DIR" || error_exit "Could not enter directory: $DEPLOY_WORK_DIR"

    show_info "⏳ Configuring SMTP and generating terraform.tfvars (Step 8)..."

    # Prompt for SMTP settings
    SMTP_HOST=$(prompt_input "SMTP Host (e.g., smtp.mailtrap.io:587):" "^[a-z0-9.-]+(:[0-9]+)?$") || return 1
    cache_credential "SMTP_HOST" "$SMTP_HOST"

    SMTP_USER=$(prompt_input "SMTP User:") || return 1
    cache_credential "SMTP_USER" "$SMTP_USER"

    SMTP_PASSWORD=$(prompt_input "SMTP Password:" "" true) || return 1
    cache_credential "SMTP_PASSWORD" "$SMTP_PASSWORD"

    # Stricter email validation (SEC-01)
    SMTP_SENDER_ADDRESS=$(prompt_input "SMTP Sender Address (e.g., noreply@example.com):" "^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$") || return 1
    cache_credential "SMTP_SENDER_ADDRESS" "$SMTP_SENDER_ADDRESS"

    # Ensure terraform directory exists
    mkdir -p terraform

    # Create terraform.tfvars using escape_hcl (SEC-01)
    cat > terraform/terraform.tfvars <<EOF
# Generated by deploy.sh at $(date)
zitadel_domain         = $(escape_hcl "${ZITADEL_DOMAIN}")
zitadel_token          = $(escape_hcl "${TERRAFORM_TOKEN}")
goaldone_url           = $(escape_hcl "${GOALDONE_URL}")
smtp_host              = $(escape_hcl "${SMTP_HOST}")
smtp_user              = $(escape_hcl "${SMTP_USER}")
smtp_password          = $(escape_hcl "${SMTP_PASSWORD}")
smtp_sender_address    = $(escape_hcl "${SMTP_SENDER_ADDRESS}")
EOF

    chmod 600 terraform/terraform.tfvars
    show_success "terraform/terraform.tfvars generated with 600 permissions."

    mark_step_complete 8 "Create .tfvars"
    return 0
}

step_terraform_init() {
    [[ -n "${DEPLOY_WORK_DIR:-}" ]] || error_exit "DEPLOY_WORK_DIR not set. Run step 4 first."
    cd "${DEPLOY_WORK_DIR}/terraform" || error_exit "Could not enter terraform directory"

    show_info "⏳ Initializing Terraform (Step 9)..."
    terraform init -input=false >/dev/null 2>&1 &
    show_spinner "$!"
    wait "$!" || error_exit "Terraform init failed" "terraform"
    
    show_success "Terraform initialized."
    mark_step_complete 9 "Terraform Init"
    return 0
}

step_terraform_plan() {
    [[ -n "${DEPLOY_WORK_DIR:-}" ]] || error_exit "DEPLOY_WORK_DIR not set. Run step 4 first."
    cd "${DEPLOY_WORK_DIR}/terraform" || error_exit "Could not enter terraform directory"

    show_info "⏳ Generating Terraform plan (Step 10)..."
    terraform plan -input=false -out=tfplan >/tmp/terraform-plan.log 2>&1 &
    show_spinner "$!"
    wait "$!" || error_exit "Terraform plan failed" "terraform"
    
    local summary
    summary=$(grep -E "Plan: [0-9]+ to add, [0-9]+ to change, [0-9]+ to destroy" /tmp/terraform-plan.log || true)
    if [[ -n "$summary" ]]; then
        show_info "Plan Summary: $summary"
    fi

    # Prompt for confirmation if in an interactive shell
    if [[ -t 0 ]]; then
        echo "" >&3
        echo -n "$(get_timestamp) ${BLUE}⏳ Do you want to proceed with these changes? (yes/no): ${NC}" >&3
        read -r plan_confirm
        if [[ "$plan_confirm" != "yes" ]]; then
            show_error "Terraform apply cancelled by user."
            return 1
        fi
    fi
    
    show_success "Terraform plan generated."
    mark_step_complete 10 "Terraform Plan"
    return 0
}

step_terraform_apply() {
    [[ -n "${DEPLOY_WORK_DIR:-}" ]] || error_exit "DEPLOY_WORK_DIR not set. Run step 4 first."
    cd "${DEPLOY_WORK_DIR}/terraform" || error_exit "Could not enter terraform directory"

    show_info "⏳ Applying Terraform plan (Step 11)..."
    terraform apply -input=false tfplan >/dev/null 2>&1 &
    show_spinner "$!"
    wait "$!" || error_exit "Terraform apply failed" "terraform"
    
    show_info "Extracting outputs..."
    BACKEND_PAT=$(terraform output -raw goaldone_backend_pat) || error_exit "Failed to extract goaldone_backend_pat" "terraform"
    APP_CLIENT_ID=$(terraform output -raw goaldone_app_client_id) || error_exit "Failed to extract goaldone_app_client_id" "terraform"
    
    cache_credential "BACKEND_PAT" "$BACKEND_PAT"
    cache_credential "APP_CLIENT_ID" "$APP_CLIENT_ID"
    
    show_success "Terraform applied and outputs extracted."
    mark_step_complete 11 "Terraform Apply"
    return 0
}

step_output_summary() {
    show_info "⏳ Generating final output summary (Step 12)..."
    
    local output_file="${SCRIPT_DIR}/deploy-outputs.txt"
    
    # Ensure variables are available (they should be in environment, but just in case)
    local zitadel_domain="${ZITADEL_DOMAIN:-}"
    local goaldone_url="${GOALDONE_URL:-}"
    local backend_pat="${BACKEND_PAT:-}"
    local app_client_id="${APP_CLIENT_ID:-}"

    {
        echo "=============================================================================="
        echo "GoalDone Deployment Summary"
        echo "Generated on: $(date)"
        echo "=============================================================================="
        echo ""
        echo "Access URLs:"
        echo "  - Zitadel (SSO): https://${zitadel_domain}"
        echo "  - GoalDone App:  https://${goaldone_url}"
        echo ""
        echo "Credentials & IDs:"
        echo "  - Backend PAT:      ${backend_pat}"
        echo "  - OIDC Client ID:   ${app_client_id}"
        echo ""
        echo "Next Steps:"
        echo "  1. Backend Configuration:"
        echo "     - Update 'backend/src/main/resources/application-prod.yaml' (if not using env vars)"
        echo "     - Ensure it uses the Backend PAT for machine-to-machine communication."
        echo ""
        echo "  2. Frontend Configuration:"
        echo "     - Update 'frontend/src/environments/environment.prod.ts' with the OIDC Client ID."
        echo ""
        echo "  3. Deployment Verification:"
        echo "     - Visit https://${goaldone_url} and log in via Zitadel."
        echo ""
        echo "=============================================================================="
    } | tee "$output_file"

    chmod 600 "$output_file"
    show_success "Summary generated and saved to $output_file"
    
    mark_step_complete 12 "Output Summary"
    return 0
}

# ==============================================================================
# Usage and Help
# ==============================================================================
usage() {
    cat <<EOF
GoalDone Automated Installation Script

Usage:
  sudo ./deploy.sh [OPTIONS]

Options:
  --help                Show this help message
  --reset-state         Delete .deploy-state and .deploy-state.creds (with confirmation)
  --skip-health-check   Skip Zitadel health check validation after startup

Prerequisites:
  - Run with sudo
  - Ubuntu 24.04
  - Network access
  - Zitadel domain and SMTP credentials ready

State Management:
  The script maintains .deploy-state file to track progress.
EOF
}

# ==============================================================================
# Argument Parsing
# ==============================================================================
while [[ $# -gt 0 ]]; do
    case "$1" in
        --help)
            usage
            exit 0
            ;;
        --reset-state)
            # Check if user is root for state reset
            if [[ $UID -ne 0 ]]; then
                show_error "This operation must be run as root. Run with: sudo ./deploy.sh --reset-state"
                exit 1
            fi
            echo -n "Delete .deploy-state and .deploy-state.creds? (yes/no): "
            read -r response
            if [[ "$response" == "yes" ]]; then
                reset_state
                exit 0
            else
                echo "Cancelled."
                exit 0
            fi
            ;;
        --skip-health-check)
            SKIP_HEALTH_CHECK=true
            ;;
        *)
            show_error "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
    shift
done

# Check if user is root (per D-03)
if [[ $UID -ne 0 ]]; then
    show_error "This script must be run as root. Run with: sudo ./deploy.sh"
    exit 1
fi

# ==============================================================================
# Main Execution Loop
# ==============================================================================
main() {
    # Initialize logging (append mode)
    exec >> "$LOG_FILE" 2>&1

    show_header "GoalDone Automated Installation"
    show_info "Starting GoalDone Automated Installation Script"
    
    load_state

    # Step 1: Pre-flight validation (must happen before checkpoint prompt per D-07)
    if ! check_prerequisites; then
        show_error "Pre-flight validation failed. See $LOG_FILE for details."
        exit 1
    fi
    show_success "All prerequisites met."

    if [[ -f "$STATE_FILE" ]]; then
        prompt_recovery_action
    fi

    show_info "Starting execution from Step $START_STEP to Step $TOTAL_STEPS..."
    echo "" >&3

    for step in $(seq "$START_STEP" "$TOTAL_STEPS"); do
        local step_start_time=$(date +%s)
        case $step in
            1)
                show_header "Step 1: Verify Prerequisites"
                check_prerequisites || error_exit "Prerequisites validation failed"
                mark_step_complete 1 "Verify Prerequisites"
                ;;
            2)
                show_header "Step 2: Install Packages"
                step_install_packages
                ;;
            3)
                show_header "Step 3: Configure UFW"
                step_configure_ufw
                ;;
            4)
                show_header "Step 4: Clone Repository"
                step_clone_repository
                ;;
            5)
                show_header "Step 5: Setup .env"
                step_setup_env
                ;;
            6)
                show_header "Step 6: Docker Compose"
                step_docker_compose_up
                ;;
            7)
                show_header "Step 7: Extract Token"
                step_extract_token
                ;;
            8)
                show_header "Step 8: Create .tfvars"
                step_create_tfvars
                ;;
            9)
                show_header "Step 9: Terraform Init"
                step_terraform_init
                ;;
            10)
                show_header "Step 10: Terraform Plan"
                step_terraform_plan
                ;;
            11)
                show_header "Step 11: Terraform Apply"
                step_terraform_apply
                ;;
            12)
                show_header "Step 12: Output Summary"
                step_output_summary
                ;;
        esac
        show_info "Step $step completed in $(get_duration $step_start_time)"
    done

    show_header "Installation Complete"
    show_success "All steps completed successfully in $(get_duration $SCRIPT_START_TIME)!"
    show_info "Total execution time: $(get_duration $SCRIPT_START_TIME)"
}

# Start script

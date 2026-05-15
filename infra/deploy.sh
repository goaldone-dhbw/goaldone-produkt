#!/bin/bash
# GoalDone Deployment Script
# Repository cloning, environment setup, and Terraform deployment
# Prerequisites: Run install-deps.sh first (with sudo)

set -euo pipefail
IFS=$'\n\t'

# ==============================================================================
# Global Variables
# ==============================================================================
if [[ -n "${BASH_SOURCE[0]:-}" && "${BASH_SOURCE[0]}" != "/dev/stdin" && -f "${BASH_SOURCE[0]}" ]]; then
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
else
    SCRIPT_DIR="$HOME"
fi
STATE_FILE="${SCRIPT_DIR}/.deploy-state"
CREDS_FILE="${SCRIPT_DIR}/.deploy-state.creds"
LOG_FILE="${SCRIPT_DIR}/deploy.log"
TOTAL_STEPS=9
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

    echo "[$(get_timestamp)] prompt_input called: $prompt_text" >> "$LOG_FILE"

    while [[ $attempts -lt $max_attempts ]]; do
        attempts=$((attempts + 1))

        # Ensure we're reading from the terminal, not a pipe
        if [[ "$is_hidden" == "true" ]]; then
            echo -n -e "$(get_timestamp) ${BLUE}⏳ $prompt_text ${NC}" >&3
            input=""
            local char
            while IFS= read -rsn1 char < /dev/tty; do
                if [[ $char == $'\0' || $char == $'\n' ]]; then
                    break
                elif [[ $char == $'\177' || $char == $'\b' ]]; then
                    if [[ -n "$input" ]]; then
                        input="${input%?}"
                        printf '\b \b' >&3
                    fi
                else
                    input+="$char"
                    printf '*' >&3
                fi
            done
            echo "" >&3
        else
            echo -n -e "$(get_timestamp) ${BLUE}⏳ $prompt_text ${NC}" >&3
            read -r input < /dev/tty 2>&3 || {
                echo "[$(get_timestamp)] ERROR: Failed to read input from /dev/tty" >> "$LOG_FILE"
                show_error "Failed to read input"
                return 1
            }
        fi

        echo "[$(get_timestamp)] Input received (${#input} chars)" >> "$LOG_FILE"

        # Adaptive correction: trim whitespace
        input="${input#"${input%%[![:space:]]*}"}"
        input="${input%"${input##*[![:space:]]}"}"

        # Adaptive correction: lowercase for domains/URLs
        if [[ "$prompt_text" == *"domain"* ]] || [[ "$prompt_text" == *"URL"* ]] || [[ "$validation_regex" == *"a-z0-9.-"* ]]; then
            local original_input=$input
            input=$(echo "$input" | tr '[:upper:]' '[:lower:]')
            if [[ "$input" != "$original_input" ]]; then
                show_info "Corrected input to lowercase: $input"
                echo "[$(get_timestamp)] Lowercase correction applied" >> "$LOG_FILE"
            fi
        fi

        # Adaptive correction: strip spaces from passwords
        if [[ "$is_hidden" == "true" ]]; then
            if [[ "$input" == *" "* ]]; then
                input="${input// /}"
                show_info "Stripped spaces from password."
                echo "[$(get_timestamp)] Spaces stripped from password" >> "$LOG_FILE"
            fi
        fi

        # Validation
        if [[ -n "$validation_regex" ]]; then
            if [[ ! "$input" =~ $validation_regex ]]; then
                show_error "Invalid input format. (Attempt $attempts/$max_attempts)"
                echo "[$(get_timestamp)] Validation failed for input against regex: $validation_regex" >> "$LOG_FILE"
                continue
            fi
        fi

        echo "$input"
        echo "[$(get_timestamp)] Input validated and accepted" >> "$LOG_FILE"
        return 0
    done

    show_error "Max attempts reached for input: $prompt_text"
    echo "[$(get_timestamp)] ERROR: Max attempts reached for: $prompt_text" >> "$LOG_FILE"
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
ensure_file_accessibility() {
    # Ensure state and log files are readable/writable by the current non-root user
    # This handles transition from root-owned files (created by install-deps.sh)
    local current_user=$(whoami)

    if [[ -f "$STATE_FILE" && ! -w "$STATE_FILE" ]]; then
        # Try to fix permissions if we can't write
        if sudo -n true 2>/dev/null; then
            sudo chmod 644 "$STATE_FILE" 2>/dev/null || true
            sudo chown "$current_user:$current_user" "$STATE_FILE" 2>/dev/null || true
        else
            show_warning "State file not writable and sudo not available. State changes may not persist."
        fi
    fi

    if [[ -f "$LOG_FILE" && ! -w "$LOG_FILE" ]]; then
        if sudo -n true 2>/dev/null; then
            sudo chmod 644 "$LOG_FILE" 2>/dev/null || true
            sudo chown "$current_user:$current_user" "$LOG_FILE" 2>/dev/null || true
        fi
    fi
}

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
STEP_1_CLONE_REPOSITORY=${STEP_1_CLONE_REPOSITORY:-pending}
STEP_2_SETUP_ENV=${STEP_2_SETUP_ENV:-pending}
STEP_3_DOCKER_COMPOSE=${STEP_3_DOCKER_COMPOSE:-pending}
STEP_4_EXTRACT_TOKEN=${STEP_4_EXTRACT_TOKEN:-pending}
STEP_5_CREATE_TFVARS=${STEP_5_CREATE_TFVARS:-pending}
STEP_6_TERRAFORM_INIT=${STEP_6_TERRAFORM_INIT:-pending}
STEP_7_TERRAFORM_PLAN=${STEP_7_TERRAFORM_PLAN:-pending}
STEP_8_TERRAFORM_APPLY=${STEP_8_TERRAFORM_APPLY:-pending}
STEP_9_OUTPUT_SUMMARY=${STEP_9_OUTPUT_SUMMARY:-pending}
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
FIRST_SUPERADMIN_EMAIL="${FIRST_SUPERADMIN_EMAIL:-}"
FIRST_SUPERADMIN_PASSWORD="${FIRST_SUPERADMIN_PASSWORD:-}"
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
        1) STEP_1_CLONE_REPOSITORY="completed" ;;
        2) STEP_2_SETUP_ENV="completed" ;;
        3) STEP_3_DOCKER_COMPOSE="completed" ;;
        4) STEP_4_EXTRACT_TOKEN="completed" ;;
        5) STEP_5_CREATE_TFVARS="completed" ;;
        6) STEP_6_TERRAFORM_INIT="completed" ;;
        7) STEP_7_TERRAFORM_PLAN="completed" ;;
        8) STEP_8_TERRAFORM_APPLY="completed" ;;
        9) STEP_9_OUTPUT_SUMMARY="completed" ;;
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
        echo "" >&3
        show_warning "Script interrupted or exiting with code $exit_code"
        
        # Only prompt if we are in an interactive shell
        if [[ -c /dev/tty ]]; then
            echo "" >&3
            echo -n "Save current state before exiting? (yes/no): " >&3
            read -r save_choice < /dev/tty 2>&3 || save_choice=""
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
    local required_binaries=("curl" "git" "docker" "terraform" "jq")
    for binary in "${required_binaries[@]}"; do
        if command -v "$binary" &>/dev/null; then
            show_success "Binary found: $binary"
        else
            show_error "Missing required binary: $binary."
            show_info "Please run the dependency installation script first: sudo ./install-deps.sh"
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
    if [[ $available_ram_gb -lt 2 ]]; then
        show_error "Insufficient RAM. Available: ${available_ram_gb}GB, Minimum required: 2GB"
        validation_failed=true
    elif [[ $available_ram_gb -lt 4 ]]; then
        show_warning "Low RAM. Available: ${available_ram_gb}GB (recommended minimum: 4GB)"
    else
        show_success "RAM: ${available_ram_gb}GB available (≥8GB)"
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

    local step_names=("" "Clone Repository" "Setup .env" "Start Docker Compose" "Extract Terraform Token" "Create .tfvars" "Terraform Init" "Terraform Plan" "Terraform Apply" "Output Summary")
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
    read -r choice < /dev/tty 2>&3 || {
        show_error "Failed to read choice"
        return 1
    }
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
            read -r confirm < /dev/tty 2>&3 || confirm=""
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
# Step Implementations
# ==============================================================================
step_clone_repository() {
    local repo_url="https://github.com/goaldone-dhbw/goaldone-produkt.git"
    local repo_branch="setup-script"
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
        read -r clone_choice < /dev/tty 2>&3 || clone_choice=""

        case "$clone_choice" in
            1)
                show_info "Using existing repository."
                ;;
            2)
                show_warning "Removing existing directory..."
                rm -rf "$repo_dir"
                show_info "⏳ Cloning repository from GitHub..."
                git clone --branch "$repo_branch" "$repo_url" &
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
        git clone --branch "$repo_branch" "$repo_url" &
        show_spinner "$!"
        wait "$!" || error_exit "Clone failed" "git"
    fi
    
    # Verify clone
    [[ -d "$repo_dir/.git" ]] || error_exit "Clone verification failed: .git directory not found" "git"
    
    # Detect partial/corrupted clone
    if ! git -C "$repo_dir" fsck --quiet >/dev/null 2>&1; then
        show_warning "Detected corrupted clone; removing and re-cloning..."
        rm -rf "$repo_dir"
        git clone --branch "$repo_branch" "$repo_url" &
        show_spinner "$!"
        wait "$!" || error_exit "Retry clone failed" "git"
    fi
    
    # Set deployment work directory
    DEPLOY_WORK_DIR="$(pwd)/${repo_dir}/infra"
    show_success "Repository ready at $DEPLOY_WORK_DIR"
    echo "[$(get_timestamp)] DEPLOY_WORK_DIR set to: $DEPLOY_WORK_DIR" >> "$LOG_FILE"
    
    mark_step_complete 1 "Clone Repository"
    return 0
}

step_setup_env() {
    echo "[$(get_timestamp)] === STEP 2: Setup .env ===" >> "$LOG_FILE"

    [[ -n "${DEPLOY_WORK_DIR:-}" ]] || error_exit "DEPLOY_WORK_DIR not set. Run step 4 first."

    echo "[$(get_timestamp)] Changing to DEPLOY_WORK_DIR: $DEPLOY_WORK_DIR" >> "$LOG_FILE"
    if ! cd "$DEPLOY_WORK_DIR" 2>> "$LOG_FILE"; then
        echo "[$(get_timestamp)] ERROR: Could not enter directory: $DEPLOY_WORK_DIR" >> "$LOG_FILE"
        error_exit "Could not enter directory: $DEPLOY_WORK_DIR"
    fi

    echo "[$(get_timestamp)] Current working directory: $(pwd)" >> "$LOG_FILE"
    echo "[$(get_timestamp)] Contents: $(ls -la | head -20)" >> "$LOG_FILE"

    # Look for template in standard locations
    local template=""
    if [[ -f "infra-setup/.env-example" ]]; then
        template="infra-setup/.env-example"
        echo "[$(get_timestamp)] Found template at: $template" >> "$LOG_FILE"
    elif [[ -f ".env-example" ]]; then
        template=".env-example"
        echo "[$(get_timestamp)] Found template at: $template" >> "$LOG_FILE"
    elif [[ -f "infra-setup/.env-dev-example" ]]; then
        template="infra-setup/.env-dev-example"
        echo "[$(get_timestamp)] Found template at: $template" >> "$LOG_FILE"
    else
        echo "[$(get_timestamp)] ERROR: No .env template found" >> "$LOG_FILE"
        echo "[$(get_timestamp)] Available files:" >> "$LOG_FILE"
        find . -maxdepth 2 -name "*.env*" -o -name "*.example*" >> "$LOG_FILE" 2>&1
        error_exit "Template file not found. Checked: .env-example, infra-setup/.env-example, infra-setup/.env-dev-example"
    fi

    local target="infra-setup/.env"

    [[ -f "$template" ]] || error_exit "Template $template not found in $DEPLOY_WORK_DIR"
    echo "[$(get_timestamp)] Template verified: $template" >> "$LOG_FILE"

    # Log environment state before interactive input
    echo "[$(get_timestamp)] Pre-interactive-input state:" >> "$LOG_FILE"
    echo "[$(get_timestamp)]   PWD=$(pwd)" >> "$LOG_FILE"
    echo "[$(get_timestamp)]   DEPLOY_WORK_DIR=$DEPLOY_WORK_DIR" >> "$LOG_FILE"
    echo "[$(get_timestamp)]   /dev/tty exists: $([ -c /dev/tty ] && echo yes || echo no)" >> "$LOG_FILE"

    if [[ -f "$target" ]]; then
        show_info ".env file already exists."
        echo "" >&3
        echo "Options:" >&3
        echo "  1) Reuse existing (skip collection)" >&3
        echo "  2) Create new (overwrite)" >&3
        echo "  3) Backup and create new" >&3
        echo "" >&3
        echo -n "$(get_timestamp) ${BLUE}⏳ Choose option (1-3): ${NC}" >&3
        read -r env_choice < /dev/tty 2>&3 || {
            echo "[$(get_timestamp)] ERROR: Failed to read env_choice from /dev/tty" >> "$LOG_FILE"
            error_exit "Failed to read user input"
        }
        echo "[$(get_timestamp)] User chose: $env_choice" >> "$LOG_FILE"

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
    
    # Check if /dev/tty is available for interactive input
    if [[ ! -c /dev/tty ]]; then
        echo "[$(get_timestamp)] WARNING: /dev/tty not accessible" >> "$LOG_FILE"
        show_warning "Interactive terminal not available. Cannot proceed with credential collection."
        echo "[$(get_timestamp)] ERROR: This script requires an interactive terminal (/dev/tty) for credential input." >> "$LOG_FILE"
        echo "[$(get_timestamp)] Suggestion: Run the script from an interactive SSH/terminal session." >> "$LOG_FILE"
        return 1
    fi
    echo "[$(get_timestamp)] /dev/tty verified, proceeding with interactive input" >> "$LOG_FILE"

    # Auto-generate Masterkey using openssl (most reliable method)
    echo "[$(get_timestamp)] About to generate Zitadel Master Key..." >> "$LOG_FILE"
    ZITADEL_MASTERKEY=""

    # Try openssl first (most reliable)
    if command -v openssl &>/dev/null; then
        echo "[$(get_timestamp)] Using openssl for master key generation..." >> "$LOG_FILE"
        ZITADEL_MASTERKEY=$(openssl rand -hex 16 2>> "$LOG_FILE")
        gen_status=$?
        echo "[$(get_timestamp)] openssl rand -hex 16 exit code: $gen_status" >> "$LOG_FILE"
    fi

    # Fallback to base64 + od if openssl didn't work or produce output
    if [[ -z "$ZITADEL_MASTERKEY" ]]; then
        echo "[$(get_timestamp)] Attempting fallback with base64 encoding..." >> "$LOG_FILE"
        # Read 24 bytes and base64 encode, then take first 32 chars
        ZITADEL_MASTERKEY=$(head -c 24 /dev/urandom 2>> "$LOG_FILE" | base64 2>> "$LOG_FILE" | tr -d '=' | cut -c1-32 2>> "$LOG_FILE")
        gen_status=$?
        echo "[$(get_timestamp)] base64 fallback exit code: $gen_status" >> "$LOG_FILE"
    fi

    echo "[$(get_timestamp)] Zitadel Master Key generated (length: ${#ZITADEL_MASTERKEY})" >> "$LOG_FILE"

    if [[ -z "$ZITADEL_MASTERKEY" || ${#ZITADEL_MASTERKEY} -ne 32 ]]; then
        echo "[$(get_timestamp)] ERROR: Invalid master key (length: ${#ZITADEL_MASTERKEY}, expected 32)" >> "$LOG_FILE"
        echo "[$(get_timestamp)] Value: '$ZITADEL_MASTERKEY'" >> "$LOG_FILE"
        error_exit "Failed to generate Zitadel master key"
    fi

    show_info "Generated Zitadel Master Key: $ZITADEL_MASTERKEY"
    cache_credential "ZITADEL_MASTERKEY" "$ZITADEL_MASTERKEY"
    echo "[$(get_timestamp)] Zitadel Master Key cached" >> "$LOG_FILE"

    echo "[$(get_timestamp)] Prompting for Zitadel admin password" >> "$LOG_FILE"

    # Collect other credentials
    ZITADEL_ADMIN_PASSWORD=$(prompt_input "Zitadel admin password (min 8 chars):" ".{8,}" true) || {
        echo "[$(get_timestamp)] ERROR: Failed to get Zitadel admin password" >> "$LOG_FILE"
        return 1
    }
    cache_credential "ZITADEL_ADMIN_PASSWORD" "$ZITADEL_ADMIN_PASSWORD"
    echo "[$(get_timestamp)] Zitadel admin password cached" >> "$LOG_FILE"
    
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

    mark_step_complete 2 "Setup .env"
    return 0
}

step_docker_compose_up() {
    [[ -n "${DEPLOY_WORK_DIR:-}" ]] || error_exit "DEPLOY_WORK_DIR not set. Run step 4 first."
    cd "$DEPLOY_WORK_DIR" || error_exit "Could not enter directory: $DEPLOY_WORK_DIR"

    show_info "⏳ Starting Docker Compose services..."

    # Debug logging for docker command detection
    echo "[$(get_timestamp)] === Docker Command Detection Debug ===" >> "$LOG_FILE"
    echo "[$(get_timestamp)] Current user: $(whoami)" >> "$LOG_FILE"
    echo "[$(get_timestamp)] Current UID: $(id -u)" >> "$LOG_FILE"
    echo "[$(get_timestamp)] Current GID: $(id -g)" >> "$LOG_FILE"
    echo "[$(get_timestamp)] User groups: $(id -G)" >> "$LOG_FILE"
    echo "[$(get_timestamp)] PATH: $PATH" >> "$LOG_FILE"
    echo "[$(get_timestamp)] Running as sudo: ${SUDO_USER:-none}" >> "$LOG_FILE"

    # Test docker command availability
    echo "[$(get_timestamp)] Testing 'command -v docker-compose'..." >> "$LOG_FILE"
    if command -v docker-compose >/dev/null 2>&1; then
        echo "[$(get_timestamp)]   ✓ docker-compose found at: $(command -v docker-compose)" >> "$LOG_FILE"
    else
        echo "[$(get_timestamp)]   ✗ docker-compose not found" >> "$LOG_FILE"
    fi

    echo "[$(get_timestamp)] Testing 'command -v docker'..." >> "$LOG_FILE"
    if command -v docker >/dev/null 2>&1; then
        echo "[$(get_timestamp)]   ✓ docker found at: $(command -v docker)" >> "$LOG_FILE"
    else
        echo "[$(get_timestamp)]   ✗ docker not found" >> "$LOG_FILE"
    fi

    echo "[$(get_timestamp)] Testing 'docker compose version'..." >> "$LOG_FILE"
    if docker compose version >/dev/null 2>&1; then
        echo "[$(get_timestamp)]   ✓ docker compose version: $(docker compose version)" >> "$LOG_FILE"
    else
        echo "[$(get_timestamp)]   ✗ docker compose version failed: $?" >> "$LOG_FILE"
        docker compose version >> "$LOG_FILE" 2>&1 || true
    fi

    local -a cmd
    if command -v docker-compose >/dev/null 2>&1; then
        cmd=("docker-compose")
        echo "[$(get_timestamp)] Selected command: docker-compose" >> "$LOG_FILE"
    elif command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
        cmd=("docker" "compose")
        echo "[$(get_timestamp)] Selected command: docker compose" >> "$LOG_FILE"
    else
        echo "[$(get_timestamp)] ERROR: No docker command found!" >> "$LOG_FILE"
        error_exit "Docker Compose not found. Install Docker Compose v2 or the standalone docker-compose binary." "docker"
    fi

    # Ensure machinekey directory exists and is writable (for Zitadel token generation)
    echo "[$(get_timestamp)] Creating machinekey directory if not exists..." >> "$LOG_FILE"
    mkdir -p infra-setup/machinekey
    chmod 700 infra-setup/machinekey
    echo "[$(get_timestamp)] machinekey directory ready at: $(pwd)/infra-setup/machinekey" >> "$LOG_FILE"

    echo "[$(get_timestamp)] Executing: ${cmd[@]} -f infra-setup/docker-compose.yml up -d" >> "$LOG_FILE"
    "${cmd[@]}" -f infra-setup/docker-compose.yml up -d &
    show_spinner "$!"
    wait "$!" || error_exit "Failed to start Docker Compose" "docker"

    show_info "Waiting for containers to stabilize..."
    sleep 3

    # Validate services running
    "${cmd[@]}" -f infra-setup/docker-compose.yml ps | tee /tmp/docker-compose-ps.txt >/dev/null 2>&1
    if ! grep -E 'zitadel|postgres' /tmp/docker-compose-ps.txt | grep -qiE 'Up|running'; then
        error_exit "Services not running. Check logs with: ${cmd[@]} -f infra-setup/docker-compose.yml logs" "docker"
    fi
    
    if [[ "$SKIP_HEALTH_CHECK" == "true" ]]; then
        show_warning "Skipping health check validation as requested."
        mark_step_complete 3 "Docker Compose"
        return 0
    fi

    show_info "Running Zitadel health check (90s timeout)..."
    echo "[$(get_timestamp)] Health check: polling Docker container health status" >> "$LOG_FILE"
    echo "[$(get_timestamp)] Health check: will also verify https://${ZITADEL_DOMAIN:-sso.example.com}/debug/healthz" >> "$LOG_FILE"

    local timeout=90
    local i=0

    while [[ $i -lt $timeout ]]; do
        # Stage 1: Docker container health
        local container_health
        container_health=$("${cmd[@]}" -f infra-setup/docker-compose.yml ps --format json 2>/dev/null \
            | grep -o '"Health":"[^"]*"' | grep -o 'healthy\|unhealthy\|starting' | head -1 || echo "unknown")

        echo "[$(get_timestamp)] [$i/${timeout}s] Docker health: $container_health" >> "$LOG_FILE"

        if [[ "$container_health" == "healthy" ]]; then
            echo "[$(get_timestamp)] Docker reports zitadel-api healthy." >> "$LOG_FILE"
            echo "" >&3
            show_success "Docker reports Zitadel healthy."

            # Stage 2: External domain check (warning only)
            if [[ -n "$ZITADEL_DOMAIN" ]]; then
                local http_code
                http_code=$(curl -sk -o /dev/null -w "%{http_code}" \
                    "https://${ZITADEL_DOMAIN}/debug/healthz" 2>>"$LOG_FILE" || echo "000")
                echo "[$(get_timestamp)] External check https://${ZITADEL_DOMAIN}/debug/healthz → HTTP $http_code" >> "$LOG_FILE"

                if [[ "$http_code" == "200" ]]; then
                    show_success "External health check passed (HTTP $http_code)."
                else
                    show_warning "Domain not yet reachable (HTTP $http_code) — containers are up, DNS/TLS may need a moment."
                fi
            fi

            mark_step_complete 3 "Docker Compose"
            return 0
        fi

        if [[ $((i % 10)) -eq 0 && $i -gt 0 ]]; then
            echo -n " [${i}s]" >&3
            # Log container ps snapshot every 10s
            "${cmd[@]}" -f infra-setup/docker-compose.yml ps >> "$LOG_FILE" 2>&1
        else
            echo -n "." >&3
        fi

        sleep 1
        i=$((i + 1))
    done

    echo "" >&3
    # Log final state before failing
    echo "[$(get_timestamp)] Health check timed out after ${timeout}s. Final container state:" >> "$LOG_FILE"
    "${cmd[@]}" -f infra-setup/docker-compose.yml ps >> "$LOG_FILE" 2>&1
    echo "[$(get_timestamp)] Zitadel logs (last 30 lines):" >> "$LOG_FILE"
    "${cmd[@]}" -f infra-setup/docker-compose.yml logs zitadel-api --tail=30 >> "$LOG_FILE" 2>&1

    error_exit "Zitadel did not become healthy after ${timeout} seconds. Check deploy.log for details." "docker"
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

    mark_step_complete 4 "Extract Token"
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

    # First superadmin user
    FIRST_SUPERADMIN_EMAIL=$(prompt_input "First Superadmin Email:" "^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$") || return 1
    cache_credential "FIRST_SUPERADMIN_EMAIL" "$FIRST_SUPERADMIN_EMAIL"

    FIRST_SUPERADMIN_PASSWORD=$(prompt_input "First Superadmin Password (min 8 chars, upper+lower+digit+symbol):" "" true) || return 1
    cache_credential "FIRST_SUPERADMIN_PASSWORD" "$FIRST_SUPERADMIN_PASSWORD"

    # Ensure terraform directory exists
    mkdir -p terraform

    # Auto-discover Zitadel org ID from the authenticated service account
    show_info "Discovering Zitadel organization ID..."
    local org_id
    org_id=$(curl -sf "https://${ZITADEL_DOMAIN}/auth/v1/users/me" \
        -H "Authorization: Bearer ${TERRAFORM_TOKEN}" \
        | jq -r '.user.details.resourceOwner') || true

    if [[ -z "$org_id" || "$org_id" == "null" ]]; then
        error_exit "Failed to discover Zitadel org ID. Check ZITADEL_DOMAIN and TERRAFORM_TOKEN."
    fi
    show_success "Discovered org ID: $org_id"

    # Ensure GOALDONE_URL always has https:// prefix
    [[ "${GOALDONE_URL}" != https://* ]] && GOALDONE_URL="https://${GOALDONE_URL}"

    # Create terraform.tfvars using escape_hcl (SEC-01)
    cat > terraform/terraform.tfvars <<EOF
# Generated by deploy.sh at $(date)
zitadel_domain         = $(escape_hcl "${ZITADEL_DOMAIN}")
zitadel_token          = $(escape_hcl "${TERRAFORM_TOKEN}")
org_id                 = $(escape_hcl "${org_id}")
goaldone_url           = $(escape_hcl "${GOALDONE_URL}")
smtp_host              = $(escape_hcl "${SMTP_HOST}")
smtp_user              = $(escape_hcl "${SMTP_USER}")
smtp_password          = $(escape_hcl "${SMTP_PASSWORD}")
smtp_sender_address    = $(escape_hcl "${SMTP_SENDER_ADDRESS}")
first_superadmin_email    = $(escape_hcl "${FIRST_SUPERADMIN_EMAIL}")
first_superadmin_password = $(escape_hcl "${FIRST_SUPERADMIN_PASSWORD}")
EOF

    chmod 600 terraform/terraform.tfvars
    show_success "terraform/terraform.tfvars generated with 600 permissions."

    mark_step_complete 5 "Create .tfvars"
    return 0
}

step_terraform_init() {
    # Check if terraform is installed (may fail if HashiCorp repo setup failed in step 2)
    if ! terraform --version >/dev/null 2>&1; then
        error_exit "Terraform is not installed. This is required for step 6. Check install-deps.sh for repository setup errors." "terraform"
    fi

    [[ -n "${DEPLOY_WORK_DIR:-}" ]] || error_exit "DEPLOY_WORK_DIR not set. Run step 1 first."
    mkdir -p "${DEPLOY_WORK_DIR}/terraform"
    cd "${DEPLOY_WORK_DIR}/terraform" || error_exit "Could not enter terraform directory"

    show_info "⏳ Initializing Terraform (Step 9)..."
    terraform init -input=false -upgrade &
    show_spinner "$!"
    wait "$!" || error_exit "Terraform init failed" "terraform"
    
    show_success "Terraform initialized."
    mark_step_complete 6 "Terraform Init"
    return 0
}

step_terraform_plan() {
    [[ -n "${DEPLOY_WORK_DIR:-}" ]] || error_exit "DEPLOY_WORK_DIR not set. Run step 4 first."
    cd "${DEPLOY_WORK_DIR}/terraform" || error_exit "Could not enter terraform directory"

    show_info "⏳ Generating Terraform plan (Step 10)..."
    terraform plan -input=false -out=tfplan >/tmp/terraform-plan.log 2>&1 &
    show_spinner "$!"
    if ! wait "$!"; then
        echo "" >&3
        echo -e "${RED}── Terraform plan output ──${NC}" >&3
        cat /tmp/terraform-plan.log >&3 2>/dev/null
        echo -e "${RED}── End of Terraform output ──${NC}" >&3
        error_exit "Terraform plan failed" "terraform"
    fi
    
    local summary
    summary=$(grep -E "Plan: [0-9]+ to add, [0-9]+ to change, [0-9]+ to destroy" /tmp/terraform-plan.log || true)
    if [[ -n "$summary" ]]; then
        show_info "Plan Summary: $summary"
    fi

    # Prompt for confirmation if in an interactive shell
    if [[ -c /dev/tty ]]; then
        echo "" >&3
        echo -e -n "$(get_timestamp) ${BLUE}⏳ Do you want to proceed with these changes? (yes/no): ${NC}" >&3
        read -r plan_confirm < /dev/tty 2>&3 || plan_confirm=""
        if [[ "$plan_confirm" != "yes" ]]; then
            show_error "Terraform apply cancelled by user."
            return 1
        fi
    fi
    
    show_success "Terraform plan generated."
    mark_step_complete 7 "Terraform Plan"
    return 0
}

step_terraform_apply() {
    [[ -n "${DEPLOY_WORK_DIR:-}" ]] || error_exit "DEPLOY_WORK_DIR not set. Run step 1 first."
    cd "${DEPLOY_WORK_DIR}/terraform" || error_exit "Could not enter terraform directory"
    [[ -f "tfplan" ]] || error_exit "tfplan not found. Re-run step 7 (Terraform Plan) first." "terraform"

    show_info "⏳ Applying Terraform plan (Step 8)..."
    terraform apply -input=false tfplan >/tmp/terraform-apply.log 2>&1 &
    show_spinner "$!"
    if ! wait "$!"; then
        echo "" >&3
        echo -e "${RED}── Terraform apply output ──${NC}" >&3
        cat /tmp/terraform-apply.log >&3 2>/dev/null
        echo -e "${RED}── End of Terraform output ──${NC}" >&3
        error_exit "Terraform apply failed" "terraform"
    fi
    
    show_info "Extracting outputs..."
    BACKEND_PAT=$(terraform output -raw goaldone_backend_pat) || error_exit "Failed to extract goaldone_backend_pat" "terraform"
    APP_CLIENT_ID=$(terraform output -raw goaldone_app_client_id) || error_exit "Failed to extract goaldone_app_client_id" "terraform"
    
    cache_credential "BACKEND_PAT" "$BACKEND_PAT"
    cache_credential "APP_CLIENT_ID" "$APP_CLIENT_ID"
    
    show_success "Terraform applied and outputs extracted."
    mark_step_complete 8 "Terraform Apply"
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

    mark_step_complete 9 "Output Summary"
    return 0
}

# ==============================================================================
# Usage and Help
# ==============================================================================
usage() {
    cat <<EOF
GoalDone Deployment Script

Usage:
  bash ./deploy.sh [OPTIONS]

Options:
  --help                Show this help message
  --reset-state         Delete .deploy-state and .deploy-state.creds (with confirmation)
  --skip-health-check   Skip Zitadel health check validation after startup

Prerequisites:
  - Run without sudo (must have docker group membership)
  - Ubuntu 24.04
  - Network access
  - Run install-deps.sh first (with sudo)
  - Zitadel domain and SMTP credentials ready

State Management:
  The script maintains .deploy-state file to track progress.
  Shares state with install-deps.sh.
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
            echo -n "Delete .deploy-state and .deploy-state.creds? (yes/no): " >&3
            read -r response < /dev/tty 2>&3 || response=""
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

# Check if user is root
# This script should be run without sudo (docker group membership required)
# Load state first to determine START_STEP before checking root
ensure_file_accessibility
load_state

# Calculate START_STEP based on LAST_COMPLETED_STEP
if [[ $LAST_COMPLETED_STEP -gt 0 ]] && [[ $LAST_COMPLETED_STEP -lt $TOTAL_STEPS ]]; then
    START_STEP=$((LAST_COMPLETED_STEP + 1))
else
    START_STEP=1
fi

if [[ $UID -eq 0 ]]; then
    show_error "This script must be run as a regular user, not root."
    show_error "Docker group setup was completed by install-deps.sh."
    show_error "Run as: bash ./deploy.sh (NOT: sudo bash ./deploy.sh)"
    exit 1
fi

check_prerequisites || exit 1

if ! docker ps >/dev/null 2>&1; then
    show_error "Cannot access Docker. Docker group membership may not be active."
    show_error "Log out and log back in, then try again."
    exit 1
fi

# ==============================================================================
# Main Execution Loop
# ==============================================================================
main() {
    # Initialize logging (append mode)
    exec >> "$LOG_FILE" 2>&1

    show_header "GoalDone Deployment"
    show_info "Starting GoalDone Deployment Script"

    if [[ -f "$STATE_FILE" ]]; then
        prompt_recovery_action
    fi

    show_info "Starting execution from Step $START_STEP to Step $TOTAL_STEPS..."
    echo "" >&3

    for step in $(seq "$START_STEP" "$TOTAL_STEPS"); do
        local step_start_time=$(date +%s)
        case $step in
            1)
                show_header "Step 1: Clone Repository"
                step_clone_repository
                ;;
            2)
                show_header "Step 2: Setup .env"
                step_setup_env
                ;;
            3)
                show_header "Step 3: Docker Compose"
                step_docker_compose_up
                ;;
            4)
                show_header "Step 4: Extract Token"
                step_extract_token
                ;;
            5)
                show_header "Step 5: Create .tfvars"
                step_create_tfvars
                ;;
            6)
                show_header "Step 6: Terraform Init"
                step_terraform_init
                ;;
            7)
                show_header "Step 7: Terraform Plan"
                step_terraform_plan
                ;;
            8)
                show_header "Step 8: Terraform Apply"
                step_terraform_apply
                ;;
            9)
                show_header "Step 9: Output Summary"
                step_output_summary
                ;;
        esac
        show_info "Step $step completed in $(get_duration $step_start_time)"
    done

    show_header "Deployment Complete"
    show_success "All steps completed successfully in $(get_duration $SCRIPT_START_TIME)!"
    show_info "Total execution time: $(get_duration $SCRIPT_START_TIME)"
}

# Start script
main

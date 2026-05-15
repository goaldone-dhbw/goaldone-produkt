# Installation Script Roadmap

**Project:** GoalDone Automated Ubuntu Installation Script  
**Timeline:** 2026-05-12 to 2026-05-19 (this week)  
**Status:** Planning  

---

## Phase Overview

```
┌─────────────────┐     ┌─────────────────────────┐     ┌─────────────────┐     ┌──────────────────┐     ┌────────────────┐
│ Phase 1         │────→│ Phase 2                 │────→│ Phase 3         │────→│ Phase 4          │────→│ Phase 5        │
│ Foundation      │     │ Preparation Steps       │     │ Terraform       │     │ Polish & Error   │     │ Testing        │
│ & State Mgmt    │     │ (Package, UFW, .env)    │     │ Integration     │     │ Handling         │     │ & Validation   │
│ (1 day)         │     │ (1-2 days)              │     │ (1 day)         │     │ (0.5 day)        │     │ (0.5-1 day)    │
└─────────────────┘     └─────────────────────────┘     └─────────────────┘     └──────────────────┘     └────────────────┘
```

**Target Completion:** Thursday 2026-05-15 (implementation + testing)  
**Buffer:** Friday 2026-05-16 (fixes/refinement before production use)

---

## Phase 1: Script Foundation & State Management (1 day)

**Goal:** Create baseline deploy.sh with core structure, state persistence, and checkpoint recovery.

**Output:** Functional script skeleton that validates prerequisites, manages state, and can resume from checkpoints.

### Tasks

#### 1.1 Create deploy.sh Skeleton
- [ ] Create `/infra/deploy.sh` with:
  - Shebang and bash strict mode (`set -euo pipefail`)
  - Usage/help text (`./deploy.sh --help`, `./deploy.sh --reset-state`)
  - Global variables (color codes, step names, directories)
  - Trap handlers for SIGINT/SIGTERM cleanup
  - Main function skeleton with step loop
- [ ] Make executable: `chmod +x infra/deploy.sh`
- [ ] Test basic execution: `./deploy.sh --help` shows usage

**Acceptance:** Script runs without errors, shows help, can be interrupted cleanly

---

#### 1.2 Implement State Management (`.deploy-state`)
- [ ] Create functions:
  - `load_state()` — Source existing .deploy-state or initialize empty state
  - `save_state()` — Write state file atomically (write to temp, then mv)
  - `reset_state()` — Delete .deploy-state (with confirmation)
  - `mark_step_complete(step_name)` — Update step status
  - `get_last_completed_step()` — Return last successful step number
- [ ] State file format (key=value):
  ```bash
  LAST_COMPLETED_STEP=0
  STEP_1_VERIFY_PREREQUISITES=pending
  STEP_2_INSTALL_PACKAGES=pending
  ...
  ZITADEL_DOMAIN=""
  GOALDONE_URL=""
  ```
- [ ] Test: Create state, reload, update, verify persistence

**Acceptance:** State file created/loaded correctly; steps can be marked complete

---

#### 1.3 Implement Checkpoint Recovery Logic
- [ ] Create function `prompt_recovery_action()`:
  - If `.deploy-state` exists:
    - Show: "Previous deployment found (last: step_X)."
    - Prompt: Resume / Retry / Start Fresh (with descriptions)
    - Return user choice
  - If no state: return "start_fresh"
- [ ] Implement main execution loop:
  ```bash
  case $recovery_action in
    resume)
      START_STEP=$((LAST_COMPLETED_STEP + 1))
      ;;
    retry)
      START_STEP=$LAST_COMPLETED_STEP
      ;;
    start_fresh)
      reset_state
      START_STEP=1
      ;;
  esac
  ```
- [ ] Execute steps $START_STEP through final step

**Acceptance:** Script detects state, prompts correctly, resumes from right step

---

#### 1.4 Implement Pre-Flight Validation (FR-1)
- [ ] Create function `check_prerequisites()`:
  - [ ] Verify Ubuntu 24.04: Check `/etc/os-release` PRETTY_NAME
    - Fail with clear message if not 24.04
  - [ ] Check required binaries:
    - `sudo`, `curl`, `git`, `apt-get`
    - For each missing: show install command
  - [ ] Check disk space (minimum 10GB):
    - Use `df -BG /` to get available space
    - Warn if 10-20GB; error if <10GB
  - [ ] Check RAM (minimum 4GB):
    - Use `free -h` to get available memory
    - Warn if 4-8GB; error if <4GB
  - [ ] Verify sudo access:
    - Test `sudo -n true` (no password prompt required)
    - Error message if fails
  - [ ] Return 0 if all pass, 1 if critical failure
- [ ] Create function `show_error()` with color:
  ```bash
  show_error() { echo -e "\033[0;31m✗ Error: $1\033[0m" >&2; }
  show_warning() { echo -e "\033[0;33m⚠ Warning: $1\033[0m"; }
  show_success() { echo -e "\033[0;32m✓ $1\033[0m"; }
  ```
- [ ] Run checks at script start; exit 1 if critical errors

**Acceptance:** Script validates OS, binaries, resources; displays actionable errors

---

#### 1.5 Create Basic Test Script
- [ ] Write `infra/test-deploy.sh` to:
  - Verify script syntax: `bash -n deploy.sh`
  - Test state persistence: create/load/reset state
  - Test error messages: check color codes, formatting
  - Run on test Ubuntu 24.04 VM (or Docker container)
- [ ] Document test results

**Acceptance:** Test script identifies issues; syntax valid

---

**Phase 1 Completion Criteria:**
- ✓ Script structure is clean and maintainable
- ✓ State management works (create, load, update, reset)
- ✓ Checkpoint recovery prompts and routes correctly
- ✓ Pre-flight validation catches OS/binary/resource issues
- ✓ Error messages are clear and actionable
- ✓ Script runs without errors on test system

---

## Phase 2: Preparation Steps Implementation (1-2 days)

**Goal:** Implement steps 2-6 (package installation through Docker Compose startup).

**Output:** Fully working script for initial infrastructure preparation.

### Tasks

#### 2.1 Implement Step 2: Install Packages
- [ ] Create function `step_install_packages()`:
  - Run `sudo apt-get update` (with progress feedback)
  - Install Docker, Git, Terraform: `sudo apt-get install -y docker.io git terraform`
  - Capture stderr if install fails
  - Verify each binary: `docker --version`, `git --version`, `terraform --version`
  - Show versions to user
  - Error handling: Show apt error, suggest checking mirrors/internet
- [ ] Add step to main loop; save state on completion

**Acceptance:** Packages installed correctly; versions displayed; state saved

---

#### 2.2 Implement Step 3: Configure UFW Firewall
- [ ] Create function `step_configure_ufw()`:
  - Check if UFW already enabled: `sudo ufw status | grep -q "Status: active"`
  - If not enabled: prompt user to confirm enabling
  - Set default policies:
    ```bash
    sudo ufw default deny incoming
    sudo ufw default allow outgoing
    ```
  - Add rules:
    ```bash
    sudo ufw allow 22/tcp
    sudo ufw allow 80/tcp
    sudo ufw allow 443/tcp
    ```
  - Enable UFW: `sudo ufw enable` (auto-confirm)
  - Verify: `sudo ufw status` → show output
- [ ] Warning: Display UFW rules to user; confirm they understand SSH will be available
- [ ] Error handling: Show UFW command errors; suggest manual enable if script fails

**Acceptance:** UFW configured with correct rules; user sees confirmation

---

#### 2.3 Implement Step 4: Clone Repository
- [ ] Create function `step_clone_repository()`:
  - Check if directory already exists: `[ -d goaldone-produkt ]`
  - If exists: ask user (reuse or re-clone)
  - Clone: `git clone https://github.com/goaldone-dhbw/goaldone-produkt.git`
  - Verify clone succeeded: `[ -d goaldone-produkt/infra ]`
  - Change to infra directory for remaining steps (export PWD or cd)
  - Error handling: Show git error (network, auth, etc.)

**Acceptance:** Repository cloned; infra directory accessible; state saved

---

#### 2.4 Implement Step 5: Setup .env from Template
- [ ] Create function `prompt_input()`:
  - Takes prompt text, validation regex (optional), hidden flag (for passwords)
  - Shows prompt with optional default value
  - If hidden (`read -s`), don't echo input
  - Validates against regex (if provided)
  - Re-prompt on invalid input with error message
  - Return validated input
- [ ] Create function `step_setup_env()`:
  - Check for `.env-example` in infra/
  - If `.env` already exists: ask user (reuse, new, backup+new)
  - Prompt for each value:
    1. ZITADEL_MASTERKEY (32 chars)
       - Prompt: "Enter Zitadel Master Key (32 chars, generate with: tr -dc A-Za-z0-9 </dev/urandom | head -c 32)"
       - Validation: `[[ ${#input} -eq 32 ]]`
    2. ZITADEL_ADMIN_PASSWORD (hidden, min 8 chars)
    3. POSTGRES_ADMIN_PASSWORD (hidden, min 8 chars)
    4. POSTGRES_ZITADEL_PASSWORD (hidden, min 8 chars)
    5. ZITADEL_DOMAIN (e.g., sso.goaldone.de)
       - Validation: `[[ $input =~ ^[a-z0-9.-]+\.[a-z]{2,}$ ]]`
    6. GOALDONE_URL (e.g., app.goaldone.de)
       - Validation: same as domain
  - Store values in associative array: `declare -A env_vars`
  - Read `.env-example` and substitute placeholders:
    ```bash
    while IFS= read -r line; do
      for key in "${!env_vars[@]}"; do
        line="${line//\{\{$key\}\}/${env_vars[$key]}}"
      done
      echo "$line"
    done < .env-example > .env
    ```
  - Set file permissions: `chmod 600 .env`
  - Verify file readable: `test -r .env`
  - Save values to state (NON-sensitive only):
    ```bash
    STATE_VARS=(ZITADEL_DOMAIN GOALDONE_URL)
    for var in "${STATE_VARS[@]}"; do
      echo "${var}=${env_vars[$var]}" >> .deploy-state
    done
    ```
- [ ] Error handling: Show template missing, write errors, validation failures

**Acceptance:** .env created with user inputs; sensitive values hidden; file permissions correct

---

#### 2.5 Implement Step 6: Start Docker Compose & Validate
- [ ] Create function `step_docker_compose_up()`:
  - Start services: `docker-compose up -d` (in infra/)
  - Show progress: "Starting Docker Compose services..."
  - Wait 5 seconds for services to boot
  - Validate services are running:
    ```bash
    docker-compose ps | tee /tmp/docker-ps.txt
    docker-compose ps | grep -E 'zitadel|postgres' | grep 'Up' || return 1
    ```
  - Health check (polling for 30 seconds):
    ```bash
    for i in {1..30}; do
      if curl -s http://localhost:8080/health >/dev/null 2>&1; then
        show_success "Zitadel health check passed"
        return 0
      fi
      echo "⏳ Waiting for Zitadel... ($i/30)"
      sleep 1
    done
    ```
  - If health check fails: show troubleshooting (logs, disk, ports, .env values)
  - Return 0 on success, 1 on failure
- [ ] Error handling:
  ```
  Error: Zitadel did not respond after 30s.
  Troubleshooting:
  1. Check Docker logs: docker-compose logs zitadel | tail -20
  2. Check disk space: df -h
  3. Check port 8080: netstat -tulpn | grep 8080 || true
  4. Verify .env values (ZITADEL_MASTERKEY especially)
  ```

**Acceptance:** Docker Compose services start; health check passes; state saved

---

**Phase 2 Completion Criteria:**
- ✓ All steps 2-6 implement without errors
- ✓ User inputs validated and stored securely
- ✓ Docker Compose services running and healthy
- ✓ .env file created with proper permissions
- ✓ Checkpoint recovery works for all steps
- ✓ Error messages guide troubleshooting

---

### Phase 02.1: Implement the actual terraform files and workflow (INSERTED)

**Goal:** [Urgent work - to be planned]
**Requirements**: TBD
**Depends on:** Phase 2
**Plans:** 0 plans

Plans:
- [ ] TBD (run /gsd:plan-phase 02.1 to break down)

## Phase 3: Terraform Integration (1 day)

**Goal:** Implement steps 7-12 (Terraform token extraction through output summary).

**Output:** Fully functional infrastructure provisioning via Terraform.

**Plans:** 3 plans
- [ ] 03-01-PLAN.md — Extract Token & Create .tfvars
- [ ] 03-02-PLAN.md — Terraform Execution Workflow
- [ ] 03-03-PLAN.md — Final Summary & Integration

---

## Phase 4: Error Handling & Polish (0.5 day) [Planned: 3 plans]

**Goal:** Enhance error messages, add progress tracking, and improve UX.

**Output:** Production-ready script with comprehensive error handling.

**Plans:** 3 plans
- [ ] 04-01-logging.md — Logging & UI Foundation
- [ ] 04-02-spinners-hints.md — Progress & Troubleshooting
- [ ] 04-03-validation-robustness.md — Robust Configuration

### Tasks

#### 4.1 Enhance Error Messages & Logging
- [ ] Create `deploy.log` in infra/ directory
- [ ] Log all operations:
  - Timestamps for each step
  - Commands executed
  - Errors and warnings
  - Final summary
- [ ] Ensure error messages:
  - Describe what failed (not just "Error")
  - Show underlying error (command output)
  - Suggest resolution steps
  - Provide debugging commands
- [ ] Test error paths:
  - Missing binaries → show install command
  - Terraform validation errors → show terraform output
  - Docker health check timeout → show logs, ports, disk space commands

**Acceptance:** Error messages are actionable; log file complete and useful

---

#### 4.2 Add Progress Indicators & Timestamps
- [ ] Add timestamps to all user-facing messages:
  ```bash
  show_step() { echo "[$(date '+%H:%M:%S')] ⏳ $1"; }
  show_success() { echo "[$(date '+%H:%M:%S')] ✓ $1"; }
  ```
- [ ] Add elapsed time tracking:
  ```bash
  START_TIME=$(date +%s)
  # ... later ...
  ELAPSED=$(($(date +%s) - START_TIME))
  echo "Completed in ${ELAPSED}s"
  ```
- [ ] Show progress during long operations (e.g., Docker Compose wait)

**Acceptance:** All messages timestamped; progress visible; elapsed times shown

---

#### 4.3 Add Input Validation & Escaping
- [ ] Review all user inputs for special characters
- [ ] Escape inputs before using in files/commands:
  ```bash
  # For domain names: ensure no spaces, quotes
  domain="${domain//[^a-zA-Z0-9.-]/}"
  # For passwords: escape for both bash and terraform
  password_escaped="${password//\\/\\\\}" # etc.
  ```
- [ ] Validate email format: `[[ $email =~ ^[^@]+@[^@]+$ ]]`
- [ ] Validate domain format: `[[ $domain =~ ^[a-z0-9.-]+\.[a-z]{2,}$ ]]`

**Acceptance:** Special characters handled safely; no injection vulnerabilities

---

#### 4.4 Improve UX (Colors, Formatting)
- [ ] Use colors consistently:
  - ✓ Success (green): `\033[0;32m`
  - ✗ Error (red): `\033[0;31m`
  - ⚠ Warning (yellow): `\033[0;33m`
  - ⏳ Info (blue): `\033[0;34m`
- [ ] Use Unicode symbols: ✓, ✗, ⚠, ⏳, ──
- [ ] Format long outputs clearly:
  - Terraform plan: show summary first, offer full review
  - Docker logs: show last 10 lines, offer full log
- [ ] Center headers/titles for clarity

**Acceptance:** Output is visually clear; colors and symbols used consistently

---

**Phase 4 Completion Criteria:**
- ✓ All error messages are actionable with troubleshooting steps
- ✓ Comprehensive logging in deploy.log
- ✓ Progress indicators and timestamps throughout
- ✓ Input validation prevents injection
- ✓ UX is polished with colors, symbols, formatting

---

## Phase 5: Testing & Validation (0.5-1 day)

**Goal:** Test script on fresh Ubuntu 24.04 instances; validate all paths and error recovery.

**Output:** Production-ready script validated through testing.

### Tasks

#### 5.1 Smoke Test: End-to-End Execution
- [ ] Test on fresh Ubuntu 24.04 VM (cloud provider or local vagrant)
- [ ] Run: `sudo ./deploy.sh`
- [ ] Provide all required inputs (masterkey, passwords, domains, SMTP)
- [ ] Verify:
  - All steps complete without error
  - State file created and updated after each step
  - Docker Compose services running
  - Terraform resources created in Zitadel
  - Outputs captured (PAT, ClientID)
  - Output summary displayed correctly
  - deploy-outputs.txt created with all values
- [ ] Time the execution; confirm <15 minutes
- [ ] Check deploy.log; verify all operations logged

**Acceptance:** Script completes successfully; all outputs correct

---

#### 5.2 Test Checkpoint Recovery
- [ ] Simulate failure at step 6 (Docker Compose):
  - Stop script mid-way through Docker Compose validation (Ctrl+C)
  - Verify state file contains completed steps
  - Run script again; select "Resume"
  - Verify script starts at step 7 (not from step 1)
- [ ] Simulate failure at step 11 (Terraform apply):
  - Modify .tfvars to have invalid Zitadel domain
  - Run script; terraform plan/apply will fail
  - Fix .tfvars; run script again; select "Retry step 11"
  - Verify script re-runs terraform apply with fixed values
- [ ] Test "Start Fresh":
  - Run script; select "Start Fresh"
  - Verify state file deleted; script starts from step 1

**Acceptance:** Checkpoint recovery works for all scenarios

---

#### 5.3 Test Error Paths
- [ ] Missing binaries:
  - Simulate missing `curl` on system (rename temporarily)
  - Run script; verify error message and install command shown
  - Restore binary; script can then proceed
- [ ] Docker Compose startup failure:
  - Modify .env to have invalid masterkey (too short)
  - Run script; Docker Compose will fail health check
  - Verify error message shows logs, disk, port checks
- [ ] Terraform validation error:
  - Use invalid domain in .tfvars (e.g., "localhost")
  - Run terraform plan; verify error shown with suggestion
  - Fix and retry

**Acceptance:** Error messages are clear; debugging suggestions work

---

#### 5.4 Test Input Validation
- [ ] Invalid Zitadel masterkey:
  - Provide 30-char key (not 32)
  - Verify re-prompt with error message
  - Accept only 32-char key
- [ ] Invalid domain:
  - Provide "localhost" or "domain without dot"
  - Verify re-prompt with validation error
- [ ] Invalid email:
  - Provide "notanemail" for SMTP user
  - Verify re-prompt with format error

**Acceptance:** Validation catches all invalid inputs; re-prompts correctly

---

#### 5.5 Test Idempotency
- [ ] Run script twice on same system:
  - First run: complete deployment
  - Second run: select "Resume" (should start from step 7+ since earlier steps already done)
  - Verify no errors; terraform plan shows no changes
- [ ] Verify Docker Compose doesn't try to re-create services
- [ ] Verify apt install doesn't error on already-installed packages

**Acceptance:** Second run doesn't cause errors; already-completed steps skipped

---

#### 5.6 Documentation Review
- [ ] Create `/infra/deploy.sh.md` user guide:
  - System requirements (Ubuntu 24.04, sudo access, etc.)
  - How to run: `sudo ./deploy.sh`
  - What to expect at each step
  - Required inputs (masterkey generation, credential sources)
  - Common errors and solutions
  - Checkpoint recovery instructions
  - Troubleshooting commands
- [ ] Review for clarity; test instructions on fresh system

**Acceptance:** User guide is complete, clear, and tested

---

**Phase 5 Completion Criteria:**
- ✓ All steps 7-12 implement correctly
- ✓ Terraform token extracted from machinekey
- ✓ .tfvars created with proper format and permissions
- ✓ Terraform init/plan/apply flow works
- ✓ Outputs captured and displayed
- ✓ Summary output is user-friendly and actionable

---

## Success Metrics

**By end of Phase 5 (Friday 2026-05-16):**

- [ ] Script completes end-to-end on fresh Ubuntu 24.04
- [ ] All Zitadel infrastructure provisioned via Terraform
- [ ] Checkpoint recovery allows resuming from any failed step
- [ ] Error messages guide users toward resolution
- [ ] Script completes in <15 minutes
- [ ] Outputs captured and displayed clearly
- [ ] User guide provides clear instructions and troubleshooting
- [ ] Validated on multiple fresh instances

---

## Release Criteria

**Ready for production use when:**
1. ✓ All phases complete
2. ✓ Tested on ≥2 instances
3. ✓ Zero critical errors in testing
4. ✓ User guide reviewed and clear
5. ✓ Deploy script pushed to repository (with comments explaining complex logic)

**Release artifacts:**
- `infra/deploy.sh` — main script
- `infra/deploy.sh.md` — user guide
- Update README if needed with deployment instructions

---

## Schedule

| Phase | Duration | Start | End | Status |
|-------|----------|-------|-----|--------|
| 1. Foundation | 1 day | Mon 5/12 | Mon 5/12 | Pending |
| 2. Preparation | 1-2 days | Tue 5/13 | Wed 5/14 | Pending |
| 3. Terraform | 1 day | Thu 5/15 | Thu 5/15 | Pending |
| 4. Polish | 0.5 day | Fri 5/16 | Fri 5/16 | Pending |
| 5. Testing | 0.5-1 day | Fri 5/16 | Fri 5/16 | Pending |
| **Complete** | **4-5 days** | **Mon 5/12** | **Fri 5/16** | **Pending** |

**Buffer:** Sat-Sun (if needed for fixes)

---

## Dependencies & Risks

### External Dependencies
- Terraform Zitadel provider (registry.terraform.io/zitadel/zitadel)
- Docker Hub (for image pulls)
- GitHub (for repo clone)
- SMTP provider (user-supplied)

### Risks & Mitigations
| Risk | Impact | Mitigation |
|------|--------|-----------|
| Zitadel provider outdated | Resources may not exist | Verify provider docs during Phase 3 |
| Docker startup slow | Script timeout on health check | Increase timeout to 60s if needed |
| Network issues during clone | Clone fails | Retry logic + clear error message |
| Secrets in log files | Security exposure | Ensure .env/.tfvars not logged; use `read -s` for passwords |
| State file corruption | Can't resume | Atomic writes; backup on state change |
| Terraform lock conflicts | Plan/apply blocks | Suggest `terraform force-unlock` in error message |

---

## Open Questions

1. **Terraform working directory:** Is terraform code in `/infra/terraform/` or `/infra/`?
   - Assume: `/infra/terraform/` (adjust if different)

2. **Zitadel organization ID:** Is there a default org that should be used, or should script prompt/detect?
   - Assume: `org_id = "default"` (confirm with team)

3. **Multiple SMTP providers:** Should script support multiple SMTP providers, or is one sufficient?
   - Assume: One SMTP provider for v1 (can extend later)

4. **SSL/TLS certificates:** Does Zitadel domain need valid cert, or is self-signed OK during initial setup?
   - Assume: Script can use self-signed; document if issues arise

5. **Backend/Frontend next steps:** Should script help configure backend/frontend, or just output PAT/ClientID?
   - Assume: Output only; users configure next steps manually (document in deploy.sh.md)

### Phase 6: Integrate backend and frontend deployment with Docker images, .env and env.js configuration auto-populated from Zitadel Terraform outputs

**Goal:** Automate GoalDone backend and frontend Docker deployment as a new deploy.sh step, with Docker Compose app stack, environment injection from Terraform outputs, Traefik routing, and health checking.
**Requirements**: D-01, D-02, D-03, D-04, D-05, D-06, D-07, D-08
**Depends on:** Phase 5
**Plans:** 2/2 plans complete

Plans:
- [x] 06-01-PLAN.md — Docker Compose app stack + CORS env var
- [x] 06-02-PLAN.md — Deploy.sh step 9 (Deploy App) + state management

---

**Next Step:** Run `/gsd:plan-phase 1` to begin implementation of Phase 1.

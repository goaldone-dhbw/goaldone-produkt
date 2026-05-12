# Phase 1: Script Foundation & State Management - Context

**Gathered:** 2026-05-12  
**Status:** Ready for planning

---

<domain>
## Phase Boundary

Create the baseline `deploy.sh` script with core structure, state persistence, and checkpoint recovery capability. The script will:
1. Be executable directly by users on fresh Ubuntu 24.04 VPS instances
2. Validate prerequisites (OS, binaries, disk, RAM, sudo)
3. Persist deployment state to allow checkpoint recovery (resume/retry/start-fresh)
4. Provide a maintainable skeleton for remaining phases (Phase 2-5) to add step implementations

This phase establishes the **foundation and plumbing** — the script structure, state management, and checkpoint recovery UI. Actual step implementations (package install, Docker Compose, Terraform) follow in later phases.

</domain>

---

<decisions>
## Implementation Decisions

### Script Location & Invocation
- **D-01:** Script location: `/infra/deploy.sh` (placed at infra root, not infra-setup subdirectory)
- **D-02:** Invocation model: User runs `sudo ./infra/deploy.sh` (sudo required at invocation time, not by script internally)
- **D-03:** Script performs check `[[ $UID -eq 0 ]]` at startup; fails if not root with clear message

### State Persistence & Credential Caching
- **D-04:** Primary state file: `.deploy-state` in script directory (alongside deploy.sh) — tracks step completion status
- **D-05:** Credential cache file: `.deploy-state.creds` in script directory — stores plaintext credentials for session recovery
  - **Security trade-off:** Plain text credentials for convenience during multi-step recovery. File permissions: chmod 600 (user read-only)
  - **Rationale:** User consciously accepts this; credentials live in project directory anyway during deployment; recovery UX improved
  - **Downstream impact:** All phase implementations must respect this design and NOT store credentials elsewhere
- **D-06:** Session credential loading:
  - On script start, check for `.deploy-state.creds`
  - If exists and valid, source it into bash variables for current session
  - If recovery action is "start fresh", delete both `.deploy-state` and `.deploy-state.creds`
  - If recovery action is "resume" or "retry", reuse cached values (don't re-prompt)

### Checkpoint Recovery Timing & Flow
- **D-07:** Checkpoint prompt timing: **After pre-flight validation succeeds** (not at script start)
  - Flow: Start → Validate prerequisites → Detect state → Prompt recovery action → Execute remaining steps
  - Rationale: User immediately knows if system is compatible before deciding recovery strategy; cleaner UX
- **D-08:** Checkpoint recovery options (user chooses):
  1. **Resume**: Continue from next step after LAST_COMPLETED_STEP
  2. **Retry**: Re-execute LAST_COMPLETED_STEP (useful after fixing external issues)
  3. **Start Fresh**: Delete state files and begin from step 1
- **D-09:** State file format: Bash key-value pairs (sourced, not parsed)
  - Example:
    ```bash
    LAST_COMPLETED_STEP=4
    STEP_1_VERIFY_PREREQUISITES=completed
    STEP_2_INSTALL_PACKAGES=pending
    STEP_3_CONFIGURE_UFW=pending
    # Non-sensitive config values only (no passwords)
    ZITADEL_DOMAIN=sso.goaldone.de
    GOALDONE_URL=app.goaldone.de
    ```

### Output Formatting & User Feedback
- **D-10:** Colored output using ANSI escape codes (not `tput`):
  - ✓ Success (green): `\033[0;32m` + message + `\033[0m`
  - ✗ Error (red): `\033[0;31m` + message + `\033[0m`
  - ⚠ Warning (yellow): `\033[0;33m` + message + `\033[0m`
  - ⏳ Info (blue): `\033[0;34m` + message + `\033[0m`
- **D-11:** Helper functions for consistent output:
  - `show_success() { echo -e "\033[0;32m✓ $1\033[0m"; }`
  - `show_error() { echo -e "\033[0;31m✗ Error: $1\033[0m" >&2; }`
  - `show_warning() { echo -e "\033[0;33m⚠ Warning: $1\033[0m"; }`
  - `show_info() { echo -e "\033[0;34m⏳ $1\033[0m"; }`

### Pre-Flight Validation (FR-1 Coverage)
- **D-12:** Ubuntu version check: Parse `/etc/os-release` for PRETTY_NAME; reject if not "Ubuntu 24.04"
- **D-13:** Required binary check: `sudo`, `curl`, `git`, `apt-get` — fail fast with install command for each missing binary
- **D-14:** Disk space validation:
  - Use `df -BG /` to check available root filesystem space
  - Error if <10GB; warning if 10-20GB; pass if ≥20GB
- **D-15:** RAM validation:
  - Use `free -h` to check available memory
  - Error if <4GB; warning if 4-8GB; pass if ≥8GB
- **D-16:** Sudo access test: Run `sudo -n true` (no password prompt required); error if fails with clear message

### Error Handling & Traps
- **D-17:** Trap handlers for SIGINT (Ctrl+C) and SIGTERM:
  - Offer to save state before exiting
  - Clean up any temporary files
  - Exit gracefully with exit code 130 (SIGINT) or 143 (SIGTERM)
- **D-18:** Error propagation: Use `set -euo pipefail` at script top
- **D-19:** Error messages must:
  - Describe what failed (not just exit code)
  - Show underlying error/command output when relevant
  - Suggest resolution or debugging commands
  - Example: "Error: Zitadel did not respond after 30s. Check Docker logs: `docker-compose logs zitadel | tail -20`"

### Test Script (Task 1.5)
- **D-20:** Test script location: `infra/test-deploy.sh` (companion to deploy.sh)
- **D-21:** Test coverage:
  - Bash syntax validation: `bash -n deploy.sh`
  - State file creation/load/reset: Create state, verify keys, reset, confirm deletion
  - Color codes: Verify output contains correct ANSI codes
  - Error messages: Sample each error path, verify message format
  - Run on test Ubuntu 24.04 (Docker container or VM) if possible

### Claude's Discretion
- **D-22:** Error message verbosity/tone: Claude has flexibility on exact phrasing of error messages, as long as they follow the "describe problem + suggest resolution" pattern
- **D-23:** Helper function naming: Claude may adjust function names (e.g., `show_error` vs `log_error`) for consistency with bash conventions, as long as they're used uniformly throughout

</decisions>

---

<canonical_refs>
## Canonical References

Downstream agents (researcher, planner, executor) MUST consult these before implementing:

### Requirements & Specification
- `.planning/REQUIREMENTS.md` §FR-1 — Pre-Flight Validation specification
- `.planning/REQUIREMENTS.md` §FR-2 — State Management & Checkpoint Recovery specification
- `.planning/ROADMAP.md` §Phase 1 — Complete task breakdown (1.1-1.5)

### Architecture & Design
- `.planning/PROJECT.md` §Architecture § Script Structure — Script skeleton and layer breakdown
- `.planning/PROJECT.md` §Architecture § State File Format — Exact format of .deploy-state

### Existing Code & Patterns
- `/infra/infra-setup/.env-example` — Template for environment variables that script will create
- `/infra/infra-setup/docker-compose.yml` — Docker Compose structure (future reference for Phase 2)
- `/infra/infra-setup/README.md` — Infrastructure setup docs (context for script flow)

### Standards & Best Practices
- No external specs; requirements fully captured in REQUIREMENTS.md and ROADMAP.md

</canonical_refs>

---

<code_context>
## Existing Code Insights

### Reusable Assets
- `/infra/infra-setup/.env-example` — Template file that deploy.sh will read and substitute placeholders into .env
- `/infra/infra-setup/docker-compose.yml` — Reference for Step 6 (Phase 2): script will run `docker-compose up -d` in this directory
- No existing bash scripts in `/infra/` to reference; script is built from scratch

### Established Patterns
- Environment variable substitution pattern is already shown in `.env-example` (template placeholders)
- Docker Compose service names are known (zitadel, postgres, traefik)
- Directory structure: `/infra/infra-setup/` contains compose + .env files; `/infra/` root is where deploy.sh goes

### Integration Points
- Phase 1 script only validates prerequisites and manages state; no integration with Phase 2+ yet
- Future phases will add step functions (`step_install_packages()`, `step_configure_ufw()`, etc.) that Phase 1's main loop will call
- Phase 1 should NOT implement these step functions; only the loop structure and checkpoint logic

</code_context>

---

<specifics>
## Specific Ideas & References

### Password Generation Helper
User mentioned in REQUIREMENTS that Zitadel master key can be generated with:
```bash
tr -dc A-Za-z0-9 </dev/urandom | head -c 32
```
Script should display this command when prompting for master key, so user doesn't need to memorize it.

### Help & Usage Text
Script should support:
- `./deploy.sh --help` — Show usage and all flags
- `./deploy.sh --reset-state` — Delete state files and exit (with confirmation)
- These are referenced in ROADMAP task 1.1, should be implemented in skeleton

### Timestamps in Output
All user-facing messages should include timestamps (mentioned in ROADMAP task 4.2, but good to establish pattern early):
```bash
show_step() { echo "[$(date '+%H:%M:%S')] ⏳ $1"; }
```

</specifics>

---

<deferred>
## Deferred Ideas

None — discussion stayed within Phase 1 scope.

</deferred>

---

*Phase: 01-script-foundation-state-management-1-day*  
*Context gathered: 2026-05-12*

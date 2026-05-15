---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
last_updated: "2026-05-15T20:22:50.628Z"
progress:
  total_phases: 7
  completed_phases: 4
  total_plans: 11
  completed_plans: 11
---

# Project State & Context Memory

**Project:** GoalDone Automated Ubuntu Installation Script  
**Status:** Executing Phase 06
**Last Updated:** 2026-05-12

---

## Project Overview

Creating a fully automated bash script (`deploy.sh`) that provisions the complete GoalDone infrastructure stack on fresh Ubuntu 24.04 VPS instances.

**Core Value:** Enable rapid, repeatable, error-resilient infrastructure deployment without manual step-by-step configuration.

---

## Key Decisions Made

### Technology Stack

- **Language:** Bash (pure script, no external dependencies beyond standard Ubuntu tools)
- **State Persistence:** `.deploy-state` file (key-value format, sourced as bash variables)
- **Checkpoint Recovery:** User prompted to resume/retry/restart on script re-entry
- **Error Handling:** Detailed, actionable error messages with troubleshooting steps
- **Logging:** Full execution log in `deploy.log` for debugging

### User Interaction Model

- **Target Users:** Mixed technical/non-technical
- **Credential Input:** Interactive prompts (hidden for passwords)
- **Error UX:** Clear messages with resolution suggestions + debugging commands
- **Progress Feedback:** Timestamps, progress indicators (✓, ✗, ⚠, ⏳)

### Scope Boundaries

- **Single VPS only** (not multi-cloud)
- **Ubuntu 24.04 only** (OS validation enforced)
- **No external secret manager** v1 (credentials via prompts)
- **One SMTP provider** (extensible later)
- **No rollback automation** (manual checkpoint recovery)

---

## Technical Requirements Captured

### Prerequisites Validation (Step 1)

- ✓ Ubuntu 24.04 version check
- ✓ Required binaries: sudo, curl, git, apt-get
- ✓ Disk space minimum: 10GB (warn 10-20GB, error <10GB)
- ✓ RAM minimum: 4GB (warn 4-8GB, error <4GB)
- ✓ Sudo access verification

### Preparation (Steps 2-6)

- ✓ Package installation (Docker, Git, Terraform)
- ✓ UFW firewall rules (allow 22, 80, 443; deny inbound others)
- ✓ Repository clone from GitHub
- ✓ .env creation from template with user inputs
- ✓ Docker Compose startup with health validation

### Zitadel Configuration (Step 5 inputs)

Required user inputs:

1. ZITADEL_MASTERKEY (32 chars, validated)
2. ZITADEL_ADMIN_PASSWORD (hidden input, min 8 chars)
3. POSTGRES_ADMIN_PASSWORD (hidden input, min 8 chars)
4. POSTGRES_ZITADEL_PASSWORD (hidden input, min 8 chars)
5. ZITADEL_DOMAIN (domain format, e.g., sso.goaldone.de)
6. GOALDONE_URL (domain format, e.g., app.goaldone.de)

### Terraform Integration (Steps 7-12)

- ✓ Extract Terraform token from Zitadel machinekey file
- ✓ Create .tfvars with SMTP config + user inputs
- ✓ Terraform init/plan/apply workflow
- ✓ Capture outputs: BACKEND_PAT, OIDC_CLIENT_ID
- ✓ Display summary with next steps

---

## Checkpoint Recovery Strategy

**State File (.deploy-state):**

- Key-value pairs (sourced as bash variables)
- Tracks: LAST_COMPLETED_STEP, each step's status (pending/completed)
- **Does NOT store:** passwords, tokens, sensitive data
- **Stores:** domain names, non-sensitive config

**User Options on Script Restart:**

1. **Resume** — Continue from last completed step
2. **Retry** — Re-execute the failed step (useful after fixing external issues)
3. **Start Fresh** — Delete state, begin from step 1

**Benefits:**

- Users can recover from transient failures (network, Docker startup delays)
- No need to re-enter credentials after a failed step
- Clear choice between continuing and re-running specific steps

---

## Error Handling Philosophy

**Every error message must:**

1. **Describe what failed** (not just exit code)
2. **Show underlying error** (command output, stderr)
3. **Suggest resolution** (e.g., "Install curl with: sudo apt-get install curl")
4. **Provide debugging commands** (e.g., "Check Zitadel logs: docker-compose logs zitadel")

**Examples:**

```
Error: Zitadel did not respond after 30s.
Troubleshooting:

1. Check Docker logs: docker-compose logs zitadel | tail -20
2. Check disk space: df -h
3. Verify .env (especially ZITADEL_MASTERKEY must be exactly 32 chars)
4. Check if port 8080 is already in use: netstat -tulpn | grep 8080

```

---

## Phase Breakdown

| Phase | Focus | Duration | Status | Target Date |
|-------|-------|----------|--------|------------|
| 1 | Script foundation, state management, pre-flight validation | 1 day | ✅ Done | Mon 5/12 |
| 2 | Package install, UFW, .env setup, Docker Compose | 1-2 days | ✅ Done | Tue-Wed 5/13-14 |
| 02.1 | Implement the actual terraform files and workflow | - | ✅ Done | Tue 5/12 |
| 3 | Terraform token, .tfvars, init/plan/apply, outputs | 1 day | ⏳ In Progress | Thu 5/15 |
| 4 | Error handling, logging, UX polish, colors | 0.5 day | Pending | Fri 5/16 |
| 5 | Testing on fresh VMs, checkpoint recovery, documentation | 0.5-1 day | Pending | Fri-Sat 5/16-17 |

**Total:** 4-5 days, target completion **Friday 2026-05-16**

---

## Known Unknowns

1. **Terraform directory structure:** Assume terraform code in `/infra/terraform/` (verify if different)
2. **Zitadel org_id:** Assume `org_id = "default"` (confirm with team)
3. **Terraform provider version:** Verify Zitadel provider resources exist in version used
4. **DNS/SSL for Zitadel domain:** Script will need domain to be DNS-resolvable before terraform apply

---

## Testing Strategy

### Smoke Testing

- End-to-end execution on ≥2 fresh Ubuntu 24.04 VMs
- Verify all infrastructure created in Zitadel
- Confirm <15 minute execution time
- Check deploy-outputs.txt contains PAT and ClientID

### Checkpoint Recovery Testing

- Simulate failures at different steps
- Test resume/retry/start-fresh options
- Verify state file accuracy

### Error Path Testing

- Missing binaries → error message shows install command
- Invalid credentials → re-prompt with error
- Docker startup failures → show logs, disk, port checks
- Terraform errors → show terraform output + suggestions

### Idempotency Testing

- Run script twice on same system
- Verify second run detects completed steps
- Confirm no duplicate resource creation

---

## Deliverables

**Final deliverables (Phase 5 completion):**

1. `/infra/deploy.sh` — Main installation script (~400-500 lines)
2. `/infra/deploy.sh.md` — User guide with instructions and troubleshooting
3. `/infra/.deploy-state` — Generated during first run (git-ignored)
4. `/infra/.env` — Generated by script from template (git-ignored)
5. `/infra/terraform.tfvars` — Generated by script (git-ignored)
6. `/infra/deploy-outputs.txt` — Generated with critical values (git-ignored)
7. `/infra/deploy.log` — Execution log (git-ignored)

---

## Success Criteria Summary

**Script is production-ready when:**

- ✓ Executes end-to-end without manual intervention (except credential input)
- ✓ All Zitadel infrastructure provisioned correctly
- ✓ Checkpoint recovery works for all steps
- ✓ Error messages are actionable with troubleshooting steps
- ✓ Completes in <15 minutes on typical VPS
- ✓ Outputs captured (PAT, ClientID) and displayed
- ✓ Tested on ≥2 fresh Ubuntu 24.04 instances
- ✓ User guide is clear and complete
- ✓ Zero critical errors in testing
- ✓ Code is well-commented for complex logic

---

## Next Steps

**Immediate (Phase 1):**

1. Run `/gsd:plan-phase 1` to begin script foundation development
2. Create deploy.sh skeleton with structure
3. Implement state management
4. Implement checkpoint recovery logic
5. Implement pre-flight validation

**Then proceed through Phase 2-5 sequentially**

---

## Accumulated Context

### Roadmap Evolution

- Phase 02.1 inserted after Phase 2: Implement the actual terraform files and workflow (INSERTED)
- Phase 6 added: Integrate backend and frontend deployment with Docker images, .env and env.js configuration auto-populated from Zitadel Terraform outputs

---

## Notes for Future Context

- **User timezone:** Not yet captured; remember to adjust any scheduled steps
- **Repository URL:** Confirmed as https://github.com/goaldone-dhbw/goaldone-produkt.git
- **Terraform provider:** Using Zitadel Terraform provider (https://registry.terraform.io/providers/zitadel/zitadel)
- **Docker Compose location:** Expected at `/infra/docker-compose.yml`
- **Terraform location:** Expected at `/infra/terraform/` or `/infra/` (TBD)
- **UFW ports:** Allow SSH (22), HTTP (80), HTTPS (443); deny all inbound others

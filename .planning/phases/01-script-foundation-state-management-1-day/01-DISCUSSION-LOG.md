# Phase 1: Script Foundation & State Management - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.  
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-12  
**Phase:** 01-script-foundation-state-management-1-day  
**Areas discussed:** 4 (Script Location, Privilege Model, Input Capture, State File Location, Credential Security, Output Formatting, Checkpoint Timing)

---

## Script Location

**Question:** Where should deploy.sh be placed and invoked from?

| Option | Description | Selected |
|--------|-------------|----------|
| /infra/deploy.sh | Place in /infra root; user runs 'sudo ./infra/deploy.sh' from project root. Simpler path, matches typical monorepo patterns. | ✓ |
| /infra/infra-setup/deploy.sh | Place in same dir as docker-compose.yml and .env-example. Keeps all infra ops in one subdirectory. | |
| /deploy.sh (project root) | Place at top level; user runs 'sudo ./deploy.sh' from anywhere. Most accessible, but mixes tool with code. | |

**User's choice:** /infra/deploy.sh (Recommended)  
**Rationale:** Cleaner structure; infra tools in /infra, not at project root; matches expected layout for a monorepo.

---

## Privilege Model

**Question:** How should the script handle sudo requirement?

| Option | Description | Selected |
|--------|-------------|----------|
| Require user to invoke with 'sudo' | User runs: 'sudo ./deploy.sh'. Script checks 'am I root?' at start. Simple, transparent, user knows they're running privileged code. | ✓ |
| Script internally requests sudo | User runs: './deploy.sh'. Script prompts for password when needed. Better UX but less transparent; can confuse if password prompt comes mid-flow. | |

**User's choice:** Require user to invoke with 'sudo' (Recommended)  
**Rationale:** More transparent; user consciously escalates privileges; clearer error handling if not run as sudo.

---

## Input Capture Model (Checkpoint Recovery Credentials)

**Question:** How should the script handle credential input when checkpoint recovery happens?

| Option | Description | Selected |
|--------|-------------|----------|
| Re-prompt only for missing/changed values | If resuming step 6 after step 5 succeeded, don't re-prompt for ZITADEL_MASTERKEY. Load from temp file within session. Faster, less annoying. | ✓ |
| Always re-prompt from scratch | Every restart means re-entering all credentials. More secure (no temp memory), but slower and frustrating on repeated retries. | |
| Offer user choice | Prompt: 'Reuse Zitadel masterkey from previous run?' User decides per credential. | |

**User's choice:** Re-prompt only for missing/changed values (Recommended)  
**Rationale:** Better UX during recovery flows; users don't want to re-enter 6+ credentials every retry; session-scoped security is acceptable for this use case.

---

## State File Location

**Question:** Where should .deploy-state be saved?

| Option | Description | Selected |
|--------|-------------|----------|
| Script directory | Save where deploy.sh is located. User easily finds it; matches typical tool behavior (state near code). | ✓ |
| /tmp/.deploy-state | Use /tmp for temp state. Cleaner (doesn't clutter repo), but lost on reboot. | |
| User-specified directory | Advanced: let user control state location via --state-dir flag. More flexible but adds complexity. | |

**User's choice:** Script directory (Recommended)  
**Rationale:** Easier debugging; state is visible alongside script; doesn't get lost on reboot; matches Unix tool conventions.

---

## Credential Security & Storage Method

**Question:** How should credential values be preserved across restarts (for 'reuse' during recovery)?

| Option | Description | Selected |
|--------|-------------|----------|
| In-memory during current session only | Load credentials at start, store in bash variables for current session, then discard. Each restart requires re-entry. No temp files = no security risk from temp storage. | |
| In .deploy-state.secure (encrypted) | Cache sensitive values in an encrypted temp file. Requires encryption setup (openssl). More convenience, but added complexity. Not recommended for MVP. | |
| In temporary session file (.deploy-session) | Write credentials to /tmp/.deploy-session.$$.[PPID], readable only by owner (chmod 600). Lost on terminal close or reboot. Good UX/security balance. | |
| **In plaintext .deploy-state.creds** | **Store plaintext in script directory, chmod 600. User consciously accepts trade-off for convenience.** | ✓ |

**User's choice:** Store plaintext in .deploy-state.creds with chmod 600  
**Rationale:** User explicitly accepted this trade-off; credentials will be in .env/.tfvars files anyway; recovery UX is improved; file permissions prevent casual access.

---

## Credential Security Confirmation

**Question:** Confirm: plain text .deploy-state.creds file will contain sensitive values (passwords, tokens). Should the script:

| Option | Description | Selected |
|--------|-------------|----------|
| Store plaintext in .deploy-state.creds with chmod 600 | User consciously accepts this trade-off. File readable only by owner (600 perms). We'll document clearly in deploy.sh that sensitive values are in this file. | ✓ |
| Skip credential caching entirely | If recovery needed, user re-enters all credentials. More secure, but slower recovery experience. Each restart = credential re-entry. | |

**User's choice:** Store plaintext with chmod 600 (as chosen)  
**Rationale:** Confirmed that user wants the convenience trade-off; script will enforce chmod 600 permissions and clearly document this design choice in comments.

---

## Checkpoint Prompt Timing

**Question:** When should the script prompt for checkpoint recovery action?

| Option | Description | Selected |
|--------|-------------|----------|
| Immediately on start | Script runs, detects .deploy-state, prompts within first 2 seconds. User knows status immediately; can change course before anything runs. | |
| After pre-flight validation succeeds | Run validation first, then prompt if state exists. Better: user knows if system is compatible before deciding recovery strategy. Slightly delays the prompt. | ✓ |

**User's choice:** After pre-flight validation succeeds  
**Rationale:** Better UX; user learns system is compatible before deciding recovery action; failed validation would prevent recovery anyway.

---

## Output Formatting

**Question:** For colored output (errors, success, warnings), what approach?

| Option | Description | Selected |
|--------|-------------|----------|
| ANSI escape codes | Use \033[31m for red, \033[32m for green, etc. Widely supported. Already shown in requirements. Simple implementation. | ✓ |
| Use 'tput' for terminal-safe codes | Call tput to get color codes. More robust across terminal types, but adds external command dependency. Overkill for MVP. | |

**User's choice:** ANSI escape codes (Recommended)  
**Rationale:** Simple, widely supported, no external dependencies, already specified in REQUIREMENTS; standard approach for bash scripts.

---

## Claude's Discretion

- **Exact phrasing of error messages** — User trusts Claude to compose clear, actionable error messages following the pattern (describe problem + suggest resolution)
- **Helper function naming** — Claude may adjust function names for consistency with bash conventions, as long as pattern is uniform throughout

---

**End of Discussion Log**

---
phase: 06-integrate-backend-and-frontend-deployment
plan: 02
subsystem: infra/deploy.sh
tags: [bash, deployment, docker, terraform, app-deployment]
dependency_graph:
  requires: [06-01]
  provides: [step_deploy_app, write_app_env, pull_or_build_image]
  affects: [infra/deploy.sh]
tech_stack:
  added: []
  patterns: [docker-compose-app-stack, atomic-env-write, docker-health-polling]
key_files:
  modified:
    - infra/deploy.sh
decisions:
  - "Health polling uses docker inspect (not curl) so backend on internal network is reachable"
  - "pull_or_build_image tries GHCR first, falls back to local Docker build with troubleshooting hints"
  - "write_app_env uses atomic temp-file + mv pattern and chmod 600 for security"
  - "GOALDONE_DB_PASSWORD added to both save_state and save_credentials for checkpoint recovery"
metrics:
  duration: "3m 12s"
  completed_date: "2026-05-15"
  tasks_completed: 2
  files_modified: 1
---

# Phase 06 Plan 02: Extend deploy.sh with App Deployment Step Summary

One-liner: Extended deploy.sh from 9 to 10 steps adding automated Docker image pull/build, app.env generation from Terraform outputs, and health-checked app stack startup.

## What Was Built

Added three new functions (`pull_or_build_image`, `write_app_env`, `step_deploy_app`) to `infra/deploy.sh` and updated state management, terraform output extraction, checkpoint recovery, and the output summary to reflect a fully deployed running application.

## Tasks Completed

### Task 1: Extend step_terraform_apply and update state management for 10 steps
**Commit:** 3f1896d

Changes to `infra/deploy.sh`:
- `TOTAL_STEPS` changed from 9 to 10
- `load_state` default initialization adds `ZITADEL_PROJECT_ID=""`, `GOALDONE_ORG_ID=""`, `GOALDONE_DB_PASSWORD=""`
- `save_state` replaces `STEP_9_OUTPUT_SUMMARY` with `STEP_9_DEPLOY_APP` + `STEP_10_OUTPUT_SUMMARY` and persists the three new variables
- `save_credentials` writes `ZITADEL_PROJECT_ID`, `GOALDONE_ORG_ID`, `GOALDONE_DB_PASSWORD` to the 600-mode creds file
- `mark_step_complete` case 9 sets `STEP_9_DEPLOY_APP`, case 10 sets `STEP_10_OUTPUT_SUMMARY`
- `prompt_recovery_action` step_names array extended to 11 entries with "Deploy App" at index 9
- `step_terraform_apply` now extracts `zitadel_project_id` and `goaldone_org_id` from Terraform outputs and caches them

### Task 2: Add step_deploy_app function and update main loop + output summary
**Commit:** 32cf155

New functions added to `infra/deploy.sh`:
- `pull_or_build_image(image_name, dockerfile_path, build_context)`: Tries `docker pull ghcr.io/goaldone-dhbw/<name>:latest`, falls back to `docker build`, shows detailed troubleshooting on both failures
- `write_app_env()`: Writes all Spring Boot + frontend env vars to `${SCRIPT_DIR}/app.env` using atomic temp+mv and `chmod 600`
- `step_deploy_app()`: Full step 9 - prompts for `GOALDONE_DB_PASSWORD` if not cached, pulls/builds both images, verifies zitadel Docker network, detects docker compose command, starts `docker-compose.app.yml`, polls backend health (60s timeout), polls frontend health (30s timeout), calls `mark_step_complete 9`

Updated functions:
- `step_output_summary`: now shows "Application Status: RUNNING", backend health URL, config file paths, useful docker compose commands; calls `mark_step_complete 10`
- Main execution loop: case 9 calls `step_deploy_app`, case 10 calls `step_output_summary`

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None. All env vars written to `app.env` are sourced from Terraform outputs and user prompts. The `docker-compose.app.yml` referenced by `step_deploy_app` is created in plan 06-01.

## Self-Check: PASSED

- infra/deploy.sh: FOUND
- 06-02-SUMMARY.md: FOUND
- Commit 3f1896d (Task 1): FOUND
- Commit 32cf155 (Task 2): FOUND

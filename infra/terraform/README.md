# GoalDone Zitadel Terraform Configuration

This directory contains the Terraform configuration for provisioning Zitadel resources for the GoalDone project.

## Resources Managed
- SMTP Configuration (`smtp.tf`)
- Login & Password Policies (`login_policy.tf`)
- GoalDone Project & Roles (`roles.tf`)
- OIDC Application for Frontend (`oidc.tf`)
- Branding & Label Policy (`branding.tf`)
- Backend Service User & Personal Access Token (`service_user.tf`)

## Prerequisites
- Terraform installed
- A Zitadel instance running
- A service account token for Zitadel (extracted from machinekey)

## Usage
These files are intended to be executed by the `infra/deploy.sh` script.

1. `deploy.sh` generates `terraform.tfvars` with the required variables.
2. `terraform init`
3. `terraform apply -auto-approve`

## Outputs
- `goaldone_backend_pat`: The PAT for the backend service (sensitive).
- `goaldone_app_client_id`: The Client ID for the OIDC application.
- `zitadel_project_id`: The ID of the created project.
- `zitadel_org_id`: The ID of the organization.

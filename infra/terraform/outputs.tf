output "goaldone_backend_pat" {
  value     = zitadel_personal_access_token.backend_pat.token
  sensitive = true
}

output "goaldone_app_client_id" {
  value     = zitadel_application_oidc.frontend_app.client_id
  sensitive = true
}

output "zitadel_project_id" {
  value = zitadel_project.goaldone.id
}

output "zitadel_domain_output" {
  value = var.zitadel_domain
}

output "goaldone_url_output" {
  value = var.goaldone_url
}

output "goaldone_org_id" {
  value = var.org_id
}

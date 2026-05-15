resource "zitadel_application_oidc" "frontend_app" {
  org_id                      = var.org_id
  project_id                  = zitadel_project.goaldone.id
  name                        = "GoalDone Frontend"
  redirect_uris               = ["https://${var.goaldone_url}/callback", "https://${var.goaldone_url}/link-callback"]
  post_logout_redirect_uris   = ["https://${var.goaldone_url}"]
  response_types              = ["OIDC_RESPONSE_TYPE_CODE"]
  grant_types                 = ["OIDC_GRANT_TYPE_AUTHORIZATION_CODE", "OIDC_GRANT_TYPE_REFRESH_TOKEN"]
  app_type                    = "OIDC_APP_TYPE_WEB"
  auth_method_type            = "OIDC_AUTH_METHOD_TYPE_NONE"
  version                     = "OIDC_VERSION_1_0"
  access_token_type            = "OIDC_TOKEN_TYPE_JWT"
  dev_mode                    = false
  id_token_role_assertion     = true
  access_token_role_assertion = true
  id_token_userinfo_assertion = true

  login_version {
    login_v1 = false
  }
}

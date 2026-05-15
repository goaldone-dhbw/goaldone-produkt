resource "zitadel_login_policy" "default" {
  org_id                         = var.org_id
  user_login                     = true
  allow_register                 = false
  allow_external_idp             = true
  force_mfa                      = false
  force_mfa_local_only           = false
  passwordless_type              = "ALLOWED"
  hide_password_reset            = false
  ignore_unknown_usernames       = false
  default_redirect_uri           = ""
  password_check_lifetime        = "240h"
  external_login_check_lifetime  = "240h"
  mfa_init_skip_lifetime         = "720h"
  second_factor_check_lifetime   = "18h"
  multi_factor_check_lifetime    = "12h"
}

# resource "terraform_data" "disable_org_registration" {
#   input = {
#     domain = var.zitadel_domain
#     token  = var.zitadel_token
#   }
#
#   provisioner "local-exec" {
#     command = <<-EOT
#       curl -sf -X PUT \
#         "https://${self.input.domain}/admin/v1/restrictions" \
#         -H "Authorization: Bearer ${self.input.token}" \
#         -H "Content-Type: application/json" \
#         -d '{"disallowPublicOrgRegistration": true}'
#     EOT
#   }
# }

resource "zitadel_default_login_policy" "default" {
  user_login                     = true
  allow_register                 = false
  allow_external_idp             = true
  force_mfa                      = false
  force_mfa_local_only           = false
  passwordless_type              = "ALLOWED"
  hide_password_reset            = false
  ignore_unknown_usernames       = false
  default_redirect_uri           = "https://${var.goaldone_url}"
  password_check_lifetime        = "240h"
  external_login_check_lifetime  = "240h"
  mfa_init_skip_lifetime         = "720h"
  second_factor_check_lifetime   = "18h"
  multi_factor_check_lifetime    = "12h"
  second_factors                 = []
  multi_factors                  = []
  idps                           = []
}

resource "zitadel_password_complexity_policy" "default" {
  org_id        = var.org_id
  min_length    = 8
  has_uppercase = true
  has_lowercase = true
  has_number    = true
  has_symbol    = true
}

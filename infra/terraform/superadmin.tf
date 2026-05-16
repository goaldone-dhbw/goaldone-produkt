resource "zitadel_human_user" "first_superadmin" {
  org_id           = var.org_id
  user_name        = var.first_superadmin_email
  first_name       = "Super"
  last_name        = "Admin"
  email            = var.first_superadmin_email
  is_email_verified = true
  initial_password = var.first_superadmin_password
  initial_skip_password_change = true
}

resource "zitadel_user_grant" "first_superadmin_role" {
  depends_on = [terraform_data.project_roles]

  org_id     = var.org_id
  user_id    = zitadel_human_user.first_superadmin.id
  project_id = zitadel_project.goaldone.id
  role_keys  = ["SUPER_ADMIN"]
}

resource "zitadel_human_user" "first_superadmin" {
  org_id           = var.org_id
  user_name        = var.first_superadmin_email
  initial_password = var.first_superadmin_password

  email {
    address     = var.first_superadmin_email
    is_verified = true
  }
}

resource "zitadel_user_grant" "first_superadmin_role" {
  depends_on = [terraform_data.project_roles]

  org_id     = var.org_id
  user_id    = zitadel_human_user.first_superadmin.id
  project_id = zitadel_project.goaldone.id
  role_keys  = ["SUPER_ADMIN"]
}

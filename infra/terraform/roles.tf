resource "zitadel_project" "goaldone" {
  org_id                 = var.org_id
  name                   = "GoalDone"
  project_role_assertion = true
  project_role_check     = true
}

resource "terraform_data" "project_roles" {
  depends_on = [zitadel_project.goaldone]

  input = {
    project_id = zitadel_project.goaldone.id
    domain     = var.zitadel_domain
    token      = var.zitadel_token
  }

  provisioner "local-exec" {
    command = <<-EOT
      curl -sf -X POST \
        "https://${self.input.domain}/management/v1/projects/${self.input.project_id}/roles/_bulk" \
        -H "Authorization: Bearer ${self.input.token}" \
        -H "Content-Type: application/json" \
        -d '{"roles": [{"key": "SUPER_ADMIN", "displayName": "Super Admin", "group": "admins"}, {"key": "COMPANY_ADMIN", "displayName": "Company Admin", "group": "admins"}, {"key": "USER", "displayName": "User", "group": "users"}]}'
    EOT
  }
}

resource "zitadel_machine_user" "backend_service" {
  org_id            = var.org_id
  user_name         = "backend-service"
  name              = "GoalDone Backend Service"
  description       = "Service user for backend authentication"
  access_token_type = "ACCESS_TOKEN_TYPE_JWT"
}

resource "zitadel_org_member" "backend_service_org_owner" {
  org_id  = var.org_id
  user_id = zitadel_machine_user.backend_service.id
  roles   = ["ORG_OWNER"]
}

resource "zitadel_instance_member" "backend_service_iam" {
  user_id = zitadel_machine_user.backend_service.id
  roles   = ["IAM_OWNER", "IAM_USER_MANAGER"]
}

resource "zitadel_personal_access_token" "backend_pat" {
  org_id  = var.org_id
  user_id = zitadel_machine_user.backend_service.id
}

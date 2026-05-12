variable "zitadel_domain" {
  type        = string
  description = "The domain of the Zitadel instance (e.g., sso.goaldone.de)"
}

variable "zitadel_token" {
  type        = string
  sensitive   = true
  description = "Zitadel service account token for API access"
}

variable "org_id" {
  type        = string
  description = "The numeric ID of the Zitadel organization (auto-discovered by deploy.sh)"
}

variable "goaldone_url" {
  type        = string
  description = "The URL of the GoalDone application (e.g., app.goaldone.de)"
}

variable "smtp_host" {
  type        = string
  description = "SMTP host for mail delivery (including port, e.g., smtp.mailprovider.de:587)"
}

variable "smtp_user" {
  type        = string
  sensitive   = true
  description = "SMTP username"
}

variable "smtp_password" {
  type        = string
  sensitive   = true
  description = "SMTP password"
}

variable "smtp_sender_address" {
  type        = string
  description = "Sender email address for SMTP"
}

variable "smtp_sender_name" {
  type        = string
  default     = "GoalDone"
  description = "Sender name for SMTP"
}

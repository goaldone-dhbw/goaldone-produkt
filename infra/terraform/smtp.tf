resource "zitadel_smtp_config" "default" {
  host           = var.smtp_host
  user           = var.smtp_user
  password       = var.smtp_password
  sender_address = var.smtp_sender_address
  sender_name    = var.smtp_sender_name
  tls            = true
}

resource "zitadel_label_policy" "default" {
  org_id                  = var.org_id
  primary_color           = "#10b981"
  primary_color_dark      = "#10b981"
  background_color        = "#ffffff"
  background_color_dark   = "#111827"
  font_color              = "#000000"
  font_color_dark         = "#ffffff"
  warn_color              = "#f59e0b"
  warn_color_dark         = "#f59e0b"
  hide_login_name_suffix  = true
  disable_watermark       = true
  theme_mode              = "THEME_MODE_AUTO"
}

# The provider's logo_path attribute requires jwt_profile_file auth, which we don't use.
# Workaround: upload directly via the Assets API, which accepts the PAT as a Bearer token.
resource "terraform_data" "upload_logo" {
  depends_on = [zitadel_label_policy.default]

  triggers_replace = [
    filemd5("${path.module}/assets/logo.png")
  ]

  provisioner "local-exec" {
    command = <<-EOT
      HTTP_CODE=$(curl -s --http1.1 -X POST \
        "https://${var.zitadel_domain}/assets/v1/org/policy/label/logo" \
        -H "Authorization: Bearer ${var.zitadel_token}" \
        -H "x-zitadel-orgid: ${var.org_id}" \
        -F "file=@${path.module}/assets/logo.png" \
        -o /tmp/zitadel-logo-upload.json \
        -w "%%{http_code}")
      echo "Logo upload HTTP $HTTP_CODE: $(cat /tmp/zitadel-logo-upload.json)"
      [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ] || exit 1
    EOT
  }
}

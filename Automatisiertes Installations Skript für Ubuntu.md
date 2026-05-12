Das Skript ist ausgelegt für Ubuntu 24.04, das frisch auf einem VPS installiert wurde. Es wird außerdem auf einem normalen User Account ausgeführt, nicht auf dem root account, daher benutzt immer sudo und verlange wenn nötig das sudo Passwort einmal oder starte das Skript erst gar nicht, wenn es nicht als sudo gestartet wurde. 

Das Skript muss so gestaltet werden, dass die falls ein Schritt fehlgeschlagen ist und das Skript neugestartet wird, nicht wieder von vorne begonnen wird, sondern der Nutzer gefragt wird, ob er an der vorherigen Stelle wieder weiter machen will oder den letzten Schritt erneut ausführen möchte oder ganz von vorne wieder beginnen will. 

Ebenso müssen Fehler dargestellt werden, so dass diese für den Nutzer verständlich sind. Ebenso sollen sinnvolle Ausgaben während der Ausführung der einzelnen Schritte erstellt werden.

# Schritte

## Vorbereitung
1. Installation von Docker, Git und Terraform
2. Festlegen von UFW Regeln (Freigabe von 80, 443, 22 und Blockieren aller anderen Ports für Eingangstraffic)
3. Klonen unseres Repos (https://github.com/goaldone-dhbw/goaldone-produkt.git)
4. Wechseln in infra Ordner und erstellen einer neuen .env Datei aus der .env-example
5. Dabei muss der Nutzer nach folgenden Eingaben gefragt werden:
	1. Masterkey für Zitadel
	   ```
	   # Must be exactly 32 characters. Generate with:  
	   #   tr -dc A-Za-z0-9 </dev/urandom | head -c 32  
       ZITADEL_MASTERKEY=XYUIGTDBUKOWCLDIIXLUBHLEYESMXGRC
       ```
    2. Zitadel-Admin-Password: ZITADEL_ADMIN_PASSWORD=examplePass
    3. Postgres Admin Password: POSTGRES_ADMIN_PASSWORD=CHANGE_ME_postgres_admin_password
    4. Postgres Zitadel Password: POSTGRES_ZITADEL_PASSWORD=CHANGE_ME_zitadel_db_password
    5. Zitadel URL: ZITADEL_DOMAIN=sso.goaldone.de
6. Anschließend erstelle die .env mit den neuen Variablen gesetzt. Dabei müssen eventuelle Platzhalter ausgetauscht werden für diese env variablen
7. Docker Compose starten und warten, ob alles ordentlich startet. Falls nein, dann gib eine Fehlermeldung an den Nutzer zurück und unterbrich die weitere Ausführung des Skripts mit Lösungsvorschläge. 
8. Anschließend nutze das erstelle Service Machine User Passwort aus der ./machinekey Datei um im Anschluss eine neue .tfvars Datei zu erstellen. Diese wird anschließend für Terraform genutzt. 

## Terraform
### Erstellen von Terraform Dateien (Vorbereitung vor dem erstellen des Skripts)

Folgende Schritte muss mittels Terraform Provider von Zitadel ausgeführt werden. 
1. Hinzufügen eine SMTP Email providers
	- Nutzen der Variablen aus .tfvars
2. Change login behaviour:
	- Deactivate User Registration allowed
	- Set default redirection_uri to: https://{GOALDONE_URL}/
3. Anlegen eines neuen Projekts in Zitadel
	- Name des Projekts: Goaldone
4. Hinzufügen von Rollen im Projekt:
	- **key**: COMPANY_ADMIN **DisplayName**: Unternehmens-Admin **group**: admins
	- **key**: SUPER_ADMIN **DisplayName**: Super-Admin **group**: admins
	- **key**: USER **DisplayName**: Nutzer **group**: users
5. Anlegen einer neuen Application:
	- Name: Goaldone
	- Application Type: Web
	- OIDC Config: PKCE
	- Response Types: Code
	- Auth Method: None
	- Grant Types: Authorization Code, Refresh Token
	- User new Login UI: true
6. Ändern der Token Einstellungen der neuen Anwendung:
	- Auth Token Type: JWT
	- Add user roles to the access token
	- User Roles inside ID Token
	- Include user's profile info in the ID Token
7. Setup Redirect Settings for Application:
	- Nutze eine Umgebungsvariable aus .tfvars (GOALDONE_URL) als Base URL und hinzufügen der follgenden Domains:
		- https://{GOALDONE_URL}/callback
		- https://{GOALDONE_URL}/link-callback
8. Updaten des Brandings:
	- Farben werden über Terraform geändert
	- Logo Upload über einen Local-Exec Block
	  ```shell
	  resource "null_resource" "upload_logo" {
  triggers = {
    logo_hash = filemd5("./assets/logo-light.png")
  }

  provisioner "local-exec" {
    command = <<EOT
      curl -X POST "https://${var.zitadel_domain}/instance/policy/label/logo" \
        -H "Authorization: Bearer ${var.terraform_token}" \
        -F "file=@./assets/logo-light.png"
    EOT
  }

  depends_on = [zitadel_default_label_policy.branding]
}
	  ```
7. Erstellen eines neuen Service Machine Nutzers
	- Access Token Type: JWT
	- Name: Goaldone Service User
	- Memberships: ![[Pasted image 20260512133942.png]]
8. Ggf. einzelner Schritt zum hinzufügen der Rollen: IAM_OWNER und IAM_USER_MANAGER als Instanz Rollen zum neuen Service User
9. Hinzufügen von Rollen zum Service Nutzer in der Default Goaldone Org: ORG_OWNER, ORG_USER_MANAGER, ORG_PROJECT_PERMISSIONS_EDITOR
10. Anlegen eines PAT für Goaldone Backend und Ausgabe als output von Terraform

#### Aufbau Terraform:
```terraform
# variables.tf (bzw. aus .tfvars gelesen)
variable "zitadel_domain"       { type = string }
variable "smtp_host"            { type = string }
variable "smtp_user"            { type = string }
variable "smtp_password"        { type = string sensitive = true }
variable "smtp_sender_address"  { type = string }
variable "goaldone_url"         { type = string }    # z.B. "app.goaldone.de"
variable "terraform_token"      { type = string sensitive = true }
variable "org_id"               { type = string }    # ID der Default Goaldone Org

# ── 1. SMTP Email Provider ────────────────────────────────────────────────────
resource "zitadel_email_provider_smtp" "main" {
  sender_address = var.smtp_sender_address
  sender_name    = "Goaldone"
  host           = var.smtp_host
  tls            = true
  user           = var.smtp_user
  password       = var.smtp_password
}

# ── 2. Login Policy ───────────────────────────────────────────────────────────
resource "zitadel_default_login_policy" "main" {
  allow_register          = false
  allow_username_password = true
  allow_external_idp      = true
  default_redirect_uri    = "https://${var.goaldone_url}/"
  # weitere Felder behalten Standardwerte
}

# ── 3. Projekt anlegen ────────────────────────────────────────────────────────
resource "zitadel_project" "goaldone" {
  org_id                   = var.org_id
  name                     = "Goaldone"
  project_role_assertion   = true
  project_role_check       = true
  has_project_check        = true
}

# ── 4. Projektrollen hinzufügen ───────────────────────────────────────────────
resource "zitadel_project_role" "company_admin" {
  org_id       = var.org_id
  project_id   = zitadel_project.goaldone.id
  role_key     = "COMPANY_ADMIN"
  display_name = "Unternehmens-Admin"
  group        = "admins"
}

resource "zitadel_project_role" "super_admin" {
  org_id       = var.org_id
  project_id   = zitadel_project.goaldone.id
  role_key     = "SUPER_ADMIN"
  display_name = "Super-Admin"
  group        = "admins"
}

resource "zitadel_project_role" "user" {
  org_id       = var.org_id
  project_id   = zitadel_project.goaldone.id
  role_key     = "USER"
  display_name = "Nutzer"
  group        = "users"
}

# ── 5 + 6. OIDC Anwendung + Token-Einstellungen + Redirect ───────────────────
resource "zitadel_app_oidc" "goaldone" {
  org_id     = var.org_id
  project_id = zitadel_project.goaldone.id
  name       = "Goaldone"

  # Application Type: Web, PKCE
  app_type         = "OIDC_APP_TYPE_WEB"
  auth_method_type = "OIDC_AUTH_METHOD_TYPE_NONE"   # Auth Method: None (PKCE)

  # Response Type + Grant Types
  response_types = ["OIDC_RESPONSE_TYPE_CODE"]
  grant_types    = [
    "OIDC_GRANT_TYPE_AUTHORIZATION_CODE",
    "OIDC_GRANT_TYPE_REFRESH_TOKEN"
  ]

  # Token-Einstellungen (Schritt 6)
  access_token_type            = "OIDC_TOKEN_TYPE_JWT"
  access_token_role_assertion  = true     # Rollen im Access Token
  id_token_role_assertion      = true     # Rollen im ID Token
  id_token_userinfo_assertion  = true     # Profil-Info im ID Token

  # Redirect URIs (Schritt 6)
  redirect_uris = [
    "https://${var.goaldone_url}/callback",
    "https://${var.goaldone_url}/link-callback"
  ]
  post_logout_redirect_uris = [
    "https://${var.goaldone_url}/"
  ]

  # Login v2 UI verwenden
  login_version {
    login_v2 {
      # base_uri leer lassen = Instanz-Default wird genutzt
    }
  }

  version    = "OIDC_VERSION_1_0"
  dev_mode   = false
}

# ── 7. Branding – Farben ──────────────────────────────────────────────────────
resource "zitadel_default_label_policy" "branding" {
  primary_color         = "#FF6B00"
  primary_color_dark    = "#FF8C3A"
  background_color      = "#FFFFFF"
  background_color_dark = "#1A1A1A"
  warn_color            = "#CC0000"
  warn_color_dark       = "#FF4444"
  font_color            = "#111111"
  font_color_dark       = "#F5F5F5"
  hide_login_name_suffix = false
  disable_watermark      = true
}

# ── 7. Branding – Logo Upload ─────────────────────────────────────────────────
resource "null_resource" "upload_logo" {
  triggers = {
    logo_hash      = filemd5("./assets/logo-light.png")
    logo_dark_hash = filemd5("./assets/logo-dark.png")
  }

  provisioner "local-exec" {
    command = <<EOT
      curl -s -X POST "https://${var.zitadel_domain}/instance/policy/label/logo" \
        -H "Authorization: Bearer ${var.terraform_token}" \
        -F "file=@./assets/logo-light.png"

      curl -s -X POST "https://${var.zitadel_domain}/instance/policy/label/logo/dark" \
        -H "Authorization: Bearer ${var.terraform_token}" \
        -F "file=@./assets/logo-dark.png"
    EOT
  }

  depends_on = [zitadel_default_label_policy.branding]
}

# ── 8. Service Machine User ───────────────────────────────────────────────────
resource "zitadel_machine_user" "goaldone_service" {
  org_id            = var.org_id
  user_name         = "goaldone-service-user"
  name              = "Goaldone Service User"
  access_token_type = "ACCESS_TOKEN_TYPE_JWT"
}

# ── 9. Instanz-Rollen (IAM-Ebene) ────────────────────────────────────────────
resource "zitadel_instance_member" "service_iam_roles" {
  user_id = zitadel_machine_user.goaldone_service.id
  roles   = ["IAM_OWNER", "IAM_USER_MANAGER"]
}

# ── 9. Org-Rollen in der Goaldone Org ────────────────────────────────────────
resource "zitadel_org_member" "service_org_roles" {
  org_id  = var.org_id
  user_id = zitadel_machine_user.goaldone_service.id
  roles   = [
    "ORG_OWNER",
    "ORG_USER_MANAGER",
    "ORG_PROJECT_PERMISSIONS_EDITOR"
  ]
}

# ── 10. PAT generieren ────────────────────────────────────────────────────────
resource "zitadel_personal_access_token" "goaldone_backend_pat" {
  org_id          = var.org_id
  user_id         = zitadel_machine_user.goaldone_service.id
  expiration_date = "2030-01-01T00:00:00Z"
}

output "goaldone_backend_pat" {
  value     = zitadel_personal_access_token.goaldone_backend_pat.token
  sensitive = true
}
output "goaldone_app_client_id" {
  value     = zitadel_app_oidc.goaldone.client_id
  sensitive = false   # ClientID ist kein Geheimnis
}
```

| #   | Feature                                    | Terraform-Ressource                                                                                                                                       |
| --- | ------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | SMTP Provider                              | `zitadel_email_provider_smtp`                                                                                                                             |
| 2   | Login Policy (kein Register, redirect_uri) | `zitadel_default_login_policy`                                                                                                                            |
| 3   | Projekt „Goaldone"                         | `zitadel_project`                                                                                                                                         |
| 4   | Projektrollen (3 Rollen mit group)         | `zitadel_project_role` × 3                                                                                                                                |
| 5   | OIDC App (Web, PKCE, Code+Refresh)         | `zitadel_app_oidc`                                                                                                                                        |
| 6   | Token-Settings + Redirects + Login v2      | Attribute in `zitadel_app_oidc` [registry.terraform](https://registry.terraform.io/providers/zitadel/zitadel/latest/docs/resources/application_oidc)      |
| 7   | Branding Farben + Logo-Upload              | `zitadel_default_label_policy` + `null_resource`                                                                                                          |
| 8   | Machine User (JWT)                         | `zitadel_machine_user`                                                                                                                                    |
| 9a  | IAM-Instanzrollen                          | `zitadel_instance_member`                                                                                                                                 |
| 9b  | Org-Rollen                                 | `zitadel_org_member` [registry.terraform](https://registry.terraform.io/providers/zitadel/zitadel/latest/docs/resources/org_member)                       |
| 10  | PAT + Output                               | `zitadel_personal_access_token` [registry.terraform](https://registry.terraform.io/providers/zitadel/zitadel/latest/docs/resources/personal_access_token) |
#### Aufbau vars.tfvars
```shell
zitadel_domain      = "auth.goaldone.de"
goaldone_url        = "app.goaldone.de"
org_id              = "123456789"
smtp_host           = "smtp.mailprovider.de:587"
smtp_user           = "noreply@goaldone.de"
smtp_sender_address = "noreply@goaldone.de"
# smtp_password und terraform_token: via TF_VAR_ Umgebungsvariable setzen!
```

### Weitere Schritte im Skript für Terraform
9. Initialisieren des Terraform Providers mit terraform init
10. Terraform plan
11. Terraform apply (Fehler sollen hübsch angezeigt werden im Skript)
12. Aufsammeln der Output Werte von Terraform (PAT, ClientID)
13. Vorerst Ende des Skripts, später werden beide Werte noch weiterbenutzt für das Aufsetzen des Front und Backends (Vorerst ausgeben an den Nutzer)


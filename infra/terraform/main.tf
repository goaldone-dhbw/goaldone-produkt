terraform {
  required_providers {
    zitadel = {
      source  = "zitadel/zitadel"
      version = "2.10.0"
    }
  }
}

provider "zitadel" {
  domain       = var.zitadel_domain
  access_token = var.zitadel_token
}

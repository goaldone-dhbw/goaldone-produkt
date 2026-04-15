# Goaldone Infrastructure Setup

This directory contains the Docker Compose configuration for the Goaldone infrastructure stack running on the VPS.

## Stack Overview

- **Traefik**: Reverse proxy + Let's Encrypt automation (DNS challenge via Cloudflare)
- **PostgreSQL**: Database for Zitadel (identity provider)
- **Zitadel**: OpenID Connect (OIDC) provider for authentication

## Prerequisites

- Docker Engine 20.10+ and Docker Compose 2.0+
- A domain name with Cloudflare DNS (for Let's Encrypt DNS challenge)
- Cloudflare API token with `Zone:DNS:Edit` permission for your domain

## Setup Instructions

### 1. Prepare Environment Variables

```bash
cp .env.example .env
```

Edit `.env` and fill in the following:

- **DOMAIN**: Your domain (e.g., `goaldone.de`)
- **ACME_EMAIL**: Email for Let's Encrypt certificates
- **CF_DNS_API_TOKEN**: Cloudflare API token with DNS edit permission
- **POSTGRES_PASSWORD**: Strong password for PostgreSQL
- **ZITADEL_MASTERKEY**: Random 32-character key for Zitadel encryption
- **ZITADEL_FIRSTINSTANCE_ORG_HUMAN_PASSWORD**: Initial superadmin password (can be changed later)

### 2. Deploy to VPS

```bash
# From your local machine
scp -r .env docker-compose.yml user@vps:/path/to/goaldone/infra-setup/

# SSH into VPS
ssh user@vps

# Navigate to the directory
cd /path/to/goaldone/infra-setup

# Start services
docker compose up -d
```

### 3. Verify Deployment

```bash
# Check container status
docker compose ps

# Monitor Zitadel startup (watch until "server is listening on :8080")
docker compose logs -f zitadel

# Test HTTPS (should show valid Let's Encrypt certificate)
curl -I https://auth.goaldone.de
```

### 4. Access Zitadel

1. Open browser to `https://auth.goaldone.de`
2. Log in with:
   - **Username**: `superadmin@goaldone.de`
   - **Password**: Value of `ZITADEL_FIRSTINSTANCE_ORG_HUMAN_PASSWORD` from `.env`
3. Verify the "Goaldone" organization is visible

## Service Details

### Traefik Dashboard

Available at `https://traefik.{DOMAIN}` (requires basic auth for production use).

### PostgreSQL

- **Host**: `postgres` (internal network only)
- **Port**: `5432`
- **Database**: Value of `POSTGRES_DB` from `.env`
- **User**: Value of `POSTGRES_USER` from `.env`

### Zitadel

- **Internal Port**: `8080`
- **External URL**: `https://auth.{DOMAIN}`
- **gRPC Endpoint**: Same domain (Traefik handles h2c passthrough)

## Volume Persistence

Two named Docker volumes ensure data survives container restarts:

- `infra-setup_postgres-data`: PostgreSQL data directory
- `infra-setup_traefik-certs`: Let's Encrypt certificates and ACME state

```bash
# View volumes
docker volume ls | grep infra-setup

# Inspect volume location
docker volume inspect infra-setup_postgres-data
```

## Troubleshooting

### Zitadel won't start: "connection refused to postgres"

Ensure PostgreSQL is healthy:

```bash
docker compose ps postgres
docker compose logs postgres
```

Wait for the healthcheck to pass (first log should show "database system is ready to accept connections").

### SSL certificate issues

Check Traefik logs:

```bash
docker compose logs traefik | grep -i acme
```

Verify Cloudflare API token has correct permissions and domain is using Cloudflare DNS.

### Database locked / corruption

This should never happen with named volumes, but if needed:

```bash
# Backup data
docker volume inspect infra-setup_postgres-data  # Shows mount point
# Then manually inspect or delete if absolutely necessary

# Full reset (WARNING: deletes all data)
docker compose down -v
docker compose up -d
```

## Environment File Security

**NEVER commit `.env` to version control.** The `.gitignore` file in this directory prevents accidental commits, but always verify before pushing:

```bash
git status  # Should show .env as ignored
```

## Next Steps

Once the infrastructure is running:

1. Configure OIDC applications (separate ticket)
2. Connect frontend/backend to Zitadel for authentication
3. Monitor container health in production

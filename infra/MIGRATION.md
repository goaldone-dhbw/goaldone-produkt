# VPS-Migration: Umzug auf einen neuen Server (ohne Datenverlust)

Dieses Runbook beschreibt den Umzug der kompletten GoalDone-Infrastruktur von einem VPS
auf einen neuen VPS (z. B. Anbieterwechsel DigitalOcean → Hetzner/Netcup), **ohne
Datenverlust** und **unter Beibehaltung des bestehenden CI/CD-Workflows** (Push → Dev,
nach Bestätigung → Prod).

> Annahmen dieses Plans: Dev + Prod (App-Stacks + Zitadel) laufen auf **einem** VPS und
> ziehen auf **einen** neuen VPS. Die Domains (`goaldone.de`, `dev.goaldone.de`,
> `sso.goaldone.de`, `sso.dev.goaldone.de`) bleiben **identisch** — am Ende wird nur das
> DNS umgehängt. Der alte VPS bleibt während der Migration erreichbar.

---

## Warum dieser Ansatz

Weil der Anbieter wechselt, ist ein Provider-Snapshot/Disk-Clone nicht möglich. Da der
alte VPS noch läuft und die Postgres-Images auf dieselbe Major-Version (17) gepinnt sind,
ist ein **kalter Docker-Volume-Kopiervorgang über SSH** die sicherste und einfachste
Methode:

- byte-genaue Kopie inkl. des kompletten **Zitadel-Krypto-Zustands**,
- keine `pg_restore`-Fallstricke (Ownership, Extensions, Rollen),
- gleiches Verfahren für App- und Zitadel-DBs sowie die TLS-Zertifikate.

Optional vorhandene `.dump`-Dateien dienen als **Backup** und für einen **Probelauf ohne
Downtime** (Phase 2).

---

## Architektur-Überblick (Stand Repo)

Zwei getrennte Postgres-Datenbestände + Reverse-Proxy:

| Komponente | DB / Volume | Compose |
|---|---|---|
| **App-DB Dev** | `db=goaldone`, Volume `postgres-dev-data` | `docker/dev/docker-compose.yaml` |
| **App-DB Prod** | `db=goaldone`, Volume `postgres-prod-data` | `docker/prod/docker-compose.yaml` |
| **Zitadel-DB** | `db=zitadel`, Volume `postgres-data` | `infra/infra-setup/docker-compose.yml` |
| **Traefik (TLS)** | Volume `letsencrypt` (acme.json) | `infra/infra-setup/docker-compose.yml` |
| **Zitadel Login-PAT** | Volume `zitadel-bootstrap` | `infra/infra-setup/docker-compose.yml` |

Wichtig:

- Die App-Stacks hängen am **externen** Docker-Netzwerk `zitadel` und werden über die
  **Traefik-Instanz im Zitadel-Stack** geroutet. → **Startreihenfolge: Zitadel-Stack zuerst,
  dann App-Stacks.**
- Die CD-Pipeline injiziert nur `POSTGRES_PASSWORD` und `ZITADEL_CLIENT_ID` in die
  VPS-`.env`. `ZITADEL_SERVICE_ACCOUNT_TOKEN`, `ZITADEL_GOALDONE_PROJECT_ID` und
  `ZITADEL_GOALDONE_ORG_ID` liegen **nur** in `~/docker/{dev,prod}/.env` → müssen mitkopiert
  werden.
- `infra/deploy.sh` (interaktiver Greenfield-Installer) **darf NICHT** für die Migration
  benutzt werden — er führt Terraform + First-Instance-Bootstrap aus und würde eine *neue*
  Zitadel-Instanz mit neuen Client-IDs erzeugen.

---

## Phase 0 — Inventur des alten VPS (read-only, kein Risiko)

Auf dem **alten** VPS als User `goaldone` ausführen und Output sichern:

```bash
docker ps -a
docker volume ls
docker network ls
ls -la ~/docker ~/docker/dev ~/docker/prod
# Zitadel-Stack-Verzeichnis(se) finden:
find ~ -maxdepth 4 -name 'docker-compose*.y*ml' 2>/dev/null
find ~ -maxdepth 4 -name '.env' 2>/dev/null
```

Ziel: bestätigen, **wie viele Zitadel-Instanzen** existieren (eine geteilte vs. getrennt
für dev/prod) und wo deren Compose-/`.env`-Dateien + Volumes liegen.

**Zu migrierende Volumes** (exakte Namen aus `docker volume ls` übernehmen — Compose
prefixt mit dem Projektnamen, z. B. `goaldone-prod_postgres-prod-data`):

- Zitadel: `postgres-data`, `zitadel-bootstrap`, `letsencrypt`
- App Dev: `…_postgres-dev-data`
- App Prod: `…_postgres-prod-data`

**Zu migrierende Dateien (Secrets/Config):**

- `infra-setup/.env` (enthält `ZITADEL_MASTERKEY`, Postgres-Passwörter) — **kritisch**
- `~/docker/dev/.env` und `~/docker/prod/.env` (Service-Account-Token, Project-/Org-ID,
  Client-ID, `ACTIVE_COLOR`)
- alle Zitadel-Compose-Dateien + ggf. `infra-setup/machinekey/`

---

## Phase 1 — Neuen VPS provisionieren (alter bleibt online)

1. VPS erstellen, **Ubuntu 24.04**.
2. User `goaldone` anlegen, in `docker`-Gruppe + sudo.
3. **SSH auf Port 6767** konfigurieren (`/etc/ssh/sshd_config` → `Port 6767`),
   Passwort-Login deaktivieren.
4. **Denselben SSH-Public-Key** in `~goaldone/.ssh/authorized_keys` hinterlegen, der zum
   GitHub-Secret `VPS_SSH_KEY` passt → in der CI muss dann nur `VPS_HOST` geändert werden.
5. **Provider-Firewall**: TCP **80, 443, 6767** eingehend erlauben.
6. Docker + Compose-Plugin installieren (manuell, oder `infra/install-deps.sh` aus dem Repo).
7. GHCR-Login: `echo $GHCR_TOKEN | docker login ghcr.io -u <user> --password-stdin`
   (gleicher Token wie in der CI; die CD macht das bei jedem Deploy ohnehin erneut).

---

## Phase 2 — Probelauf aus den `.dump`-Dateien (optional, ohne Downtime)

Empfohlene Validierung, **bevor** der alte Server angefasst wird (`pg_dump` ist
online-konsistent → kein Risiko für den laufenden Betrieb):

1. Verzeichnisstruktur replizieren: `~/docker/dev`, `~/docker/prod`, Zitadel-Stack-Dir.
2. Compose-Dateien + alle `.env` (inkl. Masterkey) vom alten VPS kopieren (scp).
3. Zitadel-Stack starten → erzeugt Netzwerk `zitadel` + Traefik + leere DB, dann
   Zitadel-DB aus Dump restaurieren.
4. Auf dem Test-Rechner per `/etc/hosts` die vier Domains auf die neue VPS-IP zeigen lassen
   und Login/Funktion prüfen — **ohne** echtes DNS anzufassen.
5. Erkenntnisse notieren, Stacks herunterfahren, Volumes für sauberen Final-Cut leeren.

> Wer den Probelauf überspringt, geht direkt zu Phase 3 (kalte Volume-Kopie = autoritativ).

---

## Phase 3 — Finaler Cutover (kurzes Wartungsfenster)

**Vorab (1–2 Tage früher):** DNS-TTL der vier Records auf 300s senken.

1. **Wartungsfenster ankündigen.** Auf dem **alten** VPS alle Stacks stoppen
   (konsistente Postgres-Kopie nur bei gestoppter DB):
   ```bash
   docker compose -f ~/docker/prod/docker-compose.yaml stop
   docker compose -f ~/docker/dev/docker-compose.yaml stop
   docker compose -f <zitadel-stack>/docker-compose.yml stop
   ```
2. **Kalte Volume-Kopie** je Volume (siehe Skript `infra/migrate-volumes.sh`), Muster:
   ```bash
   # auf ALT: Volume als tar.gz exportieren
   docker run --rm -v <VOLUME>:/data -v "$PWD":/backup alpine \
     tar czf /backup/<VOLUME>.tgz -C /data .
   scp -P 6767 <VOLUME>.tgz goaldone@<NEU>:~/migrate/
   # auf NEU: leeres Ziel-Volume anlegen und befüllen
   docker volume create <VOLUME>
   docker run --rm -v <VOLUME>:/data -v ~/migrate:/backup alpine \
     sh -c "cd /data && tar xzf /backup/<VOLUME>.tgz"
   ```
   Für **alle** Volumes aus Phase 0 wiederholen. Volume-Namen müssen exakt den von Compose
   erwarteten entsprechen (Projektpräfix beachten).
3. **`.env`- und Compose-Dateien** final synchronisieren (falls seit Probelauf geändert).
4. **Start auf neuem VPS in korrekter Reihenfolge:**
   ```bash
   # 1) Zitadel-Stack zuerst (legt Netzwerk `zitadel` + Traefik an, skippt Bootstrap)
   docker compose -f <zitadel-stack>/docker-compose.yml up -d
   # 2) App-Stacks
   docker compose -f ~/docker/dev/docker-compose.yaml up -d
   docker compose -f ~/docker/prod/docker-compose.yaml up -d
   ```
   Das mitkopierte `letsencrypt`-Volume sorgt dafür, dass bestehende Zertifikate
   weitergelten (IP-unabhängig) → keine Let's-Encrypt-Rate-Limit-Probleme.
5. **Verifizieren auf neuem VPS, noch vor DNS-Switch** (per lokalem `/etc/hosts`):
   - `docker ps` → alle healthy; `docker compose logs zitadel-api` ohne Krypto-/Masterkey-Fehler.
   - Login über `sso.goaldone.de` + App-Login + vorhandene Tasks/Schedules sichtbar.
   - Backend-Health: `https://goaldone.de/api/v1/actuator/health`.

---

## Phase 4 — DNS-Cutover & CI/CD umstellen

1. **DNS A-Records** der vier Domains auf neue VPS-IP setzen. Propagation abwarten (TTL 300s).
2. Erneut alle vier Domains real prüfen (Login, App, gültige Zertifikate).
3. **GitHub-Secret `VPS_HOST`** auf neue IP/Hostname ändern.
   (Bei wiederverwendetem SSH-Key bleibt `VPS_SSH_KEY` unverändert; alle DEV_/PROD_-Secrets bleiben.)
4. **CD-Pipeline testen:** kleinen Commit auf `master` pushen → Build → Deploy-Dev →
   (Prod-Environment-Approval) → Deploy-Prod. Prüfen, dass `scripts/deploy.sh` die
   Blue-Green-Frontend-Rotation + Backend-Update sauber durchführt und `~/docker/{dev,prod}/.env`
   (mit `ACTIVE_COLOR`) korrekt fortschreibt.
5. **Alten VPS** noch 1–2 Tage (gestoppt) als Rollback halten, dann kündigen.

---

## Kritische Stolpersteine (Checkliste)

- [ ] **Masterkey identisch** (`ZITADEL_MASTERKEY` aus altem `infra-setup/.env`). Wichtigster Punkt.
- [ ] **Postgres-Major-Version identisch** (17) — bei Volume-Kopie zwingend. Images sind gepinnt → ok.
- [ ] **App-`.env` mitkopiert** (Service-Account-Token, Project-/Org-ID werden von CD nicht injiziert).
- [ ] **Volume-Kopie nur bei gestoppter Postgres** (sonst inkonsistent/torn).
- [ ] **Startreihenfolge**: Zitadel-Stack (Netzwerk `zitadel` + Traefik) vor App-Stacks.
- [ ] **`infra/deploy.sh` NICHT** verwenden (würde neue Zitadel-Instanz + neue Client-IDs erzeugen).
- [ ] **Firewall** des neuen Providers: 80/443/6767 offen.
- [ ] **Gleicher SSH-Key + Port 6767** → CI braucht nur `VPS_HOST`-Änderung.
- [ ] **DNS-TTL** vorab senken; alten Server als Rollback halten.

---

## Verifikation (End-to-End)

1. `docker ps` → alle Container `healthy`.
2. Zitadel-Console (`https://sso.goaldone.de/ui/console`) Login mit bestehendem Admin.
3. App-Login (`https://goaldone.de`) + `https://dev.goaldone.de` mit bestehendem Nutzer →
   vorhandene Tasks/Schedules sichtbar (= App-DB + Zitadel-Identitäten konsistent migriert).
4. `https://goaldone.de/api/v1/actuator/health` = `UP`.
5. Test-Push auf `master` → CD deployt erfolgreich (Dev automatisch, Prod nach Approval).
6. Datenintegritäts-Stichprobe: Zeilenanzahl Kern-Tabellen alt vs. neu vergleichen
   (`SELECT count(*)` auf `user_account`, `task`, `appointment`, `working_time`).

---

## Hilfsskript

Für die Volume-Kopie steht `infra/migrate-volumes.sh` bereit (kopiert ein benanntes Volume
vom alten zum neuen VPS über SSH). Siehe Kommentar-Header im Skript für Verwendung.

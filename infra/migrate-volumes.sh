#!/usr/bin/env bash
# ==============================================================================
# migrate-volumes.sh — Docker-Named-Volumes vom alten VPS auf den neuen kopieren
# ==============================================================================
# Kopiert ein oder mehrere Docker-Volumes byte-genau vom ALTEN VPS auf DIESEN
# (neuen) Host, gestreamt über SSH (kein Zwischenspeichern auf der Platte).
#
# !!! WICHTIG: Auf dem ALTEN VPS müssen die Container, die die Volumes nutzen,
#     GESTOPPT sein (insb. Postgres), sonst ist die Kopie inkonsistent.
#     Siehe infra/MIGRATION.md, Phase 3.
#
# Ausführen: auf dem NEUEN VPS (zieht die Daten vom alten).
#
# Usage:
#   ./migrate-volumes.sh <old_host> [--port N] [--user U] [--force] <volume> [<volume> ...]
#
# Beispiele:
#   ./migrate-volumes.sh 203.0.113.10 postgres-data zitadel-bootstrap letsencrypt
#   ./migrate-volumes.sh old.example.com --port 6767 --user goaldone \
#       goaldone-prod_postgres-prod-data goaldone-dev_postgres-dev-data
#
# Volume-Namen exakt aus `docker volume ls` auf dem alten VPS übernehmen
# (Compose prefixt sie mit dem Projektnamen).
# ==============================================================================

set -euo pipefail

OLD_HOST=""
SSH_PORT=6767
SSH_USER=goaldone
FORCE=false
VOLUMES=()

# ---- Argumente parsen --------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)  SSH_PORT="$2"; shift 2 ;;
    --user)  SSH_USER="$2"; shift 2 ;;
    --force) FORCE=true; shift ;;
    -h|--help)
      grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*)
      echo "Unbekannte Option: $1" >&2; exit 1 ;;
    *)
      if [[ -z "$OLD_HOST" ]]; then OLD_HOST="$1"; else VOLUMES+=("$1"); fi
      shift ;;
  esac
done

if [[ -z "$OLD_HOST" || ${#VOLUMES[@]} -eq 0 ]]; then
  echo "Usage: $0 <old_host> [--port N] [--user U] [--force] <volume> [<volume> ...]" >&2
  exit 1
fi

SSH=(ssh -p "$SSH_PORT" "${SSH_USER}@${OLD_HOST}")

echo "Quelle:  ${SSH_USER}@${OLD_HOST}:${SSH_PORT}"
echo "Ziel:    $(hostname) (lokal)"
echo "Volumes: ${VOLUMES[*]}"
echo

# ---- SSH-Verbindung prüfen ---------------------------------------------------
if ! "${SSH[@]}" 'docker --version >/dev/null 2>&1'; then
  echo "FEHLER: SSH-Verbindung oder Docker auf dem alten VPS nicht verfügbar." >&2
  exit 1
fi

# ---- Sicherheitshinweis: laufende Container auf dem alten VPS ----------------
RUNNING="$("${SSH[@]}" 'docker ps --format "{{.Names}}" | tr "\n" " "' || true)"
if [[ -n "${RUNNING// /}" ]]; then
  echo "WARNUNG: Auf dem alten VPS laufen noch Container: $RUNNING"
  echo "         Für eine konsistente Postgres-Kopie zuerst dort 'docker compose stop' ausführen."
  read -r -p "Trotzdem fortfahren? (yes/no): " ans
  [[ "$ans" == "yes" ]] || { echo "Abgebrochen."; exit 1; }
fi

# ---- Kopierschleife ----------------------------------------------------------
for VOL in "${VOLUMES[@]}"; do
  echo "──────────────────────────────────────────────────────────────"
  echo "Volume: $VOL"

  # Existiert das Quell-Volume?
  if ! "${SSH[@]}" "docker volume inspect '$VOL' >/dev/null 2>&1"; then
    echo "  FEHLER: Volume '$VOL' existiert auf dem alten VPS nicht. Übersprungen." >&2
    continue
  fi

  # Ziel-Volume: anlegen / Nicht-Leerheit prüfen
  if docker volume inspect "$VOL" >/dev/null 2>&1; then
    NONEMPTY="$(docker run --rm -v "$VOL":/data alpine sh -c 'ls -A /data 2>/dev/null | head -1')"
    if [[ -n "$NONEMPTY" && "$FORCE" != true ]]; then
      echo "  FEHLER: Ziel-Volume '$VOL' ist nicht leer. Mit --force überschreiben." >&2
      continue
    fi
    if [[ -n "$NONEMPTY" && "$FORCE" == true ]]; then
      echo "  --force: leere bestehendes Ziel-Volume..."
      docker run --rm -v "$VOL":/data alpine sh -c 'rm -rf /data/* /data/.[!.]* /data/..?* 2>/dev/null || true'
    fi
  else
    docker volume create "$VOL" >/dev/null
    echo "  Ziel-Volume '$VOL' angelegt."
  fi

  # Stream: alt -> tar -> ssh -> tar -> neu
  echo "  Kopiere..."
  "${SSH[@]}" "docker run --rm -v '$VOL':/data alpine tar czf - -C /data ." \
    | docker run --rm -i -v "$VOL":/data alpine tar xzf - -C /data

  # Größen-Stichprobe
  SRC_SZ="$("${SSH[@]}" "docker run --rm -v '$VOL':/data alpine du -sh /data | cut -f1")"
  DST_SZ="$(docker run --rm -v "$VOL":/data alpine du -sh /data | cut -f1)"
  echo "  ✓ fertig — Quelle: $SRC_SZ / Ziel: $DST_SZ"
done

echo "──────────────────────────────────────────────────────────────"
echo "Alle angegebenen Volumes verarbeitet."
echo "Nächster Schritt: Stacks in korrekter Reihenfolge starten (Zitadel zuerst)."
echo "Siehe infra/MIGRATION.md, Phase 3.4."

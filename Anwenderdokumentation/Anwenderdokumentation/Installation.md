# Installationsanleitung

Diese Anleitung beschreibt die Produktivinstallation von GoalDone auf einem Server mit Ubuntu 24.04 LTS. Der Prozess ist hochgradig automatisiert und nutzt moderne Technologien wie Docker und Terraform, um eine stabile und reproduzierbare Umgebung zu schaffen.

## Inhaltsverzeichnis
1. [Systemvoraussetzungen](#systemvoraussetzungen)
2. [Netzwerk-Voraussetzungen](#netzwerk-voraussetzungen)
3. [Vorbereitung und Zugangsdaten](#vorbereitung-und-zugangsdaten)
4. [Installationskonzept: Das Zwei-Sitzungen-Modell](#installationskonzept-das-zwei-sitzungen-modell)
5. [Schritt 1: Abhängigkeiten installieren (install-deps.sh)](#schritt-1-abhängigkeiten-installieren-install-depssh)
6. [Schritt 2: Deployment (deploy.sh)](#schritt-2-deployment-deploysh)
7. [Verifizierung der Installation](#verifizierung-der-installation)
8. [Fehlerbehebung](#fehlerbehebung)

## Systemvoraussetzungen

Um einen reibungslosen Betrieb von GoalDone (einschließlich der IAM-Lösung Zitadel und der PostgreSQL-Datenbank) zu gewährleisten, müssen folgende Mindestanforderungen erfüllt sein:

### Hardware-Mindestanforderungen
*   **Arbeitsspeicher (RAM):** Mindestens 4 GB (Empfohlen: 8 GB für bessere Performance).
*   **Prozessor (CPU):** Mindestens 2 CPU-Kerne.
*   **Festplattenspeicher:** Mindestens 20 GB freier Speicherplatz (SSD empfohlen).

### Software-Voraussetzungen
*   **Betriebssystem:** Ubuntu 24.04 LTS (Noble Numbat). Andere Distributionen oder Versionen werden offiziell nicht unterstützt.
*   **Benutzerrechte:** Zugriff auf einen Benutzer mit `sudo`-Berechtigungen ist für den ersten Teil der Installation zwingend erforderlich.

## Netzwerk-Voraussetzungen

Eine korrekte Netzwerkkonfiguration ist entscheidend, da GoalDone auf verschlüsselte Verbindungen (HTTPS) und eine saubere Trennung zwischen Anwendung und Identitätsmanagement setzt.

### DNS-Konfiguration
Sie benötigen zwei separate A-Records (Subdomains), die beide auf die IP-Adresse Ihres Servers zeigen:
1.  **Anwendungs-URL:** z.B. `app.ihre-domain.de` (für die Benutzeroberfläche von GoalDone).
2.  **SSO/IAM-URL:** z.B. `sso.ihre-domain.de` (für das Identitätsmanagement-System Zitadel).

**Wichtig:** Stellen Sie sicher, dass die DNS-Einträge propagiert sind (verifizierbar mit `nslookup` oder `dig`), bevor Sie mit der Installation beginnen.

### Firewall / Offene Ports
Die folgenden Ports müssen in der Firewall Ihres Providers (z.B. AWS Security Groups, Hetzner Firewall) sowie lokal auf dem Server freigegeben sein:
*   **Port 22 (SSH):** Für den Fernzugriff auf den Server.
*   **Port 80 (HTTP):** Für die initiale ACME-Validierung (Let's Encrypt).
*   **Port 443 (HTTPS):** Für den verschlüsselten Zugriff auf die Anwendung.

## Vorbereitung und Zugangsdaten

Bevor Sie die Installationsskripte starten, sollten Sie die folgenden Informationen und Zugangsdaten bereithalten. Das Skript `deploy.sh` wird Sie während der Ausführung nach diesen Werten fragen.

### Zugangsdaten-Checkliste

| Parameter                | Beschreibung                                            | Format / Constraint                                                   |
|:-------------------------|:--------------------------------------------------------|:----------------------------------------------------------------------|
| **ZITADEL_URL**          | URL unter der Zitadel später erreichbar ist             | gültige URL (z.B. `https://sso.ihre-domain.de`)                       |
| **APP_URL**              | URL unter der GoalDone-Anwendung später erreichbar ist  | gültige URL (z.B. `https://app.ihre-domain.de`)                       |
| **SMTP_HOST**            | Hostname Ihres Mail-Servers (z.B. `smtp.sendgrid.net`). | Gültiger Hostname.                                                    |
| **SMTP_PORT**            | Port Ihres Mail-Servers (meist 587 oder 465).           | Numerisch (z.B. 587).                                                 |
| **SMTP_USER**            | Benutzername für den E-Mail-Versand.                    | Meist eine E-Mail-Adresse.                                            |
| **SMTP_PASSWORD**        | Passwort für den E-Mail-Server.                         | -                                                                     |
| **SMTP_SENDER**          | Absenderadresse (z.B. `noreply@ihre-domain.de`).        | Gültige E-Mail-Adresse.                                               |
| **SUPER_ADMIN_EMAIL**    | E-Mail-Adresse für den initialen Super-Admin.           | Gültige E-Mail-Adresse.                                               |
| **SUPER_ADMIN_PASSWORD** | Das gewünschte Passwort für den ersten Super-Admin.     | Mindestens 8 Zeichen, inkl. Großbuchstaben, Zahlen und Sonderzeichen. |


## Installationskonzept: Das Zwei-Sitzungen-Modell

Die Installation von GoalDone ist in zwei Phasen unterteilt, da im ersten Schritt alle Tools für die Installation 
(z.B. Docker, Terraform) installiert werden und anschließend der Benutzer Zugriff auf die Docker Gruppe in Linux benötigt. 
Dieser Zugriff kann erst in der zweiten Sitzung nach einem neuen Login auf den Server bereitgestellt werden.
Dieses Konzept wird als **"Zwei-Sitzungen-Modell"** bezeichnet.

1.  **Sitzung 1: System-Abhängigkeiten (`install-deps.sh`)**
    *   **Ziel:** Installation von Docker, Docker Compose, Terraform, `jq` und anderen System-Tools.
    *   **Rechte:** Erfordert `sudo` / Root-Rechte.
    *   **Abschluss:** Am Ende dieses Skripts wird Ihr Benutzer der Gruppe `docker` hinzugefügt. Damit diese Änderung wirksam wird, **müssen Sie sich einmal vom Server abmelden und wieder anmelden**.

2.  **Sitzung 2: Produkt-Deployment (`deploy.sh`)**
    *   **Ziel:** Klonen des GoalDone-Repositories, Generierung der Umgebungsvariablen, Start der Docker-Container und Konfiguration der IAM-Infrastruktur via Terraform.
    *   **Rechte:** Erfolgt als **regulärer Benutzer** (ohne `sudo`).
    *   **Wichtig:** Führen Sie dieses Skript niemals mit `sudo` aus, da dies zu Berechtigungsproblemen bei den generierten Dateien und Docker-Volumes führen kann.

## Schritt 1: Abhängigkeiten installieren (install-deps.sh)

In diesem ersten Schritt wird Ihr Server für den Betrieb von GoalDone vorbereitet. Das Skript installiert alle notwendigen Pakete und konfiguriert grundlegende Sicherheitseinstellungen.

### Start der Installation
Sie können die Installation direkt über einen One-Liner starten, der das Bootstrap-Skript lädt:

```bash
curl -sSL https://projekt.goaldone.de/install.sh | sudo sh
```

*Alternativ, wenn Sie das Repository bereits lokal haben:*
```bash
sudo bash infra/install-deps.sh
```

### Was dieses Skript tut
1.  **Paketquellen aktualisieren:** Stellt sicher, dass das System auf dem neuesten Stand ist.
2.  **Installation von Docker & Docker Compose:** Die Basis für alle Container-Dienste.
3.  **Installation von Terraform:** Wird benötigt, um die Konfiguration von Zitadel automatisiert durchzuführen.
4.  **Hilfswerkzeuge:** Installiert `git`, `jq`, `curl` und andere notwendige CLI-Tools.
5.  **Firewall-Konfiguration (UFW):** 
    *   Aktiviert die Ubuntu-Firewall.
    *   Erlaubt standardmäßig SSH (Port 22), HTTP (Port 80) und HTTPS (Port 443).
6.  **Benutzerrechte:** Fügt Ihren aktuellen Benutzer der Gruppe `docker` hinzu.

### WICHTIGER ABSCHLUSS: Logout & Login
Sobald das Skript erfolgreich abgeschlossen wurde, sehen Sie eine entsprechende Meldung auf der Konsole. 

**Sie müssen sich nun zwingend einmal von Ihrer SSH-Sitzung abmelden (`exit`) und neu anmelden.**

Nur durch die Neuanmeldung werden die neuen Gruppenberechtigungen (Docker-Zugriff ohne Sudo) für Ihren Benutzer aktiv. Ohne diesen Schritt wird das nachfolgende Deployment-Skript fehlschlagen.

## Schritt 2: Deployment (deploy.sh)

Nachdem Sie sich neu angemeldet haben, können Sie mit dem eigentlichen Deployment der GoalDone-Anwendung beginnen. Dieses Skript ist interaktiv und führt Sie durch die Konfiguration.

### Start des Deployments
Führen Sie das Skript als regulärer Benutzer aus (**KEIN sudo**):

```bash
bash infra/deploy.sh
```

### Ablauf des Deployments
Das Skript arbeitet die folgenden Phasen nacheinander ab:

1.  **Repository-Setup:** Falls Sie das Bootstrap-Skript genutzt haben, wird nun das vollständige GoalDone-Repository in ein Unterverzeichnis geklont.
2.  **Konfiguration der Umgebung (`.env`):**
    *   Das Skript fragt Sie nach den URLs (App und SSO), SMTP-Daten und Daten für den ersten Super-Admin Nutzer (siehe [Vorbereitung](#vorbereitung-und-zugangsdaten)).
    *   Diese Werte werden sicher in einer `.env`-Datei im Verzeichnis `infra/infra-setup/` gespeichert.
3.  **Start der Infrastruktur:**
    *   Die Docker-Container für die PostgreSQL-Datenbank und das IAM-System Zitadel werden gestartet.
    *   Ein Health-Check wartet, bis Zitadel vollständig einsatzbereit ist.
4.  **Terraform-Provisionierung:**
    *   Terraform konfiguriert Zitadel (erstellt Projekte, OIDC-Applikationen und setzt SMTP-Einstellungen).
    *   Die notwendigen IDs (Client ID, etc.) werden automatisch für die GoalDone-Anwendung extrahiert.
5.  **Applikations-Deployment:**
    *   Start der eigentlichen GoalDone-Anwendung (Backend und Frontend).
    *   Konfiguration des Traefik Reverse-Proxys für SSL-Zertifikate.

### Fortschrittsspeicherung und Fortsetzung
Das Skript nutzt eine Datei namens `.deploy-state` im aktuellen Verzeichnis, um den Fortschritt zu speichern. Sollte die Installation an einem Punkt abbrechen (z.B. wegen eines Tippfehlers bei den SMTP-Daten), können Sie den Fehler korrigieren und das Skript einfach erneut starten. Es wird an der letzten erfolgreichen Stelle fortgesetzt.

## Verifizierung der Installation

Nachdem das Skript `deploy.sh` erfolgreich abgeschlossen wurde, sollten Sie die Funktionalität Ihrer Installation anhand der folgenden Checkliste überprüfen (INST-06).

### Nach der Installation: Checkliste

1.  **Erreichbarkeit der Anwendung:**
    *   Öffnen Sie Ihren Browser und navigieren Sie zu Ihrer Anwendungs-URL (z.B. `https://app.ihre-domain.de`).
    *   Sie sollten die Login-Seite von GoalDone sehen, die Sie zur Authentifizierung an Zitadel weiterleitet.

2.  **Erster Login als Super-Admin:**
    *   Melden Sie sich mit der E-Mail-Adresse und Passwort an, die Sie als `SUPER_ADMIN_EMAIL` und `SUPER_ADMIN_PASSWORD` angegeben haben.
    *   Sie werden beim ersten Login eventuell aufgefordert, Ihr Passwort zu ändern.

3.  **Erstellung der ersten Organisation (D-08):**
    *   Nach dem Login sollten Sie Zugriff auf die Anwendung als Superadmin haben.
    *   Navigieren Sie zu den Superadmineinstellungen und erstellen Sie eine erste Organisation. Dieser Schritt verifiziert die korrekte Anbindung der Datenbank und des Backends.

4.  **Prüfung der Container-Status:**
    *   Führen Sie auf der Server-Konsole den folgenden Befehl aus:
        ```bash
        docker ps
        ```
    *   Alle relevanten Container (`goaldone-backend`, `goaldone-frontend`, `traefik`, `zitadel`, `db`) sollten den Status `Up` (healthy) aufweisen.

5.  **Logs kontrollieren:**
    *   Sollten Komponenten nicht wie erwartet funktionieren, prüfen Sie die Logs der Applikation:
        ```bash
        docker compose -f infra/infra-setup/docker-compose.yml logs -f backend
        ```

## Fehlerbehebung

Sollte die Installation nicht reibungslos verlaufen, finden Sie hier Lösungsansätze für häufige Probleme.

### Log-Dateien prüfen
Die Installationsskripte protokollieren detaillierte Informationen in Log-Dateien im aktuellen Verzeichnis. Prüfen Sie diese bei Fehlern:
*   `install-deps.log`: Protokoll der Abhängigkeitsinstallation (Stufe 1).
*   `deploy.log`: Detailliertes Protokoll des Deployments (Stufe 2).

Nutzen Sie `grep`, um gezielt nach Fehlern zu suchen:
```bash
grep -i "error" deploy.log
```

### Häufige Probleme

1.  **DNS-Propagierung (Problem 1):**
    *   **Symptom:** Let's Encrypt Zertifikate können nicht erstellt werden oder der Health-Check für Zitadel schlägt fehl.
    *   **Ursache:** Die DNS-Einträge für Ihre Subdomains sind noch nicht weltweit bekannt.
    *   **Lösung:** Warten Sie einige Zeit und prüfen Sie die Erreichbarkeit mit `dig app.ihre-domain.de`.

2.  **Externe Firewall (Problem 3):**
    *   **Symptom:** Die Installation läuft ohne Fehler durch, aber die URLs sind im Browser nicht erreichbar (Timeout).
    *   **Ursache:** Die Ports 80 und 443 sind in der Firewall Ihres Cloud-Providers (nicht auf dem Server selbst) gesperrt.
    *   **Lösung:** Öffnen Sie die Ports 80 (HTTP) und 443 (HTTPS) in der Web-Konsole Ihres Providers.


**Strategie bei Fehlern:**
1. Analysieren Sie den Fehler in `deploy.log`.
2. Beheben Sie die Ursache (z.B. korrekte SMTP-Daten in der `.env` eintragen oder Firewall öffnen).
3. Starten Sie das Skript `bash infra/deploy.sh` erneut. Es wird automatisch an der Stelle fortgesetzt, an der es zuvor abgebrochen ist.

---
[Zurück zur Übersicht (README.md)](README.md)


# Organisationseinstellungen

In den Organisationseinstellungen können Administratoren die Mitglieder ihrer Organisation verwalten, Rollen zuweisen und neue Personen einladen.

## Mitgliederverwaltung

Die Mitgliederliste bietet einen zentralen Überblick über alle Personen, die aktuell Zugriff auf die Organisation haben.

### Rollen und Berechtigungen

GoalDone unterscheidet zwischen zwei Hauptrollen:

*   **Admin (Administrator):** Hat vollen Zugriff auf alle Funktionen der Organisation. Zusätzlich zur normalen Nutzung kann ein Administrator Mitglieder verwalten, Rollen ändern, Personen einladen oder aus der Organisation entfernen.
*   **Benutzer:** Kann alle produktiven Funktionen von GoalDone nutzen (Aufgaben erstellen, Arbeitszeiten verwalten, Kalender einsehen), hat jedoch keinen Zugriff auf die Organisationseinstellungen.

### Mitgliederliste und Aktionen

In der Mitgliederliste sehen Sie den Namen, die E-Mail-Adresse und die aktuelle Rolle jedes Mitglieds. Als Administrator können Sie folgende Aktionen durchführen:

*   **Rolle ändern:** Sie können einen Benutzer zum Administrator befördern oder einem Administrator die Admin-Rechte entziehen (sofern er nicht der letzte Administrator ist).
*   **Mitglied entfernen:** Sie können Personen aus der Organisation entfernen. Diese verlieren damit sofort den Zugriff auf alle Daten dieser Organisation.

> [!TIP]
> **Screenshot Platzhalter:** Hier wird die Mitgliederliste mit den verfügbaren Aktionen angezeigt.

## Wechseln zwischen Organisationen

Wenn Sie Administrator in mehreren Organisationen sind, können Sie innerhalb der Organisationseinstellungen zwischen diesen wechseln. Dies ermöglicht es Ihnen, die Mitgliederverwaltung für jede Organisation separat vorzunehmen, ohne sich neu anmelden zu müssen.

## Einladungsprozess

Neue Mitglieder können einfach per E-Mail in die Organisation eingeladen werden.

1.  **Einladung versenden:** Geben Sie in den Organisationseinstellungen die E-Mail-Adresse der Person ein, die Sie einladen möchten.
2.  **E-Mail-Empfang:** Der Empfänger erhält eine automatisierte E-Mail mit einem Einladungslink.
3.  **Registrierung/Login:**
    *   Falls die Person bereits ein GoalDone-Konto hat, muss sie sich lediglich einloggen, um der Organisation beizutreten.
    *   Falls noch kein Konto existiert, wird die Person durch den Registrierungsprozess geführt. Nach Abschluss der Registrierung ist sie automatisch Mitglied Ihrer Organisation.

## Sicherheitsregeln und Einschränkungen

Um die Integrität und Sicherheit Ihrer Organisation zu gewährleisten, gelten folgende Regeln:

### Last-Admin-Regel (Sicherung des Administrator-Zugriffs)

Eine Organisation muss immer mindestens einen Administrator haben. Das System verhindert daher aktiv Aktionen, die dazu führen würden, dass eine Organisation "admin-los" wird:
*   Der letzte Administrator einer Organisation kann **nicht gelöscht** werden.
*   Dem letzten Administrator kann die **Admin-Rolle nicht entzogen** werden.

Wenn Sie die Organisation verlassen oder Ihre Rolle abgeben möchten, müssen Sie zuerst ein anderes Mitglied zum Administrator befördern.

### Schutz vor Selbstlöschung

Als Administrator können Sie sich nicht selbst aus der Mitgliederliste löschen. Dies dient als Schutzmechanismus, um versehentlichen Aussperrungen vorzubeugen.

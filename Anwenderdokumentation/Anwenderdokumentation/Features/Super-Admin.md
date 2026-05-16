# Super-Admin

## Überblick
Die Super-Admin-Oberfläche dient der zentralen Verwaltung der gesamten GoalDone-Plattform. Sie ist ausschließlich für System-Administratoren zugänglich und ermöglicht die Steuerung aller Organisationen sowie die Verwaltung anderer Super-Admins.

## Organisationsverwaltung
In diesem Bereich haben Super-Admins Einblick in alle auf der Plattform registrierten Unternehmen und Organisationen.

### Organisation anlegen
Super-Admins können neue Organisationen direkt im System erstellen. Beim Anlegen müssen folgende Informationen angegeben werden:
* **Name des Unternehmens:** Die offizielle Bezeichnung der Organisation (mindestens 2, maximal 255 Zeichen).
* **Admin-Vorname:** Der Vorname des ersten Administrators der neuen Organisation.
* **Admin-Nachname:** Der Nachname des ersten Administrators.
* **Admin-E-Mail:** Die E-Mail-Adresse, an die die Zugangsdaten und die Einladung gesendet werden.

Nach der Erstellung wird automatisch ein Administrator-Konto für die angegebene E-Mail-Adresse vorbereitet.

### Organisation löschen
Sollte eine Organisation nicht mehr benötigt werden, kann sie durch einen Super-Admin gelöscht werden. 
* **Bestätigungszwang:** Das Löschen muss explizit bestätigt werden, um versehentliche Datenverluste zu vermeiden.
* **Warnhinweis:** Durch das Löschen einer Organisation werden **alle** zugehörigen Daten (Benutzerverknüpfungen, Aufgaben, Arbeitszeiten, Pläne) unwiderruflich entfernt.

## Super-Admin-Verwaltung
Super-Admins können den Zugriff auf die Verwaltungsplattform steuern.

### Super-Admins einladen
Weitere Personen können zur Unterstützung der Plattformverwaltung eingeladen werden. Dies erfolgt durch Angabe der E-Mail-Adresse. Der Eingeladene erhält eine Benachrichtigung und kann nach der Anmeldung auf die Super-Admin-Funktionen zugreifen.

### Super-Admins entfernen
Berechtigungen für Super-Admins können jederzeit entzogen werden. 
* **Last-Admin-Regel:** Zum Schutz des Systems muss immer mindestens ein Super-Admin existieren. Das System verhindert das Entfernen des letzten verbleibenden Super-Admins, um eine dauerhafte Aussperrung aus der Plattformverwaltung zu verhindern.

## Querverweise
* [Organisationseinstellungen](Organisationseinstellungen.md)
* [Glossar](../Glossar.md)

[Zurück zur Übersicht](../README.md)

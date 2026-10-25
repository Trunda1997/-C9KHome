# C9K Home

Prototype eines alternativen Android-/Google-TV-Launchers für TCL C9K.

## Enthalten
- TV-Launcher / HOME-Activity für Fernbedienungsbedienung
- automatische Auflistung von HDMI-Eingängen über `TvInputManager`
- Live-Aktualisierung bei Verbinden/Trennen/Standby
- automatische Logo-Zuordnung anhand der vom TV gemeldeten Labels
- Logo-Bibliothek für PlayStation/PS5, Xbox, Nintendo/Switch 2, Sonos, Apple TV, Fire TV, Chromecast, NVIDIA Shield, Roku, Audio- und AV-Hersteller u. a.
- Langdruck auf eine HDMI-Kachel: manuelle Logo-Zuordnung oder zurück auf Automatik
- direkter Versuch, HDMI-Passthrough-Eingänge zu öffnen
- dynamische Reihe installierter Android-TV-Apps
- direkter Start des originalen Google Play Store
- direkter Start der originalen Android/TCL-Systemeinstellungen
- keine Ersetzung von Bild-/Ton-/CEC-/Update-Systemkomponenten

## Wichtiger Hinweis zur HDMI-Geräteerkennung
Eine normale Drittanbieter-App kann nur die Informationen verwenden, die TCL/Android über das öffentliche TV Input Framework meldet. Wenn der TV einen CEC-Namen wie „PlayStation 5“ oder „Apple TV“ als Input-Label bereitstellt, wird das Logo automatisch erkannt. Meldet der TV nur „HDMI 1“, ist keine sichere Herstellererkennung möglich. Dafür ist die gespeicherte Langdruck-Zuordnung eingebaut.

## Build
1. Projekt in Android Studio öffnen.
2. Android SDK 36 installieren, falls Android Studio danach fragt.
3. `Build > Build APK(s)`.
4. APK per USB/ADB auf dem C9K installieren.

## Installation per ADB
```bash
adb connect TV-IP:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Danach HOME drücken und **C9K Home** auswählen. Falls der Launcher-Auswahldialog auf der TCL-Firmware nicht erscheint, kann je nach Firmware zusätzlich ADB nötig sein:

```bash
adb shell cmd package set-home-activity de.tcl.c9khome/.MainActivity
```

Zum sicheren Rückweg kann die App wieder entfernt werden:

```bash
adb uninstall de.tcl.c9khome
```

## TCL-Systemfunktionen
Der Launcher fängt die TCL-Einstellungs-/Quick-Settings-Taste nicht ab. Das Zahnrad im Launcher startet `Settings.ACTION_SETTINGS`; der Play-Store-Eintrag startet weiterhin `com.android.vending`.

## Marken
Die genannten Marken und Produktnamen gehören den jeweiligen Rechteinhabern. Die HDMI-Kacheln verwenden im Prototyp typografische Markenbezeichnungen statt eingebetteter offizieller Logo-Dateien.

## APK ohne lokale Entwicklungsumgebung bauen
Eine GitHub-Actions-Datei ist bereits unter `.github/workflows/build-apk.yml` enthalten. Nach dem Hochladen in ein GitHub-Repository kann der Workflow **Build C9K Home APK** manuell gestartet werden; das Debug-APK wird anschließend als Build-Artefakt bereitgestellt.


## TCL Home Test V0.5

Die Oberfläche ist in eigenständige Java-Screens und Repositories aufgeteilt. Die obere Leiste führt durch Suche, Startseite, Live TV, Filme, Serien und Apps; das Zahnrad öffnet die originalen Systemeinstellungen.

Die Startseite behält die geordneten V0.4-Favoriten in `home_favorites/packages`. Langes OK öffnet Hinzufügen/Entfernen bzw. Verschieben; im Sortiermodus speichert OK und Zurück verwirft die Änderung. Die HDMI-Erkennung, Marken-Zuordnung und Umschaltung verwenden weiterhin dieselben Hilfsklassen.

Medien- und Personensuche verwendet ausschließlich gelieferte Mediendatensätze. App-Suche und echte Google-Play-Verknüpfungen liegen getrennt im Apps-Bereich. Die App-Verzeichnisse sind benannte Store-Verknüpfungen, keine personalisierten Empfehlungen.

`EntertainmentAppRepository` erkennt installierte Anbieter über Paket-Mappings, Android-Kategorien, Metadaten, TV-Intents und zugängliche TV-Providerdaten. Paketänderungen aktualisieren die Ansicht und entfernen Anbieter-Caches. `MediaRepository.MediaSource` und `LiveTvRepository.LiveSource` sind die Anschlussstellen für spätere echte APIs und Konten. Ohne Berechtigung oder Datenquelle werden Leerzustände angezeigt. Netflix-/Disney+-/Prime-/YouTube-Konten sind derzeit nicht angebunden.

Provider-Abfragen und Bilder laufen im Hintergrund. Der Mediencache ist auf 120 Datensätze, Live-Daten auf 100 Einträge und der Bildcache auf 6 MiB begrenzt. Fehlende Daten erzeugen keine erfundenen Titel, Episoden oder Ranglisten.

Die Workflow-Dateien bauen `TCL Home Test V0.5.apk` und führen Android-TV-Tests mit D-Pad/OK/Zurück aus. Ein physischer TCL-C9K-/PS5-Test ist zusätzlich erforderlich.

Hinweis zur Signierung: Die bisherige Actions-Pipeline verwendet den Standard-Debug-Keystore des jeweiligen Runners. `applicationId` bleibt `de.tcl.c9khome`; eine identische Update-Signatur ist aber nur mit dem ursprünglichen Keystore gesichert. Dieser wird nicht im Repository gespeichert.

# Prüf- und Implementierungsplan: Aufschlaganzeige nach FIP-Regeln

## Ziel

Die Aufschlaganzeige soll für jeden Punkt eindeutig zeigen:

- welches Team aufschlägt
- welcher der vier Spieler aufschlägt
- ob von rechts oder links aufgeschlagen wird
- ob beim Golden Point oder Star Point die Rückschlagseite noch gewählt werden muss

Die Logik muss reguläre Games, Satz-Tie-Breaks und Match-Tie-Breaks regelkonform abbilden. Mixed-spezifische Regeln werden vorerst nicht umgesetzt.

## Regelgrundlage

Primärquelle ist `FIP_Rules-of-Padel.pdf` im Root-Verzeichnis.

Relevante Abschnitte:

- Regel 1: Score in a Game
- Abschnitt Tie-Break
- Alternative Score Methods
- Regel 4: Choice of Sides and Service
- Regel 5: Changes of Sides
- Regel 6: The Serve
- Regel 8: Return of Serve

## Relevante FIP-Regeln

### Aufschlagseite im regulären Game

Nach FIP-Regel 6 Absatz 5:

- Der erste Punkt eines Games wird von rechts aufgeschlagen.
- Nach jedem ausgespielten Punkt wechselt die Aufschlagseite.
- Die Folge lautet rechts, links, rechts, links.
- Ein neues Game beginnt wieder rechts.

Diese Regel gilt unabhängig von Einstand oder Vorteil. Entscheidend ist die Anzahl der bereits ausgespielten Punkte im aktuellen Game.

### Reihenfolge der Aufschläger

Nach FIP-Regel 6 Absatz 8:

- Vor jedem Satz bestimmt jedes Team, welcher seiner beiden Spieler zuerst aufschlägt.
- Die so festgelegte Reihenfolge darf während des Satzes nicht geändert werden.
- Wenn ein Team erneut mit dem Aufschlag an der Reihe ist, schlägt dessen anderer Spieler auf.

Beispiel:

1. Ich
2. Gegner 1
3. Partner
4. Gegner 2
5. Ich
6. Gegner 1

Die Reihenfolge hängt davon ab, welches Team und welcher Spieler den Satz beginnt.

Vor einem neuen Satz dürfen beide Teams ihre interne Reihenfolge neu festlegen.

### Tie-Break

Der FIP-Abschnitt zum Tie-Break schreibt vor:

1. Der nach der bisherigen Satzreihenfolge fällige Spieler schlägt den ersten Punkt auf.
2. Dieser erste Punkt wird von rechts aufgeschlagen.
3. Danach schlägt der nächste Spieler des anderen Teams zwei Punkte auf, zuerst von links und danach von rechts.
4. Anschließend schlägt der nächste Spieler in der bestehenden Reihenfolge zwei Punkte auf.
5. Die vier Spieler behalten die für den Satz festgelegte Reihenfolge.
6. Das Muster läuft bis zum Ende als 1-2-2 weiter.
7. Nach jeweils sechs Tie-Break-Punkten werden die Spielfeldseiten gewechselt. Die Aufschlagreihenfolge ändert sich dadurch nicht.
8. Im folgenden Satz schlägt das Team zuerst auf, das den Tie-Break nicht mit dem Aufschlag begonnen hat.

Beispiel bei Tie-Break-Start mit `Ich`:

| Bereits gespielte Punkte | Nächster Aufschläger | Seite |
|---:|---|---|
| 0 | Ich | rechts |
| 1 | Gegner 1 | links |
| 2 | Gegner 1 | rechts |
| 3 | Partner | links |
| 4 | Partner | rechts |
| 5 | Gegner 2 | links |
| 6 | Gegner 2 | rechts |
| 7 | Ich | links |
| 8 | Ich | rechts |
| 9 | Gegner 1 | links |

Damit wechseln sich im Tie-Break nicht nur die Teams ab. Die bestehende Reihenfolge aller vier Spieler muss fortgeführt werden.

### Match-Tie-Break und Super-Tie-Break

Die FIP-Regeln beschreiben:

- Match-Tie-Break bis sieben Punkte
- Super-Tie-Break bis zehn Punkte
- jeweils mit mindestens zwei Punkten Vorsprung

Die App verwendet für `TWO_SETS_AND_MATCH_TIE_BREAK` standardmäßig zehn Punkte. Das entspricht dem beschriebenen Super-Tie-Break.

Für die Aufschlagfolge gilt dieselbe 1-2-2-Spielerrotation wie beim Satz-Tie-Break.

### Golden Point

Bei 40:40:

- Das rückschlagende Team wählt die Rückschlagseite.
- Der Aufschlag erfolgt entsprechend von rechts oder links.
- Die Rückschläger dürfen ihre festgelegten Positionen nicht tauschen.
- Der Gewinner des Punkts gewinnt das Game.

Die Aufschlagseite kann deshalb nicht automatisch aus `pointsInCurrentGame` abgeleitet werden.

### Star Point

Nach `deuce 1`, `advantage 1`, `deuce 2`, `advantage 2` und der Rückkehr zu `deuce 3`:

- Das rückschlagende Team wählt die Rückschlagseite.
- Der Aufschlag erfolgt entsprechend von rechts oder links.
- Der Gewinner des Star Point gewinnt das Game.

Auch hier darf die App nicht automatisch eine Seite aus der Punktparität ableiten.

### Mixed

Die FIP-Regeln enthalten bei Golden Point und Star Point zusätzliche Vorgaben für Mixed-Paarungen. Diese werden in dieser Änderung nicht umgesetzt:

- keine Erfassung des Geschlechts
- keine geschlechtsabhängige Bestimmung des Rückschlägers
- keine entsprechende Behauptung in UI oder Dokumentation

## Analyse der aktuellen Implementierung

### Korrekte Teile

Die aktuelle Implementierung ist auf Teamebene in folgenden Punkten korrekt:

- `MatchState.servingTeam` bestimmt das aufschlagende Team.
- Nach einem regulären Game wechselt `PadelScoreEngine` das Team.
- `ServeCalculator.isDeuceCourtServe(pointsInCurrentGame)` ergibt bei geraden Werten rechts und bei ungeraden Werten links.
- `pointsInCurrentGame` wird nach einem Game zurückgesetzt.
- Im Tie-Break läuft `pointsInCurrentGame` weiter und erzeugt dadurch rechts, links, rechts, links.
- `servingTeamForNextTieBreakPoint()` bildet die Teamfolge 1-2-2 korrekt ab.
- `servingTeamForNextSetAfterTieBreak()` setzt für den nächsten Satz das andere Team als das Tie-Break-Startteam.
- `MatchScoreComponents.kt` spiegelt die Position für das oben dargestellte Gegnerteam.

### Fehlende oder falsche Teile

1. `Team.US` und `Team.OPPONENT` unterscheiden nicht zwischen den beiden Spielern eines Teams.
2. Die App kann deshalb die vorgeschriebene Reihenfolge aller vier Aufschläger nicht anzeigen oder prüfen.
3. Im Tie-Break wird nur die Teamfolge berechnet, nicht die konkrete Spielerfolge.
4. Beim Golden Point wird automatisch eine Seite angezeigt, obwohl das Rückschlagteam wählen darf.
5. Beim Star Point wird ebenfalls automatisch eine Seite angezeigt.
6. Vor einem neuen Satz gibt es keine Möglichkeit, die nach FIP-Regel 6 zulässige neue teaminterne Reihenfolge festzulegen.
7. Die Ambient-Anzeige kennzeichnet nur das Team.
8. Gespeicherte Matchzustände enthalten keine konkrete Spielerrotation.

## Festgelegte Spielerbezeichnungen

Die erste Umsetzung verwendet feste neutrale Bezeichnungen:

- `Ich`
- `Partner`
- `Gegner 1`
- `Gegner 2`

Vorteile:

- keine Texteingabe auf der Uhr nötig
- sofort verständliche Zuordnung
- stabil für Persistenz und Tests
- später um frei gewählte Namen erweiterbar

Technische Identitäten dürfen nicht aus lokalisierten Texten abgeleitet werden.

## Implementierungsplan

### 1. Spielermodell einführen

Neue Datei:

`app/src/main/java/ch/hygro/padelscore/model/PlayerSlot.kt`

Vorgeschlagenes Modell:

```kotlin
enum class PlayerSlot(val team: Team) {
    US_1(Team.US),
    US_2(Team.US),
    OPPONENT_1(Team.OPPONENT),
    OPPONENT_2(Team.OPPONENT);

    fun teammate(): PlayerSlot
}
```

Darstellung:

| Technischer Wert | Anzeige |
|---|---|
| `US_1` | Ich |
| `US_2` | Partner |
| `OPPONENT_1` | Gegner 1 |
| `OPPONENT_2` | Gegner 2 |

`PlayerSlot` muss die Zugehörigkeit zu `Team` sowie den jeweiligen Teampartner eindeutig liefern.

### 2. Aufschlagseite modellieren

Neue Datei:

`app/src/main/java/ch/hygro/padelscore/model/ServiceCourt.kt`

```kotlin
enum class ServiceCourt {
    DEUCE,
    ADVANTAGE,
    RECEIVER_CHOICE
}
```

Bedeutung:

- `DEUCE`: Aufschlag von rechts
- `ADVANTAGE`: Aufschlag von links
- `RECEIVER_CHOICE`: Rückschlagteam muss bei Golden Point oder Star Point wählen

### 3. Vollständige Aufschlagzuordnung berechnen

Datei:

`app/src/main/java/ch/hygro/padelscore/logic/ServeCalculator.kt`

Neuer Ergebniswert:

```kotlin
data class ServiceAssignment(
    val player: PlayerSlot,
    val court: ServiceCourt
)
```

Geplante Methoden:

```kotlin
fun serviceAssignment(state: MatchState): ServiceAssignment

fun servingPlayerForNextTieBreakPoint(
    firstServingPlayer: PlayerSlot,
    firstServerForUs: PlayerSlot,
    firstServerForOpponent: PlayerSlot,
    pointsPlayed: Int
): PlayerSlot
```

Die bestehende Methode `servingTeamForNextTieBreakPoint()` wird durch die spielerbezogene Berechnung ersetzt oder nur noch als abgeleitete Kompatibilitätsfunktion verwendet.

Regeln der Berechnung:

- Reguläres Game verwendet den im Zustand gespeicherten konkreten Spieler.
- Tie-Break verwendet den ersten Tie-Break-Aufschläger und die bestehende Reihenfolge aller vier Spieler.
- Gerade Anzahl gespielter Punkte ergibt rechts.
- Ungerade Anzahl ergibt links.
- Bei aktivem Golden Point oder Star Point ohne Auswahl ergibt sich `RECEIVER_CHOICE`.
- Eine gespeicherte Entscheidungspunktseite hat Vorrang.
- Negative Punktzahlen werden abgewiesen.

### 4. Matchkonfiguration erweitern

Datei:

`app/src/main/java/ch/hygro/padelscore/model/MatchConfiguration.kt`

Benötigte Konfiguration:

```kotlin
val firstServingPlayer: PlayerSlot
val firstServerForUs: PlayerSlot
val firstServerForOpponent: PlayerSlot
```

Invarianten:

- `firstServingPlayer` muss zu `firstServingTeam` gehören, solange beide Felder während der Migration existieren.
- `firstServerForUs.team` muss `Team.US` sein.
- `firstServerForOpponent.team` muss `Team.OPPONENT` sein.

Langfristig soll `firstServingTeam` aus `firstServingPlayer.team` abgeleitet werden. Für die rückwärtskompatible Migration kann das bestehende Feld zunächst erhalten bleiben.

### 5. Matchzustand erweitern

Datei:

`app/src/main/java/ch/hygro/padelscore/model/MatchState.kt`

Benötigte Felder:

```kotlin
val servingPlayer: PlayerSlot
val nextServerForUs: PlayerSlot
val nextServerForOpponent: PlayerSlot
val tieBreakFirstServingPlayer: PlayerSlot? = null
val setOpeningServersConfirmed: Boolean = true
val selectedDecidingPointCourt: ServiceCourt? = null
```

Bedeutung:

- `servingPlayer` ist die eindeutige Quelle für die aktuelle Anzeige.
- `nextServerForUs` ist der nächste eigene Aufschläger.
- `nextServerForOpponent` ist der nächste gegnerische Aufschläger.
- `tieBreakFirstServingPlayer` hält den konkreten Tie-Break-Starter fest.
- `setOpeningServersConfirmed` verhindert Punkteingaben vor Bestätigung der neuen Satzreihenfolge.
- `selectedDecidingPointCourt` speichert die Wahl bei Golden Point oder Star Point.

Invarianten:

- `servingPlayer.team == servingTeam`, solange `servingTeam` noch gespeichert wird
- `nextServerForUs.team == Team.US`
- `nextServerForOpponent.team == Team.OPPONENT`
- `selectedDecidingPointCourt` darf nur `DEUCE`, `ADVANTAGE` oder `null` sein

### 6. Startkonfiguration erweitern

Datei:

`app/src/main/java/ch/hygro/padelscore/presentation/StartScreen.kt`

Die bisherige Auswahl `ERSTER AUFSCHLAG: WIR/GEGNER` wird erweitert um:

- erstes Aufschlagteam
- erster eigener Aufschläger: Ich oder Partner
- erster gegnerischer Aufschläger: Gegner 1 oder Gegner 2

Die Auswahl muss kompakt bleiben. Geeignet sind drei nacheinander antippbare Konfigurationsfelder.

### 7. Reihenfolge vor jedem neuen Satz bestätigen

Neue Datei:

`app/src/main/java/ch/hygro/padelscore/presentation/SetServerOrderScreen.kt`

Anbindung:

`app/src/main/java/ch/hygro/padelscore/presentation/MatchScreen.kt`

Vor dem ersten Punkt jedes weiteren Satzes:

- zeigt die App das nach der bisherigen Rotation fällige erste Aufschlagteam
- lässt sie für beide Teams den ersten Aufschläger des neuen Satzes wählen
- sperrt sie die Punkteingabe bis zur Bestätigung

Das zuerst aufschlagende Team darf nicht beliebig geändert werden. Nach einem Tie-Break muss es das Team sein, das den Tie-Break nicht begonnen hat.

Die Spielerwahl innerhalb der Teams ist dagegen vor jedem Satz neu möglich.

### 8. Scoring Engine auf konkrete Spieler umstellen

Datei:

`app/src/main/java/ch/hygro/padelscore/logic/PadelScoreEngine.kt`

Reguläres Game:

1. Das Game endet.
2. Das andere Team kommt an die Reihe.
3. `servingPlayer` wird auf dessen vorgemerkten Spieler gesetzt.
4. Für das Team, das gerade aufgeschlagen hat, wird der nächste Spieler auf den Teampartner gesetzt.
5. `pointsInCurrentGame` wird auf null gesetzt.

Satz-Tie-Break:

1. Der nach der normalen Reihenfolge fällige Spieler wird `tieBreakFirstServingPlayer`.
2. Der erste Punkt bleibt bei diesem Spieler.
3. Danach werden die Spieler in der 1-2-2-Sequenz berechnet.
4. Die etablierte Reihenfolge aller vier Spieler bleibt erhalten.
5. Nach Ende des Tie-Breaks wird für den nächsten Satz das andere Team als erstes Aufschlagteam vorgemerkt.
6. Die Spielerwahl wird vor dem ersten Punkt des neuen Satzes bestätigt.

Match-Tie-Break:

- verwendet dieselbe Spielerfolge
- endet bei der konfigurierten Zielpunktzahl und zwei Punkten Vorsprung
- benötigt keinen folgenden Satz

Golden Point und Star Point:

- beim Aktivieren wird `selectedDecidingPointCourt` auf `null` gesetzt
- vor dem Punkt muss die Rückschlagseite gewählt werden
- nach dem Game wird die Auswahl zurückgesetzt
- die Wahl beeinflusst nur die Anzeige, nicht die Punktewertung

### 9. Gelben Punkt um Spielerkennung erweitern

Dateien:

- `app/src/main/java/ch/hygro/padelscore/presentation/MatchScreen.kt`
- `app/src/main/java/ch/hygro/padelscore/presentation/MatchScoreComponents.kt`

Der Indikator vermittelt drei Informationen:

1. Team durch die Teamfläche
2. Spieler durch eine Kurzkennung
3. Aufschlagseite durch die Position

Kurzkennungen:

| Spieler | Kennung |
|---|---|
| Ich | I |
| Partner | P |
| Gegner 1 | 1 |
| Gegner 2 | 2 |

Darstellung:

- Der Punkt erscheint nur in der Fläche des aufschlagenden Teams.
- Seine Position zeigt rechts oder links.
- Im Punkt steht die Spielerkennung.
- Für das obere Gegnerteam bleibt die bestehende visuelle Spiegelung erhalten.
- Zusätzlich zeigt der Center-Bereich bei Bedarf `Aufschlag: Partner` oder `Aufschlag: Gegner 2`.
- Die Bedeutung darf nicht ausschließlich über Farbe vermittelt werden.
- Die Accessibility-Beschreibung lautet beispielsweise `Partner schlägt von rechts auf`.

### 10. Entscheidungspunktseite auswählen

Dateien:

- `app/src/main/java/ch/hygro/padelscore/presentation/MatchScreen.kt`
- `app/src/main/java/ch/hygro/padelscore/presentation/MatchScoreComponents.kt`

Bei `RECEIVER_CHOICE`:

- werden beide möglichen Punktpositionen angezeigt
- beide tragen dieselbe Spielerkennung
- die Rückschlagseite kann über eigene Touch-Ziele ausgewählt werden
- nach der Auswahl wird nur die gewählte Position vollständig hervorgehoben
- ein Tippen auf die Auswahl darf keinen Spielpunkt vergeben
- die normale Punkteingabe bleibt gesperrt, bis eine Seite gewählt wurde

Die Auswahl liegt fachlich beim rückschlagenden Team, unabhängig davon, wer die Uhr bedient.

### 11. Manuelle Aufschlägerkorrektur ermöglichen

Neue Datei:

`app/src/main/java/ch/hygro/padelscore/presentation/ServerCorrectionDialog.kt`

Anbindung:

`app/src/main/java/ch/hygro/padelscore/presentation/MatchScreen.kt`

Ein langer Druck auf den gelben Punkt öffnet eine Korrektur.

Angeboten werden nur die beiden Spieler des bereits feststehenden Aufschlagteams.

Die Korrektur:

- verändert keinen Punktestand
- verändert nicht das Aufschlagteam
- setzt den nächsten Aufschläger dieses Teams konsistent auf den Teampartner
- wird sofort gespeichert
- erlaubt die Behebung einer falsch eingegebenen Satzreihenfolge

Die App kann nicht erkennen, wer real auf dem Platz aufschlägt. Sie kann nur die geplante Reihenfolge anzeigen und eine Korrektur anbieten.

### 12. Ambient-Anzeige angleichen

Datei:

`app/src/main/java/ch/hygro/padelscore/presentation/AmbientMatchScreen.kt`

Die Ambient-Anzeige soll mindestens zeigen:

- aufschlagendes Team
- kurze Spielerkennung

Falls genügend Platz vorhanden ist, soll auch die Seite sichtbar sein. Die Logik darf nicht separat berechnet werden, sondern verwendet dasselbe `ServiceAssignment` wie die aktive Anzeige.

### 13. Persistenz rückwärtskompatibel erweitern

Relevante Dateien:

- `app/src/main/java/ch/hygro/padelscore/storage/MatchJsonCodec.kt`
- `app/src/main/java/ch/hygro/padelscore/storage/MatchRepository.kt`
- `app/src/main/java/ch/hygro/padelscore/storage/MatchHistoryRepository.kt`
- `app/src/test/java/ch/hygro/padelscore/storage/MatchCodecTest.kt`

Zu speichern sind:

- Spielerfelder der `MatchConfiguration`
- `servingPlayer`
- `nextServerForUs`
- `nextServerForOpponent`
- `tieBreakFirstServingPlayer`
- `setOpeningServersConfirmed`
- `selectedDecidingPointCourt`
- dieselben Felder in gespeicherten Undo-Zuständen

`MatchJsonCodec` serialisiert aktuell nur gemeinsame Konfigurationen und Satzresultate. Deshalb müssen zusätzlich die tatsächlichen Encoder und Decoder für vollständige Matchzustände in den Repositories geprüft und angepasst werden.

Migration alter Daten:

- Fehlen Spielerfelder, wird nicht stillschweigend eine möglicherweise falsche Identität behauptet.
- Vor dem nächsten Punkt erscheint die Bestätigung der Spielerreihenfolge.
- Als Vorschlag wird Spieler 1 des gespeicherten Aufschlagteams verwendet.
- Ungültige Enum-Werte führen ebenfalls zur Bestätigung.
- Alte Matchdaten und Undo-Zustände bleiben ladbar.
- Historische Matches müssen nur erweitert werden, wenn der konkrete Aufschläger später dort dargestellt werden soll.

### 14. Lokalisierung ergänzen

Dateien:

- `app/src/main/res/values/strings.xml`
- alle vorhandenen lokalisierten `strings.xml`

Neue Texte umfassen mindestens:

- Ich
- Partner
- Gegner 1
- Gegner 2
- erster eigener Aufschläger
- erster gegnerischer Aufschläger
- Aufschlagreihenfolge bestätigen
- Aufschläger korrigieren
- Rückschlagseite wählen
- Aufschlag von rechts
- Aufschlag von links

Es dürfen keine fest eingebauten deutschen UI-Texte in Kotlin-Dateien eingeführt werden.

### 15. Logiktests vervollständigen

Datei:

`app/src/test/java/ch/hygro/padelscore/logic/ServeCalculatorTest.kt`

Reguläre Aufschlagseite:

- 0 Punkte ergibt rechts
- 1 Punkt ergibt links
- 2 Punkte ergibt rechts
- 3 Punkte ergibt links
- Einstand und Vorteil setzen die Folge fort
- neues Game beginnt wieder rechts

Reguläre Spielerrotation:

- Start mit jedem der vier möglichen Spieler
- Folge mit eigenem Team als Startteam
- Folge mit Gegnerteam als Startteam
- jedes Team wechselt bei seinem nächsten Aufschlaggame zum Teampartner
- die neu bestätigte Satzreihenfolge wird verwendet
- eine Korrektur setzt auch den nächsten Spieler konsistent
- Spieler und Team bleiben immer konsistent

Tie-Break:

- vollständige Folge für alle vier möglichen Startspieler
- erste zehn Punkte
- Folge über 6:6 hinaus
- erster Punkt rechts
- danach konsequenter Seitenwechsel
- Seitenwechsel nach sechs Punkten verändert die Aufschlagfolge nicht
- nächster Satz beginnt mit dem anderen Team als dem Tie-Break-Startteam
- der konkrete Spieler entspricht der für den neuen Satz bestätigten Reihenfolge

Match-Tie-Break:

- identische Spielerfolge
- beide möglichen Startteams
- alle möglichen Startspieler
- Fortsetzung bei 10:9
- Abschluss bei 11:9

Entscheidungspunkte:

- Golden Point ergibt vor Auswahl `RECEIVER_CHOICE`
- Star Point ergibt erst beim dritten Deuce `RECEIVER_CHOICE`
- rechts und links können gewählt werden
- Gameabschluss entfernt die Auswahl

### 16. Engine-Tests ergänzen

Datei:

`app/src/test/java/ch/hygro/padelscore/logic/PadelScoreEngineTest.kt`

Neue Testfälle:

- `servingPlayer` wechselt nach jedem regulären Game korrekt
- alle vier Spieler kommen in der festgelegten Reihenfolge an die Reihe
- `pointsInCurrentGame` wird nach einem Game zurückgesetzt
- Satz-Tie-Break hält die konkrete Spielerfolge ein
- Match-Tie-Break hält dieselbe Spielerfolge ein
- Punktevergabe ist vor Bestätigung eines neuen Satzes gesperrt
- neue Satzreihenfolge wird übernommen
- Golden Point verlangt eine Seitenwahl
- Star Point verlangt eine Seitenwahl
- Entscheidungspunkt gewinnt unabhängig von der gewählten Seite das Game
- Undo stellt Spieler, Team und Aufschlagseite wieder her

### 17. UI-Logik testbar auslagern

Falls die Positionsberechnung in `MatchScoreComponents.kt` verbleibt, soll sie in reine Funktionen ausgelagert werden.

Zu prüfen:

- eigenes Team rechts und links
- Gegnerteam gespiegelt rechts und links
- korrekte Kennung für alle vier Spieler
- Punkt wird nur in der richtigen Teamfläche angezeigt
- `RECEIVER_CHOICE` zeigt beide Positionen
- Auswahl wird eindeutig hervorgehoben
- Accessibility-Beschreibung enthält Spieler und Seite
- Punkteingabe bleibt während Satzbestätigung und Seitenwahl gesperrt
- langer Druck öffnet die Korrektur
- normaler Druck wertet weiterhin einen Punkt

### 18. Dokumentation aktualisieren

Datei:

`README.md`

Nach der Implementierung soll dokumentiert werden:

- Die App verfolgt Team und konkreten Aufschläger.
- Sie zeigt rechte oder linke Aufschlagseite.
- Sie verwendet im Tie-Break die 1-2-2-Folge unter Beibehaltung der Reihenfolge aller vier Spieler.
- Die teaminterne Reihenfolge kann vor jedem Satz festgelegt werden.
- Golden Point und Star Point berücksichtigen die Wahl der Rückschlagseite.
- Mixed-spezifische Regeln sind nicht enthalten.

## Nicht Teil dieser Änderung

- Mixed-Regeln
- Geschlechtsdaten
- frei eingegebene Spielernamen
- vollständige Anzeige des konkreten Rückschlägers bei jedem normalen Punkt
- automatische Erkennung, wer real auf dem Platz aufschlägt
- automatische Erkennung einer real vertauschten Rückschlagreihenfolge

Das Modell soll eine spätere Erweiterung um Namen oder Rückschläger ermöglichen, ohne diese Funktionen jetzt vorzutäuschen.

## Risiken

### Bedienung auf kleinem Display

Eine Kennung in einem 12-dp-Punkt kann schwer lesbar sein. Deshalb wird der Spieler zusätzlich im Center-Bereich und über Accessibility beschrieben.

### Versehentliche Punkteingabe

Die Teamflächen dienen bereits zur Punktevergabe. Auswahl von Entscheidungspunktseite und Aufschlägerkorrektur müssen eigene Touch-Ziele besitzen.

### Eingabe vor jedem Satz

Da die Reihenfolge nach FIP-Regel 6 vor jedem Satz neu gewählt werden darf, ist ein Bestätigungsschritt nötig. Er muss kurz sein und die bisherige Reihenfolge als Vorschlag anzeigen.

### Rückwärtskompatibilität

Bei alten Matchzuständen ist der konkrete Spieler nicht immer eindeutig rekonstruierbar. In diesem Fall ist eine Bestätigung korrekter als eine stille Annahme.

### Tatsächlicher Spielablauf

Die App zeigt die gespeicherte Soll-Reihenfolge. Sie kann nicht erkennen, ob auf dem Platz ein anderer Spieler aufschlägt. Deshalb ist die manuelle Korrektur wichtig.

## Verifikation

### Automatisierte Tests

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "ch.hygro.padelscore.logic.ServeCalculatorTest" --tests "ch.hygro.padelscore.logic.PadelScoreEngineTest"
```

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "ch.hygro.padelscore.storage.MatchCodecTest"
```

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

```powershell
.\gradlew.bat :app:lintDebug
```

### Manuelle Prüfung

1. Matchstart mit jedem möglichen ersten Team.
2. Matchstart mit jedem der vier möglichen ersten Spieler.
3. Reguläre Rotation durch alle vier Spieler.
4. Mehrfacher Einstand.
5. Golden Point mit Wahl rechts.
6. Golden Point mit Wahl links.
7. Star Point mit Wahl rechts.
8. Star Point mit Wahl links.
9. Satz-Tie-Break mit jedem möglichen Startspieler.
10. Langer Tie-Break über 6:6 hinaus.
11. Match-Tie-Break bis mindestens 11:9.
12. Neuer Satz nach regulärem Satzende.
13. Neuer Satz nach Tie-Break.
14. Neue teaminterne Reihenfolge für Satz zwei.
15. Neue teaminterne Reihenfolge für Satz drei.
16. Manuelle Aufschlägerkorrektur.
17. Undo vor und nach Spielerwechsel.
18. Undo vor und nach Entscheidungspunktwahl.
19. App während eines Entscheidungspunkts schließen und wieder öffnen.
20. App vor Bestätigung eines neuen Satzes schließen und wieder öffnen.
21. Alten Matchzustand ohne Spielerfelder laden.

## Abnahmekriterien

Die Änderung ist abgeschlossen, wenn:

- jedes Game rechts beginnt
- die Aufschlagseite nach jedem Punkt wechselt
- der konkrete Aufschläger jederzeit angezeigt wird
- die beiden Spieler jedes Teams korrekt alternieren
- die Reihenfolge vor jedem Satz gewählt oder bestätigt werden kann
- Satz- und Match-Tie-Break die korrekte 1-2-2-Spielerfolge verwenden
- der erste Tie-Break-Punkt rechts aufgeschlagen wird
- jeder folgende Tie-Break-Punkt die Seite wechselt
- das Tie-Break-Startteam den nächsten Satz nicht beginnt
- Golden Point keine automatische Seite behauptet
- Star Point keine automatische Seite behauptet
- Spieler und gewählte Seite Undo und Wiederaufnahme überstehen
- alte Matchzustände sicher migriert oder zur Bestätigung vorgelegt werden
- Mixed-Regeln weder ausgeführt noch behauptet werden
- die gezielten Tests, die gesamte Wear-Test-Suite und `:app:lintDebug` erfolgreich sind

## Tatsächlich geprüfte Dateien

- `FIP_Rules-of-Padel.pdf`
- `README.md`
- `AUFSCHLAGANZEIGE_PLAN.md`
- `app/src/main/java/ch/hygro/padelscore/logic/ServeCalculator.kt`
- `app/src/main/java/ch/hygro/padelscore/logic/PadelScoreEngine.kt`
- `app/src/main/java/ch/hygro/padelscore/model/MatchState.kt`
- `app/src/main/java/ch/hygro/padelscore/model/MatchConfiguration.kt`
- `app/src/main/java/ch/hygro/padelscore/model/Team.kt`
- `app/src/main/java/ch/hygro/padelscore/presentation/StartScreen.kt`
- `app/src/main/java/ch/hygro/padelscore/presentation/MatchScreen.kt`
- `app/src/main/java/ch/hygro/padelscore/presentation/MatchScoreComponents.kt`
- `app/src/main/java/ch/hygro/padelscore/presentation/AmbientMatchScreen.kt`
- `app/src/main/java/ch/hygro/padelscore/storage/MatchJsonCodec.kt`
- `app/src/test/java/ch/hygro/padelscore/logic/ServeCalculatorTest.kt`
- `app/src/test/java/ch/hygro/padelscore/logic/PadelScoreEngineTest.kt`
- `app/src/test/java/ch/hygro/padelscore/storage/MatchCodecTest.kt`
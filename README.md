# Planning Poker

Kollaboratives Planning Poker für Scrum-Teams. Teammitglieder schätzen synchron GitLab-Issues; das abgestimmte Ergebnis wird am Issue hinterlegt.

Schnellstart: `docker compose up --build`, dann http://localhost:4200 öffnen. Als Projekt-ID `1` (Issues `1`–`5` im GitLab-Mock) verwenden.

Entstehung und Entscheidungen im Detail: `IMPLEMENTATION_PLAN.md` (testgetrieben umgesetzt).

## Gewählte Variante: A – mit eigenem Backend

- **Verdeckte Schätzungen bleiben geheim.** Nur ein Server kann Karten bis zur Auflösung zurückhalten. Ohne Backend müssten die Karten an alle Clients gehen oder in GitLab abgelegt werden, wo sie jeder Teilnehmer lesen könnte.
- **Rollen werden serverseitig durchgesetzt.** Steuerungsaktionen prüft das Backend anhand des Tokens. Ohne Backend wäre der Scrum Master nur eine Konvention im Client.
- **GitLab-Token verlässt nie den Server.** Teilnehmer brauchen keinen eigenen GitLab-Zugang. Der Personal Access Token liegt ausschließlich in der Backend-Konfiguration.
- **Echtzeit ohne Fremddienst.** GitLab bietet keinen Push-Kanal für beliebige Clients. Das eigene Backend liefert ihn per SSE.
- **Zustand überlebt Reloads und Neustarts.** Sitzungen, Runden und das Protokoll liegen in PostgreSQL.

## Bedienung

1. **Sitzung erstellen:** Auf der Startseite GitLab-Projekt-ID, optional ein Issue und den eigenen Namen eingeben. Wer die Sitzung erstellt, ist Scrum Master.
2. **Einladen:** Den angezeigten Link (`/s/{sessionId}`) oder die Session-ID teilen. Entwickler treten mit ihrem Namen bei.
3. **Issue freigeben:** Der Scrum Master wählt ein Issue aus der GitLab-Liste und klickt „Issue freigeben“. Erst dann sehen die Entwickler Titel und Beschreibung und können schätzen.
4. **Schätzen:** Jeder Entwickler wählt eine Karte. Alle sehen namentlich „Hat geschätzt“ oder „Ausstehend“, aber keine Werte. Haben alle geschätzt, erhält der Scrum Master einen Hinweis.
5. **Aufdecken:** Der Scrum Master deckt auf. Angezeigt werden Karte je Person, Verteilung, Durchschnitt und häufigster Wert.
6. **Abschließen:** Der Scrum Master startet entweder eine „Neue Runde“ oder wählt eine Karte und klickt „In GitLab übernehmen“. Abgeschlossene Runden erscheinen im Sitzungsprotokoll.

## Architektur und Kommunikation

```
Browser (Angular) ──REST/JSON (Befehle)─────▶ Backend (Quarkus) ──REST + PRIVATE-TOKEN──▶ GitLab / Mock
                  ◀──SSE (Snapshots)─────────                    ──JDBC──▶ PostgreSQL
```

- **Befehle per REST/JSON:** z. B. Schätzung abgeben oder aufdecken. Jeder Befehl ist ein eigener Endpunkt. Fehler kommen als HTTP-Status mit `{"message"}`.
- **Echtzeit per Server-Sent Events:** Nach jeder Änderung bekommt jeder Teilnehmer den kompletten, für ihn gefilterten Snapshot der Sitzung.
- **Warum SSE statt WebSocket:**
  - Updates fließen nur vom Server zum Client; Befehle gehen ohnehin per REST.
  - SSE ist reines HTTP, funktioniert durch Proxys und verbindet sich im Browser selbst neu.
  - REST bringt Statuscodes, Validierung und Testbarkeit mit.
- **Warum komplette Snapshots statt einzelner Events:**
  - Der Client rechnet keinen Zustand zusammen.
  - Verlorene Events oder Reconnects sind unkritisch, das nächste Event enthält den vollständigen Stand.
  - Das Filtern pro Empfänger (keine fremden Karten vor der Auflösung, `allVoted` nur für den Scrum Master) passiert an genau einer Stelle im Backend.
- **Persistenz:** PostgreSQL mit Flyway-Migrationen.
- **GitLab-Zugriff:** über einen Quarkus REST-Client, Ziel konfigurierbar (`GITLAB_BASE_URL`).

## Rollen und Tokens

- **Token pro Teilnehmer:** Erstellen und Beitreten liefern ein zufälliges, geheimes Teilnehmer-Token. Das Backend speichert nur dessen SHA-256-Hash.
  - REST-Aufrufe senden das Token im Header `X-Participant-Token`.
  - Der SSE-Stream bekommt es als `?token=`, weil `EventSource` keine Header setzen kann.
- **Rolle:** Das Backend leitet sie aus dem Token ab: `SCRUM_MASTER` für den Ersteller, sonst `DEVELOPER`. Steuerungsaktionen durch Entwickler ergeben `403`.
- **Speicherort im Browser:** Das Frontend legt das Token im `localStorage` ab, damit ein Reload die Teilnahme nicht beendet.
- **Kein Login:** Wer den Link kennt, kann beitreten. Das ist für die Aufgabe ausreichend.
- **Token in Logs:** Weil das Token in der SSE-URL steht, schreibt nginx für diesen Pfad kein Access-Log.

## Annahmen

- Der Scrum Master moderiert nur und schätzt selbst nicht. „Alle haben geschätzt“ bezieht sich auf alle Entwickler.
- Namen sind pro Sitzung eindeutig (Groß-/Kleinschreibung egal).
- Eine Sitzung gehört zu genau einem GitLab-Projekt (numerische Projekt-ID). Mehrere Issues werden nacheinander geschätzt.
- Beitritt ist jederzeit möglich; Spätere zählen sofort mit.
- Teilnehmer ohne Verbindung werden als offline markiert, bleiben aber in der Liste und zählen weiter. Der Scrum Master kann trotzdem jederzeit aufdecken.
- Der Scrum Master kann die Sitzung nicht verlassen; es gibt keine Rollenübergabe. Mit seinem Token verbindet er sich neu.
- Ein Issue-Wechsel während einer Runde verwirft die bisherigen Karten.
- `?` und `☕` zählen in Verteilung und häufigstem Wert, aber nicht im Durchschnitt. Bei Gleichstand gewinnt der höhere Wert in Kartenreihenfolge (`34 < ? < ☕`).

## Struktur

| Ordner | Inhalt |
|---|---|
| `backend/` | Quarkus (Jakarta EE, REST, Hibernate ORM Panache, Flyway, PostgreSQL, GitLab REST-Client), Java 21 |
| `frontend/` | Angular 22 (Standalone Components) |
| `gitlab-mock/` | Minimaler GitLab-API-Mock (Node, ohne Abhängigkeiten) für Entwicklung und Tests |
| `docker-compose.yml` | Startet PostgreSQL, GitLab-Mock, Backend und Frontend |

## Voraussetzungen

- Docker + Docker Compose **oder**
- Java 21, Maven 3.9+, Node.js 22+ und Docker (für Quarkus Dev Services/PostgreSQL in Dev-Mode und Tests)

## Start mit Docker Compose

```bash
cp .env.example .env      # Default: GitLab-Mock mit Fake-Token; für echtes GitLab anpassen
docker compose up --build
```

- Frontend: http://localhost:4200 (nginx; leitet `/api` an das Backend weiter)
- Backend: http://localhost:8080 (Health: `/q/health`)
- GitLab-Mock: http://localhost:8081 (Token `fake-token`, Projekt `1` mit Issues `1`–`5`; aktueller Inhalt unter `/__test/state`)

Die Container starten in Abhängigkeit ihrer Healthchecks: PostgreSQL und Mock zuerst, dann das Backend (`/q/health/ready`), dann das Frontend. Daten bleiben im Volume `db-data`; `docker compose down -v` setzt alles zurück. Der Mock hält seinen Zustand nur im Speicher.

**Echtes GitLab statt Mock:** In `.env` `GITLAB_BASE_URL` (z. B. `https://gitlab.com`) und `GITLAB_TOKEN` (Personal Access Token mit Scope `api`) setzen. Der Mock-Container läuft weiter, wird dann aber nicht genutzt.

Das GitLab-Token (Personal Access Token, Scope `api`) wird nie ins Frontend ausgeliefert, nur das Backend nutzt es.

## Lokale Entwicklung

Backend (Dev-Mode mit Live-Reload; Quarkus Dev Services startet PostgreSQL automatisch per Docker):

```bash
cd backend
mvn quarkus:dev
```

Frontend (Proxy auf `localhost:8080`):

```bash
cd frontend
npm install
npm start
```

Frontend läuft dann unter http://localhost:4200.

## Konfiguration

| Variable | Default | Beschreibung |
|---|---|---|
| `GITLAB_BASE_URL` | `https://gitlab.com` (Compose: `http://gitlab-mock:8081`) | GitLab-Instanz |
| `GITLAB_TOKEN` | – (Compose: `fake-token`) | Access Token für die GitLab API; der Mock akzeptiert nur diesen Wert |
| `CORS_ORIGINS` | `http://localhost:4200` | Erlaubte Origins |
| `DB_URL` | `jdbc:postgresql://localhost:5432/poker` (Compose: `db`) | JDBC-URL (nur Prod-Profil; Dev/Test nutzen Dev Services) |
| `DB_USER` / `DB_PASSWORD` | `poker` / `poker` | Datenbank-Zugang |

## Tests

```bash
cd gitlab-mock && npm test        # Tests des GitLab-Mocks
cd backend && mvn test            # Backend-Tests (starten Mock + PostgreSQL automatisch, benötigt Node.js und Docker)
cd backend && mvn verify          # zusätzlich End-to-End-Test gegen das gepackte Backend (überspringen mit -DskipITs)
cd frontend && npm test           # Frontend-Tests (Jest + jsdom, kein Browser nötig)
```

Der End-to-End-Test (`PlanningPokerE2EIT`) startet das gebaute `quarkus-run.jar` als eigenen Prozess im Prod-Profil gegen PostgreSQL (Testcontainers) und den GitLab-Mock. Er spielt eine komplette Sitzung durch: Erstellen, Beitreten, Issue-Liste, Freigabe, Fortschritt per SSE ohne Kartenleck, Aufdecken, zweite Runde, Bestätigen (Label und Kommentar im Mock). Danach startet er das Backend neu und prüft, dass die Sitzung samt Tokens und Protokoll erhalten bleibt und weiterläuft. Logs der Backend-Prozesse: `backend/target/e2e-backend-*.log`.

`backend/src/test/resources/docker-java.properties` setzt `api.version=1.44`, da Docker Engine 29+ ältere API-Versionen ablehnt, die Testcontainers sonst verwendet.

Der Mock bietet neben den GitLab-v4-Endpunkten `/__test/state`, `/__test/reset` und `/__test/fail-next` für Assertions in Tests.

### CI

Diese Befehle decken alles ab (benötigt Java 21, Maven, Node.js 22 und Docker):

```bash
(cd gitlab-mock && npm test)
(cd backend && mvn -B verify)
(cd frontend && npm ci && npm test && npx ng build)
```

## Frontend

Angular 22 mit Standalone Components und Signals (Zone-basierte Change Detection).

- **Routen:** `/` (Sitzung erstellen oder beitreten) und `/s/{sessionId}` (Sitzung). Der Einladungslink ist die Sitzungs-URL. Im Beitrittsformular kann man die Session-ID oder den kompletten Link einfügen.
- **`SessionStore`:** wird pro Sitzungsseite bereitgestellt.
  - Hält den letzten Snapshot als Signal; alles Weitere wird daraus abgeleitet (Rolle, Zustand, „alle haben geschätzt“).
  - Befehle gehen per REST raus. Die Antwort wird nicht ausgewertet, der neue Zustand kommt immer über SSE.
  - Fehlermeldungen des Backends (`{message}`) werden angezeigt.
- **Zugangsdaten:** Token und Teilnehmer-ID liegen pro Sitzung im `localStorage` (`planning-poker.session.{id}`). Dadurch überlebt die Teilnahme ein Neuladen der Seite.
  - Ein HTTP-Interceptor setzt den Header `X-Participant-Token`.
  - Wer keine oder ungültige Zugangsdaten hat (`401`), sieht wieder das Beitrittsformular.
- **Reconnect:** Bei kurzen Abbrüchen verbindet sich `EventSource` selbst neu; die Seite zeigt solange einen Hinweis.
  - Schließt der Browser den Stream endgültig, prüft der Store per `GET` den Grund:
    - `401` → neu beitreten
    - `404` → Sitzung unbekannt
    - sonst → nach 2 s erneut verbinden
- **Rollen:** Steuerungen (Issue-Auswahl, Freigeben, Aufdecken, Neue Runde, Übernehmen) werden nur dem Scrum Master angezeigt. Das Backend prüft die Rolle trotzdem selbst.
- **Übernahme:** Der Wert wird aus dem Kartensatz gewählt; es gibt bewusst keine Vorauswahl. Durchschnitt und häufigster Wert dienen nur zur Orientierung.
- **Optionale Features:**
  - *Ausreißer:* ab 3 numerischen Schätzungen werden Karten markiert, die mindestens 2 Stufen im Kartensatz vom Median entfernt liegen.
  - *Konsens:* Haben alle (mindestens 2) dieselbe Karte gewählt, erscheint ein Konsens-Hinweis mit Animation.

Die Frontend-Tests ersetzen `EventSource` per Injection-Token durch einen Fake und das Backend durch `HttpTestingController`. Alle Ansichten werden pro Rolle (Scrum Master und Entwickler) geprüft.

## API

Authentifizierung: Beim Erstellen/Beitreten liefert das Backend einmalig ein geheimes Token. Alle weiteren Aufrufe senden es im Header `X-Participant-Token`. Die Rolle wird serverseitig aus dem Token abgeleitet; gespeichert wird nur ein SHA-256-Hash.

| Methode | Pfad | Auth | Antwort |
|---|---|---|---|
| POST | `/api/sessions` – `{projectId, issueIid?, name}` | – | `201 {sessionId, participantId, token}`; Ersteller ist Scrum Master |
| POST | `/api/sessions/{id}/join` – `{name}` | – | `201 {sessionId, participantId, token}`; Rolle Entwickler |
| GET | `/api/sessions/{id}` | Token | `200` Snapshot (siehe unten) |
| GET | `/api/sessions/{id}/events?token=…` | Token (Query) | `text/event-stream`, siehe Echtzeit |
| PUT | `/api/sessions/{id}/issue` – `{issueIid}` | SM | `204`; wählt Issue aus, eine laufende Abstimmung wird verworfen |
| POST | `/api/sessions/{id}/rounds` | SM | `204`; gibt das Issue frei, Runde startet (`VOTING`) |
| PUT | `/api/sessions/{id}/votes` – `{card}` | Entwickler | `204`; verdeckte Schätzung abgeben/ändern |
| POST | `/api/sessions/{id}/reveal` | SM | `204`; Karten aufdecken (`REVEALED`), auch wenn nicht alle geschätzt haben |
| POST | `/api/sessions/{id}/rounds/restart` | SM | `204`; neue Runde für dasselbe Issue, Karten zurückgesetzt |
| GET | `/api/sessions/{id}/issues?page=1&perPage=20` | SM | `200 {issues: [{iid, title, description, webUrl, labels}], nextPage}`; offene Issues des Projekts aus GitLab (`nextPage` ist `null` auf der letzten Seite) |
| POST | `/api/sessions/{id}/confirm` – `{card}` | SM | `204`; nur in `REVEALED`; schreibt den Wert an das GitLab-Issue (siehe unten), danach `CONFIRMED` |
| POST | `/api/sessions/{id}/leave` | Entwickler | `204`; Teilnehmer und eigene Karte werden entfernt, Token ungültig |

Karten: `"0"`, `"1"`, `"2"`, `"3"`, `"5"`, `"8"`, `"13"`, `"21"`, `"34"`, `"?"`, `"☕"`.

Snapshot (aus Sicht des Aufrufers):

```json
{
  "sessionId": "…", "projectId": "1", "state": "NO_ISSUE|ISSUE_SELECTED|VOTING|REVEALED|CONFIRMED",
  "me": {"id": "…", "name": "Anna", "role": "SCRUM_MASTER", "hasVoted": false, "online": true},
  "participants": [{"id": "…", "name": "Ben", "role": "DEVELOPER", "hasVoted": true, "online": false}],
  "issue": {"iid": 3, "title": "…", "description": "…", "webUrl": "…", "labels": ["backend"]},
  "roundNumber": 2, "myVote": "5", "allVoted": false,
  "result": {"votes": [{"participantId": "…", "name": "Ben", "card": "5"}],
             "groups": [{"card": "5", "count": 1}], "average": 5.0, "mostFrequent": "5"},
  "confirmedCard": null,
  "history": [{"issueIid": 3, "issueTitle": "…", "roundNumber": 1,
               "votes": [{"name": "Ben", "card": "8"}], "groups": [{"card": "8", "count": 1}],
               "average": 8.0, "mostFrequent": "8", "confirmedCard": null}]
}
```

`issue` sehen Entwickler erst nach der Freigabe (`VOTING`, `REVEALED`, `CONFIRMED`); Titel, Beschreibung und Labels werden bei der Issue-Auswahl aus GitLab geladen und in der Sitzung gespeichert. `allVoted` (alle Entwickler haben in der laufenden Runde geschätzt) wird nur an den Scrum Master gesendet. `result` ist nur bei `REVEALED`/`CONFIRMED` gesetzt; vorher sind fremde Karten nie sichtbar. `history` ist das Sitzungsprotokoll: alle aufgedeckten bzw. bestätigten Runden außer der aktuellen, älteste zuerst. Stimmen bleiben dort mit Namen erhalten, auch wenn der Teilnehmer die Sitzung verlassen hat. Nicht aufgedeckte (verworfene) Runden erscheinen nie im Protokoll.

### Ergebnis in GitLab hinterlegen

`POST /confirm` schreibt den gewählten Wert per GitLab-API an das Issue:

- **Numerische Karte** (`0`–`34`): Label `SP::<wert>` wird gesetzt (`PUT /projects/:id/issues/:iid` mit `add_labels`), alle anderen `SP::*`-Labels werden im selben Aufruf per `remove_labels` entfernt. Zusätzlich ein Kommentar (`POST …/notes`) mit Wert, Runde, Stimmverteilung und Durchschnitt.
- **`?` / `☕`**: kein Label, nur der Kommentar (keine sinnvolle Story-Point-Zahl).

Warum Labels statt des `weight`-Felds: `weight` ist ein Premium-Feature, Scoped Labels (`SP::…`) sind in jeder GitLab-Edition sichtbar, filterbar und durch den Scope automatisch exklusiv. Der Kommentar macht die Schätzung im Issue nachvollziehbar.

Schlägt der GitLab-Aufruf fehl, antwortet das Backend mit `502`, die Runde bleibt `REVEALED` und der Scrum Master kann erneut bestätigen. Nach Erfolg ist die Runde `CONFIRMED`; anschließend kann ein neues Issue gewählt werden. Projekt-IDs werden als numerische IDs erwartet (Pfade wie `gruppe/projekt` werden nicht URL-kodiert).

### Echtzeit (Server-Sent Events)

Befehle laufen über REST, Updates kommen über einen SSE-Stream pro Teilnehmer. Nach dem Verbinden und nach jeder Änderung der Sitzung (Beitritt, Verlassen, Issue-Auswahl, Freigabe, Schätzung, Aufdecken, Online-Status) sendet das Backend ein Event `snapshot` mit dem kompletten, für den Empfänger gefilterten Snapshot (gleiches Format wie `GET /api/sessions/{id}`). Der Client muss also keinen Zustand zusammenrechnen; ein Reconnect liefert sofort den aktuellen Stand.

```
event: snapshot
data: {"sessionId":"…","state":"VOTING",…}

: ping
```

- Das Token wird hier als Query-Parameter übergeben, weil `EventSource` keine Header setzen kann. Nur dieser Endpunkt akzeptiert `?token=`.
- `online` ist `true`, solange der Teilnehmer mindestens einen offenen Stream hat. Offline-Teilnehmer bleiben in der Liste und zählen beim Abstimmungsfortschritt weiter mit.
- Alle 15 s kommt ein Heartbeat-Kommentar (`: ping`); konfigurierbar über `poker.sse.heartbeat-interval`.
- Nach `leave` schließt der Server den Stream des Teilnehmers.
- Hinter nginx ist für den Stream `proxy_buffering off` gesetzt.

Fehler: `400` ungültige Eingabe, `401` Token fehlt/ungültig, `403` falsche Rolle, `404` Sitzung unbekannt, `409` Konflikt (Name bereits vergeben, Aktion im aktuellen Zustand nicht erlaubt, Scrum Master verlässt Sitzung), `502` GitLab nicht erreichbar oder Fehler beim Schreiben. Ein bei GitLab unbekanntes Projekt/Issue beim Anlegen bzw. Auswählen ergibt `400`. Fehlerformat: `{"message": "..."}`.

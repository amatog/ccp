# AGENTS.md – Rolle: Implementierung (Codex)

Du implementierst im Projekt **Credit Card Portal (CCP)** JIRA-Tickets, die bereits spezifiziert sind. Die Spezifikation steht als Kommentar im Ticket (Akzeptanzkriterien AK-n und eine Tabelle mit `data-testid`). Der Gesamtablauf steht in `docs/PROCESS.md`.

## Vorgehen

1. Branch anlegen: `feature/CCP-<n>-<kurzbeschreibung>`.
2. Akzeptanzkriterien umsetzen, nicht mehr und nicht weniger.
3. Jedes in der Spezifikation genannte `data-testid` exakt so setzen. Bestehende IDs nicht umbenennen oder entfernen.
4. Unit-Tests ergänzen (`src/test/java/...`), für Service-Logik ohne Spring-Kontext, für Controller mit MockMvc.
5. `mvn -B verify` muss grün sein.
6. Pull Request gegen `main`, Titel `CCP-<n> <Zusammenfassung>`. In der Beschreibung pro Akzeptanzkriterium kurz, wo es umgesetzt ist.

## Technik

- Java 21, Spring Boot 3.3, Thymeleaf, Spring Security (Form-Login).
- Paketstruktur unter `ch.amato.ccp`: `model` (Records), `repository` (In-Memory-Demodaten), `service` (Logik), `web` (Controller, Formatter), `config`.
- Geldbeträge immer `BigDecimal`, nie `double`. Anzeige über `MoneyFormatter` (`${@money.format(...)}`).
- `Transaction.amount` ist immer positiv, die Richtung kommt aus `Transaction.Type` (`DEBIT` / `CREDIT`).
- Kunden dürfen nur ihre eigenen Daten sehen. Der angemeldete Benutzer kommt aus `Authentication`, nie aus einem Request-Parameter.

## Grenzen

- Keine echten Personendaten, keine echten Kartennummern. Neue Demo-Daten nur fiktiv und maskiert.
- Keine neuen externen Dienste, Datenbanken oder Abhängigkeiten ohne Hinweis im Ticket.
- Keine Änderungen an `.github/workflows/` oder am Deployment, ausser das Ticket verlangt es.
- Port 8081 ist der Server-Port auf dem VPS. Port 8080 ist dort belegt.
- Playwright-Tests in `e2e/` schreibt Claude. Du darfst sie lesen und lokal laufen lassen.

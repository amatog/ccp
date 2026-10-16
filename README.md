# Credit Card Portal (CCP)

Demo-Projekt: KI-gestützter Change- und Testprozess.

Ein einfaches Kreditkarten-Kundenportal (Java 21 / Spring Boot), in dem Kundinnen und Kunden ihre Transaktionen einsehen können. Im Mittelpunkt steht nicht die App, sondern der automatisierte Ablauf bei jeder Änderung:

1. **JIRA**: Neue Anforderung im Projekt `CCP`
2. **Claude**: Spezifikation und Akzeptanzkriterien
3. **Codex**: Implementierung, Pull Request auf GitHub
4. **Gemini**: Code-Review des Pull Requests
5. **Mensch**: Freigabe und Merge
6. **GitHub Actions**: Build und Deployment auf den Hostinger-VPS
7. **Claude**: Playwright-Tests anpassen bzw. erstellen und ausführen
8. **Skript**: Testresultat zurück ins JIRA-Ticket

Details zu Rollen, Konventionen und Pipelines: [`docs/PROCESS.md`](docs/PROCESS.md).

## Projektstruktur

```
src/main/java/ch/amato/ccp/   Spring-Boot-App (model, repository, service, web, config)
src/main/resources/templates/ Thymeleaf-Seiten (Login, Transaktionen)
src/test/java/                Unit- und MockMvc-Tests
e2e/                          Playwright-Tests (TypeScript)
scripts/jira-report.mjs       Schreibt das Testresultat ins JIRA-Ticket
.github/workflows/            CI (Pull Requests) und Deploy & Test (main)
CLAUDE.md / AGENTS.md / GEMINI.md   Anweisungen für die drei KI-Agents
```

## Lokal starten

```bash
mvn -B verify
java -jar target/app.jar          # http://localhost:8080
```

Demo-Zugänge (fiktiv): `anna` oder `marco`, Passwort `demo1234`.

## Playwright-Tests

```bash
cd e2e
npm ci
npx playwright install chromium
npx playwright test                                   # gegen localhost:8080
BASE_URL=http://<VPS_HOST>:8081 npx playwright test   # gegen den Server
```

> Hinweis: Die App verwendet ausschliesslich fiktive Daten und maskierte Testkartennummern.

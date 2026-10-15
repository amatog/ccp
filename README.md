# Credit Card Portal (CCP)

Demo-Projekt: KI-gestützter Change- und Testprozess.

Ein einfaches Kreditkarten-Kundenportal (Java / Spring Boot), in dem Kundinnen und Kunden ihre Transaktionen einsehen können. Im Mittelpunkt steht nicht die App, sondern der automatisierte Ablauf bei jeder Änderung:

1. **JIRA** – Neue Anforderung im Projekt `CCP`
2. **Claude** – Spezifikation und Akzeptanzkriterien
3. **Codex** – Implementierung, Pull Request auf GitHub
4. **Gemini** – Code-Review des Pull Requests
5. **Mensch** – Freigabe und Merge
6. **GitHub Actions** – Build und Deployment auf den Hostinger-VPS
7. **Claude** – Playwright-Test anpassen bzw. erstellen und ausführen
8. **Skript** – Testresultat zurück ins JIRA-Ticket

> Hinweis: Die App verwendet ausschliesslich fiktive Daten und maskierte Testkartennummern.

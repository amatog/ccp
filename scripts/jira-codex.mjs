#!/usr/bin/env node
// JIRA-Anbindung für die KI-Workflows (codex-implement.yml und gemini-review.yml).
//
//   node scripts/jira-codex.mjs start   – Spezifikation laden, Auftrag für Codex bauen,
//                                         Ticket auf «In Arbeit» setzen und Start kommentieren
//   node scripts/jira-codex.mjs done    – PR-Link ins Ticket schreiben, Auslöse-Label entfernen
//   node scripts/jira-codex.mjs failed  – Fehlschlag ins Ticket schreiben, Auslöse-Label entfernen
//   node scripts/jira-codex.mjs spec          – Titel und neueste Spezifikation in SPEC_OUT schreiben (Gemini-Review)
//   node scripts/jira-codex.mjs review        – Gemini-Empfehlung und PR-Kommentar-Link ins Ticket schreiben
//   node scripts/jira-codex.mjs review-failed – Fehlschlag des Gemini-Reviews ins Ticket schreiben
//   node scripts/jira-codex.mjs tests-start   – Spezifikation + Auftrag für Claude (Playwright) schreiben, Start melden
//   node scripts/jira-codex.mjs tests-done    – Ergebnis der Playwright-Tests ins Ticket schreiben
//   node scripts/jira-codex.mjs tests-failed  – Fehlschlag des Test-Workflows ins Ticket schreiben
//
// Umgebung: JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN, ISSUE_KEY, RUN_URL
//   start:  PROMPT_TEMPLATE, PROMPT_OUT, GITHUB_OUTPUT (Branch-Name und Titel als Step-Outputs)
//   done:   PR_URL, BRANCH, CHANGED_FILES (eine Datei pro Zeile)
//   failed: FAILURE_REASON (optional)
//   spec:   SPEC_OUT
//   review: PR_URL, REVIEW_URL, RECOMMENDATION
//   tests-start: SPEC_OUT, PROMPT_TEMPLATE, PROMPT_OUT, PR_URL, GITHUB_OUTPUT (feature)
//   tests-done:  PR_URL, COMMENT_URL, TEST_FILES, PASSED, FAILED, COST_USD
//   tests-failed: PR_URL, FAILURE_REASON (optional)
// Mit DRY_RUN=1 und SPEC_FIXTURE=<json-Datei> läuft das Skript ohne JIRA (für lokale Tests).

import { readFileSync, writeFileSync, appendFileSync } from 'node:fs';

const env = process.env;
const command = process.argv[2];
const DRY_RUN = env.DRY_RUN === '1';
const TRIGGER_LABEL = env.TRIGGER_LABEL || 'codex-bereit';
const ISSUE_KEY = (env.ISSUE_KEY || '').trim();

if (!/^CCP-\d+$/.test(ISSUE_KEY)) {
  fail(`Ungültiger Ticket-Schlüssel «${ISSUE_KEY}» (erwartet z. B. CCP-6).`);
}
if (!DRY_RUN && (!env.JIRA_BASE_URL || !env.JIRA_EMAIL || !env.JIRA_API_TOKEN)) {
  fail('JIRA_BASE_URL, JIRA_EMAIL und JIRA_API_TOKEN müssen gesetzt sein.');
}

const base = (env.JIRA_BASE_URL || '').replace(/\/$/, '');
const auth = Buffer.from(`${env.JIRA_EMAIL}:${env.JIRA_API_TOKEN}`).toString('base64');

switch (command) {
  case 'start': await start(); break;
  case 'done': await done(); break;
  case 'failed': await failed(); break;
  case 'spec': await specForReview(); break;
  case 'review': await review(); break;
  case 'review-failed': await reviewFailed(); break;
  case 'tests-start': await testsStart(); break;
  case 'tests-done': await testsDone(); break;
  case 'tests-failed': await testsFailed(); break;
  default: fail(`Unbekannter Befehl «${command}» (start | done | failed | spec | review | review-failed | tests-start | tests-done | tests-failed).`);
}

// --- Befehle --------------------------------------------------------------------

async function start() {
  const fixture = DRY_RUN ? JSON.parse(readFileSync(env.SPEC_FIXTURE, 'utf8')) : null;
  const issue = fixture ?? await jira('GET', `/rest/api/2/issue/${ISSUE_KEY}?fields=summary,description`);
  const summary = issue.fields.summary.trim();

  // Massgebend ist die neueste Spezifikation (Kommentar mit Überschrift «Spezifikation»).
  const spec = fixture
    ? [...(fixture.fields.comment?.comments ?? [])].reverse().find(isSpec)
    : await findNewestSpec();
  if (!spec) {
    await comment(`(!) *Codex nicht gestartet:* Im Ticket ist keine Spezifikation (Kommentar mit Überschrift «Spezifikation») vorhanden.\nLauf: ${env.RUN_URL || '-'}`);
    await removeTriggerLabel();
    // Fehler ist bereits gemeldet – der allgemeine Fehler-Schritt im Workflow soll nicht nochmals melden.
    if (env.GITHUB_OUTPUT) appendFileSync(env.GITHUB_OUTPUT, 'reported=true\n');
    fail('Keine Spezifikation im Ticket gefunden.');
  }

  const branch = `feature/${ISSUE_KEY}-${slug(summary)}`;
  const values = {
    ISSUE_KEY,
    SUMMARY: summary,
    BRANCH: branch,
    DESCRIPTION: (issue.fields.description || '(keine Beschreibung)').trim(),
    SPEC: spec.body.trim(),
  };
  // Ersetzung per Funktion, damit «$» im Tickettext nicht als Muster interpretiert wird.
  const prompt = readFileSync(env.PROMPT_TEMPLATE, 'utf8')
    .replace(/\{\{(ISSUE_KEY|SUMMARY|BRANCH|DESCRIPTION|SPEC)\}\}/g, (_, k) => values[k]);
  writeFileSync(env.PROMPT_OUT, prompt);

  if (env.GITHUB_OUTPUT) {
    appendFileSync(env.GITHUB_OUTPUT, `branch=${branch}\ntitle=${ISSUE_KEY} ${summary.replace(/[\r\n]+/g, ' ')}\n`);
  }

  await transition('In Arbeit');
  await comment(`*Codex gestartet* (Prozessschritt 2: Implementierung)\n* Branch: {{${branch}}}\n* Lauf: [GitHub Actions|${env.RUN_URL || '-'}]`);
  console.log(`Auftrag für ${ISSUE_KEY} erstellt: ${env.PROMPT_OUT} (Branch ${branch})`);
}

async function done() {
  const files = (env.CHANGED_FILES || '').split('\n').map((f) => f.trim()).filter(Boolean);
  const list = files.length ? files.map((f) => `* {{${f}}}`).join('\n') : '* (keine)';
  await comment(
    `(/) *Codex fertig – Pull Request eröffnet*\n` +
    `* PR: [${env.PR_URL}|${env.PR_URL}]\n` +
    `* Branch: {{${env.BRANCH}}}\n` +
    `* Lauf: [GitHub Actions|${env.RUN_URL || '-'}]\n\n` +
    `*Geänderte Dateien:*\n${list}\n\n` +
    `Nächste Schritte: CI am PR, Playwright-Tests (Claude), Review (Gemini), Freigabe & Merge.`,
  );
  await removeTriggerLabel();
}

async function failed() {
  const reason = env.FAILURE_REASON || 'Der Workflow ist fehlgeschlagen.';
  await comment(`(x) *Codex-Lauf fehlgeschlagen*\n* Grund: ${reason}\n* Details: [GitHub Actions|${env.RUN_URL || '-'}]\n\nFür einen neuen Versuch das Label {{${TRIGGER_LABEL}}} erneut setzen.`);
  await removeTriggerLabel();
}

/** Schreibt Titel, Beschreibung und neueste Spezifikation als Markdown-Datei für das Review. */
async function specForReview() {
  const fixture = DRY_RUN ? JSON.parse(readFileSync(env.SPEC_FIXTURE, 'utf8')) : null;
  const issue = fixture ?? await jira('GET', `/rest/api/2/issue/${ISSUE_KEY}?fields=summary,description`);
  const spec = fixture
    ? [...(fixture.fields.comment?.comments ?? [])].reverse().find(isSpec)
    : await findNewestSpec();
  const text =
    `# ${ISSUE_KEY}: ${issue.fields.summary.trim()}\n\n` +
    `## Beschreibung\n\n${(issue.fields.description || '(keine Beschreibung)').trim()}\n\n` +
    `## Spezifikation (massgebend)\n\n${spec ? spec.body.trim() : '(Im Ticket ist keine Spezifikation vorhanden.)'}\n`;
  writeFileSync(env.SPEC_OUT, text);
  console.log(`Spezifikation für ${ISSUE_KEY} geschrieben: ${env.SPEC_OUT}${spec ? '' : ' (keine gefunden)'}`);
}

async function review() {
  const rec = (env.RECOMMENDATION || '').trim() || 'unbekannt';
  const icon = /^Freigeben$/i.test(rec) ? '(/)' : /^Nicht freigeben/i.test(rec) ? '(x)' : '(!)';
  await comment(
    `${icon} *Gemini-Review abgeschlossen* (Prozessschritt 4)\n` +
    `* Empfehlung: *${rec}*\n` +
    `* Review-Kommentar: [am Pull Request|${env.REVIEW_URL || env.PR_URL}]\n` +
    `* Lauf: [GitHub Actions|${env.RUN_URL || '-'}]\n\n` +
    `Nächster Schritt: Freigabe und Merge durch einen Menschen.`,
  );
}

async function reviewFailed() {
  await comment(
    `(x) *Gemini-Review fehlgeschlagen*\n` +
    `* Pull Request: [${env.PR_URL || '-'}|${env.PR_URL || '-'}]\n` +
    `* Details: [GitHub Actions|${env.RUN_URL || '-'}]\n\n` +
    `Neuer Versuch: in GitHub unter Actions → «Gemini – Pull Request reviewen» → Run workflow.`,
  );
}

/** Spezifikation und Auftrag für die Playwright-Tests bereitlegen und den Start melden. */
async function testsStart() {
  const fixture = DRY_RUN ? JSON.parse(readFileSync(env.SPEC_FIXTURE, 'utf8')) : null;
  const issue = fixture ?? await jira('GET', `/rest/api/2/issue/${ISSUE_KEY}?fields=summary,description`);
  const spec = fixture
    ? [...(fixture.fields.comment?.comments ?? [])].reverse().find(isSpec)
    : await findNewestSpec();
  if (!spec) fail('Keine Spezifikation im Ticket gefunden.');

  const summary = issue.fields.summary.trim();
  const feature = slug(summary);
  writeFileSync(env.SPEC_OUT,
    `# ${ISSUE_KEY}: ${summary}\n\n## Beschreibung\n\n${(issue.fields.description || '(keine Beschreibung)').trim()}\n\n` +
    `## Spezifikation (massgebend)\n\n${spec.body.trim()}\n`);
  const values = { ISSUE_KEY, SUMMARY: summary, FEATURE: feature };
  writeFileSync(env.PROMPT_OUT, readFileSync(env.PROMPT_TEMPLATE, 'utf8')
    .replace(/\{\{(ISSUE_KEY|SUMMARY|FEATURE)\}\}/g, (_, k) => values[k]));
  if (env.GITHUB_OUTPUT) appendFileSync(env.GITHUB_OUTPUT, `feature=${feature}\n`);

  await comment(`*Claude schreibt Playwright-Tests* (Prozessschritt 7)\n* Pull Request: [${env.PR_URL}|${env.PR_URL}]\n* Lauf: [GitHub Actions|${env.RUN_URL || '-'}]`);
  console.log(`Auftrag für ${ISSUE_KEY} erstellt (tests/${feature}.spec.ts)`);
}

async function testsDone() {
  const files = (env.TEST_FILES || '').split('\n').map((f) => f.trim()).filter(Boolean);
  const failed = Number(env.FAILED || 0);
  const icon = failed === 0 ? '(/)' : '(x)';
  const cost = env.COST_USD ? `\n* KI-Kosten (geschätzt): ${Number(env.COST_USD).toFixed(2)} USD` : '';
  await comment(
    `${icon} *Playwright-Tests von Claude auf den PR-Branch committet*\n` +
    `* Dateien: ${files.length ? files.map((f) => `{{${f}}}`).join(', ') : '(keine)'}\n` +
    `* Gesamte Suite gegen die lokal gestartete App: ${env.PASSED || 0} bestanden, ${failed} fehlgeschlagen\n` +
    `* Zusammenfassung: [am Pull Request|${env.COMMENT_URL || env.PR_URL}]\n` +
    `* Lauf: [GitHub Actions|${env.RUN_URL || '-'}]${cost}\n\n` +
    (failed === 0
      ? 'Nächste Schritte: CI und Gemini-Review laufen automatisch, danach Freigabe und Merge.'
      : 'Mindestens ein Test schlägt fehl. Befunde im PR-Kommentar prüfen, bevor freigegeben wird.'),
  );
}

async function testsFailed() {
  const reason = env.FAILURE_REASON || 'Der Workflow ist fehlgeschlagen.';
  await comment(
    `(x) *Automatische Playwright-Tests fehlgeschlagen*\n` +
    `* Grund: ${reason}\n* Pull Request: [${env.PR_URL || '-'}|${env.PR_URL || '-'}]\n` +
    `* Details: [GitHub Actions|${env.RUN_URL || '-'}]\n\n` +
    `Neuer Versuch: in GitHub unter Actions → «Claude – Playwright-Tests schreiben» → Run workflow.`,
  );
}

// --- JIRA-Hilfen ------------------------------------------------------------------

async function jira(method, path, body) {
  if (DRY_RUN) {
    console.log(`DRY_RUN: ${method} ${path}${body ? ' ' + JSON.stringify(body) : ''}`);
    return {};
  }
  const res = await fetch(base + path, {
    method,
    headers: { Authorization: `Basic ${auth}`, Accept: 'application/json', 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`JIRA ${method} ${path} fehlgeschlagen (${res.status}): ${await res.text()}`);
  return res.status === 204 ? {} : res.json();
}

function isSpec(c) {
  return /^\s*(h\d\.\s*|#+\s*)?Spezifikation/m.test(c.body || '');
}

/** Blättert die Kommentare vom neuesten zum ältesten durch, bis eine Spezifikation gefunden ist. */
async function findNewestSpec() {
  const pageSize = 100;
  for (let startAt = 0; ; startAt += pageSize) {
    const page = await jira('GET',
      `/rest/api/2/issue/${ISSUE_KEY}/comment?orderBy=-created&startAt=${startAt}&maxResults=${pageSize}`);
    const comments = page.comments ?? [];
    const spec = comments.find(isSpec);
    if (spec) return spec;
    if (comments.length === 0 || startAt + comments.length >= (page.total ?? 0)) return null;
  }
}

/** Kommentar in Wiki-Markup (REST API v2). */
async function comment(text) {
  await jira('POST', `/rest/api/2/issue/${ISSUE_KEY}/comment`, { body: text });
}

/** Wechselt in den Status mit dem angegebenen Namen, falls ein solcher Übergang existiert. */
async function transition(statusName) {
  if (DRY_RUN) return jira('POST', `/rest/api/2/issue/${ISSUE_KEY}/transitions (→ ${statusName})`);
  const { transitions = [] } = await jira('GET', `/rest/api/2/issue/${ISSUE_KEY}/transitions`);
  const t = transitions.find((x) => x.to?.name === statusName || x.name === statusName);
  if (!t) {
    console.log(`Kein Übergang nach «${statusName}» verfügbar – Status bleibt unverändert.`);
    return;
  }
  await jira('POST', `/rest/api/2/issue/${ISSUE_KEY}/transitions`, { transition: { id: t.id } });
}

/** Entfernt das Auslöse-Label, damit ein erneutes Setzen einen neuen Lauf startet. */
async function removeTriggerLabel() {
  await jira('PUT', `/rest/api/2/issue/${ISSUE_KEY}`, { update: { labels: [{ remove: TRIGGER_LABEL }] } });
}

function slug(text) {
  return text.toLowerCase()
    .replace(/ä/g, 'ae').replace(/ö/g, 'oe').replace(/ü/g, 'ue').replace(/ß/g, 'ss')
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
    .slice(0, 40).replace(/-+$/, '') || 'aenderung';
}

function fail(message) {
  console.error(message);
  process.exit(1);
}

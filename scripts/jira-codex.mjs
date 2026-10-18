#!/usr/bin/env node
// JIRA-Anbindung für den Codex-Workflow (.github/workflows/codex-implement.yml).
//
//   node scripts/jira-codex.mjs start   – Spezifikation laden, Auftrag für Codex bauen,
//                                         Ticket auf «In Arbeit» setzen und Start kommentieren
//   node scripts/jira-codex.mjs done    – PR-Link ins Ticket schreiben, Auslöse-Label entfernen
//   node scripts/jira-codex.mjs failed  – Fehlschlag ins Ticket schreiben, Auslöse-Label entfernen
//
// Umgebung: JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN, ISSUE_KEY, RUN_URL
//   start:  PROMPT_TEMPLATE, PROMPT_OUT, GITHUB_OUTPUT (Branch-Name und Titel als Step-Outputs)
//   done:   PR_URL, BRANCH, CHANGED_FILES (eine Datei pro Zeile)
//   failed: FAILURE_REASON (optional)
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
  default: fail(`Unbekannter Befehl «${command}» (start | done | failed).`);
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

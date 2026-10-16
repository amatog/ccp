#!/usr/bin/env node
// Schreibt das Playwright-Testresultat als Kommentar ins JIRA-Ticket.
//
// Ticket-Schlüssel: aus JIRA_ISSUE oder aus der Commit-Message (erstes "CCP-123").
// Ohne JIRA-Secrets oder ohne Ticket-Schlüssel wird der Schritt übersprungen, nicht abgebrochen.

import { readFileSync, existsSync } from 'node:fs';

const {
  JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN,
  JIRA_ISSUE, COMMIT_MESSAGE = '',
  RESULTS_FILE = 'e2e/test-results/results.json',
  RUN_URL = '', COMMIT_SHA = '', APP_URL = '',
} = process.env;

const issueKey = (JIRA_ISSUE || COMMIT_MESSAGE.match(/\bCCP-\d+\b/)?.[0] || '').trim();
const DRY_RUN = process.env.DRY_RUN === '1';

if (!DRY_RUN && (!JIRA_BASE_URL || !JIRA_EMAIL || !JIRA_API_TOKEN)) {
  console.log('JIRA-Secrets fehlen – Report wird übersprungen.');
  process.exit(0);
}
if (!issueKey) {
  console.log('Kein JIRA-Ticket in Commit-Message oder Eingabe gefunden – Report wird übersprungen.');
  process.exit(0);
}

// --- Resultate einlesen ---------------------------------------------------
let stats = { expected: 0, unexpected: 0, flaky: 0, skipped: 0, duration: 0 };
const failed = [];

if (existsSync(RESULTS_FILE)) {
  const report = JSON.parse(readFileSync(RESULTS_FILE, 'utf8'));
  stats = { ...stats, ...report.stats };
  const walk = (suite, path = []) => {
    const here = suite.title ? [...path, suite.title] : path;
    for (const spec of suite.specs ?? []) {
      if (!spec.ok) failed.push([...here, spec.title].filter(Boolean).join(' › '));
    }
    for (const child of suite.suites ?? []) walk(child, here);
  };
  for (const suite of report.suites ?? []) walk(suite);
} else {
  console.log(`Keine Resultatdatei unter ${RESULTS_FILE} – melde fehlgeschlagenen Lauf.`);
  stats.unexpected = 1;
  failed.push('Testlauf konnte nicht ausgeführt werden (siehe GitHub-Actions-Log)');
}

const passed = stats.unexpected === 0;
const total = stats.expected + stats.unexpected + stats.flaky + stats.skipped;

// --- Kommentar im Atlassian Document Format ---------------------------------
const text = (t, marks) => ({ type: 'text', text: t, ...(marks ? { marks } : {}) });
const para = (...content) => ({ type: 'paragraph', content });
const bullet = (items) => ({
  type: 'bulletList',
  content: items.map((t) => ({ type: 'listItem', content: [para(text(t))] })),
});

const content = [
  {
    type: 'panel',
    attrs: { panelType: passed ? 'success' : 'error' },
    content: [para(text(passed ? '✅ Playwright-Tests bestanden' : '❌ Playwright-Tests fehlgeschlagen', [{ type: 'strong' }]))],
  },
  bullet([
    `Tests gesamt: ${total}`,
    `Bestanden: ${stats.expected}`,
    `Fehlgeschlagen: ${stats.unexpected}`,
    `Instabil (flaky): ${stats.flaky}`,
    `Übersprungen: ${stats.skipped}`,
    `Dauer: ${Math.round(stats.duration / 1000)} s`,
    `Commit: ${COMMIT_SHA.slice(0, 7)}`,
    `Getestete Instanz: ${APP_URL}`,
  ]),
];

if (failed.length) {
  content.push(para(text('Fehlgeschlagene Tests:', [{ type: 'strong' }])));
  content.push(bullet(failed));
}
if (RUN_URL) {
  content.push(para(
    text('Details und HTML-Report: '),
    text('GitHub-Actions-Lauf', [{ type: 'link', attrs: { href: RUN_URL } }]),
  ));
}

// --- An JIRA senden -----------------------------------------------------------
if (DRY_RUN) {
  console.log(`DRY_RUN: Kommentar für ${issueKey}:`);
  console.log(JSON.stringify({ body: { type: 'doc', version: 1, content } }, null, 2));
  process.exit(0);
}

const url = `${JIRA_BASE_URL.replace(/\/$/, '')}/rest/api/3/issue/${issueKey}/comment`;
const auth = Buffer.from(`${JIRA_EMAIL}:${JIRA_API_TOKEN}`).toString('base64');

const res = await fetch(url, {
  method: 'POST',
  headers: {
    Authorization: `Basic ${auth}`,
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
  body: JSON.stringify({ body: { type: 'doc', version: 1, content } }),
});

if (!res.ok) {
  console.error(`JIRA-Kommentar fehlgeschlagen (${res.status}): ${await res.text()}`);
  process.exit(1);
}
console.log(`Testresultat in ${issueKey} kommentiert (${passed ? 'bestanden' : 'fehlgeschlagen'}).`);

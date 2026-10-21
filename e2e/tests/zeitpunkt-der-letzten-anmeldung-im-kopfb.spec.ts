import { test, expect, Page } from '@playwright/test';
import { loginAndOpenTransactions } from './helpers';

// CCP-21: Zeitpunkt der letzten Anmeldung im Kopfbereich.
// Ausschliesslich mit der Demo-Kundin «nora», damit parallel laufende Tests
// anderer Dateien (anna, marco, lena) die Login-Historie nicht beeinflussen.
// Seriell, weil alle Tests dieselbe Login-Historie von Nora verändern.
test.describe.configure({ mode: 'serial' });

const USER = 'nora';
const ZEITPUNKT_MUSTER = /^\d{2}\.\d{2}\.\d{4}, \d{2}:\d{2}$/;
const ERSTE_ODER_ZEITPUNKT = /^(Erste Anmeldung|\d{2}\.\d{2}\.\d{4}, \d{2}:\d{2})$/;

/** Wandelt «TT.MM.JJJJ, HH:MM» in Minuten seit Epoche um (Wanduhrzeit, als UTC gelesen). */
function wanduhrMinuten(text: string): number {
  const m = /^(\d{2})\.(\d{2})\.(\d{4}), (\d{2}):(\d{2})$/.exec(text);
  if (!m) {
    throw new Error(`Unerwartetes Format: «${text}»`);
  }
  const [, tag, monat, jahr, stunde, minute] = m;
  return Date.UTC(Number(jahr), Number(monat) - 1, Number(tag), Number(stunde), Number(minute)) / 60000;
}

/** Aktuelle Wanduhrzeit in Europe/Zurich, in Minuten (als UTC gelesen). */
function jetztZurichMinuten(): number {
  const teile = new Intl.DateTimeFormat('de-CH', {
    timeZone: 'Europe/Zurich',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date());
  const wert = (typ: string) => Number(teile.find((t) => t.type === typ)?.value);
  return (
    Date.UTC(wert('year'), wert('month') - 1, wert('day'), wert('hour'), wert('minute')) / 60000
  );
}

async function abmelden(page: Page) {
  await page.getByTestId('logout-button').click();
  await expect(page.getByTestId('logout-message')).toBeVisible();
}

test.describe('CCP-21 Letzte Anmeldung', () => {
  test('AK-1: Anzeige im Kopfbereich mit Beschriftung «Letzte Anmeldung»', async ({ page }) => {
    await loginAndOpenTransactions(page, USER);
    const summary = page.getByTestId('account-summary');
    const lastLogin = summary.getByTestId('last-login');
    await expect(lastLogin).toBeVisible();
    await expect(summary).toContainText('Letzte Anmeldung');
    await expect(lastLogin).toHaveText(ERSTE_ODER_ZEITPUNKT);
  });

  test('AK-2: Erneute Anmeldung zeigt die vorherige Anmeldung', async ({ page }) => {
    await loginAndOpenTransactions(page, USER);
    await abmelden(page);

    await loginAndOpenTransactions(page, USER);
    const lastLogin = page.getByTestId('last-login');
    await expect(lastLogin).not.toHaveText('Erste Anmeldung');
    await expect(lastLogin).toHaveText(ZEITPUNKT_MUSTER);

    const text = (await lastLogin.textContent())?.trim() ?? '';
    const differenz = jetztZurichMinuten() - wanduhrMinuten(text);
    // Höchstens 2 Minuten vor jetzt; -1 Minute Toleranz für Uhrabweichung zwischen Server und Testrechner.
    expect(differenz).toBeLessThanOrEqual(2);
    expect(differenz).toBeGreaterThanOrEqual(-1);
  });

  test('AK-3: Wert bleibt während der Sitzung gleich', async ({ page }) => {
    await loginAndOpenTransactions(page, USER);
    const lastLogin = page.getByTestId('last-login');
    await expect(lastLogin).toHaveText(ERSTE_ODER_ZEITPUNKT);
    const x = (await lastLogin.textContent())?.trim() ?? '';

    await page.reload();
    await expect(page.getByTestId('transactions-table')).toBeVisible();
    await expect(lastLogin).toHaveText(x);

    await page.getByTestId('filter-from').fill('2026-10-01');
    await page.getByTestId('filter-to').fill('2026-10-31');
    await page.getByTestId('filter-submit').click();
    await expect(page.getByTestId('filter-reset')).toBeVisible();
    await expect(lastLogin).toHaveText(x);

    await page.getByTestId('filter-reset').click();
    await expect(page.getByTestId('filter-reset')).toHaveCount(0);
    await expect(lastLogin).toHaveText(x);
  });

  test('AK-4: Kopfbereich für Nora stimmt', async ({ page }) => {
    await loginAndOpenTransactions(page, USER);
    await expect(page.getByTestId('customer-name')).toHaveText('Nora Keller');
    await expect(page.getByTestId('card-number')).toHaveText('**** **** **** 5151');
    await expect(page.getByTestId('balance-total')).toHaveText('-22.50 CHF');
    await expect(page.getByTestId('count-debits')).toHaveText('2 Belastungen');
    await expect(page.getByTestId('count-credits')).toHaveText('1 Gutschrift');

    const rows = page.getByTestId('transaction-row');
    await expect(rows).toHaveCount(3);
    await expect(rows.nth(0)).toHaveAttribute('data-transaction-id', 'N-4003');
    await expect(rows.nth(1)).toHaveAttribute('data-transaction-id', 'N-4002');
    await expect(rows.nth(2)).toHaveAttribute('data-transaction-id', 'N-4001');
  });
});

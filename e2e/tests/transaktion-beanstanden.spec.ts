import { test, expect, Page } from '@playwright/test';
import { login, loginAndOpenTransactions } from './helpers';

// CCP-28: Transaktion beanstanden.
// Jeder Test entspricht einem Akzeptanzkriterium aus der Spezifikation im JIRA-Ticket.
// Beanstandungen liegen in der HTTP-Session: Jeder Test meldet sich selbst an und
// startet damit mit einer leeren Sitzung. Die Tests hängen nicht voneinander ab.

const SUCCESS_TEXT = 'Ihre Beanstandung wurde erfasst. Die Transaktion ist jetzt in Abklärung.';

/** Zeile einer Transaktion über ihre ID. */
function row(page: Page, id: string) {
  return page.locator(`[data-transaction-id="${id}"]`);
}

/** Setzt Von/Bis (ISO-Datum) und optional die Kategorie und sendet den Filter ab. */
async function filter(page: Page, from: string, to: string, category?: string) {
  await page.getByTestId('filter-from').fill(from);
  await page.getByTestId('filter-to').fill(to);
  if (category !== undefined) {
    await page.getByTestId('filter-category').selectOption({ label: category });
  }
  await page.getByTestId('filter-submit').click();
  await expect(page).toHaveURL(/\/transactions\?/);
  await page.waitForLoadState('load');
}

/** Öffnet das Beanstandungsformular über den Button in der Zeile der Transaktion. */
async function openDisputeForm(page: Page, id: string) {
  await row(page, id).getByTestId('dispute-button').click();
  await expect(page).toHaveURL(new RegExp(`/transactions/${id}/dispute(\\?.*)?$`));
  await expect(page.getByTestId('dispute-form')).toBeVisible();
  // Das Inline-Script, das den Absende-Button steuert, steht am Seitenende.
  await page.waitForLoadState('load');
}

/** Beanstandet eine Transaktion über die Oberfläche und wartet auf die Übersicht. */
async function dispute(page: Page, id: string, reason: string, comment = '') {
  await openDisputeForm(page, id);
  await page.getByTestId('dispute-reason').selectOption({ label: reason });
  if (comment !== '') {
    await page.getByTestId('dispute-comment').fill(comment);
  }
  await page.getByTestId('dispute-submit').click();
  await expect(page).toHaveURL(/\/transactions(\?.*)?$/);
  await expect(page.getByTestId('dispute-success')).toHaveText(SUCCESS_TEXT);
  await page.waitForLoadState('load');
}

/** Prüft, dass die Zeile als «In Abklärung» markiert ist und keinen Button mehr hat. */
async function expectDisputed(page: Page, id: string) {
  await expect(row(page, id).getByTestId('dispute-status')).toHaveText('In Abklärung');
  await expect(row(page, id).getByTestId('dispute-button')).toHaveCount(0);
}

/** Prüft, dass die Zeile beanstandbar ist (Button, kein Status). */
async function expectDisputable(page: Page, id: string) {
  await expect(row(page, id).getByTestId('dispute-button')).toHaveCount(1);
  await expect(row(page, id).getByTestId('dispute-button')).toBeVisible();
  await expect(row(page, id).getByTestId('dispute-status')).toHaveCount(0);
}

test.describe('CCP-28 Transaktion beanstanden', () => {
  test('AK-1: Button nur bei Belastungen', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filter(page, '2026-10-01', '2026-10-31');

    for (const id of ['A-1006', 'A-1005', 'A-1004', 'A-1002', 'A-1001']) {
      const button = row(page, id).getByTestId('dispute-button');
      await expect(button).toHaveCount(1);
      await expect(button).toBeVisible();
      await expect(button).toHaveText('Beanstanden');
    }

    const credit = row(page, 'A-1003');
    await expect(credit).toHaveAttribute('data-transaction-type', 'CREDIT');
    await expect(credit.getByTestId('dispute-button')).toHaveCount(0);
    await expect(credit.getByTestId('dispute-status')).toHaveCount(0);

    await expect(page.getByTestId('dispute-status')).toHaveCount(0);
  });

  test('AK-2: Formular öffnet mit den Transaktionsdaten', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await openDisputeForm(page, 'A-1005');

    // Filterparameter fehlen oder sind leer.
    const url = new URL(page.url());
    expect(url.pathname).toBe('/transactions/A-1005/dispute');
    for (const param of ['from', 'to', 'category']) {
      expect(url.searchParams.get(param) ?? '').toBe('');
    }

    const details = page.getByTestId('dispute-transaction');
    await expect(details).toContainText('12.10.2026');
    await expect(details).toContainText('Digitec Galaxus');
    await expect(details).toContainText('-349.00 CHF');

    const reason = page.getByTestId('dispute-reason');
    await expect(reason).toHaveValue('');
    const options = await reason.evaluate(el =>
      Array.from((el as HTMLSelectElement).options).map(o => o.text.trim()));
    expect(options).toEqual([
      'Bitte Grund wählen',
      'Unbekannte Transaktion',
      'Doppelt belastet',
      'Falscher Betrag',
    ]);

    await expect(page.getByTestId('dispute-comment')).toHaveValue('');
    await expect(page.getByTestId('dispute-submit')).toBeDisabled();
  });

  test('AK-3: Ohne Grund kein Absenden', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await openDisputeForm(page, 'A-1005');

    const reason = page.getByTestId('dispute-reason');
    const submit = page.getByTestId('dispute-submit');
    await expect(submit).toBeDisabled();

    await reason.selectOption({ label: 'Doppelt belastet' });
    await expect(submit).toBeEnabled();

    await reason.selectOption({ label: 'Bitte Grund wählen' });
    await expect(submit).toBeDisabled();
    await expect(page).toHaveURL(/\/transactions\/A-1005\/dispute(\?.*)?$/);
    await expect(page.getByTestId('dispute-form')).toBeVisible();
  });

  test('AK-4: Beanstanden mit Grund und Kommentar', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await openDisputeForm(page, 'A-1005');

    await page.getByTestId('dispute-reason').selectOption({ label: 'Falscher Betrag' });
    await page.getByTestId('dispute-comment').fill('Laut Quittung 249.00 CHF');
    await page.getByTestId('dispute-submit').click();

    await expect(page).toHaveURL(/\/transactions$/);
    await expect(page.getByTestId('dispute-success')).toHaveText(SUCCESS_TEXT);
    await expectDisputed(page, 'A-1005');
    await expectDisputable(page, 'A-1004');
  });

  test('AK-5: Beanstanden ohne Kommentar', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await openDisputeForm(page, 'A-1002');

    await page.getByTestId('dispute-reason').selectOption({ label: 'Unbekannte Transaktion' });
    await expect(page.getByTestId('dispute-comment')).toHaveValue('');
    await page.getByTestId('dispute-submit').click();

    await expect(page).toHaveURL(/\/transactions(\?.*)?$/);
    await expect(page.getByTestId('dispute-success')).toBeVisible();
    await expect(row(page, 'A-1002').getByTestId('dispute-status')).toHaveText('In Abklärung');
  });

  test('AK-6: Status bleibt nach Neuladen und Filtern erhalten', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await dispute(page, 'A-1005', 'Falscher Betrag', 'Laut Quittung 249.00 CHF');

    await page.reload();
    await expect(page.getByTestId('transactions-table')).toBeVisible();
    await expectDisputed(page, 'A-1005');
    await expect(page.getByTestId('dispute-success')).toHaveCount(0);

    await page.getByTestId('filter-category').selectOption({ label: 'Elektronik' });
    await page.getByTestId('filter-submit').click();
    await expect(page).toHaveURL(/\/transactions\?.*category=Elektronik/);
    await expect(page.getByTestId('filter-category')).toHaveValue('Elektronik');
    await expect(row(page, 'A-1005').getByTestId('dispute-status')).toHaveText('In Abklärung');
  });

  test('AK-7: Bereits beanstandete Transaktion nicht erneut beanstandbar', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await dispute(page, 'A-1005', 'Falscher Betrag');

    await page.goto('/transactions/A-1005/dispute');
    await expect(page).toHaveURL(/\/transactions$/);
    await expect(page.getByTestId('dispute-error'))
      .toHaveText('Diese Transaktion ist bereits in Abklärung.');
    await expect(page.getByTestId('dispute-form')).toHaveCount(0);
    await expect(row(page, 'A-1005').getByTestId('dispute-status')).toHaveText('In Abklärung');
  });

  test('AK-8: Gutschrift nicht beanstandbar', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');

    await page.goto('/transactions/A-1003/dispute');
    await expect(page).toHaveURL(/\/transactions$/);
    await expect(page.getByTestId('dispute-error'))
      .toHaveText('Gutschriften können nicht beanstandet werden.');
    await expect(page.getByTestId('dispute-form')).toHaveCount(0);
    await expect(row(page, 'A-1003')).toHaveCount(1);
    await expect(row(page, 'A-1003').getByTestId('dispute-status')).toHaveCount(0);
  });

  test('AK-9: Abbrechen speichert nichts und behält den Filter', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filter(page, '2026-10-01', '2026-10-31');
    await openDisputeForm(page, 'A-1005');

    await page.getByTestId('dispute-reason').selectOption({ label: 'Doppelt belastet' });
    await page.getByTestId('dispute-cancel').click();

    await expect(page).toHaveURL(/\/transactions(\?.*)?$/);
    await expect(page.getByTestId('transactions-table')).toBeVisible();
    await expect(page.getByTestId('filter-from')).toHaveValue('2026-10-01');
    await expect(page.getByTestId('filter-to')).toHaveValue('2026-10-31');
    await expectDisputable(page, 'A-1005');
    await expect(page.getByTestId('dispute-success')).toHaveCount(0);
  });

  test('AK-10: Filter bleibt nach dem Absenden erhalten', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    await filter(page, '2026-10-01', '2026-10-31', 'Reisen');

    await expect(row(page, 'M-2002').getByTestId('tx-merchant')).toHaveText('Hotel Alpenblick');
    await expect(row(page, 'M-2002').getByTestId('tx-amount')).toContainText('-420.00 CHF');
    await dispute(page, 'M-2002', 'Doppelt belastet');

    await expect(page.getByTestId('filter-category')).toHaveValue('Reisen');
    await expect(page.getByTestId('filter-from')).toHaveValue('2026-10-01');
    await expect(page.getByTestId('filter-to')).toHaveValue('2026-10-31');
    await expect(page.getByTestId('filter-summary'))
      .toHaveText('2 Transaktionen in der Kategorie Reisen, Summe: -340.00 CHF');

    await expect(row(page, 'M-2002').getByTestId('dispute-status')).toHaveText('In Abklärung');
    await expect(row(page, 'M-2003')).toHaveCount(1);
    await expect(row(page, 'M-2003').getByTestId('dispute-button')).toHaveCount(0);
    await expect(row(page, 'M-2003').getByTestId('dispute-status')).toHaveCount(0);
  });

  test('AK-11: Transaktion eines anderen Kunden ergibt 404', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');

    const response = await page.goto('/transactions/M-2002/dispute');
    expect(response?.status()).toBe(404);
    await expect(page.getByTestId('dispute-form')).toHaveCount(0);
  });

  test('AK-11: Nicht angemeldet führt auf die Login-Seite', async ({ page }) => {
    // Frischer Browser-Kontext ohne Anmeldung.
    await page.goto('/transactions/A-1005/dispute');
    await expect(page.getByTestId('login-username')).toBeVisible();
    await expect(page.getByTestId('dispute-form')).toHaveCount(0);
  });

  test('AK-12: Kennzahlen bleiben unverändert', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filter(page, '2026-10-01', '2026-10-31');
    await dispute(page, 'A-1005', 'Falscher Betrag');

    await expect(page.getByTestId('filter-summary'))
      .toHaveText('6 Transaktionen im Zeitraum, Summe: -628.95 CHF');
    await expect(page.getByTestId('balance-total')).toHaveText("-1'382.07 CHF");
    await expect(page.getByTestId('count-debits')).toHaveText('142 Belastungen');
    await expect(page.getByTestId('count-credits')).toHaveText('13 Gutschriften');
  });

  test('AK-13: Status gilt pro Sitzung', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await dispute(page, 'A-1005', 'Falscher Betrag');
    await expectDisputed(page, 'A-1005');

    await page.getByTestId('logout-button').click();
    await expect(page.getByTestId('logout-message')).toBeVisible();

    await login(page, 'anna');
    await expect(page).toHaveURL(/\/transactions$/);
    await expect(page.getByTestId('transactions-table')).toBeVisible();
    await expectDisputable(page, 'A-1005');
  });
});

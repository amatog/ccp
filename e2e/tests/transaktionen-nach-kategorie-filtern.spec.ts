import { test, expect, Page } from '@playwright/test';
import { login, loginAndOpenTransactions } from './helpers';

// CCP-19: Transaktionen nach Kategorie filtern.
// Jeder Test entspricht einem Akzeptanzkriterium aus der Spezifikation im JIRA-Ticket.

/** Erwartete Optionen im Kategorie-Dropdown: «Alle Kategorien», danach alphabetisch. */
const KATEGORIE_OPTIONEN = [
  'Alle Kategorien',
  'Elektronik',
  'Gastronomie',
  'Lebensmittel',
  'Mobilität',
  'Reisen',
  'Shopping',
  'Telekom',
  'Zahlung',
];

const ANNA_ANZAHL = 155;
const MARCO_ANZAHL = 146;

/**
 * Setzt Von/Bis (ISO-Datum, leer = Feld leer lassen) und die Kategorie
 * (Kategoriename, leer = «Alle Kategorien») und sendet den Filter mit «Filtern» ab.
 */
async function filter(page: Page, from: string, to: string, category: string) {
  await page.getByTestId('filter-from').fill(from);
  await page.getByTestId('filter-to').fill(to);
  await page.getByTestId('filter-category').selectOption(category);
  await page.getByTestId('filter-submit').click();
  await expect(page).toHaveURL(/\/transactions\?/);
}

/** Prüft, dass genau diese Transaktionen in genau dieser Reihenfolge angezeigt werden. */
async function expectTransactionIds(page: Page, ids: string[]) {
  const rows = page.getByTestId('transaction-row');
  await expect(rows).toHaveCount(ids.length);
  for (const [i, id] of ids.entries()) {
    await expect(rows.nth(i)).toHaveAttribute('data-transaction-id', id);
  }
}

/** Liefert den sichtbaren Text der aktuell gewählten Option im Kategorie-Dropdown. */
async function selectedCategoryLabel(page: Page): Promise<string> {
  return page.getByTestId('filter-category').evaluate(
    (el) => {
      const select = el as HTMLSelectElement;
      return select.options[select.selectedIndex]?.text.trim() ?? '';
    },
  );
}

test.describe('CCP-19 Transaktionen nach Kategorie filtern', () => {
  test('AK-1: Standard ist «Alle Kategorien»', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');

    const select = page.getByTestId('filter-category');
    await expect(select).toHaveValue('');
    await expect.poll(() => selectedCategoryLabel(page)).toBe('Alle Kategorien');

    // Das Dropdown zeigt immer alle acht Kategorien alphabetisch nach «Alle Kategorien».
    await expect
      .poll(() =>
        select.evaluate((el) =>
          Array.from((el as HTMLSelectElement).options).map((o) => o.text.trim()),
        ),
      )
      .toEqual(KATEGORIE_OPTIONEN);

    await expect(page.getByTestId('transaction-row')).toHaveCount(ANNA_ANZAHL);
    await expect(page.getByTestId('filter-summary')).toHaveCount(0);
    await expect(page.getByTestId('filter-reset')).toHaveCount(0);
  });

  test('AK-2: Nur Kategorie', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filter(page, '', '', 'Elektronik');

    await expect(page).toHaveURL(/\/transactions\?(.*&)?category=Elektronik(&|$)/);
    await expectTransactionIds(page, [
      'A-1005', 'A-0137', 'A-0087', 'A-0072', 'A-0032', 'A-0020', 'A-0015',
    ]);

    const badges = page.getByTestId('transactions-table').getByTestId('category-badge');
    await expect(badges).toHaveCount(7);
    for (let i = 0; i < 7; i++) {
      await expect(badges.nth(i)).toHaveText('Elektronik');
    }

    await expect(page.getByTestId('filter-summary'))
      .toHaveText("7 Transaktionen in der Kategorie Elektronik, Summe: -1'650.02 CHF");
  });

  test('AK-3: Kategorie mit Gutschriften', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filter(page, '', '', 'Zahlung');

    const rows = page.getByTestId('transaction-row');
    await expect(rows).toHaveCount(11);
    for (let i = 0; i < 11; i++) {
      await expect(rows.nth(i)).toHaveAttribute('data-transaction-type', 'CREDIT');
    }

    await expect(page.getByTestId('filter-summary'))
      .toHaveText("11 Transaktionen in der Kategorie Zahlung, Summe: 8'904.51 CHF");
  });

  test('AK-4: Kategorie und Zeitraum kombiniert', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    await filter(page, '2026-10-01', '2026-10-31', 'Reisen');

    await expectTransactionIds(page, ['M-2003', 'M-2002']);
    await expect(page.getByTestId('filter-summary'))
      .toHaveText('2 Transaktionen in der Kategorie Reisen, Summe: -340.00 CHF');
  });

  test('AK-5: Kategorie mit Umlaut', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    await filter(page, '', '', 'Mobilität');

    await expect(page).toHaveURL(/category=Mobilit%C3%A4t(&|$)/);
    const rows = page.getByTestId('transaction-row');
    await expect(rows).toHaveCount(30);
    await expect(rows.first()).toHaveAttribute('data-transaction-id', 'M-2004');

    await expect(page.getByTestId('filter-summary'))
      .toHaveText("30 Transaktionen in der Kategorie Mobilität, Summe: -1'940.36 CHF");
  });

  test('AK-6: Kategorie ohne Treffer im Zeitraum', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filter(page, '2026-10-01', '2026-10-10', 'Elektronik');

    await expect(page.getByTestId('transactions-table')).toHaveCount(0);
    await expect(page.getByTestId('transaction-row')).toHaveCount(0);
    await expect(page.getByTestId('filter-no-results'))
      .toHaveText('Keine Transaktionen gefunden.');
    await expect(page.getByTestId('filter-summary')).toHaveCount(0);
  });

  test('AK-7: Unbekannte Kategorie in der URL', async ({ page }) => {
    await login(page, 'anna');
    await expect(page).toHaveURL(/\/transactions$/);

    const response = await page.goto('/transactions?category=Haustiere');
    expect(response?.status()).toBe(200);
    await expect(page.getByTestId('filter-error')).toHaveText('Unbekannte Kategorie.');
    await expect(page.getByTestId('transaction-row')).toHaveCount(ANNA_ANZAHL);
    await expect(page.getByTestId('filter-summary')).toHaveCount(0);
  });

  test('AK-8: Ungültiger Zeitraum hebt auch den Kategoriefilter auf', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await filter(page, '2026-10-10', '2026-10-05', 'Elektronik');

    await expect(page.getByTestId('filter-error'))
      .toHaveText('Das Von-Datum darf nicht nach dem Bis-Datum liegen.');
    await expect(page.getByTestId('transaction-row')).toHaveCount(ANNA_ANZAHL);
  });

  test('AK-9: Auswahl bleibt sichtbar und lässt sich zurücksetzen', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    await filter(page, '2026-10-01', '2026-10-31', 'Reisen');

    await expect(page.getByTestId('filter-category')).toHaveValue('Reisen');
    await expect.poll(() => selectedCategoryLabel(page)).toBe('Reisen');
    await expect(page.getByTestId('filter-from')).toHaveValue('2026-10-01');
    await expect(page.getByTestId('filter-to')).toHaveValue('2026-10-31');

    await page.getByTestId('filter-reset').click();
    await expect(page).toHaveURL(/\/transactions$/);
    await expect(page.getByTestId('filter-category')).toHaveValue('');
    await expect.poll(() => selectedCategoryLabel(page)).toBe('Alle Kategorien');
    await expect(page.getByTestId('filter-from')).toHaveValue('');
    await expect(page.getByTestId('filter-to')).toHaveValue('');
    await expect(page.getByTestId('transaction-row')).toHaveCount(MARCO_ANZAHL);
  });

  test('AK-10: Kopfbereich bleibt Gesamtkonto', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    await filter(page, '', '', 'Reisen');

    // Sicherstellen, dass der Kategoriefilter tatsächlich aktiv ist.
    await expect(page.getByTestId('filter-summary')).toBeVisible();

    await expect(page.getByTestId('balance-total')).toHaveText("-1'040.68 CHF");
    await expect(page.getByTestId('count-debits')).toHaveText('130 Belastungen');
    await expect(page.getByTestId('count-credits')).toHaveText('16 Gutschriften');
  });

  test('AK-11: Datenschutz bleibt gewahrt', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    await filter(page, '', '', 'Elektronik');

    await expectTransactionIds(page, ['M-0122', 'M-0114', 'M-0020']);
    await expect(page.locator('[data-transaction-id^="A-"]')).toHaveCount(0);
    await expect(page.locator('[data-transaction-id^="L-"]')).toHaveCount(0);
  });
});

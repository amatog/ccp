import { test, expect, Page } from '@playwright/test';
import { loginAndOpenTransactions } from './helpers';

/** Liefert das Kategorie-Badge einer Transaktionszeile. */
function badgeOf(page: Page, transactionId: string) {
  return page
    .locator(`[data-transaction-id="${transactionId}"]`)
    .getByTestId('tx-category')
    .getByTestId('category-badge');
}

test.describe('CCP-11 Kategorie als farbiges Etikett', () => {
  test('AK-1: Jede Zeile enthält in der Kategoriezelle genau ein Badge', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    const rows = page.getByTestId('transaction-row');
    await expect(rows).toHaveCount(155);
    // Stichprobe: die sechs neuesten Zeilen (Oktober 2026)
    for (let i = 0; i < 6; i++) {
      await expect(rows.nth(i).getByTestId('tx-category').getByTestId('category-badge')).toHaveCount(1);
    }
  });

  test('AK-2: Badge zeigt die Kategorie als Text', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await expect(badgeOf(page, 'A-1001')).toHaveText('Lebensmittel');
    await expect(badgeOf(page, 'A-1002')).toHaveText('Reisen');
    await expect(badgeOf(page, 'A-1004')).toHaveText('Gastronomie');
    await expect(badgeOf(page, 'A-1005')).toHaveText('Elektronik');
  });

  test('AK-3: Badge trägt einen maschinenlesbaren Kategorie-Schlüssel', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await expect(badgeOf(page, 'A-1001')).toHaveAttribute('data-category', 'lebensmittel');
    await expect(badgeOf(page, 'A-1006')).toHaveAttribute('data-category', 'telekom');
  });

  test('AK-4: Hintergrundfarbe gemäss Kategorie', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await expect(badgeOf(page, 'A-1001')).toHaveCSS('background-color', 'rgb(220, 252, 231)');
    await expect(badgeOf(page, 'A-1002')).toHaveCSS('background-color', 'rgb(219, 234, 254)');
  });

  test('AK-5: Gleiche Kategorie hat bei Belastung und Gutschrift dieselbe Farbe', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');
    const debit = badgeOf(page, 'M-2002');
    const credit = badgeOf(page, 'M-2003');
    await expect(page.locator('[data-transaction-id="M-2002"]')).toHaveAttribute('data-transaction-type', 'DEBIT');
    await expect(page.locator('[data-transaction-id="M-2003"]')).toHaveAttribute('data-transaction-type', 'CREDIT');
    await expect(debit).toHaveAttribute('data-category', 'reisen');
    await expect(credit).toHaveAttribute('data-category', 'reisen');

    const debitColor = await debit.evaluate((el) => getComputedStyle(el).backgroundColor);
    await expect(credit).toHaveCSS('background-color', debitColor);
    // Farbe entspricht zudem der Tabelle (Reisen = #DBEAFE)
    await expect(debit).toHaveCSS('background-color', 'rgb(219, 234, 254)');
  });

  test('AK-6: Badges bleiben nach dem Zeitraumfilter erhalten', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    await page.getByTestId('filter-from').fill('2026-10-01');
    await page.getByTestId('filter-to').fill('2026-10-05');
    await page.getByTestId('filter-submit').click();

    const rows = page.getByTestId('transaction-row');
    await expect(rows).toHaveCount(2);
    await expect(page.locator('[data-transaction-id="A-1001"]')).toHaveCount(1);
    await expect(page.locator('[data-transaction-id="A-1002"]')).toHaveCount(1);
    await expect(badgeOf(page, 'A-1001')).toHaveCount(1);
    await expect(badgeOf(page, 'A-1002')).toHaveCount(1);
    await expect(badgeOf(page, 'A-1001')).toHaveText('Lebensmittel');
    await expect(badgeOf(page, 'A-1002')).toHaveText('Reisen');
  });

  test('AK-7: Kategoriezelle bleibt erhalten und enthält den Kategorietext', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    const row = page.locator('[data-transaction-id="A-1001"]');
    await expect(row.getByTestId('tx-category')).toHaveCount(1);
    await expect(row.getByTestId('tx-category')).toHaveText('Lebensmittel');
    await expect(page.locator('[data-transaction-id="A-1006"]').getByTestId('tx-category')).toHaveText('Telekom');
  });
});

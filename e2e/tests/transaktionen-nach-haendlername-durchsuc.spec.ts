import { test, expect, Page } from '@playwright/test';
import { loginAndOpenTransactions } from './helpers';

// CCP-15: Transaktionen nach Händlername durchsuchen (clientseitige Live-Suche)

/** Zeile anhand der Transaktions-ID. */
function row(page: Page, id: string) {
  return page.locator(`[data-transaction-id="${id}"]`);
}

/** Anzahl der nicht ausgeblendeten Transaktionszeilen (ausgeblendete bleiben im DOM). */
async function visibleRowCount(page: Page): Promise<number> {
  return page
    .getByTestId('transaction-row')
    .evaluateAll((els) => els.filter((el) => !(el as HTMLElement).hidden).length);
}

/** Händlernamen aller nicht ausgeblendeten Zeilen in Anzeigereihenfolge. */
async function visibleMerchants(page: Page): Promise<string[]> {
  return page
    .getByTestId('transaction-row')
    .evaluateAll((els) =>
      els
        .filter((el) => !(el as HTMLElement).hidden)
        .map((el) => (el.querySelector('[data-testid="tx-merchant"]')?.textContent ?? '').trim()),
    );
}

test.describe('CCP-15 Händlersuche', () => {
  test('AK-1: Suchfeld ist über der Tabelle sichtbar und leer, alle Zeilen sichtbar', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');

    const search = page.getByTestId('merchant-search');
    await expect(search).toBeVisible();
    await expect(search).toHaveValue('');
    await expect(search).toHaveAccessibleName('Händler suchen');
    await expect(search).toHaveAttribute('type', 'search');
    await expect(search).toHaveAttribute('placeholder', 'z. B. Migros');

    // Ausserhalb von Filter- und Exportformular
    await expect(page.getByTestId('filter-form').getByTestId('merchant-search')).toHaveCount(0);
    await expect(page.getByTestId('export-form').getByTestId('merchant-search')).toHaveCount(0);

    // Über der Tabelle
    const searchBox = await search.boundingBox();
    const tableBox = await page.getByTestId('transactions-table').boundingBox();
    expect(searchBox).not.toBeNull();
    expect(tableBox).not.toBeNull();
    expect(searchBox!.y).toBeLessThan(tableBox!.y);

    await expect(page.getByTestId('transaction-row')).toHaveCount(155);
    expect(await visibleRowCount(page)).toBe(155);
    await expect(page.getByTestId('merchant-search-count')).toBeHidden();
    await expect(page.getByTestId('merchant-search-no-results')).toBeHidden();
  });

  test('AK-2: Suche «migros» / «MIGROS» ohne Gross-/Kleinschreibung zeigt 9 Treffer', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    const search = page.getByTestId('merchant-search');
    const count = page.getByTestId('merchant-search-count');

    await search.fill('migros');
    await expect(count).toBeVisible();
    await expect(count).toHaveText('9 Treffer');
    await expect(row(page, 'A-1001')).toBeVisible();
    await expect(row(page, 'A-1002')).toBeHidden();
    // Ausgeblendete Zeilen bleiben im DOM
    await expect(page.getByTestId('transaction-row')).toHaveCount(155);
    expect(await visibleRowCount(page)).toBe(9);
    const merchants = await visibleMerchants(page);
    expect(merchants).toHaveLength(9);
    expect(merchants.every((m) => m === 'Migros Zürich')).toBe(true);

    await search.fill('MIGROS');
    await expect(count).toHaveText('9 Treffer');
    await expect(row(page, 'A-1001')).toBeVisible();
    await expect(row(page, 'A-1002')).toBeHidden();
    expect(await visibleMerchants(page)).toEqual(merchants);
  });

  test('AK-2: Marco durchsucht nur seine eigenen Transaktionen', async ({ page }) => {
    await loginAndOpenTransactions(page, 'marco');

    await page.getByTestId('merchant-search').fill('migros');
    await expect(page.getByTestId('merchant-search-count')).toHaveText('15 Treffer');
    await expect(row(page, 'A-1001')).toHaveCount(0);
    const merchants = await visibleMerchants(page);
    expect(merchants).toHaveLength(15);
    expect(merchants.every((m) => m === 'Migros Marktgasse')).toBe(true);
  });

  test('AK-3: Teilstring mit Umlaut «zürich» / «ZÜRICH» zeigt 34 Treffer', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    const search = page.getByTestId('merchant-search');
    const count = page.getByTestId('merchant-search-count');

    await search.fill('zürich');
    await expect(count).toHaveText('34 Treffer');
    const merchants = await visibleMerchants(page);
    expect(merchants).toHaveLength(34);
    expect(merchants.filter((m) => m === 'Coop City Zürich')).toHaveLength(18);
    expect(merchants.filter((m) => m === 'Migros Zürich')).toHaveLength(9);
    expect(merchants.filter((m) => m === 'Manor Zürich')).toHaveLength(7);

    await search.fill('ZÜRICH');
    await expect(count).toHaveText('34 Treffer');
    expect(await visibleMerchants(page)).toEqual(merchants);
  });

  test('AK-4: Führende und nachfolgende Leerzeichen werden ignoriert', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');

    await page.getByTestId('merchant-search').fill('  migros  ');
    await expect(page.getByTestId('merchant-search-count')).toHaveText('9 Treffer');
    await expect(row(page, 'A-1001')).toBeVisible();
    await expect(row(page, 'A-1002')).toBeHidden();
  });

  test('AK-5: Leeres Suchfeld zeigt wieder alle Transaktionen', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    const search = page.getByTestId('merchant-search');
    const count = page.getByTestId('merchant-search-count');

    await search.fill('migros');
    await expect(count).toHaveText('9 Treffer');
    await expect(row(page, 'A-1002')).toBeHidden();

    await search.fill('');
    await expect(row(page, 'A-1002')).toBeVisible();
    await expect(row(page, 'A-1001')).toBeVisible();
    expect(await visibleRowCount(page)).toBe(155);
    await expect(count).toBeHidden();
    await expect(page.getByTestId('merchant-search-no-results')).toBeHidden();
    await expect(page.getByTestId('transactions-table')).toBeVisible();
  });

  test('AK-6: Ohne Treffer erscheint «Keine Transaktionen gefunden», Tabelle ausgeblendet', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    const search = page.getByTestId('merchant-search');
    const noResults = page.getByTestId('merchant-search-no-results');
    const table = page.getByTestId('transactions-table');

    await search.fill('xyz');
    await expect(noResults).toBeVisible();
    await expect(noResults).toContainText('Keine Transaktionen gefunden');
    await expect(noResults).toHaveAttribute('role', 'status');
    await expect(noResults).toHaveAttribute('aria-live', 'polite');
    await expect(table).toBeHidden();
    await expect(page.getByTestId('merchant-search-count')).toBeHidden();
    expect(await visibleRowCount(page)).toBe(0);
    // Suchfeld bleibt bedienbar
    await expect(search).toBeVisible();

    await search.fill('');
    await expect(table).toBeVisible();
    await expect(noResults).toBeHidden();
    await expect(page.getByTestId('transaction-row')).toHaveCount(155);
    expect(await visibleRowCount(page)).toBe(155);
  });

  test('AK-7: Suche wirkt zusätzlich zum Zeitraumfilter ab 01.10.2026', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');

    await page.getByTestId('filter-from').fill('2026-10-01');
    await page.getByTestId('filter-submit').click();
    await expect(page).toHaveURL(/from=2026-10-01/);
    await expect(page.getByTestId('filter-summary')).toBeVisible();

    const search = page.getByTestId('merchant-search');
    // Nach dem Reload ist das Suchfeld leer
    await expect(search).toHaveValue('');

    await search.fill('migros');
    await expect(page.getByTestId('merchant-search-count')).toHaveText('1 Treffer');

    const a1001 = row(page, 'A-1001');
    await expect(a1001).toBeVisible();
    await expect(a1001.getByTestId('tx-date')).toHaveText('02.10.2026');
    await expect(a1001.getByTestId('tx-merchant')).toHaveText('Migros Zürich');
    await expect(a1001.getByTestId('tx-amount')).toContainText('-84.35 CHF');
    expect(await visibleRowCount(page)).toBe(1);
    expect(await visibleMerchants(page)).toEqual(['Migros Zürich']);
  });

  test('AK-8: Tippen und Enter im Suchfeld lösen keinen Reload aus', async ({ page }) => {
    await loginAndOpenTransactions(page, 'anna');
    const urlBefore = page.url();

    let navigations = 0;
    page.on('request', (request) => {
      if (request.isNavigationRequest()) navigations++;
    });

    const search = page.getByTestId('merchant-search');
    await search.pressSequentially('migros');
    await expect(page.getByTestId('merchant-search-count')).toHaveText('9 Treffer');
    expect(page.url()).toBe(urlBefore);

    await search.press('Enter');
    // Bei einem Reload wäre das Feld leer und die Trefferzahl verschwunden
    await expect(search).toHaveValue('migros');
    await expect(page.getByTestId('merchant-search-count')).toHaveText('9 Treffer');
    await expect(row(page, 'A-1002')).toBeHidden();
    expect(page.url()).toBe(urlBefore);
    expect(navigations).toBe(0);
  });
});

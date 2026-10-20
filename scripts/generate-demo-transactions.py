"""Erzeugt fiktive Demo-Transaktionen 20.10.2025 – 30.09.2026 plus die bestehenden Oktober-2026-Daten.
Aufruf: python3 scripts/generate-demo-transactions.py src/main/resources/demo/transactions.csv


Regeln:
- Oktober 2026 (bestehende IDs A-1001…, M-2001…, L-3001…) bleibt unverändert.
- Historische Belastungen sind höchstens CHF 495.00 (keine neuen CCP-13-Warnungen).
- Monatliche Kartenzahlung (Gutschrift, Kategorie «Zahlung») am 25. des Folgemonats
  begleicht den Vormonat. September 2026 ist noch offen (Zahlung wäre am 25.10.2026).
"""
import random
import sys
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP

START = date(2025, 10, 20)
END = date(2026, 9, 30)
MAX_DEBIT = Decimal("495.00")

OCTOBER = {
    "anna": [
        ("A-1001", "2026-10-02", "Migros Zürich", "Lebensmittel", "84.35", "DEBIT"),
        ("A-1002", "2026-10-04", "SBB Online", "Reisen", "62.00", "DEBIT"),
        ("A-1003", "2026-10-06", "Zalando Rückerstattung", "Shopping", "49.90", "CREDIT"),
        ("A-1004", "2026-10-09", "Restaurant Seeblick", "Gastronomie", "118.50", "DEBIT"),
        ("A-1005", "2026-10-12", "Digitec Galaxus", "Elektronik", "349.00", "DEBIT"),
        ("A-1006", "2026-10-14", "Swisscom", "Telekom", "65.00", "DEBIT"),
    ],
    "marco": [
        ("M-2001", "2026-10-01", "Coop Bern", "Lebensmittel", "56.20", "DEBIT"),
        ("M-2002", "2026-10-05", "Hotel Alpenblick", "Reisen", "420.00", "DEBIT"),
        ("M-2003", "2026-10-08", "Hotel Alpenblick Gutschrift", "Reisen", "80.00", "CREDIT"),
        ("M-2004", "2026-10-11", "Tankstelle Aare", "Mobilität", "92.70", "DEBIT"),
    ],
    "lena": [
        ("L-3001", "2026-10-03", "Möbelhaus Limmat", "Shopping", "500.00", "DEBIT"),
        ("L-3002", "2026-10-07", "Reisebüro Horizont", "Reisen", "500.05", "DEBIT"),
        ("L-3003", "2026-10-10", "Reisebüro Horizont Gutschrift", "Reisen", "750.00", "CREDIT"),
        ("L-3004", "2026-10-13", "Elektro Zentrum Baden", "Elektronik", "1200.00", "DEBIT"),
        ("L-3005", "2026-10-15", "Bäckerei Sonnenhof", "Lebensmittel", "12.40", "DEBIT"),
    ],
}

# Händlerpools je Kunde und Kategorie: (Händler, min, max). Alles erfunden bzw. generisch.
PROFILES = {
    "anna": {
        "prefix": "A", "seed": 4242,
        "fixed": [("Swisscom", "Telekom", "65.00", 14)],
        "pools": {
            "Lebensmittel": ([("Migros Zürich", 25, 120), ("Coop City Zürich", 15, 90),
                              ("Bäckerei Sternen", 6, 24), ("Denner Wiedikon", 12, 60)], (4, 6)),
            "Gastronomie": ([("Restaurant Seeblick", 45, 140), ("Café Limmatquai", 9, 28),
                             ("Pizzeria Bella Vista", 30, 85)], (1, 3)),
            "Reisen": ([("SBB Online", 18, 120)], (1, 2)),
            "Mobilität": ([("ZVV Ticketautomat", 4, 9)], (0, 2)),
            "Shopping": ([("Zalando", 40, 180), ("Manor Zürich", 25, 150), ("H&M Bahnhofstrasse", 20, 90)], (1, 2)),
            "Elektronik": ([("Digitec Galaxus", 30, 349), ("Interdiscount", 20, 160)], (0, 1)),
        },
        "refunds": [("Zalando Rückerstattung", "Shopping")],
        "specials": {
            (2025, 12): [("Manor Zürich", "Shopping", "189.90"), ("Digitec Galaxus", "Elektronik", "429.00"),
                         ("Confiserie Sprüngli", "Lebensmittel", "64.50")],
            (2026, 2): [("Bergbahnen Flumserberg", "Reisen", "158.00")],
            (2026, 7): [("Edelweiss Air", "Reisen", "478.00"), ("Hotel Bellavista Lugano", "Reisen", "396.00")],
        },
    },
    "marco": {
        "prefix": "M", "seed": 1881,
        "fixed": [("Sunrise", "Telekom", "59.90", 20)],
        "pools": {
            "Lebensmittel": ([("Coop Bern", 20, 110), ("Migros Marktgasse", 15, 95),
                              ("Volg Muri", 8, 40)], (3, 5)),
            "Gastronomie": ([("Restaurant Kornhauskeller", 50, 160), ("Bistro Bundesplatz", 14, 38)], (1, 2)),
            "Mobilität": ([("Tankstelle Aare", 60, 110), ("Parkhaus Bahnhof Bern", 8, 26)], (2, 3)),
            "Reisen": ([("SBB Online", 25, 140), ("Hotel Alpenblick", 160, 420)], (0, 1)),
            "Shopping": ([("Ochsner Sport", 35, 220), ("Loeb Bern", 30, 160)], (0, 2)),
            "Elektronik": ([("Fust Bern", 40, 290)], (0, 1)),
        },
        "refunds": [("Ochsner Sport Rückerstattung", "Shopping"), ("Hotel Alpenblick Gutschrift", "Reisen")],
        "specials": {
            (2025, 11): [("Garage Wankdorf", "Mobilität", "485.00")],
            (2026, 1): [("Skischule Adelboden", "Reisen", "320.00")],
            (2026, 8): [("Camping Lago Maggiore", "Reisen", "285.00")],
        },
    },
    "lena": {
        "prefix": "L", "seed": 7070,
        "fixed": [("Salt Mobile", "Telekom", "49.95", 5)],
        "pools": {
            "Lebensmittel": ([("Bäckerei Sonnenhof", 5, 22), ("Migros Baden", 25, 130),
                              ("Coop Wettingen", 20, 95)], (3, 5)),
            "Gastronomie": ([("Restaurant Kursaal", 55, 180), ("Café Badstrasse", 8, 26)], (1, 3)),
            "Shopping": ([("Möbelhaus Limmat", 60, 480), ("Globus Baden", 40, 260)], (1, 2)),
            "Reisen": ([("Reisebüro Horizont", 120, 490), ("SBB Online", 20, 95)], (0, 2)),
            "Elektronik": ([("Elektro Zentrum Baden", 45, 450)], (0, 1)),
            "Mobilität": ([("Baden Parking", 6, 22)], (1, 2)),
        },
        "refunds": [("Reisebüro Horizont Gutschrift", "Reisen"), ("Globus Baden Rückerstattung", "Shopping")],
        "specials": {
            (2025, 12): [("Globus Baden", "Shopping", "468.00"), ("Restaurant Kursaal", "Gastronomie", "236.00")],
            (2026, 4): [("Möbelhaus Limmat", "Shopping", "495.00")],
            (2026, 6): [("Reisebüro Horizont", "Reisen", "489.00")],
        },
    },
}


def money(x) -> Decimal:
    return Decimal(x).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def months():
    y, m = START.year, START.month
    while (y, m) <= (END.year, END.month):
        yield y, m
        y, m = (y + 1, 1) if m == 12 else (y, m + 1)


def month_bounds(y, m):
    first = date(y, m, 1)
    nxt = date(y + 1, 1, 1) if m == 12 else date(y, m + 1, 1)
    return max(first, START), min(nxt - timedelta(days=1), END)


def rnd_day(rng, lo, hi):
    return lo + timedelta(days=rng.randint(0, (hi - lo).days))


def generate(user):
    p = PROFILES[user]
    rng = random.Random(p["seed"])
    rows = []  # (date, merchant, category, amount, type)
    for y, m in months():
        lo, hi = month_bounds(y, m)
        partial = lo.day > 1
        month_rows = []
        for merchant, cat, amt, day in p["fixed"]:
            d = date(y, m, day)
            if lo <= d <= hi:
                month_rows.append((d, merchant, cat, money(amt), "DEBIT"))
        for cat, (pool, (nmin, nmax)) in p["pools"].items():
            n = rng.randint(nmin, nmax)
            if partial:
                n = max(0, n // 2)
            for _ in range(n):
                merchant, amin, amax = rng.choice(pool)
                amt = min(money(rng.uniform(amin, amax)), MAX_DEBIT)
                month_rows.append((rnd_day(rng, lo, hi), merchant, cat, amt, "DEBIT"))
        for merchant, cat, amt in p["specials"].get((y, m), []):
            month_rows.append((rnd_day(rng, lo, hi), merchant, cat, money(amt), "DEBIT"))
        # gelegentlich eine Rückerstattung
        if not partial and rng.random() < 0.35:
            merchant, cat = rng.choice(p["refunds"])
            month_rows.append((rnd_day(rng, lo, hi), merchant, cat, money(rng.uniform(19, 160)), "CREDIT"))
        rows.extend(month_rows)

    # Monatliche Kartenzahlung am 25. des Folgemonats (nur wenn noch im Zeitraum)
    payments = []
    for y, m in months():
        ny, nm = (y + 1, 1) if m == 12 else (y, m + 1)
        pay_day = date(ny, nm, 25)
        if pay_day > END:
            continue
        net = sum((a if t == "DEBIT" else -a) for d, _, _, a, t in rows if (d.year, d.month) == (y, m))
        payments.append((pay_day, "Zahlung – besten Dank", "Zahlung", money(net), "CREDIT"))
    rows.extend(payments)

    rows.sort(key=lambda r: (r[0], r[4] == "CREDIT", r[1]))
    out = []
    for i, (d, merchant, cat, amt, t) in enumerate(rows, start=1):
        out.append((f"{p['prefix']}-{i:04d}", d.isoformat(), merchant, cat, f"{amt:.2f}", t))
    return out


def main(path):
    lines = ["# Fiktive Demo-Transaktionen. Alle Personen, Händler und Beträge sind erfunden.",
             "# Zeitraum: 20.10.2025 bis heute. Format: username;id;date;merchant;category;amount;type",
             "# amount ist immer positiv, die Richtung ergibt sich aus type (DEBIT/CREDIT)."]
    for user in ("anna", "marco", "lena"):
        rows = generate(user) + OCTOBER[user]
        for r in rows:
            assert ";" not in r[2]
            if r[5] == "DEBIT" and not r[0][2:].startswith("1") and not r[0][2:].startswith("2") \
                    and not r[0][2:].startswith("3"):
                assert Decimal(r[4]) <= Decimal("500.00"), r
            lines.append(";".join((user,) + r))
        debits = [Decimal(r[4]) for r in rows if r[5] == "DEBIT"]
        credits = [Decimal(r[4]) for r in rows if r[5] == "CREDIT"]
        bal = sum(credits) - sum(debits)
        high = [r[0] for r in rows if r[5] == "DEBIT" and Decimal(r[4]) > 500]
        print(f"{user}: rows={len(rows)} debits={len(debits)} credits={len(credits)} balance={bal} "
              f"oldest={rows[0][:3]} high={high}", file=sys.stderr)
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main(sys.argv[1])

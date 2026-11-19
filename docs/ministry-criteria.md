# Community ministry criteria

How organisations get into the Community spotlight (C1/C2 monthly vote, `pipeline/community/ministries.json`), how
their links are chosen, and the evidence for the current five. Related decisions: #5, #16, #19 in docs/decisions.md.

## What the spotlight is and isn't

- **It is** a short monthly list of Christian organisations that readers can vote on anonymously. Each entry has a
  name, two neutral sentences, a link to the organisation's website and a "Give directly" link to its own page.
- **The app handles no money.** It takes no payments, no commission and no referral fee. "Give directly" opens the
  organisation's own page in the browser; anything given is between the reader and the organisation.
- **No endorsement.** Being listed means an organisation met the criteria below on the date checked. It is not a
  recommendation, rating or guarantee of how funds are used.
- **No tax advice.** Whether a gift is tax-deductible depends on the reader's country and on which legal entity
  receives it (for example, a national office rather than an international body). The app doesn't say.
- The vote is a popularity signal for readers only. It moves no money.

## Eligibility

All of these, checked on the organisation's own site:

1. **Identifiable organisation:** a named organisation with an official website it controls and a working contact
   (contact page, form or published email address).
2. **Public financial reporting:** an annual report, audited financial statements or an equivalent public disclosure,
   reachable from the official site.
3. **Public statement of mission:** a page saying what the organisation does and for whom.

The description must be checkable against the organisation's own pages or reports; figures come from its latest
published report and are re-checked whenever it is featured again.

## Link policy

For every `website` and `give_url`:

- **https only**, a plain host (no login, no port, no tracking parameters such as `utm_*` or `?p=`). Use the page's
  canonical address.
- **The official domain.** `give_url` must be on the same registrable domain as `website` (subdomains are fine, e.g.
  `donate.caritas.org`), or on a domain in the allow-list below.
- **A verified donation page:** after redirects the page returns 200, stays on the organisation's domain, and is a
  real donation page (a form, amounts, a payment step or bank details for giving), not a page that only mentions
  giving. Prefer the international or main site over a geo-redirect to a national office on another domain.
- **Otherwise the official homepage.** If no donation page can be verified, `give_url` is the official homepage, and
  the publish report says so.

### Allow-list of alternate domains (`community.ALTERNATE_DOMAINS`)

| Domains | Reason |
|---|---|
| `obolodisanpietro.va`, `peterspence.va` | Peter's Pence: the Italian and English names of the same Holy See site, both under `.va` (the Holy See's own top-level domain). `https://www.peterspence.va` redirects to `https://www.obolodisanpietro.va/en.html` (checked 2026-11-19). The current entry uses `obolodisanpietro.va` for both links, so this pair is a safeguard rather than in active use. |

Adding a pair needs evidence that both domains belong to the same organisation (a redirect from one to the other,
or the organisation's own site naming both), recorded in this table.

## Selection and rotation

- Five organisations per round. The project owner decides who is featured and in which months; the code only
  enforces the link and format rules.
- Months are UTC (`months_featured`). Every featured month needs its Firestore counters seeded
  (`backend/scripts/seed-month.mjs`).
- Before each round: re-run the link checks, re-read the financial-reporting link and update any figures in the
  description from the latest report.

## Removal

An organisation is dropped from future months when:

- a link breaks or stops leading to the organisation's own donation page or homepage;
- a credible concern is raised about the organisation (for example, a regulator's finding or a serious, sourced
  news report), pending review; or
- the organisation asks to be removed.

Counters for months it was already featured in stay as they are. Its `id` is never reused.

## The five organisations

Checked 2026-11-19 with `curl -L` (status, final URL, page title and `<link rel="canonical">`) and by reading the
donation, reporting and contact pages and the linked report PDFs.

| Organisation | Date checked | Links verified | Financial reporting | Notes |
|---|---|---|---|---|
| Caritas Internationalis (`caritas-internationalis`) | 2026-11-19 | website `https://www.caritas.org/` 200, canonical itself; give `https://donate.caritas.org/en/` 200, title "Global Action Fund", canonical itself. Contact: `/who-we-are/contact/` 200 | [Annual reports](https://www.caritas.org/annual-report/) (latest: [2025 PDF](https://www.caritas.org/wp-content/uploads/2026/08/CI-Annual-Report-2025-EN-Web.pdf), 200); [accountability page](https://www.caritas.org/who-we-are/accountability-and-financial-transparency/) says the General Secretariat's finances are reviewed each year by an external auditor | The supplied `/donate-now` redirects to a "Support Us" overview, so Caritas's own donation subdomain is used. Mission: caritas.org "Who we are" |
| Aid to the Church in Need (`aid-to-the-church-in-need`) | 2026-11-19 | website `https://acninternational.org/` 200; give `https://acninternational.org/donate/` 200, title "Donate \| ACN International", canonical itself. Contact: `/contact/` 200 | [About page "Facts and figures"](https://acninternational.org/about/) (2025: 5,368 projects in 141 countries, income and spending breakdown); national offices publish full statements | Its donation page says gifts go to "ACN Aid to the Church in Need gGmbH, a non-profit organization officially registered in Germany and audited internationally by KPMG". The supplied `?p=56376` is the homepage (canonical `acninternational.org/`); the homepage's Donate button (`/redirect-to-donation-page/?page=donate/`) returned a 301 to `http://www.churchinneed.org/` (the US office, another domain, plain http) from a US connection, so it isn't used. The description's "around 5,000 projects … more than 140 countries" matches the 2025 figures |
| Pontifical Mission Societies (`pontifical-mission-societies`) | 2026-11-19 | website `https://pontificalmissions.org/` 200; give `https://give.pontificalmissions.org/donate/give` 200, title "Support the Pope's Global Mission" (donation form). Contact: `/contact-us` 200 | [Annual Report Transparency](https://pontificalmissions.org/what-we-do/annual-report-transparency): combined financial statements 2025–2024 ([PDF](https://pontificalmissions.org/docs/default-source/default-document-library/combined_financial_statement_2025-2024.pdf?sfvrsn=9fbe44d8_1), 200) and earlier years | Both links go to the **US national office** (The Pontifical Mission Societies USA). The international site [ppoomm.va](https://www.ppoomm.va/en.html) has a [donation page](https://www.ppoomm.va/en/i-progetti/dona.html) (200), but it lists bank transfers (IBANs at the IOR) only, with no online form, and no financial reports could be found on ppoomm.va (only annals and bulletins), so it doesn't meet the reporting criterion as linked. The description describes the worldwide network, not the US office |
| Peter's Pence (`peters-pence`) | 2026-11-19 | website `https://www.obolodisanpietro.va/en.html` 200, canonical itself; give `https://www.obolodisanpietro.va/en/dona.html` 200, title "Donate - Peter's Pence", canonical itself, amounts and payment options. Contact: `obolo@spe.va` (FAQ page) | [Annual reports](https://www.obolodisanpietro.va/en/rapporti-annuali.html): [2024](https://www.obolodisanpietro.va/content/dam/obolodisanpietro/rapporti-annuali/ad-2024/Rapporto-Obolo-2024-in-EUR-ENG.pdf) and [2025](https://www.obolodisanpietro.va/content/dam/obolodisanpietro/rapporti-annuali/ad-2025/Annual-Disclosure-2025-EN.pdf) disclosures (both 200) | Our description states that most of the funds support the Holy See's own offices and apostolic work. The reports agree: in 2024, €61.2M of €74.5M supported the Holy See's activities and €13.3M went to direct assistance; in 2025, €41.2M of €54.5M and €13.3M. The description quotes 2024 figures; the 2025 disclosure is now out, so update the figures before the next round |
| Malteser International (`malteser-international`) | 2026-11-19 | website `https://www.malteser-international.org/en.html` 200, canonical itself; give `https://www.malteser-international.org/en/donation.html` 200, title "Donation \| Malteser International", canonical itself. Contact: `/en/contact.html` 200, `contact@malteser-international.org` | [Finance page](https://www.malteser-international.org/en/about-us/how-we-work/finance.html) (2024 balance sheet and income statement; "financial report 2024 audited by PwC"); [annual reports](https://www.malteser-international.org/en/about-us/service/media-library/annual-report.html) (latest: [2025 PDF](https://www.malteser-international.org/fileadmin/Files_sites/malteser-international/A-About_us/E-Service/C-Publications/Annual_Reports/Annual_Report_2025/Malteser-International-Annual-Report-2025_online.pdf), 200) | The relief agency of the Sovereign Order of Malta, which is a sovereign order with its own diplomatic relations, **not a Vatican office**. The bare domain redirects to the German `/de.html`, so the English pages are used |

## Enforced by code (publish refuses otherwise)

`pipeline/src/pipeline/community.py` (`load_ministries`, `link_problems`, `release_problems`) and
`schema/ministries.json`:

- `id`: lowercase `[a-z0-9-]`, 3–50 chars, unique. It names the Firestore counters (`YYYY-MM_id`): never rename or
  reuse an id once it has been featured.
- `name` is a plain string (shown the same in every language); `description.en` required, `description.es` optional;
  each at most 240 characters.
- `website` and `give_url`: `https://` only, plain host; `give_url` on the website's registrable domain or an
  allow-listed alternate.
- A release publish needs at least one entry and no `test` entries (`test-` ids are for debug builds and the
  emulator only).

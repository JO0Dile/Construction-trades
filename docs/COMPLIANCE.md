# Compliance, privacy, and what this app does not claim

This app holds ID numbers, photographs of ID documents, signatures,
attendance, locations and money. That is a regulated combination in Israel,
and the design has to be defensible before the product is sold to anybody,
let alone to a public body.

Two things are true at once and both matter:

* The engineering here is built so that the rules **can** be met and can be
  configured per jurisdiction.
* Nothing in this repository is legal advice, and nothing in it has been
  reviewed by an Israeli lawyer. Every statement below marked **[confirm]**
  needs a qualified opinion before it is relied on or repeated to a customer.

## What is a business rule and what is a legal rule

These get conflated in requirement documents, and the conflation is expensive:
a business rule that is described to a customer as a legal requirement is a
misrepresentation, and a legal requirement that is treated as a preference
gets configured away.

| Rule | What it actually is |
| --- | --- |
| A second-tier crew never sees the first tier's margin | **Business confidentiality.** Standard commercial practice, enforced because customers require it. Not, so far as is known here, a specific statutory duty **[confirm]** |
| Books and the documents behind them are retained for a set period | **Legal.** Israeli bookkeeping regulations set the period; confirm the current figure with an accountant **[confirm]** |
| Personal data is protected, minimised and access-controlled | **Legal.** Israel's Protection of Privacy Law and its amendments **[confirm]** |
| Contractor registration and classification | **Legal**, and it decides what work a firm may lawfully carry out **[confirm]** |
| A signature is never overwritten by a sync | **Engineering**, chosen because the alternative destroys evidence |

Retention periods, classification categories and required documents are
therefore **configuration**, not constants in the source. A number compiled
into an app is a number that is wrong in the next jurisdiction and cannot be
corrected without a store release.

## Platform verification is not professional qualification

The app can record that a firm has supplied a registration number, a
classification and a licence with an expiry date, and it can refuse to let an
expired one be used. That makes the firm **verified on this platform**.

It does not make the firm **legally qualified** to carry out regulated work.
Those are different claims and the interface must never let one read as the
other. Wherever a verification badge appears it says what was checked, by
whom, and on what date — never a bare tick.

## Confidentiality is enforced where the data is, not where it is drawn

The rule lives in `core/access/Commercial.kt` and is one line: *a commercial
figure may be sent only to a party to the contract it belongs to.*

When the server exists it runs that same function. For a second-tier crew the
response body is:

```json
{ "agreementId": "a.sub", "money": { "contractSum": 7000 } }
```

It is **not** this, with the first two fields hidden by the client:

```json
{ "mainContractAmount": 10000, "firstTierProfit": 3000, "theirContractAmount": 7000 }
```

A field the API sent has been disclosed. Anyone can read a response body, and
a phone is not a trusted place to keep a secret. `Commercial.disclose` returns
money as `null` rather than zero for exactly this reason: absent, not blanked.

`Commercial.margin` returns `null` rather than `0.0` when the caller is not
the firm in the middle, because zero is an answer and the question must not be
answerable.

## Location

Location is recorded **per event**, never continuously:

* on a proof-of-work photograph, so the picture can be tied to the floor it
  was taken on
* on a site sign-in, where the customer requires it

There is no background tracking, no location while the app is closed, and no
movement history. The distinction is not cosmetic — continuous tracking of
employees is a materially different proposition under privacy law and under
Israeli employment law **[confirm]**, and it is not a feature this app has.

Where location is captured the interface says so at the moment of capture,
and the permission strings on both stores explain the purpose in all three
languages (`shared/i18n/strings.json`, keys `perm_location_*`).

## Personal data held

| Data | Why | Notes |
| --- | --- | --- |
| Name, phone, email | Identify a person on site | |
| ID number | Statutory site register and induction | Sensitive. Encrypted at rest |
| Photograph of an ID document | Verifying the above | Sensitive. Encrypted at rest |
| Signature image | Inductions, permits, daily logs | Evidence; never overwritten |
| Attendance times | Timesheets and pay | |
| Photographs of work | Proof of work, snagging | May incidentally contain people |
| Event location | See above | Per event only |

The database is encrypted with SQLCipher. A device passcode gates the app.
When a server exists, the same categories need encryption at rest there, real
backups, and an access log — see `docs/SERVER.md`.

## Audit

Actions that change money, contracts, permissions or evidence write an audit
row: who, what, which record, previous value, new value, when. Audit rows are
not editable or deletable from any screen in the app, at any role. That is a
property of the data layer rather than of the interface, because a rule the
interface enforces is a rule an API call skips.

## Before this is sold

1. An Israeli lawyer reviews every **[confirm]** above.
2. A privacy notice in Hebrew, Arabic and English, and a record of processing.
3. A retention schedule, configured rather than assumed.
4. A data processing agreement with whoever hosts it.
5. A named contact for a subject access request, and a tested way to answer one.
6. A penetration test of the server, once there is one.

None of these are code. All of them are conditions of selling this to anybody
who will ask, and a public body will ask.

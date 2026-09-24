# Handing this over

For whoever buys this: a firm, a contractor group, or a ministry. It is the
document to read first, and it exists because everything else in `docs/` is
written for somebody building the app rather than somebody taking it on.

Three questions, answered in order: what works today, what is deliberately
switched off and how to switch it on, and what needs a person rather than
code.

## What works today, on a phone, costing nothing to run

The app is offline-first and that is not a limitation, it is the design. A
crew in a basement car park with no signal has to be able to record what they
did. Everything lives in a Room database on the device, encrypted with
SQLCipher.

So there is **no server to pay for, no hosting, and no per-user cost**. The
whole product runs on the phones it is installed on:

- 22 trades, 527 catalogue items, 201 safety checks, 24 project templates
- the five lenses — plan, stuff, people, evidence, money — and every register
  under them: daily log, concrete pours, scaffolding, lifting, temporary
  works, excavations, permits, toolbox talks, snags, incidents, violations,
  plant and its pre-use checks, purchase orders, payment applications,
  timesheets, waste, protective equipment, visitors, handover packs
- the emergency roll call, fed by the clockings and the visitor logs, and the
  heat check
- site admission at the gate, roles, and the chain of command that decides who
  may see what
- a tamper-evident audit trail, and CSV and PDF export
- backup and restore to an encrypted file
- Hebrew, Arabic and English throughout, including the audit trail

## What is prepared and switched off

Both of these are **built to the point where the next step is configuration
and testing, not design**. Neither is switched on, and neither can be without
spending money — which is the reason they are in this state rather than an
oversight.

### Sending a verification code by SMS

**Built:** `core/people/Verification.kt` — how long a code lives, how many
wrong tries it takes, how long a number waits afterwards, when another may be
sent, and what counts as the same six digits when the keypad is Arabic.
Tested, including the attack arithmetic.

**Not built, and deliberately:** anything that sends. There is no "enter the
code we sent you" screen, because no code was sent and a screen that asks for
one teaches people the app lies to them.

**To switch it on:**

1. A server. An SMS has to be sent by something that is not the phone
   receiving it — `docs/SERVER.md` explains why sending from the app proves
   nothing and why a gateway key inside an APK is a bill waiting to happen.
2. An SMS provider account. In Israel, a carrier gateway or an international
   one with Israeli sender-ID registration. **This is a paid account and a
   form, not a code change**, and messages cost per message.
3. Two endpoints, `POST /verify/start` and `/verify/check`, enforcing the
   numbers already in `Verification.kt`. Where the app and the server
   disagree, the server wins: the rate limit is the whole security of a
   six-digit code, and the app is the thing being attacked.
4. A screen, and `phoneVerifiedAt` on the account.

**The rule not to break:** verification gates nothing that keeps somebody
safe. An unverified number is a number nobody has confirmed, not a person who
may not sign an induction or report a near miss.

### Charging for it

**Built:** `core/money/Plans.kt` — three plans, what each includes, seat
limits, what a lapse takes back and what it never takes back. A test walks
every capability against every plan in both states. `Settings → Plans and
pricing` shows the whole thing in three languages. `docs/PRICING.md` is the
decision, not a proposal.

**Not built, and deliberately:** any way to buy, any enforcement, and any
storage for which plan a firm holds.

The reason is worth understanding before anyone changes it. A plan recorded in
the local database is a plan the person holding the phone can set, and a limit
that can be lifted by tapping a button on your own device is not a limit.
Shipping one would be worse than shipping none, because it teaches the
customers you already have that the plan is free. A seat cap with no way to
pay past it would also have broken every firm with a crew of more than three
on the day it shipped.

**To switch it on, in this order:**

1. The server, which holds the entitlement and is the only thing that can say
   a firm has paid.
2. Play Billing and StoreKit, **checked server-side**. Neither can be verified
   without a signed release on a store track with products configured, which
   is why neither is written blind.
3. Direct billing for the Site plan. Israeli contractors need a חשבונית מס
   with the ח.פ. on it, and neither store issues one in the firm's name.

**The rule not to break:** nothing that keeps somebody safe is ever behind a
payment. It is `Plans.NEVER_CHARGED` rather than a sentence in a document,
because a sentence in a document does not fail a build, and a test walks the
whole set against every plan including a lapsed one. Putting a permit or an
incident report behind a paywall means deleting a passing test with "safety"
in its name.

## What costs money to run, when it is switched on

| | What it is |
|---|---|
| A server | One machine. `docs/SERVER.md` says what to buy and in what order to build on it |
| SMS | A provider account, per message, plus Israeli sender-ID registration |
| The stores | Google Play a one-off developer fee, Apple a yearly one |
| Store commission | 15–30% of anything sold through in-app purchase, which is the argument for direct billing on the Site plan |
| Storage | Grows with every photograph and video, once sync exists |

Until any of that is switched on, the running cost of this product is zero.

## What needs a decision, not a build

- Whether the Site plan is sold through the stores at all, or direct.
- Whether there is a trial, and whether it needs a card.
- How long the audit trail is kept. The app keeps everything until somebody
  sets a number, deliberately: how long site records must be held is a legal
  question with different answers for a payment record, a safety inspection
  and a personal ID number, and it is answered by the organisation running the
  app rather than by whoever wrote the code.

## iPhone

**The iPhone app is well behind the Android one, and has never been compiled.**
It covers the first version of the ground — inventory, projects, the
schedule, safety checklists, the barcode scanner, export — over 14 data
models. Android has 59 tables. Everything built since the first version is
Android only: money, the people and the chain of command, the site registers
(daily log, concrete, scaffolding, lifting, temporary works, excavations,
permits, snags, violations), the roll call, heat checks and plant pre-use
checks.

It has never been compiled because this repository has been worked on where
there is no Swift toolchain, so the first job on a Mac is to make it build at
all. After that the rules are the easy half: everything under
`android/app/src/main/java/.../core` is plain Kotlin with no Android in it and
a test beside it, and each file says in prose what it enforces and why — it
ports rule by rule, and the tests are the specification. The screens are the
long half.

A buyer should price the iPhone app as a second build on top of a finished
design, not as a finishing touch.

## What needs a person, not code

These are the ones that cannot be finished by building anything, and they are
the ones a buyer should budget for first.

1. **A tradesperson reading the Hebrew and Arabic.** Every string and every
   catalogue entry exists in all three languages and the build fails if one
   goes missing. That proves the translations are *complete*. It cannot prove
   they are the words used on a site, and nobody who works a site has read
   them yet. An hour with a foreman in each language is worth more than
   anything else outstanding.
2. **112 photographs.** Everything else in the catalogue has one. The list,
   with a brief for each, is [`PHOTOS.md`](PHOTOS.md) — written to be read on
   a phone.
3. **Store screenshots**, on a real device, once per language.
4. **A lawyer.** The privacy notice, the terms and the accessibility statement
   all say on their own last section that they have not been through one. That
   is true and a reader is entitled to know it.
5. **The safety content checked against the current published regulation.** It
   carries a warning in the app saying it is a pointer for the site file and
   not the text of the standard.

## Where things are

| | |
|---|---|
| What to buy and build server-side | [`SERVER.md`](SERVER.md) |
| The plans, and what must never be charged for | [`PRICING.md`](PRICING.md) |
| Publishing to Play and the App Store | [`STORE_COMPLIANCE.md`](STORE_COMPLIANCE.md), [`RELEASING.md`](RELEASING.md) |
| Putting it on a phone and trying it | [`TESTING.md`](TESTING.md) |
| Adding a language | [`LOCALIZATION.md`](LOCALIZATION.md) |
| What the audit trail guarantees | [`AUDIT.md`](AUDIT.md) |
| The photographs still needed | [`PHOTOS.md`](PHOTOS.md) |

Every push runs the tests and twenty-three checks. Most of them guard things a
compiler cannot see: an English sentence written into the audit register, a
catalogue block missing its Hebrew, a documented permission the app does not
hold. They are listed in `.github/workflows/ci.yml`, each with a comment
saying what went wrong once to make it worth having.

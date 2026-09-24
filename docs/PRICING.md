# What to charge, and what must never be charged for

*Taking this product on rather than building it? Start with*
*[`HANDOVER.md`](HANDOVER.md).*

**Decided.** The rules below are `core/money/Plans.kt`, and the tests in
`PlansTest.kt` fail if this document and that file ever disagree. The screen a
customer reads is `ui/settings/PlansScreen.kt`, in all three languages.

**Nothing is charged yet.** See *What is not built* at the bottom for why, and
for what has to exist first.

## The rule that comes before the price

> **Nothing that keeps somebody safe is ever behind a payment.**

Being admitted at the gate, signing an induction, reading a permit, reporting
an incident or a near miss, seeing a violation written against you, checking in
and out. All free, on every plan, for ever, including when a subscription has
lapsed. So is reading and exporting anything already recorded.

This is not generosity. A worker who cannot report a near miss because their
foreman's card expired is a worker who does not report it, and the register
that was supposed to prove the site was run properly has a hole in it on
exactly the day somebody will ask. It is also the difference between a tool a
safety officer will stand behind and one they will not.

It lives in the code as `Plans.NEVER_CHARGED`, and a test walks every
capability against every plan in both the paid and the lapsed state. Putting a
permit behind a paywall means deleting a passing test with "safety" in its
name, which is a thing a reviewer sees. A sentence in a document does not fail
a build.

The corollary: **a worker never pays and is never counted as a seat.** The firm
that engaged them pays. A labourer who installs this to see their own hours and
their own tickets is a user, not a line item.

## The plans

| | Free | Pro | Site |
|---|---|---|---|
| Price | — | ₪49/month or ₪490/year | ₪149/month or ₪1,490/year |
| People | up to 3 | up to 15 | unlimited |
| Everything safety | ✓ | ✓ | ✓ |
| All five lenses, all registers | ✓ | ✓ | ✓ |
| More than one company on a device | — | ✓ | ✓ |
| Signed audit export | — | ✓ | ✓ |
| Subcontractor chains, contracts, payment applications | — | — | ✓ |

Priced per **company**, not per person, because that is how the money actually
works on a site: the boss pays and the crew does not.

The annual plan is two months free. It is the honest version of what a lifetime
purchase is really selling — the feeling of having paid and being done —
without the perpetual obligation. A lifetime licence was considered and
rejected: at roughly seventeen months of the subscription it buys an obligation
with no end, against a server bill and storage that grow every year with every
photograph. Products die of this.

**Free has to be genuinely useful, not crippled.** A sole trader and a two-man
crew never pay and everything works: the catalogue, the registers, the
photographs, the audit trail. They are not a lost sale. They are how the third
person who joins the firm finds out the app exists.

### When a subscription lapses

The data stays readable and exportable for ever, and only writing is limited.
A firm that cannot get its own site diary out of an app is a firm that will
never put one in, and the day they most need the export is the day they have
stopped paying.

A lapse is a flag beside the plan rather than a fourth plan, so what the firm
bought is remembered. Paying again restores it instead of asking them to choose
a plan a second time.

### Where the seat limit is checked, and where it is not

At the office, when somebody is added to the books. **Never at the gate.**
Admission is in the never-charged set, so a man who turns up to work is signed
in whatever the firm has paid — and that can carry a firm past its own limit.
That is the rule working as intended, not a hole in it: the limit is on a firm
choosing to add somebody in the office, not on a person arriving at a site.

### Israel

- Prices are shown in ₪ including מע"מ at 18%, which is what a consumer price
  must be.
- Contractors need a חשבונית מס with the ח.פ. on it. Neither store issues one
  in the firm's name, which is a real reason to offer direct billing for the
  Site plan rather than routing everything through in-app purchase.
- Both stores require that an in-app purchase be available in-app. Direct
  billing can exist beside it; it cannot replace it for consumer sales.

## What is not built, and why

There is no billing and no enforcement, and the plans screen says so on itself
rather than showing three buttons that do nothing.

**A paywall needs somewhere to check the answer that is not the phone asking
the question.** Everything this app knows lives on the device, by design — a
crew in a basement car park with no signal has to be able to work. That is the
right trade for a site diary and the wrong one for an entitlement: a plan
recorded in the local database is a plan the person holding the phone can set,
and a limit that can be lifted by tapping a button on your own device is not a
limit. Shipping one would be worse than shipping none, because it teaches the
customers you already have that the plan is free.

So the storage for a plan is deliberately not there either. A column nothing
can honestly write is a column that lies, and this repository has been burned
before by mechanisms that were complete, commented, and reached by nothing.

What has to exist first, in order:

1. **The server** (`docs/SERVER.md`). It holds the entitlement and is the only
   thing that can say a firm has paid.
2. **Play Billing and StoreKit**, checked server-side. Neither can be verified
   without a signed release on a store track with products configured, so
   neither is written blind.
3. **Direct billing for the Site plan**, which is the חשבונית מס path above.

Until then: everybody is on Free, nothing is enforced, and the count of people
on the books — which is the figure the seats will be counted against — is shown
on the plans screen so a firm can see where they would stand.

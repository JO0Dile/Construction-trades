# Privacy notice

**Trades Work Manager — Israel edition**

Last revised: 6 September 2026. Version 1, draft.

> **Not yet reviewed by a lawyer.** This describes accurately what the software
> does, which is the hard part and the part an engineer can answer. Whether it
> discharges the operator's obligations under Israeli or any other privacy law
> is a question for a qualified adviser, and one that has to be answered before
> this is published as the operator's policy. See `docs/COMPLIANCE.md`.

## Who this is about

The app is used by construction firms. Two different people appear in it:

* **The person using it** — a tradesperson, foreman, manager or owner.
* **People they record** — workers on their site, and anybody who happens to
  be in a photograph of the work.

The firm that installs and runs the app decides what is recorded and why. In
data-protection terms the firm is the one answerable for it. The publisher of
the app is not sent any of it.

## What the app holds

| Data | Why it exists |
| --- | --- |
| Name, phone, email | To tell one person on a site from another |
| ID number | The statutory site register, and the safety induction |
| Photograph of a face or an ID document | To confirm the person on site is the person on the register |
| Signature image | Inductions, permits to work, daily logs, deliveries |
| Attendance and hours | Timesheets and pay |
| Photographs of the work | Proof of work, snags, concrete tickets, handover |
| Location, at specific moments | See below |
| Contracts, prices, invoices | Running the job |
| An audit trail of who changed what | Because a signed record has to be defensible |

## Where it is held

**On the phone or tablet, and nowhere else.**

There is no account on a server, because there is no server. The database is
encrypted at rest with SQLCipher and can be locked behind a device passcode.
Photographs are written to the app's own private storage — not to the shared
gallery, so they do not appear in other apps or in a cloud photo backup.

The one thing that leaves the device is a **version check**: the app asks a
public release feed whether a newer build exists. That request carries no
personal data and no identifier of any kind — it is the same request from
every installation.

There is no analytics, no crash reporting, no advertising, no tracking
identifier and no third-party software development kit that collects anything.
That is a property of the build and can be checked: the app declares
`INTERNET` for the version check alone.

## Location

Location is recorded **at specific moments and never continuously**:

* on a proof-of-work photograph, so the picture can be tied to the floor it
  was taken on
* on a site sign-in, where the firm requires it

There is no background location, no location while the app is closed, and no
movement history. The app cannot follow anybody around. The distinction is not
cosmetic: continuous tracking of employees is a materially different
proposition in law and it is not something this app can do.

Location can be declined. The app keeps working; the photograph simply carries
no coordinates.

## Sensitive data

ID numbers and photographs of identity documents are the most sensitive things
here. They exist because an Israeli site register and a safety induction both
call for them. They are encrypted at rest, they never leave the device, and
they are shown only to roles that manage people.

## Who can see what

Access is by role and, on a job with more than one firm, by contract. A
subcontractor sees the terms of its own agreement and not another firm's — and
when a server is added, that rule runs on the server rather than in the app,
because a rule enforced only by an interface is not enforced. See
`docs/SERVER.md`.

## How long it is kept

Records are kept for as long as the firm sets a retention period for, and the
retention period is a setting rather than a number compiled into the app.
Israeli bookkeeping rules set a minimum for anything that is an accounting
record; the firm's accountant should set the figure.

When records are removed on retention, the removal itself is logged. A gap in
the audit trail is always explained by an entry saying who removed what.

## Deleting everything

Settings has a **delete all data** action. It removes the database and the
photographs from the device. There is nothing held anywhere else to ask for,
because nothing was sent anywhere else.

## Rights

Under Israeli privacy law a person may ask to see what is held about them and
to have it corrected. Because the data sits on the firm's own devices, that
request goes to **the firm**, not to the publisher of the app. The firm should
name a contact and be able to answer within the period the law allows.

## Changes

The revision date at the top changes when this does. A change that widens what
is collected will be put to users before it takes effect, not after.

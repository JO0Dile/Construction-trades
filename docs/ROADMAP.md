# Making 350 features feel like one app

The feature list is real and most of it is worth building. The risk is not the
count — it is that 350 features become 350 screens, and the app turns into a
launcher for tiny disconnected tools that each want their own data entered
again.

This document is the answer to that. It is not a list of what to build; it is
the shape everything gets built *into*.

## The one idea

**Everything hangs off a job.**

A job (`ProjectEntity`) is a place with work happening in it. Almost every
feature in the list is one of five things about a job:

| Lens         | The question it answers          | Examples from the list                       |
| ------------ | -------------------------------- | -------------------------------------------- |
| **Plan**     | What is supposed to happen?      | tasks, schedule, milestones, dependencies, permits, method statements |
| **Stuff**    | What does it need?               | materials, tools, deliveries, procurement, plant hire, fuel |
| **People**   | Who is on it?                    | crews, tickets and certifications, timesheets, subcontractors, access |
| **Evidence** | What happened, and can we prove it? | photos, checklists, inspections, sign-offs, incidents, snags, dayworks |
| **Money**    | What did it cost, who owes what? | budget, variations, valuations, invoices, retention |

A feature that does not answer one of those five questions about a job is
either (a) a setting, (b) a report, or (c) not for this app. That test is the
whole architecture. Applied honestly it collapses ~350 items into five tabs
inside one screen, plus a dashboard that reads across jobs and a settings area.

So: **no new top-level tab per feature.** A new capability is a new section
inside one of the five lenses, or a new column in a report.

## Why this is not just tidy-mindedness

Three concrete consequences, which are the actual reasons:

**Data is entered once.** A delivery note photographed under Evidence updates
the quantity under Stuff and the accrual under Money. If procurement were its
own app-inside-the-app, it would ask for the delivery again.

**Permissions have one shape.** A role is a set of lenses, per job. "Finance"
sees Money everywhere and Plan nowhere. "Foreman" sees everything on their own
jobs. That is one rule, checked in one place — not 350 permission checks.

**Offline stays possible.** Every lens writes to the same local database and
syncs by the same rules. A feature that needs its own backend, its own cache
and its own conflict resolution is the one that breaks offline for everything
else, which is why integrations (below) are deliberately at the edge.

## Order of building

Each phase is usable on its own. Nothing here is a rewrite of what is above it.

**Phase 1 — the five lenses exist. Done.**
Plan, Stuff and Evidence: tasks, materials, photos, safety checklists, site
plans, and jobs started from nothing in any kind of place.

**Phase 2 — accounts and roles. Done.**
Personal or company account; a company holds members with roles. Roles gate
lenses, per the rule above. Local-first: an account is a row on the device, so
the app still opens and works in a basement with no signal. Sync comes later
and does not change the model.

**Phase 3 — People and Money. Done.**
Money: contract value, VAT at the job's own rate, cost lines, variations with
approval, invoices, margin and forecast. People: certifications with expiry
warnings, thirty days out. Both are lenses on what already existed, not new
apps beside it.

**Phase 4 — reports across jobs. Done.**
The dashboard grows: what is owed across the book of work, the margin on all
of it, which jobs have run late, whose tickets lapse this month. Every tile
belongs to a lens, so the dashboard is different work for different people.
Not one line of new data entry — the whole phase is queries over what phases
1–3 already collect, which is the point.

**Phase 4b — the Evidence lens gets teeth. Done.**
Toolbox talks with an attendance register, and permits to work for the five
kinds of job that need one: hot work, height, confined space, excavation,
electrical isolation. Two rules carry the weight. A permit cannot be issued
until every precaution on it is ticked, because the precautions *are* the
permit. And whether a permit authorises anything is worked out from the clock
every time it is asked, never read off its status column — nobody goes round
updating database rows at knocking-off time, so a permit that ran out at five
has to stop saying "live" at five, on a phone that has been open since two.

**Phase 4c — the door, and the induction behind it. Done.**
Signing in is typing the username or ID number and the password somebody in
the office gave you, not picking yourself off a list of everyone on the
device — that list handed a crew roster to whoever picked the phone up, and it
did not match how people are actually told who they are.

Behind the door is a safety induction that cannot be skipped, dismissed or
gone back from. Two versions of it: one page of PPE for somebody on the tools,
and the same page plus the rules they enforce for somebody running the job —
which one you get is derived from the existing role grid rather than from a
second list beside it. It is signed by hand, and the signature is stored
against the account with the date.

Still open: one person belonging to more than one company. A tradesperson is
on this site today and another tomorrow, and identity — their ID number, their
photograph, their induction — belongs to the person, while the role belongs to
the membership. The identity fields already sit on the account for that
reason; the membership table is the next change.

**Phase 4d — snagging. Done.**
The defects found on a job, raised with a photograph and closed with another.
One rule carries it: *fixed* is a claim and *closed* is a verification, and a
snag somebody says they put right stays on the outstanding count until somebody
else has been and looked. Collapsing those two into one button gives you a list
a subcontractor can complete from the van, which is worth nothing at handover.

Handover readiness is a separate number from the outstanding count, because a
scuff to touch up next week is a real snag that should stay on the list without
pretending to hold up a building.

**Phase 4e — the daily log. Done.**
The יומן עבודה an Israeli site manager is required to keep, and the clearest
case in the app for entering things once. By five o'clock the app already knows
which tasks closed, what was delivered, which permits were issued, which
briefings were given and what went wrong — it watched all of it. The log counts
that back and asks only for the three things a person knows: the weather, the
headcount, and what the day was actually like. Then it is signed, and it stops
being editable, because a daily log somebody can tidy up after an accident is
evidence of nothing.

The day window is local midnight to local midnight rather than a fixed
twenty-four hours. Israel moves its clocks in March and October, so two days a
year are twenty-three and twenty-five hours long, and a fixed window either
loses an hour of work off one or steals an hour from the next.

**Phase 4f — photographs that are worth something later. Done.**
The date, the coordinates and the photographer are burned into the pixels of
every site photograph rather than drawn over them at display time, because the
value of a stamp is what survives leaving the app. A photograph emailed to a
loss adjuster arrives as a picture of a wall unless the date came with it. A
person's own photograph and their ID document are left alone: stamping somebody
with the coordinates of where they stood is no use as evidence and is not a
thing to do to identity documents.

**Phase 4g — concrete, against the clock. Done.**
The first screen in the app whose value is that it is fast rather than that it
is complete. Concrete has roughly ninety minutes of working life from the
moment water met cement at the plant, and about sixty on an Israeli summer
afternoon — and a load that has run out looks exactly like a load that has not.
Nobody can tell by eye, the test that proves it comes back twenty-eight days
later, and by then the element is cast and the only remedy is a hammer.

So the screen is a countdown. The time is read off the supplier's ticket rather
than defaulted to when somebody noticed the truck, because the clock starts at
the batch plant; the truck closest to running out sorts to the top; and the
temperature entered once on the pour shortens every load in it. A rejected load
is kept as a row, with the reason, and never counted towards the volume that
went in: a pour that quietly does not add up is worse than one that visibly
does not.

**Phase 4h — the scaffold register. Done.**
Israeli regulations require a scaffold to be inspected before first use, after
any alteration, after weather that could have affected it, and at least once
every seven days, with the result written down. The register is not paperwork
about the scaffold: on a lapsed inspection it is the reason nobody may climb
it.

So the states are not a traffic light with an amber that means carry on
carefully. There are two things a person at the bottom of a scaffold needs —
may I go up, or not — and everything else hangs off that one answer. An
alteration voids a passing inspection with days still left on it, because a
scaffold that has been changed is a different scaffold. A failed inspection is
not redeemed by being recent. And a scaffold that has just gone up reads "never
inspected", not a blank row.

Inspections are inserted and never updated, the same rule as the daily log and
for the same reason. The seven days are seven calendar days in the site's own
zone rather than a fixed 168 hours: one week a year in Israel is 167 hours
long, and a fixed span would move the deadline.

**Phase 4i — lifting operations. Done.**
The first screen in the app that refuses to let somebody press a button. Every
other lens records what happened; this one decides whether it may.

Everything that stops a lift is known before the load leaves the ground and
forgotten while it is in the air. A crane at ninety-six per cent of its chart at
thirty metres is a different machine from the same crane at forty per cent, and
nobody redoes that arithmetic with a load swinging. So the plan carries the
weight *including the rigging* — slings, shackles, spreader beam, hook block are
on the same rope, and leaving them out is the ordinary way a lift planned at
ninety per cent turns out to have been at a hundred and four — against the
capacity read off the duty chart at the radius actually being worked.

The three legally required people are named, and each is named **with the
ticket the role relies on**, because a slinger holds several certificates and
only one of them is the slinging ticket. Blocking a lift because somebody's
first-aid card lapsed would be wrong; blocking it because their slinging ticket
lapsed is the entire point, and it is the first thing in the app to make the
certifications table do work rather than sit there being a list.

The gate reports every blocker at once. A plan that fails four checks and
reports one is a plan somebody fixes four times, walking back to the crane in
between. And editing any number, or changing who is on it, clears the approval:
a supervisor signed off a specific lift, and a plan that keeps the signature
while the numbers move underneath it is a plan nobody actually approved.

**Phase 4j — temporary works. Done.**
Propping, formwork, shoring, façade retention: the structures that hold a
building up while it cannot hold itself, and then come down again. They fail
differently from permanent work. A permanent structure that is wrong is usually
wrong slowly; temporary works fail at the moment somebody loads them and the
moment somebody takes them away, and both of those are decisions a person makes
on site.

So the register is two gates rather than a status field. Nothing may take load
until it has been designed, independently checked, erected, **and inspected
against that design** — the last of those being a separate act from building it,
because the commonest failure is not a bad design but a good design built
differently with nobody holding the two up against each other. And nothing may
come down until somebody with the authority has released it.

The second gate is the one that kills people. Striking props under a slab that
has not reached strength drops the slab, and the decision is usually made by
whoever needs the props for the next floor. It is almost always verbal. Here it
has a name and a time against it, and where the propping is tied to a pour the
app already recorded, the gate also knows when the concrete went in — so it can
refuse until the days the engineer specified have passed. The app does not work
that number out and does not have an opinion about the concrete; it defaults to
fourteen days because that errs towards leaving props in, and the engineer's
number replaces it.

**Phase 4k — excavations. Done.**
Two things kill people in trenches and they are not the same thing. The sides
come in — a cubic metre of soil weighs about a tonne and a half, and somebody
buried to the chest cannot be pulled out by hand. Or what was already in the
ground is struck: a live cable, a gas main, a water main that fills the trench
with the man still in it.

The second is settled before the first spade goes in and cannot be undone
afterwards, so locating services is a gate here rather than a line on a
checklist somewhere else. A trench cut too steep can be battered back; a cable
that has been cut has been cut.

The inspection window is a **day**, not the scaffold register's week. It rains
overnight, the sides dry and crack, a lorry parks near the edge — and the man
climbing in at six in the morning is relying on somebody having looked since all
of that. Yesterday's inspection is not an inspection of today's trench, and the
app counts calendar days in the site's own zone rather than a rolling
twenty-four hours, which would say a seven-o'clock inspection last night still
covered a six-o'clock start.

**Phase 4l — the fire watch. Done.**
Not a new register: a hole in one that was already there. Permits to work
already covered hot work, and a hot work permit could be signed back the instant
the welding stopped.

That is the wrong instant. Hot work fires do not mostly start while the torch is
lit — they start afterwards, from a spark that fell into a void an hour ago and
has been smouldering since. The welding stopping is the moment the danger
becomes invisible, not the moment it ends, and signing the permit back is what
tells everybody the area is safe to leave.

So the permit now records when the work actually stopped — which is not when
the permit runs out, since a welder who finishes at two on a permit that runs
to five is owed his hour from two — and will not close until the hour has been
kept. With no stop time recorded the hour runs from the end of the window
instead, which can only hold the permit open longer than the truth.

The minutes owed round **up**, where the permit's own countdown rounds down.
The two numbers look alike and pull opposite ways: flooring the window is
generous in the safe direction, and flooring the watch would say "nothing left
to wait" with thirty seconds still to stand there.

**Phase 4m — payment applications and retention. Done.**
The row that has sat at the top of "what is not built" the whole time, while
six safety registers went past it.

Two things in it are got wrong constantly and both cost real money. The first
is that applications are **cumulative**: number three says what the work is
worth in total, not what was done since number two. Paying the figure on the
face of each one in turn pays for the same work twice, and it happens because
the number printed largest is the number nobody should be paying. So the app
works out the running total itself, from the last application that was actually
paid, and shows the difference as the answer with the three figures it came
from above it.

The second is what "שוטף + 30" means. The clock starts at the end of the month
the invoice falls in, not on the invoice date, so an invoice dated the 3rd of
March is due on the 30th of April — and one dated the 31st of March is due on
the same day. A subcontractor budgeting from "net 30" is a month out and short
of cash. The due date is worked out once, at certification, and stored, so
changing the terms on a job later does not restate when last March's money was
owed.

Retention is held with a limit, because a flat percentage on every application
would keep growing with the job and end up holding back more than was ever
agreed. It comes back in two halves: one at practical completion, one when the
defects period ends.

A negative "due this time" is shown as money owed back rather than as zero. It
means the earlier applications certified more than the work turned out to be
worth, and it has to come back one way or another.

**Phase 4n — the hours reach the money. Done.**
The app has recorded clock-ins with an hourly rate against them since the
schedule was built, and has never once used them. Labour is usually the largest
number on a job, and it was arriving in the Money lens only when somebody
remembered to type a cost line — which is to say, entered twice or not at all.

Overtime is a **daily** question, so the hours are gathered into person-days
before any band is applied. Two nine-hour days are two hours of overtime; one
eighteen-hour day is eight. Summing a week and splitting once gets a larger
answer and survives review, because the total hours are right and only the money
is wrong. A shift is attributed to the day it started, so a night shift is one
long night rather than two short days with no overtime in either.

The statutory multipliers are constants; the length of an ordinary day is not,
and neither is which day is a rest day. A collective agreement can be more
generous than the law, and on a site with Jewish, Muslim and Christian crews the
day of rest is three different days — hard-coding Saturday would underpay two of
the three.

The figure is deliberately **not** added to the job's costs. Labour clocked and
labour typed are two accounts of the same money and summing them doubles it, so
both are shown and the screen says when they disagree. The gap is the useful
part: either hours nobody costed, or a cost line nobody worked. A day with no
rate against it is shown as unpriced rather than as free, because free is the
direction a cost screen must never be wrong in.

**Phase 4o — the pack that leaves the app. Done.**
Nine registers had been recording evidence, and every one of them could only be
read inside the app. Evidence that cannot leave is evidence of nothing — the
same argument the photograph watermarks are built on, applied to everything
else.

Two things were missing. Nobody could see what was still open **across** a job:
each register answers its own question, and at handover the question is all of
them at once. And nothing assembled any of it into something a client could be
handed.

The pack does not refuse to print while things are outstanding. An interim pack
is a real thing — a client asks for the file at the end of a phase, a
subcontractor leaves and wants their part of it. What it does instead is record
what was outstanding at the moment it was produced, on the **first row** of the
document, so a pack that is skimmed rather than read still says whether the job
was finished when it was printed. A document that quietly omits the eleven
permits nobody closed is worse than no document, because somebody will file it
and believe it.

Zero counts are left out rather than listed: three blocking snags buried in a
wall of "0 open permits" is how a list stops being read. And the order is what
matters at handover rather than what is biggest, so twenty unsigned logs do not
sort above one scaffold left standing in the street.

**Phase 5 — integrations, at the edge. Not started.**
Israeli government and enterprise systems, accounting exports, weather,
equipment telematics. Each one is an adapter that reads or writes data the app
already owns. None of them is allowed to become a source of truth, because the
moment one is, the app stops working offline.

## What is not built

Whole categories still at zero, in roughly the order they are worth doing:

| Not started | Lens it will land in |
| ----------- | -------------------- |
| Subcontract ledgers | Money |
| Sync between devices | every lens, one mechanism |
| Israeli government and accounting integrations | the edge, Phase 5 |
| Site security, structural, underground, façade, green building, commissioning, legal, PR, weather, AI | not yet placed |

Since that list was written, ten of its rows have landed and are no longer on
it: the plant register, purchase orders with goods received, toolbox talks with
permits to work, one person belonging to several companies, snagging, the
concrete half of "concrete and structural", the scaffold register, lifting
operations, temporary works, and excavations — leaving "underground" on that
row meaning services diversions and tunnelling rather than trenches.

The ones that are genuinely hard are sync, the government integrations, and
anything needing a server. The rest are now another table, another lens
section, another screen — which is what the four phases above were for.

## The 350, honestly

A full list of 350 wanted features exists. Sorted by what it would actually
take to build them, it comes out roughly:

| | About | |
| --- | ---: | --- |
| Built | 67 | |
| Buildable here — on the device, no server | 118 | where the work is |
| Needs a server | 60 | sync, chat, push-to-talk, client portal |
| Needs an API somebody has to grant | 55 | government bodies, Priority/SAP, weather, traffic, CCTV |
| Needs hardware | 30 | turnstiles, biometrics, sensors, drones, wearables |
| Is a product in its own right | 25 | BIM viewer, AR overlay, CAD engine, the AI predictions |

So about half of the list cannot be built into this app however long anybody
works at it, because it needs a signed agreement, a server, a device that does
not exist yet, or a team-year. That is not an argument against the list — it is
an argument for spending the time on the hundred and fifty that are real, since
those are the ones that make the app worth opening on a site tomorrow.

## The rule for anything not on the list

When a new feature is proposed, it gets three questions:

1. **Which lens?** If the answer is "a new one", the answer is usually no.
2. **What does it stop someone re-entering?** If nothing, it is a report.
3. **Does it work with no signal?** If not, it is an integration and lives at
   the edge.

A feature that cannot answer these is not a small feature. It is a second app.

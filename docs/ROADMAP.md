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

**Phase 5 — integrations, at the edge. Not started.**
Israeli government and enterprise systems, accounting exports, weather,
equipment telematics. Each one is an adapter that reads or writes data the app
already owns. None of them is allowed to become a source of truth, because the
moment one is, the app stops working offline.

## What is not built

Whole categories still at zero, in roughly the order they are worth doing:

| Not started | Lens it will land in |
| ----------- | -------------------- |
| Payment applications, retention, subcontract ledgers | Money |
| Sync between devices | every lens, one mechanism |
| Israeli government and accounting integrations | the edge, Phase 5 |
| Site security, concrete and structural, underground, façade, green building, commissioning, legal, PR, weather, AI | not yet placed |

Since that list was written, five of its rows have landed and are no longer on
it: the plant register, purchase orders with goods received, toolbox talks with
permits to work, one person belonging to several companies, and snagging.

The ones that are genuinely hard are sync, the government integrations, and
anything needing a server. The rest are now another table, another lens
section, another screen — which is what the four phases above were for.

## The rule for anything not on the list

When a new feature is proposed, it gets three questions:

1. **Which lens?** If the answer is "a new one", the answer is usually no.
2. **What does it stop someone re-entering?** If nothing, it is a report.
3. **Does it work with no signal?** If not, it is an integration and lives at
   the edge.

A feature that cannot answer these is not a small feature. It is a second app.

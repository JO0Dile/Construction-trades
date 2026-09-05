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

**Phase 1 — the five lenses exist (mostly done).**
Plan and Stuff and Evidence are in the app today: tasks, materials, photos,
safety checklists, site plans. People and Money do not exist yet.

**Phase 2 — accounts and roles.**
Personal account or company account; a company holds members with roles
(admin, manager, finance, HR, worker). Roles gate lenses, per the rule above.
Local-first: an account is a row on the device, so the app still opens and
works in a basement with no signal. Sync comes later and does not change the
model. This is the phase that unlocks People, because a person on a job is a
member of the company.

**Phase 3 — People and Money.**
Timesheets and certifications hang off members. Budgets, variations and
invoices hang off jobs. Both are lenses, not new apps.

**Phase 4 — reports across jobs.**
The dashboard grows: cost to date, who is where, what is overdue, what expires
this month. Everything here is a query over data phases 1–3 already collect.
Nothing new is entered.

**Phase 5 — integrations, at the edge.**
Israeli government and enterprise systems, accounting exports, weather,
equipment telematics. Each one is an adapter that reads or writes data the app
already owns. None of them is allowed to become a source of truth, because the
moment one is, the app stops working offline.

## The rule for anything not on the list

When a new feature is proposed, it gets three questions:

1. **Which lens?** If the answer is "a new one", the answer is usually no.
2. **What does it stop someone re-entering?** If nothing, it is a report.
3. **Does it work with no signal?** If not, it is an integration and lives at
   the edge.

A feature that cannot answer these is not a small feature. It is a second app.

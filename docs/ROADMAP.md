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

**Phase 4p — one box that looks everywhere. Done.**
Forty-three destinations and a register behind most of them, and no way to find
a record without first knowing which register it was in. There were three
search boxes — the catalogue, the crew, the gate — and each one searched its
own table.

The hard part is not the searching, it is the three scripts. The existing boxes
compare with SQL `LIKE` over a lowercased column, and `LIKE` in SQLite folds
case for ASCII and nothing else: it does not fold Hebrew or Arabic at all, and
no amount of SQL will take a harakat off a letter. So the comparison moved into
Kotlin, over text with the marks stripped — Hebrew points, Arabic harakat, the
tatweel, the several accepted spellings of alef, ta marbuta against ha, and the
five Hebrew letters that change shape at the end of a word. The invisible
direction marks are dropped rather than turned into spaces: they sit inside
words in right-to-left text as a matter of course, and a search that treats one
as a word break stops finding half the database without ever saying so.

Arabic-Indic digits are folded to Latin, because `Formats` already puts them on
the screen and what is on the screen is what gets typed back in. And a number
is matched a second way, against the record's digits with the separators taken
out, so an ID typed 03-123456 finds one stored 03123456 — nobody remembers
which side of that a number went in on.

Two rules about what comes back. Every word has to appear somewhere, in any
field and in any order, because that is how a person describes a record they
half-remember. And the lens each kind needs is checked **before** the rows are
read, not after: a search that loads the wage bill and then filters it has
already loaded the wage bill. The lenses are copied off the screens themselves
rather than decided again, so search shows exactly what its register shows.

What is shown and what is matched are not the same text. A status held as
PART_RECEIVED and a role held as SITE_MANAGER fold to the words somebody would
type, so they are matched — and never printed. An ID number is matched and
never shown: typing one is how the gate finds a man and that has to keep
working, but a list of results is read over somebody's shoulder.

Seven registers so far: jobs, people, stock, orders, permits, snags and plant.
The daily logs, the pours, the scaffolds, the lifts and the rest are found
through the job they belong to, which is how anybody looks for them anyway.

**The roll call. Done.**
Every open check-in is already a statement that somebody has not left the
site, and until now nothing read them for the one question that matters when
the alarm goes: is there still a man in the building. On most sites that is
answered from a paper register kept in the hut that is on fire.

The list is taken once, at the moment the alarm goes, and never refreshed —
if it kept refreshing, a man who walked out of the gate and clocked off during
the evacuation would quietly vanish off it and nobody would know whether he
was accounted for or simply gone from the query. A doubtful name is flagged,
never dropped: somebody who forgot to clock out two days ago is almost
certainly not on site, and the one time that is wrong, leaving them off is
fatal. Standing in front of you is one state; confirmed safe by telephone is
another, and it cannot be recorded without saying how, because otherwise the
fast way to finish a roll call is to tick everybody off without ringing
anyone.

The site being cleared is worked out from the people on it rather than chosen
from a dropdown — if every open check-in names the same job, that is the site;
if they disagree, the record says none, which is true rather than convenient.
A roll call is **ended**, never closed or passed, ending it with names still
missing is always allowed because at some point the list goes to the fire
brigade, and the audit row keeps those names rather than a count.

Anybody who can write to the safety register can start one, which includes a
worker. Narrowing it to a supervisor would mean the app refuses the man
holding the phone in the one scenario the feature exists for.

Three things were added after the first release. Israel's emergency numbers
— 101, 102, 100 — are one tap away on both halves of the screen, opening the
dialer with the number in; the person presses call, so the app needs no call
permission and cannot ring from a pocket. First aiders are marked on the
list, read from their in-date first-aid tickets in any of the three
languages, and the header names the ones counted present, because the second
question at a muster point is whether anybody there knows what to do. And the
list can be handed over — to WhatsApp, a text, whatever the person uses —
running or ended, missing names first, because the moment it is needed is
when the fire brigade arrives and asks who is still inside.

**The heat check. Done.**
Heat is one of the things that most often hurts people on an Israeli site in
summer, and it does not look like a hazard. What decides it is temperature and
humidity together, because sweat does not evaporate into wet air: a humid 32
degrees on the coast reads as Danger, a dry 40 in the Arava as a step below
it, and the thermometer on the hut says the opposite.

The index is the US National Weather Service's own formula — the Rothfusz
regression with its two published adjustments, and the simpler estimate below
the range it holds for — checked against seven points of the published NWS
chart to within half a degree Fahrenheit. The bands are the NWS's, converted
exactly. None of it is offered as what Israeli law requires, and the screen
says so. A check can be marked as work in full sun, and the level is then
judged on the NWS's stated worst case rather than the shade figure.

What gets recorded is what was done, ticked by the person who did it. A reading
with nothing done is **not** refused: "Danger, nothing done" is a true record,
and a register that will not write it down hides exactly the days somebody
needs to see later. The record spells out what the level called for and was
not done.

**Numbers in any digits. Done.**
Every number field parsed with the JVM's own parser, which reads ASCII digits
only — so an Arabic keyboard typing Arabic-Indic digits entered nothing, with
nothing on screen to say why. Worse, seven decimal fields filtered their input
to digits and full stops, throwing the comma away: on a keyboard that offers a
comma as the decimal point, "7,5" became 75. A payment ten times too big, a
concrete pour ten times too large, a lift radius of twenty-five metres for one
of two and a half, and the field showed exactly what it had kept. One parser
and one input filter now serve every field, and both are tested with
Arabic-Indic, Extended Arabic-Indic and comma-decimal input.

**Plant pre-use checks. Done.**
Every excavator, telehandler, forklift and dumper is looked over by whoever
is about to drive it, before the shift. What matters about that check is what
happens when it finds something, so a defect here takes the machine out of
service at once — through the same audited status change as any other, so the
register, the dashboard and the audit trail all say so — and a later check
that happens not to spot the leak again does **not** put it back. Somebody
decides it has been put right, and says so.

A check is good for the calendar day it was made, like an excavation
inspection: a machine checked at ten to midnight is not carried through the
morning shift. Every item has to be answered, nothing starts pre-filled
(a form that opens with every item "OK" gets submitted without anybody
walking round anything), a defect has to say what it is, and a check in which
every item was marked as not applying is refused because nothing was looked
at. The register shows each machine's state for today on its row, because the
question at seven in the morning is which machines may be started.

**The construction waste register. Done.**
The first of the green-building row. Every skip and lorry of rubble leaves
for somewhere, and at the end of a job the question is whether anybody can
show where — a local authority can ask for proof that construction waste went
to a licensed facility, and a client with a green-building target asks how
much was kept out of landfill. Each load is recorded as it leaves: what it
was, how much, who took it, which facility, and the ticket, by number, by
photograph or both.

A load with no ticket is still recorded, because it still left, and counted
as unproven until one is added; the handover pack now lists those loads
beside the open permits and the standing scaffolds. Hazardous waste is the
exception and is never recorded without its ticket number. Totals are kept
per unit and never added across tonnes and cubic metres, a transfer station
is not counted as kept out of landfill because this record cannot see what
the station did with it, and a job with no loads has no diversion rate
rather than a rate of nothing. Presented as keeping the evidence for whoever
asks, not as what any authority requires on a particular job.

**The reachability pass. Done.**
The fault this codebase actually has is not a crash. It is something that
compiles, lints, passes every test and cannot be reached, or that says no
without saying anything. Two checks now fail the build on the kinds a machine
can see, and the first run of each found real bugs:

- `tools/check-unreachable.py` — a route nothing navigates to, a screen
  nothing calls, a view model action no screen invokes, a string translated
  three times and shown on no screen. It found the draft violation nobody
  could reopen, and 44 dead strings, one of them the sentence meant to say a
  photograph had failed to save. One more, a plural for how many people were
  on site, turned out to be a missing row in the daily log rather than a dead
  word — the log now counts who checked in on the job that day, beside the
  typed headcount, because a site diary is supposed to say it.
- `tools/check-ignored-results.py` — a call to a repository write that can be
  refused, whose answer nothing reads. Resolved through the container to the
  class that answers, so two functions of the same name cannot be confused.
  It found twelve on seven screens: issuing a permit, recording work stopped,
  closing it, marking a snag fixed, verifying it, signing the daily log,
  editing it, certifying a payment, marking it paid, setting an order's date,
  recording an induction, adding an ID number. Each now says so when it is
  refused. Setting a person's trade had the same fault through a `Result`.

**The protective equipment register. Done.**
What an inspector asks for after an accident is not that the firm had
helmets in the container. It is that this man was given this helmet on this
day and put his name to it. So an issue is refused without the receiver's
own signature, drawn on the phone at the container door, and it is refused
for nothing else that is not a typo: a stock shelf that reads nought does not
stop a helmet going on a head. Handing something out takes it off the stock
list in the same step, and when the shelf count was short the person issuing
is told, because they have just found out somebody took stock without
recording it.

The register shows who is holding what, with anything past its replace-by
date first. The date is picked from a few intervals so nobody counts forward
on their fingers, and the screen says that how long a harness lasts is on its
label and the manufacturer's to say — the app has no opinion on it. Handing
something back dates the row and puts nothing on the shelf, because a used
harness is not stock. Issuing is for the owner, a manager or a safety
officer, because it needs both the safety record and the list of who is in
the firm, and a worker's role is shown neither.

**The visitor log. Done.**
An inspector, the client's engineer, a driver waiting to unload: the people
least likely to know where the assembly point is, and most likely to be
forgotten at it, because nobody on the site knows their face. Each job now
has a visitor log — a name, and optionally who they are from, who they came
to see, a phone number, whether they were told the site rules, and their
signature. Only the name is required, because every field a gate log demands
is a field that gets "x" typed into it.

Anybody signed in and not out goes on the next roll call automatically,
marked as a visitor on the screen and in the text that is shared from it, and
counts towards which site the roll call is for. A visitor signed in more than
a shift ago is flagged on both the log and the roll call — and, like a stale
check-in, never dropped from it.

**Translation sheets that keep up. Done.**
The spreadsheets a buyer hands to the people who fix the Hebrew and Arabic
were written once, by hand, and the app kept growing after them: 478
interface strings, the twenty count forms and thirty hand tools had never
been on them, so nobody would ever have been asked to translate them. Both
generators now rebuild the sheets from the app and the catalogue, carry
across every column a translator typed, carry the developer's note on where
a string appears, and fail the build when a sheet falls behind.

**Choosing a language changes the whole app. Done.**
For twenty-four versions picking Hebrew left most of the screen in English.
The translations were all in the APK; the manifest told Android the activity
handled language changes itself, so the screen was never rebuilt and only the
text the app looks up for itself switched. Now the activity is recreated, the
choice is kept across restarts on older Android, and the libraries' Hebrew is
no longer stripped from the build. `tools/check-locale-switch.py` fails the
build if any of the three comes back.

That was not the whole of it. The first test to ask Android's own resource
lookup what a Hebrew phone is shown, rather than reading the files, failed:
Android turns "he" into the old code "iw" before it looks anything up, and
the app's Hebrew was filed only under "he". The catalogue, which the app
looks up by the new code itself, came out in Hebrew; every button and heading
stayed English. The generator now writes the Hebrew file under both codes,
and the same check fails the build if the two ever differ.

**Every update says what it does. Done.**
Written once per version in English, Hebrew and Arabic, shipped in the app
and attached to the release. A phone offered an update lists every version
between the one it has and the one on offer; after installing, What's new
lists everything since it last looked; Settings keeps the history. The build
fails until the version has notes in all three languages.

**Concrete cube results. Done.**
The lab's seven- and twenty-eight-day figures against each pour, every cube
kept rather than only the mean. A twenty-eight-day mean under the strength the
mix asks for, or any cube under it, is marked for the engineer on the pour and
in the pour list. Whether a pour conforms is the engineer's call under the
standard; the screen says so.

**The drawing register. Done.**
Each drawing and the revision of it to build from. A new revision replaces the
old one on every phone in one step; the list shows only current revisions,
and an earlier one opens marked as replaced. Revisions are ordered by when
they arrived rather than by their letters, because firms letter them
differently.

**Tickets by trade. Done.**
The catalogue now says which tickets a trade usually needs — a licensed
electrician, welding, work at height — only where there is one answer, and
the People lens lists who lacks one or holds only an expired one. "Usually"
is the screen's own word: what a job calls for is the job's to say. A ticket
counts when its title is the kind's name in any of the three languages, the
rule the roll call already uses to find first aiders.

**Questions to the designers. Done.**
Every question put to the architect, the engineer or the supervisor gets a
number, the day it was asked, who it was put to, the drawing it is about, and
the day the site needs the answer. An unanswered one past that day — on the
site's own calendar, so Thursday evening is still Thursday — is shown first
and in red, the answer is written once and not edited afterwards, and the
handover pack counts the ones never answered.

**Inspection requests. Done.**
Before the steel is poured over, the membrane screeded or the wall closed,
somebody is asked to look. Each request says what and where, of whom, and
the day the site wants them, and gets a number; the result is written once
with the name of whoever inspected, a failure needs its reason, and a photo
of the signed form can go with it. A failed inspection is not edited into a
pass: it is asked again under a new number that points back at it, so the
record keeps both. A pour shows the passed inspection of the steel or the
forms that let it go ahead, or says none is recorded, and the handover pack
counts both. Which elements need inspecting, and by whom, is the job's
specification; the register only keeps the record.

**Phase 5 — integrations, at the edge. Not started.**
Israeli government and enterprise systems, accounting exports, weather,
equipment telematics. Each one is an adapter that reads or writes data the app
already owns. None of them is allowed to become a source of truth, because the
moment one is, the app stops working offline.

## What is not built

Whole categories still at zero, in roughly the order they are worth doing:

| Not started | Lens it will land in |
| ----------- | -------------------- |
| Sync between devices | every lens, one mechanism |
| Israeli government and accounting integrations | the edge, Phase 5 |
| Site security, structural, underground, façade, the rest of green building, legal, PR, weather, AI | not yet placed |

Since that list was written, twelve of its rows have landed and are no longer
on it: the plant register, purchase orders with goods received, toolbox talks
with permits to work, one person belonging to several companies, snagging, the
concrete half of "concrete and structural", the scaffold register, lifting
operations, temporary works, excavations, subcontract ledgers, and
commissioning as a stage of work — leaving "underground" on that row meaning
services diversions and tunnelling rather than trenches.

Backup and restore has since landed and is off that list. It is not sync —
it is one phone's record, taken deliberately to a file the person keeps — but
it closes the gap that mattered most: everything lived on one device and a lost
device lost all of it.

The ones that are genuinely hard are sync, the government integrations, and
anything needing a server. The rest are now another table, another lens
section, another screen — which is what the four phases above were for.

## More than one firm on a job

Everything above was written for a firm running its own work. The multi-tier
model changes what the app is: a general contractor, the subcontractor it
engaged, and that subcontractor's crew can all be on the same job in the same
database, and none of them may see what the others agreed.

Three rules carry it, all in `core/` with tests, all deliberately small:

* **`access/Party`** — what a firm is *on this job*, which is not what it is on
  the next one. Engagement runs downward only, so the chain stays a chain.
* **`access/Commercial`** — a commercial figure may be sent only to a party to
  the contract it belongs to. Everything else is a consequence of that one
  sentence.
* **`work/Assignment`** — the lifecycle, with a side that owns each move. Only
  the crew accepts and submits; only the payer approves and cancels.

The part that is **not** built, and is the reason `docs/SERVER.md` exists: on
one device these are display rules. The moment two firms share a job they are
access rules, and an access rule that lives in the client is not an access
rule. Until there is a server running the same functions, this is a model that
is correct and unenforced.

## The 350, honestly

A full list of 350 wanted features exists. **It is not in this repository**,
which is why the first row below is an estimate and the rest are not. Sorted
by what it would actually take to build them, it comes out roughly:

| | About | |
| --- | ---: | --- |
| Built | ~97 | *estimated — see below* |
| Buildable here — on the device, no server | ~90 | where the work is |
| Needs a server | 60 | sync, chat, push-to-talk, client portal |
| Needs an API somebody has to grant | 55 | government bodies, Priority/SAP, weather, traffic, CCTV |
| Needs hardware | 30 | turnstiles, biometrics, sensors, drones, wearables |
| Is a product in its own right | 25 | BIM viewer, AR overlay, CAD engine, the AI predictions |

The built figure has gone 35 → 44 → 76 → 77 → ~89 → ~91 → ~94 → ~95 → ~96 → ~97 since the third of
September. The last exact count was 77, on the fourteenth. Landing since
then, and plausibly items on the list: site admission at the gate, one search
box across the whole app, terms of service, the stock item detail sheet, the
plans screen, the rules for a phone verification code, restore actually
reaching the end of its own mechanism, the emergency roll call, the heat
check, plant pre-use checks, the waste register, the protective equipment
register, the visitor log, and inspection requests.

**It is an estimate and it should not be quoted as anything else.** Counting
it properly needs the 350-item list, which lives outside this repository.
Anybody holding that list can settle it in an afternoon: mark each line
built, buildable here, or one of the four kinds of impossible, and replace
this table with the real numbers. It is worth doing before the list is shown
to anybody deciding whether to buy this.

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

# When you get a server

Nothing in the app needs one. Every screen reads and writes the phone's own
database and nothing waits on a network, which is the whole reason it works in
a basement car park. A server adds one thing: the same data on more than one
phone.

This is what is already built for that, what is not, and what to buy.

## What to buy

**Not a server, and not a switch.**

A 24-port switch connects wired devices inside a building — computers,
printers, cameras, wireless access points. It has nothing to do with serving an
app to phones. The crew's phones are on mobile data on a site; they reach
anything over the public internet whether the box is in your office or in a
data centre.

What this app needs is small: something always on, with an HTTPS address, a
database, and backups. For a handful of firms that is a **rented virtual
machine at around €5 a month** — Hetzner, DigitalOcean, Lightsail, any of them.
Ten minutes to set up, cancel any time, grow it later if it needs growing.

A machine in your own office costs more than the machine:

- a static address from your ISP, which is a paid extra in Israel
- a domain and TLS certificates
- patching, and somebody to notice when it stops
- a UPS, because it is offline exactly when the office loses power — which is
  also when somebody on site needs it

And there is a harder reason. The app holds **ID numbers and photographs of ID
documents**. That is sensitive personal data about your workers. A hosting
provider with encrypted disks, real backups and a door with a lock on it is far
easier to defend than a tower under a desk that can be carried out of the
building.

Buy the switch when you are wiring an office or a site cabin. That is a
different job.

## What is already built

**The conflict rules.** `core/sync/SyncPolicy` and `core/sync/Revision`, with
tests. This is the part worth getting right and the part that has nothing to do
with networks:

- **A signature is never overwritten.** A permit that was issued, a daily log
  that was signed, an induction — these beat an edit made on another phone
  whatever the timestamps say, because that edit was made in ignorance of the
  signature. Two different signatures on one row is the one case no rule may
  decide, so it is reported rather than resolved.
- **Records of what happened are kept, not merged.** Stock movements, audit
  entries, incidents, photographs. Two phones can only ever add different ones.
  This is why stock levels are derived from movements rather than stored as a
  number: a number has to be merged and can be lost, a list cannot.
- **Everything else is latest-write-wins**, ordered by timestamp and then by
  device id. The device id matters more than it looks: two phones offline on
  one site can write in the same millisecond, and without a tie-break every
  device would reach a different answer and the row would flip forever.

**A device identity.** Minted once per installation and kept, because a device
that gets a new id every launch loses every argument it has already won.

## What is not built, and cannot be

- **The transport.** There is nothing to talk to.
- **Authentication between phone and server.** Accounts are local rows today.
- **Tombstones.** A delete on one phone has to be a delete everywhere, rather
  than a row that quietly comes back on the next sync. This needs a server to
  agree with.
- **A time authority.** A phone with its clock set wrong is the failure that
  ruins last-writer-wins: it wins every argument on every device from then on,
  silently. The app can spot a timestamp from the future
  (`Revisions.isFromTheFuture`) but it cannot know the real time. Only the
  server can, and it should stamp what it accepts.

## The order to do it in

1. Rent the smallest machine. Put a database on it and nothing else.
2. Authentication first, before any data moves. An endpoint that trusts
   whatever a phone claims is worse than no endpoint.
3. Push before pull. Getting local changes off a phone that might be lost or
   broken is the half that actually protects anybody.
4. Then pull, applying `SyncPolicy` on the way in.
5. Tombstones last, once the first four are boring.

Do not start with real-time. Nothing on a building site needs to be
instantaneous, and a sync that runs when the phone finds Wi-Fi is both simpler
and better suited to a place where signal comes and goes.

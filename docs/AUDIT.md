# The audit trail, and how to check it without the app

Every change of consequence in this app is written to an audit trail. This
document is for the person who has to decide whether to believe it — an
inspector, an auditor, a court, a client in a payment dispute.

## Why signing was necessary

The trail has never had an update method. That was the whole of the
protection, and it is not enough: the database is a file on a phone, and
anybody who can reach it with a SQLite editor can change what a row says or
remove one. "We did not write an update method" is not an answer to "was this
log edited".

So each entry now carries a sequence number and a SHA-256 taken over
everything it says, together with the hash of the entry before it.

- **Change a word** and that entry's hash no longer matches, and its
  successor's link breaks.
- **Remove an entry** and there is a hole in the sequence and a broken link
  across the gap.
- **Insert one** and it would have to produce a hash the following entry
  already committed to.

## What it does not do

It does not prevent alteration. It makes alteration show.

And it cannot detect the newest entries being deleted, because nothing has
yet committed to them: a trail truncated at the end is indistinguishable from
a trail that stopped. Detecting that needs the head of the chain written
somewhere the holder of the phone does not control. That is a server, it does
not exist yet, and `docs/SERVER.md` treats it as outstanding rather than done.

Two further limits, stated because they bound what a verified export proves:

- Verification covers the most recent 2,000 entries. Both the screen and the
  exported document say so.
- Entries written before this feature shipped carry no signature. They are
  reported as unverifiable rather than counted as intact, because "intact"
  over a log that was never signed is the more dangerous answer.

## How long the trail is kept

Nothing is deleted by default. **Settings → Audit trail** offers a retention
period — keep everything, one year, three years, seven years — and applying it
is a separate, deliberate action rather than something that happens quietly in
the background while somebody is on another screen.

The app supplies the mechanism and declines to supply the policy. How long
site records must be held is a legal question with different answers for a
payment application, a scaffold inspection and a worker's ID number, and it is
answered by the organisation running the app, not by whoever wrote the code. A
period invented in the source would destroy evidence on somebody else's job,
months later, with nobody having decided anything.

A purge writes its own entry recording how many entries it removed, the cutoff
it used, and the sequence and previous hash of the oldest entry that survived.
That last part matters: the first surviving entry points at something that is
no longer there, which is the one break verification cannot tell from a
deletion. The `PURGE` row is what explains it.

## What is hashed

SHA-256 over these values, in this order, each prefixed with its length in
**UTF-8 bytes** and followed by a semicolon:

```
previousHash, sequence, entityType, entityId, action,
actorId, actorName, summary, payloadJson, occurredAtMillis
```

An absent field is written as `-;` instead, which no length-prefixed value can
produce — so an absent payload and an empty one are different records.

The length counts bytes rather than characters so that no value can be made to
look like the end of one field and the start of the next. A log is exactly
where somebody would put a separator on purpose, and `העקדה` is five
characters but ten bytes.

The first entry's `previousHash` is sixty-four zeros.

The implementation is `core/security/AuditChain.kt`. Its tests assert the
digests against an implementation written separately in Python; if the two
ever disagree, one of them changed the meaning of a signed record.

## Checking an export

In the app: **Settings → Audit trail → the share button**. It verifies first,
then writes two files.

- The **PDF** is for reading. It shortens signatures to stay legible on A4 and
  does not carry the fields needed to recompute them.
- The **CSV** is for checking. It carries every field the signature was taken
  over.

Then, on any computer with Python:

```
python3 tools/verify-audit-export.py audit-trail-2026-09-07.csv
```

It exits 0 if the trail verifies and 1 if it does not, naming the first entry
that fails and why. It needs nothing from this repository but itself.

Two things to know when reading its output:

- An empty `actorId` or `payloadJson` cell means the field was **absent**. The
  app never stores an empty string in either.
- The readable *When* column is formatted for a person. `occurredAtMillis` is
  the value that was hashed, and is the one the checker uses.

A note that the trail "starts at an entry not in this file" is expected after
a retention purge. The purge writes its own entry recording what it cut, which
is the one break the chain cannot tell from a deletion — so look for the
`PURGE` row and read its payload.

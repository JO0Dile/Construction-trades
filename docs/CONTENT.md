# Sending content

Four kinds of thing are worth sending, and they want four different formats.
None of them needs a shared folder or a hand-off — they all go straight into
the repository, on a branch, and CI checks them the moment they land.

| What | Where it goes | Checked by |
| ---- | ------------- | ---------- |
| Photographs of items | `shared/assets/catalog/images/` | `tools/image-coverage.py` |
| Trade terminology | `shared/assets/catalog/items/<trade>.json` | `CatalogIntegrityTest` |
| Supplier part lists | `shared/assets/catalog/items/<trade>.json` | `CatalogIntegrityTest` |
| Prices | not yet — see below | — |

## 1. The photographs

**The filename is the item id.** That is the entire rule.

```
shared/assets/catalog/images/
  el.rcd.40a.30ma.webp
  pl.pipe.pex.16.webp
  ca.tool.circular_saw.webp
```

To see every id that still has no picture, grouped by trade:

```bash
python3 tools/image-coverage.py           # 0 of 497 today
python3 tools/image-coverage.py --ids     # just the ids, one per line
```

If your files are named after the things rather than the ids — `RCD 40A.jpg`,
`צינור פקס 16.png` — don't rename 497 files by hand:

```bash
python3 tools/prepare-images.py ~/photos --dry-run   # see what it would match
python3 tools/prepare-images.py ~/photos             # convert, resize, rename
```

It matches loosely (case, spaces, dashes and underscores are all ignored),
converts to WebP at 512×512, and **lists what it could not match rather than
guessing**. Anything on that list needs renaming by hand or is an item we do
not have.

Target about 40–60 KB each; 497 of them is then roughly 25 MB. That is more
than the app used to carry and worth a decision rather than a shrug: either
ship the lot, or ship the trades most customers switch on and let the rest
come down with a catalogue refresh.

**One thing to be careful about:** a photograph from a supplier's catalogue or
website is their copyright, and shipping it inside an app is redistribution. If
you took the picture, or the supplier has said yes in writing, it is fine.
Otherwise it is a real risk at store review, not a theoretical one. Your own
photographs of your own stock are always safe and honestly look better — they
show the thing as it actually arrives.

## 2. Trade terminology and part lists

These are the same file, so send them together. One JSON file per trade, and
every item looks like this:

```json
{
  "id": "el.rcd.40a.30ma",
  "kind": "FITTING",
  "category": "breaker",
  "unit": "PCS",
  "names": {
    "en": "RCD 40A 30mA",
    "he": "מפסק פחת 40A 30mA",
    "ar": "قاطع تسرب أرضي ٤٠ أمبير ٣٠ مللي أمبير"
  },
  "spec": {
    "en": "2-pole, type A, 6kA",
    "he": "דו-קוטבי, טיפוס A, 6kA",
    "ar": "قطبان، النوع A، ٦ كيلو أمبير"
  },
  "attributes": { "poles": "2", "type": "A" },
  "tags": ["rcd", "פחת", "قاطع"]
}
```

What matters, in order:

1. **All three languages, every time.** `CatalogIntegrityTest` fails the build
   if any name or spec is missing one — an untranslated item shows as English
   on a Hebrew phone, which is exactly the thing this app exists not to do.
2. **The Hebrew and Arabic are the valuable part.** English I can write. What I
   cannot invent is what an electrician in Haifa actually calls it out loud, or
   the Arabic a crew uses on site rather than the dictionary word. That is the
   content nobody else can supply.
3. **`tags` is the search index.** Put the slang in here — the shortened forms,
   the misspellings people type, the supplier's own name for it. Searching
   `פחת` in English mode already finds the RCD because every translation and
   tag is indexed together; more tags makes that better.
4. **`category` must have an icon.** The list is `categoryIcons` in
   `manifest.json`. A new category needs one line added there or the build
   fails — deliberately, so a new trade cannot quietly ship looking unfinished.

**If JSON is awkward, send a spreadsheet instead.** One row per item, columns:
`id`, `kind`, `category`, `unit`, `name_en`, `name_he`, `name_ar`, `spec_en`,
`spec_he`, `spec_ar`, `tags`. I will convert it. Do not hand-write JSON if a
spreadsheet is faster for you — a comma in the wrong place costs more of your
time than the conversion costs mine.

Suppliers' part numbers are welcome as an extra column; they belong in
`attributes` (`"sku": "..."`), and they make barcode scanning far more useful
because a scanned code can then match a real catalogue item.

## 3. Prices — hold these for now

There is nowhere good to put prices yet, and putting them in the catalogue
would be a mistake:

- A price is not a property of a wall plug. It is a property of a wall plug
  **from a supplier, on a date, at a quantity**. One number in the catalogue is
  wrong for everyone the moment it is written.
- Prices belong to the **Money** lens (`docs/ROADMAP.md`, Phase 3), alongside
  suppliers and quantity breaks. That model does not exist yet.

So keep them, and send them with the supplier they came from and the date. When
Phase 3 lands they go in whole and stay correct. `purchasePrice` on a stock item
exists today for what *you* paid for the thing on your van, which is a different
number and already works.

## 4. How to actually send them

Whichever is easier:

- **A branch.** `git checkout -b content/electrical-hebrew`, drop the files in,
  push. CI tells you within about three minutes whether every item is
  translated and every category has an icon.
- **Send them to me here.** A spreadsheet, a zip of photos, a plain list of
  terms — I will format, place and push them. For a large batch of photos this
  is usually slower than a branch, because they have to travel through the
  conversation.

Either way the checks are the same, and they run before anything reaches a
phone.

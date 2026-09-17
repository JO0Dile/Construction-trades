# Catalogue format

The files in `shared/assets/catalog` are read unchanged by both apps: Gradle
merges the directory into the APK's assets, and the iOS target adds it as a
folder reference in the bundle. A change here is a change to a published data
format, and it breaks both apps at once — which is why the format carries a
`schemaVersion` and why `CatalogIntegrityTest` runs against these files rather
than fixtures.

```
shared/assets/catalog/
  manifest.json           the trades, and which files describe each
  items/<trade>.json      tools and materials
  safety/<trade>.json     checklists, with the regulation each comes from
  templates/<trade>.json  reusable job set-ups
```

## Versioning

- `schemaVersion` — the *shape*. A loader that does not know a schema version
  refuses the file rather than half-reading it.
- `catalogVersion` — the *content*. The app re-loads reference data when the
  shipped version is ahead of what the device has loaded, and prefers a
  downloaded catalogue only when its version is higher. This is what allows a
  catalogue correction to reach devices without a store release.

## manifest.json

```json
{
  "schemaVersion": 1,
  "catalogVersion": 1,
  "trades": [
    {
      "id": "electrical",
      "icon": "bolt",
      "colorHex": "#F2B705",
      "names": { "en": "Electrical", "he": "חשמל", "ar": "كهرباء" },
      "itemsFile": "items/electrical.json",
      "safetyFile": "safety/electrical.json",
      "templatesFile": "templates/electrical.json"
    }
  ]
}
```

## items/&lt;trade&gt;.json

```json
{
  "tradeId": "electrical",
  "catalogVersion": 1,
  "items": [
    {
      "id": "el.rcd.40a.30ma",
      "kind": "MATERIAL",
      "category": "protection",
      "unit": "PCS",
      "names": { "en": "…", "he": "…", "ar": "…" },
      "spec":  { "en": "…", "he": "…", "ar": "…" },
      "attributes": { "ratedCurrentA": "40", "sensitivityMa": "30" },
      "tags": ["rcd", "safety", "board"]
    }
  ]
}
```

- `id` is global, not per trade, and is permanent. The seeder's duplicate guard
  is "this catalogue id is already stocked", so changing an id creates a second
  copy of an item somebody has been counting. Add a new id instead; never
  rename one.
- `kind` — `TOOL`, `MATERIAL`, `SAFETY`, `FITTING`, `CONSUMABLE`, `OTHER`.
- `unit` — `PCS`, `M`, `M2`, `M3`, `KG`, `L`, `ROLL`, `BAG`, `PAIR`, `BOX`. An
  unknown code degrades to "pcs" in the UI rather than crashing.
- `attributes` is free-form. It is deliberately a string map so a new
  specification field needs no schema change and no database migration.

## safety/&lt;trade&gt;.json

```json
{
  "tradeId": "electrical",
  "catalogVersion": 1,
  "checklists": [
    {
      "id": "sf.el.loto",
      "mandatoryBeforeWork": true,
      "titles": { "en": "…", "he": "…", "ar": "…" },
      "references": ["חוק החשמל, התשי״ד-1954"],
      "items": [
        { "id": "sf.el.loto.2", "critical": true, "texts": { "en": "…", "he": "…", "ar": "…" } }
      ]
    }
  ]
}
```

- `critical: true` means a `FAIL` or an unanswered check **blocks sign-off**.
  The block is recomputed from the answers every time
  (`SafetyRepository.refreshBlockedState`), so no screen can clear it.
- `mandatoryBeforeWork` marks a checklist that must be completed before the
  work it covers. A mandatory checklist with no critical item can always be
  signed and is therefore decorative; `CatalogIntegrityTest` fails on one.
- `references` must name the standard or regulation the checklist encodes. The
  test fails on an empty list, because an unsourced safety instruction is worse
  than none — it looks authoritative and cannot be checked.

**These references are pointers for the site file. They are not the text of the
standard, they are not legal advice, and they must be reviewed against the
current published regulation before the app is relied on for compliance.** The
app says so itself, on the Safety screen.

## templates/&lt;trade&gt;.json

```json
{
  "tradeId": "plumbing",
  "catalogVersion": 1,
  "templates": [
    {
      "id": "tpl.pl.bathroom",
      "estimatedDays": 4,
      "names": { "en": "…", "he": "…", "ar": "…" },
      "descriptions": { "en": "…", "he": "…", "ar": "…" },
      "materials": [{ "itemId": "pl.pipe.pex.16", "quantity": 45 }],
      "tasks": [{ "id": "tpl.pl.bt.1", "order": 1, "titles": { "en": "…", "he": "…", "ar": "…" } }]
    }
  ]
}
```

Every `itemId` must exist in some trade's `items` file. A template naming a
missing item would create a project with a line nobody can buy, so the test
fails on it.

## scopes.json

The work breakdown. Two dimensions that are constantly confused for one:

* **stage** — how far up the building has got. `civil` → `slab` → `rough-in`
  → `second-fix` → `commissioning` → `handover`.
* **scope** — the trade operation itself, grouped into `phases`.

"Electrical, third floor" is not one job. It is slab conduit before the pour
(העקדה / العقدة), rough-in in the walls (الشغل الأسود), trim-out after paint
(الشغل الأبيض), and commissioning — four crews, four prices, four
inspections, months apart. A task that cannot say which of the four it is
cannot be priced, assigned or signed off.

```json
{
  "schemaVersion": 1,
  "catalogVersion": 2,
  "stages": [
    {
      "id": "rough-in",
      "names": { "en": "…", "he": "…", "ar": "…" },
      "colloquial": { "ar": "الشغل الأسود" },
      "descriptions": { "en": "…" }
    }
  ],
  "phases": [{ "id": "electrical", "names": { "en": "…", "he": "…", "ar": "…" } }],
  "scopes": [
    {
      "id": "electrical-rough",
      "phaseId": "electrical",
      "stageId": "rough-in",
      "tradeId": "electrical",
      "names": { "en": "…", "he": "…", "ar": "…" },
      "colloquial": { "ar": "الشغل الأسود / زرع العلب" },
      "descriptions": { "en": "…" }
    }
  ]
}
```

`colloquial` is what the crew actually says, and it is searched alongside
`names`. A foreman typing "العقدة" has to land on slab conduit; a search that
only knows the contract wording is decoration. `tradeId` is a suggestion for
who usually carries the scope, never a restriction on who may be assigned it.

## Editing safely

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*CatalogIntegrityTest'
```

That parses every file through the app's own model types with **unknown keys
rejected**, so a typo'd field name fails loudly instead of being dropped at
runtime, and checks that every item is trilingual, ids are unique, mandatory
checklists have teeth, checklists cite a source, template lines resolve, and
every scope points at a stage, phase and trade that exist.

A `spec` is optional. "Side cutters" needs no sentence explaining it, and
inventing one in three languages produces filler rather than help. What is not
optional is finishing it: an item with an English spec and no Hebrew one is
half-translated on the phone where it matters, and the test says so.

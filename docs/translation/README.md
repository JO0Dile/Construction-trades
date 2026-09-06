# Translation and photography worksheets

Two spreadsheets. Both are UTF-8 with a byte-order mark, so Excel opens them
in Hebrew and Arabic without mangling anything.

## items.csv — 497 catalogue items

One row per thing the app can stock. Columns:

| column | what it is |
| --- | --- |
| `trade` | which trade's list it appears in |
| `id` | the app's own key. **Do not change it** — it is what the phone databases already store |
| `kind` | MATERIAL, CONSUMABLE, TOOL or SAFETY |
| `category` | how it groups on the stock screen |
| `english` | the source wording |
| `hebrew_draft`, `arabic_draft` | what ships today |
| `hebrew_yours`, `arabic_yours` | **fill these in** with the wording actually used on site |
| `image_note` | anything about the photograph — which variant, which angle |

Leave `*_yours` blank where the draft is already right. Only the filled rows
get changed, so a partial pass is useful.

The `id` is also the picture's filename: a photograph saved as
`shared/assets/catalog/images/el.tool.cable.tracer.webp` appears against that
item with no code change. See `docs/CATALOG_FORMAT.md`.

## work-breakdown.csv — 73 rows

Stages, phases, scopes of work, and the trade names themselves.

`street_arabic` is the extra column that matters. It is what the crew says
rather than what the contract says, and the app searches it alongside the
formal name — so a foreman typing `العقدة` finds slab conduit, and `الشغل
الأسود` finds rough-in. Where a stage or scope has a Hebrew site term that
differs from the formal one, put it in `street_yours`; the field takes any
language.

## interface.csv — 901 strings

Every word the app itself says: buttons, labels, hints, warnings, error
messages. Same shape as the others — `hebrew_yours` and `arabic_yours` are the
columns to fill, and blank means the draft is fine.

`where_it_appears` is a note about the context, present on the strings where
it matters. Read it: "Sign" on a contract and "Sign" on a permit are different
words in Hebrew, and this is where that gets caught.

Two things not to change:

* **`key`** is what the code looks the string up by. Changing it breaks the
  screen.
* **Anything in `{}` or `%s`** is a value the app substitutes in — a name, a
  number, a date. It has to survive into the translation, though it may move
  within the sentence.

This is the largest of the three and the least urgent. The item names are what
the photography waits on; the interface can be corrected in passes.

## Terms flagged for confirmation

Three items were named from a spoken description and should be checked before
the photography is commissioned:

* `el.tool.rotary.sds_plus` — recorded as **باتيشون**, read as *percussion*
  (a hammer drill). If it means something else on site, say so.
* `el.tool.breaker.sds_max` — **كونجو**, the large breaker for chasing.
* `el.tool.cable.tracer` — **مزمار**, from "one end clips to the board, the
  other follows the wire and buzzes". Filed as a tone generator and probe.

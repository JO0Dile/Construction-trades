# Item photographs

Drop an image in here named after the catalogue item id and it appears in the
app. No code change, no registration, no list to update.

Live on Android today: the directory is listed once at startup and the id is
looked up per row. The iOS target bundles the same directory, but its stock
rows do not draw thumbnails yet, so the pictures are shipped and unused there
until they do.

```
shared/assets/catalog/images/
  el.rcd.40a.30ma.webp
  pl.pipe.pex.16.webp
  ca.tool.circular_saw.webp
```

## The rule

**File name = item id + extension.** The id is the `id` field in
`shared/assets/catalog/items/<trade>.json`. To see every id, and which ones
still have no picture:

```bash
python3 tools/image-coverage.py
```

Accepted extensions, in the order the apps look for them: `.webp`, `.jpg`,
`.png`. Prefer **WebP** — same picture, roughly a third of the size.

## What the apps do with them

A stock row shows, in this order of preference:

1. a photo **the user took of their own item** — always wins, it is their
   actual stock in its actual state;
2. the picture in this folder;
3. the category icon.

So adding images here never overrides anybody's own photograph, and any item
you have not got round to still looks finished.

## Size

Aim for **512×512** and about **40–60 KB** each. At that size the whole set of
178 costs roughly 8 MB, which fits the store budget with room to spare.

Do not drop 4 MB camera originals in here: 178 of those is 700 MB, which is
larger than the app is allowed to be. If what you have is camera originals or
supplier images at odd sizes, this converts and renames a whole folder for you:

```bash
python3 tools/prepare-images.py ~/Downloads/product-photos
```

## Legal

Only add images you have the right to ship: your own photographs, or supplier
images you have permission to redistribute. A product shot lifted from a
distributor's website is their copyright, and it ships inside the app to every
user — which is exactly the kind of thing that gets an app pulled from a store.

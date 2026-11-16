# Nuvio One TV identity

Original N/play monogram created for this fork. `logo-master.png` is the transparent
master asset. Android resources use small resized copies for low startup memory.

Regenerate launcher icons, optional TV badge variants, TV banners and wordmarks:

```bash
python3 scripts/package-nuvio-branding.py
```

Requires ImageMagick (`magick`). The script uses the font already distributed in
the app, preserves all six launcher background color options, and leaves the
master untouched. The Compose launch screen adds no video or minimum delay and
keeps profile colors and background pictures supported.

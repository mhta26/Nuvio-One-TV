# Subtitle fonts

The requested **IBM Plex Sans Arabic Medium** file is retained unchanged as the source font. The playback font adds missing Noto symbol glyphs (including ♪ ♫ ★ ♥) directly, so TV renderers can display them even without suitable system fallback fonts. It preserves all **1706 original glyph contours and advance widths**, original cmap glyph IDs, Medium weight, and horizontal text metrics. HarfBuzz checks also confirmed identical Arabic shaping, kerning and diacritic positions for seven mixed-language samples.

The packaged derivative is named **Nuvio Subtitle Arabic**, as required by IBM's OFL Reserved Font Name “Plex”. The requested Arabic/Latin face is visually unchanged. It has **4515 Unicode mappings** in about 981KiB. Only this single font is loaded during playback; no per-character font probing, extra rendering timers, font downloads, or application-startup font loading are needed. Native font extraction runs on IO before mpv initializes.

ExoPlayer, MPV and external ASS in libass/VLC use the same font data. Embedded subtitle font names/styles are not rewritten. External text timings, colors and emphasis are preserved by the existing conversion path. System fallback remains available for scripts outside these 4515 mappings; no finite font covers all Unicode characters or emoji.

Sources, with SIL OFL copies bundled under `app/src/main/assets/licenses`:

- IBM: https://raw.githubusercontent.com/IBM/plex/master/packages/plex-sans-arabic/fonts/complete/ttf/IBMPlexSansArabic-Medium.ttf
- Noto Symbols: https://raw.githubusercontent.com/google/fonts/main/ofl/notosanssymbols/NotoSansSymbols%5Bwght%5D.ttf
- Noto Symbols 2: https://raw.githubusercontent.com/google/fonts/main/ofl/notosanssymbols2/NotoSansSymbols2-Regular.ttf

Rebuild the derivative with Python `fonttools` installed:

```sh
python3 scripts/package-subtitle-font.py --symbols '/path/NotoSansSymbols[wght].ttf' --symbols2 /path/NotoSansSymbols2-Regular.ttf
PYTHONPATH=scripts python3 -m unittest discover -s scripts/tests -p 'test_*.py'
```

The script pins upstream input SHA-256 values and asserts every original outline, advance and cmap ID after packaging. App builds use the committed TTF files and require no fonttools. CI parses the actual shipped SFNT cmap tables with the Python standard library and checks Arabic, standard/Quranic marks, Latin, digits, quotes/brackets, and symbols. Native libass was also checked with system fonts disabled: the mixed Arabic/English/quotes/music/symbol sample rendered with this font without a missing-glyph fallback.

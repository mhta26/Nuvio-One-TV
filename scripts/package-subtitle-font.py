"""Create a static subtitle font without changing IBM glyphs; requires fonttools.

Inputs: official Noto fonts linked in SUBTITLE-FONTS.md. App builds use the committed TTF.
The derivative is renamed because IBM reserves the name Plex under its OFL license.
"""
import argparse
import hashlib
from pathlib import Path
import tempfile

from fontTools import subset
from fontTools.merge import Merger
from fontTools.pens.recordingPen import DecomposingRecordingPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

parser = argparse.ArgumentParser()
parser.add_argument('--symbols', type=Path, required=True)
parser.add_argument('--symbols2', type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
fonts = root / 'app/src/main/res/font'
original = fonts / 'ibm_plex_sans_arabic_medium.ttf'
expected = ('f7e7e04b4a24b6c78893d50cbfd2b2f6cae49617ab047bfef668d252adb128f7',
            '7d5fb73b7ca67a6798101741f5d280a3d016a56a197afcd4199dbb57b4b82a21')
base = TTFont(original)
used = set(base.getBestCmap())
with tempfile.TemporaryDirectory() as directory:
    inputs = [str(original)]
    for index, source in enumerate((args.symbols, args.symbols2)):
        assert hashlib.sha256(source.read_bytes()).hexdigest() == expected[index], 'Unexpected source font'
        font = TTFont(source)
        if 'fvar' in font:
            font = instantiateVariableFont(font, {'wght': 400}, inplace=True)
        points = set(font.getBestCmap()) - used
        options = subset.Options()
        job = subset.Subsetter(options=options)
        job.populate(unicodes=points)
        job.subset(font)
        used.update(font.getBestCmap())
        # IBM has horizontal metrics only. Preserve those; do not introduce vertical layout tables.
        for tag in ('vhea', 'vmtx', 'VORG'):
            if tag in font:
                del font[tag]
        path = Path(directory) / f'symbols-{index}.ttf'
        font.save(path)
        inputs.append(str(path))
    merged = Merger().merge(inputs)
    for key in ('ascent', 'descent', 'lineGap'):
        setattr(merged['hhea'], key, getattr(base['hhea'], key))
    for key in ('sTypoAscender', 'sTypoDescender', 'sTypoLineGap', 'usWinAscent', 'usWinDescent', 'usWeightClass'):
        setattr(merged['OS/2'], key, getattr(base['OS/2'], key))
    names = {0: 'Copyright IBM Corp. and The Noto Project Authors. SIL OFL 1.1.',
             1: 'Nuvio Subtitle Arabic', 2: 'Medium', 3: 'NuvioSubtitleArabic-Medium',
             4: 'Nuvio Subtitle Arabic Medium', 6: 'NuvioSubtitleArabic-Medium',
             16: 'Nuvio Subtitle Arabic', 17: 'Medium'}
    for record in merged['name'].names:
        if record.nameID in names:
            record.string = names[record.nameID].encode(record.getEncoding(), errors='replace')
    output = fonts / 'nuvio_subtitle_arabic_medium.ttf'
    merged.save(output)

# Verify all original glyphs, including contextual Arabic forms not directly mapped by cmap.
result = TTFont(output)
before, after = base.getGlyphSet(), result.getGlyphSet()
new_order = result.getGlyphOrder()
for index, glyph in enumerate(base.getGlyphOrder()):
    first, second = DecomposingRecordingPen(before), DecomposingRecordingPen(after)
    before[glyph].draw(first)
    after[new_order[index]].draw(second)
    assert first.value == second.value, f'Original outline changed: {glyph}'
    assert before[glyph].width == after[new_order[index]].width, f'Advance changed: {glyph}'
for point, glyph in base.getBestCmap().items():
    assert base.getGlyphID(glyph) == result.getGlyphID(result.getBestCmap()[point])
print(f'Preserved {len(base.getGlyphOrder())} original glyph outlines and advances; '
      f'packaged {len(result.getBestCmap())} Unicode mappings in {output.stat().st_size} bytes.')

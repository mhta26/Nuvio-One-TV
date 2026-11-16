"""Verify the actual shipped TTF cmap tables, using only the Python standard library."""
from pathlib import Path
import struct
import unittest
import unicodedata

ROOT = Path(__file__).resolve().parents[2]
FONTS = ROOT / 'app/src/main/res/font'


def unicode_map(path, glyph_ids=False):
    data = path.read_bytes()
    u16 = lambda offset: struct.unpack_from('>H', data, offset)[0]
    i16 = lambda offset: struct.unpack_from('>h', data, offset)[0]
    u32 = lambda offset: struct.unpack_from('>I', data, offset)[0]
    tables = {}
    for n in range(u16(4)):
        pos = 12 + n * 16
        tables[data[pos:pos + 4]] = u32(pos + 8)
    base = tables[b'cmap']
    points = {}
    for n in range(u16(base + 2)):
        record = base + 4 + n * 8
        platform, encoding = u16(record), u16(record + 2)
        if platform != 0 and not (platform == 3 and encoding in (1, 10)):
            continue
        table = base + u32(record + 4)
        form = u16(table)
        if form == 12:
            for group in range(u32(table + 12)):
                start, stop, glyph = struct.unpack_from('>III', data, table + 16 + group * 12)
                for point in range(start + (glyph == 0), stop + 1):
                    points[point] = glyph + point - start
        elif form == 4:
            count = u16(table + 6) // 2
            ends = table + 14
            starts = ends + count * 2 + 2
            deltas = starts + count * 2
            offsets = deltas + count * 2
            for segment in range(count):
                start, stop = u16(starts + segment * 2), u16(ends + segment * 2)
                delta, offset = i16(deltas + segment * 2), u16(offsets + segment * 2)
                for point in range(start, min(stop, 0xFFFE) + 1):
                    if offset:
                        glyph = u16(offsets + segment * 2 + offset + 2 * (point - start))
                        glyph = (glyph + delta) & 0xFFFF if glyph else 0
                    else:
                        glyph = (point + delta) & 0xFFFF
                    if glyph:
                        points[point] = glyph
    return points if glyph_ids else set(points)


class SubtitleFontCoverageTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.primary = unicode_map(FONTS / 'ibm_plex_sans_arabic_medium.ttf')
        cls.all_fonts = unicode_map(FONTS / 'nuvio_subtitle_arabic_medium.ttf')

    def covered(self, sample, points):
        missing = sorted({f'U+{ord(c):04X} {c}' for c in sample
                          if not c.isspace() and unicodedata.category(c) != 'Cf' and ord(c) not in points})
        self.assertEqual([], missing)

    def test_arabic_letters_and_extended_letters_use_ibm(self):
        self.covered('ءآأؤإئابتثجحخدذرزسشصضطظعغفقكلمنهويىةٱ پچژگک‌یےںۀہھٹڈڑ', self.primary)

    def test_all_standard_and_quranic_diacritics_use_ibm(self):
        self.covered(''.join(map(chr, range(0x640, 0x660))), self.primary)
        self.covered(''.join(map(chr, range(0x6D6, 0x6EE))), self.primary)

    def test_latin_digits_arabic_digits_and_persian_digits_use_ibm(self):
        self.covered('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzÀáéüçñ 0123456789٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹', self.primary)

    def test_quotes_brackets_parentheses_and_punctuation_use_ibm(self):
        self.covered('''()[]{}<> «»‹› “”—‘’"' :;!?.,،؛؟…–- /\\ @#%&*+_=|~`^٪٫٬ ؞''', self.primary)

    def test_musical_geometric_math_and_currency_symbols_have_fallback(self):
        self.covered('♪♫★♥☆♡✓☑□■●◯☀☁☎✆⚠→←↑↓ €£¥¢©®™°±×÷≠≤≥∞', self.all_fonts)

    def test_shipping_font_preserves_every_original_character_mapping(self):
        primary = unicode_map(FONTS / 'ibm_plex_sans_arabic_medium.ttf', glyph_ids=True)
        shipped = unicode_map(FONTS / 'nuvio_subtitle_arabic_medium.ttf', glyph_ids=True)
        self.assertGreaterEqual(len(shipped), 4515)
        self.assertEqual(primary, {cp: shipped[cp] for cp in primary})

    def test_original_ibm_file_is_unmodified(self):
        import hashlib
        expected = 'eef162792cf2a6ba5af7943af9f86843b1286ab67aaf615c18c07fd8e97a4a90'
        self.assertEqual(expected, hashlib.sha256((FONTS / 'ibm_plex_sans_arabic_medium.ttf').read_bytes()).hexdigest())


if __name__ == '__main__':
    unittest.main()

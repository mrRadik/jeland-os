"""Regression checks for preparing upstream Android resources, without downloads."""
import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("prepare_comaps", ROOT / "tools/prepare-comaps.py")
prepare_comaps = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare_comaps)


class HebrewPluralCompatibilityTest(unittest.TestCase):
    def test_pinned_upstream_missing_quantities_are_completed(self):
        source = ROOT / "third_party/comaps/android/app/src/main/res/values-iw/strings.xml"
        resources = ET.parse(source).getroot()
        missing = {
            p.get("name") for p in resources.findall("plurals")
            if p.find("item[@quantity='many']") is None
        }
        self.assertEqual(missing, {
            "bookmarks_places", "bookmarks_detect_message", "tracks", "minutes_short",
        })
        prepare_comaps.complete_hebrew_plurals(resources, "values-iw")
        for plural in resources.findall("plurals"):
            many = plural.find("item[@quantity='many']")
            other = plural.find("item[@quantity='other']")
            self.assertIsNotNone(many, plural.get("name"))
            self.assertEqual(many.text, other.text)

    def test_preserves_formatting_and_existing_translations(self):
        resources = ET.fromstring('''<resources>
          <plurals name="formatted"><item quantity="other">About <b>%d</b> places</item></plurals>
          <plurals name="translated"><item quantity="many">Existing many</item>
            <item quantity="other">Other</item></plurals>
        </resources>''')
        prepare_comaps.complete_hebrew_plurals(resources, "values-he-rIL")
        many = resources.find("plurals[@name='formatted']/item[@quantity='many']")
        self.assertEqual(many.find("b").text, "%d")
        self.assertEqual(many.find("b").tail, " places")
        self.assertEqual(resources.find("plurals[@name='translated']/item[@quantity='many']").text,
                         "Existing many")
        once = ET.tostring(resources)
        prepare_comaps.complete_hebrew_plurals(resources, "values-he-rIL")
        self.assertEqual(ET.tostring(resources), once)

    def test_leaves_other_languages_unchanged(self):
        resources = ET.fromstring('<resources><plurals name="minutes">'
                                 '<item quantity="other">%d minutes</item></plurals></resources>')
        original = ET.tostring(resources)
        for qualifier in ("values", "values-en", "values-ru", "values-hi"):
            prepare_comaps.complete_hebrew_plurals(resources, qualifier)
            self.assertEqual(ET.tostring(resources), original)


if __name__ == "__main__":
    unittest.main()

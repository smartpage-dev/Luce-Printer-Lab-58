#!/usr/bin/env python3
"""Focused XML/source contract for the observed NewApi build failure.

This is not Android lint, AAPT2, a complete API database, or a device renderer.
It checks the real XML files, then resolves this project's *simple explicit*
style inheritance for selected API levels. Negative controls reintroduce the
original bad placement and must be rejected. Stdlib only; no SDK needed.
"""
from pathlib import Path
import copy
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ATTRIBUTE = 'android:windowLightNavigationBar'
INTRODUCED = 27
COMMON = {
    'android:fontFamily': 'sans',
    'android:colorAccent': '#126E67',
    'android:windowLightStatusBar': 'true',
    'android:windowActionModeOverlay': 'true',
    'android:windowBackground': '#F0F5F4',
}


def load_documents(root: Path):
    """Parse every XML resource, retaining its source folder qualifier."""
    return [(str(p.relative_to(root)), ET.parse(p).getroot())
            for p in sorted((root / 'app/src/main/res').rglob('*.xml'))]


def folder_api(relative_path: str, minimum: int) -> int:
    qualifiers = Path(relative_path).parts[-2].split('-')
    versions = [int(q[1:]) for q in qualifiers if re.fullmatch(r'v[0-9]+', q)]
    return max([minimum] + versions)


def api_violations(documents, minimum: int):
    """Check only the attribute involved in this incident; not all Android APIs."""
    errors = []
    for name, root in documents:
        for item in root.iter('item'):
            if item.get('name') == ATTRIBUTE and folder_api(name, minimum) < INTRODUCED:
                errors.append((name, ATTRIBUTE, INTRODUCED))
    return errors


def resolve_style(documents, name: str, api: int, minimum: int = 26):
    """Resolve simple values[-vNN] style inheritance; not Android resource emulation."""
    definitions = {}
    for path, root in documents:
        folder = Path(path).parts[-2]
        if folder != 'values' and not re.fullmatch(r'values-v[0-9]+', folder):
            continue
        level = folder_api(path, minimum)
        if level > api:
            continue
        for style in root.findall('style'):
            key = style.get('name')
            if key not in definitions or level > definitions[key][0]:
                definitions[key] = (level, style)
    chain = set()
    def walk(key):
        if key in chain:
            raise ValueError('Cyclic style inheritance: ' + key)
        if key not in definitions:
            raise ValueError('Missing local style: ' + key)
        chain.add(key)
        style = definitions[key][1]
        parent = style.get('parent', '')
        if parent.startswith('@style/'):
            values = walk(parent[len('@style/'):])
        elif parent.startswith('android:style/'):
            values = {}  # Platform defaults intentionally not simulated.
        else:
            raise ValueError('Expected an explicit supported parent: ' + parent)
        values.update({item.get('name'): (item.text or '').strip() for item in style.findall('item')})
        chain.remove(key)
        return values
    return walk(name)


class ResourceCompatibilityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.docs = load_documents(ROOT)
        cls.gradle = (ROOT / 'app/build.gradle').read_text()

    def test_01_minimum_and_identity_unchanged(self):
        self.assertRegex(self.gradle, r'\bminSdk\s+26\b')
        self.assertRegex(self.gradle, r'\btargetSdk\s+35\b')
        self.assertRegex(self.gradle, r'\bcompileSdk\s+35\b')
        self.assertIn("applicationId 'hk.luce.printerlab'", self.gradle)

    def test_02_default_theme_is_complete_without_new_attribute(self):
        base = next(root for path, root in self.docs if path.endswith('/values/styles.xml'))
        common = base.find("style[@name='PrinterLabBaseTheme']")
        theme = base.find("style[@name='AppTheme']")
        self.assertIsNotNone(common)
        self.assertIsNotNone(theme)
        self.assertEqual(common.get('parent'), 'android:style/Theme.Material.Light.NoActionBar')
        self.assertEqual(theme.get('parent'), '@style/PrinterLabBaseTheme')
        self.assertEqual({i.get('name'): i.text for i in common.findall('item')}, COMMON)
        self.assertFalse(any(i.get('name') == ATTRIBUTE for i in base.iter('item')))

    def test_03_new_attribute_exists_only_in_v27(self):
        uses = [(path, i.text) for path, root in self.docs for i in root.iter('item')
                if i.get('name') == ATTRIBUTE]
        self.assertEqual(uses, [('app/src/main/res/values-v27/styles.xml', 'true')])
        self.assertEqual(api_violations(self.docs, 26), [])

    def test_04_api26_retains_common_theme_without_unsupported_attribute(self):
        self.assertEqual(resolve_style(self.docs, 'AppTheme', 26), COMMON)

    def test_05_api27_inherits_common_theme_and_enables_attribute(self):
        self.assertEqual(resolve_style(self.docs, 'AppTheme', 27), {**COMMON, ATTRIBUTE: 'true'})

    def test_06_newer_platforms_retain_all_explicit_properties(self):
        for api in (28, 31, 35, 37):
            with self.subTest(api=api):
                self.assertEqual(resolve_style(self.docs, 'AppTheme', api), {**COMMON, ATTRIBUTE: 'true'})

    def test_07_original_unqualified_placement_is_rejected(self):
        old = ET.fromstring('<resources><style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar">'
                            '<item name="android:windowLightNavigationBar">true</item></style></resources>')
        self.assertEqual(api_violations([('app/src/main/res/values/styles.xml', old)], 26),
                         [('app/src/main/res/values/styles.xml', ATTRIBUTE, 27)])

    def test_08_accidental_v26_move_is_rejected(self):
        mutated = [(p.replace('values-v27', 'values-v26'), root) for p, root in self.docs]
        self.assertEqual(api_violations(mutated, 26),
                         [('app/src/main/res/values-v26/styles.xml', ATTRIBUTE, 27)])

    def test_09_cycles_are_not_silently_accepted(self):
        mutated = copy.deepcopy(self.docs)
        for path, root in mutated:
            if path.endswith('/values/styles.xml'):
                root.find("style[@name='PrinterLabBaseTheme']").set('parent', '@style/AppTheme')
        with self.assertRaisesRegex(ValueError, 'Cyclic'):
            resolve_style(mutated, 'AppTheme', 26)

    def test_10_manifest_still_uses_default_app_theme(self):
        manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
        self.assertEqual(manifest.find('application').get('{http://schemas.android.com/apk/res/android}theme'),
                         '@style/AppTheme')

    def test_11_lint_gate_remains_enabled_without_suppression(self):
        self.assertRegex(self.gradle, r'\babortOnError\s+true\b')
        self.assertNotIn('lint-baseline', self.gradle)
        self.assertNotRegex(self.gradle, r'\b(?:disable|ignore)\b[^\n]*NewApi')
        self.assertFalse((ROOT / 'app/lint-baseline.xml').exists())
        for path, root in self.docs:
            for node in root.iter():
                self.assertNotIn('{http://schemas.android.com/tools}targetApi', node.attrib, path)
                self.assertNotIn('{http://schemas.android.com/tools}ignore', node.attrib, path)
        workflow = (ROOT / '.github/workflows/android-lab.yml').read_text()
        self.assertIn(':app:testDebugUnitTest :app:lintDebug :app:assembleDebug', workflow)
        self.assertNotIn('continue-on-error: true', workflow)

    def test_12_version_bump_and_ci_regression_hook(self):
        self.assertRegex(self.gradle, r'\bversionCode\s+4\b')
        self.assertIn("versionName '0.1.3-lab'", self.gradle)
        self.assertIn('python3 tools/test_android_resources.py', (ROOT / 'tools/run_core_tests.sh').read_text())


if __name__ == '__main__':
    print('Focused XML/source checks only: NOT an Android SDK build, lint run, or screen-rendering test.', flush=True)
    unittest.main(verbosity=2)

import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { COLOR_FILE, checkPairs, contrastRatio, loadColors } from '../check-contrast.mjs';

test('black_on_white_is_21', () => {
  assert.equal(contrastRatio('#000000', '#FFFFFF').toFixed(2), '21.00');
});

test('ratio_does_not_depend_on_argument_order', () => {
  assert.equal(contrastRatio('#E91E63', '#FFFFFF'), contrastRatio('#FFFFFF', '#E91E63'));
});

// OnSurface/Surface is not changed by U-75, so reading the real Color.kt here stays valid after it.
test('real_onsurface_on_surface_is_18_09', () => {
  const colors = loadColors(fs.readFileSync(COLOR_FILE, 'utf8'));
  assert.equal(contrastRatio(colors.OnSurface, colors.Surface).toFixed(2), '18.09');
});

// Old OnPrimary/Primary hex, written here on purpose: U-75 changes Color.kt, so this must not read it.
test('fixture_old_onprimary_on_primary_hex_is_4_35', () => {
  assert.equal(contrastRatio('#FFFFFF', '#E91E63').toFixed(2), '4.35');
});

test('failing_pair_without_known_flag_fails', () => {
  const colors = { OnSecondary: '#FFFFFF', Secondary: '#FF4081', OnSurface: '#1F0416', Surface: '#FFF5F8' };
  // 3.33 is under 4.5 (text) but passes the 3.0 bar for large text.
  assert.equal(checkPairs([{ fg: 'OnSecondary', bg: 'Secondary', kind: 'text' }], colors).failures, 1);
  assert.equal(checkPairs([{ fg: 'OnSecondary', bg: 'Secondary', kind: 'large' }], colors).failures, 0);
  assert.equal(checkPairs([{ fg: 'OnSurface', bg: 'Surface', kind: 'text' }], colors).failures, 0);
});

test('strict_fails_on_known_failing', () => {
  const colors = { OnSecondary: '#FFFFFF', Secondary: '#FF4081' };
  const pair = [{ fg: 'OnSecondary', bg: 'Secondary', kind: 'text', knownFailing: true, fixBy: 'U-75' }];
  assert.equal(checkPairs(pair, colors).failures, 0);
  assert.equal(checkPairs(pair, colors, { strict: true }).failures, 1);
});

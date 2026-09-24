import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { parseUtcTimestamp, calculateRemainingSeconds, formatCountdown } from '../src/utils/timer.js';

describe('Timer Utility Tests', () => {

  test('1. Parses UTC timestamp with Z suffix correctly', () => {
    const isoString = '2026-09-23T04:03:00.000Z';
    const parsed = parseUtcTimestamp(isoString);
    assert.equal(parsed, new Date(isoString).getTime());
    assert.equal(new Date(parsed).toISOString(), '2026-09-23T04:03:00.000Z');
  });

  test('2. Interprets legacy naive timestamp without Z as UTC (Defense-in-depth)', () => {
    const legacyWithoutZ = '2026-09-23T04:03:00';
    const parsed = parseUtcTimestamp(legacyWithoutZ);
    // Must match the exact UTC epoch ms of 2026-09-23T04:03:00Z
    assert.equal(parsed, new Date('2026-09-23T04:03:00Z').getTime());
    assert.equal(new Date(parsed).toISOString(), '2026-09-23T04:03:00.000Z');
  });

  test('3. Respects explicit timezone offset (+00:00, +05:30, -05:00)', () => {
    // Offset +00:00
    const utcOffset = '2026-09-23T04:03:00+00:00';
    assert.equal(parseUtcTimestamp(utcOffset), new Date('2026-09-23T04:03:00Z').getTime());

    // Offset +05:30 (09:33:00 IST is 04:03:00 UTC)
    const istOffset = '2026-09-23T09:33:00+05:30';
    assert.equal(parseUtcTimestamp(istOffset), new Date('2026-09-23T04:03:00Z').getTime());

    // Offset -05:00 (Sep 22, 23:03:00 EST is Sep 23, 04:03:00 UTC)
    const estOffset = '2026-09-22T23:03:00-05:00';
    assert.equal(parseUtcTimestamp(estOffset), new Date('2026-09-23T04:03:00Z').getTime());
  });

  test('4. 15-minute challenge started now shows ~15 minutes (900 seconds) remaining', () => {
    const now = Date.now();
    const startedAt = new Date(now).toISOString(); // e.g. "2026-09-23T04:03:00.123Z"
    const remaining = calculateRemainingSeconds(startedAt, 15, now);
    assert.equal(remaining, 900);
  });

  test('5. 15-minute challenge started now (without Z from server) still shows 900 seconds remaining', () => {
    const nowUtc = new Date(Date.now());
    // Strip trailing Z to simulate naive server serialization
    const naiveUtcString = nowUtc.toISOString().replace(/Z$/, '');
    const remaining = calculateRemainingSeconds(naiveUtcString, 15, nowUtc.getTime());
    assert.equal(remaining, 900);
  });

  test('6. Expired challenge returns 0 remaining seconds', () => {
    // Challenge started 20 minutes ago with a 15-minute limit
    const startedAt = new Date(Date.now() - 20 * 60 * 1000).toISOString();
    const remaining = calculateRemainingSeconds(startedAt, 15);
    assert.equal(remaining, 0);
  });

  test('7. Formats countdown seconds into MM:SS format correctly', () => {
    assert.equal(formatCountdown(900), '15:00');
    assert.equal(formatCountdown(59), '00:59');
    assert.equal(formatCountdown(5), '00:05');
    assert.equal(formatCountdown(0), '00:00');
    assert.equal(formatCountdown(null), '');
  });

  test('8. Handles null / undefined / empty inputs gracefully', () => {
    assert.equal(parseUtcTimestamp(null), 0);
    assert.equal(parseUtcTimestamp(''), 0);
    assert.equal(calculateRemainingSeconds(null, 15), null);
    assert.equal(calculateRemainingSeconds('2026-09-23T04:00:00Z', null), null);
  });

});

/**
 * Parses an incoming timestamp into absolute UTC epoch milliseconds.
 * 
 * Defense-in-depth against naive ISO strings:
 * - "2026-09-23T04:03:00" -> treated as UTC (appends 'Z' to avoid browser-local interpretation)
 * - "2026-09-23T04:03:00Z" -> parsed directly as UTC
 * - "2026-09-23T04:03:00+00:00" -> respects the explicit timezone offset
 * 
 * @param {string|number|Date} dateStr - Raw timestamp from server or WebSocket
 * @returns {number} Epoch milliseconds (UTC), or 0 if invalid
 */
export const parseUtcTimestamp = (dateStr) => {
  if (!dateStr) return 0;
  if (typeof dateStr === 'number') return dateStr;
  if (dateStr instanceof Date) return dateStr.getTime();

  const str = String(dateStr).trim();
  if (!str) return 0;

  // Check if string already contains a timezone indicator:
  // - Ends with 'Z' (case-insensitive)
  // - Ends with [+-]HH:mm, [+-]HHmm, or [+-]HH
  const hasTimezone = /[zZ]$|[+-]\d{2}(?::?\d{2})?$/.test(str);
  const normalized = hasTimezone ? str : `${str}Z`;

  const parsed = new Date(normalized).getTime();
  return isNaN(parsed) ? 0 : parsed;
};

/**
 * Calculates remaining seconds for an active coding challenge.
 * 
 * @param {string|number} startedAt - Challenge start timestamp
 * @param {number|string} timeLimitMinutes - Challenge time limit in minutes
 * @param {number} [now=Date.now()] - Current epoch milliseconds
 * @returns {number|null} Remaining seconds clamped to 0, or null if inputs invalid
 */
export const calculateRemainingSeconds = (startedAt, timeLimitMinutes, now = Date.now()) => {
  if (!startedAt || !timeLimitMinutes) return null;

  const startTime = parseUtcTimestamp(startedAt);
  if (!startTime) return null;

  const durationMs = Number(timeLimitMinutes) * 60 * 1000;
  const expiresAt = startTime + durationMs;
  const diffSec = Math.floor((expiresAt - now) / 1000);

  return Math.max(0, diffSec);
};

/**
 * Formats seconds into MM:SS display format.
 * 
 * @param {number|null} totalSeconds - Total remaining seconds
 * @returns {string} Formatted MM:SS string
 */
export const formatCountdown = (totalSeconds) => {
  if (totalSeconds === null || totalSeconds === undefined) return '';
  const mins = Math.floor(totalSeconds / 60);
  const secs = totalSeconds % 60;
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
};

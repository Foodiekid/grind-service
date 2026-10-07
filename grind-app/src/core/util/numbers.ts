/** Small number helpers shared by the formulas. Ported from the web app's `util.js` (golden tests). */

export function clamp(n: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, n));
}

/** A finite number from user input, or null for empty, missing or non-numeric input. */
export function toNum(value: unknown): number | null {
  if (value === '' || value == null) return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

export function median(values: readonly number[]): number | null {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  // The middle value, or the two middle values when the count is even.
  const middle = sorted.slice(sorted.length % 2 === 1 ? mid : mid - 1, mid + 1);
  return middle.reduce((a, b) => a + b, 0) / middle.length;
}

/** Trailing average over up to `window` values; the first values average what exists so far. */
export function movingAverage(values: readonly number[], window = 5): number[] {
  return values.map((_, i) => {
    const slice = values.slice(Math.max(0, i - window + 1), i + 1);
    return slice.reduce((a, b) => a + b, 0) / slice.length;
  });
}

// Reads values written by scripts/golden/encoding.mjs: {"$": "NaN"} back to NaN, and so on.

const SPECIAL: Record<string, unknown> = {
  undefined,
  NaN: Number.NaN,
  Infinity: Number.POSITIVE_INFINITY,
  '-Infinity': Number.NEGATIVE_INFINITY,
  '-0': -0,
};

export function decode(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(decode);
  if (value && typeof value === 'object') {
    const entries = Object.entries(value);
    if (entries.length === 1 && entries[0]?.[0] === '$' && typeof entries[0][1] === 'string' && entries[0][1] in SPECIAL) {
      return SPECIAL[entries[0][1]];
    }
    return Object.fromEntries(entries.map(([key, inner]) => [key, decode(inner)]));
  }
  return value;
}

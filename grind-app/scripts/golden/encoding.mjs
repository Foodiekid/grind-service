// JSON can't hold undefined, NaN, Infinity or -0, which are exactly the awkward values the golden cases need.
// They are written as {"$": "NaN"} and so on; tests/golden/decode.ts reads them back.

const SPECIAL = (value) => {
  if (value === undefined) return 'undefined';
  if (typeof value !== 'number') return null;
  if (Number.isNaN(value)) return 'NaN';
  if (value === Infinity) return 'Infinity';
  if (value === -Infinity) return '-Infinity';
  if (Object.is(value, -0)) return '-0';
  return null;
};

export function encode(value) {
  const special = SPECIAL(value);
  if (special) return { $: special };
  if (Array.isArray(value)) return Array.from(value, encode);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).map((key) => [key, encode(value[key])]));
  }
  return value;
}

/**
 * The message for a number field, or '' when the value is fine. The rule part of the web app's `validate.js`; showing
 * the message next to the field belongs to the screen components.
 */

export interface NumberInputRules {
  min?: number;
  max?: number;
  /** Only whole numbers when 1. */
  step?: number;
  /** Shown after the limit, e.g. `kg`. */
  unit?: string;
}

/** `+0.5` reads as `0.5`, `100` as `100`: limits print the way people write them. */
const format = (n: number): string => (Number.isInteger(n) ? String(n) : String(+n.toFixed(2)));

/**
 * @param value the raw text of the field
 * @param badInput true when the platform couldn't read the text as a number at all
 */
export function numberInputError(value: string, rules: NumberInputRules, badInput = false): string {
  if (badInput) return 'Enter a number.';
  if (value === '') return '';
  const n = Number(value);
  if (!Number.isFinite(n)) return 'Enter a number.';
  const unit = rules.unit ? ` ${rules.unit}` : '';
  if (rules.min !== undefined && n < rules.min) return `Too low — the minimum is ${format(rules.min)}${unit}.`;
  if (rules.max !== undefined && n > rules.max) return `Too high — the maximum is ${format(rules.max)}${unit}.`;
  if (rules.step === 1 && !Number.isInteger(n)) return 'Use a whole number.';
  return '';
}

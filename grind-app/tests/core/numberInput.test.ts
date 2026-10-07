// The number-field rules from the web app's validate.js, as plain inputs and messages.
import { describe, expect, it } from 'vitest';

import { numberInputError } from '@/core/validate/numberInput.ts';

describe('numberInputError', () => {
  const weight = { min: 0, max: 500, unit: 'kg' };

  it('accepts empty and in-range values', () => {
    expect(numberInputError('', weight)).toBe('');
    expect(numberInputError('82.5', weight)).toBe('');
    expect(numberInputError('500', weight)).toBe('');
  });

  it('names the limit and unit when a value is out of range', () => {
    expect(numberInputError('-1', weight)).toBe('Too low — the minimum is 0 kg.');
    expect(numberInputError('500.1', weight)).toBe('Too high — the maximum is 500 kg.');
    expect(numberInputError('0.1', { min: 0.25 })).toBe('Too low — the minimum is 0.25.');
  });

  it('asks for a number when the text is not one', () => {
    expect(numberInputError('', weight, true)).toBe('Enter a number.');
    expect(numberInputError('Infinity', weight)).toBe('Enter a number.');
  });

  it('asks for a whole number when the step is 1', () => {
    expect(numberInputError('8.5', { min: 0, step: 1 })).toBe('Use a whole number.');
    expect(numberInputError('8', { min: 0, step: 1 })).toBe('');
  });
});

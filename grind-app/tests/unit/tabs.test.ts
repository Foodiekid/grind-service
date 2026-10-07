import { describe, expect, it } from 'vitest';
import { DEFAULT_TAB, TABS, isTabId } from '@/app/tabs.ts';

describe('tabs', () => {
  it('has unique ids and the default is one of them', () => {
    const ids = TABS.map((t) => t.id);
    expect(new Set(ids).size).toBe(ids.length);
    expect(isTabId(DEFAULT_TAB)).toBe(true);
  });

  it('rejects anything that is not a tab id', () => {
    expect(isTabId('labs')).toBe(false);
    expect(isTabId(undefined)).toBe(false);
    expect(isTabId(42)).toBe(false);
  });
});

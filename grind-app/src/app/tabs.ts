// Bottom tabs. PLACEHOLDER set until the tab bar is decided with mockups; Trends, Labs and Profile sit under More.
export const TABS = [
  { id: 'today', label: 'Today' },
  { id: 'train', label: 'Train' },
  { id: 'routes', label: 'Routes' },
  { id: 'fuel', label: 'Fuel' },
  { id: 'journal', label: 'Journal' },
  { id: 'more', label: 'More' },
] as const;

export type TabId = (typeof TABS)[number]['id'];

export const DEFAULT_TAB: TabId = 'today';

export const isTabId = (v: unknown): v is TabId => TABS.some((t) => t.id === v);

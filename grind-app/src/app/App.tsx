import { useState } from 'react';
import { DEFAULT_TAB, TABS, type TabId } from './tabs.ts';

// Step 1b shell: proves the build, the native projects and the tab layout boot. No data, no network.
// Real screens arrive in step 5, after the step-4 mockups.
export function App() {
  const [tab, setTab] = useState<TabId>(DEFAULT_TAB);
  const current = TABS.find((t) => t.id === tab) ?? TABS[0];

  return (
    <div className="shell">
      <main className="screen" aria-labelledby="screen-title">
        <h1 id="screen-title" className="screen-title">{current.label}</h1>
        <p className="screen-note">Coming in step 5.</p>
      </main>

      <nav className="tabbar" aria-label="Main">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            className="tab"
            aria-current={t.id === tab ? 'page' : undefined}
            onClick={() => {
              setTab(t.id);
            }}
          >
            {t.label}
          </button>
        ))}
      </nav>
    </div>
  );
}

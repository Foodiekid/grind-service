// Writes docs/MUTATION-LOG.md: how mutation testing runs, one row per run (newest first), the open survivors and the
// accepted equivalent mutants. The full report with causes and fixes is reports/mutation/runs.html (html.mjs).
import { writeFileSync } from 'node:fs';

import { findResolution, RESOLUTIONS } from './results.mjs';

const LOG = '../docs/MUTATION-LOG.md';

const cell = (text) => String(text ?? '').replaceAll('\n', ' ').replaceAll('|', '\\|').slice(0, 120);
const percent = (value) => (value === null ? '-' : `${value.toFixed(2)}%`);

/** What a run tested, in a few words: "2 tested, 1,097 reused; 5 open survivors retested". */
export function describeSteps(steps) {
  const count = (value) => (value === null ? '?' : value.toLocaleString('en'));
  return steps.map((step) => {
    if (step.what === 'open survivors') return `${step.survivorsRetested} open survivors retested`;
    if (step.what === 'all mutants') return `all ${count(step.tested)} tested`;
    return `${count(step.tested)} tested, ${count(step.reused)} reused`;
  }).join('; ');
}

/** Survivors of a run that were open (not accepted as equivalent) by today's resolutions. */
export function openSurvivors(run, resolutions) {
  return run.survivors.filter((s) => findResolution(resolutions, s.file, s.mutator, s.code)?.kind !== 'equivalent');
}

export function writeLog(runs, summary, resolutions) {
  const runRows = [...runs].reverse().map((run) => {
    const date = run.date.slice(0, 16).replace('T', ' ');
    const open = openSurvivors(run, resolutions).length;
    return `| ${date} UTC | ${run.mode} | ${run.status} | ${describeSteps(run.steps)} | ${percent(run.score)} | ${open} | ${run.survivors.length - open} | ${run.breakAt ?? '-'} |`;
  });
  const openSection = summary.open.length === 0
    ? 'None.'
    : ['| File | Line | Mutator | Mutated to |', '|---|---|---|---|',
      ...summary.open.map((s) => `| \`${s.file}\` | ${s.line} | ${s.mutator} | \`${cell(s.replacement)}\` |`)].join('\n');
  const acceptedSection = ['| File | Line | Mutator | Why it can\'t change a result |', '|---|---|---|---|',
    ...summary.accepted.map((s) => `| \`${s.file}\` | ${s.line} | ${s.mutator} | ${cell(s.resolution.cause)} |`)].join('\n');
  const staleSection = summary.staleEquivalents.length === 0
    ? ''
    : `\n\n**Stale equivalent entries** (they match no survivor; remove them from \`grind-app/${RESOLUTIONS}\`):\n\n${summary.staleEquivalents.map((entry) => `- \`${entry.file}\`: \`${entry.code}\``).join('\n')}`;

  writeFileSync(LOG, `# Mutation testing log

Stryker plants small bugs (mutants) in \`grind-app/src/core\` and checks that a test fails for each one. Written by
\`grind-app/scripts/mutation/run.mjs\` after every run from \`docs/mutation/history.json\`; never edit it by hand. The
full report (what each run tested, every failure with its cause, fix and status now) is
\`grind-app/reports/mutation/runs.html\`.

- **Daily / local (\`npm run mutation\`):** retests the mutants in source changed since the last run, then every
  open survivor (Stryker can't see test changes by itself). Everything else comes from the cache
  (\`grind-app/reports/stryker-incremental.json\`).
- **Weekly (\`npm run mutation:full\`):** retests everything and refreshes the cache.
- **Score** = mutants a test caught / all mutants; the build breaks below the \`break\` threshold in
  \`grind-app/stryker.config.json\`. Every open survivor gets a test, a code fix, or (only if it can't change any
  result) an equivalent entry; each is recorded with its cause and fix in \`grind-app/${RESOLUTIONS}\`.

## Runs (newest first)

| Date | Mode | Result | What ran | Score | Open | Accepted | Break at |
|---|---|---|---|---|---|---|---|
${runRows.join('\n')}

## Open survivors (latest run)

${openSection}

## Accepted equivalent mutants

${acceptedSection}${staleSection}
`);
}

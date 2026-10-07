// Writes reports/mutation/runs.html: one self-contained page with every mutation-testing run (date, mode, what was
// tested and reused, result), every mutant that ever survived with its cause, the fix applied and its status in the
// latest run, and the open survivors still to fix. Stryker's own page (index.html, same folder) shows single mutants.
import { writeFileSync } from 'node:fs';

import { statusNow } from './history.mjs';
import { describeSteps } from './log.mjs';
import { findResolution } from './results.mjs';

const PAGE = 'reports/mutation/runs.html';

/** Why a test suite usually misses each kind of mutant: the default cause until a resolution says more. */
const MUTATOR_CAUSE = {
  ArithmeticOperator: 'An arithmetic operator was swapped (+ and -, * and /), and no test checks the computed number.',
  ArrayDeclaration: 'A list was emptied or filled with junk, and no test notices the difference.',
  ArrowFunction: 'A function was replaced by one returning nothing, and no test notices.',
  BlockStatement: 'A block of code was emptied, and no test notices it is missing.',
  BooleanLiteral: 'true and false were swapped, and no test checks that outcome.',
  CallExpression: 'A call was removed, and no test checks its effect.',
  ConditionalExpression: 'A condition was forced to always true or always false, and no test checks both outcomes.',
  EqualityOperator: 'A comparison was changed (for example >= to >), and no test uses the exact boundary value.',
  LogicalOperator: '&& and || were swapped, and no test covers the combination where they differ.',
  MethodExpression: 'A method call was changed or removed (for example slice or trim), and no test checks its effect.',
  ObjectLiteral: 'An object was emptied, and no test checks its fields.',
  OptionalChaining: 'Optional chaining (?.) was removed, and no test passes a missing value there.',
  Regex: 'A regular expression was loosened (an anchor or class changed), and no test sends text it should refuse.',
  StringLiteral: 'A text value was changed or emptied, and no test checks it.',
  UnaryOperator: 'A sign or negation was flipped, and no test notices.',
  UpdateOperator: '++ and -- were swapped, and no test notices.',
};

const STATUS_LABEL = { killed: 'Fixed: a test catches it', accepted: 'Accepted (equivalent)', open: 'Open: needs a fix', gone: 'Code changed or removed' };
const KIND_LABEL = { test: 'New test', code: 'Code change', equivalent: 'Equivalent mutant' };

const escape = (text) => String(text ?? '')
  .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
const percent = (value) => (value === null || value === undefined ? '-' : `${value.toFixed(2)}%`);
const when = (iso) => `${iso.slice(0, 10)} ${iso.slice(11, 16)} UTC`;
const badge = (kind, text) => `<span class="badge ${kind}">${escape(text)}</span>`;

/** Every mutant that survived in any run, once, with the first and last run it survived in. */
function everyFailure(runs) {
  const failures = new Map();
  for (const run of runs) {
    for (const survivor of run.survivors) {
      const known = failures.get(survivor.key);
      if (known === undefined) failures.set(survivor.key, { ...survivor, firstSeen: run.date, lastSeen: run.date, runs: 1 });
      else Object.assign(known, { lastSeen: run.date, runs: known.runs + 1 });
    }
  }
  return [...failures.values()];
}

function failureRow(failure, summary, resolutions, { withDates }) {
  const resolution = findResolution(resolutions, failure.file, failure.mutator, failure.code);
  const status = statusNow(failure, summary, resolutions);
  const cause = resolution?.cause ?? MUTATOR_CAUSE[failure.mutator] ?? 'No test fails when this code changes.';
  const fix = resolution === undefined
    ? '<span class="muted">Not recorded yet</span>'
    : `${badge('kind', KIND_LABEL[resolution.kind] ?? resolution.kind)} ${escape(resolution.fix)}${resolution.date ? ` <span class="muted">(${escape(resolution.date)})</span>` : ''}`;
  const dates = withDates
    ? `<td class="nowrap" data-label="First failed">${escape(when(failure.firstSeen))}<br><span class="muted">${failure.runs} run${failure.runs === 1 ? '' : 's'}</span></td>`
    : '';
  return `<tr>
  ${dates}<td data-label="Where"><code>${escape(failure.file.replace('src/core/', ''))}:${failure.line}</code><br><span class="muted">${escape(failure.mutator)}</span></td>
  <td class="change" data-label="Change"><div class="from"><code>${escape(failure.original)}</code></div><div class="to"><code>${escape(failure.replacement)}</code></div></td>
  <td data-label="Cause">${escape(cause)}</td>
  <td data-label="Fix applied">${fix}</td>
  <td data-label="Status now">${badge(status, STATUS_LABEL[status])}</td>
</tr>`;
}

function failureTable(failures, summary, resolutions, { withDates }) {
  if (failures.length === 0) return '<p class="muted">No surviving mutants.</p>';
  const head = `${withDates ? '<th>First failed</th>' : ''}<th>Where</th><th>Change (original, then the mutant)</th><th>Cause</th><th>Fix applied</th><th>Status now</th>`;
  return `<div class="scroll"><table><thead><tr>${head}</tr></thead><tbody>
${failures.map((failure) => failureRow(failure, summary, resolutions, { withDates })).join('\n')}
</tbody></table></div>`;
}

/** A small line chart of the score per run, oldest to newest, with the break threshold as a dashed line. */
function scoreChart(runs) {
  const width = 640;
  const height = 120;
  const pad = 8;
  const scores = runs.map((run) => run.score ?? 0);
  const low = Math.min(90, ...scores);
  const y = (score) => pad + (1 - (score - low) / (100 - low)) * (height - 2 * pad);
  const x = (index) => (runs.length === 1 ? width / 2 : pad + (index / (runs.length - 1)) * (width - 2 * pad));
  const points = scores.map((score, index) => `${x(index).toFixed(1)},${y(score).toFixed(1)}`).join(' ');
  const breakAt = runs.at(-1)?.breakAt;
  const breakLine = breakAt == null ? '' : `<line x1="0" x2="${width}" y1="${y(breakAt)}" y2="${y(breakAt)}" class="break"/><text x="${width - 4}" y="${y(breakAt) - 4}" text-anchor="end" class="axis">break ${breakAt}%</text>`;
  const dots = scores.map((score, index) => `<circle cx="${x(index)}" cy="${y(score)}" r="3.5"><title>${escape(when(runs[index].date))}: ${percent(score)}</title></circle>`).join('');
  return `<svg viewBox="0 0 ${width} ${height}" role="img" aria-label="Mutation score per run">${breakLine}<polyline points="${points}"/>${dots}<text x="4" y="${y(100) + 10}" class="axis">100%</text><text x="4" y="${height - 2}" class="axis">${low}%</text></svg>`;
}

function runCard(run, index, summary, resolutions) {
  const changed = run.changedFiles.length === 0
    ? ''
    : `<p><strong>Changed code retested:</strong> ${run.changedFiles.map((entry) => `<code>${escape(entry.file.replace('src/core/', ''))}</code>${entry.count == null ? '' : ` (${entry.count})`}`).join(', ')}</p>`;
  const survivors = run.survivors.map((survivor) => ({ ...survivor, firstSeen: run.date, runs: 1 }));
  return `<details class="run"${index === 0 ? ' open' : ''}>
<summary>
  <span class="nowrap">${escape(when(run.date))}</span>
  ${badge(run.mode === 'full' ? 'full' : 'changes', run.mode === 'full' ? 'Full run' : 'Changes')}
  ${badge(run.status === 'passed' ? 'killed' : 'open', run.status === 'passed' ? 'Passed' : 'Failed')}
  <span class="score">${percent(run.score)}</span>
  <span class="muted">${run.survivors.length} survived</span>
</summary>
<div class="body">
  <p><strong>What ran:</strong> ${escape(describeSteps(run.steps))}.</p>
  ${changed}
  <p><strong>Result:</strong> ${run.counts.killed} killed, ${run.counts.timeout} timed out, ${run.counts.survived} survived${run.counts.noCoverage > 0 ? `, ${run.counts.noCoverage} without coverage` : ''}; score ${percent(run.score)} (${percent(run.scoreWithAccepted)} counting accepted), break at ${run.breakAt ?? '-'}.</p>
  ${run.note ? `<p class="note">${escape(run.note)}</p>` : ''}
  ${failureTable(survivors, summary, resolutions, { withDates: false })}
</div>
</details>`;
}

export function writeHtmlReport(runs, summary, resolutions) {
  const latest = runs.at(-1);
  const failures = everyFailure(runs);
  const statusCount = (status) => failures.filter((failure) => statusNow(failure, summary, resolutions) === status).length;
  const generated = new Date().toISOString();

  const page = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Mutation Test Runs</title>
<style>
:root { --bg: #f7f7f8; --card: #fff; --text: #1d1d1f; --muted: #6e6e73; --line: #e3e3e8; --accent: #0a84ff;
  --good: #1f8a3b; --good-bg: #e6f4ea; --bad: #c62828; --bad-bg: #fdecea; --warn: #8a5a00; --warn-bg: #fff4dc;
  --info: #3d4a9e; --info-bg: #eceefb; }
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) { --bg: #111113; --card: #1c1c1f; --text: #f2f2f4;
  --muted: #9a9aa2; --line: #2e2e33; --accent: #4da3ff; --good: #6fd38a; --good-bg: #173322; --bad: #ff8a80;
  --bad-bg: #3a1a1a; --warn: #ffcc66; --warn-bg: #3a2e12; --info: #aab4ff; --info-bg: #23284a; } }
:root[data-theme="dark"] { --bg: #111113; --card: #1c1c1f; --text: #f2f2f4; --muted: #9a9aa2; --line: #2e2e33;
  --accent: #4da3ff; --good: #6fd38a; --good-bg: #173322; --bad: #ff8a80; --bad-bg: #3a1a1a; --warn: #ffcc66;
  --warn-bg: #3a2e12; --info: #aab4ff; --info-bg: #23284a; }
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--text); font: 15px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
main { max-width: 1180px; margin: 0 auto; padding: 24px 16px 48px; }
h1 { font-size: 26px; margin: 0 0 4px; } h2 { font-size: 19px; margin: 32px 0 12px; }
a { color: var(--accent); } .muted { color: var(--muted); } .nowrap { white-space: nowrap; }
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; margin-top: 20px; }
.card { background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: 14px 16px; }
.card .value { font-size: 26px; font-weight: 650; } .card .label { color: var(--muted); font-size: 13px; }
.chart { background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: 12px; margin-top: 12px; }
svg { width: 100%; height: auto; display: block; }
svg polyline { fill: none; stroke: var(--accent); stroke-width: 2; } svg circle { fill: var(--accent); }
svg .break { stroke: var(--bad); stroke-dasharray: 5 4; } svg .axis { fill: var(--muted); font-size: 11px; }
.scroll { overflow-x: auto; background: var(--card); border: 1px solid var(--line); border-radius: 12px; }
table { border-collapse: collapse; width: 100%; min-width: 860px; font-size: 13.5px; }
th, td { text-align: left; vertical-align: top; padding: 10px 12px; border-bottom: 1px solid var(--line); }
th { font-size: 12px; text-transform: uppercase; letter-spacing: .04em; color: var(--muted); font-weight: 600; }
tr:last-child td { border-bottom: 0; }
code { font: 12.5px/1.4 ui-monospace, SFMono-Regular, Menlo, monospace; word-break: break-word; }
.change { max-width: 340px; } .change div { padding: 3px 6px; border-radius: 6px; margin-bottom: 4px; }
.change .from { background: var(--bg); } .change .to { background: var(--bad-bg); }
.badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; font-weight: 600; white-space: nowrap; }
.badge.killed { color: var(--good); background: var(--good-bg); } .badge.open { color: var(--bad); background: var(--bad-bg); }
.badge.accepted, .badge.gone { color: var(--warn); background: var(--warn-bg); }
.badge.kind, .badge.changes { color: var(--info); background: var(--info-bg); } .badge.full { color: var(--text); background: var(--line); }
details.run { background: var(--card); border: 1px solid var(--line); border-radius: 12px; margin-bottom: 10px; }
details.run summary { cursor: pointer; padding: 12px 16px; display: flex; flex-wrap: wrap; gap: 8px 12px; align-items: center; }
details.run .score { font-weight: 650; } details.run .body { padding: 0 16px 16px; }
details.run .scroll { border-radius: 8px; }
@media (max-width: 720px) {
  table { min-width: 0; } thead { display: none; }
  table, tbody, tr, td { display: block; width: 100%; } tr { padding: 10px 0; border-bottom: 1px solid var(--line); }
  td { border: 0; padding: 4px 14px; } .change { max-width: none; }
  td::before { content: attr(data-label); display: block; font-size: 11px; text-transform: uppercase; letter-spacing: .04em; color: var(--muted); }
} .note { background: var(--info-bg); color: var(--info); padding: 8px 12px; border-radius: 8px; }
</style>
</head>
<body>
<main>
<h1>Mutation test runs</h1>
<p class="muted">GRIND app business logic (<code>grind-app/src/core</code>). Generated ${escape(when(generated))} from <code>docs/mutation/history.json</code>; causes and fixes from <code>grind-app/scripts/mutation/resolutions.json</code>. Single mutants: Stryker's report <a href="index.html">index.html</a>.</p>

<div class="cards">
  <div class="card"><div class="value">${percent(latest.score)}</div><div class="label">Latest score (break at ${latest.breakAt ?? '-'})</div></div>
  <div class="card"><div class="value">${badge(latest.status === 'passed' ? 'killed' : 'open', latest.status === 'passed' ? 'Passed' : 'Failed')}</div><div class="label">Latest run, ${escape(when(latest.date))}</div></div>
  <div class="card"><div class="value">${summary.open.length}</div><div class="label">Open survivors now</div></div>
  <div class="card"><div class="value">${failures.length}</div><div class="label">Mutants that ever survived</div></div>
  <div class="card"><div class="value">${statusCount('killed') + statusCount('gone')}</div><div class="label">Fixed (test or code change)</div></div>
  <div class="card"><div class="value">${statusCount('accepted')}</div><div class="label">Accepted as equivalent</div></div>
  <div class="card"><div class="value">${runs.length}</div><div class="label">Runs recorded</div></div>
</div>

<h2>Score per run</h2>
<div class="chart">${scoreChart(runs)}</div>

<h2>Open survivors now</h2>
${failureTable(summary.open.map((open) => failures.find((failure) => failure.key === open.key) ?? { ...open, firstSeen: latest.date, runs: 1 }), summary, resolutions, { withDates: true })}

<h2>Every failure so far</h2>
<p class="muted">Each mutant that survived in any run, once: what changed, why no test caught it, the fix applied, and its status in the latest run.</p>
${failureTable(failures, summary, resolutions, { withDates: true })}

<h2>Runs (newest first)</h2>
${[...runs].reverse().map((run, index) => runCard(run, index, summary, resolutions)).join('\n')}
</main>
</body>
</html>
`;
  writeFileSync(PAGE, page);
  return `grind-app/${PAGE}`;
}

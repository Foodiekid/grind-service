/** A lab panel: biomarker values with their qualifiers and source details. From `backup.js` (`sanitizeLab`). */

import { isValidDateStr, type DateStr } from '../util/dates.ts';
import { createdAt, field, idOk, inRange, isObject, str, textOr } from './fields.ts';
import { LAB_RANGE, isRecordSource, type RecordSource } from './ranges.ts';

export type LabQualifier = '<' | '>' | '≤' | '≥';
const QUALIFIERS: readonly string[] = ['<', '>', '≤', '≥'];

export interface LabValueDetail {
  /** The value as printed on the report. */
  raw: string;
  unit: string;
  /** Evidence: the line the value was read from. */
  ev: string;
  edited: boolean;
}

export interface LabPanel {
  id: string;
  date: DateStr;
  labName: string;
  biomarkers: Record<string, number>;
  createdAt: string;
  source?: RecordSource;
  qualifiers?: Record<string, LabQualifier>;
  details?: Record<string, LabValueDetail>;
  reportedDate?: DateStr;
}

/** @param knownKeys the biomarkers GRIND knows; any other key is dropped */
export function sanitizeLab(raw: unknown, knownKeys: readonly string[], now: Date): LabPanel | null {
  if (!isObject(raw) || !idOk(raw.id) || typeof raw.date !== 'string' || !isValidDateStr(raw.date)) return null;
  const biomarkers: Record<string, number> = {};
  for (const key of knownKeys) {
    const rawValue = field(raw.biomarkers, key);
    const value = rawValue == null ? null : Number(rawValue);
    if (inRange(value, LAB_RANGE)) biomarkers[key] = value;
  }
  const kept = Object.keys(biomarkers);
  if (kept.length === 0) return null;
  const out: LabPanel = {
    id: raw.id,
    date: raw.date,
    labName: textOr(str(raw.labName, 80), 'Lab panel'),
    biomarkers,
    createdAt: createdAt(raw.createdAt, now),
  };
  if (isRecordSource(raw.source)) out.source = raw.source;
  if (isObject(raw.qualifiers)) {
    const qualifiers: Record<string, LabQualifier> = {};
    for (const key of kept) {
      const qualifier = raw.qualifiers[key];
      if (typeof qualifier === 'string' && QUALIFIERS.includes(qualifier)) qualifiers[key] = qualifier as LabQualifier;
    }
    if (Object.keys(qualifiers).length > 0) out.qualifiers = qualifiers;
  }
  if (isObject(raw.details)) {
    const details: Record<string, LabValueDetail> = {};
    for (const key of kept) {
      const detail = raw.details[key];
      if (isObject(detail)) {
        details[key] = {
          raw: str(detail.raw, 12),
          unit: str(detail.unit, 16),
          ev: str(detail.ev, 160),
          edited: detail.edited === true,
        };
      }
    }
    if (Object.keys(details).length > 0) out.details = details;
  }
  if (typeof raw.reportedDate === 'string' && isValidDateStr(raw.reportedDate)) out.reportedDate = raw.reportedDate;
  return out;
}

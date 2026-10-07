/** The user's profile: display name, goal and avatar. From `backup.js` (`sanitizeProfile`). */

import { isObject, str, textOr } from './fields.ts';

export interface Profile {
  key: 'current_user';
  name: string;
  goal: string;
  /** A small inline image (WebP, PNG or JPEG); never a link, so showing it fetches nothing. */
  avatarUrl: string | null;
}

const AVATAR = /^data:image\/(webp|png|jpeg);base64,[A-Za-z0-9+/]+={0,2}$/;

export function sanitizeProfile(raw: unknown): Profile | null {
  if (!isObject(raw)) return null;
  const avatar = raw.avatarUrl;
  return {
    key: 'current_user',
    name: textOr(str(raw.name, 60).trim(), 'Athlete'),
    goal: str(raw.goal, 160).trim(),
    avatarUrl: typeof avatar === 'string' && avatar.length < 600000 && AVATAR.test(avatar) ? avatar : null,
  };
}

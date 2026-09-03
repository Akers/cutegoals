/**
 * CSRF token resolution, ported from web/packages/shared/src/api/client.ts.
 *
 * The backend issues a non-HttpOnly `csrf_token` cookie at login and also
 * allows deployment setups to embed the token in a meta tag:
 *   <meta name="csrf-token" content="..." />
 */

export const CSRF_HEADER_NAME = 'X-CSRF-TOKEN';

const CSRF_META_SELECTOR = 'meta[name="csrf-token"]';
const CSRF_COOKIE_NAME = 'csrf_token';

function readCookie(name: string): string | null {
  if (typeof document === 'undefined') return null;
  const match = document.cookie.match(new RegExp(`(?:^|;\\s*)${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * Resolve the CSRF token from the meta tag first, then the csrf_token cookie.
 * Returns an empty string when unavailable (safe to skip the header).
 */
export function getCsrfToken(): string {
  if (typeof document === 'undefined') return '';
  const meta = document.querySelector(CSRF_META_SELECTOR);
  const fromMeta = meta?.getAttribute('content');
  if (fromMeta) return fromMeta.trim();
  return readCookie(CSRF_COOKIE_NAME) ?? '';
}

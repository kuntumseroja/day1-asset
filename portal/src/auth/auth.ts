import type { UserProfile } from '../api/types';

const KEYCLOAK_URL = 'http://localhost:8180';
const REALM = 'detp';
const CLIENT_ID = 'portal';

const DEV_USERS: Record<string, UserProfile> = {
  bankA: {
    participantId: 'BANK-A',
    role: 'MAKER',
    tier: 'TIER_1',
    displayName: 'Bank A Pilot',
  },
  bankB: {
    participantId: 'BANK-B',
    role: 'MAKER',
    tier: 'TIER_2',
    displayName: 'Bank B Pilot',
  },
  operator: {
    participantId: 'BI-OMNIBUS',
    role: 'OPERATOR',
    tier: 'TIER_1',
    displayName: 'BI Operator',
  },
};

function encodeDevToken(profile: UserProfile): string {
  return btoa(JSON.stringify(profile));
}

export function devLogin(userKey: keyof typeof DEV_USERS = 'bankA'): UserProfile {
  const profile = DEV_USERS[userKey];
  sessionStorage.setItem('detp_token', encodeDevToken(profile));
  sessionStorage.setItem('detp_user', JSON.stringify(profile));
  return profile;
}

export function logout(): void {
  sessionStorage.removeItem('detp_token');
  sessionStorage.removeItem('detp_user');
}

export function getStoredUser(): UserProfile | null {
  const raw = sessionStorage.getItem('detp_user');
  return raw ? (JSON.parse(raw) as UserProfile) : null;
}

export function isAuthenticated(): boolean {
  return !!sessionStorage.getItem('detp_token');
}

/** Keycloak OIDC redirect stub — not wired in dev; use devLogin instead. */
export function keycloakLoginRedirect(): void {
  const redirectUri = encodeURIComponent(window.location.origin + '/login/callback');
  window.location.href =
    `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/auth` +
    `?client_id=${CLIENT_ID}&redirect_uri=${redirectUri}&response_type=code&scope=openid`;
}

export { DEV_USERS };

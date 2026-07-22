/**
 * Runtime config from Vite env vars (set at build time).
 * Production builds default to same-origin paths (nginx on AWS).
 * Local dev defaults match docker-compose port mappings.
 */
function stripTrailingSlash(url: string): string {
  return url.replace(/\/$/, '');
}

function defaultApiBase(): string {
  if (import.meta.env.PROD) {
    return '/api/v1';
  }
  return 'http://localhost:8093/api/v1';
}

function defaultReconBase(): string {
  if (import.meta.env.PROD) {
    return '/recon/api/v1';
  }
  return 'http://localhost:8085/api/v1';
}

function defaultWsUrl(): string {
  if (import.meta.env.PROD && typeof window !== 'undefined') {
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${proto}//${window.location.host}/ws`;
  }
  return 'ws://localhost:8093/api/v1/ws';
}

const apiBase = import.meta.env.VITE_API_BASE ?? defaultApiBase();
const reconBase = import.meta.env.VITE_RECON_BASE ?? defaultReconBase();
const wsUrl = import.meta.env.VITE_WS_URL ?? defaultWsUrl();

export const env = {
  apiBase: stripTrailingSlash(apiBase),
  reconBase: stripTrailingSlash(reconBase),
  wsUrl,
} as const;

/**
 * Runtime config from Vite env vars (set at build time).
 * Local dev defaults match docker-compose port mappings.
 */
const apiBase = import.meta.env.VITE_API_BASE ?? 'http://localhost:8093/api/v1';
const reconBase = import.meta.env.VITE_RECON_BASE ?? 'http://localhost:8085/api/v1';
const wsUrl = import.meta.env.VITE_WS_URL ?? 'ws://localhost:8093/api/v1/ws';

export const env = {
  apiBase: apiBase.replace(/\/$/, ''),
  reconBase: reconBase.replace(/\/$/, ''),
  wsUrl,
} as const;

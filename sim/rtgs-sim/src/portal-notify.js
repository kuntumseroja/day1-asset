const PORTAL_SIM_URL = (process.env.PORTAL_SIM_URL || 'http://localhost:8093').replace(/\/$/, '');

export async function notifyPortal(payload) {
  try {
    const res = await fetch(`${PORTAL_SIM_URL}/api/v1/settlement/ingest`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      console.warn(`portal ingest HTTP ${res.status}`);
    }
  } catch (e) {
    console.warn('portal ingest failed:', e.message);
  }
}

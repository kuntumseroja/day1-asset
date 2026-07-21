import { useCallback, useEffect, useRef, useState } from 'react';
import { WS_URL } from '../api/client';
import type { SettlementEvent } from '../api/types';

export function useSettlementEvents(onEvent?: (event: SettlementEvent) => void) {
  const [lastEvent, setLastEvent] = useState<SettlementEvent | null>(null);
  const [connected, setConnected] = useState(false);
  const handlerRef = useRef(onEvent);
  handlerRef.current = onEvent;

  const connect = useCallback(() => {
    const ws = new WebSocket(WS_URL);

    ws.onopen = () => setConnected(true);
    ws.onclose = () => {
      setConnected(false);
      setTimeout(connect, 3000);
    };
    ws.onerror = () => ws.close();
    ws.onmessage = (msg) => {
      try {
        const event = JSON.parse(msg.data as string) as SettlementEvent;
        setLastEvent(event);
        handlerRef.current?.(event);
      } catch {
        /* ignore malformed payloads */
      }
    };

    return ws;
  }, []);

  useEffect(() => {
    const ws = connect();
    return () => ws.close();
  }, [connect]);

  return { lastEvent, connected };
}

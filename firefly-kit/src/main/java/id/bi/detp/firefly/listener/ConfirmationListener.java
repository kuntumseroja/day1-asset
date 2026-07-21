package id.bi.detp.firefly.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.bi.detp.firefly.client.FireflyMintClient;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
public class ConfirmationListener {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationListener.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> seenEventIds = ConcurrentHashMap.newKeySet();
    private WebSocketClient client;
    private Consumer<JsonNode> handler;
    private volatile boolean connected;

    @Value("${firefly.ws-url:ws://localhost:8092/ws}")
    private String wsUrl;

    @Value("${firefly.listener-id:default}")
    private String listenerId;

    public ConfirmationListener(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void onConfirmation(Consumer<JsonNode> handler) {
        this.handler = handler;
    }

    public long getLastOffset() {
        Long offset = jdbc.queryForObject(
                "SELECT last_offset FROM detp.listener_offsets WHERE listener_id = ?",
                Long.class, listenerId);
        return offset != null ? offset : 0L;
    }

    private void saveOffset(long offset) {
        jdbc.update("""
            INSERT INTO detp.listener_offsets (listener_id, topic, last_offset, updated_at)
            VALUES (?, 'token_mint_confirmed', ?, NOW())
            ON CONFLICT (listener_id) DO UPDATE SET last_offset = ?, updated_at = NOW()
            """, listenerId, offset, offset);
    }

    public void connect() {
        long resumeFrom = getLastOffset();
        URI uri = URI.create(wsUrl + "?offset=" + resumeFrom);
        client = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                connected = true;
                log.info("ConfirmationListener connected, resume from offset {}", resumeFrom);
            }

            @Override
            public void onMessage(String message) {
                try {
                    JsonNode event = mapper.readTree(message);
                    if (!event.path("finalized").asBoolean(false)) {
                        log.debug("Skipping non-final event {}", event.path("eventId").asText());
                        return;
                    }
                    String eventId = event.path("eventId").asText();
                    if (!seenEventIds.add(eventId)) {
                        log.info("Dedup event-id {}", eventId);
                        return;
                    }
                    long offset = event.path("offset").asLong();
                    if (handler != null) handler.accept(event);
                    saveOffset(offset);
                } catch (Exception e) {
                    log.error("Failed to process WS message", e);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                connected = false;
                log.warn("WS closed: {} {}", code, reason);
            }

            @Override
            public void onError(Exception ex) {
                log.error("WS error", ex);
            }
        };
        client.connect();
    }

    public void disconnect() {
        if (client != null) client.close();
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }
}

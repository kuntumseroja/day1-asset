package id.bi.detp.firefly.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FireflyMintClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void mintAcceptsContractShapedResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/tokens/mint", exchange -> {
            byte[] body = "{\"status\":\"ACCEPTED\",\"mintId\":\"mint-1\",\"idempotencyKey\":\"pact-uetr-001\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(202, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();

        var client = new FireflyMintClient("http://localhost:" + server.getAddress().getPort(), true);
        var result = client.mint("wRD", 1_000_000L, "pact-uetr-001");
        assertEquals("ACCEPTED", result.status());
        assertEquals("pact-uetr-001", result.idempotencyKey());
    }

    @Test
    void duplicateAcceptedMappedCorrectly() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/tokens/mint", exchange -> {
            byte[] body = "{\"status\":\"DUPLICATE_ACCEPTED\",\"idempotencyKey\":\"dup-key\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();

        var client = new FireflyMintClient("http://localhost:" + server.getAddress().getPort(), true);
        var result = client.mint("wRD", 1L, "dup-key");
        assertEquals(MintErrorAction.DUPLICATE_ACCEPTED, result.action());
    }
}

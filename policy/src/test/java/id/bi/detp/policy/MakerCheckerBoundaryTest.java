package id.bi.detp.policy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MakerCheckerBoundaryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("detp")
            .withUsername("detp")
            .withPassword("detp");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("policy.traffic-replay-path", () -> Path.of("../sim/yesterday-traffic.json").toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void authorCannotApproveOwnRule() throws Exception {
        String drl = Files.readString(Path.of("src/main/resources/samples/per_issuance_cap.drl"));
        String createBody = """
                {"name":"per_issuance_cap","drlContent":%s,"authorId":"author-a"}
                """.formatted(objectMapperQuote(drl));

        String createResponse = mockMvc.perform(post("/api/v1/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String ruleId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResponse).get("id").asText();

        mockMvc.perform(patch("/api/v1/rules/" + ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SUBMIT_TEST\",\"actorId\":\"author-a\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/rules/" + ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"actorId\":\"author-a\"}"))
                .andExpect(status().isForbidden());
    }

    private static String objectMapperQuote(String drl) {
        return "\"" + drl.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}

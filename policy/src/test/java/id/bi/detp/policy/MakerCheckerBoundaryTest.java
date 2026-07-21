package id.bi.detp.policy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:maker_checker;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema.sql",
        "spring.kafka.listener.auto-startup=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "policy.traffic-replay-path=../sim/yesterday-traffic.json",
        "policy.seed.enabled=false"
})
class MakerCheckerBoundaryTest {

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

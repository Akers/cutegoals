package com.cutegoals.common.logging;

import com.cutegoals.common.CommonTestApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the real {@code logback-spring.xml} configuration is loaded
 * correctly and produces valid JSON with all required fields, including
 * custom fields ({@code app}, {@code timezone}).
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = CommonTestApplication.class
)
class LogbackConfigurationTest {

    private static final Logger log = LoggerFactory.getLogger(LogbackConfigurationTest.class);

    @Test
    void logOutputContainsRequiredFieldsAndCustomFields(CapturedOutput output) throws Exception {
        // Arrange: log a message through the real logback-spring.xml configuration
        String testMessage = "LogbackConfigurationTest integration message";
        log.info(testMessage);

        // Act: capture and parse the JSON output
        String captured = output.getAll();
        assertThat(captured).as("Captured output should not be empty").isNotEmpty();

        ObjectMapper mapper = new ObjectMapper();
        boolean found = false;

        for (String line : captured.split("\\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            // Skip non-JSON output (Spring Boot banner, JVM warnings, etc.)
            if (!line.startsWith("{")) continue;

            JsonNode node;
            try {
                node = mapper.readTree(line);
            } catch (Exception e) {
                continue; // skip malformed lines
            }
            if (testMessage.equals(node.get("message").asText())) {
                found = true;

                // Assert: required fields
                assertThat(node.has("timestamp")).as("Log line missing 'timestamp'").isTrue();
                assertThat(node.has("level")).as("Log line missing 'level'").isTrue();
                assertThat(node.has("message")).as("Log line missing 'message'").isTrue();
                assertThat(node.has("logger_name")).as("Log line missing 'logger_name'").isTrue();
                assertThat(node.has("thread_name")).as("Log line missing 'thread_name'").isTrue();

                // Assert: custom fields from logback-spring.xml
                assertThat(node.has("app")).as("Log line missing custom field 'app'").isTrue();
                assertThat(node.get("app").asText()).as("custom field 'app' value mismatch").isEqualTo("CuteGoals");
                assertThat(node.has("timezone")).as("Log line missing custom field 'timezone'").isTrue();
                assertThat(node.get("timezone").asText()).as("custom field 'timezone' value mismatch").isEqualTo("Asia/Shanghai");

                break;
            }
        }

        assertThat(found).as("Test log message not found in output").isTrue();
    }
}

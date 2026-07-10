package com.cutegoals.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import com.cutegoals.common.util.MaskUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.fieldnames.LogstashFieldNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that LogstashEncoder produces valid JSON with required fields.
 *
 * Covers:
 * - Log output is valid JSON (one JSON object per line)
 * - Required fields: timestamp, level, message, logger_name, thread_name
 * - Sensitive values do not appear in log output
 */
class StructuredLoggingTest {

    private ByteArrayOutputStream outputStream;
    private OutputStreamAppender<ILoggingEvent> appender;
    private Logger logger;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        outputStream = new ByteArrayOutputStream();

        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext(context);

        // Match field naming and custom fields from logback-spring.xml
        LogstashFieldNames fieldNames = new LogstashFieldNames();
        fieldNames.setTimestamp("timestamp");
        fieldNames.setLevel("level");
        fieldNames.setLogger("logger_name");
        fieldNames.setThread("thread_name");
        fieldNames.setLevelValue("[ignore]");
        fieldNames.setVersion("[ignore]");
        encoder.setFieldNames(fieldNames);

        // Match custom fields from logback-spring.xml
        encoder.setCustomFields("{\"app\":\"CuteGoals\",\"timezone\":\"Asia/Shanghai\"}");

        encoder.start();

        appender = new OutputStreamAppender<>();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.setOutputStream(outputStream);
        appender.start();

        logger = context.getLogger("com.cutegoals.common.logging.StructuredLoggingTest");
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void logOutputIsValidJsonWithRequiredFields() throws Exception {
        logger.info("JSON validation test message");

        String output = outputStream.toString();
        assertThat(output).isNotEmpty();

        for (String line : output.split("\\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;

            JsonNode node = mapper.readTree(line);
            assertThat(node.has("timestamp")).as("Log line missing 'timestamp'").isTrue();
            assertThat(node.has("level")).as("Log line missing 'level'").isTrue();
            assertThat(node.has("message")).as("Log line missing 'message'").isTrue();
            assertThat(node.has("logger_name")).as("Log line missing 'logger_name'").isTrue();
            assertThat(node.has("thread_name")).as("Log line missing 'thread_name'").isTrue();
            assertThat(node.has("app")).as("Log line missing custom field 'app'").isTrue();
            assertThat(node.get("app").asText()).as("custom field 'app' value mismatch").isEqualTo("CuteGoals");
            assertThat(node.has("timezone")).as("Log line missing custom field 'timezone'").isTrue();
            assertThat(node.get("timezone").asText()).as("custom field 'timezone' value mismatch").isEqualTo("Asia/Shanghai");
        }
    }

    @Test
    void logMessageContentPreserved() throws Exception {
        String testMessage = "User login attempt from device";
        logger.info(testMessage);

        String output = outputStream.toString();
        boolean found = false;

        for (String line : output.split("\\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;

            JsonNode node = mapper.readTree(line);
            if (testMessage.equals(node.get("message").asText())) {
                found = true;
                break;
            }
        }

        assertThat(found).as("Expected log message not found in JSON output").isTrue();
    }

    @Test
    void sensitiveFieldsNotInLogOutput() {
        // These values MUST NOT appear in log output when MaskUtil is used
        String password = "myPassword123!";
        String pin = "4321";
        String token = "jwt.token.here";
        String phone = "13800138000";

        // Log the sensitive values THROUGH MaskUtil (as the application should do).
        // If MaskUtil has a bug and returns the raw value, the doesNotContain
        // assertions below will catch it.
        logger.info("Password: {}", MaskUtil.maskPassword(password));
        logger.info("PIN: {}", MaskUtil.maskPin(pin));
        logger.info("Token: {}", MaskUtil.maskToken(token));
        logger.info("Phone: {}", MaskUtil.maskPhone(phone));

        String output = outputStream.toString();

        // Verify original sensitive values are NOT in the output
        assertThat(output).as("Password must not appear in logs").doesNotContain(password);
        assertThat(output).as("PIN must not appear in logs").doesNotContain(pin);
        assertThat(output).as("Token must not appear in logs").doesNotContain(token);
        assertThat(output).as("Phone must not appear in logs").doesNotContain(phone);

        // Verify masked strings ARE in the output
        assertThat(output).contains(MaskUtil.MASKED);
    }
}

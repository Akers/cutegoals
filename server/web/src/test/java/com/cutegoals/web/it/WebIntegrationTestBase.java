package com.cutegoals.web.it;

import com.cutegoals.web.CuteGoalsApplication;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Base class for CuteGoals integration tests.
 * Provides MockMvc, ObjectMapper, and common test utilities.
 * Uses H2 PostgreSQL mode for lightweight database.
 */
@SpringBootTest(
    classes = CuteGoalsApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class WebIntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String sessionCookie;

    /**
     * Read response body safely.
     */
    protected String responseBody(MvcResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Helper: perform POST request with JSON body and return status+body.
     */
    protected MvcResult postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andReturn();
    }

    /**
     * Helper: perform authenticated POST with session cookie.
     */
    protected MvcResult postJsonAuth(String url, Object body) throws Exception {
        var request = post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (sessionCookie != null) {
            request.cookie(new jakarta.servlet.http.Cookie("SESSION", sessionCookie));
        }
        return mockMvc.perform(request).andReturn();
    }

    /**
     * Helper: perform authenticated GET with session cookie.
     */
    protected MvcResult getAuth(String url) throws Exception {
        var request = get(url);
        if (sessionCookie != null) {
            request.cookie(new jakarta.servlet.http.Cookie("SESSION", sessionCookie));
        }
        return mockMvc.perform(request).andReturn();
    }

    /**
     * Helper: parse response JSON body.
     */
    protected <T> T parse(MvcResult result, Class<T> clazz) throws Exception {
        return objectMapper.readValue(responseBody(result), clazz);
    }

    /**
     * Helper: get JSON path value from response.
     */
    protected String extractJsonPath(MvcResult result, String path) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(responseBody(result), path).toString();
    }

    /**
     * Helper: assert HTTP status.
     */
    protected void assertStatus(MvcResult result, int expected) {
        int actual = result.getResponse().getStatus();
        if (actual != expected) {
            String body = responseBody(result);
            throw new AssertionError(
                String.format("Expected status %d but got %d. Body: %s",
                    expected, actual, body));
        }
    }
}

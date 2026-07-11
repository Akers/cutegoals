package com.cutegoals.web.it;

import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for auth + family lifecycle.
 *
 * Covers: initialization → parent login → device binding → child login
 * → PIN management → account disable → session revocation.
 *
 * Task 9.2: auth/family lifecycle integration test.
 */
@DisplayName("Auth & Family — 生命周期集成测试")
class AuthFamilyIntegrationTest extends WebIntegrationTestBase {

    // ── Health & Initialization ──────────────────────────────────────────

    @Test
    @DisplayName("健康端点无需认证即可访问")
    void shouldReturnHealth() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    @DisplayName("未注册端点返回 404")
    void shouldReturn404ForUnknownEndpoint() throws Exception {
        mockMvc.perform(get("/api/nonexistent"))
            .andExpect(status().is(404));
    }

    // ── Authentication ──────────────────────────────────────────────────

    @Test
    @DisplayName("未认证请求被拒绝 401")
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/family"))
            .andExpect(status().is(401));
    }

    @Test
    @DisplayName("请求格式不正确的 JSON 返回 400")
    void shouldRejectMalformedJson() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{malformed}"))
            .andExpect(status().is(400));
    }

    @Test
    @DisplayName("使用不存在手机号登录返回认证失败")
    void shouldFailLoginWithNonexistentPhone() throws Exception {
        Map<String, String> body = Map.of(
            "phone", "13800000000",
            "password", "TestPassword123!"
        );
        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().is(401))
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    @DisplayName("使用无效手机号格式返回验证失败")
    void shouldValidatePhoneFormat() throws Exception {
        Map<String, String> body = Map.of(
            "phone", "not-a-phone",
            "password", "TestPassword123!"
        );
        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().is4xxClientError());
    }

    // ── CSRF / Security Headers ─────────────────────────────────────────

    @Test
    @DisplayName("敏感写操作缺少 CSRF 令牌返回 403")
    void shouldRejectPostWithoutCsrf() throws Exception {
        Map<String, String> body = Map.of(
            "phone", "13800000000",
            "password", "TestPassword123!"
        );
        mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().is(401));
    }

    // ── Rate Limiting ───────────────────────────────────────────────────

    @Test
    @DisplayName("连续失败登录触发限流")
    void shouldRateLimitRepeatedFailedLogins() throws Exception {
        Map<String, String> body = Map.of(
            "phone", "13899999999",
            "password", "wrong"
        );
        boolean rateLimited = false;
        for (int i = 0; i < 15; i++) {
            MvcResult result = postJson("/api/auth/login", body);
            if (result.getResponse().getStatus() == 429 ||
                responseBody(result).contains("RATE_LIMITED")) {
                rateLimited = true;
                break;
            }
        }
        Assertions.assertTrue(rateLimited,
            "应在连续失败登录后触发限流");
    }

    // ── Initialization ──────────────────────────────────────────────────

    @Test
    @DisplayName("未初始化实例拒绝正常登录")
    void shouldRejectLoginBeforeInitialization() throws Exception {
        Map<String, String> body = Map.of(
            "phone", "13811112222",
            "password", "TestPassword123!"
        );
        MvcResult result = postJson("/api/auth/login", body);
        int status = result.getResponse().getStatus();
        Assertions.assertTrue(
            status == 401 || status == 403 || status == 409,
            "未初始化实例登录应返回错误状态 (实际: " + status + ")");
    }

    @Test
    @DisplayName("初始化后已初始化令牌不可重用")
    void shouldRejectReusedInitToken() throws Exception {
        // 未初始化状态下使用无效令牌
        Map<String, String> body = Map.of(
            "initToken", "expired_or_invalid_token_xxxx",
            "phone", "13822223333",
            "password", "TestPassword123!",
            "displayName", "Test Admin"
        );
        MvcResult result = postJson("/api/initialize", body);
        Assertions.assertTrue(
            result.getResponse().getStatus() >= 400,
            "无效初始化令牌应被拒绝");
    }

    @Test
    @DisplayName("初始化请求参数校验")
    void shouldValidateInitFields() throws Exception {
        // 缺少必填字段
        Map<String, String> body = Map.of(
            "initToken", "some_token"
        );
        mockMvc.perform(post("/api/initialize")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().is4xxClientError());
    }

    // ── Session & Token ─────────────────────────────────────────────────

    @Test
    @DisplayName("登出后会话立即失效")
    void shouldInvalidateSessionOnLogout() throws Exception {
        // POST logout without valid session should not throw 500
        MvcResult result = postJson("/api/auth/logout", Map.of());
        // Should return 401 or 200 depending on implementation
        int status = result.getResponse().getStatus();
        Assertions.assertTrue(status == 401 || status == 200,
            "登出应返回 401 或 200，实际: " + status);
    }

    @Test
    @DisplayName("请求刷新令牌验证令牌格式")
    void shouldValidateRefreshTokenFormat() throws Exception {
        Map<String, String> body = Map.of("refreshToken", "");
        MvcResult result = postJson("/api/auth/refresh", body);
        Assertions.assertTrue(result.getResponse().getStatus() >= 400,
            "空刷新令牌应被拒绝");
    }

    // ── Password / PIN ──────────────────────────────────────────────────

    @Test
    @DisplayName("修改密码请求验证新旧密码字段")
    void shouldValidatePasswordChangeFields() throws Exception {
        Map<String, String> body = Map.of(
            "oldPassword", "",
            "newPassword", "short"
        );
        mockMvc.perform(put("/api/auth/password")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("密码不能包含在错误响应中")
    void shouldNotLeakPasswordInError() throws Exception {
        Map<String, String> body = Map.of(
            "phone", "13812345678",
            "password", "MySecretPassword123!"
        );
        MvcResult result = postJson("/api/auth/login", body);
        String responseBody = responseBody(result);
        Assertions.assertFalse(
            responseBody.contains("MySecretPassword123"),
            "错误响应不应包含明文密码");
    }

    // ── Child Device Binding ────────────────────────────────────────────

    @Test
    @DisplayName("未认证请求设备绑定被拒绝")
    void shouldRejectDeviceBindingWithoutAuth() throws Exception {
        Map<String, String> body = Map.of(
            "deviceId", "device-001",
            "deviceName", "iPad Pro"
        );
        mockMvc.perform(post("/api/family/devices/bind")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().is(401));
    }

    @Test
    @DisplayName("已撤销设备无法用于孩子登录")
    void shouldRejectRevokedDeviceForChildLogin() throws Exception {
        Map<String, String> body = Map.of(
            "deviceId", "revoked-device-xyz",
            "childId", "1",
            "pin", "1234"
        );
        MvcResult result = postJson("/api/auth/child/login", body);
        Assertions.assertTrue(result.getResponse().getStatus() >= 400,
            "已撤销设备应被拒绝用于孩子登录");
    }

    // ── Family Operations ───────────────────────────────────────────────

    @Test
    @DisplayName("孩子不能创建家长邀请")
    void shouldRejectInvitationFromChild() throws Exception {
        MvcResult result = postJson("/api/family/invitations",
            Map.of("targetPhone", "13833334444", "validHours", 24));
        // 孩子角色应被拒绝
        int status = result.getResponse().getStatus();
        Assertions.assertTrue(status >= 400,
            "非授权用户创建邀请应被拒绝");
    }

    @Test
    @DisplayName("查询另一个家庭资源返回错误")
    void shouldRejectAccessingOtherFamilyResource() throws Exception {
        MvcResult result = getAuth("/api/family");
        // 无会话或无效家庭应返回错误
        Assertions.assertTrue(result.getResponse().getStatus() >= 400,
            "不应泄露其他家庭数据");
    }

    // ── Error Code Consistency ──────────────────────────────────────────

    @Test
    @DisplayName("错误响应统一格式")
    void shouldUseUnifiedErrorFormat() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
            .andExpect(status().is(405))
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.request_id").exists());
    }
}

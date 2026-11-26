package com.landfall.module.auth.internal;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.landfall.common.api.ApiResponse;
import com.landfall.common.id.UuidV7Generator;
import com.landfall.module.auth.AuthContext;
import com.landfall.support.AbstractIntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@AutoConfigureMockMvc
@Import(AuthLifecycleIT.AuthProbeConfig.class)
class AuthLifecycleIT extends AbstractIntegrationTest {

    private static final String TOKEN_REGEX = "^[A-Za-z0-9_-]{43}$";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final BootstrapAdminInitializer bootstrapAdminInitializer;
    private final List<UUID> createdUserIds = new ArrayList<>();

    @Autowired
    AuthLifecycleIT(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            SysUserRoleMapper sysUserRoleMapper,
            BootstrapAdminInitializer bootstrapAdminInitializer) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.bootstrapAdminInitializer = bootstrapAdminInitializer;
    }

    @AfterEach
    void cleanupCreatedUsers() {
        for (UUID userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", userId);
        }
        createdUserIds.clear();
    }

    @Test
    void registerLoginMeLogout_completesLifecycleAndRecordsLoginActivity() throws Exception {
        String s = suffix();
        String username = "u_" + s;
        String email = "U_" + s + "@Landfall.Dev";

        MvcResult reg = postJson(
                        "/api/v1/auth/register", Map.of("username", username, "email", email, "password", "Pass123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.data.nickname").value(username))
                .andExpect(jsonPath("$.data.roles", containsInAnyOrder("ROLE_USER")))
                .andExpect(jsonPath("$.data.permissions", empty()))
                .andReturn();
        UUID userId = track(UUID.fromString(jsonText(reg, "/data/userId")));

        postJson(
                        "/api/v1/auth/register",
                        Map.of(
                                "username",
                                username.toUpperCase(),
                                "email",
                                "o_" + s + "@landfall.dev",
                                "password",
                                "Pass123456"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(20101));
        postJson(
                        "/api/v1/auth/register",
                        Map.of("username", "o_" + s, "email", email.toUpperCase(), "password", "Pass123456"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(20102));
        postJson("/api/v1/auth/login", Map.of("account", username, "password", "WrongPass123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(20103));
        postJson("/api/v1/auth/login", Map.of("account", "missing_" + s, "password", "Pass123456"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(20103));

        String token1 = login(email.toUpperCase(), "Pass123456");
        String token2 = login(username, "Pass123456");
        assertNotEquals(token1, token2);
        assertEquals(
                2,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM user_activity_logs WHERE user_id = ? AND action_type = 'LOGIN'",
                        Integer.class,
                        userId));

        getAuth("/api/v1/auth/me", token1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.roles", containsInAnyOrder("ROLE_USER")));

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        getAuth("/api/v1/auth/me", token1)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20006));
        getAuth("/api/v1/auth/me", token2).andExpect(status().isOk());
    }

    @Test
    void changePassword_kicksAllOnlineTokensAndEvictsPermissionCache() throws Exception {
        String s = suffix();
        String username = "pwd_" + s;
        UUID userId = register(username, "pwd_" + s + "@landfall.dev", "OldPass123");
        String token1 = login(username, "OldPass123");
        String token2 = login(username, "OldPass123");
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.hasKey(AuthRbacCache.KEY_PREFIX + userId)));

        putAuthJson("/api/v1/auth/password", token1, Map.of("oldPassword", "WrongOld123", "newPassword", "NewPass123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(20104));
        putAuthJson("/api/v1/auth/password", token1, Map.of("oldPassword", "OldPass123", "newPassword", "NewPass123"))
                .andExpect(status().isOk());

        assertEquals(Boolean.FALSE, stringRedisTemplate.hasKey(AuthRbacCache.KEY_PREFIX + userId));
        getAuth("/api/v1/auth/me", token1)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20006));
        getAuth("/api/v1/auth/me", token2)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20006));
        postJson("/api/v1/auth/login", Map.of("account", username, "password", "OldPass123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(20103));
        login(username, "NewPass123");
    }

    @Test
    void publicAllowlist_injectsValidVisitorSlidesTtlAndDegradesExpiredOrCorruptedSessions() throws Exception {
        String s = suffix();
        String username = "pub_" + s;
        UUID userId = register(username, "pub_" + s + "@landfall.dev", "Pass123456");

        String activeToken = login(username, "Pass123456");
        String expiredToken = login(username, "Pass123456");
        String loggedOutToken = login(username, "Pass123456");
        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(loggedOutToken)))
                .andExpect(status().isOk());

        // 有效令牌在公开路由注入上下文并滑动续期
        StpUtil.renewTimeout(activeToken, 120L);
        getAuth("/api/v1/public/auth-probe", activeToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.canAccessAdmin").value(false));
        assertTrue(StpUtil.getTokenTimeout(activeToken) > 600_000L);

        // 真 TTL 过期令牌与已登出令牌在公开路由降级游客、受保护路由返 20006
        StpUtil.renewTimeout(expiredToken, 1L);
        await().atMost(Duration.ofSeconds(3)).until(() -> StpUtil.getTokenTimeout(expiredToken) == -2L);
        for (String stale : List.of(bearer(expiredToken), bearer(loggedOutToken))) {
            assertPublicGuestAndProtectedExpired(stale);
        }

        // 并发会话丢失（Account-Session 被清理但 token key 残留）不抛 500，公开路由降级游客、受保护路由返 20006
        stringRedisTemplate.delete(StpUtil.getStpLogic().splicingKeySession(userId.toString()));
        assertPublicGuestAndProtectedExpired(bearer(activeToken));

        // 软删用户访问受保护路由返 20006 并踢出同账号其他令牌与权限缓存
        String softDelToken1 = login(username, "Pass123456");
        String softDelToken2 = login(username, "Pass123456");
        jdbcTemplate.update("UPDATE sys_user SET deleted_at = now() WHERE id = ?", userId);
        getAuth("/api/v1/auth/me", softDelToken1)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20006));
        assertEquals(-2L, StpUtil.getTokenTimeout(softDelToken2));
        assertEquals(Boolean.FALSE, stringRedisTemplate.hasKey(AuthRbacCache.KEY_PREFIX + userId));

        // 不带 Authorization 访问公开路由 200 游客、受保护路由 401 + 20001
        mockMvc.perform(get("/api/v1/public/auth-probe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false));
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20001));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer forged-token", "Basic dXNlcjpwYXNz", "Bearer ", "malformed-header"})
    void malformedOrForgedAuthorization_degradesOnPublicAndFails20006OnProtected(String header) throws Exception {
        assertPublicGuestAndProtectedExpired(header);
        mockMvc.perform(get("/v3/api-docs").header(HttpHeaders.AUTHORIZATION, header))
                .andExpect(status().isOk());
    }

    @Test
    void adminAccessPermissionAnnotationBannedStatusAndBootstrapAdmin_enforcedStrictly() throws Exception {
        String adminToken = login("admin", "Admin123456");
        getAuth("/api/v1/public/auth-probe", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canAccessAdmin").value(true));
        getAuth("/api/v1/admin/auth-probe", adminToken).andExpect(status().isOk());
        getAuth("/api/v1/admin/role-manage-probe", adminToken).andExpect(status().isOk());

        // 幂等重跑默认超管初始化器：不建重复记录，也不覆盖已修改的密码与昵称
        putAuthJson(
                        "/api/v1/auth/password",
                        adminToken,
                        Map.of("oldPassword", "Admin123456", "newPassword", "RotatedAdmin123"))
                .andExpect(status().isOk());
        jdbcTemplate.update(
                "UPDATE sys_user SET nickname = '自定义超管' WHERE LOWER(username) = 'admin' AND deleted_at IS NULL");
        try {
            bootstrapAdminInitializer.run(null);
            assertEquals(
                    1,
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM sys_user WHERE LOWER(username) = 'admin' AND deleted_at IS NULL",
                            Integer.class));
            String rotatedToken = login("admin", "RotatedAdmin123");
            getAuth("/api/v1/auth/me", rotatedToken)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").value("自定义超管"));
            putAuthJson(
                            "/api/v1/auth/password",
                            rotatedToken,
                            Map.of("oldPassword", "RotatedAdmin123", "newPassword", "Admin123456"))
                    .andExpect(status().isOk());
        } finally {
            jdbcTemplate.update(
                    "UPDATE sys_user SET nickname = '系统管理员' WHERE LOWER(username) = 'admin' AND deleted_at IS NULL");
        }

        // 普通用户访问 /api/v1/admin/** 返 403 + 20007
        String s = suffix();
        String normalUser = "norm_" + s;
        UUID normalUserId = register(normalUser, "norm_" + s + "@landfall.dev", "Pass123456");
        String normalToken = login(normalUser, "Pass123456");
        getAuth("/api/v1/admin/auth-probe", normalToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20007));

        // ROLE_MODERATOR 通过 admin:access，但缺少 admin:role:manage 返 403 + 20003
        String modUser = "mod_" + s;
        UUID modUserId = register(modUser, "mod_" + s + "@landfall.dev", "Pass123456");
        UUID modRoleId =
                jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE code = 'ROLE_MODERATOR'", UUID.class);
        sysUserRoleMapper.insert(new SysUserRoleEntity(modUserId, modRoleId));
        String modToken = login(modUser, "Pass123456");
        getAuth("/api/v1/admin/auth-probe", modToken).andExpect(status().isOk());
        getAuth("/api/v1/admin/role-manage-probe", modToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20003));

        // 封禁用户：受保护与后台均返 403 + 20004，公开路由静默降级游客 200，登录正确密码 20004 / 错密码 20103
        jdbcTemplate.update("UPDATE sys_user SET status = 'BANNED' WHERE id = ?", normalUserId);
        getAuth("/api/v1/auth/me", normalToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20004));
        getAuth("/api/v1/admin/auth-probe", normalToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20004));
        getAuth("/api/v1/public/auth-probe", normalToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false));
        postJson("/api/v1/auth/login", Map.of("account", normalUser, "password", "Pass123456"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20004));
        postJson("/api/v1/auth/login", Map.of("account", normalUser, "password", "WrongPass123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(20103));
    }

    private void assertPublicGuestAndProtectedExpired(String header) throws Exception {
        mockMvc.perform(get("/api/v1/public/auth-probe").header(HttpHeaders.AUTHORIZATION, header))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(false));
        mockMvc.perform(get("/api/v1/public/health").header(HttpHeaders.AUTHORIZATION, header))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, header))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20006));
    }

    private UUID register(String username, String email, String password) throws Exception {
        MvcResult res = postJson(
                        "/api/v1/auth/register", Map.of("username", username, "email", email, "password", password))
                .andExpect(status().isOk())
                .andReturn();
        return track(UUID.fromString(jsonText(res, "/data/userId")));
    }

    private String login(String account, String password) throws Exception {
        MvcResult res = postJson("/api/v1/auth/login", Map.of("account", account, "password", password))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token", matchesPattern(TOKEN_REGEX)))
                .andReturn();
        return jsonText(res, "/data/token");
    }

    private ResultActions postJson(String uri, Object body) throws Exception {
        return mockMvc.perform(
                post(uri).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions putAuthJson(String uri, String token, Object body) throws Exception {
        return mockMvc.perform(put(uri).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions getAuth(String uri, String token) throws Exception {
        return mockMvc.perform(get(uri).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private String jsonText(MvcResult result, String pointer) throws Exception {
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .at(pointer)
                .asText();
    }

    private UUID track(UUID userId) {
        createdUserIds.add(userId);
        return userId;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String suffix() {
        return UuidV7Generator.generate().toString().replace("-", "").substring(24);
    }

    @TestConfiguration
    static class AuthProbeConfig {

        @RestController
        static class AuthProbeController {

            private final AuthContext authContext;

            AuthProbeController(AuthContext authContext) {
                this.authContext = authContext;
            }

            @GetMapping("/api/v1/public/auth-probe")
            ApiResponse<Map<String, Object>> publicProbe() {
                return authContext
                        .currentUserId()
                        .map(id -> Map.<String, Object>of(
                                "authenticated", true,
                                "userId", id.toString(),
                                "canAccessAdmin", authContext.hasPermission("admin:access")))
                        .map(ApiResponse::ok)
                        .orElseGet(() -> ApiResponse.ok(Map.of("authenticated", false)));
            }

            @GetMapping("/api/v1/admin/auth-probe")
            ApiResponse<Map<String, Object>> adminProbe() {
                return ApiResponse.ok(
                        Map.of("userId", authContext.requireUserId().toString()));
            }

            @SaCheckPermission("admin:role:manage")
            @GetMapping("/api/v1/admin/role-manage-probe")
            ApiResponse<Map<String, Object>> roleManageProbe() {
                return ApiResponse.ok(Map.of("allowed", true));
            }
        }
    }
}

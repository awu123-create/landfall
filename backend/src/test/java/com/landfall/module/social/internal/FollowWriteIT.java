package com.landfall.module.social.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.landfall.common.id.UuidV7Generator;
import com.landfall.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class FollowWriteIT extends AbstractIntegrationTest {

    private static final String TOKEN_REGEX = "^[A-Za-z0-9_-]{43}$";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final List<UUID> createdUserIds = new ArrayList<>();

    @Autowired
    FollowWriteIT(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void cleanupCreatedUsers() {
        for (UUID userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", userId);
        }
        createdUserIds.clear();
    }

    @Test
    void followIsIdempotentAndMutualFollowAppearsOnlyWhenBothDirectionsExist() throws Exception {
        UUID alice = registerUser("follow_a_");
        UUID bob = registerUser("follow_b_");
        String aliceToken = login(accountOf(alice));

        followJson(aliceToken, bob)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.following").value(true))
                .andExpect(jsonPath("$.data.mutualFollow").value(false))
                .andExpect(jsonPath("$.data.followerCount").value(1));

        // 重复关注（网络重试 / 连点）幂等：不新增记录、粉丝数不重复增长
        followJson(aliceToken, bob)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(true))
                .andExpect(jsonPath("$.data.followerCount").value(1));
        assertEquals(1, countFollowRows(alice, bob));

        // Bob 回关后构成 MutualFollow
        followJson(login(accountOf(bob)), alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mutualFollow").value(true));
        followJson(aliceToken, bob)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mutualFollow").value(true))
                .andExpect(jsonPath("$.data.followerCount").value(1));
    }

    @Test
    void unfollowRemovesFollowingAndClearsMutualFollowForBothSides() throws Exception {
        UUID alice = registerUser("unfollow_a_");
        UUID bob = registerUser("unfollow_b_");
        String aliceToken = login(accountOf(alice));
        String bobToken = login(accountOf(bob));
        followJson(aliceToken, bob).andExpect(status().isOk());
        followJson(bobToken, alice).andExpect(status().isOk());

        unfollowJson(bobToken, alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(false))
                .andExpect(jsonPath("$.data.mutualFollow").value(false))
                .andExpect(jsonPath("$.data.followerCount").value(0));

        // Alice 仍关注 Bob，但双向关系已断开，互关标记必须实时回退
        followJson(aliceToken, bob)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(true))
                .andExpect(jsonPath("$.data.mutualFollow").value(false));
        assertEquals(1, countFollowRows(alice, bob));
        assertEquals(0, countFollowRows(bob, alice));

        // 未关注时取关同样幂等成功
        unfollowJson(bobToken, alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(false));
    }

    @Test
    void followingSelfIsRejectedWith50001() throws Exception {
        UUID alice = registerUser("self_a_");
        followJson(login(accountOf(alice)), alice)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(50001));
    }

    @Test
    void followingMissingOrSoftDeletedUserIsRejectedWith50002() throws Exception {
        UUID alice = registerUser("missing_a_");
        String aliceToken = login(accountOf(alice));

        followJson(aliceToken, UuidV7Generator.generate())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(50002));

        UUID softDeleted = registerUser("missing_b_");
        jdbcTemplate.update("UPDATE sys_user SET deleted_at = now() WHERE id = ?", softDeleted);
        followJson(aliceToken, softDeleted)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(50002));

        unfollowJson(aliceToken, UuidV7Generator.generate())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(50002));
    }

    @Test
    void followRequiresLoginAndRejectsForgedToken() throws Exception {
        UUID alice = registerUser("auth_a_");
        UUID bob = registerUser("auth_b_");

        mockMvc.perform(post("/api/v1/users/{userId}/follow", bob))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20001));
        mockMvc.perform(post("/api/v1/users/{userId}/follow", bob)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer forged-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20006));
        mockMvc.perform(delete("/api/v1/users/{userId}/follow", bob))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    void followTableKeepsOnlyImmutableRelationFacts() {
        assertEquals(
                0,
                count(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_name = 'user_follows' AND column_name = 'is_mutual'
                        """));
        assertEquals(
                1,
                count(
                        """
                        SELECT COUNT(*) FROM pg_constraint
                        WHERE conname = 'uk_user_follows_pair' AND contype = 'u'
                        """));
        assertEquals(
                1,
                count(
                        """
                        SELECT COUNT(*) FROM pg_constraint
                        WHERE conname = 'chk_user_follows_not_self' AND contype = 'c'
                        """));
        assertEquals(
                1,
                count(
                        """
                        SELECT COUNT(*) FROM pg_indexes
                        WHERE tablename = 'user_follows' AND indexname = 'idx_user_follows_followee_follower'
                        """));
    }

    @Test
    void deletingUserCascadesFollowRows() throws Exception {
        UUID alice = registerUser("cascade_a_");
        UUID bob = registerUser("cascade_b_");
        followJson(login(accountOf(alice)), bob).andExpect(status().isOk());
        assertTrue(countFollowRows(alice, bob) > 0);

        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", bob);
        createdUserIds.remove(bob);
        assertEquals(0, countFollowRows(alice, bob));
    }

    private ResultActions followJson(String token, UUID targetUserId) throws Exception {
        return mockMvc.perform(
                post("/api/v1/users/{userId}/follow", targetUserId).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions unfollowJson(String token, UUID targetUserId) throws Exception {
        return mockMvc.perform(
                delete("/api/v1/users/{userId}/follow", targetUserId).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private UUID registerUser(String usernamePrefix) throws Exception {
        String suffix = suffix();
        String username = usernamePrefix + suffix;
        String email = username + "@landfall.dev";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "email", email, "password", "Pass123456"))))
                .andExpect(status().isOk())
                .andReturn();
        UUID userId = UUID.fromString(objectMapper
                .readTree(result.getResponse().getContentAsString())
                .at("/data/userId")
                .asText());
        createdUserIds.add(userId);
        return userId;
    }

    private String login(String account) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("account", account, "password", "Pass123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token", org.hamcrest.Matchers.matchesPattern(TOKEN_REGEX)))
                .andReturn();
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .at("/data/token")
                .asText();
    }

    private String accountOf(UUID userId) {
        return jdbcTemplate.queryForObject("SELECT username FROM sys_user WHERE id = ?", String.class, userId);
    }

    private int countFollowRows(UUID followerId, UUID followeeId) {
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_follows WHERE follower_id = ? AND followee_id = ?",
                Integer.class,
                followerId,
                followeeId);
        return rows == null ? 0 : rows;
    }

    private int count(String sql) {
        Integer rows = jdbcTemplate.queryForObject(sql, Integer.class);
        return rows == null ? 0 : rows;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String suffix() {
        return UuidV7Generator.generate().toString().replace("-", "").substring(24);
    }
}

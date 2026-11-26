package com.landfall;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.landfall.common.id.UuidV7Generator;
import com.landfall.support.AbstractIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class LandfallApplicationIT extends AbstractIntegrationTest {

    private final MockMvc mockMvc;
    private final RedisConnectionFactory redisConnectionFactory;

    @Autowired
    LandfallApplicationIT(MockMvc mockMvc, RedisConnectionFactory redisConnectionFactory) {
        this.mockMvc = mockMvc;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Test
    void openApiDocs_exposesLandfallContractMetadata() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi", startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("Landfall API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.info.description").value("Landfall（见屿）多媒体社区后端接口契约"))
                .andExpect(jsonPath("$.paths['/api/v1/public/health'].get").exists());
    }

    @Test
    void redisConnection_usesIsolatedDatabase15ForReadWrite() {
        LettuceConnectionFactory lettuce = assertInstanceOf(LettuceConnectionFactory.class, redisConnectionFactory);
        assertEquals(REDIS_TEST_DATABASE, lettuce.getDatabase());

        String key = "landfall:it:ping:" + UuidV7Generator.generate();
        stringRedisTemplate.opsForValue().set(key, "pong", Duration.ofSeconds(30));
        assertEquals("pong", stringRedisTemplate.opsForValue().get(key));
    }
}

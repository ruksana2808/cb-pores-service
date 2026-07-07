package com.igot.cb.pores.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    @Test
    void testRedisConnectionFactory() {
        RedisConfig redisConfig = new RedisConfig();
        ReflectionTestUtils.setField(redisConfig, "redisHost", "localhost");
        ReflectionTestUtils.setField(redisConfig, "redisPort", 6379);

        RedisConnectionFactory factory = redisConfig.redisConnectionFactory();
        assertNotNull(factory);
        assertTrue(factory instanceof LettuceConnectionFactory);
    }

    @Test
    void testRedisTemplate() {
        RedisConfig redisConfig = new RedisConfig();
        RedisConnectionFactory mockFactory = mock(RedisConnectionFactory.class);
        RedisTemplate<String, String> template = redisConfig.redisTemplate(mockFactory);
        assertNotNull(template);
    }
}
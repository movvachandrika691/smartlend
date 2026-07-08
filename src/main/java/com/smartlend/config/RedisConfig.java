package com.smartlend.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for caching and JWT blacklist.
 *
 * activateDefaultTyping is intentionally NOT used here.
 * It causes @class metadata to leak into HTTP responses by interfering
 * with Spring's ObjectMapper even when using a separate named bean.
 *
 * Instead, we never cache Page<T> objects directly in Redis.
 * The service layer caches only List<LoanApplicationResponse> and
 * reconstructs Page<T> from the list + pageable after cache read.
 * This is simpler, safer, and avoids all generic type deserialization issues.
 */
@Configuration
public class RedisConfig {

    /**
     * Redis-specific ObjectMapper — named to avoid Spring Boot
     * auto-configuring it as the primary HTTP serializer.
     * No activateDefaultTyping — avoids @class leaking into API responses.
     */
    @Bean("redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            @org.springframework.beans.factory.annotation.Qualifier("redisObjectMapper")
            ObjectMapper redisObjectMapper) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
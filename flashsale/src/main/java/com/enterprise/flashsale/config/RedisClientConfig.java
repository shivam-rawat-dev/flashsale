package com.enterprise.flashsale.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisClientConfig {

    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient(RedisProperties redisProperties) {

        RedisURI.Builder redisUriBuilder = RedisURI.builder()
                .withHost(redisProperties.getHost())
                .withPort(redisProperties.getPort());

        if (redisProperties.getPassword() != null
                && !redisProperties.getPassword().isBlank()) {

            redisUriBuilder.withPassword(
                    redisProperties.getPassword().toCharArray()
            );
        }

        return RedisClient.create(redisUriBuilder.build());
    }
}
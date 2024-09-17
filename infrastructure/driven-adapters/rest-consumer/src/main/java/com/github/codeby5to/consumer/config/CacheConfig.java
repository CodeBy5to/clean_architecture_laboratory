package com.github.codeby5to.consumer.config;

import com.github.codeby5to.model.pokemon.Pokemon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;


@Configuration
public class CacheConfig {

    @Bean
    public ReactiveRedisTemplate<String, Pokemon> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<Pokemon> serializer = new Jackson2JsonRedisSerializer<>(Pokemon.class);
        RedisSerializationContext.RedisSerializationContextBuilder<String, Pokemon> builder =RedisSerializationContext.newSerializationContext(new Jackson2JsonRedisSerializer<>(String.class));
        RedisSerializationContext<String, Pokemon> context = builder.value(serializer).build();
        return new ReactiveRedisTemplate<>(factory, context);
    }
}

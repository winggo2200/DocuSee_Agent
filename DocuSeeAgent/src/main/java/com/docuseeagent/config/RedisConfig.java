package com.docuseeagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
//지정한 host, port, serializer를 등록하기 위한 부분
@EnableAutoConfiguration(exclude={RedisAutoConfiguration.class, RedisReactiveAutoConfiguration.class})
public class RedisConfig {
    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Value("${spring.data.redis.password}")
    private String password;

//    @Bean
//    public ReactiveRedisConnectionFactory redisConnectionFactory() {
//        return new LettuceConnectionFactory(host, port);
//    }
//
//    @Bean
//    public ReactiveRedisOperations<String, Object> redisTemplate() {
//        ReactiveRedisConnectionFactory rrcf = redisConnectionFactory();
//
//        /*
//        JSON 형식의 데이터를 Redis에 저장하기 위한 직렬화 및 역직렬화를 담당하는 Jackson2JsonRedisSerializer를 생성
//        이 때, Object.class를 전달하여 어떠한 객체 타입이라도 처리할 수 있도록 설정
//        * */
//        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
//
//        RedisSerializationContext.RedisSerializationContextBuilder<String, Object> builder = RedisSerializationContext
//                .newSerializationContext(new StringRedisSerializer());
//
//        RedisSerializationContext<String, Object> context = builder.value(serializer).hashValue(serializer)
//                .hashKey(serializer).build();
//
//        return new ReactiveRedisTemplate<>(rrcf, context);
//    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // Redis 연결을 설정
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setHostName(host);
        redisStandaloneConfiguration.setPort(port);
        redisStandaloneConfiguration.setPassword(password);

        return new LettuceConnectionFactory(redisStandaloneConfiguration);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();

        redisTemplate.setConnectionFactory(redisConnectionFactory());

        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());

        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());

        redisTemplate.setDefaultSerializer(new StringRedisSerializer());

        return redisTemplate;
    }
}

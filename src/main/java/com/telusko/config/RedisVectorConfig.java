package com.telusko.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

@Configuration
public class RedisVectorConfig {

    @Bean
    public JedisPooled jedisPooled() {
        // default Redis port
        return new JedisPooled("localhost", 6379);
    }

    @Bean
    public VectorStore redisVectorStore(JedisPooled jedis, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedis, embeddingModel)
                .indexName("product-index")
                .prefix("product")
                .vectorAlgorithm(RedisVectorStore.Algorithm.HSNW) // Optional
                .initializeSchema(true) // Create index if missing
                .build();
    }
}

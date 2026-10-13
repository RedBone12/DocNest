package com.priyanshu.index.indexing_service.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SearchCacheInvalidator {

    private static final String VERSION_KEY = "docnest:search:version";

    private final StringRedisTemplate redis;

    public SearchCacheInvalidator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void invalidate() {
        try {
            Long version = redis.opsForValue().increment(VERSION_KEY);
            log.info("Search cache invalidated; version is now {}", version);
        } catch (Exception e) {
            log.warn("Search cache invalidation failed: {}", e.getMessage());
        }
    }
}

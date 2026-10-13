package com.priyanshu.documents.search_service.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priyanshu.documents.search_service.dto.SearchResponse;
import com.priyanshu.documents.search_service.service.DocumentSearchService;

@RestController
@RequestMapping("/search")
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    private final DocumentSearchService searchService;

    public SearchController(DocumentSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public SearchResponse search(
            @RequestParam String q,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth
    ) throws IOException {
        String userId = auth.getName();

        logger.info("Search request received - query: '{}', page: {}, size: {}, userId: {}", q, page, size, userId);

        long startTime = System.currentTimeMillis();
        try {
            List<String> tagList = tags == null || tags.isBlank() ? List.of()
                    : Arrays.stream(tags.split(",")).map(String::trim).filter(tag -> !tag.isBlank()).toList();
            SearchResponse response = searchService.search(q, tagList, fromDate, toDate, page, size, userId);
            long duration = System.currentTimeMillis() - startTime;

            logger.info("Search completed successfully - query: '{}', results: {}, duration: {}ms",
                       q, response, duration);

            return response;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Search failed - query: '{}', userId: {}, duration: {}ms, error: {}",
                        q, userId, duration, e.getMessage(), e);
            throw e;
        }
    }
}


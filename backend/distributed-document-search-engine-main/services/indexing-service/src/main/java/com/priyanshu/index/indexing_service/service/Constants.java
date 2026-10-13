package com.priyanshu.index.indexing_service.service;

public class Constants {
    public static final String INDEX_NAME = "documents_index_v4";
    public static final String PREVIOUS_INDEX_NAME = "documents_index_v3";

    public static final String INDEX_MAPPING_JSON = """
                    {
                    "settings": {
                        "analysis": {
                        "filter": {
                            "edge_ngram_filter": {
                            "type": "edge_ngram",
                            "min_gram": 1,
                            "max_gram": 20
                            }
                        },
                        "analyzer": {
                            "autocomplete_analyzer": {
                            "type": "custom",
                            "tokenizer": "standard",
                            "filter": ["lowercase", "edge_ngram_filter"]
                            },
                            "search_analyzer": {
                            "type": "standard"
                            }
                        }
                        }
                    },
                    "mappings": {
                        "properties": {
                        "documentId": { "type": "keyword" },
                        "ownerId": { "type": "keyword" },
                        "title": {
                            "type": "text",
                            "analyzer": "autocomplete_analyzer",
                            "search_analyzer": "search_analyzer"
                        },
                        "description": {
                            "type": "text",
                            "analyzer": "autocomplete_analyzer",
                            "search_analyzer": "search_analyzer"
                        },
                        "content": {
                            "type": "text",
                            "analyzer": "standard"
                        },
                        "fileSize": { "type": "long" },
                        "status": { "type": "keyword" },
                        "tags": { "type": "keyword" },
                        "aiSummary": { "type": "text", "analyzer": "standard" },
                        "aiTags": { "type": "keyword" },
                        "aiStatus": { "type": "keyword" },
                        "aiMessage": { "type": "text", "index": false },
                        "createdAt": { "type": "date" }
                        }
                    }
                    }
                    """;

}

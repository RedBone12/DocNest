# Elasticsearch Index Mapping

This document defines the Elasticsearch index structure used for full-text search and metadata filtering in the Distributed Document Search system.

---
## documents_index
---

## Mapping definition

```json
{
    "settings":{
        "analysis":{
            "analyzer":{
                "custom_text_analyzer":{
                    "type": "standard",
                    "stopwords": "_english_"
                }
            }
        }
    },
    "mappings":{
        "properties":{
            "documentId":{
                "type": "keyword"
            },
            "ownerId": {
                "type": "keyword"
            },
            "title": {
                "type": "text",
                "analyzer": "custom_text_analyzer"
            },
            "description": {
                "type": "text",
                "analyzer": "custom_text_analyzer"
            },
            "content": {
                "type": "text",
                "analyzer": "custom_text_analyzer"
            },
            "tags": {
                "type": "keyword"
            },
            "createdAt": {
                "type": "date"
            }
        }
    }
}
```

---
## Field Explanation

| Field            | Type        |Explantion                               |
|------------------|-------------|------------------------------------------|
| documentId       | keyword     | Unique identifier for document           |
| ownerId          | keyword     | Used for access control and filtering    |
| title            | text        | Full-text searchable title               |
| description      | text        | Full-text searchable description         |
| content          | text        | Main searchable document content         |
| tags             | keyword     | Metadata filtering                       |
| createdAt        | date        | Sorting and time range queries           |

---
## Search capabilities enabled
- Full-text search on title, description and content
- Metadata filtering by tags and ownerId
- phrase search
- partial matching


# Messaging Design

## Topic: document_upload

key : documentId

value : 
{
    "documentId" : "uuid",
    "ownerId": "uuid",
    "minIOPath": "/bucket/file.pdf",
    "title": "sample",
    "tags": ["techs"]
}

## Consumer

Indexing Service

## Retry Strategy

- 3 retries
- exponential backoff
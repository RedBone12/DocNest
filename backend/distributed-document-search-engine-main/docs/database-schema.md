# Database Schema - Document Service

This document describes the PostgreSQL schema used by Document service to store document metadata and manage indexing lifecycle.

---
## Table : documents

Stores metadata for each uploaded document and its storage location in minIO.

## Schema

```sql
CREATE TABLE documents(
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    tags TEXT[],
    minio_bucket VARCHAR(100) NOT NULL,
    minio_object_key VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    -- UPLOADED | INDEXING | INDEXED | FAILED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)
```
---

## Indexes
Indexes are added to support common query patterns such as filtering by owner, status, tags, and time range.

```sql
CREATE INDEX idx_documents_owner ON documents(owner_id);
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_tags ON documents USING GIN(tags);
CREATE INDEX idx_documents_created_at ON documents(created_at);
```

---
## Field Description

| Column            | Description                                       |
|-------------------|---------------------------------------------------|
| id                | Unique document identifier used across services   |
| owner_id          | User who uploaded the document                    |
| title             | Document title                                    |
| description       | Optional description                              |
| tags              | Metadata tags for filtering                       |
| minio_bucket      | Bucket name where file is stored                  |
| minio_object_key  | Object path inside MinIO                          |
| file_size         | Size of file in bytes                             |
| content_type      | MIME type (pdf, docx, etc.)                       |
| status            | Indexing lifecycle status                         |
| created_at        | Upload timestamp                                  |
| updated_at        | Last update timestamp                             |

---

## Status Lifecycle

UPLOADED -> INDEXING -> INDEXED
                \
                -> FAILED

---

## Design Notes

- PostgreSQL is used as the system of record for metadata.
- Actual document content is stored in MinIO.
- Elasticsearch stores only indexed searchable content.
- Status field allows tracking indexing progress and retries.
- GIN index is used for efficient tag-based filtering.
- UUIDs are used for safe distributed ID generation.


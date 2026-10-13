# API Contracts

## Auth Service

### POST auth/login
Request:
{
    "email" : "user@test.com",
    "password" : "password"
}

Response:
{
    "token" : "jwt-token"
}

---
## Document Service

### POST /documents/upload
Headers:
Authorization: Bearer <token>

Request: multipart/form-data
- file
- title
- tags

Response:
{
    "documentId" : "uuid,
    "status" : "uploaded"
}

---

## Search Service

### GET /search?q=&tags=&fromDate=&toDate=

Response:
{
  "results": [
    {
      "documentId": "uuid",
      "title": "sample",
      "score": 0.98
    }
  ]
}
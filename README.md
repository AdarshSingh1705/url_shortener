# ShortLink API

A URL shortener backend built to demonstrate **caching** and **rate limiting**
patterns on top of a standard Spring Boot + PostgreSQL REST API — not just CRUD.

## Why this exists

Most "backend project" portfolios are CRUD + auth. This one deliberately
isolates two specific systems concepts instead:

- **Cache-aside reads**: short-code lookups hit Redis first; only a cache miss
  touches Postgres, and the result is written back to Redis for next time.
- **Fixed-window rate limiting**: the create-link endpoint is protected per-client
  using a single Redis `INCR` + `EXPIRE`, O(1) per request.

## Tech stack

- Java 17, Spring Boot 3.3
- PostgreSQL (persistent storage) + Redis (cache + rate-limit counters)
- Spring Data JPA, Spring Data Redis
- JUnit 5 + Mockito

## Getting started

**Option A — Docker (recommended for Postgres + Redis only)**
```bash
docker compose up -d
mvn spring-boot:run
```

**Option B — Manual**
Install Postgres and Redis locally, create a database matching your `.env`
(or the `DB_*` defaults in `application.properties`), then:
```bash
mvn clean install
mvn spring-boot:run
```

> This project doesn't ship a Maven wrapper jar binary (it's a binary file
> that has to be downloaded from Maven Central, which isn't always reachable
> in every environment) — use your local `mvn`, or generate one yourself with
> `mvn -N wrapper:wrapper` once you have Maven installed.

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/urls` | Create a short link. Rate-limited (10 req/min/client by default). |
| `GET` | `/{shortCode}` | Redirect to the original URL; records a click. |
| `GET` | `/api/urls/{shortCode}/analytics` | Total clicks, clicks by day, clicks by referrer. |

**Create a short link**
```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://www.autodesk.com/products/fusion-360", "expiresInDays": 30}'
```
```json
{
  "shortCode": "1",
  "shortUrl": "http://localhost:8080/1",
  "longUrl": "https://www.autodesk.com/products/fusion-360",
  "clickCount": 0,
  "createdAt": "2026-09-12T10:00:00",
  "expiresAt": "2026-10-12T10:00:00"
}
```

**Follow it**
```bash
curl -i http://localhost:8080/1
# HTTP/1.1 302 Found
# Location: https://www.autodesk.com/products/fusion-360
```

**Check analytics**
```bash
curl http://localhost:8080/api/urls/1/analytics
```

## Design notes

- **Short codes** are base62-encoded database ids, not random strings — every
  code is unique by construction, so there's no collision-retry loop.
- **Click IPs are never stored**; only a SHA-256 hash is kept, which is enough
  to dedupe/analyze without holding onto anything personally identifying.
- **Cache TTL** defaults to 24h; a link that goes cold naturally falls out of
  Redis and gets re-populated on its next access.

## Testing


```bash
mvn test
```
OR
### cmd 
```aiignore

 Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
 
 .\test.ps1
```

OUTPUT:-
```aiignore
[1] Create a short link
  PASS: POST /api/urls returns 201 (got 201)
  Got shortCode: 1

[2] Reject an invalid longUrl
  PASS: POST with bad longUrl returns 400 (got 400)

[3] Follow the short link
  PASS: GET /1 returns 302 (got 302)
  PASS: redirects to the original URL (https://www.autodesk.com/products/fusion-360)

[4] Check analytics
  PASS: GET analytics returns 200 (got 200)
  Response: {"shortCode":"1","totalClicks":1,"clicksByDay":[{"date":"2026-09-12","count":1}],"clicksByReferrer":[{"referrer":"direct","count":1}]}

[5] Look up a nonexistent short code
  PASS: GET /zzzzzzz returns 404 (got 404)

[6] Rate limit test (default: 10 requests/minute/client)
  request #1 -> 201
  request #2 -> 201
  request #3 -> 201
  request #4 -> 201
  request #5 -> 201
  request #6 -> 201
  request #7 -> 201
  request #8 -> 201
  request #9 -> 429
  request #10 -> 429
  request #11 -> 429
  request #12 -> 429
  PASS: rate limiter kicked in (got a 429) 

=== Summary: 8 passed, 0 failed ===

```

Covers the base62 codec round-trip and the rate limiter's window/expiry logic
(via a mocked Redis template — no live Redis needed to run the test suite).

## Limitations & possible next steps

- No auth/ownership on links yet — anyone can create one, anyone can view its
  analytics. Adding per-user API keys would be the natural next step.
- Fixed-window limiter allows a burst at window boundaries; a sliding-window
  or token-bucket algorithm would tighten that at the cost of more Redis ops.
- No Flyway/Liquibase migrations — schema is managed via
  `spring.jpa.hibernate.ddl-auto=update`, fine for a demo, not for production.
- Click recording happens synchronously in the redirect request; at real
  traffic volumes this would move to an async queue so it can't slow down
  the redirect itself.

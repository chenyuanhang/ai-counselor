# AI Counselor Backend

Java 8 compatible Spring Boot backend for the counselor frontend.

## Stack

- Java 8
- Spring Boot 2.7.18
- MyBatis-Plus
- MySQL
- JWT
- WebClient + SSE proxy

## What It Provides

- `POST /auth/dev-login`: local development login.
- `GET /auth/login`: redirects to the school AuthX/CAS login URL when `app.portal.cas-base-url` is configured.
- `GET /auth/callback`: CAS callback, validates `ticket` with `/serviceValidate`, then redirects to frontend with `token` and `user`.
- `GET /auth/logout`: clears the frontend token and redirects through the school AuthX/CAS logout URL.
- `GET /auth/slo`: JSONP-compatible single logout endpoint for the AuthX application configuration.
- `GET /api/user/me`: current user.
- `POST /api/conversations`: create conversation.
- `GET /api/conversations`: list current user's conversations.
- `GET /api/messages?conversationId=...`: list current user's messages in a conversation.
- `POST /api/chat/files`: upload and extract up to 10 files for the next chat round. Multipart field: `files`.
- `POST /api/chat/stream`: proxy upstream AI SSE and save both user and assistant messages.

## Initialize Database

```bash
mysql -u root -p < src/main/resources/schema.sql
```

## Build

```bash
mvn clean package -DskipTests
```

The Docker image expects this jar to exist:

```text
target/ai-counselor-backend-1.0.0.jar
```

## Profiles

Configuration files:

```text
src/main/resources/application.yml
src/main/resources/application-local.yml
src/main/resources/application-online.yml
```

`application-local.yml` and `application-online.yml` are intentionally not tracked because they contain machine-specific or production configuration. Create them from your deployment environment and provide these values:

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `app.jwt.secret`
- `app.agent.base-url`
- `app.agent.agent-id`
- `app.portal.app-id`
- `app.portal.cas-base-url`, or OAuth2/OIDC fields
- `app.portal.redirect-uri`

## Run Local

```bash
java -jar target/ai-counselor-backend-1.0.0.jar --spring.profiles.active=local
```

## Docker

Build the jar first:

```bash
mvn clean package -DskipTests
```

Build the image:

```bash
docker build -t ai-counselor-backend:1.0.0 .
```

Run with environment-specific configuration mounted or supplied through environment variables:

```bash
docker run -d \
  --name ai-counselor-backend \
  --restart always \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e TZ=Asia/Shanghai \
  ai-counselor-backend:1.0.0
```

If you use Docker Compose in production, keep the compose file local because it usually contains server-specific settings.

Check logs:

```bash
docker logs -f ai-counselor-backend
```

Check health:

```bash
curl -i http://127.0.0.1:8080/health
```

## Frontend Proxy

The current Vite frontend expects:

- `/counselor/api/*` -> backend `/api/*`
- `/counselor/auth/*` -> backend `/auth/*`

OpenResty should proxy these prefixes to this backend service, not directly to the upstream AI service.

Example:

```nginx
location /counselor/api/ {
    proxy_pass http://127.0.0.1:8080/api/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 3600s;
}

location /counselor/auth/ {
    proxy_pass http://127.0.0.1:8080/auth/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
}
```

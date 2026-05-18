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
- `GET /auth/login`: redirects to the school portal OIDC login URL.
- `GET /auth/callback`: OIDC callback, redirects to frontend with `token` and `user`.
- `GET /api/user/me`: current user.
- `POST /api/conversations`: create conversation.
- `GET /api/conversations`: list current user's conversations.
- `GET /api/messages?conversationId=...`: list current user's messages in a conversation.
- `POST /api/chat/stream`: proxy upstream AI SSE and save both user and assistant messages.

## Initialize Database

```bash
mysql -u root -p < src/main/resources/schema.sql
```

## Build

```bash
mvn clean package -DskipTests
```

## Run

```bash
java -jar target/ai-counselor-backend-1.0.0.jar
```

Production examples:

```bash
export DB_URL='jdbc:mysql://127.0.0.1:3306/ai_counselor?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true'
export DB_USERNAME='root'
export DB_PASSWORD='your_password'
export JWT_SECRET='replace-with-a-long-random-secret-at-least-32-bytes'
export AGENT_BASE_URL='http://10.66.6.226:9022/af-api'
export AGENT_ID='2036332866958286848'
java -jar target/ai-counselor-backend-1.0.0.jar
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

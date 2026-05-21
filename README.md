# AI Counselor

AI Counselor is a full-stack counselor assistant project. The repository is organized as a standard frontend and backend workspace:

- `frontend/`: React + Vite chat frontend.
- `backend/`: Spring Boot backend for authentication, conversation history, and AI SSE proxy.

## Frontend

```bash
cd frontend
npm install
npm run dev
```

Build:

```bash
cd frontend
npm run build
```

Copy `frontend/.env.example` to `frontend/.env` when local environment overrides are needed.

## Backend

```bash
cd backend
mvn clean package -DskipTests
java -jar target/ai-counselor-backend-1.0.0.jar
```

Initialize the database before running the backend:

```bash
cd backend
mysql -u root -p < src/main/resources/schema.sql
```

The backend reads database, JWT, upstream AI service, and portal login settings from environment variables. See `backend/README.md` for the main configuration examples.

Existing databases created before file attachments need one extra column:

```sql
ALTER TABLE counselor_message ADD COLUMN attachments TEXT NULL AFTER content;
```

## Deployment Prefix

The frontend is configured for the `/counselor/` base path. The default frontend API prefixes are:

- `/counselor/api/*` -> backend `/api/*`
- `/counselor/auth/*` -> backend `/auth/*`

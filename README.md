# SmartLend — AI-Powered Loan Risk Assessment System

SmartLend is a backend REST API for managing loan applications end to end. Customers can register, submit loan applications, and track status. Loan officers review applications using an AI-generated risk score. Admins have access to system stats and full audit logs.

Built with Java 17 and Spring Boot 3. Uses RSA-signed JWT auth, Redis for caching and token blacklisting, and Spring AI for loan risk scoring via Groq LLM.

> **Live Demo** → https://smartlend.up.railway.app/api/swagger-ui.html

---

## What it does

- Customer registers and logs in using JWT (RSA-signed, not HMAC)
- Customer submits a loan application — amount, income, employment type, credit score
- AI automatically scores the risk as LOW / MEDIUM / HIGH using an LLM
- Loan officer sees all applications, reviews the AI score, and approves or rejects
- Every action is logged automatically to an audit table using Spring AOP
- Admin can view system-wide stats and the complete audit trail
- Tokens are blacklisted in Redis on logout so old tokens stop working immediately
- Rate limiting on sensitive endpoints using Bucket4j

---

## Tech stack

| Layer      | Tech |
|------------|--------------------------------|
| Language   | Java 17 |
| Framework  | Spring Boot 3.2.3 |
| Security   | Spring Security 6 + RSA JWT |
| ORM        | Spring Data JPA + Hibernate |
| Database   | PostgreSQL 16 |
| Cache      | Redis 7 |
| AI         | Spring AI + Groq LLM API |
| Docs       | Springdoc OpenAPI 3 / Swagger UI |
| Monitoring | Spring Actuator |
| Containers | Docker + Docker Compose |
| CI/CD      | GitHub Actions |
| Hosting    | Railway |

---

## Project structure

```
src/main/java/com/smartlend/
├── ai/              AI risk scoring and chat
├── audit/           AOP-based automatic audit logging
├── config/          Security, Redis, Swagger config
├── controller/      REST endpoints
├── exception/       Global exception handler
├── model/
│   ├── dto/         Request and response objects
│   ├── entity/      JPA entities
│   └── enums/       Role, LoanStatus, RiskLevel
├── ratelimit/       Bucket4j rate limiter
├── repository/      Spring Data JPA repositories
├── security/        JWT service, filter, RSA key loading
└── service/         Business logic
```

---

## Getting started

### Prerequisites

- Java 17
- Maven 3.8+
- Docker and Docker Compose (for running Postgres + Redis locally)
- A Groq API key — free at https://console.groq.com

### 1. Clone the repo

```bash
git clone hhttps://github.com/movvachandrika691/smartlend.git
cd smartlend
```

### 2. Generate RSA keys

```bash
mkdir -p src/main/resources/keys

openssl genrsa -out src/main/resources/keys/private_key.pem 2048
openssl rsa -in src/main/resources/keys/private_key.pem -pubout -out src/main/resources/keys/public_key.pem
```

These keys are gitignored and stay local. Never commit them.

### 3. Set your environment variables

```bash
export OPENAI_API_KEY=your_groq_api_key_here
```

The app uses `application-dev.yml` for local development. Postgres and Redis connection defaults are already set there so you don't need to configure anything else locally.

### 4. Start Postgres and Redis

```bash
docker-compose up -d postgres redis
```

### 5. Run the app

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 6. Open Swagger

```
http://localhost:8080/api/swagger-ui.html
```

---

## Configuration files

There are three yml files in `src/main/resources/`:

- `application.yml` — base config, no secrets, safe to commit
- `application-dev.yml` — local dev overrides (Postgres password, Redis host, verbose logging) — gitignored
- `application-prod.yml` — production overrides (Railway env vars, strict logging, schema validation) — gitignored

For local dev, only `application-dev.yml` matters after the base. For production, all values are injected as environment variables via Railway dashboard.

---

## Local URLs

| Service      | URL                                       |
|--------------|-------------------------------------------|
| API Base     | http://localhost:8080/api                 |
| Swagger UI   | http://localhost:8080/api/swagger-ui.html |
| Health Check | http://localhost:8080/api/actuator/health |

## Production URLs

| Service      | URL                                                  |
|--------------|------------------------------------------------------|
| API Base     | https://smartlend.up.railway.app/api                 |
| Swagger UI   | https://smartlend.up.railway.app/api/swagger-ui.html |
| Health Check | https://smartlend.up.railway.app/api/actuator/health |

---

## API overview

### Auth
| Method | Endpoint       | Access        |
|--------|----------------|---------------|
| POST   | /auth/register | Public        |
| POST   | /auth/login    | Public        |
| POST   | /auth/refresh  | Public        |
| POST   | /auth/logout   | Authenticated |

### Loans
| Method | Endpoint | Access |
|---|---|---|
| POST | /loans | CUSTOMER |
| GET | /loans/my | CUSTOMER |
| GET | /loans/{id} | All roles |
| GET | /loans | LOAN_OFFICER, ADMIN |
| PATCH | /loans/{id}/approve | LOAN_OFFICER |
| PATCH | /loans/{id}/reject | LOAN_OFFICER |

### AI
| Method | Endpoint | Access |
|---|---|---|
| POST | /ai/chat | Authenticated |

### Admin
| Method | Endpoint          | Access |
|--------|-------------------|--------|
| GET    | /admin/statistics | ADMIN  |
| GET    | /admin/audit-logs | ADMIN  |

---

## How to test using Swagger

1. Open http://localhost:8080/api/swagger-ui.html
2. Call `POST /auth/register` to create an account
3. Call `POST /auth/login` — copy the `accessToken` from response
4. Click the **Authorize** button at the top right
5. Paste just the token value — no need to type Bearer, Swagger adds it
6. Now all protected endpoints are unlocked for testing

To test role-based access, register separate accounts with `CUSTOMER`, `LOAN_OFFICER`, and `ADMIN` roles and switch tokens between them.

---

## Test accounts (for quick testing)

You can register these through the API or insert directly in the DB:

| Role         | Email             | Password  |
|--------------|-------------------|-----------|
| CUSTOMER     | customer@test.com | Test@1234 |
| LOAN_OFFICER | officer@test.com  | Test@1234 |
| ADMIN        | admin@test.com    | Test@1234 |

Note: The register API accepts any role in the request body for testing purposes. In a real production app, ADMIN and LOAN_OFFICER accounts would typically be created separately by a super admin.

---

## A few design decisions worth knowing

**RSA keys instead of a shared secret for JWT** — with a shared secret, any service that needs to verify tokens also needs the secret. RSA lets other services verify tokens using only the public key without ever seeing the private key. More secure for multi-service setups.

**Token blacklisting in Redis on logout** — JWT is stateless by default, which means there's no way to invalidate a token early. Storing the token ID in Redis with a TTL matching the token's remaining lifetime solves this without sessions.

**AOP for audit logging** — putting audit log calls inside every service method creates clutter and can be forgotten. AOP intercepts at the framework level so logging happens automatically for every annotated method regardless.

**Soft delete everywhere** — nothing is hard deleted. A `deleted_at` timestamp marks records as inactive. This is standard practice in financial applications where you might need to recover or audit historical data.

**AI fallback on failure** — if the Groq API is down or slow, loan submission still succeeds. The risk level defaults to MEDIUM and a note is saved that AI scoring was unavailable. Applications don't get blocked because of an external API issue.

---

## Running with Docker (full stack)

To run everything including the app in Docker:

```bash
# Make sure your .env file has DB_PASSWORD and OPENAI_API_KEY set
docker-compose up --build
```

The compose file starts Postgres, Redis, and the Spring Boot app together with health checks.

---

## CI/CD

GitHub Actions pipeline runs on every push to `main`:

1. Build with Maven and run tests
2. Build Docker image
3. Push image to Docker Hub
4. Deploy to Railway automatically

---

## License

MIT — use it however you want.
# E-commerce Backend API

A portfolio-grade e-commerce REST API built as a **modular monolith** with Java 21, Spring Boot 4.1.3 and PostgreSQL 17.

> Current scope: customer authentication and product catalogue. Shopping cart, checkout, order processing, inventory reservation and payment simulation are tracked in [`docs/roadmap.md`](docs/roadmap.md); they are not implemented yet.

## Technology

- Java 21
- Spring Boot 4.1.3 / Spring MVC
- Spring Security 7 / JWT (HS256)
- Spring Data JPA / Hibernate
- PostgreSQL 17 / Flyway
- Bean Validation
- Springdoc OpenAPI / Swagger UI
- Maven, Docker, Docker Compose, GitHub Actions
- JUnit 5 and Mockito

## Implemented features

### Authentication and users

- Customer registration and login
- BCrypt password hashing (work factor 12)
- 15-minute JWT access tokens
- Role-based authorization (`CUSTOMER`, `ADMIN`)
- `GET /api/v1/users/me` for the authenticated principal
- Registration cannot assign the `ADMIN` role

### Product catalogue

- Public product listing with text search and pagination
- Public product details
- Admin-only create, update and deactivate operations
- SKU uniqueness and validation
- Positive price and non-negative stock constraints
- Soft deactivation instead of destructive deletion

## Run with Docker Compose

Prerequisites: Docker Engine / Docker Desktop with Compose v2.

1. Copy `.env.example` to `.env`.
2. Set a strong local `DB_PASSWORD`.
3. Generate a 32-byte JWT secret and paste its Base64 output into `JWT_SECRET_BASE64`:

   ```bash
   openssl rand -base64 32
   ```

4. To manage the catalogue, enable the bootstrap admin and set a unique email and password (at least 12 characters):

   ```dotenv
   BOOTSTRAP_ADMIN_ENABLED=true
   BOOTSTRAP_ADMIN_EMAIL=admin@example.com
   BOOTSTRAP_ADMIN_PASSWORD=replace-with-a-long-unique-password
   BOOTSTRAP_ADMIN_FULL_NAME=Store Administrator
   ```

5. Start the services:

   ```bash
   docker compose up --build -d
   ```

6. Open Swagger UI: <http://localhost:8080/swagger-ui.html>

The API listens on port `8080`; PostgreSQL is exposed to the host on port `5433`. The database uses a named Docker volume. **Do not run `docker compose down -v` if you need to preserve local database data.**

To stop without deleting data:

```bash
docker compose down
```

## Run tests

With Java 21 and Maven installed:

```bash
mvn clean verify
```

The initial test suite covers core authentication and product-catalogue service behavior. Database integration and concurrency tests are scheduled for later iterations.

## API overview

| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public | Register a customer |
| `POST` | `/api/v1/auth/login` | Public | Get a JWT access token |
| `GET` | `/api/v1/users/me` | Authenticated | Current user and roles |
| `GET` | `/api/v1/products` | Public | Search / paginate active products |
| `GET` | `/api/v1/products/{id}` | Public | Get active product details |
| `POST` | `/api/v1/admin/products` | Admin | Create a product |
| `PUT` | `/api/v1/admin/products/{id}` | Admin | Update a product |
| `DELETE` | `/api/v1/admin/products/{id}` | Admin | Deactivate a product |

### Example: register

```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "buyer@example.com",
  "password": "a-unique-password-12",
  "fullName": "Demo Buyer"
}
```

### Example: create a product

```http
POST /api/v1/admin/products
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "sku": "KB-100",
  "name": "Mechanical Keyboard",
  "description": "Hot-swappable mechanical keyboard",
  "category": "Accessories",
  "price": 79.90,
  "stock": 25
}
```

Use the admin account created by the optional bootstrap process to call admin endpoints.

## Database migrations

Flyway applies versioned SQL migrations from `src/main/resources/db/migration`. Hibernate is configured with `ddl-auto=validate`; schema changes must be committed as new migrations. Do not edit an already-applied migration in a shared database.

## Security notes

- Replace all local passwords and JWT signing material before exposing the service beyond localhost.
- Never commit `.env` or real secrets.
- Admin bootstrap is disabled by default and never resets an existing account password.
- The payment module will be a simulated provider; this project will not collect or store real card data.

## Project structure

```text
src/main/java/com/sardor/ecommerce/
├── auth/       # registration and login
├── common/     # API errors and domain exceptions
├── config/     # OpenAPI and optional bootstrap admin
├── product/    # public catalogue and admin product management
├── security/   # stateless JWT authentication and authorization
└── user/       # customer/admin identity
```

## Architecture and roadmap

- [Architecture and invariants](docs/architecture.md)
- [Implementation roadmap](docs/roadmap.md)

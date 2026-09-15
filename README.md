# Compliance Automation Platform

A multi-tenant, role-based compliance management backend built with Spring Boot 4.
Organizations register on the platform, define compliance requirements, assign them
to employees, track completion through a structured approval workflow, and store
supporting documents on AWS S3 — with automated email reminders firing at critical
deadlines and full JWT refresh-token support for seamless session management.

---

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Module Breakdown](#module-breakdown)
- [Role Model and Access Control](#role-model-and-access-control)
- [API Reference](#api-reference)
- [Database Design](#database-design)
- [Security Implementation](#security-implementation)
- [Automated Scheduler](#automated-scheduler)
- [Document Storage](#document-storage)
- [Audit Trail](#audit-trail)
- [Notification System](#notification-system)
- [Exception Handling](#exception-handling)
- [Configuration and Environment](#configuration-and-environment)
- [Running Locally](#running-locally)
- [Docker](#docker)

---

## Overview

The platform models the real structure of an organization: a Company owns multiple
Departments, each Department has a manager and employees under them. Compliance tasks
are created by Compliance Managers or Owners, scoped to a company, and pushed down to
employees via assignments. The assignment lifecycle —
`PENDING → IN_PROGRESS → COMPLETED → VERIFIED` — is enforced at the service layer
with role checks on every state transition. Department Managers verify completed work.
An Auditor role has read-only visibility across all compliances in their company.

Every meaningful action is captured in an immutable audit log via a custom AOP
annotation. Email notifications are sent on assignment, on status changes, at the
3-day and 1-day deadlines, and when a task goes overdue — driven by a cron scheduler
that runs daily at 9 AM. In-app notifications are also persisted alongside every email
so users have a permanent record even if the email was missed.

Authentication uses short-lived JWT access tokens (24h) paired with long-lived refresh
tokens (7 days) that rotate on every use, ensuring a stolen refresh token can only be
used once before it is invalidated.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.0.6 |
| Security | Spring Security 6, JWT (jjwt 0.12.6) |
| Persistence | Spring Data JPA, Hibernate 7 |
| Database | PostgreSQL 18 |
| Migrations | Flyway |
| Object Storage | AWS S3 (AWS SDK v2 — software.amazon.awssdk 2.31.72) |
| Email | Spring Mail (JavaMailSender, SMTP/Gmail) |
| API Documentation | SpringDoc OpenAPI 3.0.3 (Swagger UI) |
| Build | Maven |
| Utilities | Lombok, Spring AOP |
| Async | Spring `@Async` with custom thread pool executor |
| Scheduling | Spring `@Scheduled` (cron) |

---

## Architecture

The project follows a domain-driven package structure. Each bounded context
(compliance, assignment, document, audit, etc.) is a self-contained module with its
own controller, service, repository, entity, and DTO layers.

```
complianceautomationplatform/
├── auth/               # Registration, login, refresh, logout
├── security/           # JWT filter, JwtService, SecurityConfig, RevokedToken, RefreshToken
├── user/               # User CRUD, team management
├── company/            # Company entity and repository
├── department/         # Department CRUD
├── role/               # Role constants and entity
├── compliance/         # Compliance lifecycle (CRUD, status rollup, scoping)
├── assignment/         # Assign compliances to employees, verify submissions
├── document/           # S3 upload/download for compliance evidence
├── audit/              # @Audit annotation + AOP aspect + log persistence
├── notification/       # EmailService, HTML email templates, Notification persistence
├── scheduler/          # ComplianceReminderScheduler (daily cron)
├── dashboard/          # Role-scoped dashboard aggregations
├── exception/          # GlobalExceptionHandler, custom exceptions
└── config/             # AsyncConfig, SchedulerConfig, OpenApiConfig, DataInitializer
```

**Request flow:**

```
Client → JwtAuthenticationFilter → SecurityFilterChain
       → Controller (@PreAuthorize role check)
       → Service (business logic + company-scoping)
       → Repository (Spring Data JPA → PostgreSQL)
       → AuditAspect (@AfterReturning on @Audit-annotated methods)
       → EmailService (@Async — non-blocking) + NotificationRepository (synchronous)
```

---

## Module Breakdown

### Auth

Handles registration, login, token refresh, and logout. On registration, a Company and
its OWNER user are created atomically in a single transaction. Login returns both a
short-lived JWT access token and a long-lived refresh token. The refresh endpoint
rotates the refresh token on every call — the old token is deleted and a new one
issued, so a stolen refresh token can only be used once. Logout revokes the access
token's JTI and deletes all refresh tokens for the user.

### Security

`JwtAuthenticationFilter` intercepts every request, validates the JWT, checks the JTI
against the `revoked_tokens` table, and populates the `SecurityContext`. A separate
`refresh_tokens` table stores active refresh tokens keyed by user — only one refresh
token per user exists at any time. `SecurityConfig` is stateless (no session), CSRF
disabled, with all `/api/auth/**` and Swagger endpoints public. Method-level security
is enabled via `@EnableMethodSecurity`.

### Compliance

Compliance records are always scoped to a company — a user from Company A cannot see
or modify compliances belonging to Company B. The overall compliance status is a
**derived rollup** computed from its assignments at read time — it is not stored on
the `compliances` table. A compliance is only marked `VERIFIED` when every single
assignment for that compliance has been verified by a manager. Status values:
`PENDING`, `IN_PROGRESS`, `COMPLETED`, `VERIFIED`.

### Assignment

A Compliance Manager or Owner assigns a compliance to a specific employee. A
database-level unique constraint on `(compliance_id, assigned_to)` prevents the same
employee from being assigned to the same compliance twice. Employees can update their
own assignment to `IN_PROGRESS` or `COMPLETED` only — self-verification to `VERIFIED`
is blocked. Department Managers can verify any assignment for an employee in their own
department, regardless of who created the assignment.

### Document

Employees upload evidence files (up to 10 MB) against a specific compliance. Files are
stored in AWS S3. Downloads return a pre-signed URL generated on demand rather than
proxying file bytes through the application server. Deleting a compliance cascades to
delete its S3 objects first, then document rows, then assignment rows, preventing
orphaned storage.

### Notification

Every email is also persisted as a `Notification` row in the database, giving users a
permanent in-app trail. `EmailService` sends HTML emails asynchronously via
`@Async`. All dynamic values (employee names, compliance titles) are HTML-escaped
before insertion into email templates, preventing HTML injection in email bodies.

### Dashboard

Three role-specific views: Owner sees company-wide aggregated compliance stats using
SQL rollup queries computed directly from assignments. Department Manager sees their
department's stats and team member performance. Employee sees their own tasks and
completion rate. All counts are computed from `compliance_assignments.status`, not
from the deprecated (and now dropped) `compliances.status` column.

---

## Role Model and Access Control

Five roles are seeded into the database at startup via `DataInitializer`.

| Role | Description |
|---|---|
| `OWNER` | Registered via `/api/auth/register`. Full company-wide access. |
| `COMPLIANCE_MANAGER` | Created by Owner. Manages compliance records. |
| `DEPARTMENT_MANAGER` | Created by Owner. Manages employees, assigns and verifies compliances. |
| `EMPLOYEE` | Receives assignments, updates status, uploads evidence documents. |
| `AUDITOR` | Read-only access to all compliance records in the company. |

---

## API Reference

### Auth — `/api/auth`

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/register` | Public | Register a new OWNER and company |
| POST | `/login` | Public | Authenticate — returns `accessToken` + `refreshToken` |
| POST | `/refresh` | Public | Exchange a refresh token for a new token pair |
| POST | `/logout` | Authenticated | Revoke access token and all refresh tokens |

### Users — `/api/users`

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/department-managers` | OWNER | Create a department manager |
| POST | `/employees` | OWNER, DEPARTMENT_MANAGER | Create an employee |
| GET | `/` | Authenticated | List users in the company |
| GET | `/{id}` | Authenticated | Get user by ID |
| GET | `/my-team` | DEPARTMENT_MANAGER | List employees under this manager |

### Compliances — `/api/compliances`

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/` | OWNER, COMPLIANCE_MANAGER | Create a compliance task |
| GET | `/` | OWNER, COMPLIANCE_MANAGER, AUDITOR | List all company compliances |
| GET | `/{id}` | OWNER, COMPLIANCE_MANAGER, AUDITOR | Get compliance by ID |
| PUT | `/{id}` | OWNER, COMPLIANCE_MANAGER | Update compliance |
| DELETE | `/{id}` | OWNER, COMPLIANCE_MANAGER | Delete compliance (cascades to assignments, documents, S3) |
| GET | `/my-department` | DEPARTMENT_MANAGER | Compliances scoped to own department |
| GET | `/my-compliances` | EMPLOYEE | Compliances assigned to self |

### Assignments — `/api/assignments`

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/` | OWNER, DEPARTMENT_MANAGER | Assign a compliance to an employee |
| GET | `/my-tasks` | EMPLOYEE | Get own assignment list |
| PATCH | `/{id}/status` | EMPLOYEE | Update status (`IN_PROGRESS` or `COMPLETED` only) |
| PUT | `/{assignmentId}/verify` | DEPARTMENT_MANAGER | Verify a completed assignment |

### Documents — `/api/documents`

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/upload/{complianceId}` | Authenticated | Upload evidence file (multipart) |
| GET | `/compliance/{complianceId}` | Authenticated | List documents for a compliance |
| GET | `/download/{documentId}` | Authenticated | Get pre-signed S3 download URL |

### Dashboard — `/api/dashboard`

| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/owner` | OWNER | Company-wide compliance summary |
| GET | `/manager` | DEPARTMENT_MANAGER | Department-level summary |
| GET | `/manager/team` | DEPARTMENT_MANAGER | Per-member performance breakdown |
| GET | `/employee` | EMPLOYEE | Personal task summary |

### Departments — `/api/departments`

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/` | OWNER | Create a department |
| GET | `/` | Authenticated | List departments in the company |

### Audit Logs — `/api/audit`

| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/` | OWNER, AUDITOR | Retrieve audit log for the company |

---

## Database Design

Thirteen tables managed by Flyway migrations V3 through V13.

### Tables

**companies** — `id`, `name`, `email UNIQUE`, `created_at`

**roles** — `id`, `name UNIQUE`

**departments** — `id`, `name`, `company_id FK`, `created_at`

**users** — `id`, `name`, `email UNIQUE`, `password`, `enabled`, `company_id FK`,
`role_id FK`, `department_id FK`, `manager_id FK (self-ref)`, `created_at`

**compliances** — `id`, `title`, `description`, `due_date`, `frequency`,
`company_id FK`, `created_by FK`, `department_id FK`, `created_at`
*(Note: `status` column was dropped in V10 — status is now derived from assignments)*

**compliance_assignments** — `id`, `compliance_id FK`, `assigned_to FK`,
`assigned_by FK`, `status`, `remarks`, `assigned_at`, `completed_at`,
`verified_at`, `verified_by FK`, `reminder_3day_sent`, `reminder_1day_sent`,
`overdue_reminder_sent`
*(Unique constraint on `(compliance_id, assigned_to)` — no duplicate assignments)*

**compliance_documents** — `id`, `file_name`, `file_type`, `file_size`, `s3_key`,
`document_url`, `uploaded_at`, `compliance_id FK`, `uploaded_by FK`

**notifications** — `id`, `user_id FK`, `title`, `message`, `is_read`, `created_at`

**audit_logs** — `id`, `action`, `entity_type`, `entity_id`, `performed_by`,
`performed_at`, `details`

**revoked_tokens** — `id`, `token_jti UNIQUE`, `revoked_at`, `user_email`, `expires_at`

**refresh_tokens** — `id`, `token UNIQUE`, `user_id FK`, `expires_at`, `created_at`

### Key Relationships

- `companies` → `departments`, `users`, `compliances` (one-to-many)
- `users` → self (manager hierarchy via `manager_id`)
- `compliances` → `compliance_assignments`, `compliance_documents` (one-to-many)
- `users` → `compliance_assignments` (as both assignee and assigner)

---

## Security Implementation

**Two-token auth** — Login returns a short-lived JWT access token (default 24h) and a
long-lived refresh token (default 7 days). The refresh token is a random UUID stored
in the `refresh_tokens` table, not a JWT — it can be revoked instantly with a row
delete.

**Token rotation** — Every call to `POST /api/auth/refresh` deletes the old refresh
token and issues a new one. A stolen refresh token can only be used once.

**Single active session** — Only one refresh token per user exists at any time. Logging
in from a new device automatically revokes the previous session's refresh token.

**Access token revocation** — On logout, the access token's JTI is written to
`revoked_tokens`. `JwtAuthenticationFilter` checks this table on every request.

**Company isolation** — Service methods extract the acting user's company from the
`SecurityContext` and use it as a filter on all queries, preventing cross-tenant
data access.

**Stateless session** — `SessionCreationPolicy.STATELESS`; no `HttpSession` created.

---

## Automated Scheduler

`ComplianceReminderScheduler` runs on a `0 0 9 * * *` cron (daily at 9:00 AM). It
fetches only assignments due within the next 3 days or already overdue (using a
windowed SQL query rather than loading all assignments), with `JOIN FETCH` to avoid
N+1 queries on the employee and compliance associations.

- **3 days remaining** → reminder email + sets `reminder3DaySent = true`
- **1 day remaining** → reminder email + sets `reminder1DaySent = true`
- **Overdue** → overdue alert + sets `overdueReminderSent = true`

`assignmentRepository.save()` is called only when at least one flag was actually
changed — assignments outside all three windows are not written to the database.

---

## Document Storage

File uploads go through `DocumentService` which validates the file, generates a UUID
S3 key, uploads via `PutObjectRequest`, and persists a `ComplianceDocument` record.
Downloads return a pre-signed URL via `GetObjectPresignRequest`.

Deleting a compliance runs S3 deletion first (before any database changes) — if S3
fails, nothing in the database is touched and the delete can be retried. If S3
succeeds, document rows, assignment rows, and the compliance itself are deleted in
order.

---

## Audit Trail

A custom `@Audit` annotation applied to service methods triggers `AuditAspect` via
Spring AOP after successful return. For methods that also call `auditLogService.log()`
manually (where the entity ID is available), the `@Audit` annotation is intentionally
omitted to avoid duplicate entries. The `audit_logs` table is append-only.

---

## Notification System

Every email send is accompanied by a synchronous `Notification` row insert, giving
users a persistent in-app trail. `EmailService` accepts `User` entities (not email
strings) so no extra DB lookup is needed to write the notification. Email sending is
`@Async` and non-blocking; notification writes are synchronous and participate in the
caller's transaction.

All user-controlled values (names, compliance titles) are HTML-escaped via Spring's
`HtmlUtils.htmlEscape` before insertion into email templates.

---

## Exception Handling

`GlobalExceptionHandler` maps all exceptions to structured `ErrorResponse` JSON:

| Exception | HTTP Status |
|---|---|
| `ResourceNotFoundException` | 404 |
| `DuplicateResourceException` | 409 |
| `DataIntegrityViolationException` | 409 |
| `BusinessException` | 400 |
| `MethodArgumentNotValidException` | 400 (field-level errors) |
| Unhandled `Exception` | 500 |

---

## Configuration and Environment

All secrets are externalized as environment variables. No credentials are hardcoded.

| Variable | Purpose |
|---|---|
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | HMAC signing key (minimum 32 characters) |
| `JWT_EXPIRATION` | Access token lifetime in ms (e.g. `86400000` = 24h) |
| `JWT_REFRESH_EXPIRATION` | Refresh token lifetime in ms (default `604800000` = 7 days) |
| `AWS_REGION` | AWS region for S3 |
| `AWS_BUCKET_NAME` | S3 bucket name |
| `AWS_ACCESS_KEY` | AWS access key ID |
| `AWS_SECRET_KEY` | AWS secret access key |
| `MAIL_USERNAME` | Gmail SMTP username |
| `MAIL_PASSWORD` | Gmail app password |
| `SPRING_PROFILES_ACTIVE` | Active profile (`local`, `prod`) |

Config files:

| File | Purpose |
|---|---|
| `application.yml` | Shared base config across all profiles |
| `application-local.yml` | Local dev overrides — **gitignored, never commit** |
| `application-prod.yml` | Production overrides (Swagger disabled, tighter logging) |

---

## Running Locally

**Prerequisites:** Java 17, Maven, PostgreSQL

```bash
# Clone
git clone https://github.com/AbhiramAbbireddy/compliance-automation-platform
cd complianceautomationplatform

# Create the database
psql -U postgres -c "CREATE DATABASE compliance_db;"

# Copy and fill in the local config
cp src/main/resources/application-local.yml.example \
   src/main/resources/application-local.yml
# Edit application-local.yml with your DB credentials, JWT secret, AWS keys, and Gmail app password

# Run with local profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Flyway applies all migrations (V3–V13) automatically on first startup. `DataInitializer`
seeds the five roles. Register an OWNER account first via
`POST /api/auth/register` — all other users are created from that account.

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

---

## Docker

```bash
# Build the image
docker build -t compliance-platform .

# Run with environment variables
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/compliance_db \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=yourpassword \
  -e JWT_SECRET=your-secret-at-least-32-characters \
  -e JWT_EXPIRATION=86400000 \
  -e AWS_REGION=ap-south-1 \
  -e AWS_BUCKET_NAME=your-bucket \
  -e AWS_ACCESS_KEY=your-key \
  -e AWS_SECRET_KEY=your-secret \
  -e MAIL_USERNAME=you@gmail.com \
  -e MAIL_PASSWORD=your-app-password \
  -e SPRING_PROFILES_ACTIVE=prod \
  compliance-platform
```

The Dockerfile uses a two-stage build — the builder stage compiles the fat JAR with
Maven, the runtime stage runs a minimal JRE Alpine image as a non-root user. JVM flags
are tuned for containerized environments:

- `-XX:+UseContainerSupport` — respects cgroup CPU and memory limits
- `-XX:MaxRAMPercentage=75.0` — allocates 75% of container RAM to the heap
- `-XX:+ExitOnOutOfMemoryError` — fails fast instead of degrading silently"# Compliance_Flow" 

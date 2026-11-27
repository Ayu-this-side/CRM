# CRM System — Spring Boot Portfolio Project

A Customer Relationship Management system built with **Java 21 target + Spring Boot 3.3** demonstrating:
relational data modeling, layered architecture, Spring Security, Flyway, Thymeleaf, and role-based access.

> Phases 1 and 2 are implemented and hardened. Phases 3–5 (deals, tasks/communications, reporting) are still to be built.

**Repository:** https://github.com/Ayu-this-side/CRM

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Java 21 target (builds on JDK 21+) |
| Framework | Spring Boot 3.3.4 |
| UI | Thymeleaf + Security Extras |
| Data | Spring Data JPA / Hibernate |
| Database | PostgreSQL |
| Migrations | Flyway |
| Security | Spring Security (form login, BCrypt, RBAC) |
| Build | Maven 3.9+ (`mvnw` uses Maven from `PATH`) |

---

## Quick start (from GitHub)

```powershell
git clone https://github.com/Ayu-this-side/CRM.git
cd CRM
```

Then follow **Setup (First Run)** below.

---

## Prerequisites

- **JDK 21 or newer** on `PATH` (the project compiles with `--release 21`)
- **PostgreSQL** running on `localhost:5432`
- **Maven 3.9+** on `PATH`, or the bundled `mvnw` launcher

---

## Setup (First Run)

### 1. Create the database

```sql
-- Connect to PostgreSQL (psql / pgAdmin / DBeaver)
CREATE DATABASE crm_db;
```

### 2. Configure connection (if needed)

Connection settings read environment variables with local-dev defaults:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/crm_db
spring.datasource.username=${CRM_DB_USER:postgres}
spring.datasource.password=${CRM_DB_PASSWORD:Password}
```

Set them before running if your database differs:

```powershell
$env:CRM_DB_USER="postgres"
$env:CRM_DB_PASSWORD="your-password"
```

### 3. Run the application

**In Windows PowerShell (default VS Code terminal):**
```powershell
.\mvnw.cmd spring-boot:run
```

**In Windows Command Prompt (cmd):**
```cmd
mvnw.cmd spring-boot:run
```

**In Git Bash / Linux / macOS:**
```bash
./mvnw spring-boot:run
```

Flyway automatically runs `V1__init_users.sql`, `V2__init_leads_and_customers.sql`, and
`V3__harden_integrity.sql` on boot. On a **fresh database**, `DataInitializer` seeds the demo
admin, demo rep, and sample leads/customers. Seeding is skipped when users already exist, and
can be turned off completely with `-Dcrm.seed.enabled=false` (recommended outside local development).

To run the test suite (24 tests):

```powershell
.\mvnw.cmd test
```

### 4. Open in browser

```
http://localhost:8081
```

---

## Default Credentials

| Role | Email | Password | Access Scope |
|---|---|---|---|
| **Admin** | `admin@crm.com` | `admin123` | Full access: manage users, view all leads/customers, assign reps, org-wide counts |
| **Sales Rep** | `rep@crm.com` | `rep123` | Rep-scoped: view and manage only own assigned leads, customers, and conversions |

> These are **demo-only** accounts, seeded once on a fresh database. There is no self-service
> password-change screen yet; an Admin can change passwords from `/admin/users`, and demo seeding
> should be disabled outside local development.

---

## What You Can Do (Phases 1 & 2)

| Feature | Route / Action | Role Scope |
|---|---|---|
| **Login / Logout** | `/login` → Spring Security form login | Public / Authenticated |
| **Dashboard** | `/` — live KPI counts (Total Leads, Active Customers) | Admin sees org total; Rep sees own counts |
| **User Management** | `/admin/users` (list, create, edit, deactivate) | **Admin only** (`/admin/**` protected) |
| **Leads List & Filter** | `/leads` — keyword search, status filter, source filter, rep filter | Reps see own leads; Admin sees all |
| **Create Lead** | `/leads/new` — Bean validation, source/status selection | Sales Rep auto-assigned; Admin can assign an active Sales Rep |
| **Lead Detail & Pipeline** | `/leads/{id}` — status stepper, quick-action transition buttons | Rep-scoped access check |
| **Edit Lead** | `/leads/{id}/edit` — validates status transitions; blocks converted leads | Reps edit own; Admin edits all |
| **Lead → Customer Conversion** | `/leads/{id}/convert` — available only from `QUALIFIED`; pre-fills Customer, marks Lead `CONVERTED`, links records | Preserves traceability back to origin lead |
| **Customers List** | `/customers` — keyword search, status filter, rep filter | Reps see own customers; Admin sees all |
| **Customer Detail** | `/customers/{id}` — profile, origin lead badge, Phase 3/4 placeholders | Reps view own; Admin views all |
| **Create / Edit Customer** | `/customers/new`, `/customers/{id}/edit` | Direct creation & editing |

---

## Project Structure

```
src/main/java/com/crm/
├── CrmApplication.java
├── config/
│   ├── AccountStatusFilter.java # Revokes stale sessions after deactivate/role change
│   ├── AppConfig.java          # PasswordEncoder bean
│   ├── DataInitializer.java    # Seeds demo accounts/data on a fresh DB
│   └── SecurityConfig.java     # Form login, route protection, RBAC
├── controller/
│   ├── AuthController.java     # /login, /access-denied
│   ├── CustomerController.java # /customers CRUD & details
│   ├── HomeController.java     # / (dashboard with live metrics)
│   ├── LeadController.java     # /leads CRUD, pipeline progression & conversion
│   └── UserController.java     # /admin/users CRUD
├── dto/
│   ├── CustomerDto.java        # Bean validation for Customer creation/update
│   ├── LeadConvertDto.java     # Bean validation for Lead → Customer conversion
│   ├── LeadDto.java            # Bean validation for Lead creation/update
│   └── UserDto.java            # Bean validation for User accounts
├── entity/
│   ├── Customer.java           # Customer record linked to origin Lead & assigned User
│   ├── Lead.java               # Lead prospect with status pipeline & assigned User
│   └── User.java               # Implements UserDetails (Admin / Sales Rep)
├── enums/
│   ├── CustomerStatus.java     # ACTIVE, INACTIVE, CHURNED
│   ├── LeadSource.java         # WEBSITE, REFERRAL, COLD_CALL, EVENT, OTHER
│   ├── LeadStatus.java         # NEW, CONTACTED, QUALIFIED, CONVERTED, LOST + transitions
│   └── Role.java               # ADMIN, SALES_REP
├── exception/
│   ├── GlobalExceptionHandler.java  # @ControllerAdvice for 400, 404, 500
│   └── ResourceNotFoundException.java
├── repository/
│   ├── CustomerRepository.java # Search, filter, pagination & count queries
│   ├── LeadRepository.java     # Search, filter, pagination & count queries
│   └── UserRepository.java
└── service/
    ├── CustomerService.java    # Customer business logic & role-scoped visibility
    ├── LeadService.java        # Lead lifecycle, pipeline validation & conversion logic
    └── UserService.java        # Implements UserDetailsService + user CRUD

src/main/resources/
├── application.properties
├── db/migration/
│   ├── V1__init_users.sql              # Users table & indexes
│   ├── V2__init_leads_and_customers.sql # Leads & Customers tables, FKs, constraints
│   └── V3__harden_integrity.sql         # Optimistic locking cols + case-insensitive email uniqueness
├── static/css/style.css                # Dark mode design system, toolbar, stepper, tables
└── templates/
    ├── fragments/              # Reusable head + sidebar navigation
    ├── auth/                   # login, access-denied
    ├── home/                   # dashboard with KPI cards & navigation shortcuts
    ├── admin/users/            # user management list & form
    ├── leads/                  # list, detail, form, convert
    ├── customers/              # list, detail, form
    └── error/                  # error page

src/test/java/com/crm/
├── enums/LeadStatusTest.java   # Pipeline transition rules
└── service/                    # Lead/Customer/User service unit tests (24 tests total)
```

---

## Design Decisions

### 1. Lead-to-Customer Conversion & Traceability
- A Lead represents an uncommitted sales prospect with status pipeline: `NEW → CONTACTED → QUALIFIED → CONVERTED / LOST`.
- When qualified, the **Lead → Customer conversion** action creates a `Customer` record, sets `Customer.originLead = lead`, and locks the Lead status to `CONVERTED`.
- The database schema establishes a unidirectional foreign key `customers.origin_lead_id REFERENCES leads(id) UNIQUE`, avoiding circular DDL dependencies while enabling bidirectional navigation in JPA.
- Conversion is permitted only from `QUALIFIED` (enforced in service, controller, and UI).
- Converted leads are locked from modification or re-conversion, and converted customers cannot be deleted, preserving audit traceability.

### 2. Rep-Scoped Data Scoping
- Sales Reps only see and interact with their own assigned leads and customers (`WHERE assigned_rep_id = :currentUserId`).
- Admins have organization-wide visibility, can assign/reassign leads and customers across reps, and filter list views by representative.
- Only **active `SALES_REP` accounts** may own records; blank reassignment keeps the existing owner so records cannot be orphaned.
- Access checks are enforced at both the service layer (ownership verification) and the repository query level.
- `AccountStatusFilter` rechecks `enabled` and `role` on every request, so deactivation/demotion revokes existing sessions immediately.

### 3. Flyway Schema Migrations
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate verifies entities match the database schema without performing runtime DDL modifications.
- Schema versioning:
  - `V1__init_users.sql` (Phase 1)
  - `V2__init_leads_and_customers.sql` (Phase 2)
  - `V3__harden_integrity.sql` (optimistic locking + case-insensitive email uniqueness)

### 4. Data Integrity & Build Choices
- Mutable entities use JPA `@Version` optimistic locking; concurrent edits fail cleanly instead of silently overwriting each other.
- Emails are normalised (`trim().toLowerCase()`) and constrained by a `LOWER(email)` unique index.
- `UserService` blocks self-deactivation/self-demotion and removal of the last active Admin.
- Error pages return real HTTP statuses (400/403/404/409/500); internal exception details stay in logs.
- `maven-compiler-plugin` uses `annotationProcessorPaths` for Lombok and targets Java 21 bytecode; a newer JDK builds fine.

---

## Build Phases

| Phase | Status | Description |
|---|---|---|
| **1 — Foundation** | ✅ Complete | Auth, users, security, role-based navigation |
| **2 — Leads & Customers** | ✅ Complete + hardened | Core entities, strict status pipeline, search/filters, Lead → Customer conversion, rep scoping; 24 tests passing |
| **3 — Deal Pipeline** | Pending | Deal entity, stage pipeline (`PROSPECTING → WON/LOST`), close dates, probability |
| **4 — Tasks & Comms** | Pending | Task reminders, activity logging, Spring Mail integration, scheduled digests |
| **5 — Reporting & Polish** | Pending | Analytics dashboard with Chart.js, conversion metrics, performance breakdown |

## What's Next (Phases 3–5)

1. **Phase 3 — Deal pipeline:** `Deal` entity + migration, CRUD, stage transitions (`PROSPECTING → PROPOSAL → NEGOTIATION → WON/LOST`), Won/Lost rules, probability, close dates, deal detail linked from Customer.
2. **Phase 4 — Tasks & communications:** polymorphic `Task` and `CommunicationLog`, "My Tasks", Spring Mail with dev fallback, auto-logging, `@Scheduled` digest job, activity feed.
3. **Phase 5 — Reporting & polish:** aggregation queries, Chart.js dashboard (role-scoped), per-rep breakdown, search/pagination consistency, `@DataJpaTest` repository coverage, final UI/README polish.

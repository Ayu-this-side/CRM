# CRM System — Spring Boot Portfolio Project

A Customer Relationship Management system built with **Java 21 target + Spring Boot 3.3** demonstrating:
relational data modeling, layered architecture, Spring Security, Flyway, Thymeleaf, and role-based access.

> Phases 1–3 are implemented and hardened. Phases 4–5 (tasks/communications, reporting) are still to be built.

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

Flyway automatically runs `V1__init_users.sql`, `V2__init_leads_and_customers.sql`,
`V3__harden_integrity.sql`, and `V4__init_deals.sql` on boot. On a **fresh database**,
`DataInitializer` seeds the demo admin, demo rep, and sample leads/customers. Seeding is skipped
when users already exist, and can be turned off completely with `-Dcrm.seed.enabled=false`
(recommended outside local development).

To run the test suite (39 tests):

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
| **Admin** | `admin@crm.com` | `admin123` | Full access: manage users, view all leads/customers/deals, assign reps, org-wide counts |
| **Sales Rep** | `rep@crm.com` | `rep123` | Rep-scoped: view and manage only own assigned leads, customers, deals, and conversions |

> These are **demo-only** accounts, seeded once on a fresh database. There is no self-service
> password-change screen yet; an Admin can change passwords from `/admin/users`, and demo seeding
> should be disabled outside local development.

---

## What You Can Do (Phases 1–3)

| Feature | Route / Action | Role Scope |
|---|---|---|
| **Login / Logout** | `/login` → Spring Security form login | Public / Authenticated |
| **Dashboard** | `/` — live KPI counts (Total Leads, Active Customers, Open Deals) | Admin sees org totals; Rep sees own counts |
| **User Management** | `/admin/users` (list, create, edit, deactivate) | **Admin only** (`/admin/**` protected) |
| **Leads List & Filter** | `/leads` — keyword search, status filter, source filter, rep filter | Reps see own leads; Admin sees all |
| **Create Lead** | `/leads/new` — Bean validation, source/status selection | Sales Rep auto-assigned; Admin can assign an active Sales Rep |
| **Lead Detail & Pipeline** | `/leads/{id}` — status stepper, quick-action transition buttons | Rep-scoped access check |
| **Edit Lead** | `/leads/{id}/edit` — validates status transitions; blocks converted leads | Reps edit own; Admin edits all |
| **Lead → Customer Conversion** | `/leads/{id}/convert` — available only from `QUALIFIED`; pre-fills Customer, marks Lead `CONVERTED`, links records | Preserves traceability back to origin lead |
| **Customers List** | `/customers` — keyword search, status filter, rep filter, deal shortcut | Reps see own customers; Admin sees all |
| **Customer Detail** | `/customers/{id}` — profile, origin lead badge, linked deals, Phase 4 placeholder | Reps view own; Admin views all |
| **Create / Edit Customer** | `/customers/new`, `/customers/{id}/edit` | Direct creation & editing; customers with deals cannot be deleted |
| **Deals List & Filter** | `/deals` — keyword search, stage/customer/rep filters, pagination | Reps see own deals; Admin sees all |
| **Create / Edit Deal** | `/deals/new`, `/deals/{id}/edit` — value, editable probability (stage-suggested), expected close date | Rep owns new deals; Admin can assign an active Sales Rep |
| **Deal Detail & Stage Actions** | `/deals/{id}` — pipeline stepper, one-step transitions; Won records actual close date, Lost requires a reason | Rep-scoped access check; closed deals are locked |
| **Linked Deals** | From Customer detail/list, create and filter deals for that account | Inherits customer visibility rules |

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
│   ├── CustomerController.java # /customers CRUD, details & linked deals
│   ├── DealController.java     # /deals CRUD, stage actions & close outcomes
│   ├── HomeController.java     # / (dashboard with live metrics)
│   ├── LeadController.java     # /leads CRUD, pipeline progression & conversion
│   └── UserController.java     # /admin/users CRUD
├── dto/
│   ├── CustomerDto.java        # Bean validation for Customer creation/update
│   ├── DealDto.java            # Bean validation for Deals (value, probability 0–100)
│   ├── LeadConvertDto.java     # Bean validation for Lead → Customer conversion
│   ├── LeadDto.java            # Bean validation for Lead creation/update
│   └── UserDto.java            # Bean validation for User accounts
├── entity/
│   ├── Customer.java           # Customer record linked to origin Lead & assigned User
│   ├── Deal.java               # Deal tied to Customer; value, stage, probability, outcomes
│   ├── Lead.java               # Lead prospect with status pipeline & assigned User
│   └── User.java               # Implements UserDetails (Admin / Sales Rep)
├── enums/
│   ├── CustomerStatus.java     # ACTIVE, INACTIVE, CHURNED
│   ├── DealStage.java          # PROSPECTING → PROPOSAL → NEGOTIATION → WON / LOST + rules
│   ├── LeadSource.java         # WEBSITE, REFERRAL, COLD_CALL, EVENT, OTHER
│   ├── LeadStatus.java         # NEW, CONTACTED, QUALIFIED, CONVERTED, LOST + transitions
│   └── Role.java               # ADMIN, SALES_REP
├── exception/
│   ├── GlobalExceptionHandler.java  # @ControllerAdvice for 400, 404, 500
│   └── ResourceNotFoundException.java
├── repository/
│   ├── CustomerRepository.java # Search, filter, pagination & count queries
│   ├── DealRepository.java     # Search, stage/filter queries & open-deal counts
│   ├── LeadRepository.java     # Search, filter, pagination & count queries
│   └── UserRepository.java
└── service/
    ├── CustomerService.java    # Customer business logic & role-scoped visibility
    ├── DealService.java        # Deal CRUD, stage rules, Won/Lost outcomes & rep scoping
    ├── LeadService.java        # Lead lifecycle, pipeline validation & conversion logic
    └── UserService.java        # Implements UserDetailsService + user CRUD

src/main/resources/
├── application.properties
├── db/migration/
│   ├── V1__init_users.sql              # Users table & indexes
│   ├── V2__init_leads_and_customers.sql # Leads & Customers tables, FKs, constraints
│   ├── V3__harden_integrity.sql         # Optimistic locking cols + case-insensitive email uniqueness
│   └── V4__init_deals.sql               # Deals table, FKs, stage/value/probability checks & indexes
├── static/css/style.css                # Dark mode design system, toolbar, stepper, tables
└── templates/
    ├── fragments/              # Reusable head + sidebar navigation
    ├── auth/                   # login, access-denied
    ├── home/                   # dashboard with KPI cards & navigation shortcuts
    ├── admin/users/            # user management list & form
    ├── leads/                  # list, detail, form, convert
    ├── customers/              # list, detail, form (linked deals)
    ├── deals/                  # list, detail (stage actions), form
    └── error/                  # error page

src/test/java/com/crm/
├── enums/                      # LeadStatusTest, DealStageTest (pipeline rules)
└── service/                    # Lead/Customer/Deal/User service unit tests (39 tests total)
```

---

## Design Decisions

### 1. Lead-to-Customer Conversion & Traceability
- A Lead represents an uncommitted sales prospect with status pipeline: `NEW → CONTACTED → QUALIFIED → CONVERTED / LOST`.
- When qualified, the **Lead → Customer conversion** action creates a `Customer` record, sets `Customer.originLead = lead`, and locks the Lead status to `CONVERTED`.
- The database schema establishes a unidirectional foreign key `customers.origin_lead_id REFERENCES leads(id) UNIQUE`, avoiding circular DDL dependencies while enabling bidirectional navigation in JPA.
- Conversion is permitted only from `QUALIFIED` (enforced in service, controller, and UI).
- Converted leads are locked from modification or re-conversion, and converted customers cannot be deleted, preserving audit traceability.

### 2. Deal Pipeline & Outcome Integrity
- A Deal is a sales opportunity tied to one Customer. Stages move one step at a time: `PROSPECTING → PROPOSAL → NEGOTIATION → WON / LOST`; an open deal may be marked `LOST` at any point, while `WON` is only reachable from `NEGOTIATION`.
- `WON` records the actual close date and sets probability to 100%; `LOST` requires a reason. `WON` and `LOST` are terminal and their outcome data is immutable (no edit/delete), so reporting stays reliable.
- Probability is suggested from the stage (10/40/70%) but is an editable override validated to 0–100. Deals cannot be created directly as Won/Lost.
- Customers with deals cannot be deleted; deal history and the customer link are preserved.

### 3. Rep-Scoped Data Scoping
- Sales Reps only see and interact with their own assigned leads, customers, and deals (`WHERE assigned_rep_id = :currentUserId`).
- Admins have organization-wide visibility, can assign/reassign records across reps, and filter list views by representative.
- Only **active `SALES_REP` accounts** may own records; blank reassignment keeps the existing owner so records cannot be orphaned.
- Access checks are enforced at both the service layer (ownership verification) and the repository query level.
- `AccountStatusFilter` rechecks `enabled` and `role` on every request, so deactivation/demotion revokes existing sessions immediately.

### 4. Flyway Schema Migrations
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate verifies entities match the database schema without performing runtime DDL modifications.
- Schema versioning:
  - `V1__init_users.sql` (Phase 1)
  - `V2__init_leads_and_customers.sql` (Phase 2)
  - `V3__harden_integrity.sql` (optimistic locking + case-insensitive email uniqueness)
  - `V4__init_deals.sql` (Phase 3: deals, FKs, stage/value/probability checks, indexes)

### 5. Data Integrity & Build Choices
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
| **2 — Leads & Customers** | ✅ Complete + hardened | Core entities, strict status pipeline, search/filters, Lead → Customer conversion, rep scoping |
| **3 — Deal Pipeline** | ✅ Complete + hardened | Deal entity + V4 migration, stage pipeline, Won/Lost outcomes, probability, linked from Customer; 39 tests passing |
| **4 — Tasks & Comms** | Pending | Task reminders, activity logging, Spring Mail integration, scheduled digests |
| **5 — Reporting & Polish** | Pending | Analytics dashboard with Chart.js, conversion metrics, performance breakdown |

## What's Next (Phases 4–5)

1. **Phase 4 — Tasks & communications:** polymorphic `Task` and `CommunicationLog`, "My Tasks", Spring Mail with dev fallback, auto-logging, `@Scheduled` digest job, activity feed on Customer/Deal detail pages.
2. **Phase 5 — Reporting & polish:** aggregation queries, Chart.js dashboard (role-scoped), per-rep breakdown, search/pagination consistency, `@DataJpaTest` repository coverage, final UI/README polish.

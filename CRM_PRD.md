# Product Requirements Document (PRD)
## CRM System — Spring Boot Portfolio Project

**Version:** 1.0
**Author:** [Your Name]
**Date:** September 2026
**Status:** Draft

---

## 1. Overview

### 1.1 Purpose
A Customer Relationship Management (CRM) system built with Java Spring Boot to manage the full lifecycle of a sales process: capturing leads, converting them into customers, tracking sales deals through a pipeline, managing follow-up tasks, logging communications, and visualizing performance through a reporting dashboard.

### 1.2 Goal
Build a functionally complete, well-architected CRM as a portfolio project that demonstrates:
- Relational data modeling (JPA/Hibernate)
- RESTful backend design + server-rendered UI (Thymeleaf)
- Authentication & role-based authorization (Spring Security)
- Scheduled background jobs (`@Scheduled`)
- Aggregation/reporting queries
- Email integration (Spring Mail)
- Clean layered architecture (Controller → Service → Repository)

### 1.3 Target User
Single-developer learning/portfolio project. No real end-users — designed to simulate a small sales team (Admin + Sales Reps) usage pattern.

---

## 2. Scope

### 2.1 In Scope (v1)
- Lead management with status pipeline
- Lead → Customer conversion
- Customer management with contact history
- Deal/Opportunity pipeline tracking
- Task & follow-up reminders (with scheduled email digests)
- Communication logging (calls, emails, meetings)
- Reporting/analytics dashboard with charts
- User authentication & role-based access (Admin, Sales Rep)
- Server-rendered UI via Thymeleaf

### 2.2 Out of Scope (v1)
- Multi-tenancy (single organization only)
- Third-party integrations (no Gmail/Outlook sync, no external calendar sync)
- Mobile app
- Real-time notifications (WebSocket) — deferred to v2
- Billing/subscription management
- Marketing automation (campaigns, bulk email blasts)

### 2.3 Possible v2 Ideas (not committed)
- Kanban drag-and-drop board (JS-enhanced)
- REST API + React frontend as an alternative UI
- File attachments on leads/deals
- Audit log / activity timeline per entity
- Multi-tenant support

---

## 3. User Roles & Permissions

| Role | Permissions |
|---|---|
| **ADMIN** | Full access: manage users, view all leads/customers/deals, all reports |
| **SALES_REP** | Access only to their own assigned leads/customers/deals/tasks; can log communications; limited reporting (own performance only) |

Authentication via Spring Security (form login). Passwords hashed with BCrypt.

---

## 4. Core Modules & Features

### 4.1 Lead Management
**Description:** Capture and track potential customers before they convert.

**Fields:**
- Name, Company, Email, Phone
- Source (Website, Referral, Cold Call, Event, Other)
- Status: `NEW → CONTACTED → QUALIFIED → CONVERTED / LOST`
- Assigned Sales Rep
- Created date, Last updated date

**Functionality:**
- Create/edit/delete lead
- Filter/search leads by status, source, assigned rep
- Change lead status (with status-change validation, e.g. can't go from `LOST` back to `NEW` without explicit reopen)
- **Convert Lead → Customer**: one action that creates a Customer record pre-filled from the lead, marks lead as `CONVERTED`, and links the two records for traceability

---

### 4.2 Customer Management
**Description:** Manage active customer accounts, post-conversion.

**Fields:**
- Name, Company, Email, Phone, Address
- Origin Lead (nullable — customer may be created directly by Admin)
- Assigned Sales Rep
- Status: `ACTIVE`, `INACTIVE`, `CHURNED`

**Functionality:**
- Create/edit/delete customer (direct creation, bypassing lead flow, allowed for Admin)
- View customer detail page showing:
  - Linked deals
  - Linked tasks
  - Communication history log
- Search/filter customers

---

### 4.3 Sales / Deal Pipeline
**Description:** Track sales opportunities tied to a customer through to close.

**Fields:**
- Title, Customer (FK), Assigned Rep
- Value (currency amount)
- Stage: `PROSPECTING → PROPOSAL → NEGOTIATION → WON / LOST`
- Probability % (auto-suggested by stage, editable)
- Expected close date, Actual close date

**Functionality:**
- Create/edit deal
- Move deal through stages (table/list view with stage filter in v1; Kanban board is a v2 stretch goal)
- Mark deal Won/Lost (Lost requires a reason field)
- View deal detail with linked tasks and communication log

---

### 4.4 Tasks & Follow-ups
**Description:** Action items tied to leads, customers, or deals to ensure timely follow-up.

**Fields:**
- Title, Description
- Linked entity (Lead / Customer / Deal — polymorphic association)
- Due date, Priority (`LOW/MEDIUM/HIGH`)
- Status (`OPEN/DONE`)
- Assigned Rep

**Functionality:**
- Create/edit/complete task
- Personal task list per rep ("My Tasks" view)
- **Scheduled job** (`@Scheduled`, runs daily): finds tasks due today or overdue and sends a digest email to the assigned rep
- Overdue tasks visually flagged in UI

---

### 4.5 Communication Logging
**Description:** Record of interactions with a lead/customer for history and context.

**Fields:**
- Type: `EMAIL`, `CALL`, `MEETING`, `NOTE`
- Linked entity (Lead / Customer / Deal)
- Subject, Body/Notes
- Timestamp, Logged by (rep)

**Functionality:**
- Manually log a communication entry against any lead/customer/deal
- **Send real email** directly from the CRM (via Spring Mail/JavaMailSender) — on send, automatically creates a corresponding `EMAIL` communication log entry
- If SMTP isn't configured (dev mode toggle), sending falls back to log-only with a "simulated send" flag — keeps local dev friction-free
- Chronological activity feed on customer/deal detail pages

---

### 4.6 Reporting & Analytics Dashboard
**Description:** Visual summary of pipeline health and team performance.

**Metrics:**
- Leads by status (pie/bar chart)
- Lead → Customer conversion rate (overall and by source)
- Pipeline value by stage (bar chart)
- Win/loss rate (%)
- Revenue closed this month/quarter (won deals sum)
- Per-rep performance (Admin view only): deals won, revenue, conversion rate

**Functionality:**
- Dashboard as landing page after login
- Charts rendered with Chart.js, data fed via Thymeleaf model attributes or a small JSON endpoint
- Sales Reps see only their own numbers; Admin sees org-wide + per-rep breakdown

---

### 4.7 User & Auth Management
**Functionality:**
- Login/logout (Spring Security form login)
- Admin can create/deactivate rep accounts, assign roles
- Password hashing (BCrypt)
- Route-level access control (`@PreAuthorize` / security config by role)

---

## 5. Data Model (High-Level Entities)

```
User (id, name, email, password, role)
  |
  |── owns ──> Lead (id, name, company, email, phone, source, status, assignedRep, createdAt)
  |                 |
  |             convertsTo (nullable FK) ──> Customer
  |
  |── owns ──> Customer (id, name, company, email, phone, address, originLead, status, assignedRep)
  |                 |
  |                 |── has many ──> Deal
  |                 |── has many ──> Task (via polymorphic link)
  |                 |── has many ──> CommunicationLog (via polymorphic link)
  |
  |── owns ──> Deal (id, title, customer, assignedRep, value, stage, probability, expectedCloseDate, actualCloseDate, lostReason)
  |                 |── has many ──> Task
  |                 |── has many ──> CommunicationLog
  |
  |── owns ──> Task (id, title, description, linkedEntityType, linkedEntityId, dueDate, priority, status, assignedRep)
  |
  └── owns ──> CommunicationLog (id, type, linkedEntityType, linkedEntityId, subject, body, timestamp, loggedBy)
```

> **Note on Task/CommunicationLog linking:** Use a simple polymorphic pattern (`linked_entity_type` enum + `linked_entity_id` long) rather than three separate nullable FK columns — cleaner schema, one join table to query. Document this trade-off in the README as a deliberate design decision (alternative: separate `lead_tasks`, `customer_tasks`, `deal_tasks` tables if you want to demonstrate more "pure" relational modeling instead).

---

## 6. Non-Functional Requirements

| Category | Requirement |
|---|---|
| **Security** | Passwords hashed (BCrypt); role-based route protection; CSRF protection enabled (Spring Security default for Thymeleaf forms) |
| **Validation** | Bean Validation (`@NotNull`, `@Email`, etc.) on all form inputs, server-side |
| **Database** | PostgreSQL, schema managed via Flyway migrations (versioned `V1__`, `V2__` scripts) |
| **Performance** | Not a primary concern at this scale; use pagination on list views (leads/customers/deals) via Spring Data `Pageable` |
| **Error Handling** | Global exception handler (`@ControllerAdvice`) with friendly error pages |
| **Logging** | SLF4J/Logback for application logs |
| **Testing** | Unit tests for service layer (JUnit 5 + Mockito); at least a few integration tests for repositories (`@DataJpaTest`) |

---

## 7. Tech Stack

| Layer | Choice |
|---|---|
| Language/Framework | Java 21, Spring Boot 3.x |
| Web/UI | Spring MVC + Thymeleaf |
| Data | Spring Data JPA (Hibernate) |
| Database | PostgreSQL |
| Migrations | Flyway |
| Security | Spring Security (form login, role-based) |
| Email | Spring Mail (JavaMailSender), with dev fallback |
| Charts | Chart.js (via CDN in Thymeleaf templates) |
| Scheduling | Spring `@Scheduled` |
| Build | Maven |
| Testing | JUnit 5, Mockito, `@DataJpaTest` |

---

## 8. Suggested Build Phases

**Phase 1 — Foundation**
- Project setup, PostgreSQL + Flyway config
- User entity, Spring Security login, role setup

**Phase 2 — Core CRUD**
- Lead entity + CRUD + status pipeline
- Customer entity + CRUD
- Lead → Customer conversion flow

**Phase 3 — Sales Pipeline**
- Deal entity + CRUD + stage transitions
- Deal detail page

**Phase 4 — Tasks & Communication**
- Task entity + CRUD + "My Tasks" view
- CommunicationLog entity + manual logging
- Spring Mail integration + auto-log on send
- `@Scheduled` daily digest job

**Phase 5 — Reporting**
- Dashboard queries (aggregations via JPQL or Spring Data projections)
- Chart.js integration
- Role-based dashboard views (rep vs admin)

**Phase 6 — Polish**
- Pagination, search/filter on list views
- Error handling, validation messages
- Tests
- README with setup instructions + design decisions

---

## 9. Open Questions / Future Decisions
- Should Kanban drag-and-drop be attempted in v1 or deferred to v2? *(Recommended: defer — table view with stage filter ships faster and is equally demonstrable.)*
- Separate `lead_tasks`/`customer_tasks`/`deal_tasks` tables vs polymorphic linking — pick one and document the trade-off (see Section 5 note).
- H2 for automated tests + PostgreSQL for dev/prod, or PostgreSQL everywhere (via Testcontainers for tests)? *(Recommended: Testcontainers if you want to show that skill off; otherwise H2 for speed.)*

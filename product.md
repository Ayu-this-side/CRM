# Product Specification
## CRM System — Spring Boot Portfolio Project

**Version:** 1.0
**Type:** Personal learning/portfolio project

---

## 1. Overview

A Customer Relationship Management (CRM) system built with Java Spring Boot to manage the full lifecycle of a sales process: capturing leads, converting them into customers, tracking sales deals through a pipeline, managing follow-up tasks, logging communications, and visualizing performance through a reporting dashboard.

**Goal:** Demonstrate strong backend engineering skills — relational data modeling, layered architecture, security, scheduled jobs, email integration, and reporting — through a functionally complete CRM.

---

## 2. Target User

Simulated small sales team:
- **Admin** — manages users, sees all data, org-wide reporting
- **Sales Rep** — manages only their own leads/customers/deals/tasks

---

## 3. Scope

### In Scope (v1)
- Lead management with status pipeline
- Lead → Customer conversion
- Customer management with contact/activity history
- Deal/Opportunity pipeline tracking
- Task & follow-up reminders with scheduled email digests
- Communication logging (calls, emails, meetings) + real email sending
- Reporting/analytics dashboard with charts
- Authentication & role-based access control
- Server-rendered UI via Thymeleaf

### Out of Scope (v1)
- Multi-tenancy
- Third-party integrations (Gmail/Outlook/calendar sync)
- Mobile app
- Real-time notifications (WebSocket)
- Billing/subscriptions
- Marketing automation / bulk email campaigns

### Future Ideas (v2+)
- Kanban drag-and-drop deal board
- React frontend as alternative to Thymeleaf
- File attachments on records
- Full audit log / activity timeline
- Multi-tenant support

---

## 4. User Roles & Permissions

| Role | Permissions |
|---|---|
| **ADMIN** | Full access: manage users, view all leads/customers/deals, org-wide + per-rep reports |
| **SALES_REP** | Access only to own assigned leads/customers/deals/tasks; can log communications; sees only own performance reports |

Authentication via Spring Security (form login). Passwords hashed with BCrypt. CSRF protection enabled.

---

## 5. Core Features

### 5.1 Lead Management
- Fields: Name, Company, Email, Phone, Source (Website/Referral/Cold Call/Event/Other), Status, Assigned Rep, timestamps
- Status pipeline: `NEW → CONTACTED → QUALIFIED → CONVERTED / LOST`
- Create/edit/delete, search/filter by status/source/rep
- One-click **Convert Lead → Customer**, preserving traceability back to the source lead

### 5.2 Customer Management
- Fields: Name, Company, Email, Phone, Address, Origin Lead (nullable), Assigned Rep, Status
- Create/edit/delete (direct creation allowed for Admin, bypassing lead flow)
- Detail page shows linked deals, tasks, and communication history
- Search/filter

### 5.3 Sales / Deal Pipeline
- Fields: Title, Customer, Assigned Rep, Value, Stage, Probability %, Expected/Actual close date
- Stage pipeline: `PROSPECTING → PROPOSAL → NEGOTIATION → WON / LOST`
- Table/list view with stage filter (Kanban board deferred to v2)
- Won/Lost marking (Lost requires a reason)
- Detail page with linked tasks and communication log

### 5.4 Tasks & Follow-ups
- Fields: Title, Description, Linked entity (Lead/Customer/Deal), Due date, Priority, Status, Assigned Rep
- "My Tasks" personal view per rep
- Daily scheduled job (`@Scheduled`) emails each rep a digest of due/overdue tasks
- Overdue tasks visually flagged

### 5.5 Communication Logging
- Fields: Type (Email/Call/Meeting/Note), Linked entity, Subject, Body, Timestamp, Logged by
- Manual logging against any lead/customer/deal
- Real email sending via Spring Mail (JavaMailSender); auto-creates a matching log entry on send
- Dev-mode fallback: simulate send + log-only if SMTP isn't configured
- Chronological activity feed on detail pages

### 5.6 Reporting & Analytics Dashboard
- Leads by status, lead → customer conversion rate (overall + by source)
- Pipeline value by stage
- Win/loss rate, revenue closed this month/quarter
- Per-rep performance breakdown (Admin only)
- Landing page after login; charts via Chart.js
- Reps see only their own numbers

### 5.7 User & Auth Management
- Login/logout, Admin creates/deactivates rep accounts and assigns roles
- Route-level access control by role

---

## 6. Data Model (High-Level)

```
User (id, name, email, password, role)
  |
  |── owns ──> Lead (id, name, company, email, phone, source, status, assignedRep, createdAt)
  |                 |── convertsTo (nullable FK) ──> Customer
  |
  |── owns ──> Customer (id, name, company, email, phone, address, originLead, status, assignedRep)
  |                 |── has many ──> Deal
  |                 |── has many ──> Task (polymorphic link)
  |                 |── has many ──> CommunicationLog (polymorphic link)
  |
  |── owns ──> Deal (id, title, customer, assignedRep, value, stage, probability, expectedCloseDate, actualCloseDate, lostReason)
  |                 |── has many ──> Task
  |                 |── has many ──> CommunicationLog
  |
  |── owns ──> Task (id, title, description, linkedEntityType, linkedEntityId, dueDate, priority, status, assignedRep)
  |
  └── owns ──> CommunicationLog (id, type, linkedEntityType, linkedEntityId, subject, body, timestamp, loggedBy)
```

**Design note:** Task and CommunicationLog use a polymorphic link (`linkedEntityType` enum + `linkedEntityId`) rather than three separate nullable FKs — simpler schema, single join point. Document this trade-off in the README.

---

## 7. Non-Functional Requirements

| Category | Requirement |
|---|---|
| Security | BCrypt password hashing, role-based route protection, CSRF protection |
| Validation | Bean Validation on all form inputs, server-side |
| Database | PostgreSQL, schema managed via Flyway migrations |
| Performance | Pagination on all list views (`Pageable`) |
| Error Handling | Global exception handler (`@ControllerAdvice`) with friendly error pages |
| Logging | SLF4J/Logback |
| Testing | JUnit 5 + Mockito for services; `@DataJpaTest` for repositories |

---

## 8. Tech Stack

| Layer | Choice |
|---|---|
| Language/Framework | Java 21, Spring Boot 3.x |
| Web/UI | Spring MVC + Thymeleaf |
| Data | Spring Data JPA (Hibernate) |
| Database | PostgreSQL |
| Migrations | Flyway |
| Security | Spring Security (form login, role-based) |
| Email | Spring Mail (JavaMailSender), dev fallback |
| Charts | Chart.js (CDN) |
| Scheduling | Spring `@Scheduled` |
| Build | Maven |
| Testing | JUnit 5, Mockito, `@DataJpaTest` |

---

## 9. Open Questions
- Kanban board now or v2? → Recommended: defer, ship table view first
- Polymorphic task/log linking vs separate tables? → Documented decision, see Section 6
- Testcontainers vs H2 for tests? → Recommended: Testcontainers if time allows, otherwise H2

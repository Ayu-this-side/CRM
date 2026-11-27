# Build Phases
## CRM System — Spring Boot Portfolio Project

Reference: see `product.md` for full feature/data model details. This file breaks the build into 5 sequential phases, each shippable/demoable on its own.

---

## Phase 1 — Foundation, Auth & Users

**Objective:** Get a running, secured Spring Boot app with a working login before touching any CRM domain logic.

**Tasks:**
- [x] Initialize Spring Boot 3.x project (Java 21 target, Maven) with Web, Thymeleaf, Spring Data JPA, Spring Security, PostgreSQL driver, Flyway, Validation
- [x] Configure PostgreSQL connection + first Flyway migration (`V1__init_users.sql`)
- [x] `User` entity (id, name, email, password, role) + repository
- [x] Spring Security config: form login, BCrypt password encoding, role-based route protection (`ADMIN`, `SALES_REP`)
- [x] Admin-only screens to create/edit/deactivate rep accounts and assign roles
- [x] Base Thymeleaf layout (navbar, login page, logout, access-denied page)
- [x] Global exception handler (`@ControllerAdvice`) + basic error pages

**Exit criteria:** Can log in as Admin or Rep, see role-appropriate navigation, and manage user accounts as Admin.

---

## Phase 2 — Leads & Customers (✅ Complete)

**Objective:** Core entities and the lead-to-customer flow — the heart of the CRM.

**Tasks:**
- [x] `Lead` entity + Flyway migration (`V2__init_leads_and_customers.sql`) + repository/service/controller (full CRUD)
- [x] Lead status pipeline (`NEW → CONTACTED → QUALIFIED → CONVERTED / LOST`) with status-change validation
- [x] Lead list view: search/filter by status, source, assigned rep; pagination
- [x] `Customer` entity + Flyway migration + repository/service/controller (full CRUD)
- [x] **Lead → Customer conversion** action (pre-fills Customer from Lead, marks Lead `CONVERTED`, links records)
- [x] Customer detail page (placeholder sections for deals/tasks/communications, filled in later phases)
- [x] Rep-scoped visibility: reps see only their own leads/customers; Admin sees all
- [x] Bean Validation on all forms
- [x] Unit test suites (`LeadServiceTest`, `CustomerServiceTest`) passing

**Exit criteria:** Can create a lead, move it through statuses, convert it to a customer, and manage customers directly. Rep-level data scoping works. (Verified ✅)

### Phase 1/2 hardening pass (completed)
Audit fixes applied after the initial build, so the "Complete" status is defensible:
- [x] Every request revalidates the logged-in account: deactivation/role changes revoke active sessions
- [x] Demo accounts seed only on a fresh DB and can be disabled (`crm.seed.enabled=false`); no plaintext passwords in logs
- [x] Correct HTTP statuses (400/403/404/409/500) and a dedicated access-denied redirect; no internal error leakage
- [x] Strict, single-source-of-truth lead pipeline; conversion allowed only from `QUALIFIED`
- [x] Only active Sales Reps can own records; blank reassignment keeps the current owner
- [x] Converted customers cannot be deleted (lead traceability preserved)
- [x] Self-deactivation/self-demotion and removal of the last active Admin blocked
- [x] Emails normalised and unique case-insensitively; optimistic locking on mutable entities
- [x] Fetch joins on list queries, clamped pagination, active-rep dropdowns, password validation
- [x] Test suite expanded from 9 to 24 tests (services, user rules, pipeline, scoping)

---

## Phase 3 — Sales / Deal Pipeline

**Objective:** Track opportunities against customers through to close.

**Tasks:**
- `Deal` entity + Flyway migration + repository/service/controller (full CRUD)
- Stage pipeline (`PROSPECTING → PROPOSAL → NEGOTIATION → WON / LOST`) with stage-change actions
- Won/Lost handling (Lost requires a reason field; Won sets actual close date)
- Deal list view with stage filter + pagination
- Deal detail page linked from Customer detail page
- Probability % auto-suggestion by stage (editable override)
- Rep-scoped visibility consistent with Phase 2

**Exit criteria:** Can create a deal against a customer, move it through pipeline stages, and close it Won or Lost with correct data captured.

---

## Phase 4 — Tasks & Communication Logging

**Objective:** Follow-up management and interaction history, plus real email sending.

**Tasks:**
- `Task` entity with polymorphic link (`linkedEntityType` + `linkedEntityId`) to Lead/Customer/Deal
- Task CRUD + "My Tasks" personal view per rep, with due date/priority/status
- `CommunicationLog` entity with same polymorphic linking pattern
- Manual communication logging (Call/Meeting/Note types) against any lead/customer/deal
- Spring Mail integration (JavaMailSender) for sending real emails from the CRM; auto-creates matching `EMAIL` log entry on send
- Dev-mode config toggle: simulate send + log-only when SMTP isn't configured
- `@Scheduled` daily job: finds due/overdue tasks and emails each rep a digest
- Chronological activity feed component on Customer/Deal detail pages, combining tasks + communication logs

**Exit criteria:** Can create tasks tied to any entity, see them in "My Tasks," send a real email logged automatically, and confirm the scheduled digest job fires correctly (test via short interval override).

---

## Phase 5 — Reporting, Testing & Polish

**Objective:** Turn the working CRM into a demo-ready, portfolio-quality product.

**Tasks:**
- Dashboard queries: leads by status, conversion rate (overall + by source), pipeline value by stage, win/loss rate, revenue this month/quarter, per-rep breakdown (Admin only)
- Chart.js integration on dashboard (bar/pie charts fed by JPQL/projection queries)
- Set dashboard as post-login landing page; role-based view (rep sees own numbers, admin sees org-wide)
- Search/filter refinement across all list views; consistent pagination
- Unit tests (JUnit 5 + Mockito) for service layer; `@DataJpaTest` for repository layer
- UI polish pass: consistent styling, friendly validation/error messages, empty states
- README: setup instructions, architecture overview, and documented design decisions (e.g. polymorphic linking trade-off, DB choice)

**Exit criteria:** Dashboard accurately reflects seeded data, core services have test coverage, and the app is presentable end-to-end for a portfolio demo or interview walkthrough.

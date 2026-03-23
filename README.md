# Policy-as-Code Regulatory Change Agent

An **AI-powered regulatory compliance agent** that monitors financial regulations, extracts requirements, performs gap analysis, drafts policy amendments, and generates executable code specifications — fully automated and routed to the right team.

> Built with **Groq LLM** (free cloud AI), **Spring Boot 3**, **React 18**, and **H2 Database**.

---

## 🎯 What This Agent Does

Financial regulations constantly change. Banks must identify applicable changes, interpret requirements, update policies, and modify systems — often under tight timelines. Manual processes are slow, error-prone, and create compliance gaps.

This agent automates the full regulatory change lifecycle:

```
RSS Feed / Manual Input
        ↓
📡 Regulatory Monitoring   →  Detects new regulations from live RSS feeds
        ↓
🤖 AI Analysis (Groq LLM)  →  Parses regulation into discrete requirements
        ↓
📊 Impact Assessment       →  Maps requirements to business lines & systems
        ↓
🔍 Gap Analysis            →  Compares against existing bank policy
        ↓
✍️  Policy Drafting         →  Generates professional policy amendments
💻 Code Generation         →  Produces executable IF/THEN compliance rules
        ↓
🔄 Workflow Routing        →  Routes to Compliance Team or Technology Team
        ↓
🎫 Ticket Management       →  Creates tracked implementation tickets
```

---

## ✅ Core Capabilities

| Capability | Description |
|---|---|
| **Regulatory Monitoring** | Polls RSS/Atom feeds from regulatory authorities (Federal Reserve, OCC, FDIC etc.) |
| **Requirement Extraction** | Parses regulatory text into discrete, testable requirements |
| **Impact Assessment** | Determines which business lines and systems are affected |
| **Gap Analysis** | Compares new requirements against current bank policies |
| **Policy Drafting** | Generates professional policy language with section numbers and responsible officers |
| **Code Generation** | Produces executable `IF/THEN` compliance rules for system implementation |
| **Workflow Management** | Full lifecycle: ANALYZED → REVIEW_PENDING → APPROVED → IMPLEMENTED → CLOSED |
| **Team Routing** | Auto-routes to Compliance Team (policy/controls) or Technology Team (system rules) |
| **Ticket Tracking** | Creates tracked tickets with team assignment and status management |
| **Analysis History** | Maintains switchable history of all analyzed regulations in the UI |
| **Session Metrics** | Live counters for regulations detected, requirements extracted, gaps found |

---

## 🛠️ Tech Stack

### Backend
| Component | Technology |
|---|---|
| Framework | Spring Boot 3.3.2 |
| Language | Java 21 |
| AI / LLM | Groq API (`llama-3.3-70b-versatile`) via WebFlux WebClient |
| Database | H2 Embedded (file-based persistence) |
| ORM | Spring Data JPA + Hibernate |
| HTTP Client | Java 11 HttpClient (RSS feed fetching) |
| Reactive | Spring WebFlux (Groq API calls) |

### Frontend
| Component | Technology |
|---|---|
| Framework | React 18 |
| Bundler | Vite 5 |
| HTTP Client | Axios |
| Styling | Custom CSS (light theme, Plus Jakarta Sans + IBM Plex Mono) |
| State | React useState / useRef |

---

## 📋 Prerequisites

- **Java 21+** — [Download JDK](https://adoptopenjdk.net/)
- **Maven 3.8+** — [Download Maven](https://maven.apache.org/download.cgi)
- **Node.js 16+** — [Download Node.js](https://nodejs.org/)
- **Groq API Key (free)** — [Get key at console.groq.com](https://console.groq.com)

> ⚠️ No Ollama required. No local GPU needed. Groq runs in the cloud for free.

---

## 🚀 Quick Start

### Step 1: Get a Free Groq API Key

1. Go to [https://console.groq.com](https://console.groq.com)
2. Sign up → Create API Key
3. Copy the key

### Step 2: Configure Backend

Edit `backend/src/main/resources/application.yml`:

```yaml
groq:
  api-key: YOUR_GROQ_API_KEY_HERE   # Paste your key here
  model: llama-3.3-70b-versatile
  feed-delay-ms: 10000               # 7s delay between feed items (respects TPM limit)
  feed-max-items: 5                 # Max items per feed fetch (conserves daily quota)
```

### Step 3: Run Backend

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

Backend available at: `http://localhost:8080`

Verify:
- H2 Console: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:file:./data/policydb`
  - Username: `sa` / Password: *(blank)*

### Step 4: Run Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend available at: `http://localhost:5173`

---

## 📖 How to Use

### Manual Analysis

1. Open `http://localhost:5173`
2. Scroll to the **"Run Analysis"** section at the bottom
3. Paste your **Existing Policy** (YAML or plain text)
4. Paste the **New Regulation** text
5. Click **🚀 Analyze**
6. Results appear across 6 tabs automatically

**Example — Existing Policy:**
```yaml
policy:
  name: VendorRiskPolicy
  reviewFrequencyDays: 365
  loggingEnabled: false
  vendorRiskTiering: basic
  continuousMonitoring: false
```

**Example — New Regulation:**
```
OCC Third-Party Risk Management Guidance:
1. Critical vendors must undergo a risk assessment every 180 days.
2. All vendor-related access must be logged and retained for 7 years.
3. High-risk vendors must have continuous monitoring with real-time alerting.
4. Vendor risk classification must include tiers: low, medium, high, critical.
5. Automated alerts must trigger if a vendor assessment exceeds 180 days.
```

### RSS Feed Monitoring

1. Go to **📡 Monitoring** tab
2. Paste a regulatory RSS feed URL, e.g.:
   ```
   https://www.federalreserve.gov/feeds/press_all.xml
   ```
3. Click **+ Add** then **↻ Fetch Now**
4. Agent auto-analyzes each item and populates all tabs

**Recommended regulatory feeds:**
- Federal Reserve: `https://www.federalreserve.gov/feeds/press_all.xml`
- OCC: `https://www.occ.gov/rss/occ-news.xml`
- FDIC: `https://www.fdic.gov/news/financial-institution-letters/rss.xml`

---

## 🖥️ UI Tabs Explained

| Tab | What It Shows |
|---|---|
| 📡 **Monitoring** | RSS feed management, detected regulatory items with ✅ Analyzed badges |
| 📊 **Impact** | Summary metrics, Compliance/Technology team routing cards, confidence score, business line & system tags |
| 📋 **Requirements** | All extracted requirements with Gap/Satisfied badges, recommendation colors, filter by status, ticket creation |
| 🔍 **Gap Analysis** | Named gap cards with business impact explanation per requirement |
| ✍️ **Drafting** | Professional policy amendments routed to Compliance Team, Export PDF/DOCX |
| 💻 **Code Gen** | Executable IF/THEN system rules routed to Technology Team |
| 🔄 **Workflow** | Full lifecycle management with per-update breakdowns and routing pills |

### Analysis History Bar
When multiple regulations are analyzed, a **history bar** appears at the top of content tabs. Each analysis is a clickable tab showing source (🔵 Manual / 🟢 RSS Feed) and timestamp. Switching tabs instantly updates all content to show that regulation's results.

---

## 🏗️ Project Structure

```
policy-as-code-agent-ollama/
├── backend/
│   ├── pom.xml
│   ├── data/                          # H2 database files (auto-created)
│   └── src/main/
│       ├── java/com/example/policyagent/
│       │   ├── PolicyAgentApplication.java
│       │   ├── controller/
│       │   │   └── PolicyController.java       # REST endpoints + feed processing
│       │   ├── service/
│       │   │   ├── PolicyService.java          # Core AI analysis + persistence
│       │   │   ├── GroqChatService.java        # Groq LLM API client
│       │   │   ├── TicketService.java          # Ticket creation + team routing
│       │   │   └── RegulatoryWorkflowService.java
│       │   ├── entity/
│       │   │   ├── RegulatoryUpdate.java
│       │   │   ├── RequirementEntity.java
│       │   │   ├── GapEntity.java
│       │   │   ├── PolicyDraftEntity.java
│       │   │   ├── CodeSpecificationEntity.java
│       │   │   ├── TicketEntity.java           # includes assignedTeam field
│       │   │   ├── AuditLog.java
│       │   │   ├── RegulatoryStatus.java
│       │   │   └── TicketStatus.java
│       │   └── repository/
│       │       ├── RegulatoryUpdateRepository.java
│       │       ├── RequirementRepository.java  # findByRegulatoryUpdateId()
│       │       ├── GapRepository.java
│       │       ├── PolicyDraftRepository.java
│       │       ├── CodeSpecificationRepository.java
│       │       ├── AuditLogRepository.java
│       │       └── TicketRepository.java
│       └── resources/
│           └── application.yml
├── frontend/
│   ├── package.json
│   ├── index.html
│   └── src/
│       ├── main.jsx
│       ├── App.jsx                    # Main app with history, metrics, routing
│       ├── App.css                    # Light theme (Plus Jakarta Sans)
│       └── components/
│           ├── Tabs.jsx
│           ├── RequirementsPanel.jsx  # Filter, badges, ticket creation
│           └── Monitoring.jsx        # Feed management, analyzed badges
├── docker-compose.yml
└── README.md
```

---

## 🔌 API Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/api/policy/analyze` | POST | Manual regulation analysis |
| `/api/policy/fetch-feed` | POST | Fetch and analyze RSS feed |
| `/api/policy/updates` | GET | All regulatory updates |
| `/api/policy/updates/{id}` | GET | Single regulatory update |
| `/api/policy/updates/{id}/summary` | GET | Per-update breakdown with routing counts |
| `/api/policy/{id}/allowed-transitions` | GET | Available workflow transitions |
| `/api/policy/{id}/submit-review` | POST | Transition to REVIEW_PENDING |
| `/api/policy/{id}/approve` | POST | Transition to APPROVED |
| `/api/policy/{id}/mark-implemented` | POST | Transition to IMPLEMENTED |
| `/api/policy/{id}/close` | POST | Transition to CLOSED |
| `/api/policy/tickets` | GET | All tickets |
| `/api/policy/tickets` | POST | Create ticket (auto-assigns team) |
| `/api/policy/export/pdf` | POST | Export policy drafts as PDF |
| `/api/policy/export/docx` | POST | Export policy drafts as DOCX |

---

## ⚙️ Configuration Reference (`application.yml`)

```yaml
spring:
  application:
    name: policy-agent
  datasource:
    url: jdbc:h2:file:./data/policydb
    driverClassName: org.h2.Driver
    username: sa
    password: ""
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create        # Use 'update' to preserve data across restarts
    show-sql: true
  h2:
    console:
      enabled: true
      path: /h2-console

# ─────────────────────────────────────────────────────────────────
# GROQ FREE TIER LIMITS (llama-3.3-70b-versatile, as of Mar 2026)
#   TPM (Tokens Per Minute) : 12,000
#   TPD (Tokens Per Day)    : 100,000
#   ~1,400 tokens per feed item analysis
# ─────────────────────────────────────────────────────────────────
groq:
  api-key: YOUR_GROQ_API_KEY_HERE
  model: llama-3.3-70b-versatile

  # Delay between feed item analyses to stay under 12,000 TPM limit.
  # Calculation: 60s ÷ (12,000 TPM ÷ 1,400 tokens) = ~7s minimum
  feed-delay-ms: 10000

  # Max feed items per fetch to conserve daily quota (100,000 TPD).
  # 5 items × 1,400 tokens = ~7,000 tokens per fetch.
  # Allows ~14 demo runs per day within the free tier limit.
  # Set to 20 for full feed processing or 0 to disable the limit.
  feed-max-items: 5

server:
  port: 8080

logging:
  level:
    root: INFO
    org.springframework: INFO
    com.example.policyagent: DEBUG
    org.hibernate.SQL: DEBUG
```

---

## 🔄 Workflow Lifecycle

```
ANALYZED → REVIEW_PENDING → APPROVED → IMPLEMENTED → CLOSED
```

| Status | Color | Meaning |
|---|---|---|
| ANALYZED | 🔵 Blue | AI has processed the regulation |
| REVIEW_PENDING | 🟡 Amber | Submitted for compliance review |
| APPROVED | 🟢 Green | Approved by compliance team |
| IMPLEMENTED | 🟣 Purple | Technology team has implemented |
| CLOSED | ⚫ Dark | Fully closed and archived |

---

## 🎫 Ticket Routing Logic

Tickets are automatically assigned to the correct team based on recommendation type:

| Recommendation | Assigned Team | Reason |
|---|---|---|
| `update_policy` | 👔 COMPLIANCE | Policy language changes need legal review |
| `add_control` | 👔 COMPLIANCE | New controls are a compliance design responsibility |
| `implement_system_rule` | 💻 TECHNOLOGY | System rules require engineering implementation |
| `no_action` | 🛡️ RISK | Risk team confirms no action is required |

---

## 💾 Database

All data is persisted to an H2 file database at `backend/data/policydb.mv.db`.

**Access H2 Console:** `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:file:./data/policydb`
- Username: `sa` / Password: *(leave blank)*

**Useful queries:**
```sql
-- All regulatory updates
SELECT id, title, authority, status, confidence_score, publication_date
FROM regulatory_update ORDER BY id DESC;

-- Requirements per update
SELECT r.id, r.text, r.satisfied, r.recommendation, r.impacted_business_line
FROM requirement_entity r WHERE r.regulatory_update_id = 1;

-- All tickets with team assignment
SELECT t.tracking_key, t.summary, t.assigned_team, t.status
FROM ticket_entity t ORDER BY t.created_at DESC;

-- Audit trail
SELECT action, actor, details, timestamp
FROM audit_log ORDER BY timestamp DESC;

-- Gap report
SELECT g.requirement_id, g.issue, g.detail FROM gap_entity g;
```

> ⚠️ **Before the demo:** Delete `backend/data/policydb.mv.db` and restart to get a clean slate. The schema recreates automatically.

---

## 🐛 Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| `429 Too Many Requests (TPM)` | Too many rapid Groq calls | Increase `feed-delay-ms` to `20000` |
| `429 Too Many Requests (TPD)` | Daily quota exhausted | Wait until midnight UTC or reduce `feed-max-items` |
| `Cannot connect to backend` | Spring Boot not running | Run `mvn spring-boot:run` in `backend/` |
| `Schema mismatch error` | DB schema out of date | Delete `backend/data/` and restart |
| `Duplicate key warning (React)` | Multiple analyses arrive simultaneously | Fixed in App.jsx with `useRef` counter |
| `Auto analysis failed for feed item` | Rate limit hit mid-fetch | Items that fail are skipped; re-fetch after 1 minute |
| Port 8080 in use | Another process on 8080 | Change `server.port` in `application.yml` |
| Port 5173 in use | Another Vite process | Vite auto-increments to 5174 |

---

## 📊 Groq Free Tier Usage Guide

| Scenario | Tokens Used | Daily Runs Possible |
|---|---|---|
| 1 manual analysis | ~1,400 | 71 |
| 5-item feed fetch | ~7,000 | 14 |
| 20-item feed fetch | ~28,000 | 3 |
| Full demo (1 manual + 5 feed items) | ~8,400 | 11 |

To increase limits, upgrade to [Groq Dev Tier](https://console.groq.com/settings/billing).

---

## 🤝 Contributing

Pull requests welcome. Key areas for contribution:
- Additional LLM providers (OpenAI, Anthropic, Gemini)
- Policy versioning and diff view
- Email/Slack notifications for workflow transitions
- Docker deployment configuration
- Export to Confluence / SharePoint
- Multi-bank / multi-policy support

---

## 📄 License

MIT License — Free for personal and commercial use.

---

**Built for the Accenture AI Agent Showcase — Policy-as-Code Regulatory Change Agent**
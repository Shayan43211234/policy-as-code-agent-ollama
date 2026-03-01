# Policy-as-Code Regulatory Change Agent (Ollama - No API Key Needed)

A **free, local-first** AI agent that analyzes regulatory changes and generates updated policies using Ollama and Spring AI. No OpenAI key, no cloud dependencies—everything runs on your machine.

## 🎯 Features

- ✅ **100% Free** - Open-source stack with no paid services
- ✅ **No API Keys** - No OpenAI, Anthropic, or external dependencies
- ✅ **Local LLM** - Runs Ollama (llama3) on your machine
- ✅ **Spring AI Integration** - Modern Java framework for AI
- ✅ **Data Persistence** - H2 embedded database with automatic schema creation
- ✅ **LLM Result Caching** - Intelligent caching to avoid redundant LLM calls
- ✅ **Comprehensive Logging** - Full audit trail and debug logging for transparency
- ✅ **Clean Architecture** - Well-organized, production-ready structure
- ✅ **Fully Runnable** - Pre-configured and easy to start

## 📋 Prerequisites

Before running this project, ensure you have:

- **Java 21+** - [Download JDK](https://adoptopenjdk.net/)
- **Maven 3.8+** - [Download Maven](https://maven.apache.org/download.cgi)
- **Node.js 16+** - [Download Node.js](https://nodejs.org/)
- **Ollama** - [Download Ollama](https://ollama.com)

## 🚀 Quick Start

### Step 1: Start Ollama

```bash
# Install Ollama from https://ollama.com
# Then pull the llama3 model (one-time setup)
ollama pull llama3

# Start Ollama service (keep this running)
ollama serve
```

Ollama will run on `http://localhost:11434`

### Step 2: Run Backend

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

Backend API available at: `http://localhost:8080`

**Verify backend is working:**
- API Health: `http://localhost:8080/api/policy/debug/test-save` (debug endpoint)
- H2 Database Console: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:file:./data/policydb`
  - User: `sa` (no password)

### Step 3: Run Frontend

Open a new terminal:

```bash
cd frontend
npm install
npm run dev
```

Frontend available at: `http://localhost:5173`

## 📖 Usage

1. Open `http://localhost:5173` in your browser
2. Paste your **Existing Policy** (YAML format)
3. Paste the **New Regulation** text
4. Click "Analyze"
5. View the AI-generated analysis and updated policy

### Example Input

**Existing Policy:**
```yaml
name: DataAccessPolicy
version: "1.0"
rules:
  - role: admin
    resources: ["*"]
    action: "*"
```

**New Regulation:**
```
GDPR requires explicit user consent for data access. 
All data access must be logged.
```

**Expected Output:**
- Key changes identified
- Impact analysis
- Updated YAML policy with logging and consent requirements

## 🏗️ Project Structure

```
policy-as-code-agent-ollama/
├── backend/                           # Spring Boot 3.3 + Spring AI API
│   ├── pom.xml                        # Maven configuration (Java 21)
│   ├── data/                          # H2 Database files (auto-created)
│   │   ├── policydb.mv.db            # Database data
│   │   └── policydb.trace.db         # Database trace log
│   │
│   └── src/main/
│       ├── java/com/example/policyagent/
│       │   ├── PolicyAgentApplication.java
│       │   │
│       │   ├── controller/
│       │   │   └── PolicyController.java          # REST endpoints
│       │   │
│       │   ├── service/
│       │   │   ├── PolicyService.java             # Core LLM analysis + caching
│       │   │   ├── TicketService.java             # Ticket management
│       │   │   └── RegulatoryWorkflowService.java # Workflow orchestration
│       │   │
│       │   ├── entity/                            # JPA Entities
│       │   │   ├── RegulatoryUpdate.java
│       │   │   ├── RequirementEntity.java
│       │   │   ├── GapEntity.java
│       │   │   ├── PolicyDraftEntity.java
│       │   │   ├── CodeSpecificationEntity.java
│       │   │   ├── AuditLog.java
│       │   │   ├── TicketEntity.java
│       │   │   ├── RegulatoryStatus.java
│       │   │   └── TicketStatus.java
│       │   │
│       │   └── repository/                        # JPA Repositories
│       │       ├── RegulatoryUpdateRepository.java
│       │       ├── RequirementRepository.java
│       │       ├── GapRepository.java
│       │       ├── PolicyDraftRepository.java
│       │       ├── CodeSpecificationRepository.java
│       │       ├── AuditLogRepository.java
│       │       └── TicketRepository.java
│       │
│       └── resources/
│           └── application.yml                   # Spring Boot config
│
├── frontend/                          # React 18 + Vite 5 UI
│   ├── package.json
│   ├── index.html
│   ├── vite.config.js
│   ├── .env.example
│   │
│   └── src/
│       ├── main.jsx                  # React entry point
│       ├── App.jsx                   # Main component
│       ├── App.css                   # Styling
│       │
│       └── components/
│           ├── Tabs.jsx              # Tab navigation
│           ├── RequirementsPanel.jsx # Requirements display
│           └── Monitoring.jsx        # Monitoring & audit log
│
├── Documentation (Debugging & Validation)
│   ├── IMPLEMENTATION_COMPLETE.md      # Overview of persistence fix
│   ├── PERSISTENCE_DEBUG_GUIDE.md      # Detailed debugging guide
│   ├── QUICK_TEST_GUIDE.md             # Quick reference for testing
│   ├── CODE_CHANGES_SUMMARY.md         # Code modifications details
│   ├── VALIDATION_CHECKLIST.md         # Deployment validation steps
│   ├── PROJECT_TREE.md                 # Full project tree
│   ├── USE_CASES.md                    # Use case documentation
│   └── GITHUB_PUSH_GUIDE.md            # GitHub push instructions
│
└── Configuration
    ├── docker-compose.yml             # Docker compose setup
    └── .editorconfig                  # Editor formatting rules
```

## 🔧 Architecture

### Backend Stack
- **Spring Boot 3.3.2** - REST API framework
- **Spring AI 0.8.1** - AI/LLM integration
- **Spring Data JPA** - Database ORM layer
- **H2 Database** - Embedded SQL database for persistence
- **Ollama + llama3** - Local LLM provider (no API key required)
- **Java 21** - Latest Java runtime

**Key Capabilities:**
- Intelligent LLM result caching (LRU cache, 10-entry limit)
- H2 Console for database inspection at runtime
- Comprehensive logging (DEBUG level for app logic, SQL, parameters)
- Audit logging for all analysis operations
- Automatic schema creation and updates (Hibernate ddl-auto: update)
- Transaction support for data consistency

### Frontend Stack
- **React 18** - UI framework
- **Vite 5** - Lightning-fast bundler
- **Axios** - HTTP client
- **Tab-based interface** - Organized analysis views

## 🔌 API Endpoints & Tools

### Main Analysis Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/policy/requirements` | POST | Extract requirements from regulatory change |
| `/api/policy/gap-report` | POST | Generate gap analysis report |
| `/api/policy/policy-drafts` | POST | Generate updated policy drafts |
| `/api/policy/code-specs` | POST | Generate code specifications |
| `/api/policy/summary` | POST | Get analysis summary |

### Database & Debug Tools

| Tool | URL | Purpose |
|------|-----|---------|
| **H2 Database Console** | `http://localhost:8080/h2-console` | View/query database |
| **Debug Test Endpoint** | `GET http://localhost:8080/api/policy/debug/test-save` | Test H2 persistence |

### H2 Database Console

Access the database UI at: `http://localhost:8080/h2-console`

```
JDBC URL: jdbc:h2:file:./data/policydb
User: sa
Password: (leave blank)
```

**Useful Queries:**
```sql
-- View all analyses
SELECT * FROM regulatory_update ORDER BY id DESC;

-- View all requirements
SELECT * FROM requirement_entity ORDER BY id DESC;

-- View audit logs
SELECT * FROM audit_log ORDER BY timestamp DESC;

-- Count analyses
SELECT COUNT(*) as total_analyses FROM regulatory_update;
```

## 💾 Data Persistence

All analysis results are automatically persisted to H2 database:

- **Location:** `backend/data/policydb.mv.db` (auto-created on first run)
- **Tables:** Regulatory updates, requirements, gaps, policies, code specs, tickets, audit logs
- **Features:** Auto-generated schema, transaction support, concurrent access
- **Access:** Query via H2 Console or check `audit_log` table for operations

## 🐛 Troubleshooting

### "Connection refused" error
- Ensure Ollama is running: `ollama serve`
- Check Ollama is listening on `http://localhost:11434`

### "Model not found" error
- Pull the model: `ollama pull llama3`
- Verify it's installed: `ollama list`

### H2 Database issues
- **Can't access H2 Console:** Verify backend is running on port 8080
- **Database locked error:** Stop all Java processes and restart
- **Schema mismatch:** Delete `backend/data/` directory and restart (will recreate schema)
- **Data not persisting:** Check logs for `persistAnalysis() FAILED` messages

### Port already in use
- Backend: Change port in `application.yml` `server.port`
- Frontend: Vite will auto-increment if 5173 is busy

### Slow response times
- First request takes longer (model loading)
- Subsequent requests are faster
- Performance depends on your hardware

## 📦 Dependencies

All dependencies are free and open-source:
- Spring Framework (Apache 2.0)
- Ollama (MIT)
- React (MIT)
- Vite (MIT)

## 🚢 Deployment

This project is designed for:
- **Development**: Use locally as-is
- **Production**: Can be containerized with Docker (add your own Dockerfile)
- **CI/CD**: Standard Maven and npm pipelines

## 📝 Configuration

### Backend (`application.yml`)
```yaml
spring:
  application:
    name: policy-agent
  
  # LLM Configuration
  ai:
    ollama:
      base-url: http://localhost:11434  # Ollama server endpoint
      chat:
        options:
          model: llama3                  # Model to use (can change to other models)
  
  # Database Configuration
  datasource:
    url: jdbc:h2:file:./data/policydb  # H2 database location
    driverClassName: org.h2.Driver
    username: sa                        # Default user
    password: ""                        # Leave empty
  
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: update                  # Auto-create/update schema
    show-sql: true                      # Log SQL statements
  
  # H2 Console Configuration
  h2:
    console:
      enabled: true                     # Enable H2 Web Console
      path: /h2-console                 # Access at http://localhost:8080/h2-console

# Server Configuration
server:
  port: 8080                            # Change if needed

# Logging Configuration
logging:
  level:
    root: INFO
    com.example.policyagent: DEBUG      # Application-level logging
    org.hibernate.SQL: DEBUG             # SQL statement logging
```

### Frontend API Endpoint
Edit in `src/App.jsx`:
```javascript
const API_BASE = 'http://localhost:8080/api/policy';  // Change if backend is remote
```

### Change Ollama Model
To use a different model, first pull it:
```bash
ollama pull mistral
# Then update application.yml
```

## 🔍 Debugging & Monitoring

### Comprehensive Logging

The application includes extensive logging for debugging:

**Log Levels:**
- `DEBUG` - Application business logic, service layer
- `DEBUG` - Hibernate SQL statements and parameter binding
- `INFO` - General application flow

**Key Log Points:**
```
[INFO] === analyzeAndParse() START ===
[INFO] Cache MISS|HIT - calling LLM
[INFO] === safeParse() START ===
[INFO] JSON parsing succeeded
[INFO] === persistAnalysis() START ===
[INFO] RegulatoryUpdate saved successfully with ID: X
```

### Test H2 Persistence

Use the debug endpoint to test database independently:

```bash
curl http://localhost:8080/api/policy/debug/test-save
```

**Response:**
```json
{
  "success": true,
  "message": "H2 Persistence Test PASSED",
  "savedId": 1,
  "status": "DEBUG"
}
```

### Monitoring Execution

1. **Start backend with logging:** `mvn spring-boot:run`
2. **Watch console** for [INFO] log messages
3. **Query database** via H2 Console to verify persistence
4. **Check Audit Log** for all operations: `SELECT * FROM audit_log ORDER BY timestamp DESC`

### Documentation

Comprehensive guides available in project root:
- `PERSISTENCE_DEBUG_GUIDE.md` - Detailed debugging procedures
- `QUICK_TEST_GUIDE.md` - Quick reference for testing
- `IMPLEMENTATION_COMPLETE.md` - Overview of persistence layer
- `CODE_CHANGES_SUMMARY.md` - Technical code details
- `GITHUB_PUSH_GUIDE.md` - GitHub push instructions

## 🤝 Contributing

Pull requests welcome! Perfect for:
- Adding more LLM models
- Implementing policy versioning
- Extending analysis capabilities
- Enhancing UI/styling and components
- Adding deployment scripts
- Creating additional analysis endpoints
- Performance optimizations

## 📄 License

MIT License - Free for personal and commercial use

## 🎓 Learning Resources

- [Spring AI Documentation](https://docs.spring.io/spring-ai/docs/current/reference/)
- [Ollama Documentation](https://github.com/ollama/ollama)
- [React Documentation](https://react.dev/)
- [Policy-as-Code Concept](https://en.wikipedia.org/wiki/Policy_as_code)

---

**Built with ❤️ using Ollama and Spring AI**


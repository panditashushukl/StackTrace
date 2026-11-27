# StackTrace Buddy

**Live App:** <a href="https://stack-trace-neon.vercel.app" target="_blank" rel="noopener noreferrer">https://stack-trace-neon.vercel.app</a>

**StackTrace Buddy** is an AI-powered error diagnosis tool built to help developers understand and fix crashes faster. Instead of reading through long, nested error logs manually, you can paste a stack trace from any language (with optional code context or a language hint) and get a structured diagnosis—covering the error type, severity, plain-English summary, root cause, exact failing file and line, step-by-step fix, refactored code snippet, and preventive tips—rendered cleanly as Markdown.

## What the Project Does

- **Instant Error Diagnosis:** Paste a raw stack trace (up to 50,000 characters) and receive a structured breakdown formatted in Markdown with syntax-highlighted code fixes and one-click Copy / Export (`.md`).
- **Sensitive Data Protection:** Automatically hides passwords, API keys, Bearer tokens, JWTs, private keys, and database credentials from pasted traces before calling AI or saving to the database.
- **No Login Required (Optional Accounts):** Works immediately as a guest using an automatic session ID, or lets you sign in optionally using **Google OAuth2** or a **username/email and password** (with password updates directly in your profile).
- **Searchable History:** Browse **All History** (kept anonymous with no user details shown) or switch the dropdown to **My History** / **My Session**, filter by language, and search across stack trace keywords, error types, and file names.

---

## Technologies Used

### Frontend
| Technology | Role in This Project |
| :--- | :--- |
| **Angular 22 & TypeScript** | Runs the single-page web interface, reactive UI state, routing, and HTTP interceptor that attaches JWT tokens and guest session IDs automatically. |
| **Tailwind CSS** | Provides the clean, responsive styling, loading skeletons, and error banners. |
| **ngx-markdown & Marked** | Formats and renders the structured diagnostic report inside the output container as readable Markdown. |
| **PrismJS** | Adds multi-language syntax highlighting to code blocks in the rendered fix. |

### Backend
| Technology | Role in This Project |
| :--- | :--- |
| **Java 21 & Spring Boot 4** | Powers the REST API (`/api/v1/analyses`, `/api/v1/auth`, `/api/v1/users`), request validation, error handling, and 100 KB payload protection. |
| **Spring AI (Google GenAI)** | Connects the backend to Google Gemini, enforces the structured JSON response schema, and manages transient retry backoff and backup model fallback. |
| **Google Gemini API** | Analyzes multi-language stack traces and generates structured diagnoses, pinpointed locations, and code fixes. |
| **Spring Security & OAuth2 Client** | Handles stateless authentication, optional Google OIDC/OAuth2 login, and BCrypt password hashing while keeping core analysis endpoints open to guests. |
| **JJWT** | Generates and verifies signed JWT bearer tokens for logged-in users. |
| **Spring Data JPA & Hibernate** | Maps entities (`User`, `StackTrace`) and runs paginated keyword and language searches against PostgreSQL. |

### Database, Packaging & Testing
| Technology | Role in This Project |
| :--- | :--- |
| **PostgreSQL** | Persists user accounts and sanitized stack trace analyses, storing structured AI results in native `JSONB` for fast querying. |
| **Flyway** | Applies versioned SQL schema migrations automatically on startup so local and production databases stay in sync. |
| **Docker** | Packages the Spring Boot backend via a multi-stage Java 21 image (`Dockerfile`) ready for cloud hosting. |
| **JUnit 5, Mockito, Testcontainers & Vitest** | Tests backend endpoints and migrations against real PostgreSQL containers and verifies Angular components and auth guards. |

# GoalDone 🚀

GoalDone is a modern, full-stack monorepo application designed with an **OpenAPI-first** approach. It provides a robust foundation for goal management, leveraging a Spring Boot backend and an Angular frontend.

## 🏗️ Project Architecture

The project is organized as a monorepo to ensure tight coupling between the API specification, implementation, and infrastructure.

- **`api-spec/`**: The single source of truth. Contains the `openapi.yaml` definition used to generate DTOs and client services for both layers.
- **`backend/`**: Spring Boot application (Java 21) following a clean service-oriented architecture.
- **`frontend/`**: Angular (v21) application utilizing PrimeNG for a polished, consistent UI.
- **`infra/`**: Infrastructure configurations including Docker Compose, Traefik (Reverse Proxy), and Zitadel (IAM).
- **`api-testing/`**: API testing collections and workspace configurations.

## 🛠️ Technology Stack

### Backend
- **Framework:** Spring Boot 4.0.5
- **Language:** Java 21
- **Persistence:** Spring Data JPA with Hibernate & PostgreSQL/H2.
- **Migrations:** Liquibase for version-controlled database schema changes.
- **Security:** Spring Security with OAuth2/OpenID Connect (Zitadel).
- **Build Tool:** Maven.

### Frontend
- **Framework:** Angular 21
- **Styling:** TailwindCSS & PrimeUI Themes.
- **UI Library:** **PrimeNG** (Exclusive component library).
- **API Client:** Auto-generated via OpenAPI Generator.

### Infrastructure & DevOps
- **IAM:** Zitadel for identity and access management.
- **Gateway:** Traefik for routing and TLS termination.
- **Containerization:** Docker & Docker Compose.
- **CI/CD:** GitHub Actions for automated testing and deployment.

## 🚀 Getting Started

### Prerequisites
- Docker & Docker Compose
- Java 21 & Maven
- Node.js & npm

### Local Development

#### Option 1: All-in-one (Recommended)
The project includes an `mprocs.yaml` configuration to start the infrastructure, backend, and frontend simultaneously in a single terminal view.

- **MacOS / Linux:**
  ```bash
  # Erstelle eine Kopie von .env-example und benenne sie .env um, 
  # fehlende Felder müssen ausgefüllt werden
  ./run-local.sh
  ```
- **Windows (PowerShell):**
  ```powershell
  # Erstelle eine Kopie von .env-example und benenne sie .env um, 
  # fehlende Felder müssen ausgefüllt werden
  ./run-local.ps1
  ```
*Requires [mprocs](https://github.com/pvolok/mprocs) to be installed.*

#### Option 2: Manual Start

1. **Run Backend:**
   ```bash
   cd backend
   # Env Variablen müssen gesetzt werden, siehe .env.example
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```

2**Run Frontend:**
   ```bash
   cd frontend
   npm install
   # Env Variablen müssen gesetzt werden. siehe .env.example
   npm run setup-env
   npm run generate-api  # Generate API services from spec
   npm start
   ```

## 📏 Development Principles

- **OpenAPI-First:** Any change to the API contract MUST start in `api-spec/openapi.yaml`.
- **PrimeNG Only:** The frontend exclusively uses PrimeNG components. No custom UI components should be created.
- **Type Safety:** Use generated DTOs and interfaces to maintain consistency across the stack.
- **Database Migrations:** Every database change must be documented in a Liquibase changelog.

## 🧪 Testing

- **Backend:** JUnit 5, MockMvc, and AssertJ. Run with `./mvnw test`.
- **Frontend:** Vitest and Angular TestBed. Run with `npm test`.
- **API:** Testing collections are available in the `api-testing/` directory.

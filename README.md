# DevPilot

DevPilot is a full-stack AI-powered coding assistant that connects to your GitHub account and provides professional technical assistance regarding your repositories. It allows you to Bring Your Own Key (BYOK) for various AI providers, supporting OpenAI, Anthropic, Gemini, and OpenRouter.

## Prerequisites

- Node.js 18+ (for Next.js frontend)
- Java 21+ (for Spring Boot backend)
- Maven (included as `mvnw` wrapper in `backend/`)
- Docker & Docker Compose (for PostgreSQL + pgvector)

## Project Structure

- `/client` - Next.js 14 frontend (React, Tailwind CSS, TypeScript)
- `/backend` - Spring Boot 3.3 backend (Java 21, Spring AI, PostgreSQL)
- `/docker` - Docker configurations for the database

## Getting Started

### 1. Database Setup

DevPilot requires a PostgreSQL database with the `pgvector` extension for AI embeddings. You can spin this up easily using Docker.

```bash
# In the root directory (where docker-compose.yml is located)
docker-compose up -d
```
*Note: This starts PostgreSQL on port `5433` (as configured in docker-compose.yml) to avoid conflicts with any local Postgres instances you might have running.*

### 2. Backend Setup

1. Navigate to the backend directory:
   ```bash
   cd backend
   ```
2. Configure environment variables:
   Copy `.env.example` to `.env` and fill in your GitHub OAuth credentials.
   ```bash
   cp .env.example .env
   ```
3. Start the Spring Boot server:
   ```bash
   # On Windows (PowerShell)
   .\run-backend.ps1
   
   # Or using Maven directly
   .\mvnw spring-boot:run
   ```
   *The backend will automatically run Flyway database migrations and start on `http://localhost:8080`.*

### 3. Frontend Setup

1. Navigate to the client directory:
   ```bash
   cd client
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Configure environment variables (optional):
   The frontend defaults to `http://localhost:8080` for the API. If you need to change this, copy `.env.local.example` to `.env.local`.
   ```bash
   cp .env.local.example .env.local
   ```
4. Start the development server:
   ```bash
   npm run dev
   ```
   *The frontend will be available at `http://localhost:3000`.*

## AI Configuration (Bring Your Own Key)

By default, the backend uses its own environment variables for AI models. However, DevPilot supports full BYOK functionality directly from the UI.

1. Open `http://localhost:3000` and log in with GitHub.
2. Navigate to **Settings**.
3. Under the **AI Configuration (BYOK)** section, choose your provider (OpenAI, Gemini, Anthropic, or OpenRouter).
4. Enter the specific model name and your API key.
5. Your API key will be securely encrypted at rest in the database, and DevPilot will use it for all your chat requests.

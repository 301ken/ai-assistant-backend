# Database Setup Guide

This document provides detailed instructions for setting up different database configurations for the AI Scheduler Backend.

## Table of Contents

1. [H2 In-Memory (Development)](#h2-in-memory-development)
2. [PostgreSQL (Production-Ready)](#postgresql-production-ready)
3. [Docker + PostgreSQL (Recommended)](#docker--postgresql-recommended)
4. [Troubleshooting](#troubleshooting)

---

## H2 In-Memory (Development)

### When to Use

- **Local development** without external dependencies
- **Quick testing** of API endpoints
- **Learning** the application structure
- **Single-user** scenarios

### Setup

**No setup required!** The app defaults to H2 in-memory.

```bash
./mvnw spring-boot:run
```

### Pros & Cons

| ✅ Pros | ❌ Cons |
|---------|---------|
| Zero setup | Data lost on restart |
| Fast | Single user only |
| No external service | Not shareable across apps |

### Access H2 Console

View and query data in the H2 web console:

```
URL: http://localhost:8085/h2-console
JDBC URL: jdbc:h2:mem:ai_scheduler
User: sa
Password: (leave blank)
```

---

## PostgreSQL (Production-Ready)

### When to Use

- **Persistent data** across application restarts
- **Multiple users** accessing the same database
- **Production deployments**
- **Team collaboration** on local development

### Installation

#### Windows

1. Download from [postgresql.org](https://www.postgresql.org/download/windows/)
2. Run the installer
3. Remember the password for the `postgres` user
4. Verify installation:
   ```cmd
   psql --version
   ```

Or use Chocolatey:
```cmd
choco install postgresql
```

#### macOS

```bash
# Using Homebrew
brew install postgresql@15
brew services start postgresql@15

# Verify
psql --version
```

#### Linux (Ubuntu/Debian)

```bash
# Install PostgreSQL
sudo apt-get update
sudo apt-get install postgresql postgresql-contrib

# Start service
sudo systemctl start postgresql
sudo systemctl enable postgresql

# Verify
psql --version
```

### Create Database & User

```bash
# Connect to PostgreSQL as superuser
psql -U postgres

# Create database
CREATE DATABASE ai_scheduler ENCODING 'UTF8';

# Create user
CREATE USER ai_user WITH PASSWORD 'ai_password';

# Grant privileges
ALTER ROLE ai_user SET client_encoding TO 'utf8';
ALTER ROLE ai_user SET default_transaction_isolation TO 'read committed';
ALTER ROLE ai_user SET default_transaction_deferrable TO on;
GRANT ALL PRIVILEGES ON DATABASE ai_scheduler TO ai_user;

# Exit
\q
```

### Configure Application

Create or edit `.env` file:

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ai_scheduler
SPRING_DATASOURCE_USERNAME=ai_user
SPRING_DATASOURCE_PASSWORD=ai_password

# Other configurations
OPENAI_API_KEY=sk-...
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
MAIL_USERNAME=...
MAIL_PASSWORD=...
```

### Run Application

```bash
# macOS / Linux
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

### Verify Connection

Check the logs for:
```
INFO  com.zaxxer.hikari.HikariDataSource - HikariPool-1 - Start completed.
```

Or connect directly:
```bash
psql -U ai_user -d ai_scheduler -c "\dt"
```

### Useful PostgreSQL Commands

```bash
# Connect to database
psql -U ai_user -d ai_scheduler

# List all tables
\dt

# Describe a table
\d table_name

# Run a query
SELECT * FROM users LIMIT 5;

# Exit
\q
```

---

## Docker + PostgreSQL (Recommended)

### Prerequisites

- **Docker** installed ([docker.com](https://docker.com))
- **docker-compose** (included with Docker Desktop)

### Quick Start

1. Ensure `docker-compose.yml` exists in the project root
2. Run:
   ```bash
   docker-compose up -d --build
   ```
3. Check logs:
   ```bash
   docker-compose logs -f backend
   ```
4. Access the API:
   ```
   http://localhost:8085
   ```

### Configuration

The `docker-compose.yml` includes:

- **PostgreSQL 15** container with persistent volume
- **AI Scheduler Backend** container
- **Health checks** to ensure PostgreSQL is ready
- **Environment variables** pre-configured

### Useful Commands

```bash
# Start services in background
docker-compose up -d --build

# View logs (all services)
docker-compose logs

# View logs (backend only, follow)
docker-compose logs -f backend

# Stop services
docker-compose stop

# Stop and remove containers
docker-compose down

# Remove containers AND volumes (clean state)
docker-compose down -v

# Restart a service
docker-compose restart backend

# Execute command in running container
docker-compose exec backend bash

# Connect to PostgreSQL directly
docker-compose exec postgres psql -U ai_user -d ai_scheduler
```

### Environment Variables for Docker

You can override environment variables when running docker-compose:

```bash
# Set custom OpenAI key
OPENAI_API_KEY=sk-... docker-compose up -d

# Set multiple variables
export OPENAI_API_KEY=sk-...
export GOOGLE_CLIENT_ID=your-id
docker-compose up -d
```

### Volume Persistence

The `docker-compose.yml` creates a named volume `postgres_data` that persists across container restarts:

```bash
# List volumes
docker volume ls

# Inspect volume
docker volume inspect ai_scheduler-postgres_data

# Clean up volumes
docker volume prune
```

---

## Troubleshooting

### PostgreSQL Connection Issues

**Error:** `FATAL: Ident authentication failed for user "ai_user"`

**Solution:** Ensure you're using the correct password and host:
```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ai_scheduler
SPRING_DATASOURCE_USERNAME=ai_user
SPRING_DATASOURCE_PASSWORD=ai_password
```

**Error:** `Connection refused: localhost:5432`

**Solution:** PostgreSQL service is not running:
```bash
# macOS
brew services start postgresql@15

# Linux
sudo systemctl start postgresql

# Windows
# Restart the PostgreSQL service via Services app
```

### Docker Issues

**Error:** `Port 5432 is already in use`

**Solution:** Stop the conflicting service or use a different port:
```bash
# Option 1: Stop PostgreSQL service
sudo systemctl stop postgresql  # Linux
brew services stop postgresql@15  # macOS

# Option 2: Use different port in docker-compose.yml
# Change "5432:5432" to "5433:5432"
```

**Error:** `Cannot connect to postgres at startup`

**Solution:** Ensure health check passes before starting backend:
```bash
# Wait longer
docker-compose logs postgres

# If postgres is stuck, restart
docker-compose restart postgres
```

### Data Not Persisting

**H2 In-Memory:** This is expected behavior. Use PostgreSQL for persistent data.

**PostgreSQL:** Check that the volume is being used:
```bash
docker volume inspect ai_scheduler-postgres_data
# Should show Mount Point and data
```

### Switching Between H2 and PostgreSQL

To switch from PostgreSQL back to H2:

```bash
# Remove PostgreSQL config from .env or comment it out
# SPRING_DATASOURCE_URL=jdbc:postgresql://...

# Or explicitly set H2
SPRING_DATASOURCE_URL=jdbc:h2:mem:ai_scheduler

./mvnw spring-boot:run
```

---

## Summary

| Setup | Persistence | Multi-User | Setup Time | Best For |
|-------|:----------:|:----------:|:----------:|----------|
| **H2 In-Memory** | ❌ | ❌ | < 1 min | Quick testing |
| **PostgreSQL Local** | ✅ | ✅ | 10-15 min | Local dev team |
| **Docker Compose** | ✅ | ✅ | 5 min | Consistent environment |
| **Production** | ✅ | ✅ | 20-30 min | Cloud deployment |

---

## Resources

- [PostgreSQL Official Documentation](https://www.postgresql.org/docs/)
- [Docker Documentation](https://docs.docker.com/)
- [Spring Data JPA Reference](https://spring.io/projects/spring-data-jpa)
- [H2 Database Documentation](https://www.h2database.com/)

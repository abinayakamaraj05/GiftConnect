# GiftConnect – Loved Ones Gifting Platform

A college capstone project: a web-based gifting platform where users can browse gifts, add them to a cart, place orders, and track order status.

## Status: Day 1 — Foundation + User Management

This is an incremental build. Day 1 only covers:
- Project foundation (Spring Boot + Maven + MySQL wiring)
- User entity, repository, service, controller
- Registration + basic user lookup APIs
- Static landing page (HTML/CSS/JS, no framework)

Later days will add gifts, cart, orders, and payments.

## Tech Stack

- Backend: Java 17, Spring Boot 3.2, Spring Data JPA
- Build tool: Maven
- Database: MySQL (`loved_ones_gifting`)
- Frontend: HTML, CSS, vanilla JavaScript
- API style: REST

## Prerequisites

- Java 17+ installed (`java -version`)
- Maven (or use the included wrapper once generated — see note below)
- MySQL Server running locally with the `loved_ones_gifting` database created from `database/schema.sql`

## Setup

### 1. Set the database password as an environment variable

**macOS / Linux (bash/zsh):**
```bash
export DB_PASSWORD=your_mysql_password
```

**Windows (PowerShell):**
```powershell
$env:DB_PASSWORD="your_mysql_password"
```

**Windows (VS Code terminal, cmd):**
```cmd
set DB_PASSWORD=your_mysql_password
```

This must be set in the same terminal session before you run the app.

### 2. Verify MySQL is running and the database exists

```sql
CREATE DATABASE IF NOT EXISTS loved_ones_gifting;
```
(Your `database/schema.sql` should already define the `users` table and others — run it if you haven't.)

## Running the Application

If you don't have the Maven wrapper (`mvnw`/`mvnw.cmd`) yet, generate it once with:
```bash
mvn -N wrapper:wrapper
```

**macOS / Linux:**
```bash
./mvnw spring-boot:run
```

**Windows (VS Code terminal):**
```cmd
.\mvnw.cmd spring-boot:run
```

The app starts on **http://localhost:8080**. Open that URL in a browser to see the landing page.

## API Testing

### Register a user
```bash
curl -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test User",
    "email": "test@example.com",
    "password": "password123",
    "phone": "9876543210",
    "address": "Trichy"
  }'
```
Expected: `201 Created` with the saved user (password omitted from the response).

Registering the same email twice returns `409 Conflict`.

### Get all users
```bash
curl http://localhost:8080/api/users
```
Expected: `200 OK` with a JSON array of users.

### Get a user by id
```bash
curl http://localhost:8080/api/users/1
```
Expected: `200 OK` with the user, or `404 Not Found` if the id doesn't exist.

## Common Errors & Fixes

| Error | Likely Cause | Fix |
|---|---|---|
| `Communications link failure` | MySQL isn't running, or wrong host/port | Start MySQL; confirm it's on `localhost:3306` |
| `Access denied for user 'root'@'localhost'` | Wrong password or `DB_PASSWORD` not set | Re-export `DB_PASSWORD` in the same terminal you run the app from |
| `Unknown database 'loved_ones_gifting'` | Database not created yet | Run `database/schema.sql` against MySQL first |
| `Table 'users' doesn't exist` | schema.sql not applied | Apply `database/schema.sql` before starting the app |
| Port `8080` already in use | Another process is using it | Stop that process, or set `server.port=8081` in `application.properties` |
| `mvnw: command not found` / `.\mvnw.cmd` fails | Wrapper not generated yet | Run `mvn -N wrapper:wrapper` once (requires Maven installed) |

## Git Commands for Day 1

```bash
git add .
git commit -m "Day 1: project foundation and User Management module"
git push origin main
```

If this is the very first push to the repo:
```bash
git init
git remote add origin <your-repo-url>
git add .
git commit -m "Day 1: project foundation and User Management module"
git branch -M main
git push -u origin main
```

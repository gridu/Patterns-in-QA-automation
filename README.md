# Patterns in QA Automation — Course Playground

This repository contains a full-stack e-commerce System Under Test (SUT) and an automated test suite designed for practical refactoring exercises in the **Patterns in QA Automation** course.

---

## Architecture Overview

- **`sut/`**: System Under Test — FastAPI backend, vanilla JavaScript/HTML frontend, PostgreSQL database.
- **`tests`**: Automated end-to-end test suite built with **Java 25**, **JUnit 6**, **Gradle**, and **Selenium 4**. Tests cover API, UI, and hybrid omnichannel scenarios.

---

## Quick Start

### 1. Start the System Under Test (SUT)

#### Docker

```bash
cd sut
docker compose up -d --build
```
To stop the running containers:

```bash
docker compose down
```

#### Local

1. **Create and activate a virtual environment**:
   ```bash
   cd sut
   python3 -m venv .venv
   source .venv/bin/activate
   ```

2. **Install dependencies**:
   ```bash
   pip install -r requirements.txt
   ```

3. **Start the server with SQLite**:
   ```bash
   DATABASE_URL="sqlite:///./shop.db" uvicorn app.main:app --reload --port 8000
   ```

**WARNING** The initial test implementation will fail against the locally running SUT.   
Connection string for tests: `jdbc:sqlite:./sut/shop.db`

---

- **Web Application**: [http://localhost:8000](http://localhost:8000)
    - API Swagger docs [http://localhost:8000/docs](http://localhost:8000/docs)
- **PostgreSQL Database**: `localhost:5432` (DB `shop_db`)
    - User/Password: `postgres`/`postgres_password`

---

### 2. Run the Automated Tests

All tests are executed using the Gradle wrapper:

```bash
./gradlew test
```

---

## Viewing Test Results

After running the tests, an HTML report is generated at:

```bash
open build/reports/tests/test/index.html
```

---
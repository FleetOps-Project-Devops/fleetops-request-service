# 📋 FleetOps Request Service

The Request Service orchestrates the **Service Request workflow** within the FleetOps Vehicle Maintenance Platform. It handles the lifecycle of maintenance and breakdown reports, from initiation to completion.

## 🛠️ Tech Stack
*   **Framework:** Spring Boot 3.4
*   **Database:** PostgreSQL (uses `request_db`)
*   **Inter-Service Communication:** Spring `RestClient`
*   **Authentication:** Stateless JWT (Validated via `JwtAuthenticationFilter`)

## 🎯 Responsibilities
*   **Workflow State Machine:** Manages the transition of requests through `OPEN` -> `PENDING_APPROVAL` -> `IN_PROGRESS` -> `COMPLETED`.
*   **Vehicle Sync:** Communicates with the `vehicle-service` to transition a vehicle's status (e.g., to `IN_SERVICE`) when a request is approved.
*   **Task Finalization:** Converts pending tasks from the `maintenance-service` into a formal Service Request.

## 📡 API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/requests/place` | JWT | Formalize queued tasks into a Service Request |
| `GET` | `/requests` | JWT | Get current user's request history or all requests (Manager/Admin) |
| `PATCH` | `/requests/{id}/status` | MANAGER/ADMIN | Progress the state of a service request |

## 🚀 Running Locally

### Prerequisites
*   Java 21
*   Maven
*   PostgreSQL running locally (with `request_db` created)
*   `vehicle-service` and `maintenance-service` running (required for full workflow integration)

### Environment Variables
```bash
export JWT_SECRET=your-super-secret-key-minimum-32-chars
export VEHICLE_SERVICE_URL=http://localhost:8081  # Adjust port as necessary
export MAINTENANCE_SERVICE_URL=http://localhost:8082 # Adjust port as necessary
./mvnw spring-boot:run
```

## 🐳 Docker

```bash
docker build -t fleetops-request-service:v1.0.0 .
```

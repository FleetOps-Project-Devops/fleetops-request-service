# 📦 CloudCart Order Service

The Order Processing service for the CloudCart E-commerce platform. It acts as an orchestrator during the checkout process, communicating with the Product and Cart services to ensure transactional integrity.

## 🛠️ Tech Stack
*   **Framework:** Spring Boot 3.4
*   **Database:** PostgreSQL (uses `order_db`)
*   **HTTP Client:** Spring `RestClient` for inter-service communication
*   **Security:** JWT Validation (Stateless)

## 🎯 Responsibilities
*   **Dual-Mode Checkout:** Supports placing orders directly via "Buy Now" (single item, bypassing the cart) or via "Cart Checkout" (processing all items in the user's cart).
*   **Stock Validation:** Synchronously verifies product availability with the Product Service before confirming an order.
*   **Atomic Stock Deduction:** Requests the Product Service to decrement stock, handling potential `409 Conflict` errors if stock is insufficient.
*   **Cart Cleanup:** Automatically instructs the Cart Service to clear the user's cart upon a successful cart-based checkout.
*   **Price Snapshotting:** Records the price of each item at the exact time of purchase (`priceAtTime`) for accurate historical records.

## 📡 API Endpoints

| Method | Endpoint | Auth Required | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/orders/place` | Yes (JWT) | Place a new order. Payload specifies `type` (`BUY_NOW` or `CART`). |
| `GET` | `/orders` | Yes (JWT) | Retrieve the authenticated user's order history. |
| `GET` | `/orders/{id}` | Yes (JWT) | Retrieve details for a specific order (owner only). |

## 🚀 Running Locally

### Prerequisites
*   Java 17+
*   Maven
*   PostgreSQL running locally (with `order_db` created)
*   **Product Service** running (default: `http://localhost:8081`)
*   **Cart Service** running (default: `http://localhost:8082`)

### Environment Variables

```bash
export JWT_SECRET=your-super-secret-key-minimum-32-chars
export PRODUCT_SERVICE_URL=http://localhost:8081
export CART_SERVICE_URL=http://localhost:8082
mvn spring-boot:run
```

## 🐳 Docker

```bash
docker build -t cloudcart-order-service:v1.0.0 .
```

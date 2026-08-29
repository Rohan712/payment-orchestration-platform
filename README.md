# Payment Orchestration Platform

Event-driven payment processing platform built using
Java, Spring Boot, Kafka, PostgreSQL, Docker and Kubernetes.

## Project Structure
payment-orchestration-platform/
│
├── gateway/
│
├── payment-service/
│
├── integration-service/
│
├── user-service/
│
├── notification-service/
│
├── audit-service/
│
├── docs/
│
├── docker-compose.yml
│
└── README.md
## Services

| Service | Responsibility |
|---|---|
| Gateway | Authentication and API routing |
| Payment | Payment lifecycle and Stripe interaction |
| Integration | Stripe webhook processing |
| User | User management |
| Notification | Payment notifications |
| Audit | Transaction audit trail |

## Payment Flow

Client
│
▼
API Gateway
│
│ JWT Validation
▼
Payment Service
│
│ Create PaymentIntent
▼
Stripe
│
│ Webhook
▼
Integration Service
│
│ Publish Payment Status Event
▼
Kafka
│
├───────────────┐
▼               ▼
Notification     Audit
Service          Service

## Key Engineering Decisions

- Idempotency keys to prevent duplicate payments
- Kafka for asynchronous event propagation
- JWT authentication at API Gateway
- PostgreSQL for payment state
- MongoDB for audit records
- Dead-letter handling for failed events
- Unit and integration testing


## Configuration
Configure the required environment variables before running the services.
STRIPE_SECRET_KEY=
STRIPE_WEBHOOK_SECRET=
JWT_SECRET=
DB_USERNAME=
DB_PASSWORD=
DB_URL=



## API Documentation

Refer openapi.yml for API specifications and endpoints.

## Design Goals

The system was designed around the following principles:

1. Microservice separation of responsibilities
2. Event-driven communication
3. Idempotent payment processing
4. Secure API access
5. Asynchronous payment-status processing
6. Transaction auditing
7. Extensible notification handling
8. Automated testing
9. Containerized deployment
...
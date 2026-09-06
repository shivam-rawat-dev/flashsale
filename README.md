# ⚡ FlashSale - Distributed Inventory Allocation Engine

A high-concurrency, low-latency distributed flash sale backend engine built with **Spring Boot 3**, **PostgreSQL**, **Redis** (atomic Lua reservation), **RabbitMQ** (delayed exchange for TTL rollback), and **Apache Kafka** (order processing & event sourcing).

---

## 📖 Swagger / OpenAPI Documentation

Interactive OpenAPI 3.0 documentation is built-in:
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON Spec**: `http://localhost:8080/v3/api-docs`

---

## 🚀 CI/CD Pipeline (GitHub Actions ➔ AWS ECS)

The project includes an automated, production-grade CI/CD pipeline configured at [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml).

### Pipeline Stages

1. **Continuous Integration (`ci-build-and-test`)**:
   - Runs on every `push` to `main` and all `pull_request` events to `main`.
   - Sets up JDK 17 with Maven dependency caching.
   - Executes automated tests and compiles release JAR artifacts.

2. **Continuous Delivery (`cd-deploy-to-aws`)**:
   - Triggers automatically after tests pass on `main` branch push or via manual `workflow_dispatch`.
   - Authenticates to AWS using **OpenID Connect (OIDC)** (or fallback IAM access keys).
   - Authenticates to **Amazon ECR** (Elastic Container Registry).
   - Builds optimized multi-stage Docker container with GitHub Actions caching (`gha`).
   - Tags container image with both immutable Git commit SHA (`${{ github.sha }}`) and `latest`.
   - Renders and deploys the updated task definition to **Amazon ECS Fargate** with zero-downtime rolling deployment.

---

### ⚙️ GitHub Secrets & Variables Configuration

Configure the following under **GitHub Repository Settings ➔ Secrets and variables ➔ Actions**:

#### 1. Repository Secrets (`Secrets`)
| Secret Name | Description | Example / Recommended |
|---|---|---|
| `AWS_ROLE_TO_ASSUME` | *(Recommended)* AWS IAM Role ARN for GitHub OIDC | `arn:aws:iam::123456789012:role/GitHubActionsECRDeploymentRole` |
| `AWS_ACCESS_KEY_ID` | *(Alternative)* AWS IAM User Access Key | `AKIA...` (if not using OIDC) |
| `AWS_SECRET_ACCESS_KEY` | *(Alternative)* AWS IAM User Secret Key | `wJalrXUtnFEMI...` (if not using OIDC) |

#### 2. Repository Variables (`Variables`)
| Variable Name | Default Value | Description |
|---|---|---|
| `AWS_REGION` | `us-east-1` | Target AWS deployment region |
| `ECR_REPOSITORY` | `flashsale-backend` | Amazon ECR repository name |
| `ECS_CLUSTER` | `flashsale-cluster` | Amazon ECS cluster name |
| `ECS_SERVICE` | `flashsale-service` | Amazon ECS Fargate service name |

---

## 🐳 Local Development Setup

Run the full infrastructure stack locally via Docker Compose:

```bash
cd flashsale
docker-compose up -d postgres redis rabbitmq kafka
./mvnw spring-boot:run
```

---
status: proposed
# date: 2025-11-15
# decision-makers: {Hyphae APIS development team}
---
# Use Microservice Architecture for OpenPAYGO Token Integration with Hyphae APIS

## Context and Problem Statement

Hyphae APIS (Autonomous Power Interchange System) requires integration with OpenPAYGO token validation to enable pay-as-you-go autonomous power exchange within distributed microgrids. The system needs to validate cryptographic tokens before authorizing energy sharing requests, track energy consumption in real-time, and deduct consumed kWh from user token balances.

The core question is: Should OpenPAYGO token validation be embedded as a Vert.x verticle within the existing apis-main Java application, or should it be deployed as a separate microservice?

## Decision Drivers

* Language flexibility for OpenPAYGO SDK integration (Python vs Java)
* Network latency for token validation operations
* Deployment complexity and operational overhead
* Development velocity and maintainability
* Independent scaling capabilities
* Fault isolation and system resilience
* Testing and debugging ease
* Technology stack consistency

## Considered Options

* Embedded Token Service (OpenPAYGO validation as Vert.x verticle within apis-main)
* Microservice Architecture (Token Service as separate Python/FastAPI service)

## Decision Outcome

Chosen option: "Microservice Architecture", because it enables direct use of the official OpenPAYGO-python SDK without Java-Python bridging complexity, provides better fault isolation to prevent token service issues from affecting core energy trading operations, allows independent scaling and deployment cycles, and offers superior development velocity by allowing Python developers to work on token validation while Java developers maintain APIS core functionality.

### Consequences

* Good, because developers can use the official OpenPAYGO-python library directly without JNI bridges or reimplementation
* Good, because the token service can be developed, tested, and deployed independently from APIS core
* Good, because FastAPI provides excellent async performance suitable for high-frequency token operations
* Good, because fault isolation ensures token service failures don't crash the entire APIS system
* Good, because the token service can scale independently based on validation load
* Good, because Python's rapid development cycle accelerates feature iteration for payment integration
* Good, because the service can be reused across multiple APIS clusters or other energy platforms
* Good, because testing and debugging are simpler with language-native tooling
* Bad, because introduces network latency (estimated 10-50ms additional overhead per validation)
* Bad, because requires additional deployment infrastructure (container orchestration, service discovery)
* Bad, because increases operational complexity with multiple services to monitor and maintain
* Bad, because requires network reliability considerations and retry logic for resilience

### Confirmation

The implementation can be confirmed through:
* Integration testing demonstrating successful token validation across service boundaries
* Performance testing showing token validation latency remains under 100ms end-to-end
* Load testing validating the token service can handle expected transaction volumes (minimum 100 validations/second per cluster)
* Failure testing confirming APIS continues energy operations when token service is temporarily unavailable
* Security audit verifying secure communication between APIS and token service (mTLS, authentication)
* Deployment testing across development, staging, and production environments
* Monitoring dashboards showing service health metrics (response times, error rates, availability)

## Pros and Cons of the Options

### Embedded Token Service

Deploy OpenPAYGO validation as a Vert.x verticle embedded within the apis-main Java application using either JNI bridges to call Python code or reimplementation of OpenPAYGO in Java.

* Good, because single deployment artifact simplifies operations and reduces infrastructure requirements
* Good, because eliminates network latency for token validation calls (in-process communication)
* Good, because shares Hazelcast distributed memory with APIS for efficient state management
* Good, because reduces moving parts in the deployment architecture
* Good, because simplifies development environment setup (single application to run)
* Bad, because requires complex JNI bridge between Java (APIS) and Python (OpenPAYGO library)
* Bad, because alternative Java reimplementation of OpenPAYGO introduces maintenance burden and divergence risk
* Bad, because token service bugs or performance issues can crash the entire APIS application
* Bad, because requires rebuilding and redeploying entire APIS for token service updates
* Bad, because mixing Python and Java in single process complicates debugging and profiling
* Bad, because limited ability to scale token validation independently from energy trading operations
* Bad, because constrains team structure (requires developers comfortable with both Java and Python integration)

### Microservice Architecture

Deploy Token Service as a separate Python/FastAPI application communicating with apis-main via REST/HTTP.

* Good, because enables direct use of official OpenPAYGO-python library without language bridging
* Good, because provides strong fault isolation - token failures don't affect energy trading operations
* Good, because allows independent deployment and version management for token service
* Good, because enables independent horizontal scaling based on token validation load
* Good, because FastAPI provides excellent async/await performance for I/O-bound operations
* Good, because simplifies team organization - Python developers own token service, Java developers own APIS
* Good, because service can be reused across multiple APIS clusters or integrated with other platforms
* Good, because easier testing with language-native tools (pytest, requests) and mock services
* Good, because enables gradual migration strategy - can develop alongside existing system
* Good, because PostgreSQL/MongoDB can be optimized specifically for token transaction patterns
* Bad, because introduces 10-50ms network latency per token validation request
* Bad, because requires additional infrastructure (container orchestration, load balancer, service mesh)
* Bad, because increases operational complexity with separate monitoring, logging, and alerting
* Bad, because requires implementing retry logic and circuit breakers for network resilience
* Bad, because adds service discovery and configuration management requirements
* Bad, because requires securing inter-service communication (mTLS certificates, API authentication)
* Bad, because network partitions between services can cause temporary validation failures

## More Information

### Implementation Timeline

**Microservice Approach:**
* Week 1-2: Token Service core (FastAPI, OpenPAYGO integration, database schema)
* Week 3-4: TokenServiceClient in apis-main with REST communication
* Week 5-6: APIS service modifications (User, Controller, Mediator)
* Week 7-8: Testing, security hardening, deployment automation

**Embedded Approach:**
* Week 1-3: JNI bridge development or Java OpenPAYGO reimplementation
* Week 4-6: Integration with Vert.x event bus and APIS services
* Week 7-8: Testing and debugging cross-language issues

### Related Patterns

The microservice architecture aligns with proven distributed energy system patterns:
* Energy Web Foundation's modular blockchain platform architecture
* IOTA/Tangle's separated transaction and power flow validation chains
* Smart meter systems with dedicated payment gateways (Kenya Power, M-KOPA)

### Performance Considerations

Based on APIS production deployment at Okinawa Institute of Science and Technology (19 residences, 170 kWh monthly peer-to-peer trading):

* **Expected validation frequency**: 10-20 validations per hour per node (charging requests)
* **Consumption reporting frequency**: Every 0.1 kWh (approximately 6-12 reports per 1-hour charge cycle)
* **Network latency budget**: 50ms acceptable given APIS energy transfers occur over minutes/hours
* **Token service target SLA**: 99.9% availability, sub-100ms p99 response time

### Technology Stack Justification

**Python/FastAPI chosen for Token Service because:**
* Official OpenPAYGO-python library is production-proven (600,000+ households deployed)
* FastAPI provides 2-3x better async performance than Flask/Django for I/O-bound operations
* Python's ecosystem offers superior cryptography libraries (cryptography, PyCryptodome)
* Rapid development cycle enables quick iteration on payment integration features
* Strong PostgreSQL/MongoDB support for transaction logging and analytics

**PostgreSQL chosen for Token Service database because:**
* ACID compliance critical for financial token balances
* Row-level locking prevents race conditions during concurrent consumption reporting
* JSON support for flexible device metadata and transaction details
* Excellent performance for token validation queries (sub-10ms with proper indexes)

### Migration Strategy

If starting with embedded approach and facing scaling issues, migration to microservice would require:
1. Extract token validation logic to separate service
2. Implement REST API endpoints
3. Replace in-process calls with HTTP client in apis-main
4. Deploy token service alongside APIS
5. Gradual rollout with feature flag to switch between embedded/microservice

Starting with microservice architecture provides flexibility to later embed if network latency becomes prohibitive, but early operational experience suggests latency is acceptable for energy trading timescales (minutes/hours vs milliseconds).

### Security Considerations

Both architectures require:
* Secret key encryption at rest (AES-256)
* Secure key storage (HashiCorp Vault or AWS Secrets Manager)
* Audit logging for all token operations
* Rate limiting to prevent brute-force attacks

Microservice architecture additionally requires:
* mTLS for service-to-service communication
* JWT-based API authentication with 15-minute token expiry
* Network segmentation (token service in protected subnet)
* API gateway for external token generation endpoints

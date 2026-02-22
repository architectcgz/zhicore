# API Testing Implementation Plan

## Overview

本实施计划基于已完成的需求分析和设计文档，提供清晰的实施步骤来完成剩余的API测试工作。当前已完成198/393个测试用例（50.4%），需要完成剩余195个测试用例。

## Current Status

### Completed (198/393 - 50.4%)
- ✅ User Service: 35/35 tests
- ✅ Post Service: 41/41 tests  
- ✅ Comment Service: 36/36 tests
- ✅ Message Service: 20/20 tests
- ✅ Notification Service: 27/27 tests
- ✅ Search Service: 12/12 tests
- ✅ Ranking Service: 12/12 tests
- ✅ Upload Service: 15/15 tests

### Remaining (195/393 - 49.6%)
- ⏳ Admin Service: 0/25 tests
- ⏳ Gateway Service: 0/15 tests
- ⏳ Boundary Tests: 0/50 tests
- ⏳ Security Injection Tests: 0/60 tests
- ⏳ Auth Boundary Tests: 0/25 tests
- ⏳ Concurrency Tests: 0/20 tests

## Implementation Tasks

### Phase 1: Admin Service Testing (25 tests)

**Task 1.1: Create Admin Service Test Script**
- File: `tests/api/admin/test-admin-api-full.ps1`
- Test Cases: ADMIN-001 to ADMIN-025
- Coverage:
  - User Management (7 tests): List, search, disable, enable, details
  - Post Management (6 tests): List, search, delete, restore, batch delete
  - Comment Management (6 tests): List, search, delete, restore, batch delete
  - Report Management (6 tests): List, filter by status, handle, reject, batch handle
- Prerequisites:
  - Admin user credentials configured in test-env.json
  - ZhiCore-admin service running
  - ZhiCore-user, ZhiCore-post, ZhiCore-comment services running (for Feign calls)
- References: Requirements 9.1-9.25, Design Section 9

**Task 1.2: Execute Admin Service Tests**
- Start required services: ZhiCore-admin, ZhiCore-user, ZhiCore-post, ZhiCore-comment
- Run test script: `.\tests\api\admin\test-admin-api-full.ps1`
- Fix any discovered issues
- Update test-status.md with results

### Phase 2: Gateway Service Testing (15 tests)

**Task 2.1: Create Gateway Service Test Script**
- File: `tests/api/gateway/test-gateway-api-full.ps1`
- Test Cases: GW-001 to GW-015
- Coverage:
  - Routing Tests (5 tests): Forward, 404, 503, timeout, load balancing
  - Authentication Tests (5 tests): Valid token, invalid token, expired token, public endpoints, private endpoints
  - Rate Limiting Tests (5 tests): Normal rate, exceed limit, recovery, IP-based, user-based
- Prerequisites:
  - ZhiCore-gateway service running
  - At least one backend service running (e.g., ZhiCore-post)
  - Redis running (for rate limiting)
- References: Requirements 10.1-10.15, Design Section 10

**Task 2.2: Execute Gateway Service Tests**
- Start required services: ZhiCore-gateway, ZhiCore-post, Redis
- Run test script: `.\tests\api\gateway\test-gateway-api-full.ps1`
- Fix any discovered issues
- Update test-status.md with results

### Phase 3: Boundary Testing (50 tests)

**Task 3.1: Create Numeric Boundary Test Script**
- File: `tests/api/boundary/test-numeric-boundary.ps1`
- Test Cases: BOUND-001 to BOUND-015
- Coverage: Page numbers, page sizes, IDs, counts, offsets, limits, timestamps
- Test against: User, Post, Comment services
- References: Requirements 14.1-14.15, Design Boundary Tests Section 1

**Task 3.2: Create String Boundary Test Script**
- File: `tests/api/boundary/test-string-boundary.ps1`
- Test Cases: BOUND-016 to BOUND-030
- Coverage: Empty strings, whitespace, single char, max length, Unicode, Emoji, multilingual
- Test against: User, Post, Comment services
- References: Requirements 14.16-14.30, Design Boundary Tests Section 2

**Task 3.3: Create Collection Boundary Test Script**
- File: `tests/api/boundary/test-collection-boundary.ps1`
- Test Cases: BOUND-031 to BOUND-040
- Coverage: Empty arrays, single element, large arrays, duplicates, null elements, batch operations
- Test against: Post, Comment services (batch operations)
- References: Requirements 14.31-14.40, Design Boundary Tests Section 3

**Task 3.4: Create Time Boundary Test Script**
- File: `tests/api/boundary/test-time-boundary.ps1`
- Test Cases: BOUND-041 to BOUND-050
- Coverage: Time ranges, date formats, leap years, timezones, DST, timestamp limits
- Test against: Post, Comment, User services (date filtering)
- References: Requirements 14.41-14.50, Design Boundary Tests Section 4

**Task 3.5: Execute Boundary Tests**
- Run all boundary test scripts
- Fix any discovered issues
- Update test-status.md with results

### Phase 4: Security Injection Testing (60 tests)

**Task 4.1: Create XSS Injection Test Script**
- File: `tests/api/security/test-xss-injection.ps1`
- Test Cases: SEC-001 to SEC-015
- Coverage: Script tags, event handlers, img/svg/iframe, protocols, CSS expressions
- Test against: Post (title, content), Comment (content), User (username, bio)
- References: Requirements 15.1-15.15, Design Security Tests Section 1

**Task 4.2: Create SQL Injection Test Script**
- File: `tests/api/security/test-sql-injection.ps1`
- Test Cases: SEC-016 to SEC-030
- Coverage: Quotes, comments, UNION, OR/AND, DDL, blind injection
- Test against: All services (ID parameters, search queries)
- References: Requirements 15.16-15.30, Design Security Tests Section 2

**Task 4.3: Create NoSQL Injection Test Script**
- File: `tests/api/security/test-nosql-injection.ps1`
- Test Cases: SEC-031 to SEC-040
- Coverage: MongoDB operators, Redis commands, Elasticsearch, JSON/LDAP/XPath/XML
- Test against: Services using Redis/Elasticsearch (Search, Ranking)
- References: Requirements 15.31-15.40, Design Security Tests Section 3

**Task 4.4: Create Command Injection Test Script**
- File: `tests/api/security/test-command-injection.ps1`
- Test Cases: SEC-041 to SEC-050
- Coverage: Shell commands, path traversal, null bytes, CRLF, HTTP headers
- Test against: Upload service (filenames), all services (headers)
- References: Requirements 15.41-15.50, Design Security Tests Section 4

**Task 4.5: Create Special Characters Injection Test Script**
- File: `tests/api/security/test-special-chars-injection.ps1`
- Test Cases: SEC-051 to SEC-060
- Coverage: Backslash, slash, brackets, percent, hash, dollar, pipe, tilde
- Test against: All services (text inputs)
- References: Requirements 15.51-15.60, Design Security Tests Section 5

**Task 4.6: Execute Security Injection Tests**
- Run all security test scripts
- Document security vulnerabilities found
- Fix critical security issues
- Update test-status.md with results

### Phase 5: Auth Boundary Testing (25 tests)

**Task 5.1: Create Token Boundary Test Script**
- File: `tests/api/auth/test-token-boundary.ps1`
- Test Cases: AUTH-001 to AUTH-010
- Coverage: Empty, malformed, expired, tampered, wrong signature, missing claims, too long, special chars, logged out, blacklisted
- Test against: Gateway, User service
- References: Requirements 16.1-16.10, Design Auth Tests Section 1

**Task 5.2: Create Permission Boundary Test Script**
- File: `tests/api/auth/test-permission-boundary.ps1`
- Test Cases: AUTH-011 to AUTH-020
- Coverage: Admin access, private resources, modify others, delete others, privilege escalation, deleted/disabled users, batch operations, ID guessing, URL bypass
- Test against: All services (resource ownership)
- References: Requirements 16.11-16.20, Design Auth Tests Section 2

**Task 5.3: Create Session Boundary Test Script**
- File: `tests/api/auth/test-session-boundary.ps1`
- Test Cases: AUTH-021 to AUTH-025
- Coverage: Concurrent login, logout token, password change token, refresh token expiry/revocation
- Test against: User service (auth endpoints)
- References: Requirements 16.21-16.25, Design Auth Tests Section 3

**Task 5.4: Execute Auth Boundary Tests**
- Run all auth test scripts
- Fix any discovered auth issues
- Update test-status.md with results

### Phase 6: Concurrency & Idempotency Testing (20 tests)

**Task 6.1: Create Idempotency Test Script**
- File: `tests/api/concurrency/test-idempotency.ps1`
- Test Cases: IDEM-001 to IDEM-010
- Coverage: Duplicate like, favorite, follow, check-in, mark read, cancel operations, delete
- Test against: Post, User, Comment, Message, Notification services
- References: Requirements 17.1-17.10, Design Concurrency Tests Section 1

**Task 6.2: Create Concurrent Operations Test Script**
- File: `tests/api/concurrency/test-concurrent-operations.ps1`
- Test Cases: CONC-001 to CONC-010
- Coverage: Concurrent like, comment, follow, message, update, delete, create, token refresh, register, check-in
- Test against: All services
- Note: Use PowerShell jobs or Start-Job for parallel execution
- References: Requirements 17.11-17.20, Design Concurrency Tests Section 2

**Task 6.3: Execute Concurrency Tests**
- Run all concurrency test scripts
- Verify data consistency after concurrent operations
- Fix any race conditions or data inconsistencies
- Update test-status.md with results

### Phase 7: Integration & Reporting

**Task 7.1: Create Master Test Runner**
- File: `tests/run-all-tests.ps1`
- Functionality:
  - Check infrastructure services (Redis, PostgreSQL, Nacos)
  - Start required application services
  - Run all test scripts in sequence
  - Aggregate results
  - Generate summary report
- References: Requirements 12.1-12.3

**Task 7.2: Create Test Report Generator**
- File: `tests/generate-test-report.ps1`
- Functionality:
  - Parse test-status.md
  - Generate HTML report with charts
  - Calculate coverage percentages
  - Highlight failed tests
  - Export to CSV for analysis

**Task 7.3: Final Test Execution**
- Run complete test suite: `.\tests\run-all-tests.ps1`
- Review all test results
- Fix remaining issues
- Generate final test report
- Update documentation

## Prerequisites

### Infrastructure Services
- PostgreSQL (port 5432) - All databases created and initialized
- Redis (port 6379) - For caching and rate limiting
- Nacos (port 8848) - For service discovery
- RocketMQ (ports 9876, 10911) - For message queue
- Elasticsearch (port 9200) - For search service

### Application Services
- ZhiCore-gateway (port 8000)
- ZhiCore-user (port 8081)
- ZhiCore-post (port 8082)
- ZhiCore-comment (port 8083)
- ZhiCore-message (port 8086)
- ZhiCore-notification (port 8086)
- ZhiCore-search (port 8086)
- ZhiCore-ranking (port 8088)
- ZhiCore-upload (port 8089)
- ZhiCore-admin (port 8090)
- ZhiCore-leaf (port 8010)

### Test Environment
- PowerShell 5.1 or higher
- Network access to all services
- Test user credentials configured
- Sufficient disk space for test data

## Success Criteria

1. All 393 test cases executed
2. Pass rate >= 95% (at most 20 failures)
3. All critical security tests pass
4. All auth/permission tests pass
5. No data corruption in concurrency tests
6. Test report generated successfully
7. All discovered issues documented

## Risk Mitigation

### Risk 1: Service Dependencies
- **Mitigation**: Use health check endpoints before running tests
- **Fallback**: Skip tests for unavailable services, mark as SKIP

### Risk 2: Test Data Conflicts
- **Mitigation**: Use unique timestamps in test data
- **Fallback**: Clean up test data before each run

### Risk 3: Timing Issues
- **Mitigation**: Add appropriate delays between operations
- **Fallback**: Retry failed tests with exponential backoff

### Risk 4: Infrastructure Failures
- **Mitigation**: Verify infrastructure before starting tests
- **Fallback**: Provide clear error messages and recovery steps

## Timeline Estimate

- Phase 1 (Admin): 2-3 hours
- Phase 2 (Gateway): 2-3 hours
- Phase 3 (Boundary): 4-5 hours
- Phase 4 (Security): 5-6 hours
- Phase 5 (Auth): 3-4 hours
- Phase 6 (Concurrency): 3-4 hours
- Phase 7 (Integration): 2-3 hours

**Total Estimated Time**: 21-28 hours

## Next Steps

1. Review this implementation plan
2. Confirm prerequisites are met
3. Start with Phase 1 (Admin Service Testing)
4. Execute phases sequentially
5. Document issues and fixes
6. Generate final report

## Notes

- All test scripts follow the established pattern from completed tests
- Use the api-testing-agent.md steering file for guidance
- Follow PowerShell environment rules (no emoji, use Invoke-WebRequest)
- Update test-status.md after each phase
- Create checkpoint summaries after major phases

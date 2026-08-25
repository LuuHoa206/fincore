# Kiến trúc FinCore

## Lựa chọn ban đầu

FinCore sử dụng modular monolith. Toàn bộ backend được triển khai trong một ứng
dụng Spring Boot nhưng mã nguồn được chia theo miền nghiệp vụ. Cách này giữ việc
phát triển và transaction đơn giản, đồng thời vẫn tạo ranh giới để tách service
khi hệ thống thực sự cần.

```text
React Web
   |
REST API
   |
Spring Boot
   |-- identity
   |-- wallet
   |-- transaction
   |-- moneyjar
   |-- budget
   |-- savinggoal
   |-- reporting
   |-- audit
   `-- shared
   |
PostgreSQL
```

## Quy tắc module

- Mỗi module sở hữu entity, repository và nghiệp vụ của mình.
- Controller chỉ nhận request, validate, gọi use case và trả response.
- Service chịu trách nhiệm transaction và quy tắc nghiệp vụ.
- Không module nào truy cập trực tiếp repository nội bộ của module khác.
- Giao tiếp liên module thông qua public service hoặc domain event.
- DTO API không trả entity JPA trực tiếp.

## Luồng tạo chi tiêu

```text
Client
  -> TransactionController
  -> RecordExpenseService
  -> validate idempotency, wallet, jar, category and amount
  -> lock wallet balance
  -> create transaction and ledger postings
  -> create jar movement
  -> update budget usage
  -> append audit and outbox records
  -> commit
  -> return transaction DTO
```

Nếu một bước thất bại, toàn bộ thay đổi được rollback.

## Hướng mở rộng

- Redis: cache dashboard, rate limit và distributed idempotency.
- Message broker: notification, reporting projection và email.
- Object storage: ảnh hóa đơn.
- OpenTelemetry: log, metric và trace.
- AI service: gợi ý danh mục và tóm tắt tài chính; không tự ghi giao dịch.

Các thành phần này chỉ được thêm khi core transaction đã có test ổn định.

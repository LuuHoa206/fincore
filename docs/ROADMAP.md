# Roadmap phát triển 12 tuần

## Tuần 1 - Nền móng

- Hoàn thiện vision, phạm vi MVP và user story.
- Khởi tạo monorepo, CI, PostgreSQL, Flyway và cấu hình môi trường.
- Chốt các quy tắc dữ liệu tài chính.

Kết quả: project build được, database có migration và health check hoạt động.

## Tuần 2 - Identity và bảo mật

- Đăng ký, đăng nhập, refresh token và đăng xuất.
- Hash mật khẩu, validation, rate limit cơ bản.
- User scope và audit đăng nhập.

Kết quả: API bảo mật có integration test.

## Tuần 3 - Wallet và Category

- CRUD ví và danh mục.
- Số dư khởi tạo bằng adjustment có audit.
- Phân trang, tìm kiếm và quyền sở hữu dữ liệu.

## Tuần 4 - Transaction và ledger

- Thu nhập, chi tiêu và chuyển tiền.
- Ledger cân bằng, idempotency và reversal.
- Kiểm thử gửi request lặp và cập nhật đồng thời.

Đây là milestone kỹ thuật quan trọng nhất.

## Tuần 5 - Money Jar

- Tạo hũ, phân bổ và chuyển tiền giữa hũ.
- Kiểm tra tổng phân bổ.
- Quy tắc tự chia một khoản thu theo tỷ lệ.

## Tuần 6 - Budget và Saving Goal

- Ngân sách theo danh mục/tháng.
- Cảnh báo ngưỡng sử dụng.
- Mục tiêu tiết kiệm và dự báo số tiền cần góp.

## Tuần 7 - Dashboard và lịch sử

- Tổng hợp thu, chi, dòng tiền và tài sản ròng.
- Lịch sử giao dịch có cursor pagination và bộ lọc.
- Export CSV.

## Tuần 8 - Giao dịch định kỳ và chia hóa đơn

- Nhắc giao dịch định kỳ có chống chạy trùng.
- Chia hóa đơn, khoản phải thu và trạng thái thanh toán.

## Tuần 9 - Kiểm thử và hardening

- Unit test domain.
- Integration test PostgreSQL bằng Testcontainers.
- Security test, validation test và concurrency test.

## Tuần 10 - Observability

- Structured logging và correlation ID.
- Actuator, metric và trace.
- Dashboard giám sát và tài liệu xử lý sự cố.

## Tuần 11 - AI hỗ trợ

- Gợi ý danh mục giao dịch.
- Tóm tắt tài chính tháng.
- Phát hiện khoản chi bất thường theo luật trước, mô hình sau.

## Tuần 12 - Cloud và portfolio

- Docker image và triển khai AWS hoặc nền tảng tương đương.
- Load test, ghi p95 và tỷ lệ lỗi thật.
- README, sơ đồ kiến trúc, video demo và tài khoản thử.

## Definition of Done cho mỗi feature

- Nghiệp vụ và trường hợp lỗi được mô tả.
- API contract rõ ràng.
- Migration đi cùng thay đổi schema.
- Không có secret trong Git.
- Unit/integration test phù hợp với rủi ro.
- Build và lint thành công.
- Có log đủ để điều tra lỗi nhưng không lộ dữ liệu nhạy cảm.
- README hoặc tài liệu được cập nhật nếu hành vi thay đổi.

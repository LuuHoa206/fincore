# Mô hình dữ liệu dự kiến

## Nhóm định danh

- `app_users`: tài khoản và trạng thái người dùng.
- `user_roles`: quyền của người dùng.

## Nhóm tiền thực

- `wallets`: nơi giữ tiền thật như tiền mặt, ngân hàng, ví điện tử.
- `transactions`: thông tin nghiệp vụ của một khoản thu, chi hoặc chuyển tiền.
- `ledger_entries`: các dòng biến động tạo nên một giao dịch cân bằng.

## Nhóm phân bổ

- `money_jars`: hũ ngân sách ảo thuộc người dùng.
- `jar_movements`: lịch sử tăng giảm số tiền được phân bổ vào hũ.
- `allocation_rules`: quy tắc tự chia khoản thu theo tỷ lệ.

Hũ không tạo thêm tài sản. Tổng tiền được phân bổ vào các hũ không được vượt
quá tiền khả dụng mà người dùng cho phép phân bổ.

## Nhóm kế hoạch

- `categories`: danh mục thu và chi.
- `budgets`: giới hạn chi theo tháng và danh mục; số đã chi được tổng hợp từ
  `financial_transactions`, không lưu trùng trong bảng ngân sách.
- `saving_goals`: mục tiêu tiết kiệm, thường liên kết với một hũ.
- `recurring_rules`: lịch tạo nhắc nhở hoặc giao dịch định kỳ.

## Nhóm vận hành

- `idempotency_records`: kết quả request đã xử lý.
- `audit_logs`: lịch sử ai đã thay đổi dữ liệu gì.
- `outbox_events`: sự kiện chờ gửi sau khi transaction commit.

## Quan hệ chính

```text
User 1---n Wallet
User 1---n MoneyJar
User 1---n Transaction
Transaction 1---n LedgerEntry
Transaction 1---n JarMovement
Category 1---n Transaction
MoneyJar 1---n JarMovement
MoneyJar 1---0..1 SavingGoal
Category 1---n Budget
User 1---n SplitBill
SplitBill 1---n SplitBillParticipant
SplitBillParticipant 1---n SplitBillPayment
Transaction 1---0..1 SplitBill (original expense)
Transaction 1---0..1 SplitBillPayment (reimbursement)
```

## Nhóm chia hóa đơn

- `split_bills`: khoản chi đã ghi vào sổ cái, phần của người trả và trạng thái khoản cần thu.
- `split_bill_participants`: số tiền từng người cần hoàn, đã hoàn và còn lại.
- `split_bill_payments`: liên kết mỗi lần hoàn tiền với giao dịch thu thật trong `financial_transactions`.

`split_bills.expense_transaction_id` là duy nhất. Vì vậy một hóa đơn chia tiền
luôn đối chiếu được với đúng một khoản chi. Mỗi
`split_bill_payments.transaction_id` cũng duy nhất để một giao dịch thu không
thể được gắn hai lần vào công nợ.

## Kiểu dữ liệu quan trọng

- ID: UUID, tạo ở application.
- Tiền: `NUMERIC(19,4)`, không dùng floating point.
- Tiền tệ: mã ISO 4217 dài 3 ký tự.
- Thời gian nghiệp vụ: `TIMESTAMPTZ`, lưu theo UTC.
- Ngày ngân sách: `DATE` hoặc cặp `year/month` tùy use case.
- Entity thay đổi đồng thời có cột `version` cho optimistic locking.

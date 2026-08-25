# Git workflow

## Nhánh

- `main`: phiên bản ổn định và có thể deploy.
- `dev`: nhánh tích hợp trước khi phát hành.
- `feature/<short-name>`: phát triển chức năng.
- `fix/<short-name>`: sửa lỗi.
- `chore/<short-name>`: công cụ, cấu hình hoặc tài liệu.

## Quy trình

1. Cập nhật `dev` trước khi tạo nhánh mới.
2. Một nhánh giải quyết một mục tiêu rõ ràng.
3. Commit nhỏ, chạy được và có thông điệp mô tả hành vi.
4. Tạo Pull Request vào `dev`, không push tính năng trực tiếp vào `main`.
5. Build, test, migration và API contract phải được kiểm tra trước merge.
6. Chỉ merge `dev` vào `main` khi milestone đã được demo và xác nhận.

## Commit convention

```text
feat(wallet): create wallet and initial balance
fix(transaction): prevent duplicate expense submission
test(ledger): cover concurrent posting scenario
docs(architecture): record jar allocation decision
chore(ci): add backend and frontend quality gates
```

## Pull Request phải trả lời

- Vấn đề nào được giải quyết?
- Luồng nghiệp vụ thay đổi ra sao?
- Có migration hoặc biến môi trường mới không?
- Đã kiểm thử những trường hợp nào?
- Có ảnh hưởng số dư, lịch sử hoặc báo cáo không?

# Quy tắc dữ liệu tài chính

Đây là các điều kiện không được phá vỡ khi phát triển tính năng.

1. Số tiền luôn dùng `BigDecimal` và có loại tiền đi kèm.
2. Một transaction chỉ được `POSTED` khi tất cả ledger entry hợp lệ.
3. Tổng biến động của các ledger entry trong một transaction phải bằng 0.
4. Giao dịch đã `POSTED` không sửa hoặc xóa trực tiếp.
5. Sửa sai bằng transaction `REVERSAL` tham chiếu transaction gốc.
6. Chuyển giữa hai ví của cùng người dùng không tính vào thu hoặc chi.
7. Chuyển giữa hai hũ chỉ thay đổi phân bổ, không thay đổi tổng tài sản.
8. Hoàn tiền phải tham chiếu giao dịch chi tiêu ban đầu.
9. Một idempotency key chỉ đại diện cho một yêu cầu và một người dùng.
10. Cập nhật số dư, hũ, ngân sách, audit và outbox phải cùng transaction.
11. Mọi dữ liệu truy vấn phải được giới hạn theo chủ sở hữu.
12. AI chỉ tạo đề xuất; người dùng xác nhận trước khi dữ liệu thay đổi.

## Ví dụ

Người dùng chuyển 1 triệu đồng từ ví Ngân hàng sang ví Tiền mặt:

```text
Ngân hàng    -1.000.000
Tiền mặt     +1.000.000
Tổng                  0
```

Đây là chuyển nội bộ, vì vậy báo cáo thu nhập và chi tiêu không thay đổi.

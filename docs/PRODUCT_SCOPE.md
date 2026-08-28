# Phạm vi sản phẩm FinCore

## Mục tiêu

FinCore giúp người dùng biết tiền đang ở đâu, đã chi vào việc gì, còn bao nhiêu
trong từng hũ và có đang tiến gần mục tiêu tiết kiệm hay không.

## Người dùng mục tiêu

- Sinh viên và người mới đi làm cần quản lý chi tiêu cá nhân.
- Người có nhiều nguồn tiền: tiền mặt, ngân hàng và ví điện tử.
- Người muốn chia thu nhập theo tỷ lệ và theo dõi mục tiêu tiết kiệm.

## MVP

1. Đăng ký, đăng nhập và quản lý hồ sơ.
2. Tạo ví tiền mặt, tài khoản ngân hàng và ví điện tử.
3. Ghi nhận thu nhập, chi tiêu và chuyển tiền nội bộ.
4. Quản lý danh mục thu chi.
5. Tạo hũ tiền và phân bổ tiền vào hũ.
6. Tự chia một khoản thu theo tỷ lệ cấu hình.
7. Đặt ngân sách theo danh mục và theo tháng.
8. Theo dõi mục tiêu tiết kiệm.
9. Lịch sử giao dịch có tìm kiếm, lọc và phân trang.
10. Dashboard tháng: thu, chi, dòng tiền và tiến độ hũ.
11. Lịch tài chính tháng: phân biệt giao dịch đã ghi nhận với lịch thu chi dự kiến.

## Chưa làm trong MVP

- Kết nối trực tiếp với ngân hàng hoặc xử lý tiền thật.
- Đầu tư, chứng khoán và tiền mã hóa.
- Cho vay hoặc chấm điểm tín dụng.
- Tự động sửa giao dịch bằng AI.
- Microservices, Kafka hoặc hạ tầng phân tán khi chưa có nhu cầu thực tế.

## Luồng demo chính

1. Người dùng tạo ví Ngân hàng với số dư ban đầu.
2. Ghi nhận lương 15 triệu đồng.
3. Quy tắc tự chia lương vào năm hũ.
4. Ghi chi tiêu ăn uống từ hũ Thiết yếu.
5. Kiểm tra số dư ví, số dư hũ và ngân sách cùng thay đổi chính xác.
6. Gửi lại cùng một request và chứng minh giao dịch không bị tạo hai lần.
7. Đảo ngược giao dịch sai và xem audit log.
8. Xem báo cáo cuối tháng và tiến độ tiết kiệm.

## Chỉ số thành công

- Không sai số dư sau các luồng thu, chi, chuyển và đảo ngược.
- Không tạo giao dịch trùng khi request được gửi lại.
- API danh sách luôn có phân trang và bộ lọc rõ ràng.
- Các nghiệp vụ quan trọng có integration test.
- Có số liệu load test thật trước khi đưa hiệu năng vào CV.

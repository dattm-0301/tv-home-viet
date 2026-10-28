# Quyền riêng tư

TV Home Việt không có tài khoản, analytics, SDK quảng cáo hay server điều khiển từ xa của dự án.
Danh sách app, thứ tự / nhóm, số lần mở app và cache widget lưu trên thiết bị. Theme do chủ thiết bị cấu hình. Không gửi danh sách app / lịch sử mở app tới các dịch vụ giá hoặc thời tiết.

Khi Home / đồng hồ hiển thị, app gọi HTTPS tới:

| Dịch vụ | Dữ liệu gửi | Mục đích |
|---|---|---|
| api.open-meteo.com | Tọa độ đã nhập, IP và metadata HTTP thông thường | Thời tiết / mưa |
| air-quality-api.open-meteo.com | Tọa độ đã nhập, IP và metadata HTTP | AQI dự báo CAMS |
| taichinh.com | IP / metadata HTTP; không gửi tọa độ | Giá vàng SJC theo đại lý |
| giavang.now | IP / metadata HTTP; không gửi tọa độ | Giá SJC dự phòng, ghi Tham khảo |
| portals.petrolimex.com.vn | IP / metadata HTTP; không gửi tọa độ | Xăng E10 RON 95-III, DO 0,05S-II, vùng 1 |

Chính sách của từng dịch vụ áp dụng cho dữ liệu họ nhận. API miễn phí có giới hạn / điều khoản riêng; không có cam kết luôn truy cập được. Giá vàng là feed tổng hợp, không phải kết nối trực tiếp sở giao dịch; thời gian quan sát khác thời gian niêm yết. Giá xăng thay đổi theo kỳ. Thời tiết / AQI là mô hình khu vực.

Quyền Android: Internet, trạng thái mạng / Wi-Fi, yêu cầu gỡ app qua hộp xác nhận hệ thống, và một quyền nguồn vào Yandex chỉ có tác dụng trên firmware tương ứng. Bản public không xin vị trí chính xác, micro, camera, danh bạ hay quyền lưu trữ toàn bộ thiết bị. Home và screensaver phải xuất các entry point cho hệ thống Android. Provider theme yêu cầu quyền hệ thống; trợ năng là tùy chọn lọc phím.

APK / cấu hình backup, ảnh màn hình, tọa độ riêng và khóa ADB không được đưa vào repo. Tắt ADB qua mạng sau cài đặt. `allowBackup=false`; việc xóa dữ liệu app sẽ xóa thứ tự / nhóm và cache cục bộ.

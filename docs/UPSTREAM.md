# Nguồn gốc và cập nhật từ CleanYourTV

TV Home Việt là một bản fork độc lập từ [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv), phát hành dưới các giấy phép ghi trong LICENSE và THIRD_PARTY.md.

- Nhánh `main`: mã CleanYourTV gốc, giữ lịch sử upstream.
- Nhánh `feat/tv-home-viet`: bản TV Home Việt; dùng nhánh này để tải, build và báo lỗi.
- Nhánh tùy chỉnh được tạo từ upstream `095b71ad`. Phần launcher được chuyển từ bản tùy chỉnh đã kiểm tra riêng; đây không phải tuyên bố đã tích hợp tất cả tính năng/sửa lỗi launcher mới nhất của upstream.
- Nhánh tùy chỉnh tập trung vào launcher. Không kèm bộ danh sách debloat, script tự vô hiệu hóa launcher gốc hoặc toàn bộ công cụ của upstream.
- Bản public dùng `com.tvhomeviet.launcher`; bản riêng đã chạy trên Xiaomi dùng danh tính cài đặt khác. Xem ma trận hỗ trợ trong README.

## Lấy thay đổi mới

```sh
git remote add upstream https://github.com/Gathin23/cleanyourtv.git # chỉ chạy nếu chưa có
git fetch upstream main
git log --oneline main..upstream/main
```

Đọc từng thay đổi liên quan, chuyển phần phù hợp sang một nhánh thử nghiệm của TV Home Việt rồi chạy test/lint/build và kiểm tra trên thiết bị trước khi phát hành. Không ghi đè toàn bộ launcher bằng upstream hoặc chạy script debloat như một bước cập nhật. Không tự gửi PR bản tùy chỉnh này vào repo Gathin.

Các snapshot repo public độc lập từng chuẩn bị trước khi fork không được nhập thành lịch sử của fork; chỉ cây mã đã làm sạch được chuyển sang. Lịch sử riêng của TV và khóa ký không được đẩy lên GitHub.

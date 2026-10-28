# Cập nhật và giữ cấu hình

## Người dùng APK phát hành

Dùng cùng app ID `com.tvhomeviet.launcher` và cùng chữ ký phát hành. APK trên Releases không cài đè lên bản debug tự build hoặc bản riêng của chủ dự án (`com.ts.launcher`). Không gỡ app/xóa dữ liệu để ép cập nhật.

Trên Mac/Linux (Python 3.9+, Bash, ADB, Android SDK build-tools có `aapt`; xem SETUP):

```sh
python3 scripts/update.py --check
python3 scripts/update.py TV_IP:5555
# Hoặc chỉ định một bản phát hành cụ thể:
python3 scripts/update.py TV_IP:5555 --tag tv-home-viet-v1.1.0
```

Công cụ chỉ chọn các bản phát hành có tag/asset của repo TV Home Việt, bao gồm **preview** đã public; bỏ qua draft. Nó tải APK và checksum qua HTTPS, kiểm tra SHA-256, kiểm tra package qua bộ cài, sao lưu APK/theme/cá nhân, rồi cài đè bằng `install --no-incremental -r` (tải trọn APK, tránh phụ thuộc bản APK trên máy tính khi chạy). Checksum phát hiện tải lỗi/sai file; chữ ký Android mới quyết định APK có được cập nhật đè hay không. Từ 1.1, nhập theme/cá nhân yêu cầu launcher tự nạp lại giao diện, không force-stop nên không chủ động ngắt dịch vụ phím trợ năng. Không tắt kiểm tra chữ ký, không tự xóa dữ liệu, không tự thay launcher hệ thống. Bản thấp versionCode có thể bị Android từ chối.

Windows PowerShell: tải APK + SHA256SUMS từ trang Releases, kiểm tra bằng `Get-FileHash`, sao lưu như SETUP rồi `adb -s TV_IP:5555 install -r FILE.apk`. Script Python/Bash khuyến nghị chạy trong WSL; kiểm tra ADB trong WSL truy cập được TV trước.

Cài đè giữ cấu hình cá nhân, font, cột, nhóm, ghim và thứ tự app trong dữ liệu Android. Bộ sao lưu của script chỉ đảm bảo APK và các file JSON, **không khôi phục toàn bộ SharedPreferences, tài khoản hoặc firmware**. Tránh gỡ app, Clear data, factory reset hoặc xóa thư mục dữ liệu.

Nếu thất bại: giữ Home gốc làm đường quay lại, giữ nguyên dữ liệu và đọc lỗi Android. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` thường là khác chữ ký; bản debug/release hoặc APK của người khác không thể cập nhật thay nhau. Không chia sẻ keystore để giải quyết lỗi này.

## Người phát triển

- Chỉ sửa nhánh TV Home Việt, không đưa thay đổi cá nhân vào upstream/main.
- Không cần sửa mã/build cho từng gia đình: dùng UI hoặc `personal.json`; profile TV nằm trong theme JSON.
- Giữ ID ổn định; tăng `versionCode`, đổi `versionName`, cập nhật CHANGELOG. Bản release build unsigned; ký ngoài repo bằng đúng khóa phát hành cũ.
- Giữ dữ liệu Android tương thích khi thêm tùy chọn; mặc định mới chỉ áp dụng nếu người dùng chưa đặt giá trị. Không đóng gói personal.json, khóa, backup hoặc cache vào APK/source.
- Chạy `python3 -m unittest discover -s scripts/tests -v`, `bash -n scripts/tv.sh`, rồi trong launcher: `./gradlew testReleaseUnitTest lintRelease assembleRelease`.
- Test cập nhật đè trên emulator có cấu hình cá nhân trước; kiểm tra giữ nhóm/font/cột, đổi vị trí không hiện cache sai nơi, theme không reset tùy chọn.
- Đọc `docs/UPSTREAM.md` trước khi lấy thay đổi Gathin. Chuyển từng thay đổi phù hợp, không ghi đè launcher riêng hàng loạt.

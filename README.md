# TV Home Việt

Launcher cho Android TV / Google TV, phát triển từ [CleanYourTV của Gathin](https://github.com/Gathin23/cleanyourtv). Giao diện chính tiếng Việt, điều khiển bằng remote, nền màu nhẹ và các thông tin cần xem nhanh. Một số menu kế thừa còn dùng tiếng Anh trong bản preview.

![Màn hình chính](docs/screenshots/01-home.png)

- Giờ, ngày dương, âm lịch và ngày lễ Việt Nam.
- Thời tiết, dự báo theo giờ, mưa, AQI; tự nhập thành phố / tọa độ.
- Giá vàng SJC và xăng dầu, có mốc dữ liệu và nhãn cũ / tham khảo.
- Ghim, đổi tên, ẩn, chia nhóm, sắp xếp ứng dụng bằng remote.
- Màn hình chờ đồng hồ; phím tắt Nguồn vào, mạng và cài đặt.

**Nhánh sử dụng: `feat/tv-home-viet`.** Nhánh `main` giữ mã gốc CleanYourTV. [Nguồn gốc và cách cập nhật](docs/UPSTREAM.md).

[**Tải APK 1.0.1**](https://github.com/dattm-0301/tv-home-viet/releases/download/tv-home-viet-v1.0.1/TV-Home-Viet-1.0.1.apk) · [Trang phát hành và mã kiểm tra](https://github.com/dattm-0301/tv-home-viet/releases/tag/tv-home-viet-v1.0.1)

## Cài nhanh

Cần TV **Android 8.0 trở lên**, cùng mạng với máy tính và bật ADB. Tải APK từ liên kết trên; hoặc tự build:

```sh
# Cài JDK 17 và Android SDK trước; xem docs/SETUP.md.
git clone --branch feat/tv-home-viet --single-branch https://github.com/dattm-0301/tv-home-viet.git
cd tv-home-viet/launcher
./gradlew assembleDebug
cd ..
./scripts/tv.sh TV_IP:5555 install launcher/app/build/outputs/apk/debug/app-debug.apk
```

Chọn **Cho phép** khi TV hỏi kết nối. Script sao lưu APK/cấu hình cũ nếu có, cài và mở thử. Sau khi kiểm tra remote và HDMI, chọn **TV Home Việt** trong cài đặt ứng dụng Home mặc định. Giữ launcher gốc để quay lại khi cần.

**[Hướng dẫn đầy đủ: Mac / Windows / Linux, cấu hình, HDMI, màn hình chờ và khôi phục →](docs/SETUP.md)**

## Remote

| Thao tác | Cách dùng |
|---|---|
| Mở ứng dụng | Di chuyển bằng D-pad, nhấn OK |
| Ghim, đổi tên, ẩn, gỡ app | Giữ OK trên ô app; gỡ cần xác nhận của Android |
| Sắp xếp | Chọn Sắp xếp → OK nhấc app → phím mũi tên → OK thả → Back thoát |
| Tùy chọn launcher | Giữ OK trên nút Cài đặt |
| Thu gọn widget | Cuộn xuống các hàng ứng dụng, tùy chọn trong menu |
| Thoát đồng hồ | Nhấn một phím trên remote |

## TV hỗ trợ

| Thiết bị | Mức xác nhận |
|---|---|
| Xiaomi L55MA-EP / MiTV MTEQ0, Android 10 | Bản tùy chỉnh tiền thân đã dùng trên TV thật; profile HDMI riêng có sẵn |
| Google TV Android 14, 1080p emulator | Xem kết quả và giới hạn ở [bản kiểm tra](docs/SECURITY-REVIEW.md) |
| Android TV / Google TV khác, Android 8+ | Đáp ứng minSdk; cần tự kiểm tra remote, Home và HDMI trên từng firmware |
| FPT / box nhà mạng khóa ADB | Chưa xác nhận cài được; cần quyền cài app từ nhà cung cấp |
| Samsung Tizen, LG webOS | Không chạy APK Android này |

Danh sách hàng trăm TV của CleanYourTV là báo cáo về **dự án gốc**, không phải danh sách đã kiểm thử của bản tùy chỉnh này. Không gộp quy trình debloat vào bộ cài launcher.

## Dữ liệu & quyền riêng tư

Vàng kiểm tra khoảng 5 phút, xăng dầu 15 phút, thời tiết 30 phút, AQI 60 phút khi Home / đồng hồ đang hiển thị. Giá thực tế thay đổi khi nguồn niêm yết lại; đây không phải luồng giá từng giây. Mạng lỗi thì dùng cache có trạng thái cũ. [Nguồn và quyền riêng tư](PRIVACY.md).

**Giấy phép:** mã launcher MIT, giữ ghi công CleanYourTV. Bảng âm lịch đi kèm có điều khoản **cá nhân, phi thương mại** riêng; toàn bộ gói không được mô tả là thuần MIT. Xem [THIRD_PARTY.md](THIRD_PARTY.md).

[Ảnh demo & cách tái tạo](docs/DEMO.md) · [Bảo mật](SECURITY.md) · [Thay đổi](CHANGELOG.md)

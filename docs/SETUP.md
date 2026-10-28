# Setup

## 1. Chuẩn bị

- Android TV / Google TV Android 8.0+ (API 26), có remote D-pad; không cần root.
- Tải source ZIP rồi giải nén, hoặc clone repo; mở terminal trong thư mục gốc.
- Script tự động cần Bash và Python 3; cài thủ công bên dưới chỉ cần ADB.
- Cài Android SDK Platform Tools (`adb`). Nếu build: JDK **17**, Android SDK platform 34 / build tools 34+ và Internet để Gradle tải dependency.
- Trên TV bật Developer options (nhấn Build number 7 lần), bật ADB / USB debugging. Một số box yêu cầu quyền của nhà mạng; không có mật khẩu chung được xác nhận.
- Cùng mạng LAN. IP nằm ở trang Mạng của TV. Dùng mạng nhà tin cậy; tắt ADB qua mạng khi hoàn thành.

Mac / Linux:

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"  # Linux thường là $HOME/Android/Sdk
export PATH="$ANDROID_HOME/platform-tools:$PATH"
# JAVA_HOME phải trỏ tới JDK 17. Trên macOS nếu đã cài JDK 17:
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
adb connect TV_IP:5555
adb devices -l
```

Nếu TV dùng Wireless debugging có pairing (Android 11+): dùng `adb pair IP:PAIR_PORT`, nhập mã hiện trên TV, rồi `adb connect IP:CONNECT_PORT`. Hai cổng thường khác nhau; dùng giá trị TV hiển thị.

Windows: giải nén Platform Tools, mở PowerShell ở thư mục đó, dùng `./adb.exe` thay `adb`; cùng các lệnh bên dưới. Script `.sh` chạy trong Git Bash hoặc WSL; nếu dùng PowerShell thuần, theo cách cài thủ công.

## 2. Build hoặc tải APK

Bản release có ID `com.tvhomeviet.launcher`. Bản debug tự build dùng cùng ID nhưng chữ ký khác, không thể cập nhật đè lên release. Dùng cùng keystore khi tự phát hành các bản cập nhật. Không gửi keystore / mật khẩu lên GitHub.

```sh
cd launcher
./gradlew assembleDebug       # Windows: ./gradlew.bat assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
# Kiểm tra mã: ./gradlew testReleaseUnitTest lintRelease
```

`assembleRelease` tạo APK **chưa ký**. Muốn phát hành, ký bằng keystore riêng qua Android Studio hoặc `apksigner`; xem [hướng dẫn ký chính thức](https://developer.android.com/studio/publish/app-signing). Repo không chứa private key và không tự dùng debug key cho release.

Kiểm tra SHA-256 của APK với file checksum đi kèm release: macOS/Linux dùng `shasum -a 256 FILE.apk`, Windows dùng `Get-FileHash FILE.apk -Algorithm SHA256`. Chỉ dùng artifact của bản phát hành đã chọn.

## 3. Sao lưu và cài thử

```sh
./scripts/tv.sh TV_IP:5555 inspect
./scripts/tv.sh TV_IP:5555 install PATH_TO_APK
```

Script kiểm tra application ID, lưu APK cũ (kể cả split nếu có), cấu hình và Home resolver vào `.local/backups/`, rồi `adb install -r` và mở launcher. Đây là bản sao APK / cấu hình, **không phải backup firmware, tài khoản hay toàn bộ dữ liệu app**. Thư mục backup là dữ liệu riêng, đã được gitignore.

Cài thủ công với PowerShell hoặc khi không chạy script:

```sh
adb -s TV_IP:5555 shell pm path com.tvhomeviet.launcher
# Nếu đã cài: adb pull từng đường dẫn package:... phía trên về thư mục riêng.
adb -s TV_IP:5555 install -r PATH_TO_APK
adb -s TV_IP:5555 shell am start -n com.tvhomeviet.launcher/com.gathin.tvhome.MainActivity
```

Kiểm tra mở app, giữ OK, đi lại bằng remote, mở cài đặt và thử nguồn HDMI đang nối. Emulator không xác minh được HDMI vật lý / middleware hãng / nút Home thật.

## 4. Đặt Home và quay lại

Vào Cài đặt TV → Ứng dụng mặc định → Ứng dụng Home (tên mục tùy hãng), chọn TV Home Việt. Hoặc mở hộp chọn Home của Android và chọn Luôn luôn. Nếu firmware chặn đổi Home, đọc hướng dẫn **đúng model** trong CleanYourTV; không tự disable một gói hệ thống bất kỳ.

Bộ cài này không disable / uninstall launcher gốc. Muốn quay lại, chọn launcher gốc trong cùng menu. Nếu chỉ cần gỡ TV Home Việt, chọn Home gốc trước rồi gỡ như app bình thường.

Muốn rollback APK: cài lại bản sao bằng `adb install -r OLD.apk`; nếu có split dùng `adb install-multiple -r BASE.apk SPLIT.apk ...`. Android có thể từ chối hạ versionCode hoặc khác chữ ký: lúc đó dùng Home gốc và build bản sửa, không xóa dữ liệu để ép cập nhật. Sao lưu cấu hình không khôi phục mọi tùy chọn lưu trong bộ nhớ riêng của app.

## 5. Cấu hình thời tiết, nền và giá

Sửa bản sao `themes/default/config.json`, rồi:

```sh
./scripts/tv.sh TV_IP:5555 theme themes/default/config.json
```

Tọa độ mặc định là khu trung tâm Hà Nội, không phải nhà người dùng. Sửa `weather_place`, `weather_latitude`, `weather_longitude`. Dữ liệu gửi tới Open-Meteo gồm tọa độ và IP kết nối; không cần quyền GPS. Sau đổi vị trí, cache thời tiết được làm mới. Khung giờ / âm lịch của giao diện này dùng Việt Nam UTC+7.

- `background_color`: màu nền; `sky_intensity`: nền màu theo thời gian (0–100).
- `gold_brand`: `doji`, `pnj`, `phuquy`, `btmh` (giá vàng miếng SJC tại đại lý tương ứng).
- `gold_refresh_minutes`: 5–240; `fuel_refresh_minutes`: 15–720. Kiểm tra thường hơn không làm nguồn niêm yết giá mới nhanh hơn.
- `show_aqi`: AQI mô hình; không phải cảm biến tại nhà.
- `home_idle_clock_minutes`: 0 tắt, hoặc số phút không tương tác trên Home trước khi hiện đồng hồ.

Theme / config không đáng tin có thể thay đổi app hoặc phím tắt launcher mở. Chỉ nhập config đã đọc; provider ghi theme chỉ cho caller có quyền hệ thống `WRITE_SECURE_SETTINGS` (ADB shell).

## 6. Xiaomi L55MA-EP và nguồn HDMI

Dùng `themes/xiaomi-l55ma-ep/config.json` nếu đúng MiTV MTEQ0 / baymax Android 10. Profile này có action nguồn vào và map HW5 / HW6 / HW2 được xác nhận ở bản tùy chỉnh tiền thân. Không áp dụng map này cho model khác chỉ vì cùng hãng.

Độ sáng chỉ gọi panel / middleware của Xiaomi khi có; các TV khác có thể chỉ mở cài đặt màn hình hoặc không hỗ trợ. Đây không phải bộ điều khiển độ sáng phổ quát.

## 7. Màn hình chờ và phím Home đặc biệt

Mặc định đồng hồ tự mở sau 5 phút **chỉ khi đang ở Home**. Chọn màn hình chờ hệ thống tại Cài đặt TV → Screensaver → TV Home Việt nếu firmware có mục này. Không đổi `screen_off_timeout` để ép chạy: trên một số MiTV nó làm TV ngủ.

Nếu firmware gửi phím Home khác chuẩn, có thể bật dịch vụ trợ năng của TV Home Việt trong Cài đặt Trợ năng. Dịch vụ chỉ lọc phím, không xin đọc nội dung màn hình; chỉ bật khi cần. `key_filter=false` trong theme nếu firmware làm hỏng giữ phím / lặp âm lượng. Kiểm tra từng remote thật trước khi dùng hằng ngày.

Nếu mất ADB sau reboot: bật lại ADB trên TV. Nếu không kết nối, kiểm tra IP / cùng LAN / hộp Cho phép. Không mở cổng ADB ra Internet.

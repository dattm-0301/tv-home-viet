# Tùy chỉnh cho từng người dùng

## Chỉnh trực tiếp bằng remote

Giữ OK trên nút **Cài đặt** → **Tùy chọn launcher** → **Vị trí & widget · Nền · Đồng hồ**.

1. Chọn thành phố có sẵn (tọa độ trung tâm), hoặc nhập tên + **cả vĩ độ và kinh độ** từ ghim bản đồ. Đổi tên riêng không thay đổi tọa độ. Hỗ trợ tọa độ toàn cầu; không dùng GPS.
2. Chọn múi giờ IANA, ví dụ `Asia/Ho_Chi_Minh`, `Asia/Tokyo`, hoặc `system` để theo TV. Đồng hồ/ngày dương/dự báo theo lựa chọn; **âm lịch/ngày lễ là lịch Việt Nam UTC+7**. Ngoài Việt Nam, hai ngày có thể lệch nhau gần nửa đêm.
3. Bật/tắt thời tiết, AQI, vàng, xăng dầu, lịch âm, ngày lễ. Trong màn hình chờ, AQI nằm cùng bảng thời tiết; muốn hiện AQI ở đó cần bật thời tiết.
4. Chọn đại lý vàng `doji`, `pnj`, `phuquy`, `btmh`; mặc định DOJI. Giá đều là vàng miếng SJC, fallback luôn ghi Tham khảo. Xăng dầu là Petrolimex **vùng 1, E10 RON 95-III và DO 0,05S-II**, không tự đổi theo vị trí thời tiết.
5. Chọn bố cục Home: đồng hồ trái, đồng hồ phải, hoặc thời tiết ở bên phải bảng thông tin. Chọn nền màu, cường độ nền theo giờ (0 tắt), thời gian tự mở đồng hồ (0 tắt).
6. Chọn **Lưu và quay về Home**. Back/Hủy không lưu. Nhập sai tọa độ, múi giờ, màu hay khoảng thời gian sẽ hiện lỗi trước khi ghi.

Font, số cột (4–8), hiệu ứng, hình nền và thu gọn widget vẫn ở màn hình Tùy chọn launcher. Nhóm/ghim/đổi tên/thứ tự ứng dụng chỉnh bằng giữ OK trên app. Có ba mẫu bố cục Home; màn hình chờ giữ bố cục riêng. Chưa hỗ trợ kéo thả tự do tọa độ/kích thước từng widget; khung bố cục vẫn được tối ưu cho remote/TV.

## Ba lớp cấu hình

| Lớp | Lưu ở đâu | Khi cập nhật |
|---|---|---|
| Giá trị mặc định | APK | Có thể cải tiến trong bản mới |
| Theme / profile thiết bị | `config.json` | `theme` gộp những khóa mới; `theme-replace` thay toàn bộ file |
| Cá nhân trên TV | `personal.json`, cùng các lựa chọn font/cột/nhóm lưu riêng trong app | Ưu tiên hơn theme; không bị reset khi đổi APK hoặc theme |

`personal.json` chỉ chứa các tùy chọn ở màn hình Vị trí & widget. Nó có bản lưu nội bộ để khởi động trước khi bộ nhớ ngoài sẵn sàng. Nếu file sửa tay không hợp lệ, Home dùng bản hợp lệ gần nhất, tránh crash; muốn bỏ tùy chọn cá nhân hãy dùng nút đặt lại trong UI hoặc nhập `{}`. Đừng chỉ xóa file vì bản lưu vẫn còn.

Nút **Theo theme** trên màn hình launcher chỉ đặt lại cột/hàng/hiệu ứng/hình nền. Nút đặt lại ở **Vị trí & widget** chỉ bỏ cấu hình cá nhân của màn hình đó. Hai nút không xóa app hoặc nhóm/thứ tự app.

## Chỉnh từ máy tính

Tạo file riêng ngoài Git, ví dụ `.local/personal.json` (Python 3.9+; Bash trên Mac/Linux hoặc WSL):

```json
{
  "weather_place": "Đà Nẵng",
  "weather_latitude": 16.0544,
  "weather_longitude": 108.2022,
  "display_timezone": "Asia/Ho_Chi_Minh",
  "show_weather": true,
  "show_aqi": true,
  "show_gold": false,
  "show_fuel": false,
  "background_color": "#10251F",
  "sky_intensity": 25,
  "home_idle_clock_minutes": 10
}
```

```sh
./scripts/tv.sh TV_IP:5555 personal .local/personal.json
# Gộp theme; giữ các khóa cũ không có trong file mới và giữ tùy chọn cá nhân:
./scripts/tv.sh TV_IP:5555 theme themes/aurora/config.json
# Chỉ dùng khi muốn thay toàn bộ theme; vẫn giữ tùy chọn cá nhân:
./scripts/tv.sh TV_IP:5555 theme-replace themes/default/config.json
```

Import cá nhân thay toàn bộ `personal.json`; khóa bỏ trống sẽ theo theme/default. Script kiểm tra trước khi ghi và sao lưu APK, cấu hình theme, cá nhân. Những bản sao này chứa vị trí riêng: không commit/chia sẻ.

Các khóa cá nhân được hỗ trợ: tên/tọa độ, `display_timezone`, `show_weather`, `show_aqi`, `show_gold`, `show_fuel`, `show_lunar`, `show_holidays`, `background_color`, `sky_intensity`, `home_layout` (`clock_left`, `clock_right`, `weather_right`), `gold_brand`, `gold_refresh_minutes` (5–240), `fuel_refresh_minutes` (15–720), `home_idle_clock_minutes` (0–1440). Nền màu dùng `#RRGGBB`. Tên vị trí tối đa 60 ký tự.

## Nguồn vào theo model

`input_action` và `input_sources` là cấu hình theme/profile kỹ thuật, không phải tùy chọn cá nhân. Bản 1.1 đọc mapping từ file JSON thực sự; không cần sửa rồi build lại APK. Chỉ dùng profile Xiaomi nếu đúng model/firmware đã ghi trong SETUP; TV khác bắt đầu bằng theme mặc định. Không thử số cổng hoặc mở menu nhà máy một cách ngẫu nhiên.

Đổi theme không tự vô hiệu hóa launcher hay dịch vụ hệ thống. Nếu TV không có HDMI-in (box), không tạo cổng HDMI giả. Trạng thái standby không chứng minh có dây hoặc có hình.

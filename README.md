# LinkVault

**Lưu các liên kết hữu ích, sắp xếp bằng ghi chú và tìm lại nhanh khi cần.**

LinkVault là ứng dụng Android ưu tiên lưu trữ cục bộ, dùng để gom các liên kết từ trình duyệt, mạng xã hội, ứng dụng chat và mọi nơi hỗ trợ chia sẻ của Android. Ứng dụng giúp bạn lưu URL, ghi chú, danh mục và cách tổ chức trực quan trong một nơi tập trung, tránh thất lạc nội dung quan trọng trong tin nhắn, tab trình duyệt hoặc các ghi chú tạm thời.

## Điểm nổi bật

- **Lưu liên kết nhanh** — thêm thủ công hoặc chia sẻ văn bản/URL vào LinkVault từ ứng dụng Android khác.
- **Tổ chức theo ghi chú** — thêm ghi chú ngắn, chọn danh mục và chỉnh sửa mục đã lưu nhanh chóng.
- **Danh mục trực quan** — tạo danh mục với icon có sẵn hoặc ảnh tùy chỉnh từ thư viện.
- **Tìm kiếm ở nhiều nơi** — tìm liên kết trong danh sách chính hoặc trong từng danh mục.
- **Chia sẻ từng ghi chú** — gửi lại bất kỳ ghi chú/liên kết đã lưu sang ứng dụng khác.
- **Nhập và xuất CSV** — sao lưu hoặc di chuyển dữ liệu bằng file CSV theo định dạng `Link,Note,Category`.
- **Giao diện tiếng Việt và tiếng Anh** — chuyển ngôn ngữ trực tiếp trong phần Cài đặt.
- **Cá nhân hóa giao diện** — chọn chế độ Sáng, Tối hoặc Theo máy, kèm 6 bảng màu: Denim Cool, Forest Jade, Blossom Rose, Peach Amber, Lavender Mist và Teal Breeze.
- **Kiểm tra cập nhật** — kiểm tra GitHub Release mới nhất trong Cài đặt và mở trang tải APK khi có phiên bản mới.

## Màn hình ứng dụng

| Liên kết | Danh mục | Cài đặt |
| --- | --- | --- |
| <img src="docs/screenshots/Links.jpg" alt="Màn hình Liên kết" width="260" /> | <img src="docs/screenshots/Categories.jpg" alt="Màn hình Danh mục" width="260" /> | <img src="docs/screenshots/Settings.jpg" alt="Màn hình Cài đặt" width="260" /> |

## Luồng chính

### Liên kết

Lưu URL kèm ghi chú và danh mục, sau đó mở, sửa, xóa hoặc chia sẻ ngay từ danh sách. Popup thêm/sửa được tối ưu cho bàn phím di động và vẫn giữ phần chọn danh mục dễ thao tác khi đang nhập.

### Danh mục

Tạo danh mục trực quan, chọn icon mặc định đã bản địa hóa hoặc cắt ảnh tùy chỉnh từ thư viện, rồi kéo thả để sắp xếp lại thứ tự danh mục. Màn hình chi tiết danh mục có tìm kiếm riêng và các thao tác liên kết giống danh sách chính.

### Cài đặt

Chuyển ngôn ngữ, chế độ hiển thị và bảng màu giao diện. Người dùng có thể nhập/xuất CSV, mở hướng dẫn sử dụng, đăng xuất hoặc kiểm tra GitHub Release mới hơn.

## Dữ liệu và quyền riêng tư

LinkVault lưu dữ liệu cục bộ trên thiết bị bằng Room. Mỗi tài khoản Google đã đăng nhập có một kho dữ liệu riêng trên máy. Việc nhập/xuất CSV chỉ diễn ra khi người dùng chủ động thực hiện. Các file APK sinh ra, keystore, file `.env` và những artifact nhạy cảm khác không nên được đưa vào git.

## Công nghệ sử dụng

- Kotlin
- Jetpack Compose
- Material 3
- Room local database
- Kotlin Coroutines và Flow
- Google account picker
- OkHttp để kiểm tra GitHub Release công khai

## Phát triển

Mở dự án bằng Android Studio, đồng bộ Gradle dependencies, sau đó chạy cấu hình `app` trên emulator hoặc thiết bị Android thật.

Một số lệnh thường dùng:

| Lệnh | Mô tả |
| --- | --- |
| `gradle :app:assembleDebug` | Build APK debug |
| `gradle :app:testDebugUnitTest` | Chạy unit test cục bộ |
| `gradle :app:assembleRelease` | Build APK release đã ký khi biến môi trường ký release được cấu hình |

## Thông tin ứng dụng hiện tại

- Tên ứng dụng: **LinkVault**
- Bản phát hành hiện tại: **0.6**
- Minimum SDK: **24**
- Target SDK: **36**

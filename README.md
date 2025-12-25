# Read Out Loud (ROL) - Ứng dụng Đọc Văn Bản Tiếng Việt

**Read Out Loud (ROL)** là một ứng dụng Android mạnh mẽ giúp chuyển đổi văn bản thành giọng nói (Text-to-Speech) chất lượng cao, đặc biệt tối ưu cho tiếng Việt. Ứng dụng hỗ trợ cả công cụ TTS mặc định của hệ thống và công cụ AI tiên tiến từ FPT.AI.

## ✨ Tính năng nổi bật

- **Hỗ trợ đa công cụ TTS:**
  - **Device TTS:** Sử dụng các giọng đọc có sẵn trên điện thoại (Offline).
  - **AI TTS (FPT.AI):** Sử dụng công nghệ AI để mang lại giọng đọc tự nhiên, truyền cảm (Online).
- **Kho giọng đọc phong phú:**
  - Đầy đủ các vùng miền: Bắc, Trung, Nam.
  - Đa dạng giới tính: Nam và Nữ.
  - Các giọng đọc nổi tiếng từ FPT.AI như Ban Mai, Lê Minh, Thu Minh, Lan Nhi...
- **Đọc trong nền (Background Service):** Hỗ trợ đọc văn bản ngay cả khi bạn thoát ứng dụng hoặc tắt màn hình nhờ Foreground Service.
- **Xử lý văn bản thông minh:**
  - Hỗ trợ dán văn bản nhanh từ Clipboard.
  - Tự động lọc bỏ các ký tự đặc biệt (*, #, _, ~, ...) để giọng đọc mượt mà hơn.
- **Điều khiển âm lượng nhanh:** Tích hợp các mức âm lượng tối ưu (80%, 85%, 90%) ngay trên giao diện chính.
- **Giao diện hiện đại:** Thiết kế đơn giản, dễ sử dụng với Material Design.

## 🛠 Công nghệ sử dụng

- **Ngôn ngữ:** Kotlin
- **Framework:** Android SDK
- **UI:** View Binding, Material Components
- **TTS Engine:** 
  - Android TextToSpeech API
  - FPT.AI TTS API v5 (thông qua OkHttp & Coroutines)
- **Service:** Foreground Service với Notification định danh.

## 🚀 Hướng dẫn cài đặt

### Yêu cầu hệ thống
- Android 8.0 (API level 26) trở lên.
- Kết nối Internet (nếu sử dụng giọng đọc AI).

### Các bước thực hiện
1. Clone repository này về máy.
2. Mở project bằng **Android Studio**.
3. Build và chạy ứng dụng trên thiết bị thật hoặc giả lập.

## 📖 Hướng dẫn sử dụng

1. **Nhập văn bản:** Bạn có thể tự nhập hoặc nhấn nút **Dán** để lấy nội dung từ bộ nhớ tạm.
2. **Chọn loại giọng đọc:**
   - Chọn **Thiết bị** để dùng giọng đọc offline có sẵn.
   - Chọn **AI** để dùng giọng đọc chất lượng cao (Yêu cầu API Key).
3. **Cấu hình AI (Nếu dùng AI):**
   - Truy cập [console.fpt.ai](https://console.fpt.ai) để lấy API Key miễn phí.
   - Nhập API Key vào ô tương ứng trong ứng dụng.
4. **Bắt đầu đọc:** Nhấn nút **ĐỌC VĂN BẢN**. Bạn có thể điều chỉnh âm lượng hoặc dừng đọc bất kỳ lúc nào.

## 📝 Giấy phép

Dự án này được phát triển bởi **skul9x**. Vui lòng liên hệ nếu bạn có bất kỳ câu hỏi nào.

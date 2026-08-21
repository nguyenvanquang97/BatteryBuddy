# Project Guidelines & Memory Rules for BatteryBuddy

## 1. Error Handling & Problem Resolution Protocol (QUY TẮC BẮT BUỘC)

> [!IMPORTANT]
> **Khi người dùng thông báo có lỗi, sai kích thước, không đúng ý, hoặc có vấn đề**:
> 
> 1. **BƯỚC 1 - Trình bày nguyên nhân trước (Explain Root Cause First)**:
>    - Phải phân tích kỹ lưỡng, đo đạc dữ liệu thực tế và giải thích chi tiết, rõ ràng **TẠI SAO LỖI LẠI XẢY RA** cho người dùng hiểu.
>    - **TUYỆT ĐỐI KHÔNG** tự ý âm thầm sửa code, sửa ảnh, hay can thiệp vào file khi chưa giải thích nguyên nhân.
> 
> 2. **BƯỚC 2 - Đề xuất phương án & Chờ lệnh (Propose & Wait for Approval)**:
>    - Đưa ra giải pháp khắc phục cụ thể.
>    - **CHỈ THỰC HIỆN SỬA ĐỔI KHI NGƯỜI DÙNG YÊU CẦU / ĐỒNG Ý**.

---

## 2. Sprite Asset Scale & Alignment Rules (Chuẩn tỷ lệ & Cắt ảnh)

- **Thước đo Benchmark Bất Biến**: Mọi sprite Mèo bắt buộc phải lấy file **`app/src/main/res/drawable-nodpi/pet_look_front_01.png`** làm thước đo chuẩn $1:1$ (Chiều cao thân mèo $288\text{px}$, đường kính đầu $135\text{px}$, mắt xanh $95\text{px}$, chuông vàng $25\text{px}$, mặt đất $Y = 308\text{px}$).
- **Nguyên tắc cốt lõi**: Chuẩn hóa kích thước $1:1$ ngay từ **bước xuất file PNG**, TUYỆT ĐỐI KHÔNG dùng các hệ số `scale` nhân riêng theo từng event trong code `PetView.kt`.
- **Thông số chuẩn của toàn bộ hệ thống Sprite**:
  - **Chiều cao cố định**: $H = 320\text{px}$ cho toàn bộ file PNG.
  - **Đường kính đầu mèo**: $\approx 135\text{px} \pm 1\text{px}$.
  - **Đường kính chuông vàng**: $\approx 24\text{px} - 27\text{px}$.
  - **Mặt đất chuẩn (Ground Baseline)**: $Y = 308\text{px}$ (`BOTTOM_MARGIN = 12px`).
- **Quy tắc Canvas cho các tư thế duỗi dài & Cầm cờ (Wide Poses & Flags)**:
  - Giữ nguyên chiều cao $H = 320\text{px}$, mở rộng chiều ngang thành **$384 \times 320\text{px}$** (hoặc $400 \times 320\text{px}$) để thân, đuôi và cờ nằm trọn vẹn, có lề an toàn $\ge 15\text{px}$ mỗi bên.
  - **TUYỆT ĐỐI KHÔNG** ép tư thế duỗi dài vào khung vuông $320 \times 320\text{px}$ vì sẽ gây lẹm cụt mũi, đuôi hoặc cờ.
- **Quy tắc Lọc chi tiết thừa (BFS Component Isolation)**:
  - Luôn chạy thuật toán BFS trích xuất cụm pixel liên thông lớn nhất để loại bỏ hoàn toàn các chi tiết vẽ phụ (con bướm, bong bóng, mảnh vụn lem từ ô lân cận).

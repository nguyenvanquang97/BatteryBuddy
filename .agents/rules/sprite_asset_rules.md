---
trigger: always_on
---

# Sprite Asset & Slicing Architecture Rules

## 1. Thước đo Benchmark Bất Biến: `pet_look_front_01.png`
- Mọi thao tác cắt ảnh bắt buộc phải đối chiếu $1:1$ trực tiếp với file `app/src/main/res/drawable-nodpi/pet_look_front_01.png`:
  - **Chiều cao đứng chuẩn**: $288\text{px}$ (từ $Y=20$ đến $Y=307$).
  - **Đường kính đầu**: $135\text{px} \pm 1\text{px}$.
  - **Đường kính mắt xanh**: $95\text{px}$.
  - **Đường kính chuông vàng**: $25\text{px}$.
  - **Mặt đất chuẩn**: $Y = 308\text{px}$ (`BOTTOM_MARGIN = 12px`).

## 2. Không scale thủ công trong code
- Chuẩn hóa kích thước $1:1$ ngay từ **bước cắt/xuất file PNG**.
- `PetView.kt` chỉ vẽ theo `aspect ratio` tự nhiên của ảnh (`drawHeight = baseSize`, `drawWidth = baseSize * (bitmap.width / bitmap.height)`).

## 3. Tư thế duỗi dài (Wide Poses)
- Mở rộng chiều ngang thành **$384 \times 320\text{px}$** (hoặc $400 \times 320\text{px}$), giữ nguyên chiều cao $320\text{px}$.
- Không bao giờ ép vào khung vuông $320 \times 320\text{px}$ làm lẹm đuôi/mũi.
- Lề an toàn tối thiểu $\ge 15\text{px}$ mỗi bên.

## 4. Thuật toán lọc sạch (BFS Component Isolation)
- Luôn chạy BFS để chỉ lấy cụm liên thông lớn nhất của mèo/vật thể, xóa bỏ 100% các chi tiết thừa vẽ kèm trong ảnh AI.

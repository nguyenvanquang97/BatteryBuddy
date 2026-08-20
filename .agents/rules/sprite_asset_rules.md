---
trigger: always_on
---

# Sprite Asset & Slicing Architecture Rules

## 1. Không scale thủ công trong code
- Chuẩn hóa kích thước $1:1$ ngay từ **bước cắt/xuất file PNG**.
- `PetView.kt` chỉ vẽ theo `aspect ratio` tự nhiên của ảnh (`drawHeight = baseSize`, `drawWidth = baseSize * (bitmap.width / bitmap.height)`).

## 2. Thông số chuẩn bất biến của Sprite Mèo
- **Chiều cao cố định**: $H = 320\text{px}$.
- **Đường kính đầu**: $\approx 135\text{px} \pm 3\text{px}$ ($\approx 42.8\%$ chiều cao).
- **Đường kính mắt xanh**: $123\text{px} - 128\text{px}$.
- **Đường kính chuông vàng**: $24\text{px} - 26\text{px}$.
- **Mặt đất chuẩn**: $Y = 308\text{px}$.

## 3. Tư thế duỗi dài (Wide Poses)
- Mở rộng chiều ngang thành **$384 \times 320\text{px}$** (hoặc $400 \times 320\text{px}$), giữ nguyên chiều cao $320\text{px}$.
- Không bao giờ ép vào khung vuông $320 \times 320\text{px}$ làm lẹm đuôi/mũi.
- Lề an toàn tối thiểu $\ge 15\text{px}$ mỗi bên.

## 4. Thuật toán lọc sạch (BFS Component Isolation)
- Luôn chạy BFS để chỉ lấy cụm liên thông lớn nhất của mèo/vật thể, xóa bỏ 100% các chi tiết thừa vẽ kèm trong ảnh AI.

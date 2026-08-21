---
name: batterybuddy-sprite-slicing
description: >-
  Provides mathematical rules, baseline alignment formulas, alpha extraction algorithms,
  and automated scripts to cut, normalize, and export 2D sprite sheets into 320x320 px (or 384x320 px for wide poses)
  RGBA PNG assets for BatteryBuddy without distortion, clipping, or size mismatches. Use this skill whenever new sprite
  sheets are uploaded or image slicing is requested.
---

# BatteryBuddy Sprite Sheet Slicing & Normalization Skill

Use this skill to extract, remove solid black background, and normalize 2D character and object sprite sheets into the BatteryBuddy application.

---

## 1. Absolute Golden Benchmark Standard: `pet_look_front_01.png`

> [!IMPORTANT]
> **TẤT CẢ SPRITE MÈO BẮT BUỘC PHẢI LẤY FILE `app/src/main/res/drawable-nodpi/pet_look_front_01.png` LÀM THƯỚC ĐO CHUẨN BẤT BIẾN ($1:1$)**:
> - Mọi thao tác cắt ảnh, tính hệ số scale toàn cục $S$ bắt buộc phải đo đạc đối chiếu trực tiếp với các thông số thực tế của `pet_look_front_01.png`.

| Thông Số Benchmark | Giá Trị Chuẩn (`pet_look_front_01.png`) | Ý Nghĩa Kỹ Thuật |
| :--- | :--- | :--- |
| **Chiều cao đứng/ngồi chuẩn** | **$H_{\text{body}} = 288\text{px}$** (từ $Y=20$ đến $Y=307$) | Thước đo để tính $S = 288.0 / H_{\text{source}}$ |
| **Đường kính đầu (Head Width)** | **$135\text{px} \pm 1\text{px}$** | Giữ tỷ lệ đầu to Chibi chuẩn xác |
| **Đường kính mắt xanh (Eyes Span)**| **$95\text{px}$** | Khoảng cách 2 tròng mắt xanh |
| **Đường kính chuông vàng (Bell)** | **$24\text{px} - 27\text{px}$** (trung bình $25\text{px}$) | Quả chuông vàng đeo cổ |
| **Mặt đất chuẩn (Ground Baseline)** | **$Y = 308\text{px}$** (`BOTTOM_MARGIN = 12px`) | Điểm đặt chân cố định trên Status Bar |
| **Kích thước Canvas** | $320 \times 320\text{px}$ (chuẩn) hoặc $384 \times 320\text{px}$ (tư thế duỗi rộng) | Chiều cao luôn cố định $H = 320\text{px}$ |

---

## 2. The 6 Golden Rules of Sprite Slicing

### Rule 1: Calibrate Global Scale to Benchmark `pet_look_front_01.png`
- **Kích thước mèo phải chuẩn $1:1$ ngay từ bước xuất file PNG**:
  - Hệ số scale toàn cục $S$ được tính toán sao cho chiều cao thân mèo đạt đúng **$288\text{px}$** và đường kính đầu đạt đúng **$135\text{px}$** như file `pet_look_front_01.png`.
  - Tuyệt đối không dùng các hệ số scale nhân tay trong `PetView.kt`.

### Rule 2: Ground Baseline Alignment (Căn chuẩn mặt đất)
- For grounded frames (standing, walking, landing, crouching):
  $$\text{Placement } Y = 308 - \text{scaled\_height}$$
- For airborne/jumping frames:
  $$\text{airborne\_px} = \text{ground\_y\_source} - \text{max\_y}$$
  $$\text{Placement } Y = 308 - \text{scaled\_height} - (\text{airborne\_px} \times \text{Global Scale})$$

### Rule 3: Wide Poses Canvas Expansion (Mở rộng chiều ngang cho tư thế duỗi dài, chống lẹm)
- Khi nhân vật có tư thế duỗi dài (như phi thân chạy ~475px trong cell):
  - **GIỮ NGUYÊN CHIỀU CAO** $H = 320\text{px}$ và giữ nguyên tỷ lệ đầu $137\text{px}$.
  - **MỞ RỘNG CHIỀU RỘNG CANVAS** thành **$384 \times 320\text{px}$** (hoặc $400 \times 320\text{px}$).
  - Căn giữa nhân vật theo chiều ngang để có lề an toàn $\ge 15\text{px}$ mỗi bên.
  - **TUYỆT ĐỐI KHÔNG** ép vào canvas $320 \times 320\text{px}$ vì sẽ bị xén cụt mũi và đuôi.
  - `PetView.kt` tự động render theo intrinsic aspect ratio (`drawWidth = drawHeight * (bitmap.width / bitmap.height)`).

### Rule 4: Cell Overlap & BFS Component Isolation (Khử lem viền & Xóa chi tiết rác thừa)
- **Cell Overlap Margin (`CELL_OVERLAP = 40` px)**:
  - Khi crop từng cell từ lưới $2 \times 2$ hoặc $3 \times 2$, luôn mở rộng 40px mỗi chiều để không làm cụt đuôi hoặc tai mèo vượt sang ô khác.
- **BFS Component Isolation**:
  - Dùng thuật toán duyệt đồ thị BFS tìm cụm pixel liên thông lớn nhất của chủ thể chính trong ô.
  - Xóa sạch (Alpha $= 0$) toàn bộ các chi tiết vẽ phụ (con bướm, bong bóng, mảnh vụn lem từ ô lân cận).

### Rule 5: Anti-aliased Black Keying (Khử nền đen mịn lông)
- For each pixel $(R, G, B)$ with luminance $L = \max(R, G, B)$:
  - $L \le 15 \implies \text{Alpha} = 0$
  - $15 < L \le 48 \implies \text{Alpha} = \text{round}\left(\frac{L - 15}{33} \times 255\right)$, and un-premultiply RGB: $C = \min\left(255, \text{round}\left(C \times \frac{255}{\text{Alpha}}\right)\right)$
  - $L > 48 \implies \text{Alpha} = 255$

### Rule 6: Character vs Object Alignment
- **Character (Mèo)**: Căn theo Mặt đất chuẩn $Y = 308$ px, Frame nhảy giữ độ cao bay lơ lửng, kiểm tra đường kính đầu mèo **$135 \text{ px} \pm 3 \text{ px}$**.
- **Object / Creature (Bướm, chim, vật thể)**: Căn giữa hình học ($X_{\text{center}}, Y_{\text{center}}$) với cờ `--is-object`.

---

## 3. Automated Slicing Scripts

- **2x2 Sprites**: `python3 tools/extract_2x2_sprites.py <source_path> <prefix> [--is-object]`
- **3x2 Sprites (Vồ & Ngơ ngác)**: `python3 tools/extract_3x2_sprites.py`
- **Wide Running Sprites ($384 \times 320$)**: `python3 tools/extract_clean_run_sprites.py`

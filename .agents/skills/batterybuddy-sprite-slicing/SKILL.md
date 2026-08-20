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

## 1. Core Technical Specifications

| Parameter | Value | Description |
| :--- | :--- | :--- |
| **Canvas Dimensions** | $320 \times 320$ px (chuẩn) hoặc $384 \times 320$ px (tư thế duỗi dài) | Chiều cao luôn cố định $H = 320$ px |
| **Color Format** | 32-bit RGBA PNG | Transparent background (Alpha channel) |
| **Ground Baseline ($Y$)** | **$Y = 308$ px** | Distance from feet of grounded characters to bottom is $12$ px (`BOTTOM_MARGIN = 12`) |
| **Head Width Diameter** | **$135 \text{ px} \pm 3 \text{ px}$** | Standard head width of Status Cat across all states (`IDLE`, `WALK`, `RUN`, `SIT`, `POUNCE`) |
| **Eye Diameter** | **$123\text{px} - 128\text{px}$** | Blue pupil width across standard animations |
| **Bell Diameter** | **$24\text{px} - 26\text{px}$** | Gold bell width on sky blue collar |
| **Max Content Bound** | $296 \times 280$ px (trên canvas $320$) / $360 \times 280$ px (trên canvas $384$) | Minimum 12-20px safe padding on all 4 sides, zero clipping |

---

## 2. The 6 Golden Rules of Sprite Slicing

### Rule 1: Calibrate Global Scale to Head & Standing Height (Không scale cục bộ, không dùng code scale)
> [!IMPORTANT]
> **Kích thước mèo phải chuẩn $1:1$ ngay từ bước xuất file PNG**:
> - Hệ số scale toàn cục được tính dựa trên **Đường kính đầu ($135\text{px} \pm 3\text{px}$)** hoặc **Chiều cao ngồi/đứng chuẩn ($288\text{px}$)**.
> - Tuyệt đối không dùng các hệ số scale nhân tay trong `PetView.kt` vì sẽ làm vỡ tỷ lệ khi chuyển trạng thái.

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

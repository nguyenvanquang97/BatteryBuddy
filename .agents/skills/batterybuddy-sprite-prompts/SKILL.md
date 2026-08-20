---
name: batterybuddy-sprite-prompts
description: >-
  Provides guidelines, character specifications, layout rules, and prompt templates for generating
  high-quality, razor-sharp 2D sprite sheets for BatteryBuddy (Status Cat). Use this skill whenever
  the user asks to generate, design, or write prompts for new cat animations, events, or visual assets.
---

# BatteryBuddy Sprite Sheet Generation Skill

Use this skill to construct exact, high-accuracy, ultra-sharp prompts for ChatGPT (DALL-E 3 / Midjourney) when generating new animation frames, actions, or event assets for the **BatteryBuddy** pet app.

---

## 1. Golden Rules for Sprite Sheets in BatteryBuddy

### A. Bố cục linh hoạt: Lưới $2 \times 2$ (4 frames) hoặc $3 \times 2$ (6 frames) trên Canvas 1024×1024
- **TUYỆT ĐỐI KHÔNG dùng 1 hàng ngang $1 \times 4$ hay $1 \times 6$**:
  - Hàng ngang làm tỷ lệ ảnh bị dẹt ($1024 \times 512$ px), khiến nhân vật bị thu nhỏ xuống chỉ còn ~180px, dẫn đến vỡ hạt, răng cưa và mờ nét khi crop.
- **DÙNG lưới $2 \times 2$ (4 frames) hoặc $3 \times 2$ (6 frames cho chuỗi hành động dài như Vồ mồi $\rightarrow$ Hụt $\rightarrow$ Ngơ ngác)**:
  - Giúp mỗi frame nhân vật to tối đa (~$340 \times 450$ px), nét vẽ dày dặn, sắc lẹm và chi tiết mắt, lông, chuông rõ nét 100%.
  - Tận dụng tối đa không gian $1024 \times 1024$ px để biểu đạt trọn vẹn mạch cảm xúc.

### B. Nền bắt buộc: Đen tuyền đơn sắc tuyệt đối (`#000000`)
- **TUYỆT ĐỐI KHÔNG dùng nền trắng**: Nền trắng làm cháy viền lông trắng/xám khi tách alpha trong suốt.
- Nền đen bảo toàn trọn vẹn từng sợi lông tơ, nơ xanh, chuông vàng mà không bị lem viền hoặc răng cưa.
- Prompt luôn phải có câu: *"Solid pure pitch black background (#000000), absolutely NO floor shadow, NO floor line, NO gradient, NO background glow"*.

### C. Nét vẽ & Phong cách (Rendering Directives)
- Luôn chỉ định: *"Crisp, ultra-sharp vector outlines, smooth clean cel-shading (NO noisy brush texture, NO grainy blur)"*.
- Luôn nhắc người dùng đính kèm ảnh nhân vật mẫu (Reference Image) khi gửi prompt cho ChatGPT.

### D. Phân biệt rõ ràng: Sprite Mèo vs Sprite Sinh vật/Vật thể ngoài (CRITICAL)
- **Khi tạo Sprite MÈO (Cat Actions)**:
  - Bắt buộc đính kèm ảnh mèo gốc làm reference.
  - Mô tả đầy đủ: Mèo con Chibi lông trắng xám, mắt to tròn xanh biếc, nơ xanh cổ đính chuông vàng (`#FFD700`), đuôi to bông xù, quay mặt sang TRÁI.
- **Khi tạo Sprite VẬT THỂ / SINH VẬT KHÁC (Bướm, Chim, Chuột, Bát sữa, Bóng...)**:
  - > [!CAUTION]
    > **TUYỆT ĐỐI KHÔNG đính kèm ảnh mèo** vào ChatGPT (hoặc nếu gửi chung session thì phải cấm triệt để: *"Standalone creature/object, absolutely NO cat face, NO cat body, NO cat ears, NO paws, NO collar"*). Nếu không, AI sẽ tự động ghép đầu mèo vào sinh vật (như tạo ra "Mèo Bướm").
  - Mô tả cấu tạo sinh vật/vật thể thuần túy với màu sắc rực rỡ ăn khớp với phong cách Chibi.

---

## 2. Các mẫu Prompt chuẩn chỉnh (Standard Templates)

### 🏃 Mẫu 1: Hành động của Mèo (Cat Sprite - Lưới $2 \times 2$)
> *Đính kèm ảnh mèo mẫu làm reference.*
```text
Based on the character design and exact art style in the reference image (the fluffy grey-and-white chibi cat with big blue eyes, fluffy tail, and a sky-blue ribbon collar with a gold bell):

Create a 2D game animation sprite sheet of this same cat [ACTION_NAME: e.g. RUNNING AT FULL SPEED (gallop cycle)].
Arrange the 4 sequential frames in a clean 2x2 square grid (1024x1024) on a solid pitch black background (#000000), making each cat large and filling its cell:

- Top-Left (Frame 1): [FRAME_1_DESCRIPTION]
- Top-Right (Frame 2): [FRAME_2_DESCRIPTION]
- Bottom-Left (Frame 3): [FRAME_3_DESCRIPTION]
- Bottom-Right (Frame 4): [FRAME_4_DESCRIPTION]

Style requirements:
- Crisp, ultra-sharp vector outlines, smooth clean cel-shading (NO noisy brush texture, NO grainy blur).
- Large character scale filling the grid cells evenly.
- Side profile view facing LEFT.
- Solid pure pitch black background (#000000) with NO floor shadows, NO glow.
```

### 🦋 Mẫu 2: Sinh vật / Vật thể ngoài (Non-Cat Object/Creature - Lưới $2 \times 2$)
> *KHÔNG đính kèm ảnh mèo.*
```text
2D game animation sprite sheet of a real, cute [CREATURE/OBJECT: e.g. magical glowing butterfly insect] (a standalone creature/object, absolutely NO cat face, NO cat body, NO fur, NO paws, NO collar).

Arrange 4 sequential animation frames in a clean 2x2 square grid (1024x1024) on a solid pitch black background (#000000), with each [item] filling its cell:

- Top-Left (Frame 1): [FRAME_1_DESCRIPTION]
- Top-Right (Frame 2): [FRAME_2_DESCRIPTION]
- Bottom-Left (Frame 3): [FRAME_3_DESCRIPTION]
- Bottom-Right (Frame 4): [FRAME_4_DESCRIPTION]

Style requirements:
- Cute 2D anime game asset art style, vibrant fantasy colors.
- Crisp ultra-sharp vector outlines, smooth clean cel-shading (NO grainy blur).
- Large scale in each grid cell for clean sprite extraction.
- Solid pure pitch black background (#000000) with NO floor shadows, NO background glow.
```

---

## 3. Quy trình cắt ảnh tự động (Auto-slicing Workflow)
Sau khi có ảnh, sử dụng Skill [`batterybuddy-sprite-slicing`](file:///Users/macbook/Desktop/work/BatteryBuddy/.agents/skills/batterybuddy-sprite-slicing/SKILL.md) và công cụ:
```bash
python3 tools/extract_2x2_sprites.py <đường_dẫn_ảnh> <output_prefix> [--is-object]
```

# Светлый hero — 2026-10-21

Финальный ресурс: `app/src/main/res/drawable-nodpi/hero_jeland_light.png`.
Тёмный исходник `hero_jeland.png` оставлен без изменений. Смена ресурсов привязана к `UiState.dark`,
включая AUTO и штатный day/night signal. Компоновка блоков, размеров hero и остальных компонентов не менялась.

Встроенный инструмент image_gen, режим редактирования по исходному hero. Не CLI/API.
Сохранены чёрный J7, ракурс, масштаб, положение. Новый фон — светлый зимний день в горах.
Светлая тема без тёмного scrim и боковой дымки; только последние 8% высоты мягко переходят в фон приложения.
Текст hero/температуры/шапки: #030B12 днём и #E9F0F5 ночью — точные фоны противоположной темы.
Активные Wi-Fi/Bluetooth/4G используют тот же цвет, неактивные — его прозрачный вариант.
Остальной artText сохранён: светлый текст поверх тёмных обложек/карты не инвертируется.

## Финальный промпт встроенного генератора

Use case: lighting-weather / precise background edit. Asset type: production background image for Jeland OS LIGHT THEME automotive dashboard hero, landscape wide 2:1. Image 1 is the EDIT TARGET. Preserve the exact black JELAND J7 SUV, identity, all bodywork, grille, J7 plate, wheels, front-left three-quarter viewpoint, position and scale from this image. Keep the whole car visible and sharp; do not substitute another SUV. Re-create the surrounding scene as premium high-key daytime alpine photography: pale snowy mountain ridges, almost-white softly overcast sky, bright off-white snow/light limestone ground, subtle cool grey-blue detail. The overall background is very light and airy, near #E9F0F5, not dark blue. Upper left and upper right sky must remain clean and pale for dark time/date/weather text and connection icons added in code. Car remains deep glossy black with realistic daylight reflections, grounded contact shadows. Maintain the original composition, lens, horizon and crop, car in the lower-middle area. Mountains may be atmospheric but avoid smeared/blurry edges, artificial vignette, dark side borders, dramatic dark rocks or dark gradient. Outer scene edges and lower ground should be naturally pale and seamless with a very light blue-grey application background. No UI, no text overlays, no border, no frame, no icons, no extra objects, no watermark. Output a photorealistic finished background, NOT a dashboard mockup.

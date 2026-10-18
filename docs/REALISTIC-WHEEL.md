# Реалистичное колесо — 0.6.3

Ресурс: app/src/main/res/drawable-nodpi/wheel_realistic.png.
Создан встроенной генерацией изображений (imagegen), не фотография конкретного
заводского диска. Общая стилистика: реалистичная резина, графитовый диск с
обработанными серебристыми спицами, небольшой ракурс и прозрачный фон.
Исходный результат скопирован в проект без удаления оригинала и без потери alpha.
В приложении отображается через Image / ContentScale.Fit, 84 dp, без tint.
Показания шин и команды не изменены; отсутствие live-температуры осталось явным.

Проверка сборки: 78 unit-тестов без ошибок; 4 Android-теста шинного окна и
регрессий FieldFixes прошли в BlueStacks 127.0.0.1:5556. Проверены alpha и
прозрачные углы PNG, обе темы, размеры и текстовые показания, отсутствие подмены
live-температуры данными DEMO. Снимки: screenshots/qa-0.6.3/.
APK: artifacts/JelandOS-0.6.3-realistic-wheel.apk, versionCode 21.

## Финальный промпт

Use case: product-mockup. Asset type: photorealistic transparent PNG cutout for the tyre-pressure detail panel of a premium automotive Android UI, replacing a flat wheel icon. Create ONE complete realistic modern SUV wheel with tyre mounted on a metallic alloy rim. Near-frontal three-quarter product photograph, only a small angle (about 12 degrees), so the rim reads almost circular while a little tread depth remains visible at the left edge. Dark graphite wheel with bright machined silver split spokes, realistic metal reflections, five lug bolts, subtle visible brake disc; black rubber with believable fine grain, sidewall relief and clean tread. Clean premium studio product photography with a soft cool rim light, neutral bright highlights, no cartoon or line-art treatment. Centered square composition, entire wheel visible, wheel fills about 88 percent of the frame with even small transparent margin. GENUINELY TRANSPARENT background with alpha, including outside the wheel silhouette; no floor, no backdrop, no card, no surrounding car. No text, brand logos, badges, labels, watermark, UI elements, coloured halo or exaggerated glow. It must remain recognizable at 72dp on dark navy and light grey panels.

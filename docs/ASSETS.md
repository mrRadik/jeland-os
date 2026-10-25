# Изображения первого этапа

Использован навык imagegen, встроенная генерация, референс пользователя
`codex-clipboard-eccd226d-c872-4a5d-a90c-a58d546952dd.png`.
Никакие ресурсы OEM APK или JCarTools в интерфейс не копировались.

Текущий горный фон версии 0.1.1 заменён по новому фото автомобиля:
[исходные изображения и точный промпт imagegen](HERO-J7-0.1.1.md).

## hero_jeland.png — исходная версия 0.1.0 (заменена в 0.1.1)

Use case: product-mockup. Asset type: a clean full-bleed landscape hero photograph for a native Android automotive dashboard, NOT a UI screenshot. Image 1 is a subject/composition/style reference only. Generate a photorealistic premium black Jeland SUV closely matching the vehicle silhouette and front three-quarter view in the reference, parked on a rocky alpine plain before snowy mountains at blue hour. Wide 2:1 banner composition, vehicle fully visible in lower central area, top 30 percent mostly atmospheric dark blue sky and mountain scenery suitable for separately rendered time/weather UI. Restrained deep navy, graphite, silver reflections, subtle cold blue rim lighting. Highly detailed believable black paint, wheels and glass. No screen bezel, dashboard frame, interface, buttons, icons, clock, weather, typography, captions or watermark. This is just the scenic car artwork; all application UI will be rendered in Compose.

Фон непрозрачный. Визуальный кроп выполняет Compose ContentScale.Crop, файл не редактировался.

## seat_jeland.png

Use case: product-mockup. Asset type: isolated 3D seat illustration for a native Android automotive climate popup. Image 1 is a style and subject reference only: match the realistic black leather seat illustrations at the bottom. Generate one complete premium black automotive front seat, front three-quarter view, facing slightly right, with headrest, perforated leather backrest and cushion, subtle stitching, metallic seat base. Physically plausible studio-quality 3D render with cold soft rim highlights. The whole seat fully inside the image with 8% padding. Genuine transparent alpha background, no scene, no ground plane, no frame, no panel, no UI, no text, no arrows, no watermark. Neutral unlit leather: no built-in orange/red heating glow and no blue ventilation glow, these effects will be drawn interactively by the application. Single seat only.

Альфа-канал сохранён. Цветные индикаторы поверх иллюстрации рисуются Compose.

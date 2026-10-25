# Замена автомобиля в 0.1.1-mock

Использован навык imagegen, встроенный инструмент (не CLI/API fallback).
Пользователь попросил вставить именно машину с нового фото на существующий горный фон.
Проверено визуально: характерные решётка, узкие фары, силуэт кузова и серебристые диски соответствуют новому референсу.
Это генеративный композит, а не гарантированно попиксельная вклейка оригинальной фотографии.

Вход 1 (фон/заменяемая сцена): прежний hero_jeland.png версии 0.1.0.
Вход 2 (машина): codex-clipboard-7885c507-57fd-4d05-a643-ba0d580e0719.png.
Копия исходного фото сохранена в docs/references/jeland-j7-user.png.
Итоговый ресурс: app/src/main/res/drawable-nodpi/hero_jeland.png.
SHA-256 PNG: 517AF921C84517A852925481E8CC9BC560B7553C10076642B42B4CA5DE776C9A.
Сиденье, темы, раскладка и mock-функции не менялись. Изображение применяется и в предварительном разделе автомобиля.

## Точный итоговый промпт

+Use case: compositing.
Asset type: full-bleed 2:1 landscape hero image for the existing Jeland OS Android application, artwork only, not a UI screenshot.
Input images: Image 1 is the EDIT TARGET/base mountain scene. Image 2 is the exact SUBJECT TO INSERT, a black Jeland J7 photographed in front-left three-quarter view.
Primary request: Remove the incorrect SUV in Image 1 completely and replace it with the exact car from Image 2, on the same mountain foreground. The new vehicle must be immediately recognizable as Image 2, not a hybrid with Image 1's vehicle. Preserve Image 2's actual body silhouette, short rectangular grille, slim rectangular headlights, front bumper, hood lettering JELAND, J7 license plate, front/side proportions, silver mirror trim, roof rails and distinctive silver/black multi-spoke wheels. Preserve its original three-quarter viewing angle and face toward the left. Do not redesign or reinterpret the car; integrate the supplied car into the scene.
Scene: keep the snowy mountain panorama, rocky alpine foreground, blue-hour sky and restrained navy/silver atmosphere of Image 1. Match cool lighting, tire contact shadows, reflections and perspective naturally. Maintain high visibility of the dark body with restrained cold highlights.
Composition: retain Image 1's 2:1 wide banner framing; whole car visible in lower-central area, approximately the same visual size as the original vehicle, plenty of sky/mountains in the upper 30 percent for clock/weather overlays that the app renders separately. Keep wheels fully visible and vehicle clear of all edges.
Constraints: change the vehicle only with necessary local shadow/reflection blending. One car only. No UI, dashboard bezel, clock, weather icons, captions, new text, watermark or added props. Keep the physical JELAND and J7 markings from Image 2.

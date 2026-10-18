# Иллюстрация салона — 0.4.7

Встроенный imagegen, не CLI. Новый фоновый растровый ресурс вместо простой
Canvas-схемы. Это стилизованная иллюстрация салона, не технически точная копия J7.

Ресурс проекта: app/src/main/res/drawable-nodpi/climate_cabin_047.png.
Оригинал сохранён в C:/Users/nonam/.codex/generated_images/01a13e11-b5c5-7473-bfcf-a8293dd39388/exec-d595dc90-f9a9-4347-8afd-32859730826e.png.

Фон не содержит потоков и не изображает активный режим. Потоки рисуются отдельными
программными слоями из обратного состояния климата; при выключенном питании или
неизвестном направлении отсутствуют. Изображение и координаты потоков используют
одинаковое центральное кадрирование, без растяжения. Карточка имеет пропорции 4:1,
чтобы не срезать руль. Новых разрешений или команд автомобилю нет.

## Финальный промпт

Use case: stylized-concept. Asset type: production bitmap background for a very wide 4.8:1 automotive climate-control panel, not a whole UI mockup. Primary request: a polished realistic 3D illustration of a modern left-hand-drive SUV dashboard and front cabin, seen centered from between the front seats slightly above the armrest, looking straight forward. Premium dark navy/black Jeland OS visual language, restrained icy-blue edge lighting, soft realistic charcoal leather grain, brushed dark metal details, four precise slim horizontal air vents, an elegant three-spoke steering wheel at image x=23%, a small sculpted instrument hood behind it, an unlit thin landscape central infotainment screen at x=52%, the full dashboard spanning edge to edge. Lower footwell recesses visible; no seats obscuring the dash. Composition/framing: panoramic 1536x512 image; ALL important dashboard details, steering wheel, vents and screen must fit within the middle vertical 65 percent so the image can be center-cropped to a shallow 4.8:1 strip. Maintain a clear spatially believable dashboard, not schematic shapes. Background through windshield is almost black midnight blue with no scenery. Lighting: readable shaded surfaces, subtle cyan ambient trim along dashboard seam, premium calm OEM automotive configurator render, crisp controlled highlights and soft shadows, no exaggerated glowing. Constraints: ONLY the cabin illustration. No airflow ribbons, no arrows, no smoke, no logos, no lettering, no UI, no controls drawn on top, no border, no watermarks, no people. Avoid toy-like circles, flat vector diagram, flat rectangles, oversaturated neon, cyberpunk, chrome overload. The rendered image should integrate with a #081925 dark app card.

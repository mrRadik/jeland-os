# Hero: одна сцена, день и ночь — 0.9.11

Дата: 2026-10-21. Встроенный image_gen (не CLI/API). Тип правки: lighting-weather.
Дневной мастер создан из прежнего hero_jeland.png; ночной вариант — из нового дневного мастера.
Сохраняются чёрный J7, камера, скалистый берег, озеро, горы и сезон. Меняется освещение.
Генеративная правка не гарантирует пиксельного совпадения мелких деталей.

Ресурсы приложения:

- app/src/main/res/drawable-nodpi/hero_jeland_day_v2.png
- app/src/main/res/drawable-nodpi/hero_jeland_night_v2.png

Выбор по UiState.dark: LIGHT — день, DARK — ночь; AUTO использует существующий штатный
day/night signal. Системная тема не заменена расписанием или погодным запросом.
Старые ресурсы оставлены для музыкальных обложек/других потребителей и истории.
Компоновка, высота hero, климат, навигация и управление автомобилем не менялись.

Оригиналы встроенного генератора сохранены:

- День: C:/Users/nonam/.codex/generated_images/01a13e11-b5c5-7473-bfcf-a8293dd39388/exec-d1e522cc-de8e-4336-844b-590bb0fac685.png
- Ночь: C:/Users/nonam/.codex/generated_images/01a13e11-b5c5-7473-bfcf-a8293dd39388/exec-2fa9142f-d22d-4a0b-a8f5-6242891a1444.png

## Полный дневной промпт

```text
Use case: lighting-weather.
Asset type: Jeland OS automotive dashboard hero background, matching day/night pair, DAY master.
Edit target: supplied existing dusk hero photo. Regenerate it as genuine bright DAYTIME at noon. Change ONLY illumination, sky brightness and physically matching reflections/shadows; preserve the exact scene and composition.
Scene: black JELAND J7 parked front-left three-quarter view, facing left on a wet rocky alpine lakeshore with snow-capped jagged mountains. Keep every mountain silhouette, lake shore, dark bare foreground rock, puddle, cloud arrangement, tree line, car body, wheels, grille and J7 plate in the same position. Snow ONLY where present on distant mountain peaks; DO NOT turn the shore into winter or add snow, trees, structures or objects.
Lighting: pale blue bright daylight sky, soft white clouds, naturally illuminated dark glossy black SUV, realistic neutral daylight. Bright open upper sky for dark UI text. Not sunset, no orange sky, not blue-hour. Premium realistic automotive photography, crisp restrained natural details.
Framing: exactly the same wide 2:1 canvas, 1774x887 preferred, same fixed camera and full visible vehicle lower center, no crop/zoom/reposition, upper-left and upper-right remain unobstructed for UI. This image will be the spatial master for an identical nighttime version.
Constraints: no UI, no time/date/weather text, no labels or border, no vignette, no blurred edges, no gradient into background, no extra logos or watermarks; preserve existing JELAND lettering and J7 plate. No season or weather changes.
```

## Полный ночной промпт

```text
undefined
```


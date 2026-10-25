# Сторонние компоненты и данные

Лицензия Jeland OS применяется только к собственным материалам проекта.
Она не ограничивает права, предоставленные лицензиями перечисленных компонентов.
Их названия и товарные знаки принадлежат соответствующим правообладателям.

| Компонент | Версия в проекте | Условия и источник |
| --- | --- | --- |
| MapLibre Native Android OpenGL | 13.7.1 | BSD-2-Clause, https://github.com/maplibre/maplibre-native |
| Valhalla Mobile | 0.6.4 | MIT, https://github.com/Rallista/valhalla-mobile |
| Valhalla | движок/тайлы 3.9.1 | MIT, https://github.com/valhalla/valhalla |
| Ferrostar | 0.58.0 | BSD-3-Clause, https://github.com/stadiamaps/ferrostar |
| sherpa-onnx | 1.13.8, локальная AAR | Apache-2.0, https://github.com/k2-fsa/sherpa-onnx |
| ONNX Runtime | в составе нативного TTS-компонента | MIT, https://github.com/microsoft/onnxruntime |
| AndroidX, Jetpack Compose, Room | см. app/build.gradle.kts | Apache-2.0, https://android.googlesource.com/platform/frameworks/support/ |
| Kotlin, coroutines | см. Gradle | Apache-2.0, https://github.com/JetBrains/kotlin |
| Lucide / Feather | локальные векторные контуры | ISC / MIT; уведомления в app/src/main/assets/licenses/ |
| Noto Sans Regular | локальные глифы карты | SIL OFL 1.1, app/src/main/assets/navigation/fonts/Noto Sans Regular/LICENSE.md |

Полные тексты основных лицензий включены в `app/src/main/assets/licenses/`
и вместе с ними поставляются в APK. Лицензии транзитивных зависимостей
и уведомления самих библиотек продолжают действовать независимо от этой таблицы.
Старые модели Supertonic не входят в сборку 0.9.35 и не публикуются в Git.

## OpenStreetMap и карты

© OpenStreetMap contributors. Данные доступны по ODbL 1.0:
https://www.openstreetmap.org/copyright и https://opendatacommons.org/licenses/odbl/1-0/.
Подготовленные картографические базы в `app/src/main/assets/navigation/` сохраняют
условия ODbL; собственная лицензия Jeland OS не запрещает их независимое
использование, изменение или распространение, разрешённое ODbL.

Тестовый регион получен из открытой выгрузки Geofabrik:
https://download.geofabrik.de/russia/ural-fed-district-latest.osm.pbf.
Границы, SHA-256 исходной выгрузки, состав и контрольные суммы подготовленных
баз сохранены в `navigation/bundled/manifest.json`. Готовые базы включены
в репозиторий для получения отдельно от программы на тех же условиях ODbL.

Обзорная карта: © OpenStreetMap contributors · Natural Earth · Protomaps.
Источник и атрибуция: `navigation/overview/russia-overview.json`;
https://protomaps.com/ и https://www.naturalearthdata.com/about/terms-of-use/.

## Отдельный движок RuVoice

RuVoice 1.1.1 — автор kost-t-human:
https://github.com/kost-t-human/ruvoice-tts/releases/tag/v1.1.1.
Модель Silero v5_5_ru: https://github.com/snakers4/silero-models.
Для движка и модели указаны CC BY-NC-SA 4.0; полный текст и исходная атрибуция
сохранены в `app/src/main/assets/voice/ruvoice/`. Этот APK/модель не включены
в APK Jeland OS и устанавливаются пользователем отдельно, без модификации.
Разрешение добровольных донатов Jeland OS не изменяет условий этих компонентов.

## Совместимость со штатным ГУ

Названия Desay, Jeland, Bluetooth, CarPlay и других систем используются для
описания совместимости. Это не заявление об официальной поддержке производителем.
Системные APK, прошивки автомобиля, личные журналы и ключи подписи в репозиторий
и релиз Jeland OS не включаются.

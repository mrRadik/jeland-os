# Сторонние компоненты и данные

Лицензия Jeland OS применяется только к собственным материалам проекта.
Она не ограничивает права, предоставленные лицензиями перечисленных компонентов.
Их названия и товарные знаки принадлежат соответствующим правообладателям.

| Компонент | Версия в проекте | Условия и источник |
| --- | --- | --- |
| CoMaps SDK, native engine and map assets | 2026.10.05-19, commit 7f3b5be | Apache-2.0; source in third_party/comaps, upstream NOTICE/LICENSE and map asset copyright.html distributed with the app. https://codeberg.org/comaps/comaps |
| AndroidX, Jetpack Compose, Room | см. app/build.gradle.kts | Apache-2.0, https://android.googlesource.com/platform/frameworks/support/ |
| Kotlin, coroutines | см. Gradle | Apache-2.0, https://github.com/JetBrains/kotlin |
| Lucide / Feather | локальные векторные контуры | ISC / MIT; уведомления в app/src/main/assets/licenses/ |

Полные тексты основных лицензий включены в `app/src/main/assets/licenses/`
и вместе с ними поставляются в APK. Лицензии транзитивных зависимостей
и уведомления самих библиотек продолжают действовать независимо от этой таблицы.
## OpenStreetMap и карты CoMaps

© OpenStreetMap contributors. Данные доступны по ODbL 1.0:
https://www.openstreetmap.org/copyright и https://opendatacommons.org/licenses/odbl/1-0/.
Лицензия Jeland OS не ограничивает самостоятельное использование и распространение
картографических баз на условиях их собственных лицензий.

Обзорные карты и данные оформления берутся из закреплённого выпуска CoMaps.
Полная атрибуция ресурсов, LICENSE, NOTICE и сведения о сборке включены в APK
в `assets/licenses/comaps/`; исходники SDK — в `third_party/comaps`.
Подробные регионы скачиваются из каталога CoMaps по выбору пользователя.

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

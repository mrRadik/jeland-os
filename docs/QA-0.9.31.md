# APK 0.9.31 — отдельные устройства сидений

24.10.2026. versionCode 72, versionName 0.9.31-seat-devices.

## Сборка и файл

BUILD.ps1 / assembleDebug: BUILD SUCCESSFUL. Использована прежняя Android Debug
подпись (не отдельный production release key); apksigner verify успешно.
Сертификат SHA-256 совпадает с предыдущей сборкой:
6d72d2852f58666fcf0694e55552c3731b739c9ed2bbcae9fb59899d0e94c94b.

aapt подтверждает ru.jeland.os, versionCode 72, minSdk 30, targetSdk 35.
Файл JelandOS-0.9.31-seat-devices.apk, 218392186 байт.
Копии в artifacts и Jeland-ADB-Collector-Portable совпадают:
54B263EBE4A02CABB045B6179F76F62D66BB8F939BEBBD2D35CA1D5A2A6FD4E1.

В обоих каталогах обновлён jelandos-update.json по настоящему APK.
RUN-BLUESTACKS и комплект START → V указывают на 0.9.31;
синтаксис изменённых portable-скриптов проверен. START → 1 позволяет выбрать APK.
Старые APK не удалены. Probe и RuVoice не менялись. Публикация не выполнялась.

## Границы проверки

Функциональные проверки исходников перед сборкой: 41 JVM-тест и 15 625
сочетаний старых настроек в SQLite — [подробности](QA-SEAT-DEVICES.md).
Полный набор тестов повторно не запускался. Этот APK не устанавливался ни
на ГУ, ни на эмулятор; новый интерфейс и обновление на устройстве не проверены.
Автоматизации всё ещё MOCK, реальные команды автомобиля не подключались.

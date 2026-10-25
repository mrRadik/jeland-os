# 0.9.19 — автозапуск на ГУ без экрана разрешений

22.10.2026. versionCode 60, versionName 0.9.19-startup-hu.

## Основание

В JelandProbeLogs_20261022_131814_160_35804.zip: OS 0.9.17, startup.enabled=true, permissionGranted=false, lastBoot=none. Переход к ACTION_MANAGE_OVERLAY_PERMISSION возвращает ActivityNotFoundException. Полной загрузки после установки в архиве не зафиксировано. Исправленный NEW_TASK из 0.9.18 сохранён; одной замены APK для разрешения недостаточно.

## Изменения

SystemStartup пробует стандартный package-scoped экран, затем общий экран того же разрешения. Если оба отсутствуют/доступ запрещён, state.permissionScreenUnavailable=true; настройки показывают START → A и кнопку «Проверить разрешение», без повторных попыток открыть отсутствующий экран через эту кнопку. MainViewModel и onResume перечитывают реальный Settings.canDrawOverlays; после выдачи через установщик блокировка снимается. Сама OS не выдаёт права и не рисует overlay. Готовность разрешения не означает доказанный запуск поверх OEM-лаунчера.

В portable добавлены пункт A и Jeland-OS-Startup.ps1. Сначала проверяются выбранное устройство, активный пользователь 0, наличие ru.jeland.os и manifest-разрешений BOOT/SYSTEM_ALERT_WINDOW. После отдельного подтверждения выполняется только `cmd appops set --user 0 ru.jeland.os SYSTEM_ALERT_WINDOW allow`, затем обязательно get. Уже разрешённое состояние не перезаписывается. -Status ничего не меняет; -Revoke после подтверждения возвращает только этот app-op в default. Никаких shell-команд для изменения own preferences: opt-in остаётся в приложении. Нет grant-all, root, HOME-роли, перезапуска служб, записи системных файлов/прошивки или автомобильных SET. На импорте Jeland-Tools.ps1 никаких действий нет.

Установщик APK лишь напоминает о пункте A для установленного пакета ru.jeland.os; автоматической выдачи разрешений нет. Сбор логов L дополнен own app-op, boot_count, sys.boot_completed и тегом JelandOSStartup. Охват файлов/системных APK не расширен. Температура шин и другие функции не менялись.

Ограничение: BOOT_COMPLETED — полная загрузка Android, а не выход ГУ из сна. OEM может не доставить событие/перекрыть Activity позднее; это отдельно проверяется на машине. Обходов политик и циклов перехвата фокуса нет. [Ограничения открытия Activity из фона](https://developer.android.com/guide/components/activities/secure-bal), [стандартное разрешение и отсутствие обработчика в отдельных прошивках](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_OVERLAY_PERMISSION).

## APK сначала, затем проверки

Собраны assembleDebug и assembleDebugAndroidTest, без запуска полной suite. Как и прошлые устанавливаемые версии, APK подписан debug-ключом; отдельной release signing-конфигурации нет. До тестов скопирован в artifacts/JelandOS-0.9.19-startup-hu.apk и D:/projects/Jeland-ADB-Collector-Portable/JelandOS-0.9.19-startup-hu.apk. Размер 471471586 байт. SHA-256 обеих копий: 44D213912EB1D8F287C6F05D9FE2CAD38C70767EEA5D3C95387EB68BAADD9ACF. apksigner: valid v2; aapt: versionCode 60/versionName 0.9.19-startup-hu.

После выкладки именно этот APK установлен в локальный BlueStacks Rvc64 (127.0.0.1:5555). SystemStartupInstrumentation и SystemStartupUiInstrumentation: **OK (10 tests)**, 2.241 с. Включая отсутствие package route → общий экран; отсутствие обоих → инструкция установщика; рефреш при внешнем разрешении; boot-флаги, opt-in, отказ без повторов и UI в обеих темах. Используется подставной Context/permission-check, не настоящий ребут. QaPreferenceGuard восстанавливает настройки вокруг QA.

tools/test-startup-permission.ps1 под Windows PowerShell 5.1: **PASS 9** сценариев с полностью подставным ADB (status без записи; grant+verify; уже разрешено; SecurityException даже с exit=0; повторное чтение не подтвердило; не тот пользователь; пакет отсутствует; старый manifest; отзыв default). Фиксированные допустимые команды проверяются mock-реализацией; иной вызов вызывает ошибку. Физическое ГУ не подключалось и права на нём не менялись. PowerShell syntax checks прошли для меню, установщика, нового скрипта и сборщика логов.

Снимки screenshots/qa-0.9.19/startup-dark.png и startup-light.png просмотрены: текст/переключатель читаемы, обрезки нет. Сценарий отсутствующего экрана проверен UI assertions.

Дополнительно новый скрипт выполнен на настоящем Android локального эмулятора 127.0.0.1:5555: сначала -Status без изменений (No operations / Default mode: default), затем выдача через -Yes и повторный get подтвердили allow. В finally только этот app-op восстановлен в исходный default и проверен. Журнал JelandOSStartup_20261022_160100_242_20680 относится к эмулятору, не машине. Русский вывод скрипта проверен в Windows PowerShell 5.1; новый файл с UTF-8 BOM.

Реальная выдача разрешения и cold boot конкретного ГУ остаются обязательными пользовательскими проверками. APK после тестов не изменялся.

# 0.9.27 — запуск после сна ГУ

23 октября 2026. VersionCode 68, versionName 0.9.27-wake-startup.

## Причина и реализация

Ранее SystemStartup реагировал только на BOOT_COMPLETED, что объясняет
работающий запуск после ребута и отсутствие запуска после suspend/resume.
В разобранном штатном CarState ScreenManager использует PowerManager
wakeUp/goToSleep. Это основание слушать защищённые события экрана Android,
а не выдумывать ACC-broadcast или отправлять команды автомобилю.

Добавлена неэкспортируемая WakeStartupService, активная только при уже
существующих enabled=true и пользовательском SYSTEM_ALERT_WINDOW.
Та же галочка включает/выключает службу; foreground channel LOW, без звука,
с уведомлением и действием «Отключить автозапуск». Android 14+ specialUse
с соответствующим разрешением/описанием; minSdk остаётся 30, targetSdk 35.
На Android 13+ при невыданных уведомлениях отображение FGS зависит от
платформы; отдельного принудительного запроса уведомлений не добавлено.

Служба стартует при открытии приложения/изменении галочки, BOOT_COMPLETED
и MY_PACKAGE_REPLACED. Обновление пакета только восстанавливает наблюдение,
не открывает Activity. Default-off сохранён. BOOT очищает старый pending.
Служба не создаёт AppContainer и не подключается к VDB/климату/телеметрии.

Динамический RECEIVER_NOT_EXPORTED: только системные SCREEN_OFF/SCREEN_ON/
USER_PRESENT. Manifest SCREEN_ON не используется (он требует динамической
регистрации). SCREEN_OFF сохраняет pending; пробуждение объединяется одним
отложенным вызовом через 600 мс. Перед запуском проверяются opt-in,
разрешение, isInteractive и блокировка экрана. При keyguard ожидание
USER_PRESENT, а не запуск поверх замка. Pending потребляется ДО launch.
Если MainActivity уже resumed, запуск не нужен, цикл тоже потребляется.
Повторы/отказ startActivity не вызывают перехват фокуса или новые попытки.
Следующий шанс — только следующий SCREEN_OFF. При повторном сне отложенное
действие снимается. При пересоздании службы pending восстанавливается;
START_STICKY допускает обычное восстановление Android без собственного
цикла перезапусков. stopWithTask=false; никаких alarm/job/watchdog.

Нет WAKE_LOCK, удержания экрана/CPU, сетевого/периодического опроса,
батарейных исключений, смены лаунчера, root, правки прошивки, команд авто.
Permission API/флаги обычного MainActivity прежние; запуск REQUESTED
не считается доказательством физического фокуса окна на OEM.

## Диагностика

События — JelandOSStartup и собственный ограниченный 64-КиБ журнал
JelandOS_vehicle_0.log (имя совместимо с существующей очисткой собственных
журналов). ZIP экспорта дополнен lastWake, wakePending, wakeWatcher;
существующие enabled/permission/lastBoot сохранены.
START → L дополнен dumpsys activity services ru.jeland.os и dumpsys power
с тайм-аутами 12 с: можно отличить отсутствие службы, сон, REQUESTED и
отсутствие реального окна. Только чтение.

## APK прежде проверок

BUILD.ps1 assembleDebug успешен. Как и предыдущие выдаваемые APK,
это подписанная debug-сборка для установки, не Gradle release-вариант.
Сначала скопирована в artifacts и portable, затем запускались тесты.
После уточнения имени журнала APK пересобрана и обе собственные новые
копии обновлены. Старые версии/данные не удалены.
Меню V / Jeland-Install-OS-Voice и RUN-BLUESTACKS обновлены на 0.9.27,
чтобы прежняя камера-сборка не ставилась поверх исправления.

Итоговый SHA-256 обеих копий:
`CAE24C6C4692D7C9C8F938E3D466407B9059ABCE80F046D24A1A6822062E77FB`.

## Проверки и честные ограничения

- PASS: шесть JVM-тестов WakeStartupGate — первоначальный экран ON,
  один запуск/дубли, lock/unlock, восстановление pending, повторный сон,
  clear и потребление до внешней попытки запуска.
- PASS: девять прежних fake-ADB сценариев разрешения автозапуска.
- PowerShell синтаксис изменённых startup/collector скриптов проверен.
- SystemStartupInstrumentation дополнен fake-Context тестами wake opt-in,
  разрешения, ошибки запуска и декларации приватной FGS.
- Итоговая assembleDebugAndroidTest успешна, включая новый локальный
  screen-cycle тест; aapt подтверждает code68 / 0.9.27 / min30 / target35.
- Подготовлен настоящий screen-cycle тест, но только для локального
  эмулятора с явным localWakeSmoke=true и hardware vbox/goldfish/ranchu.
  Он не выдаёт разрешения, сохраняет/восстанавливает собственные ключи.
  Без флага/на ГУ тест пропускается.
- BlueStacks при этой проверке недоступен на 127.0.0.1:5555; попытка
  запуска не дала доступного процесса. Инструментальные тесты и реальный
  цикл экрана в этой сессии **не выполнялись**. На машину APK не ставилась.

Android force-stop, принудительное OEM-отключение пакета или запрет фоновой
службы не обходятся. Если прошивка именно force-stop делает при каждом ACC
OFF, стандартное ожидание экрана не сможет само воскресить остановленный
пакет: нужны свежие логи. Также OEM может позже перекрыть запрошенное окно;
постоянного возвращения фокуса намеренно нет.

Для машины: START → 1, открыть OS один раз, проверить включённую галочку,
выключить/включить автомобиль обычным способом без ребута Android.
Если не открылось — START → L. Проверка остаётся необходимой.

Официальные источники:

- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use).
- [Background-start restrictions / boot exceptions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).
- [Broadcast lifecycle and dynamic registration](https://developer.android.com/develop/background-work/background-tasks/broadcasts).

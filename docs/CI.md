# Проверки и Pull Request

Workflow `.github/workflows/android-checks.yml` запускается для PR в `main`,
изменений `main` и вручную через GitHub Actions. Проверка — **Android checks**.

Выполняются сборка APK, модульные тесты, Android Lint и компиляция Android-тестов:

```sh
bash ./gradlew --no-daemon --console=plain --stacktrace --continue \
  testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin
```

Нужны JDK 21, Python 3.11+, Android SDK Platform 36 и Build Tools 35.0.0.
Перед локальной сборкой выполните `git submodule update --init third_party/comaps`.
CI загружает этот подмодуль автоматически. Подготовка движка и карт описана
в [инструкции CoMaps](COMAPS.md).
Отчёты JUnit и Lint доступны в Actions в `android-check-reports` в течение 7 дней.
Отдельная проверка **Windows installer checks** проверяет синтаксис PowerShell
и сценарии установщика в Windows с подменённым ADB, без подключения к устройству.
Android-тесты на устройстве и проверка в автомобиле выполняются отдельно.
CI не имеет доступа к ГУ или ключу подписи и ничего не публикует.

Предлагайте изменения отдельной веткой через PR. Перед слиянием должны пройти
обязательные проверки; решение принимает владелец. Используется Squash merge.
Автоматическое слияние не включается.

Не добавляйте ключи, токены, сторонние APK, персональные журналы, временные сборки
и кэши. Для интерфейса приложите скриншоты; для интеграции с ГУ укажите модель
автомобиля и прошивку.

## Сборка Windows-установщика

На компьютере разработчика нужны Python 3.12+, готовый подписанный APK,
оригинальный RuVoice 1.1.1, Platform Tools для Windows и `aapt` из Android SDK.

```powershell
python tools/windows-installer/make_bundle.py --apk JelandOS-<версия>.apk --voice ruvoice-tts-1.1.1.apk --aapt <SDK>/build-tools/35.0.0/aapt.exe --adb-dir <SDK>/platform-tools --output <новая-папка>
```

Скрипт проверяет состав APK, формирует контрольные суммы, лицензии и инструкцию
и создаёт ZIP. На ГУ он ничего не запускает. В Releases архив публикуется под
именем `JelandOS-Windows-Installer.zip`, чтобы ссылка на latest оставалась постоянной.
Исходники установщика находятся в `tools/windows-installer`; большие APK и ZIP
хранятся только в Assets релизов.

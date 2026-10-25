# J7: Android-выход, OEM-область и реализация JCT

## Новое физическое подтверждение пользователя

Если включить штатную навигацию на физической приборке, тестовая надпись Probe
появляется обоими способами. Теперь подтверждён не только Android screencap,
но и физический путь Android display 2 → навигационный режим приборки.
Это не результат нового APK или нового вызова, а полевой результат пользователя.

Снимок и SurfaceFlinger из прошлого архива показывают overlay поверх карты:
display 2, 1920×720, слой карты z=0, метка z=1. Следовательно, в пределах этого
Android-выхода порядок слоёв правильный. Изменение типа overlay/Presentation
само по себе не включает вывод этого выхода в остальных режимах штатной приборки.

## Что найдено в локальном JCarTools

NS-jadx/sources/com/example/navistart/SpeedBoardService.java:
getDisplays(), настройка priDisplayID как индекс массива, createDisplayContext(),
WindowManager.addView(). Для собственной графики колодцев v0/d.java использует
TYPE_APPLICATION_OVERLAY=2038, полноэкранный размер -1×-1, flags=776.
То есть отдельного универсального Android «над всеми штатными ECU-слоями»
в этом renderer не обнаружено. Это тот же класс способа вывода, что у Probe.

НО renderer не является всей реализацией. В JCT есть AreaDisplayProxy:

- OpenProPri() — профильный вход для открытия навигации;
- OpenCustomPPPri() — профильный вход для кастомной приборки;
- OpenProGu(), StartProGu(String).

e0/v0.java и e0/d0.java вызывают профильные методы отдельно от создания окна.
В n0/c.java после простого XOR-декодирования имён профиль CarProfile_JAECOO_J7
ссылается на:

    com.example.navistart.carsC.cars.desaysv.ProInfoJ
    com.example.navistart.carsC.cars.desaysv.ProLan
    com.example.navistart.carsC.cars.desaysv.ProBro
    com.example.navistart.carsC.cars.desaysv.AreaDisplay

Обёртка и контракт доступны, реализация последнего класса в текущих исходниках
не восстановлена: профиль подгружается динамически. Называемые .dex файлы
Все 37 NS-modules-decrypted/m_*.dex по факту всё ещё не имеют DEX-заголовка. Файлы
jcartools-memory-*.dex валидные, но не содержат нужный профиль AreaDisplay;
JCT-telemetry-jadx — Android-служебные классы, не готовая реализация J7.
Нельзя утверждать, что полный J7-профиль расшифрован, или копировать неизвестные
команды из названий. Проверок лицензии/активации не изменяли, JCT не устанавливался
на автомобиль и не использовался для отправки команд при этом исследовании.

Официальное описание также отделяет профильный запуск, кастомную приборку,
виджеты и полноэкранный режим; предупреждает о зависимости от модели/прошивки:
[Первая настройка JCarTools](https://www.jcartools.ru/guides/first-setup/).
Это описание функций, не документация Binder/CAN-протокола.

## Штатный код подтверждает отдельное управление областью

work/system-jadx/map/sources/com/desaysv/ivi/vds/navi/a/a.java:

- d(): firstStartNaviDisplayToQNX; VDNaviDisplayCluster с
  naviFrontDeskStatus=true, displayCluster=false, perspective=0,
  perspectiveResult=false, requestDisplayNaviArea=true;
- c(): подтверждение отображения навигации, displayCluster=true;
- b(): отзыв отображения/навигационной области, флаги false;
- a(int), e(), f(), g(): отдельный VDNaviDisplayArea, в разных путях 10/2/0.

CarLan event NAVIGATION_DISPLAY_TO_CLUSTER=721699 (0xB0323),
NAVIGATION_DISPLAY_AREA=721702 (0xB0326). DTO и handshake отличаются от нашего
окна WindowManager. В штатном коде прямо назван QNX; это объясняет, почему
Android z-index нельзя автоматически считать z-index всей физической приборки.

IVI EngMode — ещё один отдельный CabinLan протокол msgType=4101/subtype=16 с
EnterOrExitEngineeringMode; это не верхнее Android-окно. Его fullscreen-эффект
не доказывает, что приложению достаточно повысить WindowManager.LayoutParams.type.

## Вывод и следующий объект исследования

Говорить «не тот Android-слой» неточно: выбран рабочий Android-канал навигации,
но нужно отдельно активировать/согласовать его область в OEM-композиторе приборки.
JCT содержит отдельный профильный шаг для этого — AreaDisplay — помимо renderer.
Дальше исследовать реализацию AreaDisplay и OEM NAVIGATION_DISPLAY_AREA/
VDNaviDisplayCluster, их feedback/restore и доступ обычному APK.

Ни один из указанных OEM/CabinLan запросов не включён в whitelist OS и не
отправлялся. Самостоятельно пробовать 0/2/10 как «размеры» или режим инженерной
приборки нельзя считать готовой интеграцией: неизвестны границы области и возврат.
Поэтому текущая OS не перекрывает штатные предупреждения и не заменяет приборку.

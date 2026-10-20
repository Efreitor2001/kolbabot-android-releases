# Колбабот Android

Самостоятельный Android Studio проект приложения `ru.kolbabot.app`. Версия 1.0.2 (4).

Исходная версия 1.0.1 получена из ветки `codex/android-1.0.1` репозитория [kolbabot.ru](https://github.com/Efreitor2001/kolbabot.ru). Точный коммит записан в `source-origin.json`. Этот каталог имеет отдельный локальный Git-репозиторий, не зависит от расположения проекта сайта и не содержит его сервер, базу данных или `.env`.

## Скачать

Текущий выпуск — [1.0.2](https://github.com/Efreitor2001/kolbabot-android-releases/releases/tag/v1.0.2):

- [RuStore APK](https://github.com/Efreitor2001/kolbabot-android-releases/releases/download/v1.0.2/KolbaBot-1.0.2-rustore.apk) — универсальная сборка для установки на Android.
- [Проект Android Studio](https://github.com/Efreitor2001/kolbabot-android-releases/releases/download/v1.0.2/KolbaBot-1.0.2-AndroidStudio.zip) — архив исходников этого выпуска.

Все опубликованные файлы находятся в [релизах приложения](https://github.com/Efreitor2001/kolbabot-android-releases/releases). Google AAB предназначен для Google Play Console и напрямую на телефон не устанавливается.

Android 8.0 и новее. Интерфейс на русском языке. Для работы нужен интернет; приложение использует аккаунты и сервер сайта [Колбабот](https://kolbabot.ru).

Поддержка: [obolo4ki@kolbabot.ru](mailto:obolo4ki@kolbabot.ru).

## Сборка

В Android Studio откройте каталог `KolbaBot`, содержащий `settings.gradle.kts`. Требуется Android SDK Platform `android-37.0`; AGP/Gradle зафиксированы в проекте. Версию JDK для Gradle задаёт `gradle/gradle-daemon-jvm.properties` (в текущей локальной настройке — 25); без этого файла скрипт предпочитает JDK 21.

```powershell
.\build.ps1 -Check
.\build.ps1 -Check -DeviceTests
.\build.ps1 -Release
```

Первая команда собирает debug APK, запускает JVM-тесты и lint. Вторая дополнительно выполняет UI-тесты на запущенном эмуляторе/телефоне. Релизная команда создаёт Google APK/AAB и универсальные RuStore/Huawei APK. Настройки подписи читаются из внешнего файла `%USERPROFILE%\.config\KolbaBot\signing\signing.properties`; отсутствие ключа завершает релизную сборку ошибкой. Firebase использует публичный `google-services.json`; локальный Huawei `agconnect-services.json` исключён из Git, в APK попадает только разрешённая публичная конфигурация.

`signing.properties` — файл настроек подписи. Сам ключ находится в файле `.jks`, путь к которому указан в свойстве `storeFile`. Команда `./build.ps1 -Release` читает эти настройки автоматически. В мастере Android Studio **Generate Signed App Bundle or APK** в поле **Key store path** укажите полный путь к `.jks`, а в **Key alias** — значение `keyAlias` из настроек. Для текущей локальной установки это `C:\Users\Efreitor2001\.config\KolbaBot\signing\kolbabot-release.jks` и alias `kolbabot`. Пароли берутся из локального файла настроек; новый ключ для обновления приложения создавать не нужно.

В PowerShell открыть папку подписи можно командой `explorer.exe "$env:USERPROFILE\.config\KolbaBot\signing"`. В поле пути мастера Android Studio используйте полный путь. Каталог `.config` доступен и обычным приложениям Windows, и средам со служебным перенаправлением `AppData`. Для существующих установок сохранено чтение прежнего `%LOCALAPPDATA%/KolbaBot/signing/signing.properties`, если нового файла нет. Переменная `KOLBABOT_SIGNING_PROPERTIES` имеет приоритет над обоими путями.

Скрипт совместим с Windows PowerShell 5.1 и PowerShell 7; сохраняйте `build.ps1` в UTF-8 с BOM. Если `JAVA_HOME` не задан, скрипт ищет JDK в установленных рядом версиях Android Studio и предпочитает версию из `gradle/gradle-daemon-jvm.properties`, когда этот файл существует.

По умолчанию приложение работает с `https://kolbabot.ru`. Тестовый HTTPS сервер: `-PKOLBABOT_BASE_URL=https://test.example.org`. Проект использует существующий API сайта; изменения серверной части для сборки не требуются.

## Интерфейс

Эталон — сайт: Manrope с кириллицей встроен в APK, цвета, градиенты, скругления, поля и кнопки заданы общими `SiteStyle.kt` / `SiteControls.kt`. Этот слой используется и пользовательскими экранами, и админкой. Нативные экраны сохраняют общую учётную запись, каталог и корзину сайта.

Выбор ПВЗ использует существующие Ozon API сайта и карту MapLibre/PMTiles. Только карта размещена в изолированном WebView; экраны, формы, списки и графики приложения нативные. Срок доставки поступает с сервера, в том числе при отключённом оформлении через Ozon; Android не добавляет дни повторно. Неизвестный срок показывается как неизвестный. Цена и возможность оплаты повторно проверяются сервером.

Вход во внешние аккаунты проходит через браузер с сохранёнными сессиями провайдера. Пароли внешних аккаунтов приложение не получает. Незавершённый вход хранится зашифрованно с исходным сроком действия; после возврата результат проверяется сразу.

## Структура и проверка

- `app/` — Android-приложение, тесты, ресурсы.
- `.github/workflows/android-build.yml` — CI для корня самостоятельного проекта.
- `RELEASE.md`, `VALIDATION.md`, `CI.md` — выпуск, результаты проверок и автоматическая сборка.

Готовые релизы, карты R8 и материалы завершённых проверок храните вне исходного проекта. Временные каталоги `validation/`, `releases/`, результаты сборок и экспорты Android Studio исключены из Git. Документация импортированной версии доступна в истории Git.

Прежние исходники и файлы сборки вынесены из сайта в соседнюю папку `KolbaBot-archive-20261016`. Исходники, ресурсы и маршруты самого сайта не изменялись. Код и APK публикуются отдельно в [kolbabot-android-releases](https://github.com/Efreitor2001/kolbabot-android-releases), основная ветка проекта — `main`. Публикация на GitHub не означает публикацию в магазинах.

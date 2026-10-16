# Выпуск Android

Версия: 1.0.2, versionCode: 4. Команда: `./build.ps1 -Release`.

- Google APK: `app/build/outputs/apk/google/release/app-google-release.apk`.
- Google AAB: `app/build/outputs/bundle/googleRelease/app-google-release.aab`.
- RuStore APK: `app/build/outputs/apk/rustore/release/app-rustore-release.apk`.
- Huawei APK: `app/build/outputs/apk/huawei/release/app-huawei-release.apk`.
- R8 mapping: `app/build/outputs/mapping/<variant>/mapping.txt`.

Сохраняйте прежний ключ подписи `ru.kolbabot.app`. Он читается из внешнего `%LOCALAPPDATA%/KolbaBot/signing/signing.properties`, либо пути `KOLBABOT_SIGNING_PROPERTIES`. Свойства: storeFile (абсолютный путь), storePassword, keyAlias, keyPassword. Секреты не передаются в CLI и не включаются в исходный ZIP.

Оригинальный сертификат официальной APK: SHA-256 `366f244016a77d9e51edeede4ea5369b80a0ac474a7b359598d7d9485044912f`. Версия 1.0.1 была тестовой; этот выпуск повышает versionCode до 4 и использует исходный ключ. Проверяйте сертификат готовых APK командой `apksigner verify --print-certs`.

Результаты текущей проверки записываются в `VALIDATION.md`. Локальная сборка не означает публикацию или прохождение модерации. Исторические сведения и ограничения магазинов исходной версии находятся в `docs/upstream/RELEASE.md`; их актуальность перед публикацией проверяется отдельно.

# HANDOFF.md — как продолжить работу над Ritmo

Читать по порядку: `AGENTS.md` → `PRODUCT.md` → `PROJECT_STATE.md` → `README.md`.

## Состояние

- MVP завершён: все три этапа реализованы и приняты пользователем.
- Последний утверждённый выпуск: `2610.18.9` (коммит `ff36640`, релиз `v2610.18.9`).
- Активной задачи нет. Работа начинается только по отдельному заданию пользователя;
  новые этапы самостоятельно не открывать.

## Обстановка

- Проект `D:\Pr\ritmo`, ветка `main`, коммит и push без Pull Request и force-push.
- Сборка: `export JAVA_HOME=/d/Pr/tools/jdk; export GRADLE_USER_HOME=/d/Pr/tools/gradle-home;`
  `./gradlew assembleDebug assembleRelease`.
- Версия: `./gradlew newVersion` (`ггмм.дд.номер`, `versionCode = ггммдд * 100 + номер`).
- Релиз: `gh release create vX dist/ritmo-X.apk dist/ritmo-X-debug.apk`. Подпись —
  `D:\Pr\keys\ritmo-release.jks` с `keystore.properties` (в git не попадают), ключ не менять.
- Проверка логики без устройства: `./gradlew -p D:/Pr/schedule-check run` — временный JVM-проект
  с копиями `Reminder.kt` и `Schedules.kt` (в репозиторий не входит); скопировать туда исходники.

## Порядок работы над заданием

1. Сверить объём с `PRODUCT.md`; сверх задания ничего не добавлять.
2. Внести изменения, собрать debug и release, проверить версию и подпись.
3. Обновить документацию: устаревшее заменять, историю не накапливать.
4. Коммит в `main`, push, GitHub Release, отчёт с SHA, ссылкой и размером APK.
5. Остановиться до проверки пользователем.

## Карта кода

`Schedules.kt` — расчёт срабатываний. `Reminder.kt` — модель, JSON, реактивация, починка значений.
`ReminderStore.kt` — хранение (`AtomicFile`). `AlarmScheduler.kt`, `ReminderReceiver.kt`,
`BootReceiver.kt` — будильники, уведомления, восстановление расписания. `Backup.kt` — копии.
`MainActivity.kt` и `ReminderAdapter.kt` — список. `EditActivity.kt` — форма и автосохранение.
`SettingsActivity.kt` и `UpdateChecker.kt` — тема, разрешения, копии, обновление.
`ThemePrefs.kt`, `Insets.kt`, `Formats.kt` — темы, отступы, форматирование.

## Чек-лист ревьюера (ChatGPT)

1. Сверить коммит и релиз с отчётом: SHA, версия, `versionCode`, размер и подпись (`CN=Ritmo`).
2. Проверить изменённую логику по существу (`Schedules.kt`, `Backup.kt`, `ReminderStore.save`).
3. Отказ импорта не меняет данные; ошибка записи не выглядит как успешное сохранение.
4. Дефекты указывать точечно, без переделки дизайна и расширения объёма работ; ничего
   не объявлять завершённым до проверки пользователем.

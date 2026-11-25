# Архитектура Hori Browser

## Разделение ответственности

| Слой | Технология | Отвечает за |
| --- | --- | --- |
| UI | Kotlin + Jetpack Compose | адресная строка, вкладки, меню, жизненный цикл |
| Мост | UniFFI (JNA) | типобезопасные вызовы Kotlin → Rust |
| Ядро | Rust `hori-core` | классификация ввода, настройки, SQLite; далее закладки, история, фильтры, sync |
| Движок | GeckoView | рендеринг, JS, сеть, Web API |

Ключевое правило: **Rust не знает об Android-объектах**. `GeckoSession`,
`GeckoView`, `Context` живут только в Kotlin. Через FFI ходят строки,
числа и простые record-типы.

## Вкладки

Один `GeckoRuntime` на процесс (`GeckoEngine`), по `GeckoSession` на вкладку
(`GeckoSessionManager`), один `GeckoView`, который перепривязывается к активной
сессии. UI-состояние вкладки (`TabState`) — immutable data class в StateFlow.

Восстановление сессий (0.2) будет храниться в Rust: список URL + индекс активной
вкладки, Kotlin при старте пересоздаёт GeckoSession по этим данным.

## Поток ввода адреса

1. Пользователь жмёт Go → `BrowserViewModel.submitAddress`.
2. `HoriCore.classifyInput` → Rust `navigation::classify`:
   явная схема / похожий на хост без пробелов → URL (https по умолчанию),
   иначе поиск через шаблон `search_engine` (`%s`).
3. Kotlin грузит результат в активную GeckoSession.

## Хранилище

SQLite (`rusqlite`, bundled) в `filesDir/hori.db`, WAL. Миграции —
`storage::migrate` + `PRAGMA user_version`. Таблицы закладок/истории/вкладок
добавляются в следующих milestone тем же механизмом.

## Блокировка (план 0.4)

Не перехватывать всё вручную: точка входа — `NavigationDelegate.onLoadRequest`
и/или WebExtension content-blocking. Правила фильтрации компилируются в
`hori-filter` (Rust), Kotlin только применяет вердикт.

## Что намеренно НЕ делается

- свой HTML-рендерер;
- перенос сетевых запросов Gecko в Rust;
- передача Android-объектов через FFI.
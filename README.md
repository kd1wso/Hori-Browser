# Hori Browser

Android-браузер: **GeckoView** как сменный веб-движок, **Rust** как ядро логики,
**Kotlin/Compose** как тонкий Android-слой.

## Архитектура

```
Kotlin (UI, вкладки, GeckoSession)
   │  UniFFI (JNA)
Rust hori-core (навигация, настройки, SQLite; позже: закладки, история, фильтры, sync)
   │
GeckoView (Gecko: HTML/CSS/JS, сеть, отрисовка)
```

- `android/` — Gradle-проект, Jetpack Compose UI.
- `core/` — крейт `hori-core` (cdylib + UniFFI scaffolding).
- `tools/build-core.ps1` — кросс-компиляция `.so` и генерация Kotlin-bindings.
- `docs/` — архитектура и сборка.

## Статус: 0.1

- одна GeckoRuntime, несколько GeckoSession (вкладки уже есть);
- адресная строка: URL vs поиск решает Rust (`classify_input`);
- назад/вперёд/обновить, переключение и закрытие вкладок;
- настройки в SQLite (`hori.db`).

Дальше по плану: 0.2 восстановление сессии → 0.3 закладки/история/загрузки →
0.4 блокировка трекеров → 0.5 приватный режим → 0.6 Hori Sync.

## Сборка

См. [docs/BUILDING.md](docs/BUILDING.md). Коротко:

```powershell
pwsh tools/build-core.ps1          # Rust -> jniLibs + Kotlin bindings
cd android
.\gradlew.bat assembleDebug        # APK
```

## Лицензия

MPL-2.0 (как GeckoView/UniFFI).
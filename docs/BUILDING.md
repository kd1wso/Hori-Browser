# Сборка Hori Browser

## Требования

- JDK 17+ (проверено на Microsoft OpenJDK 21)
- Android SDK: platform 36, build-tools; `ANDROID_HOME` задан
- Rust (stable) + targets: `rustup target add aarch64-linux-android x86_64-linux-android`
- `cargo install cargo-ndk`
- Android NDK (проверено на r28) в `$ANDROID_HOME/ndk/`

## Шаги

1. **Rust-ядро и bindings** (из корня репозитория):

   ```powershell
   pwsh tools/build-core.ps1            # debug
   pwsh tools/build-core.ps1 -Release   # release (lto, strip)
   ```

   Скрипт кладёт `libhori_core.so` в `android/app/src/main/jniLibs/{arm64-v8a,x86_64}/`
   и генерирует Kotlin-bindings в `android/app/src/main/kotlin/dev/hori/core/`.
   Оба каталога — генерируемые, в git не хранятся.

2. **APK**:

   ```powershell
   cd android
   .\gradlew.bat assembleDebug
   ```

   Результат: `android/app/build/outputs/apk/debug/app-debug.apk`.

3. **Установка на устройство** (например, Xiaomi 13 по USB с включённой отладкой):

   ```powershell
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   adb shell am start -n dev.hori.browser/.MainActivity
   ```

## Тесты ядра

```powershell
cargo test -p hori-core
```

## CI на GitHub

`.github/workflows/android.yml`: тесты ядра → сборка `.so` + bindings
(`tools/build-core.sh --release`) → `assembleDebug`/`assembleRelease` →
артефакты APK; по тегу `v*` — GitHub Release с APK.
Локальный `local.properties` в git не попадает, CI задаёт SDK сам.

## Замечания

- GeckoView тянется с `https://maven.mozilla.org/maven2/` (подключён в `settings.gradle.kts`).
- Версия GeckoView закреплена в `android/app/build.gradle.kts`; обновлять осознанно.
- UniFFI bindings используют JNA (`net.java.dev.jna:jna@aar`), `.so` пакуется
  с `useLegacyPackaging = true`, чтобы JNA мог загрузить библиотеку по имени.
- Если `cargo ndk` не находит NDK, задайте `ANDROID_NDK_HOME` на конкретную версию.
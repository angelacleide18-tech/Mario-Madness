# Android port (experimental)

This port adds multitouch arrows, Dodge (Space), OK/Pause (Enter), and Back
(Escape), Android video playback, packaged-video extraction into app-private
storage, and guards for Windows-only desktop effects. The original credits and
license apply. Desktop window/wallpaper effects are intentionally unavailable
on Android. Touch controls use the default arrow/Space/Enter/Escape bindings.

## Build

Use Haxe 4.2.5, Neko, JDK 11, Android SDK 33/build-tools 33.0.2, and NDK
21.4.7075529. Install these project-local Haxelib versions:

```
haxelib newrepo
haxelib install lime 8.0.2
haxelib install openfl 9.2.0
haxelib install flixel 5.3.1
haxelib install flixel-addons 3.1.1
haxelib install flixel-ui 2.5.0
haxelib install hxCodec 2.6.1
haxelib install hxcpp 4.3.2
haxelib install hscript 2.7.0
```

Configure `lime setup android` with the SDK, NDK, and JDK paths, then run:

```
haxelib run lime build android -debug
```

Output is under `export/debug/android/bin`. Debug builds use a development
signature; keep that signing key to make future APK updates installable without
uninstalling the old build. Do not commit signing keys.

## Device validation still required

A successful compile is not a playthrough. Test installation, title/menu/map,
multitouch chords and holds, dodge mechanics, pause/resume, videos, app background
and foreground transitions, and save persistence on a physical Android device.
The source assets total about 1.8 GB. Budget additional storage for installed
assets and extracted video files. Two textures exceed 8192 pixels, and many
exceed 4096; devices with smaller maximum texture sizes need atlas repacking
before this port can be considered supported. The minimum Android API is 23.

## Startup crash diagnostics

For an unconfirmed startup crash, build an instrumented APK with:

```
haxelib run lime build android -debug -Dandroid_diagnostics
```

This variant uses version 2.0.2/build 19 and the same application ID/signing key,
so it can update the experimental APK without deleting saves. Its Java-only
launcher offers **Iniciar jogo**, **Copiar diagnóstico**, and **Atualizar
diagnóstico**. The game runs in a separate `:game` process. After a crash, reopen
the app, refresh if needed, and copy the report into the support conversation.
The report is only stored locally and copied when requested; it is not uploaded.

Reports include device/ABI/page-size information, Android's recent process exit
reasons and available crash/ANR trace (Android 11+), and bounded startup milestones
and Java/Haxe errors. Timestamps distinguish old exits from the latest attempt.
Android may not retain a trace; a missing trace does not establish a cause. Memory
shown in the launcher is current availability, not memory at the time of a crash.
This instrumentation is not a gameplay fix or proof of device compatibility.
Normal builds omit the diagnostic launcher and startup logging.

Native crash traces use Android's tombstone protobuf format. The dependency-free
reader extracts the signal, abort/cause text and crashed thread's first 12 frames,
including relative PCs and build IDs for later symbolization. It limits input to
2 MiB and skips unknown fields. Java-only parser regression checks run with:

```
javac -d /tmp/mario-report-tests android-diagnostics/java/com/mariomadness/mobile/TombstoneSummary.java android-diagnostics/tests/com/mariomadness/mobile/TombstoneSummaryTest.java
java -cp /tmp/mario-report-tests com.mariomadness.mobile.TombstoneSummaryTest
```

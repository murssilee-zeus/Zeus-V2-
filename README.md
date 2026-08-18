# Zeus-V2 — Extreme Sub Edition

Ecualizador paramétrico Android (Compose + `DynamicsProcessing` nativo) con
**carácter de subgrave muy profundo 20–40 Hz**, compresor multibanda de
**3 cortes / 4 bandas con soft-knee**, limiter de protección anti-distorsión,
y botón **Guardar** con persistencia en almacenamiento local.

## Características

- **Motor de audio "Extreme Sub"**: 18 bandas; las 4 bandas de subgrave
  (20 / 25 / 31.5 / 40 Hz) vienen potenciadas por defecto y se pueden
  empujar todavía más con el slider **SUB 20–40 Hz** (+0..+12 dB, con
  refuerzo adicional en la PostEq). Preamp por defecto con headroom y
  limiter hard-knee final para **evitar distorsión**.
- **Modos de filtro completos**: PEAK, LO-SHELF, HI-SHELF, LOW-PASS,
  HIGH-PASS, NOTCH, BAND-PASS y BYPASS mapeados al motor nativo.
- **Compresor multibanda de 3 cortes / 4 bandas**: umbrales independientes
  para SUB / LOW-MID / HI-MID / HIGH, RATIO, **KNEE (dB)**, ATTACK, RELEASE,
  POST GAIN y 3 frecuencias de crossover configurables (20 Hz – 20 kHz).
- **Guardar**: botón **Guardar** en la barra superior → persiste TODA la
  configuración en `SharedPreferences` (JSON). Se carga automáticamente al abrir la app.
- **Target SDK 34** (Android 14+), `minSdk 28`, Compose BOM 2024.06,
  `compileSdk 34`, Kotlin 1.9.24, AGP 8.5.2, Gradle 8.7.

## Compilación del APK

### Opción A — GitHub Actions (recomendada)

1. Sube el contenido de esta carpeta a este repositorio.
2. Entra en la pestaña **Actions** → workflow **"Build Zeus-V2 APK"**
   (o haz un push a `main`; también tiene `workflow_dispatch`).
3. Al terminar, en el resumen del run, sección **Artifacts** →
   descarga **`Zeus-V2-debug`** → `app-debug.apk` listo para instalar.

### Opción B — Local (Android Studio)

1. Abrir esta carpeta (`File → Open`).
2. `Build → Build APK(s)`.
3. APK en `app/build/outputs/apk/debug/app-debug.apk`.

## Permisos

- `MODIFY_AUDIO_SETTINGS` — aplicar el procesamiento
- `RECORD_AUDIO` — Visualizer / análisis de espectro
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — servicio en background
- `POST_NOTIFICATIONS` — notificación (Android 13+)

# Pablito Fast

Aplicación Android de ventas, inventario, clientes y fiados, desarrollada con Kotlin, Jetpack Compose, Room y DataStore.

## Requisitos

- Android Studio con JDK 17
- Android SDK 34
- Dispositivo o emulador con Android 5.0 (API 21) o superior

## Compilar y verificar

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

Como el proyecto está dentro de OneDrive, Gradle envía automáticamente los archivos generados a `E:/AndroidStudioBuilds/PABLITO_FAST`. Esto evita errores `Unable to delete directory` en Android Studio. Se puede elegir otra carpeta del mismo disco con:

```powershell
.\gradlew.bat assembleDebug -PexternalBuildDir=E:/tmp/pablito-fast-build
```

## Datos

Room guarda productos, clientes, ventas, detalles y pagos de fiados. La migración de base de datos conserva instalaciones creadas con la versión 1. Las imágenes y respaldos se guardan en el almacenamiento interno de la aplicación.

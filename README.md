# SpaceSale

Punto de venta Android para pequeños negocios, desarrollado con Kotlin,
Jetpack Compose, Room y Supabase. Room mantiene la experiencia local y offline;
Supabase gestiona autenticación, negocios y el respaldo remoto en preparación
para una sincronización multidispositivo completa.

## Requisitos

- Android Studio compatible con JDK 17.
- Android SDK 34 o superior instalado.
- Dispositivo o emulador con Android 6.0 (API 23) o superior.

## Configuración local

Crea o completa `local.properties` en la raíz del proyecto. Además de la ruta
del SDK, agrega estos valores sin comillas:

```properties
SUPABASE_URL=https://TU_PROYECTO.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_TU_CLAVE
```

La clave publicable puede distribuirse en la aplicación porque la seguridad
real depende de Auth y RLS. Nunca agregues una clave `service_role`, una clave
`sb_secret_...`, credenciales PostgreSQL ni secretos OAuth al proyecto Android.

Si faltan las variables, SpaceSale muestra un mensaje de configuración en la
pantalla de acceso. El archivo `local.properties` está excluido de Git.

## Configurar Supabase

Aplica en orden las migraciones documentadas en [supabase/README.md](supabase/README.md).
La autenticación actual usa correo y un código OTP de ocho dígitos.

## Compilar y verificar

En PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

En Windows, los archivos generados se escriben de forma predeterminada en
`E:/AndroidStudioBuilds/SpaceSale` para evitar bloqueos de OneDrive. Puedes
seleccionar otra carpeta de la misma unidad:

```powershell
.\gradlew.bat :app:assembleDebug -PexternalBuildDir=E:/tmp/spacesale-build
```

En Linux y CI se usa la carpeta `build/` del repositorio. El APK de depuración
se llama `SpaceSale.apk`.

## Datos y sincronización

- Room almacena productos, clientes, ventas, detalles y pagos en el teléfono.
- Las migraciones Room existentes conservan los datos de instalaciones previas.
- Supabase almacena la cuenta, el negocio y recibe el respaldo remoto programado.
- La descarga multidispositivo y la resolución de conflictos aún están en
  desarrollo; no debe considerarse una sincronización bidireccional terminada.
- Las imágenes continúan locales hasta integrar el bucket privado de Storage.

## Calidad

GitHub Actions compila, ejecuta pruebas unitarias y lint en cada cambio dirigido
a `main`. Los reportes, credenciales, configuraciones del IDE y archivos locales
de herramientas están excluidos del repositorio.

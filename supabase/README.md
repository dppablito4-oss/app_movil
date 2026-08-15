# Supabase para SpaceSale

Room continúa siendo la fuente de datos local. El esquema de esta carpeta es el
destino remoto para autenticación, respaldo y sincronización.

## Aplicar la primera migración

1. Abre el proyecto correcto en Supabase.
2. Entra en **SQL Editor** y crea una consulta nueva.
3. Copia el contenido de
   `migrations/202608100001_initial_schema.sql`.
4. Ejecuta la consulta una sola vez.
5. Comprueba en **Table Editor** que las tablas muestran RLS habilitado.
6. Comprueba en **Storage** que `product-images` es privado.

No ejecutes la migración parcialmente ni cambies las tablas manualmente. Los
cambios posteriores deben agregarse como una nueva migración.

Si el esquema inicial ya estaba instalado, ejecuta también, en orden:

1. `migrations/202608110001_fix_business_creation_rls.sql`
2. `migrations/202608140001_sync_roles_and_idempotency.sql`
3. `migrations/202608150001_auth_branding.sql`
4. `migrations/202608150002_atomic_sales_and_stock.sql`
5. `migrations/202608150003_stable_sync_cursors.sql`

Esta corrección permite devolver el negocio recién creado sin relajar el
aislamiento entre negocios.

La segunda migración impide que el rol `viewer` escriba y prepara inserciones
idempotentes para que un reintento de red no duplique ventas.

La tercera migración agrega `logo_path` y el bucket privado
`business-assets` (máximo 2 MB por archivo). Ejecútala después de la migración
de roles porque reutiliza sus funciones de autorización.

La cuarta migración hace atómicas la confirmación y anulación de ventas, y
convierte los movimientos en la única vía para cambiar stock remoto. Debe
aplicarse antes de instalar una versión Android que invoque
`confirm_sale_bundle`, `cancel_sale_bundle` y `apply_stock_movement`.

La quinta migración agrega cursores inmutables asignados por el servidor para
paginar detalles, pagos y movimientos sin perder operaciones creadas offline.
Debe aplicarse antes de instalar la versión Android con Room v10.

## Verificación mínima de seguridad

- Registra dos usuarios de prueba distintos.
- Cada usuario debe crear su propio negocio.
- Inserta un producto con el primer usuario.
- Confirma que el segundo usuario no puede leerlo ni modificarlo.
- No agregues al cliente ninguna clave `service_role` o `sb_secret_...`.

## Configuración Android local

`local.properties` debe contener valores sin comillas:

```properties
SUPABASE_URL=https://TU_PROYECTO.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_TU_CLAVE
```

Este archivo no se versiona. En integración continua se pueden definir las dos
variables con variables de entorno del mismo nombre.

## Activar el acceso por código de correo

1. En **Authentication > Providers > Email**, deja habilitado Email.
2. Mantén desactivada la confirmación automática para que el usuario deba
   verificar el correo.
3. En **Authentication > Email Templates**, edita las plantillas de registro y
   magic link para mostrar `{{ .Token }}`. La app espera un código numérico de
   ocho dígitos, no un enlace web.
4. Guarda los cambios y prueba registro, reenvío, cierre de sesión y
   restauración de sesión antes de publicar.

## Corregir errores al enviar el correo (SMTP)

El mensaje `Supabase no pudo enviar el correo` no se resuelve cambiando la
clave pública de Android. Para direcciones externas configura un servidor en
**Project Settings > Authentication > SMTP Settings**, activa **Custom SMTP**
y completa host, puerto, usuario, contraseña, remitente y nombre. En
**Authentication > Logs** se ve el rechazo exacto.

Para una prueba rápida sin SMTP propio, agrega el correo de prueba como miembro
del proyecto de Supabase. El servicio integrado es limitado y no debe usarse
en producción. Nunca guardes la contraseña SMTP en la app.

La clave `sb_publishable_...` puede estar en el cliente Android. Nunca copies
una clave `service_role`, `sb_secret_...` ni credenciales directas de PostgreSQL
dentro de la aplicación.

## Activar Continuar con Google

La app oculta esta opción mientras no exista configuración, por lo que OTP y
contraseña siguen funcionando normalmente.

1. Conserva el `applicationId` actual hasta decidir la identidad definitiva de
   Play Store; cambiarlo crea una aplicación distinta y no conserva los datos de
   la instalación anterior.
2. Crea en Google Auth Platform un cliente OAuth web y clientes Android para el
   package vigente, con SHA-1 y SHA-256 de debug y release.
3. Activa Google en **Supabase > Authentication > Sign In / Providers** y guarda
   allí el Client ID y Client Secret web. El secret nunca va en Android.
4. Agrega solo el Client ID web público a `local.properties`:

```properties
GOOGLE_WEB_CLIENT_ID=000000000000-xxxxxxxxxxxxxxxx.apps.googleusercontent.com
```

5. Recompila y prueba usuario nuevo, usuario existente, cancelación, cierre de
   sesión y cambio de cuenta. La app intercambia el ID token mediante Supabase;
   no guarda el token en Room ni lo escribe en logs.

## Pruebas locales de base de datos

Con Supabase CLI y Docker disponibles, aplica las migraciones en una instancia
local y ejecuta `supabase test db`. El contrato pgTAP de `tests/database`
comprueba RLS, permisos de escritura por rol y políticas del bucket privado.
Estas pruebas nunca deben apuntar al proyecto de producción.

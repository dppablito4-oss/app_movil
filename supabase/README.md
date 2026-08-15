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

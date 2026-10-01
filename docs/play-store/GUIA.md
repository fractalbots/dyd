# Cómo publicar DYD Contable en Google Play

Pasos en orden. Lo que ya está hecho en el proyecto aparece marcado con ✅.

## 0. Lo que ya está listo en el proyecto
- ✅ Apunta a Android 16 (API 36), como exige Google desde el 31 de agosto de 2026.
- ✅ Firma de publicación configurable con `keystore.properties` (no se sube a git).
- ✅ Copias de seguridad de Android desactivadas para no sacar datos contables del teléfono.
- ✅ Solo pide el permiso de Internet. Las fotos se eligen con el selector del sistema, sin permisos.
- ✅ Páginas públicas `/privacidad` y `/eliminar-cuenta` en la web, enlazadas desde la app (Más).
- ✅ Al borrar un usuario se conservan los registros contables (migración `borrar_usuario`).
- ✅ Ícono, gráfico destacado y textos de la ficha (`FICHA.md`).

## 1. Publica la web (para tener la dirección de la política de privacidad)
1. Sube la carpeta `web/` a Netlify o Vercel (ya tiene `_redirects` y `vercel.json`).
2. Configura las variables `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`, `VITE_BUSINESS_NAME`, `VITE_WHATSAPP` y **`VITE_CONTACT_EMAIL`** (el correo donde recibirás pedidos de privacidad).
3. Abre `https://TU-WEB/privacidad` y `https://TU-WEB/eliminar-cuenta` y revisa que se vean con tu correo.
4. En `local.properties` del proyecto Android pon `WEB_URL=https://TU-WEB` para que la app muestre los enlaces en Más.

## 2. Crea tu cuenta de desarrollador
1. Entra a https://play.google.com/console y paga el registro único de **25 USD**.
2. Elige el tipo de cuenta:
   - **Organización** (recomendado si DYD tiene RUC de sociedad): necesitas un número D-U-N-S gratuito. Puedes publicar directo a producción.
   - **Personal**: más rápida de abrir, pero Google exige una **prueba cerrada con al menos 12 personas durante 14 días** antes de pasar a producción.
3. Completa la verificación de identidad (cédula o documentos de la empresa) y verifica un teléfono Android.

## 3. Genera el paquete firmado (.aab)
1. Abre el proyecto en Android Studio (versión reciente, con Android SDK 36 instalado). Deja que actualice Gradle si lo pide.
2. **Build > Generate Signed App Bundle or APK > Android App Bundle**.
3. **Create new…** para crear tu llave de subida: guarda el archivo `.jks` en un lugar seguro FUERA del proyecto y anota las contraseñas en tu gestor de contraseñas. Si la pierdes, Google la puede restablecer, pero tarda días.
4. Opcional: copia `keystore.properties.example` como `keystore.properties` y llénalo; así `./gradlew bundleRelease` firma solo.
5. Elige **release** y termina. El archivo queda en `app/release/app-release.aab`.
6. Para cada actualización sube `versionCode` en `app/build.gradle.kts` (2, 3, 4…).

> ⚠️ El nombre de paquete `com.dyd.contable` queda fijo para siempre al subir la primera versión.

## 4. Crea la app en Play Console
1. **Crear app**: nombre "DYD Contable", idioma Español (Latinoamérica), App, Gratis.
2. Acepta las declaraciones y sigue el panel **Configura tu app**:

| Sección | Qué responder |
|---|---|
| Política de privacidad | `https://TU-WEB/privacidad` |
| Acceso a la app | "Todas o algunas funciones están restringidas". Crea en tu app un usuario de prueba (rol Auditor o Vendedor, con datos de ejemplo) y escribe su correo y contraseña para el revisor de Google. |
| Anuncios | No contiene anuncios |
| Clasificación del contenido | Cuestionario IARC: categoría "Utilidad, productividad, comunicación u otro"; responde **No** a todo. Resultado esperado: Para todos. |
| Público objetivo | 18 años o más |
| App de noticias | No |
| Apps gubernamentales | No |
| Funciones financieras | "Mi app no ofrece ninguna de estas funciones" (no presta dinero ni procesa pagos; es contabilidad) |
| Salud | No |
| Seguridad de los datos | Ver tabla abajo |
| Eliminación de cuenta | `https://TU-WEB/eliminar-cuenta` |

### Seguridad de los datos (Data safety)
- ¿Recopila o comparte datos? **Sí**.
- ¿Están cifrados en tránsito? **Sí**.
- ¿Los usuarios pueden pedir que se borren? **Sí** (página de eliminar cuenta).
- Datos que **se recopilan** (no se comparten con terceros para publicidad; Supabase y el SRI actúan como proveedores de servicio):

| Tipo de dato | Recopilado | Obligatorio | Para qué |
|---|---|---|---|
| Información personal > Nombre | Sí | Sí | Funcionalidad de la app, administración de la cuenta |
| Información personal > Correo electrónico | Sí | Sí | Funcionalidad de la app, administración de la cuenta |
| Información personal > Otra información (cédula/RUC, dirección y teléfono de clientes) | Sí | Sí | Funcionalidad de la app |
| Información financiera > Otra información financiera (movimientos, facturas) | Sí | Sí | Funcionalidad de la app |
| Fotos y videos > Fotos (fotos de productos) | Sí | Opcional | Funcionalidad de la app |

- Ubicación, contactos del teléfono, mensajes, audio, salud, historial web, identificadores de dispositivo: **No**.

## 5. Sube la versión
1. Si tu cuenta es **personal**: **Pruebas > Prueba cerrada** > crea una lista con 12 correos de Gmail (tu equipo) > sube el `.aab` > envía a revisión. Pide a los 12 que acepten la invitación y dejen la app instalada 14 días. Después solicita el acceso a producción.
2. Si es **organización**: **Producción > Crear versión** > sube el `.aab` > pega las notas de la versión de `FICHA.md`.
3. Acepta **Play App Signing** (Google guarda la llave final; tú solo usas la de subida).
4. Países: Ecuador (y otros si quieres).
5. **Enviar a revisión**. La primera revisión suele tardar unos días.

## 6. Antes de enviar, prueba esto en el teléfono
- Iniciar sesión con tu usuario administrador y con el usuario de prueba del revisor.
- Crear un cliente con "Consultar SRI".
- Emitir una factura en ambiente de **pruebas** del SRI.
- Abrir Más > Política de privacidad y Más > Eliminar mi cuenta.

## Cómo atender un pedido de eliminación
1. Supabase > Authentication > Users > busca el correo > **Delete user**.
2. Su perfil se borra y sus registros contables quedan sin autor (se conservan 7 años por ley).
3. Responde al correo confirmando.

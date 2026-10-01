# DYD Contable

Sistema contable para DYD (pallets) con **app Android**, **panel web** y **catálogo público**,
todo sobre una sola base de datos en **Supabase**. Lo que registras en el teléfono aparece en la
web al instante, y al revés.

## Qué incluye

**Contabilidad**
- Inicio con saldo, ingresos, gastos y utilidad del mes, gráfico de 6 meses y gastos por categoría.
- Ventas, gastos y transferencias; cuentas por cobrar y por pagar con vencimientos.
- Estado de resultados mensual o anual, rentabilidad por producto y exportación a CSV.

**Inventario y costos de fabricación**
- Productos terminados y materiales (madera, clavos, insumos) con existencias, alerta de stock bajo
  y valor del inventario.
- Lista de materiales (BOM) por producto, más mano de obra y costos indirectos por unidad.
- Costo unitario, margen y precio sugerido; el costo de los materiales se actualiza con
  **promedio ponderado** en cada compra.
- **Explosión de materiales**: para N unidades muestra cuánto se necesita, cuánto hay y qué falta.
- Órdenes de producción que descuentan materiales y suman producto terminado (se bloquean si falta material).
- Kardex de entradas y salidas por artículo. Una venta o compra con artículo mueve el inventario sola.

**CRM**
- Clientes y proveedores con saldo pendiente, ventas y utilidad por cliente.
- Seguimientos (llamadas, visitas, cotizaciones) con fecha de próximo contacto; botones de llamar,
  WhatsApp y correo.

**Ecuador: facturación electrónica SRI e impuestos**
- **Consultar SRI:** al registrar un RUC, el botón «Consultar SRI» trae del catastro del SRI la razón social,
  la dirección de la matriz, la actividad económica, el estado del RUC, el tipo, el régimen y si lleva
  contabilidad. Funciona desde la app Android (el SRI solo acepta conexiones desde Ecuador); en la web
  depende de que el SRI permita la consulta desde el navegador.
- Moneda en dólares (USD). Clientes y proveedores con tipo de identificación del SRI (RUC, cédula,
  pasaporte, consumidor final, exterior); el RUC y la cédula se validan con el dígito verificador.
- Facturas electrónicas (versión 1.1.0) con clave de acceso de 49 dígitos, firma XAdES-BES con tu
  firma electrónica (.p12), envío al SRI y consulta de autorización (función `sri-factura`).
- IVA por producto (15 %, 5 %, 0 %, no objeto, exento). Emitir la factura registra la venta, las
  cuentas por cobrar y la salida de inventario; anularla lo revierte.
- RIDE imprimible (PDF desde el navegador) con código de barras de la clave de acceso.
- A consumidor final solo se factura hasta USD 50.
- Compras con IVA pagado, para el crédito tributario. El costo del inventario se guarda sin IVA.

**Intereses**
- **Mora de clientes:** interés simple sobre facturas vencidas a la tasa anual que definas
  (saldo × tasa × días / 365). Con un clic se registra como cuenta por cobrar en «Intereses ganados».
- **Ventas a crédito en cuotas:** sistema francés con interés de financiamiento anual; genera la
  tabla de cuotas y una cuenta por cobrar por cada cuota y su interés.
- **SRI:** resumen mensual de IVA (base para el formulario 104) con la fecha límite según el noveno
  dígito del RUC, y calculadora de interés de mora tributaria (tasa trimestral que publica el SRI,
  cada mes o fracción cuenta completo) más multa del 3 % mensual, sin pasar del 100 % del impuesto.

**Usuarios y seguridad**
- Inicio de sesión con correo y contraseña. El administrador crea los usuarios y les asigna un rol.
- La seguridad vive en la base de datos (Row Level Security), así que aplica igual en Android y web.

| Rol | Qué puede hacer |
|---|---|
| Administrador | Todo, incluidos usuarios y permisos. |
| Contador | Contabilidad completa, asigna el precio de venta de los productos, crea clientes con su RUC, factura, anula, impuestos e intereses, inventario y producción. |
| Vendedor | Emite facturas a clientes ya creados y ve las suyas; seguimientos; consulta inventario. No crea clientes. |
| Compras | Crea proveedores, registra compras (gastos con IVA) y entradas de material a bodega. Ve solo sus compras. |
| Bodega | Entradas, salidas y ajustes de stock. No ve dinero ni clientes. |
| Producción | Registra órdenes de producción y consulta la explosión de materiales. |
| Calidad | Aprueba o rechaza cada lote producido, con observaciones. |
| I+D | Crea y edita productos y materiales, lista de materiales, costos y fotos del catálogo. No cambia el precio de venta. |
| Auditor | Consulta todo (contabilidad, reportes, inventario, clientes y usuarios) sin modificar nada. |

Los permisos se cambian en un solo lugar: las funciones `perm_*` al final de `supabase/schema.sql`.

**Web**
- `/` catálogo público con fotos, medidas y botón “Cotizar por WhatsApp”.
- `/panel` panel administrativo: resumen, movimientos, inventario, producción, explosión, facturación SRI,
  clientes y proveedores, intereses e impuestos, empresa y usuarios.

---

## Puesta en marcha (una sola vez)

### 1. Crear la base de datos en Supabase
1. Crea una cuenta en [supabase.com](https://supabase.com) y un proyecto nuevo (región São Paulo o la más cercana).
2. Abre **SQL Editor > New query**, pega todo el contenido de `supabase/schema.sql` y pulsa **Run**.
   Crea las tablas, reglas de seguridad, el bucket de fotos `catalogo` y datos iniciales
   (cuentas, categorías, materiales y un “Pallet estándar” de ejemplo).

> ¿Ya habías ejecutado una versión anterior de `schema.sql`? No lo repitas: ejecuta
> `supabase/migraciones/actualizar.sql`. Agrega los roles nuevos (incluido Compras), el control de
> calidad, la facturación electrónica y los intereses sin tocar tus datos. Se puede ejecutar más de una vez.

### 2. Crear el primer usuario (será administrador)
1. **Authentication > Users > Add user > Create new user**, con tu correo y una contraseña.
   Marca *Auto Confirm User*.
2. El primer usuario queda como **admin** automáticamente. Los siguientes nacen como **vendedor**
   y el admin les cambia el rol desde la app o la web (Más > Usuarios), o los crea ya con su rol.
3. Recomendado: **Authentication > Sign In / Providers > Email**, desactiva *Allow new users to sign up*
   para que nadie se registre solo; los usuarios los crea el admin.

### 3. Permitir que el admin cree usuarios desde la app (opcional pero recomendado)
Se usa la función `supabase/functions/create-user`. Con la [CLI de Supabase](https://supabase.com/docs/guides/cli):
```bash
supabase login
supabase link --project-ref TU-REF-DE-PROYECTO
supabase functions deploy create-user
```
La función usa la clave secreta del servidor, que Supabase le entrega sola. No tienes que copiarla
en ningún lado. Sin esta función puedes seguir creando usuarios en el panel de Supabase (paso 2).

### 4. Facturación electrónica con el SRI
1. **Datos de la empresa:** en la app (Más > Empresa y SRI) o en la web (Empresa y SRI) llena RUC,
   razón social, dirección, establecimiento (`001`), punto de emisión (`001`), obligado a llevar
   contabilidad y régimen. Empieza en ambiente **Pruebas**.
2. **Firma electrónica:** la firma `.p12` y su clave **no se suben a la app ni se comparten por chat**.
   Se guardan como secretos de Supabase, desde tu computador:
   ```bash
   supabase secrets set SRI_P12_BASE64="$(base64 -w0 tu-firma.p12)" SRI_P12_PASSWORD='la-clave-de-tu-firma'
   ```
   (En macOS usa `base64 -i tu-firma.p12` en lugar de `base64 -w0`.)
3. **Publicar la función que firma y envía:**
   ```bash
   supabase functions deploy sri-factura
   ```
4. **Probar:** emite una factura en pruebas y pulsa «Enviar al SRI». Si sale *Autorizada*, todo está bien.
   Si sale *Devuelta* o *No autorizada*, la pantalla muestra el mensaje del SRI.
5. **Producción:** cuando las pruebas salgan bien y el SRI te haya habilitado la emisión electrónica,
   cambia el ambiente a **Producción** y pon el secuencial que sigue a tu última factura real.

Notas:
- La anulación de una factura autorizada también debe solicitarse en el portal SRI en línea; la app
  revierte la contabilidad y el inventario.
- En **Intereses e impuestos** registra la tasa trimestral de mora tributaria que publica el SRI y,
  en **Empresa y SRI**, la tasa anual de mora a clientes (sin pasar la máxima del Banco Central).
- Pendiente para una próxima versión: enviar el RIDE y el XML por correo al cliente, notas de
  crédito, retenciones y guías de remisión.

### 5. Conectar la app Android
1. En Supabase, **Project Settings > API**, copia la **Project URL** y la clave **anon public**.
2. Copia `local.properties.example` como `local.properties` y pega esos dos datos.
3. Abre la carpeta en Android Studio (Ladybug o más reciente) y pulsa **Run ▶**.

Para compilar el APK en GitHub: sube el proyecto a un repositorio, agrega los secretos
`SUPABASE_URL` y `SUPABASE_ANON_KEY` en *Settings > Secrets and variables > Actions*, y descarga el
APK desde la pestaña *Actions*.

### 6. Publicar la web
```bash
cd web
cp .env.example .env      # pon la URL, la clave anon, tu WhatsApp y el nombre del negocio
npm install
npm run dev               # prueba local en http://localhost:5173
npm run build             # genera la carpeta dist
```
Para publicarla gratis: en **Netlify** o **Vercel** importa el repositorio con directorio base `web`,
comando `npm run build`, carpeta `dist`, y agrega las mismas variables `VITE_...`.
Ya vienen `public/_redirects` (Netlify) y `vercel.json` para que funcionen las rutas del panel.

> **Importante:** la clave **anon public** puede ir en la app y en la web porque la seguridad la
> aplican las reglas de la base de datos. La clave **service_role** nunca se comparte, no va en la
> app, ni en la web, ni en un chat.

---

## Publicar en Google Play

Todo está en [`docs/play-store/`](docs/play-store/):
- `GUIA.md`: pasos para crear la cuenta de desarrollador, generar el `.aab` firmado y llenar Play Console (seguridad de los datos, acceso para el revisor, clasificación).
- `FICHA.md`: nombre, descripciones y notas de la versión.
- `graficos/`: ícono 512 × 512 y gráfico destacado 1024 × 500.

La web publica `/privacidad` y `/eliminar-cuenta` (configura `VITE_CONTACT_EMAIL`), y la app los enlaza desde Más cuando `WEB_URL` está en `local.properties`.

## Tecnología

- **Android**: Kotlin 2.1, Jetpack Compose + Material 3, MVVM con StateFlow, Navigation Compose,
  supabase-kt 3 (Auth, Postgrest, Functions, Storage). minSdk 26, targetSdk 35.
- **Web**: Vite + React 19 + TypeScript, supabase-js 2.
- **Base de datos**: Postgres en Supabase con RLS, vistas (`account_balances`, `item_stock`,
  `contact_stats`, `catalog`, `late_receivables`, `iva_monthly`) y funciones (`save_entry`, `explode_bom`,
  `produce`, `create_invoice`, `void_invoice`, `charge_late_interest`, `sri_late_charges`).
- **SRI**: Edge Function en Deno con node-forge para la firma XAdES-BES y los servicios SOAP offline
  del SRI (`celcer.sri.gob.ec` en pruebas, `cel.sri.gob.ec` en producción).

```
DYDContable/
├── app/                 App Android (com.dyd.contable)
│   └── src/main/java/com/dyd/contable/
│       ├── data/        Modelos, cliente Supabase y repositorio
│       ├── domain/      Cálculo de costos
│       ├── util/        Dinero, cantidades, fechas y CSV
│       └── ui/          Pantallas (inicio, movimientos, inventario, CRM, facturas, impuestos, reportes, usuarios)
├── supabase/
│   ├── schema.sql       Base de datos completa
│   ├── migraciones/     actualizar.sql para bases ya creadas
│   └── functions/       create-user y sri-factura (firma y envío al SRI)
└── web/                 Catálogo y panel web
```

### Reglas contables
- Los montos se guardan en centavos (enteros) para evitar errores de redondeo.
- Ingresos, gastos y utilidad se muestran **sin IVA**: el IVA cobrado o pagado es del SRI, no tuyo.
- Un movimiento **pendiente** no mueve el saldo hasta que se marca como pagado.
- El estado de resultados cuenta ventas y gastos por su fecha (base de causación).
- Las transferencias mueven dinero entre cuentas sin contarse como ingreso ni gasto.
- Costo unitario = materiales (según BOM y costo promedio) + mano de obra + indirectos.

// Facturación electrónica del SRI (Ecuador): XML de factura v1.1.0, firma XAdES-BES y servicios web.
// Se ejecuta solo en el servidor (Edge Function): la firma electrónica (.p12) nunca llega a la app.
// @deno-types="npm:@types/node-forge@1.3.11"
import forge from "npm:node-forge@1.3.1";

export interface Company {
  ruc: string; razon_social: string; nombre_comercial: string; dir_matriz: string; dir_establecimiento: string;
  estab: string; pto_emi: string; ambiente: number; obligado_contabilidad: boolean; contribuyente_especial: string;
  agente_retencion: string; regimen: string;
}
export interface InvoiceLine {
  code: string; description: string; quantity: number; unit_price: number; discount: number;
  iva_code: string; iva_rate: number; subtotal: number;
}
export interface Invoice {
  secuencial: number; fecha_emision: string; clave_acceso: string; buyer_id_type: string; buyer_id: string;
  buyer_name: string; buyer_address: string; buyer_email: string; subtotal: number; discount: number; tax: number;
  total: number; payment_method: string; term_days: number; lines: InvoiceLine[];
}

// ---------- XML ----------
const esc = (v: string) => v.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/\r/g, "");
// Siempre con etiqueta de cierre: es la forma canónica (C14N) que se firma.
const el = (tag: string, value: string | number) => `<${tag}>${esc(String(value))}</${tag}>`;
const money = (cents: number) => (cents / 100).toFixed(2);
const clean = (v: string, max = 300) => v.replace(/\s+/g, " ").trim().slice(0, max);

export function rateOf(code: string): number {
  return code === "4" ? 15 : code === "5" ? 5 : 0;
}

export function buildInvoiceXml(c: Company, inv: Invoice): string {
  const [y, m, d] = inv.fecha_emision.split("-");
  const rimpe = c.regimen === "RIMPE_EMPRENDEDOR" ? "CONTRIBUYENTE RÉGIMEN RIMPE"
    : c.regimen === "RIMPE_NEGOCIO_POPULAR" ? "CONTRIBUYENTE NEGOCIO POPULAR - RÉGIMEN RIMPE" : "";

  const groups = new Map<string, number>();
  for (const l of inv.lines) groups.set(l.iva_code, (groups.get(l.iva_code) ?? 0) + l.subtotal);
  const totalImpuestos = [...groups.entries()].map(([code, base]) =>
    "<totalImpuesto>" + el("codigo", "2") + el("codigoPorcentaje", code) + el("baseImponible", money(base)) +
    el("valor", money(Math.round(base * rateOf(code) / 100))) + "</totalImpuesto>").join("");

  const detalles = inv.lines.map((l) =>
    "<detalle>" +
    (l.code ? el("codigoPrincipal", clean(l.code, 25)) : "") +
    el("descripcion", clean(l.description)) +
    el("cantidad", Number(l.quantity).toFixed(6)) +
    el("precioUnitario", money(l.unit_price)) +
    el("descuento", money(l.discount)) +
    el("precioTotalSinImpuesto", money(l.subtotal)) +
    "<impuestos><impuesto>" + el("codigo", "2") + el("codigoPorcentaje", l.iva_code) + el("tarifa", rateOf(l.iva_code)) +
    el("baseImponible", money(l.subtotal)) + el("valor", money(Math.round(l.subtotal * rateOf(l.iva_code) / 100))) +
    "</impuesto></impuestos></detalle>").join("");

  const extra: [string, string][] = [];
  if (inv.buyer_email.trim()) extra.push(["Email", clean(inv.buyer_email)]);
  if (inv.buyer_address.trim()) extra.push(["Dirección", clean(inv.buyer_address)]);

  return '<factura id="comprobante" version="1.1.0">' +
    "<infoTributaria>" +
    el("ambiente", c.ambiente) + el("tipoEmision", "1") + el("razonSocial", clean(c.razon_social)) +
    (c.nombre_comercial.trim() ? el("nombreComercial", clean(c.nombre_comercial)) : "") +
    el("ruc", c.ruc) + el("claveAcceso", inv.clave_acceso) + el("codDoc", "01") + el("estab", c.estab) +
    el("ptoEmi", c.pto_emi) + el("secuencial", String(inv.secuencial).padStart(9, "0")) + el("dirMatriz", clean(c.dir_matriz)) +
    (c.agente_retencion.trim() ? el("agenteRetencion", c.agente_retencion.trim()) : "") +
    (rimpe ? el("contribuyenteRimpe", rimpe) : "") +
    "</infoTributaria>" +
    "<infoFactura>" +
    el("fechaEmision", `${d}/${m}/${y}`) +
    el("dirEstablecimiento", clean(c.dir_establecimiento || c.dir_matriz)) +
    (c.contribuyente_especial.trim() ? el("contribuyenteEspecial", c.contribuyente_especial.trim()) : "") +
    el("obligadoContabilidad", c.obligado_contabilidad ? "SI" : "NO") +
    el("tipoIdentificacionComprador", inv.buyer_id_type) +
    el("razonSocialComprador", clean(inv.buyer_name)) +
    el("identificacionComprador", inv.buyer_id) +
    (inv.buyer_address.trim() ? el("direccionComprador", clean(inv.buyer_address)) : "") +
    el("totalSinImpuestos", money(inv.subtotal)) +
    el("totalDescuento", money(inv.discount)) +
    "<totalConImpuestos>" + totalImpuestos + "</totalConImpuestos>" +
    el("propina", "0.00") +
    el("importeTotal", money(inv.total)) +
    el("moneda", "DOLAR") +
    "<pagos><pago>" + el("formaPago", inv.payment_method) + el("total", money(inv.total)) +
    (inv.term_days > 0 ? el("plazo", inv.term_days) + el("unidadTiempo", "dias") : "") +
    "</pago></pagos>" +
    "</infoFactura>" +
    "<detalles>" + detalles + "</detalles>" +
    (extra.length ? "<infoAdicional>" + extra.map(([k, v]) => `<campoAdicional nombre="${esc(k)}">${esc(v)}</campoAdicional>`).join("") + "</infoAdicional>" : "") +
    "</factura>";
}

// ---------- Firma XAdES-BES (la que exige el SRI: RSA-SHA1, C14N, firma envuelta) ----------
const NS_DS = "http://www.w3.org/2000/09/xmldsig#";
const NS_ETSI = "http://uri.etsi.org/01903/v1.3.2#";
const NS_DECL = `xmlns:ds="${NS_DS}" xmlns:etsi="${NS_ETSI}"`;

const sha1b64 = (s: string) => {
  const md = forge.md.sha1.create();
  md.update(s, "utf8");
  return forge.util.encode64(md.digest().bytes());
};
const bigToB64 = (n: { toString(r: number): string }) => {
  let hex = n.toString(16);
  if (hex.length % 2) hex = "0" + hex;
  return forge.util.encode64(forge.util.hexToBytes(hex));
};
const rand = () => Math.floor(Math.random() * 900000) + 100000;

export interface SigningKey { key: forge.pki.rsa.PrivateKey; cert: forge.pki.Certificate }

/** Lee el archivo .p12 del certificado de firma electrónica (Security Data, Banco Central, etc.). */
export function loadP12(p12Base64: string, password: string): SigningKey {
  const der = forge.util.decode64(p12Base64.replace(/\s+/g, ""));
  const p12 = forge.pkcs12.pkcs12FromAsn1(forge.asn1.fromDer(der), false, password);
  const keys = [
    ...(p12.getBags({ bagType: forge.pki.oids.pkcs8ShroudedKeyBag })[forge.pki.oids.pkcs8ShroudedKeyBag] ?? []),
    ...(p12.getBags({ bagType: forge.pki.oids.keyBag })[forge.pki.oids.keyBag] ?? []),
  ].map((b) => b.key as forge.pki.rsa.PrivateKey).filter(Boolean);
  const certs = (p12.getBags({ bagType: forge.pki.oids.certBag })[forge.pki.oids.certBag] ?? [])
    .map((b) => b.cert as forge.pki.Certificate).filter(Boolean);
  for (const key of keys) {
    // El .p12 suele traer también los certificados de la entidad: se usa el que corresponde a la llave.
    const cert = certs.find((c) => (c.publicKey as forge.pki.rsa.PublicKey).n.equals(key.n));
    if (cert) return { key, cert };
  }
  throw new Error("El archivo .p12 no contiene una llave con su certificado");
}

function issuerName(cert: forge.pki.Certificate): string {
  return cert.issuer.attributes.slice().reverse()
    .map((a) => `${a.shortName ?? a.type}=${String(a.value).replace(/([,+"\\<>;])/g, "\\$1")}`).join(",");
}

/** Firma el comprobante (sin declaración XML) y devuelve el XML completo listo para el SRI. */
export function signXml(comprobante: string, { key, cert }: SigningKey, now = new Date()): string {
  const certDer = forge.asn1.toDer(forge.pki.certificateToAsn1(cert)).getBytes();
  const certB64 = forge.util.encode64(certDer);
  const certDigest = (() => { const md = forge.md.sha1.create(); md.update(certDer); return forge.util.encode64(md.digest().bytes()); })();
  const serial = BigInt("0x" + cert.serialNumber).toString();

  const sigId = rand(), spId = rand(), refSpId = rand(), certId = rand(), refId = rand(), siId = rand(), svId = rand(), objId = rand();
  const signingTime = new Date(now.getTime() - 5 * 3600_000).toISOString().replace(/\.\d{3}Z$/, "-05:00");

  const signedProps =
    `<etsi:SignedProperties Id="Signature${sigId}-SignedProperties${spId}">` +
    "<etsi:SignedSignatureProperties>" +
    `<etsi:SigningTime>${signingTime}</etsi:SigningTime>` +
    "<etsi:SigningCertificate><etsi:Cert><etsi:CertDigest>" +
    `<ds:DigestMethod Algorithm="${NS_DS}sha1"></ds:DigestMethod><ds:DigestValue>${certDigest}</ds:DigestValue>` +
    "</etsi:CertDigest><etsi:IssuerSerial>" +
    `<ds:X509IssuerName>${esc(issuerName(cert))}</ds:X509IssuerName><ds:X509SerialNumber>${serial}</ds:X509SerialNumber>` +
    "</etsi:IssuerSerial></etsi:Cert></etsi:SigningCertificate>" +
    "</etsi:SignedSignatureProperties>" +
    "<etsi:SignedDataObjectProperties>" +
    `<etsi:DataObjectFormat ObjectReference="#Reference-ID-${refId}">` +
    "<etsi:Description>contenido comprobante</etsi:Description><etsi:MimeType>text/xml</etsi:MimeType>" +
    "</etsi:DataObjectFormat></etsi:SignedDataObjectProperties>" +
    "</etsi:SignedProperties>";

  const pub = cert.publicKey as forge.pki.rsa.PublicKey;
  const keyInfo =
    `<ds:KeyInfo Id="Certificate${certId}">` +
    `<ds:X509Data><ds:X509Certificate>${certB64}</ds:X509Certificate></ds:X509Data>` +
    `<ds:KeyValue><ds:RSAKeyValue><ds:Modulus>${bigToB64(pub.n)}</ds:Modulus><ds:Exponent>${bigToB64(pub.e)}</ds:Exponent></ds:RSAKeyValue></ds:KeyValue>` +
    "</ds:KeyInfo>";

  // C14N inclusiva: un subárbol lleva los espacios de nombres heredados de ds:Signature.
  const inScope = (xml: string) => xml.replace(/^<(ds|etsi):(\w+)/, (_m, p, n) => `<${p}:${n} ${NS_DECL}`);
  const digestMethod = `<ds:DigestMethod Algorithm="${NS_DS}sha1"></ds:DigestMethod>`;

  const signedInfo =
    `<ds:SignedInfo Id="Signature-SignedInfo${siId}">` +
    `<ds:CanonicalizationMethod Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"></ds:CanonicalizationMethod>` +
    `<ds:SignatureMethod Algorithm="${NS_DS}rsa-sha1"></ds:SignatureMethod>` +
    `<ds:Reference Id="SignedPropertiesID${refSpId}" Type="http://uri.etsi.org/01903#SignedProperties" URI="#Signature${sigId}-SignedProperties${spId}">` +
    digestMethod + `<ds:DigestValue>${sha1b64(inScope(signedProps))}</ds:DigestValue></ds:Reference>` +
    `<ds:Reference URI="#Certificate${certId}">` +
    digestMethod + `<ds:DigestValue>${sha1b64(inScope(keyInfo))}</ds:DigestValue></ds:Reference>` +
    `<ds:Reference Id="Reference-ID-${refId}" URI="#comprobante">` +
    `<ds:Transforms><ds:Transform Algorithm="${NS_DS}enveloped-signature"></ds:Transform></ds:Transforms>` +
    digestMethod + `<ds:DigestValue>${sha1b64(comprobante)}</ds:DigestValue></ds:Reference>` +
    "</ds:SignedInfo>";

  const md = forge.md.sha1.create();
  md.update(inScope(signedInfo), "utf8");
  const signatureValue = forge.util.encode64(key.sign(md));

  const signature =
    `<ds:Signature ${NS_DECL} Id="Signature${sigId}">` + signedInfo +
    `<ds:SignatureValue Id="SignatureValue${svId}">${signatureValue}</ds:SignatureValue>` + keyInfo +
    `<ds:Object Id="Signature${sigId}-Object${objId}"><etsi:QualifyingProperties Target="#Signature${sigId}">` +
    signedProps + "</etsi:QualifyingProperties></ds:Object></ds:Signature>";

  const i = comprobante.lastIndexOf("</");
  return '<?xml version="1.0" encoding="UTF-8"?>' + comprobante.slice(0, i) + signature + comprobante.slice(i);
}

// ---------- Servicios web del SRI ----------
export interface SriMessage { identificador: string; mensaje: string; informacionAdicional: string; tipo: string }

const hosts = { 1: "https://celcer.sri.gob.ec", 2: "https://cel.sri.gob.ec" } as const;
const tag = (xml: string, name: string) => xml.match(new RegExp(`<(?:\\w+:)?${name}>([\\s\\S]*?)</(?:\\w+:)?${name}>`))?.[1]?.trim() ?? "";
const unescapeXml = (v: string) => v.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, "&");

export function parseMessages(xml: string): SriMessage[] {
  const out: SriMessage[] = [];
  const re = /<identificador>([\s\S]*?)<\/identificador>\s*<mensaje>([\s\S]*?)<\/mensaje>\s*(?:<informacionAdicional>([\s\S]*?)<\/informacionAdicional>\s*)?<tipo>([\s\S]*?)<\/tipo>/g;
  for (const m of xml.matchAll(re)) {
    out.push({ identificador: m[1].trim(), mensaje: unescapeXml(m[2].trim()), informacionAdicional: unescapeXml((m[3] ?? "").trim()), tipo: m[4].trim() });
  }
  return out;
}

async function soap(url: string, body: string): Promise<string> {
  const res = await fetch(url, { method: "POST", headers: { "Content-Type": "text/xml; charset=utf-8", SOAPAction: "" }, body });
  const text = await res.text();
  if (!res.ok && !text.includes("Envelope")) throw new Error(`El SRI respondió ${res.status}`);
  return text;
}

/** Recepción: RECIBIDA o DEVUELTA con los mensajes del SRI. */
export async function sendToSri(ambiente: 1 | 2, signedXml: string) {
  const b64 = forge.util.encode64(forge.util.encodeUtf8(signedXml));
  const xml = await soap(`${hosts[ambiente]}/comprobantes-electronicos-ws/RecepcionComprobantesOffline`,
    '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ec="http://ec.gob.sri.ws.recepcion">' +
    `<soapenv:Header/><soapenv:Body><ec:validarComprobante><xml>${b64}</xml></ec:validarComprobante></soapenv:Body></soapenv:Envelope>`);
  return { estado: tag(xml, "estado"), mensajes: parseMessages(xml) };
}

/** Autorización: AUTORIZADO, NO AUTORIZADO o EN PROCESO. */
export async function checkAuthorization(ambiente: 1 | 2, claveAcceso: string) {
  const xml = await soap(`${hosts[ambiente]}/comprobantes-electronicos-ws/AutorizacionComprobantesOffline`,
    '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ec="http://ec.gob.sri.ws.autorizacion">' +
    `<soapenv:Header/><soapenv:Body><ec:autorizacionComprobante><claveAccesoComprobante>${claveAcceso}</claveAccesoComprobante>` +
    "</ec:autorizacionComprobante></soapenv:Body></soapenv:Envelope>");
  const blocks = [...xml.matchAll(/<autorizacion>([\s\S]*?)<\/autorizacion>/g)].map((m) => m[1]);
  const chosen = blocks.find((b) => tag(b, "estado") === "AUTORIZADO") ?? blocks[0] ?? "";
  return {
    estado: tag(chosen, "estado") || "EN PROCESO",
    numeroAutorizacion: tag(chosen, "numeroAutorizacion"),
    fechaAutorizacion: tag(chosen, "fechaAutorizacion"),
    mensajes: parseMessages(chosen),
  };
}

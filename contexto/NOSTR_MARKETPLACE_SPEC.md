# Especificación Técnica: NIPs de Nostr para Marketplace P2P

## Resumen

Este documento define los formatos de eventos Nostr utilizados en el P2P Exchange de Sparrow Wallet para publicar ofertas, comunicarse entre traders, y gestionar el ciclo de vida de transacciones.

## NIPs Implementados

| NIP | Kind | Uso |
|-----|------|-----|
| NIP-99 | 30402 | Ofertas de compra/venta (Classified Listings) |
| NIP-04 | 4 | Mensajes cifrados entre traders (legacy, funcional) |
| NIP-09 | 5 | Borrado de ofertas |
| NIP-94 | 1063 | Metadatos de imágenes (verificación) |

> **Nota**: NIP-04 está deprecado pero sigue siendo compatible. Migración futura a NIP-17 (kind 14/1059) recomendada.

---

## 1. Ofertas de Venta/Compra (NIP-99)

### Kind: 30402 (Classified Listing)

```json
{
  "kind": 30402,
  "created_at": 1704720000,
  "pubkey": "<vendedor-pubkey-hex>",
  "content": "## Vendo 0.1 BTC por 4000 EUR\n\n**Método de pago:** Bizum\n**Tiempo de entrega:** 1 hora máximo\n\nVendedor con historial verificado.",
  "tags": [
    ["d", "btc-sale-20240108-001"],
    ["title", "Vendo 0.1 BTC por 4000 EUR, pago por Bizum"],
    ["summary", "BTC disponible ahora, pago instantáneo Bizum"],
    ["published_at", "1704720000"],
    ["location", "Madrid, España"],
    ["price", "4000", "EUR"],
    ["amt", "10000000", "sats"],
    ["t", "bitcoin"],
    ["t", "p2p"],
    ["t", "bizum"],
    ["payment", "bizum"],
    ["type", "sell"],
    ["status", "active"],
    ["min_trade", "1000000", "sats"],
    ["max_trade", "10000000", "sats"],
    ["escrow_hours", "24"]
  ],
  "id": "<event-id>",
  "sig": "<firma>"
}
```

### Tags Requeridos

| Tag | Descripción | Ejemplo |
|-----|-------------|---------|
| `d` | Identificador único (UUID) | `btc-sale-20240108-001` |
| `title` | Título corto de la oferta | `Vendo 0.1 BTC...` |
| `price` | Precio y moneda fiat | `["price", "4000", "EUR"]` |
| `amt` | Cantidad en sats | `["amt", "10000000", "sats"]` |
| `type` | `buy` o `sell` | `sell` |
| `payment` | Método de pago | `bizum`, `bank_transfer` |

### Tags Opcionales

| Tag | Descripción |
|-----|-------------|
| `summary` | Resumen corto |
| `location` | Ubicación del vendedor |
| `image` | URL de imagen (verificación) |
| `min_trade` | Mínimo de sats aceptados |
| `max_trade` | Máximo de sats aceptados |
| `escrow_hours` | Horas máximas de escrow |
| `status` | `active`, `sold`, `paused` |
| `t` | Hashtags para búsqueda |

---

## 2. Mensajes Cifrados (NIP-04)

### Kind: 4 (Encrypted Direct Message)

```json
{
  "kind": 4,
  "created_at": 1704720600,
  "pubkey": "<comprador-pubkey-hex>",
  "content": "<mensaje-cifrado-AES-256-CBC>?iv=<iv-base64>",
  "tags": [
    ["p", "<vendedor-pubkey-hex>"],
    ["offer_id", "btc-sale-20240108-001"]
  ],
  "id": "<event-id>",
  "sig": "<firma>"
}
```

### Cifrado NIP-04

1. Generar shared secret con ECDH (secp256k1)
2. Derivar clave AES-256 del shared secret
3. Cifrar mensaje con AES-256-CBC
4. Formato: `base64(ciphertext)?iv=base64(iv)`

### Tags

| Tag | Descripción |
|-----|-------------|
| `p` | Pubkey del destinatario |
| `offer_id` | ID de la oferta relacionada (opcional) |
| `e` | Reply a mensaje anterior (opcional) |

---

## 3. Borrado de Ofertas (NIP-09)

### Kind: 5 (Event Deletion)

```json
{
  "kind": 5,
  "created_at": 1704730000,
  "pubkey": "<vendedor-pubkey-hex>",
  "content": "Oferta vendida, cerrada.",
  "tags": [
    ["a", "30402:<vendedor-pubkey-hex>:btc-sale-20240108-001"],
    ["k", "30402"]
  ],
  "id": "<event-id>",
  "sig": "<firma>"
}
```

### Tags

| Tag | Descripción |
|-----|-------------|
| `a` | Coordenadas del evento addressable: `kind:pubkey:d-tag` |
| `k` | Kind del evento a borrar |
| `e` | Event ID directo (alternativa a `a`) |

---

## 4. Metadatos de Imagen (NIP-94)

### Kind: 1063 (File Metadata)

```json
{
  "kind": 1063,
  "created_at": 1704720100,
  "pubkey": "<usuario-pubkey-hex>",
  "content": "Foto de verificación de pago",
  "tags": [
    ["url", "https://nostr.build/i/abc123.jpg"],
    ["m", "image/jpeg"],
    ["x", "<sha256-hash-del-archivo>"],
    ["size", "524288"],
    ["dim", "1920x1080"],
    ["alt", "Captura de transferencia Bizum"]
  ],
  "id": "<event-id>",
  "sig": "<firma>"
}
```

---

## 5. Migración a NIP-17 (Futuro)

NIP-04 está deprecado por problemas de seguridad (metadata leaks). La migración a NIP-17 implica:

### Kind: 14 (Chat Message) → 13 (Seal) → 1059 (Gift Wrap)

1. Crear mensaje kind 14 (sin firmar)
2. Cifrar con NIP-44 y envolver en kind 13 (Seal)
3. Cifrar nuevamente y envolver en kind 1059 (Gift Wrap)
4. Firmar kind 1059 con clave efímera

Beneficios:
- Oculta sender/receiver
- Timestamps aleatorios
- Sin correlación de metadata

---

## 6. Constantes en Código

```java
public class NostrEvent {
    // Ofertas P2P (NIP-99 Classified Listing)
    public static final int KIND_CLASSIFIED_LISTING = 30402;
    public static final int KIND_CLASSIFIED_DRAFT = 30403;
    
    // Mensajes cifrados (NIP-04)
    public static final int KIND_ENCRYPTED_DM = 4;
    
    // Borrado (NIP-09)
    public static final int KIND_DELETION = 5;
    
    // Metadatos archivo (NIP-94)
    public static final int KIND_FILE_METADATA = 1063;
    
    // Legacy (actual)
    public static final int KIND_P2P_TRADE_OFFER = 38400; // Migrar a 30402
}
```

---

## 7. Compatibilidad con Clientes Externos

Las ofertas publicadas con kind 30402 son visibles en:
- Amethyst (Android)
- Damus (iOS)
- Primal (Web)
- Snort (Web)
- Cualquier cliente con soporte NIP-99

Los mensajes kind 4 son compatibles con todos los clientes Nostr.

---

## 8. Flujo Completo de Transacción

```
1. Vendedor crea oferta
   └── Publica kind 30402 con tags de oferta

2. Comprador ve oferta
   └── Filtra kind 30402 por tags (type, payment, location)

3. Comprador contacta vendedor
   └── Envía kind 4 cifrado con tag offer_id

4. Negociación via DM
   └── Intercambio de kind 4 bidireccional

5. Acuerdo y pago
   └── Comprador paga fiat
   └── Comprador envía prueba (kind 1063 opcional)

6. Liberación BTC
   └── Vendedor envía BTC on-chain/Lightning

7. Cierre de oferta
   └── Vendedor publica kind 5 para borrar oferta
   └── O actualiza status="sold" en kind 30402
```

---

## 9. Validación de Eventos

### Oferta Válida (kind 30402)
- [ ] Tiene tag `d` único
- [ ] Tiene tag `title`
- [ ] Tiene tag `price` con moneda válida
- [ ] Tiene tag `amt` con cantidad en sats
- [ ] Tiene tag `type` (buy/sell)
- [ ] Tiene tag `payment` con método válido
- [ ] Firma válida
- [ ] Pubkey del creador conocida

### Mensaje Válido (kind 4)
- [ ] Tiene tag `p` con destinatario
- [ ] Content es string cifrado válido
- [ ] Formato: `base64?iv=base64`
- [ ] Firma válida

---

Documento creado: 2026-01-08
Versión: 1.0

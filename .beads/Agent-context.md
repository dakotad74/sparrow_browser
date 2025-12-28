# Agent Context - Sparrow P2P Exchange Development

**Última actualización:** 2025-12-28
**Estado del proyecto:** Sistema P2P Exchange funcional con persistencia completa

---

## 🎯 Objetivo del Proyecto

Implementar un sistema de intercambio P2P (Peer-to-Peer) en Sparrow Wallet utilizando Nostr como capa de comunicación. Los usuarios pueden crear ofertas de compra/venta de Bitcoin y comunicarse de forma segura mediante el protocolo Nostr.

---

## 📁 Estructura del Proyecto

```
SparrowDev/
├── .scripts/           # Scripts de desarrollo y testing (TODOS los .sh aquí)
│   ├── run-two-instances.sh    # Script principal para testing
│   ├── run-single-instance.sh
│   ├── force-rebuild.sh
│   ├── check-p2p-status.sh
│   └── ...
├── .beads/            # Documentación de contexto
│   └── Agent-context.md        # Este archivo
├── run-instances.sh   # Wrapper conveniente en la raíz
└── sparrow/          # Código fuente principal
    └── src/main/java/com/sparrowwallet/sparrow/
        ├── p2p/                    # Sistema P2P Exchange
        │   ├── NostrP2PService.java          # Servicio core Nostr P2P
        │   ├── NostrEventService.java        # Gestión eventos Nostr
        │   ├── P2PExchangeController.java    # UI principal
        │   ├── chat/                         # Sistema de chat
        │   │   ├── ChatService.java
        │   │   ├── ChatDialogController.java
        │   │   └── ChatsListController.java
        │   ├── identity/                     # Gestión identidades
        │   │   ├── NostrIdentityManager.java (✅ CON PERSISTENCIA)
        │   │   ├── NostrIdentity.java
        │   │   └── IdentityManagerController.java
        │   └── trade/                        # Ofertas de trading
        │       ├── TradeOfferManager.java (✅ CON PERSISTENCIA)
        │       ├── TradeOffer.java
        │       ├── CreateOfferController.java
        │       └── MyOffersController.java
        └── nostr/                  # Protocolo Nostr base
            ├── NostrRelayManager.java
            ├── NostrEvent.java
            └── NostrCrypto.java
```

---

## 🔧 Configuración de Desarrollo

### Requisitos Críticos (IMPORTANTE)

1. **SIEMPRE usar rebuild completo:**
   ```bash
   cd sparrow
   ./gradlew clean jpackage
   ```
   ⚠️ **NUNCA usar solo `compileJava`** - Los cambios no se reflejan en las instancias debido al empaquetado.

2. **Reducir sleeps a la mitad:**
   - Usar `sleep 8` en lugar de `sleep 15`
   - Usar `sleep 15` en lugar de `sleep 30`

### Scripts de Testing

**Script principal:** `.scripts/run-two-instances.sh`
- Lanza Alice (directorio `~/.sparrow`) y Bob (directorio `~/.sparrow-bob`)
- Ambas en testnet4
- Logs en `/tmp/sparrow-alice.log` y `/tmp/sparrow-bob.log`

**Uso rápido desde la raíz:**
```bash
./run-instances.sh
```

---

## 🏗️ Arquitectura del Sistema

### 1. Capa de Comunicación Nostr

**NostrEventService** - Singleton que gestiona la conexión a relays Nostr:
- Conecta a 3 relays: `wss://relay.damus.io`, `wss://nos.lol`, `wss://relay.snort.social`
- Gestiona subscripciones y publicación de eventos
- Thread-safe con manejo asíncrono

**NostrRelayManager** - Gestión de conexiones WebSocket:
- Reconexión automática
- Handler de mensajes configurable
- Chaining de handlers para múltiples tipos de eventos

### 2. Sistema de Identidades

**NostrIdentityManager** (✅ **CON PERSISTENCIA**)
- **Archivo:** `~/.sparrow/testnet4/nostr-identities.json`
- **Contenido:** Todas las identidades (efímeras y persistentes)
- **Incluye:** nsec (clave privada), npub, display name, timestamps
- **Cuándo guarda:** Al crear, importar, modificar o eliminar identidades

**Tipos de identidad:**
- **EPHEMERAL:** Uso único, auto-delete, máxima privacidad
- **PERSISTENT:** Long-term, construcción de reputación

**Constructor de reconstrucción:**
```java
public NostrIdentity(String id, String npub, String nsec, String hex,
                    String displayName, IdentityType type,
                    LocalDateTime createdAt, LocalDateTime lastUsedAt,
                    LocalDateTime expiresAt, boolean isActive)
```

### 3. Sistema de Ofertas

**TradeOfferManager** (✅ **CON PERSISTENCIA**)
- **Archivo:** `~/.sparrow/testnet4/my-trade-offers.json`
- **Contenido:** SOLO ofertas creadas por el usuario (no marketplace)
- **Incluye:** Todos los campos (tipo, cantidad, precio, ubicación, términos, estado)
- **Cuándo guarda:** Al añadir o eliminar ofertas propias

**Separación de ofertas:**
- `myOffers` - Ofertas propias (persistidas localmente)
- `marketplaceOffers` - Ofertas de otros (vienen de Nostr en tiempo real)

**Fix de duplicación implementado:**
```java
// En TradeOfferManager.addMarketplaceOffer()
if (activeIdentity != null && offer.getCreatorHex().equals(activeIdentity.getHex())) {
    log.debug("Skipping own offer from marketplace (already in myOffers)");
    return; // Evita que aparezca 2x (en myOffers y marketplaceOffers)
}
```

### 4. Protocolo de Eventos Nostr

**Kind 38400 - Trade Offers:**
```json
{
  "kind": 38400,
  "content": {
    "offer_id": "uuid",
    "type": "BUY|SELL",
    "amount_sats": 1000000,
    "currency": "USD",
    "price": 50000,
    "payment_method": "BANK_TRANSFER",
    "location": "Madrid",
    ...
  },
  "tags": [
    ["t", "p2p-trade"],
    ["type", "buy"],
    ["currency", "usd"],
    ["payment", "bank_transfer"]
  ]
}
```

**Kind 4 - Encrypted Direct Messages (NIP-04):**
- Cifrado AES-256-CBC + ECDH
- Solo visible entre comprador y vendedor

---

## 🐛 Bugs Resueltos

### 1. **Handler Timing Issue** ✅
**Problema:** Message handler configurado antes de conectar a relays
**Solución:** Mover `setMessageHandler()` de `start()` a `subscribeToOffers()`

### 2. **Offer Status Mismatch** ✅
**Problema:** Ofertas de Nostr con status DRAFT, UI filtra por ACTIVE
**Solución:** Establecer status ACTIVE al parsear eventos de Nostr

### 3. **Auto-refresh Missing** ✅
**Problema:** UI no se actualiza cuando llegan nuevas ofertas
**Solución:** Sistema de listeners observer pattern en TradeOfferManager

### 4. **Offer Duplication (3x → 2x → 1x)** ✅
**Problema inicial:** Misma oferta 3 veces (una por cada relay)
**Fix 1:** Event ID deduplication en NostrP2PService
**Problema residual:** Oferta 2x (en myOffers + marketplaceOffers)
**Fix 2:** Verificar creatorHex antes de añadir a marketplaceOffers

### 5. **Persistencia Missing** ✅
**Problema:** Identidades y ofertas se perdían al reiniciar
**Solución:** Sistema completo de persistencia JSON con GSON

---

## 🔑 Patrones de Código Importantes

### Persistencia con GSON

```java
// Guardar
private void saveToDisk() {
    File sparrowDir = Storage.getSparrowDir();
    File file = new File(sparrowDir, "data.json");
    Storage.createOwnerOnlyFile(file);

    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    try (Writer writer = new FileWriter(file)) {
        gson.toJson(dataObject, writer);
        writer.flush();
    }
}

// Cargar
private void loadFromDisk() {
    File file = new File(Storage.getSparrowDir(), "data.json");
    if(!file.exists()) return;

    try (Reader reader = new FileReader(file)) {
        Gson gson = new Gson();
        DataObject loaded = gson.fromJson(reader, DataObject.class);
        // Reconstruir objetos...
    }
}
```

### Observer Pattern para UI Updates

```java
// Manager
private final List<Runnable> listeners = new ArrayList<>();

public void addListener(Runnable listener) {
    listeners.add(listener);
}

private void notifyListeners() {
    for(Runnable listener : listeners) {
        listener.run();
    }
}

// Controller
offerManager.addListener(this::refreshOffers);
```

### Deduplicación de Eventos Nostr

```java
private final Set<String> processedEventIds =
    new ConcurrentHashMap<String, Boolean>().newKeySet();

private void handleEvent(NostrEvent event) {
    if(processedEventIds.contains(event.getId())) {
        return; // Ya procesado
    }
    processedEventIds.add(event.getId());
    // Procesar evento...
}
```

---

## 🧪 Testing

### Workflow de Testing

1. **Rebuild completo:**
   ```bash
   cd sparrow
   ./gradlew clean jpackage
   ```

2. **Lanzar instancias:**
   ```bash
   ./run-instances.sh
   ```

3. **Verificar conexión Nostr:**
   - Buscar en logs: "Relays connected (3)"
   - PIDs mostrados al final del script

4. **Probar flujo completo:**
   - Alice: Tools → P2P Exchange → Create Offer
   - Verificar: Oferta aparece UNA VEZ en Alice
   - Verificar: Oferta aparece en Bob's marketplace
   - Verificar: "Manage My Offers" muestra la oferta

5. **Probar persistencia:**
   ```bash
   pkill -f Sparrow
   ./run-instances.sh
   ```
   - Abrir Tools → P2P Exchange
   - Verificar: Ofertas propias siguen ahí
   - Verificar: Misma identidad (mismo npub)

### Logs y Debugging

```bash
# Ver logs en tiempo real
tail -f /tmp/sparrow-alice.log
tail -f /tmp/sparrow-bob.log

# Buscar eventos específicos
grep "RECEIVED EVENT" /tmp/sparrow-alice.log
grep "ADDED OFFER" /tmp/sparrow-bob.log

# Ver archivos de persistencia
cat ~/.sparrow/testnet4/nostr-identities.json | jq
cat ~/.sparrow/testnet4/my-trade-offers.json | jq
```

---

## 📝 TODOs y Próximos Pasos

### Pendientes

1. **Chat encryption error** - NIP-04 falla al cifrar mensajes
   - Error: `NativeSecp256k1Util$AssertFailException`
   - Debug logging añadido, necesita investigación

2. **Re-publicación de ofertas** - Al reiniciar, ofertas deberían republicarse a Nostr

3. **Expiración de ofertas** - Limpiar ofertas expiradas automáticamente

4. **Reputation system** - Implementar sistema de reviews y ratings

### Mejoras Futuras

- **Encryption de nsec en disco** - Actualmente en plaintext
- **Backup/restore** - Export/import de identidades
- **Multi-relay strategy** - Fallback si un relay falla
- **Offer search/filters** - Filtrar por ubicación, método de pago, etc.

---

## 🚨 Problemas Conocidos

### Rebuild Requirement
**Síntoma:** Cambios de código no se reflejan en las instancias
**Causa:** jpackage empaqueta todo, compileJava no actualiza el paquete
**Solución:** SIEMPRE usar `./gradlew clean jpackage`

### Chat Encryption
**Síntoma:** "Failed to encrypt content" al enviar mensajes
**Estado:** Debug logging añadido, causa raíz pendiente
**Workaround:** Ninguno disponible

---

## 🔗 Referencias Útiles

### Nostr Protocol
- **NIP-01:** Basic protocol flow - https://github.com/nostr-protocol/nips/blob/master/01.md
- **NIP-04:** Encrypted Direct Messages - https://github.com/nostr-protocol/nips/blob/master/04.md
- **Event Kinds:** Lista completa - https://github.com/nostr-protocol/nips

### Sparrow Internals
- **Storage:** `src/main/java/com/sparrowwallet/sparrow/io/Storage.java`
- **Config:** `src/main/java/com/sparrowwallet/sparrow/io/Config.java`
- **EventManager:** Sistema de eventos global de Sparrow

### Debugging Tools
```bash
# Ver estructura JSON de persistencia
jq . ~/.sparrow/testnet4/nostr-identities.json

# Monitor Nostr relay traffic (si tienes websocat)
websocat wss://relay.damus.io

# Check running instances
ps aux | grep Sparrow
```

---

## 💡 Decisiones de Diseño

### ¿Por qué solo persistir myOffers y no marketplaceOffers?
- Las ofertas del marketplace vienen de Nostr en tiempo real
- Son datos públicos que pueden cambiar (canceladas, completadas)
- Guardarlas localmente causaría inconsistencias
- Las ofertas propias sí se persisten para recuperarlas tras reinicio

### ¿Por qué usar GSON en lugar de Jackson u otros?
- Consistencia con el resto de Sparrow (usa GSON en Config)
- Simple y directo para este caso de uso
- Ya está en las dependencias

### ¿Por qué ConcurrentHashMap para processedEventIds?
- Thread-safety sin locks explícitos
- Nostr events llegan en threads diferentes
- newKeySet() da un Set thread-safe

---

## 🎓 Lecciones Aprendidas

1. **jpackage vs compileJava** - El empaquetado es crítico, nunca usar solo compile
2. **Timing matters** - Handlers deben configurarse DESPUÉS de conectar
3. **Identity matters** - Verificar creatorHex evita duplicación de ofertas propias
4. **Persistence is key** - Los usuarios esperan que sus datos persistan
5. **Observer pattern** - Desacopla UI de lógica de negocio eficientemente

---

## 📊 Estado Actual

✅ **Funcional:**
- Creación de identidades (efímeras y persistentes)
- Persistencia de identidades entre reinicios
- Creación de ofertas de compra/venta
- Publicación a Nostr (3 relays)
- Recepción de ofertas de otros usuarios
- Deduplicación correcta (1 oferta = 1 visualización)
- Persistencia de ofertas propias
- UI responsiva con auto-refresh
- "Manage My Offers" funcional

⚠️ **Con Issues:**
- Chat encryption (error NIP-04)

📋 **Pendiente:**
- Re-publicación de ofertas al reiniciar
- Sistema de reputación
- Filtros de búsqueda avanzados

---

**Fin del contexto. Ready para continuar tras reinicio! 🚀**

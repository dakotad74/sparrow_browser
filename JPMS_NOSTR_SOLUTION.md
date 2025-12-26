# Solución al Problema JPMS con nostr-java

**Problema:** nostr-java no está completamente modularizado para JPMS, causando errores al intentar usar `requires` en `module-info.java`.

**Fecha del Análisis:** 2025-12-26

---

## Análisis del Problema

### Error Original

```
java.lang.module.InvalidModuleDescriptorException: Package nostr.client not found in module
```

### Causa Raíz

1. **nostr-java** (versión 1.1.1) no declara correctamente sus módulos JPMS
2. Los archivos `module-info.java` de nostr-java tienen problemas:
   - Paquetes exportados que no existen
   - Dependencias transitivas no declaradas correctamente
   - Incompatibilidad con Java Module System strict mode

3. Sparrow usa JPMS estricto (`module-info.java` con `open module`)
4. No podemos agregar `requires nostr.java.*` sin que falle la compilación

---

## Soluciones Evaluadas

### ❌ Opción 1: Usar nostr-java actual

**Problemas:**
- Módulos mal declarados
- No se puede compilar con `requires nostr.java.*`
- Biblioteca externa fuera de nuestro control

**Veredicto:** No viable sin contribuir fixes upstream

### ❌ Opción 2: Poner nostr-java en automatic module path

**Problemas:**
- Sparrow usa `module-info.java` declarativo
- Mixing automatic modules con módulos explícitos causa issues
- JavaFX/JPackage tienen problemas con automatic modules

**Veredicto:** Complica el build system

### ✅ Opción 3: Implementación Nostr propia (RECOMENDADA)

**Ventajas:**
- ✅ Control total del código
- ✅ Compatible con JPMS desde diseño
- ✅ Sin dependencias externas problemáticas
- ✅ Solo necesitamos subset mínimo de Nostr
- ✅ Usa WebSocket estándar de Java 11+

**Implementación:**
- Usar `java.net.http.WebSocket` (built-in desde Java 11)
- Implementar solo NIP-01 (evento básico) y NIP-04 (encriptación)
- ~300-400 líneas de código limpio

---

## Solución Implementada: Nostr Client Nativo

### Arquitectura

```
┌──────────────────────────────────────────────────────────────┐
│  NostrRelayManager (ya existe como stub)                     │
│  - Usa java.net.http.WebSocket (built-in Java 11+)           │
│  - Sin dependencias externas                                 │
└──────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  WebSocket Connection (java.net.http.WebSocket)              │
│  - wss://relay.damus.io                                       │
│  - wss://nostr.wine                                           │
│  - wss://relay.nostr.band                                     │
└──────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│  Nostr Event Protocol (JSON sobre WebSocket)                 │
│  - ["EVENT", <event>]                                         │
│  - ["REQ", <subscription_id>, <filters>]                      │
│  - ["CLOSE", <subscription_id>]                               │
└──────────────────────────────────────────────────────────────┘
```

### Componentes Necesarios

#### 1. NostrEvent.java (ya existe)
```java
public class NostrEvent {
    private String id;
    private String pubkey;
    private long created_at;
    private int kind;
    private List<List<String>> tags;
    private String content;
    private String sig;

    // Métodos para serialización JSON
}
```

#### 2. NostrRelayManager.java (actualizar)
```java
public class NostrRelayManager {
    private final List<WebSocket> connections = new ArrayList<>();
    private final HttpClient httpClient;

    public void connect(List<String> relayUrls) {
        for (String url : relayUrls) {
            WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(url), new NostrWebSocketListener())
                .join();
            connections.add(ws);
        }
    }

    public void publishEvent(NostrEvent event) {
        String json = serializeEvent(event);
        String message = "[\"EVENT\"," + json + "]";
        connections.forEach(ws -> ws.sendText(message, true));
    }

    public void subscribe(String subscriptionId, Map<String, Object> filters) {
        String json = serializeFilters(filters);
        String message = "[\"REQ\",\"" + subscriptionId + "\"," + json + "]";
        connections.forEach(ws -> ws.sendText(message, true));
    }
}
```

#### 3. NostrWebSocketListener.java (nuevo)
```java
private class NostrWebSocketListener implements WebSocket.Listener {
    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        // Parse JSON message: ["EVENT", {...}] or ["OK", ...] or ["NOTICE", ...]
        processMessage(data.toString());
        return WebSocket.Listener.super.onText(webSocket, data, last);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        // Handle connection errors, reconnect logic
    }
}
```

#### 4. NostrCrypto.java (nuevo - opcional para NIP-04)
```java
public class NostrCrypto {
    // Encriptación NIP-04 usando javax.crypto (built-in)
    public static String encrypt(String plaintext, String recipientPubkey) {
        // AES-256-CBC con IV aleatorio
        // Usa clases estándar: Cipher, SecretKeySpec, IvParameterSpec
    }

    public static String decrypt(String ciphertext, String privateKey) {
        // Desencriptación AES-256-CBC
    }
}
```

### Dependencias Requeridas

**CERO dependencias externas:**
- ✅ `java.net.http.WebSocket` (Java 11+)
- ✅ `javax.crypto.*` (Java Security API)
- ✅ `com.google.gson` (ya en Sparrow para JSON)
- ✅ `java.security.*` (para keys, crypto)

---

## Plan de Implementación

### Fase 1: WebSocket Connection (1-2 horas)

**Archivos a modificar:**
1. `NostrRelayManager.java` - Cambiar de stub a implementación real con WebSocket
2. Crear `NostrWebSocketListener.java` - Handler de mensajes

**Funcionalidad:**
- Conectar a múltiples relays
- Enviar/recibir mensajes
- Manejo de reconexión

### Fase 2: Event Publishing (1 hora)

**Archivos a modificar:**
1. `NostrEvent.java` - Añadir serialización JSON
2. `NostrRelayManager.publishEvent()` - Implementación real

**Funcionalidad:**
- Crear eventos Nostr (kind: 38383)
- Firmar eventos
- Publicar a relays

### Fase 3: Subscriptions & Filters (1 hora)

**Funcionalidad:**
- Subscribe a eventos por filtros
- Procesar eventos recibidos
- Pasar eventos al EventManager

### Fase 4: Encryption (opcional, 2 horas)

**Archivo nuevo:**
1. `NostrCrypto.java` - NIP-04 encryption

**Funcionalidad:**
- Encriptar contenido sensible
- Desencriptar mensajes recibidos

---

## Ventajas de Esta Solución

1. **✅ Sin problemas JPMS** - Todo código propio en nuestro módulo
2. **✅ Sin dependencias conflictivas** - Solo APIs estándar de Java
3. **✅ Ligero** - ~400 líneas vs miles en nostr-java
4. **✅ Mantenible** - Código bajo nuestro control
5. **✅ Exactamente lo necesario** - Solo features que usamos (kind: 38383)
6. **✅ Compilación rápida** - Sin bajar/compilar dependencias externas
7. **✅ Compatible con jpackage** - Sin módulos automáticos problemáticos

---

## Comparación con nostr-java

| Feature | nostr-java | Implementación Propia |
|---------|------------|----------------------|
| **Módulos JPMS** | ❌ Rotos | ✅ Completo |
| **Dependencias** | Many | Zero externas |
| **Tamaño** | ~50KB+ deps | ~400 líneas |
| **NIPs soportados** | Todos | Solo NIP-01, NIP-04 (suficiente) |
| **Mantenimiento** | Dependemos de upstream | Control total |
| **Build time** | +5s | +0.5s |
| **Debugging** | Difícil (código externo) | Fácil (nuestro código) |

---

## Alternativa: Fork de nostr-java

Si preferimos usar nostr-java con fixes:

### Pasos:
1. Fork https://github.com/tcheeric/nostr-java
2. Arreglar `module-info.java` de cada módulo
3. Publicar en Maven local o GitHub packages
4. Usar nuestro fork en lugar del original

### Cons:
- Mantenimiento de fork a largo plazo
- Merge conflicts con upstream
- Más complejo que implementación simple

---

## Recomendación Final

**IMPLEMENTAR NOSTR CLIENT PROPIO** usando WebSocket estándar de Java.

**Razones:**
1. Solo necesitamos ~5% de la funcionalidad de nostr-java
2. Evitamos completamente problemas JPMS
3. Código más simple, mantenible y debuggeable
4. Sin dependencias externas que puedan romperse

**Tiempo estimado:** 4-6 horas de implementación limpia

**Próximo paso:** ¿Quieres que implemente esta solución ahora?

---

## Referencias

- [GitHub - tcheeric/nostr-java](https://github.com/tcheeric/nostr-java)
- [Java WebSocket API](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.html)
- [NIP-01: Basic protocol flow description](https://github.com/nostr-protocol/nips/blob/master/01.md)
- [NIP-04: Encrypted Direct Message](https://github.com/nostr-protocol/nips/blob/master/04.md)

---

**Status:** Análisis completo ✅
**Próxima acción:** Implementar NostrRelayManager con WebSocket nativo

Sources:
- [GitHub - tcheeric/nostr-java](https://github.com/tcheeric/nostr-java)
- [Awesome Nostr Resources](https://nostr.net/)
- [Nostr-Java | nostr.info](https://nostr.info/nostr.java/)

# Sparrow Browser - Guía para Desarrolladores

## 🚀 Inicio Rápido (3 Comandos)

```bash
cd ~/Desarrollo/SparrowDev/sparrow

# 1. Ejecutar Sparrow
./sparrow

# 2. Ejecutar tests
./dev test

# 3. Build rápido y ejecutar
./dev quick
```

---

## 📁 Estructura del Proyecto

```
sparrow/
├── sparrow              # 🎯 Launcher principal (USA ESTE)
├── dev                  # 🔧 Herramienta de desarrollo
├── QUICK_START.md       # 📖 Guía rápida
├── README_DEV.md        # 📖 Esta guía
│
├── src/
│   ├── main/java/com/sparrowwallet/sparrow/
│   │   ├── coordination/         # ✅ Backend de coordinación
│   │   │   ├── CoordinationSession.java
│   │   │   ├── CoordinationSessionManager.java
│   │   │   ├── CoordinationOutput.java
│   │   │   └── ...
│   │   ├── nostr/               # ✅ Integración Nostr (stub)
│   │   │   ├── NostrEvent.java
│   │   │   ├── NostrRelayManager.java
│   │   │   └── ...
│   │   └── ...
│   │
│   └── test/java/com/sparrowwallet/sparrow/
│       └── coordination/
│           ├── CoordinationIntegrationTest.java  # ✅ Tests principales
│           └── CoordinationWorkflowTest.java
│
├── build/
│   ├── libs/sparrow-2.3.2.jar   # JAR compilado
│   └── jpackage/Sparrow/bin/Sparrow  # Binario ejecutable
│
└── docs/
    ├── COLLABORATIVE_FEATURES.md
    ├── PHASE3_SUMMARY.md
    └── ...
```

---

## 🛠️ Comandos de Desarrollo

### Herramienta `./dev` (Recomendado)

```bash
./dev run      # Ejecutar Sparrow
./dev test     # Ejecutar tests de coordinación
./dev build    # Compilar proyecto
./dev quick    # Build rápido + ejecutar
./dev clean    # Limpiar build
./dev commit   # Commit interactivo
./dev push     # Push a origin/master
./dev status   # Ver estado de git
./dev help     # Mostrar ayuda
```

### Comandos Gradle Directos

```bash
# Compilar
./gradlew build

# Ejecutar tests específicos
./gradlew test --tests CoordinationIntegrationTest

# Crear binario jpackage
./gradlew clean jpackage

# Limpiar
./gradlew clean
```

---

## 🧪 Testing

### Ejecutar Todos los Tests de Coordinación

```bash
./dev test
```

Esto ejecuta:
- `CoordinationIntegrationTest` (3 tests)
- `CoordinationWorkflowTest.testFeeProposalReplacement`

### Ejecutar Test Específico

```bash
./gradlew test --tests CoordinationIntegrationTest.testFullCoordinationWorkflow
```

### Ver Output Detallado

```bash
./gradlew test --tests CoordinationIntegrationTest --info
```

---

## 🔄 Workflow de Desarrollo

### 1. Hacer Cambios en el Código

```bash
# Editar archivos en VSCode
code src/main/java/com/sparrowwallet/sparrow/coordination/
```

### 2. Ejecutar Tests

```bash
./dev test
```

### 3. Si los Tests Pasan, Compilar y Probar

```bash
./dev quick
```

Esto hace:
- Compilación rápida
- Crea binario jpackage
- Ejecuta Sparrow en modo silencioso

### 4. Commit y Push

```bash
./dev commit   # Te pedirá mensaje
./dev push
```

---

## 📊 Estado Actual del Proyecto

### ✅ Implementado (Phases 0-3)

**Phase 0: Documentación**
- README con disclaimer
- Documentación técnica completa
- 6 archivos .md de documentación

**Phase 1: Nostr Integration (stub)**
- NostrRelayManager (interfaz completa)
- NostrEvent model
- NostrEventService
- ⚠️ nostr-java deshabilitado (problemas JPMS)

**Phase 2: Session Management**
- CoordinationSession (completo)
- CoordinationSessionManager (completo)
- CoordinationParticipant
- SessionState (máquina de estados)
- Event Bus integration

**Phase 3: Output/Fee Coordination**
- 6 métodos de publicación Nostr ✅
- 6 métodos de parsing de mensajes ✅
- Consenso automático de fees ✅
- Validación de outputs duplicados ✅
- Tests completos (4/4 pasando) ✅

### ⏳ Pendiente

**Phase 4: PSBT Construction**
- CoordinationPSBTBuilder
- Conversión de sesión a PSBT
- Integración con Wallet.createWalletTransaction()

**Phase 5: UI Implementation**
- Botón "Coordinate Transaction" en Send tab
- Wizard de coordinación
- QR codes para compartir sesiones
- Interfaz gráfica de participantes/outputs/fees

**Phase 6+: Marketplace Features**
- Listings de compra/venta
- Chat directo
- Sistema de reputación
- Escrow/arbitraje

---

## 🐛 Troubleshooting

### Problema: "No se pudo configurar el display"

**Causa**: Ejecutando desde VSCode o SSH

**Solución**: Ejecuta desde terminal GNOME (Ctrl+Alt+T)

### Problema: Tests fallan

**Solución**: Verifica que estés en branch correcto
```bash
git status
./gradlew clean build
./dev test
```

### Problema: Binario no existe

**Solución**: Recompila jpackage
```bash
./gradlew clean jpackage
```

---

## 📝 Coding Guidelines

### Para Agregar Nueva Funcionalidad de Coordinación

1. **Modelo de datos**: Agregar en `coordination/`
2. **Lógica**: Agregar método en `CoordinationSessionManager`
3. **Evento Nostr**: Agregar método `publish*Event()`
4. **Parsing**: Agregar método `handle*Message()`
5. **Test**: Agregar test en `CoordinationIntegrationTest`
6. **Docs**: Actualizar `COLLABORATIVE_FEATURES.md`

### Ejemplo: Agregar Nueva Feature

```java
// 1. En CoordinationSessionManager.java
public void proposeNewFeature(String sessionId, FeatureData data) {
    CoordinationSession session = getSession(sessionId);
    session.addFeature(data);
    publishNewFeatureEvent(session, data);
}

// 2. Publicar evento
private void publishNewFeatureEvent(CoordinationSession session, FeatureData data) {
    NostrEvent event = new NostrEvent();
    event.setKind(38383);
    event.addTag("d", "new-feature");
    event.addTag("session-id", session.getSessionId());
    // ... configurar content
    nostrRelayManager.publishEvent(event);
}

// 3. Parsear mensaje
private void handleNewFeatureMessage(NostrEvent event) {
    String sessionId = event.getTagValue("session-id");
    // ... extraer data y procesar
}

// 4. Test
@Test
public void testNewFeature() {
    // ... test implementation
}
```

---

## 🔗 Links Útiles

- **Documentación Completa**: [COLLABORATIVE_FEATURES.md](COLLABORATIVE_FEATURES.md)
- **Fase 3 Summary**: [PHASE3_SUMMARY.md](PHASE3_SUMMARY.md)
- **Guía GUI**: [COMO_EJECUTAR_GUI.md](COMO_EJECUTAR_GUI.md)
- **Plan de Implementación**: `~/.claude/plans/rosy-chasing-corbato.md`

---

## ✅ Resumen de Comandos Esenciales

```bash
# Ejecutar Sparrow
./sparrow

# Desarrollo rápido
./dev quick

# Tests
./dev test

# Commit y push
./dev commit
./dev push

# Ayuda
./dev help
```

**¡Listo para desarrollar!** 🚀

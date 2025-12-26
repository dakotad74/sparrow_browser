# Instrucciones para Ejecutar Sparrow Browser en tu Máquina Local

## Opción Recomendada: Usar el repositorio Git

Ya que el proyecto está en GitHub, esta es la forma más sencilla:

### 1. Clona el repositorio
```bash
cd ~/Desarrollo  # O el directorio que prefieras
git clone https://github.com/dakotad74/sparrow_browser.git
cd sparrow_browser
```

### 2. Asegúrate de tener Java 21+
```bash
java -version
# Debe mostrar versión 21 o superior
```

Si no tienes Java 21:
- **Ubuntu/Debian**: `sudo apt install openjdk-21-jdk`
- **macOS**: `brew install openjdk@21`
- **Windows**: Descarga de https://adoptium.net/

### 3. Ejecuta Sparrow
```bash
# Opción A: Ejecutar directamente
./gradlew run

# Opción B: Crear el paquete jpackage y ejecutar
./gradlew clean jpackage
./build/jpackage/Sparrow/bin/Sparrow
```

### 4. Si tienes problemas de display en Linux
```bash
export GDK_BACKEND=x11
./gradlew run
```

---

## Opción Alternativa: Copiar desde el servidor

Si prefieres copiar el código directamente desde este servidor:

### 1. Desde tu máquina local, ejecuta:
```bash
# Necesitarás la IP del servidor y tus credenciales
scp -r r2d2@[IP_DEL_SERVIDOR]:/home/r2d2/Desarrollo/SparrowDev/sparrow ~/sparrow_browser

# Ejemplo si el servidor es 192.168.1.100:
# scp -r r2d2@192.168.1.100:/home/r2d2/Desarrollo/SparrowDev/sparrow ~/sparrow_browser
```

### 2. Entra al directorio y ejecuta
```bash
cd ~/sparrow_browser
./gradlew run
```

---

## Qué verás al ejecutar

Al iniciar Sparrow Browser verás la interfaz estándar de Sparrow Wallet con:

- **Funcionalidades de Sparrow originales**: Todo funciona normalmente
- **Características de coordinación (backend)**: Implementadas pero sin UI
  - La lógica de coordinación está lista
  - Los tests pasan y demuestran funcionamiento
  - La UI (botón "Coordinate Transaction") no está implementada aún (Fase 5)

---

## Para verificar que todo funciona

### Ejecutar tests de coordinación:
```bash
# Test unitario
./gradlew test --tests CoordinationWorkflowTest.testFeeProposalReplacement

# Tests de integración (flujo completo)
./gradlew test --tests CoordinationIntegrationTest

# Ver output detallado
./gradlew test --tests CoordinationIntegrationTest --info
```

Los tests demuestran:
- ✅ Creación de sesión de coordinación
- ✅ Join de 2 participantes
- ✅ Propuesta de outputs (50,000 + 30,000 sats)
- ✅ Propuesta de fees (10.0 y 12.5 sat/vB)
- ✅ Consenso automático (fee más alto: 12.5)
- ✅ Finalización de sesión
- ✅ MockNostrRelay simulando relay real

---

## Estado del Proyecto

### ✅ Implementado (Fases 0-3):
- Phase 0: Documentation
- Phase 1: Nostr Integration (stub)
- Phase 2: Session Management
- Phase 3: Output/Fee Coordination

**Código completo**: ~1,500 líneas de lógica de coordinación

### ⏳ Pendiente:
- Phase 4: PSBT Construction
- Phase 5: UI Implementation (wizard de coordinación)
- Resolver problema módulos nostr-java
- WebSocket real a relays Nostr
- Encryption NIP-44

---

## Troubleshooting

### Error: "No display detected"
**En Linux**, asegúrate de tener X11 corriendo:
```bash
export DISPLAY=:0
export GDK_BACKEND=x11
./gradlew run
```

### Error: "module not found"
Asegúrate de que las dependencias nostr-java están comentadas:
- `build.gradle` líneas 117-119
- `module-info.java` líneas 62-67

(Ya deberían estar comentadas en el código)

### Problemas de permisos
```bash
chmod +x gradlew
./gradlew run
```

### Build muy lento
```bash
# Primer build descarga dependencias (puede tardar)
./gradlew build --refresh-dependencies

# Builds subsecuentes serán mucho más rápidos
```

---

## Archivos de Documentación

Revisa estos archivos en el proyecto para más información:

1. **README.md** - Overview y disclaimer
2. **COLLABORATIVE_FEATURES.md** - Features de coordinación detalladas
3. **PHASE3_SUMMARY.md** - Resumen completo de implementación Fase 3
4. **RUNNING_GUI.md** - Guía para ejecutar con GUI
5. **DEMO_WITHOUT_GUI.md** - Demostración usando tests
6. **EJECUCION_RESUMEN.md** - Estado actual del proyecto

---

## Contacto y Contribuciones

Este es un **fork experimental** de Sparrow Wallet por Craig Raw.

⚠️ **NO USAR CON FONDOS REALES** - Solo testnet/signet

- Proyecto original: https://sparrowwallet.com
- Este fork: https://github.com/dakotad74/sparrow_browser

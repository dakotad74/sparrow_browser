# Instrucciones de Prueba: NonceRatchet en MuSig2

**Fecha:** 2026-01-08  
**Estado:** ✅ Instancias Alice y Bob ejecutándose

---

## Estado Actual

### Procesos Ejecutándose

```bash
Alice PID: 1808808 (Sparrow TESTNET4 - .sparrow-alice)
Bob   PID: 1808926 (Sparrow TESTNET4 - .sparrow-bob)
```

### Logs

```bash
tail -f /tmp/sparrow_alice_bob.log
```

---

## Prueba 1: Generación de Nonces con NonceRatchet

### Objetivo
Verificar que el NonceRatchet se inicializa y genera nonces correctamente cuando se usa MuSig2 Round 1.

### Pasos

#### En Alice:

1. **Abrir wallet MuSig2:**
   - La wallet `musig2_multiple_keystore` debería estar cargada automáticamente
   - Verificar que el tipo de política es "MuSig2 Multi Signature"

2. **Ir a la pestaña "Send"**

3. **Crear una transacción:**
   - Seleccionar un UTXO existente
   - Ingresar una dirección de destino (puede ser una dirección de prueba)
   - Establecer un monto pequeño

4. **Abrir "Sign/Finalize"**

5. **Iniciar MuSig2 Round 1:**
   - Buscar el botón o menú para "MuSig2 Signing"
   - Debería aparecer el diálogo "MuSig2 Round 1 - Nonce Exchange"

6. **Click en "Generate My Nonces":**
   - Si la wallet está encriptada, ingresará la contraseña
   - Esperar a que se generen los nonces

7. **Verificar en logs:**
   ```bash
   tail -100 /tmp/sparrow_alice_bob.log | grep -i "nonce\|ratchet"
   ```

### Mensajes Esperados en Logs

```
=== generateMyNonces called, wallet: musig2_multiple_keystore, encrypted: true/false ===
NonceRatchet initialized for wallet musig2_multiple_keystore (index=0)
MuSig2: Generating Round 1 nonce (ratchet=enabled, BIP-327)
Nonce generated from ratchet (index=1)
MuSig2: Generated nonces: R1=..., R2=...
```

### Verificar Archivos Creados

```bash
# Después de generar nonces, verificar:
ls -la /home/r2d2/Desarrollo/nuevo_sparrow/.sparrow-alice/testnet4/wallets/musig2_multiple_keystore/musig2/

# Debería mostrar:
# nonce_ratchet.state (44 bytes)
```

```bash
# Ver contenido en hexadecimal:
hexdump -C /home/r2d2/Desarrollo/nuevo_sparrow/.sparrow-alice/testnet4/wallets/musig2_multiple_keystore/musig2/nonce_ratchet.state | head -5
```

---

## Prueba 2: Recuperación de Crash

### Objetivo
Verificar que el NonceRatchet recupera correctamente después de un crash simulado.

### Pasos

1. **Anotar el índice actual del ratchet** (desde logs):
   ```
   NonceRatchet initialized for wallet musig2_multiple_keystore (index=X)
   ```

2. **Simular crash:**
   ```bash
   # Matar Alice durante generación de nonce (si es posible)
   kill -9 1808808
   ```

3. **Reiniciar Alice:**
   ```bash
   cd /home/r2d2/Desarrollo/nuevo_sparrow
   ./build/jpackage/Sparrow/bin/Sparrow --network TESTNET4 --dir .sparrow-alice &
   ```

4. **Verificar logs de recuperación:**
   ```bash
   tail -100 /tmp/sparrow_alice_bob.log | grep -i "recovery\|wal"
   ```

### Mensajes Esperados

Si hay WAL (crash durante escritura):
```
WAL file exists - performing crash recovery
=== CRASH RECOVERY MODE ===
Recovered state from WAL: index=X
Advanced state by 10 positions for safety: new index=X+10
=== CRASH RECOVERY COMPLETE ===
```

Si no hay WAL (crash después de escritura):
```
Loaded existing nonce ratchet state: index=X
```

---

## Prueba 3: Múltiples Generaciones de Nonces

### Objetivo
Verificar que cada generación de nonce avanza el índice del ratchet.

### Pasos

1. **Generar nonces 3 veces seguidas** (en Alice)
2. **Verificar en logs que el índice avanza:**
   ```
   Generación 1: index=1
   Generación 2: index=2
   Generación 3: index=3
   ```

3. **Verificar que cada nonce es diferente:**
   - Los valores R1 y R2 deben ser distintos en cada generación

---

## Prueba 4: Coordinación MuSig2 Completa (Alice + Bob)

### Objetivo
Verificar que el flujo completo de firma MuSig2 funciona con NonceRatchet en ambos lados.

### Pasos

1. **Alice: Generar nonces** (Round 1)
2. **Alice: Copiar nonces públicos** (R values)
3. **Bob: Abrir misma transacción** (o crear una compatible)
4. **Bob: Generar nonces** (Round 1)
5. **Bob: Copiar nonces públicos**
6. **Alice: Pegar nonces de Bob**
7. **Alice: Continuar a Round 2**
8. **Bob: Pegar nonces de Alice**
9. **Bob: Continuar a Round 2**
10. **Completar firma y broadcast**

### Verificar en Logs

Ambas instancias deberían mostrar:
```
NonceRatchet initialized for wallet ... (index=0)
Nonce generated from ratchet (index=1)
NonceRatchet closed and wiped from memory
```

---

## Verificación de Seguridad

### Archivos a Inspeccionar

```bash
# Estructura esperada:
~/.sparrow-alice/testnet4/wallets/musig2_multiple_keystore/musig2/
├── nonce_ratchet.state     # 44 bytes

~/.sparrow-bob/testnet4/wallets/musig2_multiple_keystore_bob/musig2/
├── nonce_ratchet.state     # 44 bytes
```

### Contenido del Estado

```bash
# Ver estructura del archivo (44 bytes):
# [32 bytes] Estado del ratchet (entropía)
# [8 bytes]  Índice (long, big-endian)
# [4 bytes]  CRC32 checksum

ls -lh /home/r2d2/Desarrollo/nuevo_sparrow/.sparrow-alice/testnet4/wallets/*/musig2/nonce_ratchet.state
```

### Verificar Permisos

```bash
# Los archivos deben tener permisos restrictivos (600):
stat -c "%a %n" /home/r2d2/Desarrollo/nuevo_sparrow/.sparrow-alice/testnet4/wallets/*/musig2/nonce_ratchet.state
```

---

## Detener las Instancias

```bash
# Detener ambas instancias:
kill 1808808 1808926

# O usar el script original y presionar Ctrl+C
```

---

## Comandos Útiles para Debugging

### Monitorear Logs en Tiempo Real

```bash
tail -f /tmp/sparrow_alice_bob.log | grep -i --color=auto "nonce\|ratchet\|musig2"
```

### Verificar Estado de los Directorios

```bash
watch -n 2 'find /home/r2d2/Desarrollo/nuevo_sparrow/.sparrow-*/testnet4/wallets -name "nonce_ratchet.*" -exec ls -lh {} \;'
```

### Extraer Solo Logs de NonceRatchet

```bash
grep -i "NonceRatchet\|ratchet" /tmp/sparrow_alice_bob.log | tail -20
```

### Ver Estructura Completa de Wallets

```bash
tree -L 5 /home/r2d2/Desarrollo/nuevo_sparrow/.sparrow-alice/testnet4/wallets/
```

---

## Casos de Prueba Adicionales

### Prueba 5: Fallback a BIP-327

Si el ratchet no se puede inicializar (por error de permisos, etc.):

```
MuSig2: No ratchet provided, using BIP-327 deterministic fallback (NOT RECOMMENDED for production)
```

### Prueba 6: Verificar Checksum

```bash
# Extraer los primeros 40 bytes y calcular CRC32
# Comparar con los últimos 4 bytes del archivo
```

### Prueba 7: WAL Persistente

```bash
# Durante generación de nonce, pausar la aplicación antes de que complete
# Verificar que existe nonce_ratchet.wal
ls -la /home/r2d2/Desarrollo/nuevo_sparrow/.sparrow-alice/testnet4/wallets/*/musig2/
```

---

## Resultados Esperados

✅ NonceRatchet se inicializa correctamente  
✅ Archivos `.state` se crean con 44 bytes  
✅ Índice avanza con cada generación  
✅ Nonces son únicos (diferentes R1, R2)  
✅ Recovery funciona si hay WAL  
✅ Cleanup se ejecuta al cerrar diálogo  
✅ Flujo completo MuSig2 funciona con ratchet  

---

**Las instancias están listas para pruebas! 🚀**

Logs en: `/tmp/sparrow_alice_bob.log`  
Alice: `PID 1808808`  
Bob: `PID 1808926`

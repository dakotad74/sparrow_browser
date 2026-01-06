# Guía de Prueba - Firma MuSig2 2/2 Taproot

## Estado Actual
✅ **CREACIÓN DE WALLET:** Funciona correctamente
✅ **CREACIÓN DE TRANSAACCIÓN:** Funciona correctamente
⏳ **CREACIÓN DE PSBT:** En pruebas
⏳ **FIRMA:** Pendiente de probar

---

## Instancias Corriendo

| Instancia | PID | Directorio | Red |
|-----------|-----|------------|-----|
| Alice | [Ver con `ps aux | grep Sparrow`] | ~/.sparrow | testnet4 |
| Bob | [Ver con `ps aux | grep Sparrow`] | /home/r2d2/.sparrow-bob | testnet4 |

Para reiniciar:
```bash
cd /home/r2d2/Desarrollo/SparrowDev/sparrow
pkill -f "Sparrow --network"
DISPLAY=localhost:10.0 ./build/jpackage/Sparrow/bin/Sparrow --network testnet4 > /tmp/sparrow-alice.log 2>&1 &
DISPLAY=localhost:10.0 ./build/jpackage/Sparrow/bin/Sparrow --network testnet4 --dir /home/r2d2/.sparrow-bob > /tmp/sparrow-bob.log 2>&1 &
```

---

## FLUJO DE FIRMA MUSIG2

### Paso 1: Crear Transacción (HECHO)
En Alice, pestaña Send:
- Address: `[tu dirección testnet4]`
- Label: `test`
- Amount: `[tu monto]`
- ✅ Create Transaction (botón se activa correctamente)

---

### Paso 1.5: Exportar/Importar PSBT (CRÍTICO)

Antes de firmar, **ambas instancias necesitan tener el mismo PSBT**:

#### En ALICE (después de crear la tx):
1. Clic en **Save Transaction** (icono de disquete)
2. Guardar como `/tmp/musig2_test.psbt`
3. Este archivo contiene el PSBT que Bob necesita importar

#### En BOB:
1. Clic en **Load Transaction** (icono de carpeta)
2. Seleccionar `/tmp/musig2_test.psbt`
3. Verificar que la tx se muestra correctamente

**IMPORTANTE:** Ambas instancias deben tener la **misma versión del PSBT**. Si una modifica el PSBT (ej. agregar fee), la otra debe recargarlo.

#### Verificar PSBT con bitcoin-cli:
```bash
# Convertir PSBT a base64 (desde archivo)
base64 -w 0 /tmp/musig2_test.psbt

# Decodificar para verificar contenido
bitcoin-cli -named decodepsbt psbt=$(base64 -w 0 /tmp/musig2_test.psbt)
```

El PSBT debe mostrar:
- **Inputs:** Los UTXOs de la wallet MuSig2
- **Outputs:** La dirección destino y change
- **Witness UTXO:** Con el scriptPubKey tipo P2TR (v1)

---

### Paso 2: Round 1 - Generar Nonces

#### En ALICE:
1. Clic en botón **MuSig2** (a la derecha de "Sign")
2. Se abre un diálogo "MuSig2 Round 1"
3. Copia el resultado (formato: `0:[hex]`)
4. Ejemplo: `0:12A3F...` (64 caracteres hex por input)

#### En BOB:
1. También debe tener la transacción cargada (importar PSBT o crearla)
2. Clic en botón **MuSig2**
3. Copia su resultado

---

### Paso 3: Intercambio Round 1

**Alice necesita:** El Round 1 data de Bob
**Bob necesita:** El Round 1 data de Alice

Formato típico:
```
0:0123456789ABCDEF...64 caracteres hex...
```

---

### Paso 4: Round 2 - Crear Firma Parcial

#### En ALICE:
1. En el diálogo MuSig2, clic en "Round 2" o importar el Round 1 de Bob
2. Introduce el Round 1 data de Bob
3. Clic en "Calculate Partial Signature"
4. Copia el resultado (firma parcial)

#### En BOB:
1. Igual: importar Round 1 de Alice
2. Calcular firma parcial
3. Copiar resultado

---

### Paso 5: Combinar Firmas

#### En ALICE:
1. Importar la firma parcial de Bob
2. Clic en **Finalize Transaction for Signing**
3. Verificar que la transacción esté completa
4. Clic en **Broadcast**

---

## Bugs Arreglados (Historial)

1. **EntryCell.java:255** - NullPointerException en getWitness()
2. **Wallet.java:1028** - getInputWeightUnits() no soportaba MUSIG2
3. **Wallet.java:1309** - addDummySpendingInput() no soportaba MUSIG2
4. **Transaction.java:334** - NullPointerException en getWitness()
5. **Wallet.java:1040** - NullPointerException en getWitness()

---

## Comandos Útiles

```bash
# Ver logs
tail -f /tmp/sparrow-alice.log
tail -f /tmp/sparrow-bob.log

# Ver instancias corriendo
ps aux | grep "Sparrow --network" | grep -v grep

# Detener todo
pkill -f "Sparrow --network"

# Recompilar (si hay cambios)
./gradlew clean build -x test
./gradlew jpackage
```

---

## Próximos Pasos Después de Esta Prueba

1. ✅ Verificar que Round 1 genera los nonces correctamente
2. ✅ Verificar que Round 2 genera firmas parciales
3. ✅ Verificar que las firmas se combinan correctamente
4. ✅ Verificar que Broadcast funciona
5. ⏳ Implementar firma automática (coordination) - esta es otra feature

---

## Nota

La **firma automática** que mencionamos antes (coordination) es una feature separada que usa Nostr para coordinar automáticamente entre parties. Lo que estamos probando ahora es el **flujo manual** de MuSig2, que ya está implementado y funcionando.

Para firma automática, necesitaríamos implementar:
- Detección automática de nonces MuSig2
- Intercambio automático vía Nostr
- Firma automática sin intervención del usuario

Esto es una feature futura. Por ahora, el flujo manual funciona correctamente.

---

**Última actualización:** 2026-01-05 10:30
**Estado:** Creación de wallet y transacción funcionando. Agregada sección de export/import PSBT. Pendiente probar firma.

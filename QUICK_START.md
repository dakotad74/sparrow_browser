# Sparrow Browser - Guía Rápida de Inicio

## 🚀 Ejecutar Sparrow (Súper Fácil)

Desde cualquier terminal:

```bash
cd ~/Desarrollo/SparrowDev/sparrow
./sparrow
```

**Eso es todo!** El script configura automáticamente el display y ejecuta Sparrow.

---

## 📝 Alias Recomendado (Opcional)

Para ejecutar Sparrow desde CUALQUIER directorio, agrega este alias a tu `~/.bashrc`:

```bash
echo 'alias sparrow-browser="cd ~/Desarrollo/SparrowDev/sparrow && ./sparrow"' >> ~/.bashrc
source ~/.bashrc
```

Luego solo ejecuta:
```bash
sparrow-browser
```

---

## 🔧 Comandos Disponibles

### Ejecución Normal
```bash
./sparrow
```

### Modo Silencioso (sin banner)
```bash
./sparrow --quiet
```

### Ejecutar Tests
```bash
./gradlew test --tests CoordinationIntegrationTest
```

### Recompilar
```bash
./gradlew clean build
```

### Recompilar Binario
```bash
./gradlew clean jpackage
```

---

## 🎯 Workflow de Desarrollo Recomendado

### 1. Hacer Cambios en el Código
```bash
# Edita archivos en src/main/java/...
code .
```

### 2. Ejecutar Tests
```bash
./gradlew test --tests CoordinationIntegrationTest
```

### 3. Si los tests pasan, recompilar y probar
```bash
./gradlew clean jpackage
./sparrow
```

### 4. Hacer Commit
```bash
git add .
git commit -m "Tu mensaje"
git push
```

---

## ⚡ Atajos de Teclado

Una vez que Sparrow esté abierto:

- **Ctrl+Q**: Salir
- **Ctrl+N**: Nueva Wallet
- **Ctrl+O**: Abrir Wallet
- **Ctrl+W**: Cerrar Tab

---

## 🐛 Troubleshooting

### Problema: "No se pudo configurar el display"

**Solución**: Ejecuta desde una terminal GNOME (Ctrl+Alt+T), no desde VSCode

### Problema: "Permission denied"

**Solución**: 
```bash
chmod +x sparrow
```

### Problema: Binario no existe

**Solución**:
```bash
./gradlew clean jpackage
```

---

## 📊 Estado del Proyecto

**Backend de Coordinación**: ✅ 100% Implementado
- Phase 0: Documentación ✅
- Phase 1: Nostr Integration (stub) ✅
- Phase 2: Session Management ✅
- Phase 3: Output/Fee Coordination ✅

**Tests**: ✅ Todos Pasando (4/4)

**UI de Coordinación**: ⏳ Pendiente (Phase 5)

---

## 📚 Documentación Completa

- [README.md](README.md) - Info general
- [COLLABORATIVE_FEATURES.md](COLLABORATIVE_FEATURES.md) - Features técnicas
- [COMO_EJECUTAR_GUI.md](COMO_EJECUTAR_GUI.md) - Guía completa de ejecución
- [SOLUCION_DISPLAY.md](SOLUCION_DISPLAY.md) - Troubleshooting display
- [EJECUCION_RESUMEN.md](EJECUCION_RESUMEN.md) - Estado del proyecto

---

## ✅ Resumen

**Para ejecutar**: `./sparrow`

**Para desarrollo**:
1. Editar código
2. `./gradlew test --tests CoordinationIntegrationTest`
3. `./gradlew clean jpackage`
4. `./sparrow`
5. Commit y push

¡Listo para desarrollar! 🚀

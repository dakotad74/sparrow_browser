# Sparrow Browser - Cheat Sheet

## 🎯 Comandos Más Usados

```bash
# EJECUTAR SPARROW
./sparrow

# TESTS
./dev test

# BUILD RÁPIDO + EJECUTAR
./dev quick

# VER AYUDA
./dev help
```

---

## 📋 Referencia Rápida `./dev`

| Comando | Alias | Descripción |
|---------|-------|-------------|
| `./dev run` | `r` | Ejecutar Sparrow |
| `./dev test` | `t` | Run tests |
| `./dev build` | `b` | Compilar |
| `./dev jpackage` | `j, pkg` | Crear binario |
| `./dev quick` | `q` | Build + run rápido |
| `./dev clean` | `c` | Limpiar build |
| `./dev commit` | - | Commit interactivo |
| `./dev push` | `p` | Push a origin |
| `./dev status` | `s` | Ver estado git |

---

## 🧪 Tests

```bash
# Todos los tests de coordinación
./dev test

# Test específico
./gradlew test --tests CoordinationIntegrationTest.testFullCoordinationWorkflow

# Ver output detallado
./gradlew test --tests CoordinationIntegrationTest --info
```

---

## 🔧 Build & Compile

```bash
# Build completo
./gradlew build

# Build rápido (solo Java)
./gradlew compileJava

# Crear binario jpackage
./gradlew clean jpackage

# Limpiar todo
./gradlew clean
```

---

## 📂 Archivos Importantes

```
sparrow/
├── sparrow                    # ⭐ Launcher principal
├── dev                        # ⭐ Herramienta dev
│
├── src/main/java/.../coordination/
│   ├── CoordinationSession.java          # Modelo de sesión
│   ├── CoordinationSessionManager.java   # Orquestador
│   ├── CoordinationOutput.java           # Modelo output
│   └── CoordinationFeeProposal.java      # Modelo fee
│
├── src/test/java/.../coordination/
│   ├── CoordinationIntegrationTest.java  # Tests principales
│   └── CoordinationWorkflowTest.java     # Tests unitarios
│
└── docs/
    ├── QUICK_START.md         # 📖 Inicio rápido
    ├── README_DEV.md          # 📖 Guía completa
    ├── CHEATSHEET.md          # 📖 Esta hoja
    └── COLLABORATIVE_FEATURES.md  # 📖 Features técnicas
```

---

## 🌳 Git Workflow

```bash
# Ver estado
./dev status

# Commit
./dev commit

# Push
./dev push

# O manualmente
git add .
git commit -m "mensaje"
git push origin master
```

---

## 🚀 Workflow de Desarrollo

```
1. Editar código
   ↓
2. ./dev test
   ↓
3. ./dev quick
   ↓
4. ./dev commit
   ↓
5. ./dev push
```

---

## 🐛 Solución Rápida de Problemas

| Problema | Solución |
|----------|----------|
| No se ejecuta GUI | `./test-display.sh` para verificar |
| Tests fallan | `./gradlew clean build` |
| Binario no existe | `./gradlew clean jpackage` |
| Display error | Ejecutar desde terminal GNOME (Ctrl+Alt+T) |

---

## 💡 Tips

- Usa `./dev q` en lugar de `./dev quick` (más rápido de escribir)
- El script `./sparrow` configura automáticamente el display
- Los tests se ejecutan en ~3 segundos
- `./dev commit` hace commit interactivo con prompt

---

## 📊 Estado del Proyecto

✅ **Implementado (100%)**:
- Phase 0: Documentación
- Phase 1: Nostr Integration (stub)
- Phase 2: Session Management
- Phase 3: Output/Fee Coordination

⏳ **Pendiente**:
- Phase 4: PSBT Construction
- Phase 5: UI Implementation

✅ **Tests**: 4/4 pasando

---

## 🔗 Documentación Completa

- `QUICK_START.md` - Inicio rápido
- `README_DEV.md` - Guía completa para desarrolladores
- `COLLABORATIVE_FEATURES.md` - Documentación técnica de features
- `PHASE3_SUMMARY.md` - Resumen de Phase 3

---

*Última actualización: 2025-12-26*

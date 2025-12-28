#!/bin/bash

# Script para ejecutar Sparrow Browser con GUI
# Ubicación: /home/r2d2/Desarrollo/SparrowDev/sparrow/run-sparrow-gui.sh

echo "=========================================="
echo "  Sparrow Browser - Experimental Fork"
echo "=========================================="
echo ""
echo "⚠️  ADVERTENCIA: Usar solo en TESTNET"
echo ""
echo "Características implementadas (Backend):"
echo "  ✅ Phase 0: Documentación"
echo "  ✅ Phase 1: Nostr Integration (stub)"
echo "  ✅ Phase 2: Session Management"
echo "  ✅ Phase 3: Output/Fee Coordination"
echo ""
echo "Limitaciones:"
echo "  ⚠️  UI no implementada (Fase 5)"
echo "  ⚠️  Nostr-java deshabilitado (JPMS issues)"
echo "  ⚠️  No verás botón 'Coordinate Transaction'"
echo ""
echo "Iniciando Sparrow Wallet..."
echo ""

# Cambiar al directorio del proyecto
cd "$(dirname "$0")"

# Ejecutar con gradlew
./gradlew run

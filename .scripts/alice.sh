#!/bin/bash
# Script para compilar y ejecutar Sparrow - Instancia Alice (Testnet4)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Directorio de datos para Alice
ALICE_DIR="$PROJECT_DIR/.sparrow-alice"

# Detectar Xwayland y configurar DISPLAY
XWAYLAND_PID=$(pgrep -u $USER Xwayland 2>/dev/null | head -1)
if [ -n "$XWAYLAND_PID" ]; then
    export DISPLAY=$(cat /proc/$XWAYLAND_PID/environ 2>/dev/null | tr '\0' '\n' | grep "^DISPLAY=" | cut -d= -f2)
    export XAUTHORITY=$(cat /proc/$XWAYLAND_PID/environ 2>/dev/null | tr '\0' '\n' | grep "^XAUTHORITY=" | cut -d= -f2)
    export XDG_RUNTIME_DIR=/run/user/$(id -u)
fi

echo "=========================================="
echo "  Sparrow Browser - Alice (Testnet4)"
echo "=========================================="
echo "Directorio de datos: $ALICE_DIR"
echo "Network: TESTNET4"
echo "Display: $DISPLAY"
echo ""
echo "Compilando (jpackage)..."
echo ""

cd "$PROJECT_DIR"
./gradlew clean jpackage -x test

if [ $? -ne 0 ]; then
    echo "ERROR: La compilación falló"
    exit 1
fi

echo ""
echo "Ejecutando Sparrow..."
echo ""

# Ejecutar binario nativo con parámetros
exec ./build/jpackage/Sparrow/bin/Sparrow --network TESTNET4 --dir "$ALICE_DIR"

#!/bin/bash
# Script para compilar y ejecutar Sparrow - Dos instancias: Alice y Bob (Testnet4)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Directorios de datos
ALICE_DIR="$PROJECT_DIR/.sparrow-alice"
BOB_DIR="$PROJECT_DIR/.sparrow-bob"

# Detectar Xwayland y configurar DISPLAY
XWAYLAND_PID=$(pgrep -u $USER Xwayland 2>/dev/null | head -1)
if [ -n "$XWAYLAND_PID" ]; then
    export DISPLAY=$(cat /proc/$XWAYLAND_PID/environ 2>/dev/null | tr '\0' '\n' | grep "^DISPLAY=" | cut -d= -f2)
    export XAUTHORITY=$(cat /proc/$XWAYLAND_PID/environ 2>/dev/null | tr '\0' '\n' | grep "^XAUTHORITY=" | cut -d= -f2)
    export XDG_RUNTIME_DIR=/run/user/$(id -u)
fi

echo "=========================================="
echo "  Sparrow Browser - Alice & Bob (Testnet4)"
echo "=========================================="
echo "Alice: $ALICE_DIR"
echo "Bob:   $BOB_DIR"
echo "Network: TESTNET4"
echo "Display: $DISPLAY"
echo ""
echo "Compilando (jpackage)..."
echo ""

cd "$PROJECT_DIR"

# Compilar
./gradlew clean jpackage -x test

if [ $? -ne 0 ]; then
    echo "ERROR: La compilación falló"
    exit 1
fi

echo ""
echo "Iniciando Alice en background..."
./build/jpackage/Sparrow/bin/Sparrow --network TESTNET4 --dir "$ALICE_DIR" &
ALICE_PID=$!

sleep 3

echo "Iniciando Bob..."
./build/jpackage/Sparrow/bin/Sparrow --network TESTNET4 --dir "$BOB_DIR" &
BOB_PID=$!

echo ""
echo "Instancias en ejecución:"
echo "  Alice PID: $ALICE_PID"
echo "  Bob   PID: $BOB_PID"
echo ""
echo "Presiona Ctrl+C para detener ambas instancias"

# Manejar Ctrl+C para detener ambos procesos
trap "kill $ALICE_PID $BOB_PID 2>/dev/null; exit" INT TERM

# Esperar a que ambos procesos terminen
wait $ALICE_PID $BOB_PID

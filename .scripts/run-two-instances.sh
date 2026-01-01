#!/bin/bash
#
# Launch two Sparrow instances in testnet for coordination testing
#
# Instance A (Alice): Uses default ~/.sparrow directory
# Instance B (Bob):   Uses /tmp/sparrow-instance2 directory
#

echo "=========================================="
echo "Sparrow Coordination Testing Launcher"
echo "=========================================="
echo ""

# Configurar variables de entorno desde Xwayland (igual que RUN-SPARROW.sh)
XWAYLAND_PID=$(pgrep -u $USER Xwayland | head -1)

if [ -z "$XWAYLAND_PID" ]; then
    echo "❌ No se encontró sesión gráfica"
    echo "Por favor ejecuta desde una terminal GNOME (Ctrl+Alt+T)"
    exit 1
fi

export DISPLAY=$(cat /proc/$XWAYLAND_PID/environ | tr '\0' '\n' | grep "^DISPLAY=" | cut -d= -f2)
export XAUTHORITY=$(cat /proc/$XWAYLAND_PID/environ | tr '\0' '\n' | grep "^XAUTHORITY=" | cut -d= -f2)
export XDG_RUNTIME_DIR=/run/user/$(id -u)

echo "✅ Variables gráficas configuradas"
echo "   DISPLAY: $DISPLAY"
echo "   XDG_RUNTIME_DIR: $XDG_RUNTIME_DIR"
echo ""

# Determinar directorio del proyecto (sparrow)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
# Si el script está en .scripts/, el proyecto está en el padre
if [[ "$SCRIPT_DIR" == *"/.scripts"* ]]; then
    PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
else
    PROJECT_DIR="$SCRIPT_DIR"
fi
cd "$PROJECT_DIR"

# Verificar que el binario exists (KEY DIFFERENCE from previous attempt)
BINARY="./build/jpackage/Sparrow/bin/Sparrow"

if [ ! -f "$BINARY" ]; then
    echo "❌ Binario no encontrado: $BINARY"
    echo "Compilando con jpackage (esto tomará ~90 segundos)..."
    echo ""
    ./gradlew clean jpackage
    echo ""
    if [ ! -f "$BINARY" ]; then
        echo "❌ ERROR: No se pudo compilar el binario"
        exit 1
    fi
    echo "✅ Binario compilado exitosamente"
    echo ""
fi

# Directorio fijo para Bob (persistente entre ejecuciones)
BOB_DIR="$HOME/.sparrow-bob"

echo "This script will launch 2 Sparrow instances:"
echo "  - Instance A (Alice): Default directory (~/.sparrow), testnet4"
echo "  - Instance B (Bob):   $BOB_DIR, testnet4"
echo ""

# Crear directorio para Bob si no existe (NO lo borramos para mantener config)
if [ ! -d "$BOB_DIR" ]; then
    echo "Creando directorio persistente para Bob: $BOB_DIR"
    mkdir -p "$BOB_DIR"
    echo "✅ Directorio creado para Bob"
else
    echo "ℹ️  Usando directorio existente de Bob (wallets y config preservados)"
fi

echo ""
echo "=========================================="
echo "Starting Instance A (Alice)..."
echo "=========================================="
echo "  Network: testnet4"
echo "  Directory: ~/.sparrow (default)"
echo ""

# Start Instance A in background - USING BINARY, NOT GRADLEW
$BINARY --network testnet4 > /tmp/sparrow-alice.log 2>&1 &
ALICE_PID=$!
echo "✅ Instance A (Alice) started with PID: $ALICE_PID"
echo "   Log: /tmp/sparrow-alice.log"

# Wait for first instance to initialize
echo ""
echo "Waiting 5 seconds for Instance A to initialize..."
sleep 5

echo ""
echo "=========================================="
echo "Starting Instance B (Bob)..."
echo "=========================================="
echo "  Network: testnet4"
echo "  Directory: $BOB_DIR (persistent)"
echo ""

# Start Instance B in background - USING BINARY, NOT GRADLEW
$BINARY --network testnet4 --dir "$BOB_DIR" > /tmp/sparrow-bob.log 2>&1 &
BOB_PID=$!
echo "✅ Instance B (Bob) started with PID: $BOB_PID"
echo "   Log: /tmp/sparrow-bob.log"

echo ""
echo "=========================================="
echo "Both instances are running!"
echo "=========================================="
echo ""
echo "Instance A (Alice) PID: $ALICE_PID"
echo "Instance B (Bob) PID:   $BOB_PID"
echo ""
echo "Logs:"
echo "  Alice: tail -f /tmp/sparrow-alice.log"
echo "  Bob:   tail -f /tmp/sparrow-bob.log"
echo ""
echo "To stop both instances:"
echo "  kill $ALICE_PID $BOB_PID"
echo ""
echo "Or to kill all Sparrow instances:"
echo "  pkill -f Sparrow"
echo ""
echo "Follow the testing guide in:"
echo "  ../TESTING_COORDINATION.md"
echo ""
echo "Press Ctrl+C to exit this script (instances will continue running)"
echo ""

# Wait for user to press Ctrl+C
trap "echo ''; echo 'Script terminated. Instances are still running.'; echo 'To stop them: kill $ALICE_PID $BOB_PID'; exit 0" INT

# Keep script running to show status
while true; do
    # Check if processes are still running
    if ! kill -0 $ALICE_PID 2>/dev/null; then
        echo "⚠️  Instance A (Alice) has stopped"
        break
    fi
    if ! kill -0 $BOB_PID 2>/dev/null; then
        echo "⚠️  Instance B (Bob) has stopped"
        break
    fi
    sleep 5
done

echo ""
echo "One or both instances have stopped."
echo "Check logs for errors:"
echo "  Alice: /tmp/sparrow-alice.log"
echo "  Bob:   /tmp/sparrow-bob.log"

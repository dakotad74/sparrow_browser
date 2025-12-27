#!/bin/bash
#
# Launch single Sparrow instance for P2P Exchange testing
#
# Uses default ~/.sparrow directory
#

echo "=========================================="
echo "Sparrow P2P Exchange Launcher"
echo "=========================================="
echo ""

# Configurar variables de entorno desde Xwayland
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

# Cambiar al directorio del script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Verificar que el binario existe
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

echo "=========================================="
echo "Starting Sparrow Wallet..."
echo "=========================================="
echo "  Network: testnet4"
echo "  Directory: ~/.sparrow (default)"
echo ""

# Matar instancias previas
pkill -f Sparrow 2>/dev/null
sleep 2

# Start Sparrow - USING BINARY, NOT GRADLEW
$BINARY --network testnet4 > /tmp/sparrow-p2p.log 2>&1 &
SPARROW_PID=$!

echo "✅ Sparrow started with PID: $SPARROW_PID"
echo "   Log: /tmp/sparrow-p2p.log"
echo ""
echo "=========================================="
echo "Sparrow is running!"
echo "=========================================="
echo ""
echo "To test P2P Exchange:"
echo "  1. Go to Tools → P2P Exchange (or press Ctrl+P)"
echo "  2. Click 'Manage Identities' to create identities"
echo "  3. Create ephemeral (privacy) or persistent (reputation) identities"
echo ""
echo "Log file:"
echo "  tail -f /tmp/sparrow-p2p.log"
echo ""
echo "To stop Sparrow:"
echo "  kill $SPARROW_PID"
echo ""
echo "Or to kill all Sparrow instances:"
echo "  pkill -f Sparrow"
echo ""
echo "Press Ctrl+C to exit this script (Sparrow will continue running)"
echo ""

# Wait for user to press Ctrl+C
trap "echo ''; echo 'Script terminated. Sparrow is still running.'; echo 'To stop it: kill $SPARROW_PID'; exit 0" INT

# Keep script running to show status
while true; do
    # Check if process is still running
    if ! kill -0 $SPARROW_PID 2>/dev/null; then
        echo "⚠️  Sparrow has stopped"
        break
    fi
    sleep 5
done

echo ""
echo "Sparrow has stopped."
echo "Check log for errors:"
echo "  /tmp/sparrow-p2p.log"

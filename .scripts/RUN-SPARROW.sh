#!/bin/bash

echo "=========================================="
echo "  Sparrow Browser - Launcher Directo"
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

echo "✅ Variables configuradas"
echo ""

# Cambiar al directorio del proyecto
cd "$(dirname "$0")"

# Verificar que el binario existe
if [ ! -f "./build/jpackage/Sparrow/bin/Sparrow" ]; then
    echo "❌ Binario no encontrado. Compilando..."
    ./gradlew clean jpackage
    echo ""
fi

echo "Iniciando Sparrow Wallet..."
echo ""

# Ejecutar binario directamente (no gradlew)
exec ./build/jpackage/Sparrow/bin/Sparrow

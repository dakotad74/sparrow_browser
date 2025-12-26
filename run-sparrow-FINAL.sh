#!/bin/bash

echo "=========================================="
echo "  Sparrow Browser - Launcher Final"
echo "=========================================="
echo ""

# Extraer variables de entorno de la sesión gráfica activa
XWAYLAND_PID=$(pgrep -u $USER Xwayland | head -1)

if [ -z "$XWAYLAND_PID" ]; then
    echo "❌ ERROR: No se detectó sesión gráfica activa"
    echo ""
    echo "Asegúrate de:"
    echo "1. Tener una sesión GNOME activa"
    echo "2. Ejecutar este script como el usuario de la sesión gráfica"
    exit 1
fi

# Extraer variables del proceso Xwayland
export DISPLAY=$(cat /proc/$XWAYLAND_PID/environ | tr '\0' '\n' | grep "^DISPLAY=" | cut -d= -f2)
export XAUTHORITY=$(cat /proc/$XWAYLAND_PID/environ | tr '\0' '\n' | grep "^XAUTHORITY=" | cut -d= -f2)
export XDG_RUNTIME_DIR=/run/user/$(id -u)

echo "Variables configuradas:"
echo "  DISPLAY=$DISPLAY"
echo "  XAUTHORITY=$XAUTHORITY"
echo "  XDG_RUNTIME_DIR=$XDG_RUNTIME_DIR"
echo ""

# Verificar que funciona
if ! xdpyinfo >/dev/null 2>&1; then
    echo "⚠️  Advertencia: No se puede conectar al display"
    echo "    Esto es normal si ejecutas desde VSCode/SSH"
    echo ""
    echo "Ejecuta desde una terminal GNOME real (Ctrl+Alt+T):"
    echo "  cd ~/Desarrollo/SparrowDev/sparrow"
    echo "  ./run-sparrow-FINAL.sh"
    echo ""
    exit 1
fi

echo "✅ Conexión al display verificada"
echo ""

# Cambiar al directorio del proyecto
cd "$(dirname "$0")"

echo "Iniciando Sparrow Wallet..."
echo ""

# Ejecutar con gradlew
exec ./gradlew run

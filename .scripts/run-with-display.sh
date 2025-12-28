#!/bin/bash

# Script para ejecutar Sparrow forzando el display
# Detecta automáticamente la sesión gráfica activa

echo "Detectando sesión gráfica..."

# Intentar encontrar el DISPLAY correcto
for display in :0 :1; do
    if DISPLAY=$display xdpyinfo >/dev/null 2>&1; then
        echo "✅ Display encontrado: $display"
        export DISPLAY=$display
        break
    fi
done

if [ -z "$DISPLAY" ]; then
    echo "❌ No se pudo detectar ningún display activo"
    echo ""
    echo "Intenta ejecutar desde una terminal GNOME (no SSH):"
    echo "1. Presiona Ctrl+Alt+T para abrir terminal gráfica"
    echo "2. Ejecuta: cd ~/Desarrollo/SparrowDev/sparrow && ./gradlew run"
    exit 1
fi

echo "DISPLAY=$DISPLAY"
echo "XDG_RUNTIME_DIR=/run/user/$(id -u)"
echo ""

# Configurar variables necesarias
export XDG_RUNTIME_DIR=/run/user/$(id -u)
export XAUTHORITY=$HOME/.Xauthority

# Cambiar al directorio del proyecto
cd "$(dirname "$0")"

echo "Ejecutando Sparrow Wallet..."
echo ""

# Ejecutar con gradlew
exec ./gradlew run

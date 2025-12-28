#!/bin/bash

echo "=== Test de Conexión al Display ==="
echo ""

# Extraer variables de Xwayland
XWAYLAND_PID=$(pgrep -u $USER Xwayland | head -1)

if [ -z "$XWAYLAND_PID" ]; then
    echo "❌ No hay sesión Xwayland activa"
    exit 1
fi

export DISPLAY=$(cat /proc/$XWAYLAND_PID/environ | tr '\0' '\n' | grep "^DISPLAY=" | cut -d= -f2)
export XAUTHORITY=$(cat /proc/$XWAYLAND_PID/environ | tr '\0' '\n' | grep "^XAUTHORITY=" | cut -d= -f2)

echo "DISPLAY=$DISPLAY"
echo "XAUTHORITY=$XAUTHORITY"
echo ""

echo "Probando conexión..."
if xdpyinfo >/dev/null 2>&1; then
    echo "✅ ¡Conexión exitosa al display!"
    echo ""
    echo "Abriendo ventana de prueba por 3 segundos..."
    timeout 3 xeyes &
    sleep 3
    echo ""
    echo "Si viste una ventana con ojos, ¡está funcionando!"
    echo ""
    echo "Ahora puedes ejecutar Sparrow con:"
    echo "  ./run-sparrow-FINAL.sh"
else
    echo "❌ No se pudo conectar al display"
    echo ""
    echo "Estás ejecutando desde una terminal sin acceso gráfico."
    echo "Abre una terminal GNOME (Ctrl+Alt+T) y vuelve a intentar."
fi

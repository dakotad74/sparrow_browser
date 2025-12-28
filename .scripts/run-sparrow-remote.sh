#!/bin/bash

# Script para ejecutar Sparrow vía SSH con X11 forwarding
# Uso desde tu máquina remota: ./run-sparrow-remote.sh

# CONFIGURACIÓN
REMOTE_USER="r2d2"
REMOTE_HOST="tu-servidor.com"  # CAMBIA ESTO por tu IP o hostname
SPARROW_DIR="/home/r2d2/Desarrollo/SparrowDev/sparrow"

echo "🚀 Conectando a $REMOTE_USER@$REMOTE_HOST para ejecutar Sparrow..."
echo "📦 Asegúrate de tener X11 forwarding habilitado"
echo ""

# Conectar vía SSH con X11 forwarding y ejecutar Sparrow
ssh -X $REMOTE_USER@$REMOTE_HOST "cd $SPARROW_DIR && ./gradlew run"

echo ""
echo "✅ Conexión cerrada"

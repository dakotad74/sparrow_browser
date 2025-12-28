#!/bin/bash

# Script para ejecutar Sparrow en un servidor sin display usando Xvfb
# (Virtual framebuffer X server - para testing headless)
# Uso: ./run-sparrow-xvfb.sh

cd /home/r2d2/Desarrollo/SparrowDev/sparrow

echo "🚀 Ejecutando Sparrow con Xvfb (virtual display)..."
echo "📁 Directorio: $(pwd)"
echo ""

# Verificar si Xvfb está instalado
if ! command -v Xvfb &> /dev/null; then
    echo "❌ Xvfb no está instalado"
    echo "📦 Instala con: sudo apt-get install xvfb"
    exit 1
fi

# Ejecutar Sparrow en display virtual
echo "🖥️  Iniciando display virtual en :99"
Xvfb :99 -screen 0 1024x768x24 &
XVFB_PID=$!

export DISPLAY=:99

echo "⏳ Esperando a que Xvfb inicie..."
sleep 2

echo "🚀 Ejecutando Sparrow..."
./gradlew run

# Limpiar
echo ""
echo "🧹 Cerrando display virtual..."
kill $XVFB_PID

echo "✅ Sparrow cerrado"

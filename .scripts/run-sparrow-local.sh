#!/bin/bash

# Script para ejecutar Sparrow localmente (en la máquina con display)
# Uso: ./run-sparrow-local.sh

cd /home/r2d2/Desarrollo/SparrowDev/sparrow

echo "🚀 Ejecutando Sparrow Browser localmente..."
echo "📁 Directorio: $(pwd)"
echo ""

# Ejecutar Sparrow
./gradlew run

echo ""
echo "✅ Sparrow cerrado"

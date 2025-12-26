# Solución al Problema "No display detected"

## 🔍 Diagnóstico

El error "No display detected" ocurre porque **estás ejecutando desde una terminal que no tiene acceso directo a tu sesión gráfica**. Esto puede pasar cuando:

1. Ejecutas desde SSH (incluso localhost)
2. Ejecutas desde una terminal que se inició sin variables de entorno gráficas
3. La terminal no heredó las variables DISPLAY y XAUTHORITY correctamente

## ✅ SOLUCIÓN: Ejecutar desde Terminal GNOME Nativa

### Paso 1: Abrir Terminal Gráfica

Presiona **Ctrl + Alt + T** (esto abrirá una terminal GNOME con acceso completo al display)

### Paso 2: Ejecutar Sparrow

```bash
cd ~/Desarrollo/SparrowDev/sparrow
DISPLAY=:0 ./gradlew run
```

O más simple:

```bash
cd ~/Desarrollo/SparrowDev/sparrow
./run-with-display.sh
```

---

## 🔧 Alternativas si el Problema Persiste

### Opción A: Ejecutar el JAR Directamente

```bash
cd ~/Desarrollo/SparrowDev/sparrow
DISPLAY=:0 java -jar build/libs/sparrow-2.3.2.jar
```

### Opción B: Usar el Binario jpackage

```bash
cd ~/Desarrollo/SparrowDev/sparrow
DISPLAY=:0 ./build/jpackage/Sparrow/bin/Sparrow
```

### Opción C: Ejecutar desde el Gestor de Aplicaciones

1. Presiona la tecla **Super** (Windows)
2. Busca "Terminal"
3. Abre Terminal
4. Ejecuta:
   ```bash
   cd ~/Desarrollo/SparrowDev/sparrow && DISPLAY=:0 ./gradlew run
   ```

---

## 🐛 Debug: Verificar Display

Desde una terminal GNOME (Ctrl+Alt+T), verifica:

```bash
echo $DISPLAY          # Debería mostrar :0 o :1
echo $XDG_SESSION_TYPE # Debería mostrar wayland o x11
xdpyinfo | head -5     # Debería mostrar info del display
```

Si alguno falla, es que la terminal no está conectada correctamente a la sesión gráfica.

---

## 🎯 Por Qué Pasa Esto

**VSCode/Claude Code** ejecuta comandos en un proceso background que:
- ❌ No hereda automáticamente las variables DISPLAY
- ❌ No tiene acceso directo a tu sesión gráfica
- ❌ No puede abrir ventanas directamente

**Terminal GNOME** (Ctrl+Alt+T):
- ✅ Hereda automáticamente DISPLAY
- ✅ Tiene acceso completo a tu sesión gráfica
- ✅ Puede abrir ventanas sin problemas

---

## ✅ Comando Definitivo

**Desde terminal GNOME (Ctrl+Alt+T):**

```bash
cd ~/Desarrollo/SparrowDev/sparrow && DISPLAY=:0 ./gradlew run
```

Esto **DEBE funcionar** si:
1. Tienes una sesión gráfica activa (que tienes - veo Wayland corriendo)
2. Lo ejecutas desde una terminal nativa de GNOME (no SSH, no VSCode terminal)
3. El usuario es r2d2 (el dueño de la sesión gráfica)

---

## 📝 Resumen

**NO FUNCIONA**: Terminal de VSCode, SSH, Claude Code
**SÍ FUNCIONA**: Terminal GNOME nativa (Ctrl+Alt+T)

**Razón**: Las apps gráficas necesitan acceso directo a la sesión gráfica del usuario.

---

Una vez que lo ejecutes desde la terminal GNOME correcta, verás la ventana de Sparrow abrirse normalmente. 🚀

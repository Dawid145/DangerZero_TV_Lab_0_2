# Danger Zero — TV Lab 0.2

Versión corregida del prototipo de diagnóstico local.

## Correcciones realizadas respecto a 0.1

1. **Origen de instalación corregido.** Se reemplazó el uso inexistente de `PackageInstaller.getPackageInfo()` por `PackageManager.getInstallSourceInfo()` en Android 11+.
2. **AccessibilityService corregido.** Ya no se busca `BIND_ACCESSIBILITY_SERVICE` dentro de `requestedPermissions`. Se consultan servicios que realmente declaran la interfaz `android.accessibilityservice.AccessibilityService` y la protección `BIND_ACCESSIBILITY_SERVICE`.
3. **Eliminados permisos innecesarios.** Se retiró `REQUEST_DELETE_PACKAGES` porque esta versión no desinstala aplicaciones.
4. **Eliminado Device Administrator del prototipo.** No aporta una capacidad de enforcement real en esta etapa y añadir privilegios sin utilizarlos aumenta superficie innecesaria.
5. **Eliminada dependencia AndroidX no utilizada.** La interfaz usa APIs del framework para reducir superficie de dependencias.
6. **Escaneo tolerante a errores.** Si un paquete es inaccesible o está malformado, no se aborta todo el análisis.
7. **Valores nulos corregidos.** Nombre de versión y etiquetas de aplicaciones tienen tratamiento seguro.
8. **SHA-256 mejorado.** El hash de firma contempla todos los certificados disponibles, en lugar de asumir siempre uno.
9. **Nomenclatura corregida.** “SIN SEÑALES” no significa “seguro”; solo significa que este motor local no encontró señales heurísticas.
10. **Interfaz TV ajustada.** Botones enfocables y navegación básica con mando.

## Seguridad de esta versión

La aplicación es **solo lectura** en esta etapa. No contiene código para:

- instalar APKs;
- desinstalar aplicaciones;
- ejecutar comandos del sistema;
- descargar o ejecutar código remoto;
- activar Device Administrator automáticamente;
- usar AccessibilityService propio;
- realizar conexiones de red.

El único permiso especial del Manifest es `QUERY_ALL_PACKAGES`, necesario para el inventario amplio de aplicaciones en dispositivos donde Android lo permita.

## Qué hace

- Inventario de aplicaciones instaladas expuestas por PackageManager.
- Nombre, paquete, versión y aplicación de sistema.
- Ruta del APK cuando el sistema la expone.
- SHA-256 del APK cuando el archivo es legible.
- SHA-256 de certificados de firma.
- Origen/instalador cuando Android proporciona esa información.
- Detección real de declaraciones de AccessibilityService.
- Heurísticas locales explicables.

## Qué NO hace todavía

No es todavía un antivirus completo ni una solución universal de bloqueo de instalaciones. Las decisiones fuertes requerirán un motor de evidencia/reputación y, para enforcement, capacidades legítimas del firmware como Device Owner/DPC cuando estén disponibles.

## Compilación

El proyecto es Android/Gradle estándar. Codemagic puede compilar un APK debug y mostrarlo con Quick Launch cuando el artefacto se genera correctamente.

Comando esperado:

    ./gradlew assembleDebug

Artefacto:

    app/build/outputs/apk/debug/app-debug.apk

**Nota:** el ZIP preparado aquí todavía no incluye `gradlew`/`gradle-wrapper.jar`, porque esos binarios deben generarse con una distribución de Gradle de confianza. Codemagic recomienda incluir el Gradle Wrapper en el repositorio y verificar su distribución mediante SHA-256.

## Próximo paso

Después de validar que 0.2 compila y arranca en un entorno de prueba, el siguiente módulo será el perfilador de compatibilidad del TV y después el motor de evidencia/reputación.

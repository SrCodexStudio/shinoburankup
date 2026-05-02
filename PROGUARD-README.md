# ProGuard Protection for ShinobuRankup

Este documento describe como usar la configuracion de ProGuard para proteger el plugin.

## Comandos de Build

### Build de Produccion (Ofuscado)
```bash
./gradlew build
# o
./gradlew buildRelease
```

Genera: `build/libs/ShinobuRankup.jar` (ofuscado y protegido)

### Build de Desarrollo (Sin Ofuscar)
```bash
./gradlew buildDev
```

Genera: `build/libs/ShinobuRankup-DEV.jar` (sin ofuscar, para debugging)

## Archivos Generados

| Archivo | Descripcion |
|---------|-------------|
| `build/libs/ShinobuRankup.jar` | JAR de produccion ofuscado |
| `build/libs/ShinobuRankup-DEV.jar` | JAR de desarrollo sin ofuscar |
| `build/proguard/mapping.txt` | Mapeo para deofuscar stack traces |
| `build/proguard/seeds.txt` | Clases/metodos preservados |
| `build/proguard/usage.txt` | Codigo removido por shrinking |

## IMPORTANTE: mapping.txt

**SIEMPRE guarda el archivo `mapping.txt` de cada version que distribuyas.**

Este archivo es necesario para:
1. Deofuscar stack traces de errores reportados por usuarios
2. Debuggear problemas en produccion
3. Entender que clase corresponde a cada nombre ofuscado

### Como deofuscar un stack trace

Usa la herramienta ReTrace de ProGuard:
```bash
java -jar proguard-retrace.jar mapping.txt stacktrace.txt
```

O usa el GUI de ProGuard para deofuscar manualmente.

## Que esta Protegido

### Clases Ofuscadas (Renombradas a k/a, k/b, etc.)
- Managers internos (ConfigManager, RankManager, etc.)
- Services (PlayerServiceImpl, SQLitePlayerService, etc.)
- Listeners internos
- GUIs
- Utilidades
- Cache implementations
- Database implementations

### Clases Preservadas (Mantienen nombre original)
- `com.shinobu.rankup.ShinobuRankup` - Clase principal
- `com.shinobu.rankup.api.*` - API publica completa
- `com.shinobu.rankup.data.*` - Data classes (PlayerData, RankData, etc.)
- `com.shinobu.rankup.event.*` - Eventos de Bukkit

## Compatibilidad

- **Minecraft**: 1.17+ (Paper/Spigot)
- **Java**: 17+
- **ProGuard**: 7.6.1

### Paper 1.21+ Notas
La configuracion usa `-useuniqueclassmembernames` en lugar de `-overloadaggressively` para evitar el error "Duplicate key" que ocurre en Paper 1.21+.

## Troubleshooting

### Error "Class not found" al cargar plugin
Verifica que `plugin.yml` tenga `main: com.shinobu.rankup.ShinobuRankup`.
Esta clase esta preservada y debe existir exactamente con ese nombre.

### EventHandlers no funcionan
Los metodos `@EventHandler` estan preservados por las reglas de ProGuard.
Si tienes un listener nuevo, asegurate que implementa `org.bukkit.event.Listener`.

### Commands no funcionan
Los metodos `onCommand` y `onTabComplete` estan preservados.
Verifica que la clase implementa `CommandExecutor` o `TabCompleter`.

### PlaceholderAPI no funciona
La clase `ShinobuRankupExpansion` esta completamente preservada.
Verifica que PlaceholderAPI esta instalado y el plugin cargo correctamente.

### Errores de reflexion
Si usas reflexion en clases internas, agrega reglas keep en `proguard-rules.pro`:
```proguard
-keep class com.shinobu.rankup.tu.ClaseInterna { *; }
```

## Archivos de Configuracion

- `build.gradle.kts` - Configuracion de Gradle con tareas de ProGuard
- `proguard-rules.pro` - Reglas de ofuscacion/preservacion
- `proguard-dictionary.txt` - Diccionario de nombres ofuscados

## String Obfuscation

El proyecto incluye `StringObfuscator.kt` para ofuscar strings sensibles.

### Uso:
```kotlin
// En desarrollo - obtener valor codificado:
val encoded = StringObfuscator.encodeString("mi_string_secreto")
println(encoded) // "HRkYGR0..."

// En produccion - decodificar en runtime:
val decoded = StringObfuscator.decode("HRkYGR0...")
```

## Soporte

Si encuentras problemas con la ofuscacion:
1. Prueba con `buildDev` para verificar si el problema es de ofuscacion
2. Revisa `mapping.txt` para identificar clases ofuscadas
3. Agrega reglas `-keep` si necesitas preservar algo adicional

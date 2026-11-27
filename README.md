# ExcellentEnchantsNK

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-Bedrock%20Edition-43A047?style=for-the-badge&logo=minecraft&logoColor=white" alt="Minecraft Bedrock Edition">
  <img src="https://img.shields.io/badge/Server-NukkitPetteriM1Edition-2196F3?style=for-the-badge" alt="NukkitPetteriM1Edition">
  <img src="https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17">
  <img src="https://img.shields.io/badge/Build-Maven-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white" alt="Maven">
</p>

<p align="center"><b>Encantamientos personalizados para NukkitPetteriM1Edition, con libros propios, datos NBT y aplicación mediante yunques.</b></p>

---

ExcellentEnchantsNK añade nueve encantamientos personalizados al servidor. Está pensado para ampliar el equipamiento de supervivencia sin modificar los encantamientos vanilla ni introducir sistemas de obtención aleatoria.

- **Autor:** BrunoJaegerK
- **Servidor:** NukkitPetteriM1Edition
- **Versión inicial:** 1.0.0

## Características

- Nueve encantamientos personalizados con efectos funcionales.
- Los encantamientos se guardan en NBT dentro de cada objeto.
- Los libros personalizados también utilizan NBT y conservan su información al moverse entre inventarios, reiniciar el servidor o pasar por un yunque.
- Los libros solo se pueden crear mediante comandos administrativos.
- Aplicación directa a objetos mediante comando.
- Aplicación de libros mediante yunques.
- Niveles configurables para cada encantamiento.
- Mensajes y parámetros editables.
- No modifica los encantamientos vanilla ni añade objetos a los cofres, pesca, aldeanos o tablas de encantamientos.

## Encantamientos incluidos

| Encantamiento | Objeto compatible | Efecto |
|---|---|---|
| Protección Elemental | Casco, pechera, pantalones y botas | Reduce el daño mágico, de fuego, lava, magma y rayos. La reducción depende del nivel. |
| Caminador de Llamas | Botas | Convierte temporalmente la lava bajo los pies en bloques de magma y protege del daño de magma. |
| Velocidad | Botas | Mantiene el efecto de Velocidad mientras se llevan puestas. Los niveles superiores aumentan el amplificador. |
| Flechas Confusas | Arcos y ballestas | Las flechas disparadas aplican Náusea al objetivo cuando impactan y causan daño. |
| Túnel | Picos | Mina una zona adicional alrededor del bloque roto: nivel I (1×2), nivel II (2×2) y nivel III (3×3). |
| Ceguera | Espadas y hachas | Aplica Ceguera al objetivo golpeado. Los niveles superiores aumentan la duración. |
| Doble Strike | Espadas y hachas | Multiplica el daño del golpe según el valor configurado. |
| Vampiro | Espadas y hachas | Recupera una parte del daño causado al objetivo. |
| Toque de Seda | Picos | Permite extraer cualquier spawner de criatura compatible con PM1E, conservando el tipo de entidad y los parámetros de aparición al volver a colocarlo. |

Los encantamientos solo se aplican a los tipos de objetos indicados. No se pueden colocar, por ejemplo, Velocidad en un casco o Túnel en una espada.

## Comandos

Todos los comandos requieren el permiso `excellentenchants.admin`, asignado a operadores por defecto.

| Comando | Descripción |
|---|---|
| `/eenchant` | Muestra la ayuda y los nombres disponibles. |
| `/eenchant add <encantamiento> [nivel]` | Aplica el encantamiento al objeto que tienes en la mano. |
| `/eenchant book <jugador> <encantamiento> [nivel]` | Entrega un libro personalizado al jugador. |
| `/eenchant give <jugador> <encantamiento> [nivel]` | Alias para entregar un libro personalizado. |

Nombres disponibles:

- `elemental_protection`
- `flame_walker`
- `speed`
- `confusing_arrows`
- `tunnel`
- `blindness`
- `double_strike`
- `vampire`
- `spawner_touch` (alias: `toque_de_seda`)

También se aceptan los nombres en español indicados en la configuración y sus alias.

Ejemplos:

```text
/eenchant add speed 2
/eenchant add tunnel 3
/eenchant add spawner_touch 1
/eenchant book Bruno vampire 1
/eenchant give Bruno confusing_arrows 2
```

El comando `add` trabaja sobre el objeto que el administrador tiene en la mano. Los comandos `book` y `give` entregan libros; no aplican directamente el encantamiento al inventario del jugador.

## Libros y yunques

Los libros personalizados no aparecen en cofres, pesca, intercambios, generadores de loot ni tablas de encantamientos. Tampoco se registran como objetos de aparición creativa. La única forma de generarlos es mediante los comandos administrativos.

Para aplicar un libro:

1. Coloca el objeto compatible en el primer espacio del yunque.
2. Coloca el libro personalizado en el segundo espacio.
3. Recoge el resultado.

El libro se consume al completar la operación. Si el objeto no es compatible, la operación se cancela y se conserva el libro.

Los libros y objetos encantados mantienen sus datos personalizados en NBT. El texto del encantamiento que aparece en el lore es informativo; la funcionalidad se comprueba usando los datos NBT, no el nombre visible del objeto.

## Configuración

El archivo se encuentra en `plugins/ExcellentEnchantsNK/config.yml`.

### Niveles máximos

`max-levels` permite establecer el nivel máximo de cada encantamiento. Por defecto, todos tienen un máximo de III.

### Protección Elemental

`elemental-protection.reduction-per-level` define la reducción de daño por nivel. El valor predeterminado es 0.15 (15%). La reducción total está limitada al 75%.

### Caminador de Llamas

`flame-walker.duration-ticks` controla cuánto tiempo permanecen los bloques de magma creados bajo los pies antes de restaurar la lava original.

### Flechas Confusas

`confusing-arrows.duration-ticks` establece la duración base de Náusea. Los niveles superiores añaden duración.

### Ceguera

`blindness.duration-ticks` establece la duración base de Ceguera. Los niveles superiores añaden duración.

### Doble Strike

`double-strike.damage-multiplier` define el multiplicador de daño. El valor predeterminado es 2.0.

### Vampiro

`vampire.heal-fraction` determina qué fracción del daño causado se recupera como salud. El valor predeterminado es 0.25 (25%).

### Toque de Seda

`spawner_touch` se aplica a picos y permite recuperar spawners de cualquier tipo de criatura que PM1E tenga registrado. El objeto conserva el NBT del `BlockEntitySpawner`, incluido `EntityId`, los tiempos de aparición, el rango, el máximo de entidades cercanas y la cantidad de criaturas. Al colocarlo, se reconstruye la entidad de bloque con esos datos. Su nivel máximo predeterminado es I. Los tipos de entidad personalizados solo funcionarán si están registrados y son compatibles con el sistema de entidades de PM1E.

### Mensajes

Todos los mensajes están dentro de `messages`. Puedes cambiar el prefijo y las respuestas de comandos sin modificar el código.

Después de modificar la configuración, reinicia el servidor para aplicar los cambios.

## Permisos

| Permiso | Descripción | Predeterminado |
|---|---|---|
| `excellentenchants.admin` | Crear libros y aplicar encantamientos mediante comandos. | OP |

Los jugadores no necesitan permisos especiales para usar objetos encantados ni para aplicar libros en yunques.

## Instalación

1. Descarga el archivo JAR desde los artefactos de la última compilación de GitHub Actions.
2. Colócalo en la carpeta `plugins` del servidor.
3. Inicia o reinicia NukkitPetteriM1Edition.
4. Ajusta `config.yml` si quieres cambiar niveles, efectos o mensajes.

## Compilar

Requisitos: JDK 17 y Maven.

```bash
mvn clean package
```

El archivo JAR se genera en `target/ExcellentEnchantsNK-1.0.0.jar`.

GitHub Actions ejecuta la compilación automáticamente al actualizar la rama principal. El JAR queda disponible como artefacto de la ejecución.

## ℹ️ About

ExcellentEnchantsNK añade encantamientos personalizados a NukkitPetteriM1Edition mediante NBT, comandos administrativos y yunques.

- **Autor:** BrunoJaegerK
- **Repositorio:** https://github.com/BrunoJaegerK/AdvancedEnchantmentsNK

## 📜 Licencia

Este proyecto se distribuye bajo la licencia GPL-3.0. Consulta el archivo LICENSE para conocer sus términos.

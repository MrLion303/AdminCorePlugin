# AdminCorePlugin

Plugin de moderación y herramientas administrativas para servidores de Minecraft.

**Compatibilidad oficial:** Minecraft 1.20.x, 1.21.x y 26.3. El workflow genera un JAR independiente para cada rango.

## Comandos

Todos los comandos administrativos requieren ser **OP**.

| Comando | Descripción |
|---|---|
| `/admincore lenguage <español\|English>` | Cambia el idioma de los mensajes del plugin. |
| `/admincore help [página]` | Muestra la ayuda de AdminCore, organizada en páginas. |
| `/vanish` | Activa o desactiva el vanish. Los jugadores no OP no pueden verte. |
| `/vanish all` | Hace que el jugador en vanish sea invisible para todos, incluidos los OP. |
| `/vanish tab` | Oculta el nombre del jugador del tabulador. |
| `/vanish all tab` | Combina el vanish para todos con el ocultamiento del nombre en el tabulador. |
| `/nick <nombre\|off>` | Cambia temporalmente el nombre mostrado o restaura el nombre original. |
| `/incognito` | Activa o desactiva un nombre aleatorio de incógnito. |
| `/hidenick` | Oculta o muestra el nombre sobre la cabeza del jugador. |
| `/blockinfo` | Muestra quién colocó el bloque que estás mirando, si está registrado. |
| `/chesthistory` | Muestra las últimas 10 veces que se abrió el cofre que estás mirando, con el nombre del jugador y la fecha/hora. |
| `/invsee <jugador>` | Abre y permite editar el inventario de un jugador conectado. |
| `/ecsee <jugador>` | Abre y permite editar el Ender Chest de un jugador conectado. |
| `/sign "mensaje"` | Recibe un letrero que guarda el mensaje indicado para colocarlo después. |
| `/setsign <material>` | Establece el tipo de letrero predeterminado que entrega `/sign`. |
| `/admin freeze <jugador>` | Congela a un jugador para impedir que se mueva. |
| `/admin unfreeze <jugador>` | Descongela a un jugador para permitirle volver a moverse. |
| `/help <mensaje>` | Envía el mensaje en el chat a todos los jugadores OP, mostrando el nombre real del administrador que lo envió. |
| `/plugins` | Los OP pueden ver la lista de plugins instalados. Los jugadores sin permisos reciben un mensaje de acceso denegado. |

## Idiomas

AdminCore permite elegir entre **Español** e **English**. El idioma se guarda para cada administrador.

## Compatibilidad

- Minecraft 1.20.x
- Minecraft 1.21.x
- Minecraft 26.3

El workflow de GitHub Actions compila automáticamente un JAR independiente para cada versión o rango compatible.

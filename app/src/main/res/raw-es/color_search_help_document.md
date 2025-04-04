## Búsqueda de colores

La búsqueda de colores admite las siguientes 5 modalidades. Todas las modalidades no distinguen mayúsculas de minúsculas.

### 1. Nombre de color (idioma actual)

Busca utilizando el nombre de color tal como se muestra en el idioma actual.

#### Ejemplo 1.1: rojo

Coincide con todos los nombres de color que contengan "rojo", como "Rojo", "Rojo anaranjado", "Rojo oscuro", "Rojo (200)", etc.

#### Ejemplo 1.2: azul50

Coincide con todos los nombres de color que incluyan "azul50". Durante la coincidencia, se ignoran todos los caracteres que no sean alfanuméricos (espacios, paréntesis, barras, guiones, etc.). Por lo tanto, "azul50" puede coincidir con "Azul (50)" o "Azul (500)", pero no con "Azul claro (50)" debido a que "claro" no puede ignorarse.

### 2. Nombre de color (inglés)

Usa el nombre de color en inglés como palabra clave de búsqueda.

#### Ejemplo 2.1: red

Coincide con todos los nombres de color en inglés que contengan "red", como "Rojo", "Rojo anaranjado", "Rojo oscuro", etc. Incluso si el idioma de la interfaz no es inglés, puedes buscar el color correspondiente usando una palabra clave en inglés.

#### Ejemplo 2.2: blue50

Igual que en el Ejemplo 1.2, puede coincidir con "Azul (50)", "Azul (500)" y "Azul claro (50)".

### 3. HEX

Comienza con "#" para activar el modo de búsqueda HEX.

#### Ejemplo 3.1: #FF007F

Coincide con el color cuyo valor HEX es "#FF007F", por ejemplo "Rosa".

#### Ejemplo 3.2: #FF

Coincide con todos los colores cuyo valor HEX empieza con "#FF", como "#FF8099", "#FF00CB", "#FFFAFA", etc.

### 4. Expresión regular

Comienza con "/" y termina con "/", activando el modo de búsqueda de expresiones regulares (similar a la sintaxis de JavaScript).

#### Ejemplo 4.1: /rojo|naranja|amarillo/

Coincide con todos los nombres de color que contengan "Rojo", "Naranja" o "Amarillo".

#### Ejemplo 4.2: /\b50\b/

Coincide con todos los nombres de color que contengan "50" con límites de palabra a ambos lados, como "Rojo (50)", "Rosa (50)", "Amarillo (50)", etc., excepto "Rojo (500)" o "Rosa (500)".

#### Ejemplo 4.3: /red|orange/

Similar al Ejemplo 2.2, la expresión regular también coincide con nombres de color en inglés. /red|orange/ puede coincidir con "Rojo intenso", "Naranja quemado", "Rojo violeta pálido", etc.

### 5. Expresión regular para HEX

Comienza con "#/" y termina con "/", activando el modo de búsqueda de expresiones regulares para HEX.

#### Ejemplo 5.1: #/FF\b/

Coincide con todos los valores HEX que contengan "FF" al final como límite de palabra, representados como "#xxxxFF", por ejemplo "#2962FF", "#D94DFF", "#F0F8FF", etc.

#### Ejemplo 5.2: #/#FF/

Coincide con todos los valores HEX que contengan "#FF", idéntico al Ejemplo 3.2, representado como "#FFxxxx".

#### Ejemplo 5.3: #/#..FF/

Coincide con todos los valores HEX que tengan "FF" en el medio (es decir "#xxFFxx"), como "#CCFF00", "#66FFE6", "#00FF7F", etc.

#### Ejemplo 5.4: #/#((?<!0).){2}0000/

Coincide con todos los valores HEX que solo contengan el componente rojo (representados como "#xx0000"), excluyendo el negro. Ejemplos: "#8B0000", "#D50000", "#FF0000", etc.

#### Ejemplo 5.5: #/#0000(.(?!0)){2}/

Coincide con todos los valores HEX que solo contengan el componente azul (representados como "#0000xx"), excluyendo el negro. Ejemplos: "#00008B", "#0000CD", "#0000FF", etc.

#### Ejemplo 5.6: #/#00((?!0).)(.(?<!0))00/

Coincide con todos los valores HEX que solo contengan el componente verde (representados como "#00xx00"), excluyendo el negro. Ejemplos: "#006400", "#00FF00", etc.
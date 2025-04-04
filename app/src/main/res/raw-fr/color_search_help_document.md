## Recherche de couleurs

La recherche de couleurs prend en charge les 5 méthodes suivantes. Toutes sont insensibles à la casse.

### 1. Nom de couleur (langue actuelle)

Utilisez le nom de la couleur tel qu'il est affiché dans la langue en cours.

#### Exemple 1.1 : rouge

Correspond à tous les noms de couleur contenant « rouge », tels que « Rouge », « Rouge orangé », « Rouge foncé », « Rouge (200) », etc.

#### Exemple 1.2 : bleu50

Correspond à tous les noms de couleur contenant « bleu50 ». Lors de la correspondance, tous les caractères non alphanumériques (tels que espaces, parenthèses, barres, tirets, etc.) sont ignorés. Ainsi, « bleu50 » peut correspondre à « Bleu (50) », « Bleu (500) » et « Gris bleu (50) », mais pas à « Bleu clair (50) » car « clair » ne peut être ignoré.

### 2. Nom de couleur (anglais)

Utilisez le nom de couleur en anglais comme mot-clé de recherche.

#### Exemple 2.1 : red

Correspond à tous les noms de couleur en anglais contenant « red », tels que « Rouge », « Rouge orangé », « Rouge foncé », etc. Même si la langue d'affichage n'est pas l'anglais, vous pouvez toujours rechercher la couleur correspondante à l'aide d'un mot-clé en anglais.

#### Exemple 2.2 : blue50

Comme dans l'exemple 1.2, il peut correspondre à « Bleu (50) », « Bleu (500) » et « Bleu clair (50) ».

### 3. HEX

Commencez par « # » pour activer le mode de recherche HEX.

#### Exemple 3.1 : #FF007F

Correspond à la couleur dont la valeur HEX est « #FF007F », par exemple « Rose ».

#### Exemple 3.2 : #FF

Correspond à toutes les couleurs dont la valeur HEX commence par « #FF », comme « #FF8099 », « #FF00CB », « #FFFAFA », etc.

### 4. Expression régulière

Commencez par « / » et terminez par « / » pour activer la recherche par expression régulière (similaire à la syntaxe JavaScript).

#### Exemple 4.1 : /rouge|orange|jaune/

Correspond à tous les noms de couleur contenant « Rouge », « Orange » ou « Jaune ».

#### Exemple 4.2 : /\b50\b/

Correspond à tous les noms de couleur contenant « 50 » avec des limites de mot de part et d'autre, tels que « Rouge (50) », « Rose (50) », « Jaune (50) », etc., à l'exclusion de « Rouge (500) », « Rose (500) », etc.

#### Exemple 4.3 : /red|orange/

Semblable à l'exemple 2.2, l'expression régulière correspond également aux noms de couleur en anglais. /red|orange/ peut correspondre à « Rouge intense », « Orange brûlé », « Rouge violet pâle », etc.

### 5. Expression régulière HEX

Commencez par « #/ » et terminez par « / » pour activer la recherche d'expressions régulières sur HEX.

#### Exemple 5.1 : #/FF\b/

Correspond à toutes les valeurs HEX contenant « FF » en fin de mot, représentées sous la forme « #xxxxFF », par exemple « #2962FF », « #D94DFF », « #F0F8FF », etc.

#### Exemple 5.2 : #/#FF/

Correspond à toutes les valeurs HEX contenant « #FF », identique à l'exemple 3.2, représentées sous la forme « #FFxxxx ».

#### Exemple 5.3 : #/#..FF/

Correspond à toutes les valeurs HEX qui contiennent « FF » au milieu (c.-à-d. « #xxFFxx »), par exemple « #CCFF00 », « #66FFE6 », « #00FF7F », etc.

#### Exemple 5.4 : #/#((?<!0).){2}0000/

Correspond à toutes les valeurs HEX ne contenant qu'une composante de rouge (sous la forme « #xx0000 ») en excluant le noir, par exemple « #8B0000 », « #D50000 », « #FF0000 », etc.

#### Exemple 5.5 : #/#0000(.(?!0)){2}/

Correspond à toutes les valeurs HEX ne contenant que la composante bleue (sous la forme « #0000xx ») en excluant le noir, par exemple « #00008B », « #0000CD », « #0000FF », etc.

#### Exemple 5.6 : #/#00((?!0).)(.(?<!0))00/

Correspond à toutes les valeurs HEX ne contenant que la composante verte (sous la forme « #00xx00 ») en excluant le noir, par exemple « #006400 », « #00FF00 », etc.
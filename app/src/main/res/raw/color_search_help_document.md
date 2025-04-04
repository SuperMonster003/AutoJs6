## Color Search

Color search supports the following 4 methods. All methods are case-insensitive.

### 1. Color Name

Search using the color name as shown in the current language.

#### Example 1.1: red

Matches all color names that contain "red", such as "Red", "Spinel red", "Strong red", "Red (200)", etc.

#### Example 1.2: blue50

Matches all color names that contain "blue50". During matching, all non-alphanumeric characters (such as spaces, parentheses, slashes, hyphens, etc.) are ignored. Therefore, "blue50" can match "Blue (50)", "Blue (500)", and "Light blue (50)", but cannot match "Blue gray (50)" because "gray" cannot be ignored.

### 2. HEX

Begin with "#" to enable HEX search mode.

#### Example 2.1: #FF007F

Matches the color whose HEX is "#FF007F", for example "Rose".

#### Example 2.2: #FF

Matches all colors whose HEX starts with "#FF", such as "#FF8099", "#FF00CB", "#FFFAFA", etc.

### 3. Regular Expression

Begin with "/" and end with "/", enabling the regular expression search mode (similar to JavaScript syntax).

#### Example 3.1: /red|orange|yellow/

Matches all color names containing "Red", "Orange", or "Yellow".

#### Example 3.2: /\b50\b/

Matches all color names containing "50" with boundaries on both sides, such as "Red (50)", "Pink (50)", "Yellow (50)", etc., but does not include "Red (500)" or "Pink (500)".

### 4. HEX Regular Expression

Begin with "#/" and end with "/", enabling the HEX regular expression search mode.

#### Example 4.1: #/FF\b/

Matches all HEX values containing "FF" at a word boundary, represented as "#xxxxFF", such as "#2962FF", "#D94DFF", "#F0F8FF", etc.

#### Example 4.2: #/#FF/

Matches all HEX values containing "#FF", identical to Example 3.2, represented as "#FFxxxx".

#### Example 4.3: #/#..FF/

Matches all HEX values with "FF" in the middle (i.e., "#xxFFxx"), such as "#CCFF00", "#66FFE6", "#00FF7F", etc.

#### Example 4.4: #/#((?<!0).){2}0000/

Matches all HEX values containing only the red component (represented as "#xx0000") while excluding black, such as "#8B0000", "#D50000", "#FF0000", etc.

#### Example 4.5: #/#0000(.(?!0)){2}/

Matches all HEX values containing only the blue component (represented as "#0000xx") while excluding black, such as "#00008B", "#0000CD", "#0000FF", etc.

#### Example 4.6: #/#00((?!0).)(.(?<!0))00/

Matches all HEX values containing only the green component (represented as "#00xx00") while excluding black, such as "#006400", "#00FF00", etc.
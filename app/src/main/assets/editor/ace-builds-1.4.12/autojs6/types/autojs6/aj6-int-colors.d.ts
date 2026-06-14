// Type definitions for AutoJs6 internal module colors
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.8.4
//
// Last modified: Jun 14, 2026

/// <reference path="../index.d.ts" />

/**
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/colors/Colors.kt
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/colors/Color.kt
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/colors/ColorNativeObject.kt
 * @Source /src/main/java/org/autojs/autojs/core/image/ColorTable.kt
 */

declare namespace Internal {

    import ColorInt = Colors.ColorInt;
    import DetectionAlgorithm = Colors.DetectionAlgorithm;
    import ColorComponent = Colors.ColorComponent;
    import ColorHueComponent = Colors.ColorHueComponent;
    import ComponentOptions = Colors.ComponentOptions;
    import ToComponentOptions = Colors.ToComponentOptions;
    import StringOptions = Colors.StringOptions;
    import FloatArray = Colors.FloatArray;

    class Colors extends org.autojs.autojs.core.image.Colors {

        web: typeof org.autojs.autojs.core.image.ColorTable.Web;
        css: typeof org.autojs.autojs.core.image.ColorTable.Css;
        material: typeof org.autojs.autojs.core.image.ColorTable.Material;
        android: typeof org.autojs.autojs.core.image.ColorTable.Android;
        all: Colors.ColorMap;
        themeColor: org.autojs.autojs.theme.ThemeColor;

        /**
         * <p style="background-color: #F0F8FF">#F0F8FF</p>
         * <p>Name (en): <b>Alice Blue</b></p>
         * <p>Name (zh): <b>爱丽丝蓝</b></p>
         * <p>Hex: <b>#F0F8FF</b></p>
         * <p>RGB: <b>240,248,255</b></p>
         * <p>HSV: <b>208,6,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        ALICE_BLUE: number;

        /**
         * <p style="background-color: #E32636">#E32636</p>
         * <p>Name (en): <b>Alizarin Crimson</b></p>
         * <p>Name (zh): <b>茜红 / 深茜红</b></p>
         * <p>Hex: <b>#E32636</b></p>
         * <p>RGB: <b>227,38,54</b></p>
         * <p>HSV: <b>355,83,89</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        ALIZARIN_CRIMSON: number;

        /**
         * <p style="background-color: #FFBF00">#FFBF00</p>
         * <p>Name (en): <b>Amber</b></p>
         * <p>Name (zh): <b>琥珀</b></p>
         * <p>Hex: <b>#FFBF00</b></p>
         * <p>RGB: <b>255,191,0</b></p>
         * <p>HSV: <b>45,100,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        AMBER: number;

        /**
         * <p style="background-color: #FFF8E1">#FFF8E1</p>
         * <p>Name (en): <b>Amber (50)</b></p>
         * <p>Name (zh): <b>琥珀 (50)</b></p>
         * <p>Hex: <b>#FFF8E1</b></p>
         * <p>RGB: <b>255,248,225</b></p>
         * <p>HSV: <b>46,12,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        AMBER_50: number;

        /**
         * <p style="background-color: #FFECB3">#FFECB3</p>
         * <p>Name (en): <b>Amber (100)</b></p>
         * <p>Name (zh): <b>琥珀 (100)</b></p>
         * <p>Hex: <b>#FFECB3</b></p>
         * <p>RGB: <b>255,236,179</b></p>
         * <p>HSV: <b>45,30,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        AMBER_100: number;

        /**
         * <p style="background-color: #FFE082">#FFE082</p>
         * <p>Name (en): <b>Amber (200)</b></p>
         * <p>Name (zh): <b>琥珀 (200)</b></p>
         * <p>Hex: <b>#FFE082</b></p>
         * <p>RGB: <b>255,224,130</b></p>
         * <p>HSV: <b>45,49,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        AMBER_200: number;

        /**
         * <p style="background-color: #FFD54F">#FFD54F</p>
         * <p>Name (en): <b>Amber (300)</b></p>
         * <p>Name (zh): <b>琥珀 (300)</b></p>
         * <p>Hex: <b>#FFD54F</b></p>
         * <p>RGB: <b>255,213,79</b></p>
         * <p>HSV: <b>46,69,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        AMBER_300: number;

        /**
         * <p style="background-color: #FFCA28">#FFCA28</p>
         * <p>Name (en): <b>Amber (400)</b></p>
         * <p>Name (zh): <b>琥珀 (400)</b></p>
         * <p>Hex: <b>#FFCA28</b></p>
         * <p>RGB: <b>255,202,40</b></p>
         * <p>HSV: <b>45,84,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        AMBER_400: number;

        /**
         * <p style="background-color: #FFC107">#FFC107</p>
         * <p>Name (en): <b>Amber (500)</b></p>
         * <p>Name (zh): <b>琥珀 (500)</b></p>
         * <p>Hex: <b>#FFC107</b></p>
         * <p>RGB: <b>255,193,7</b></p>
         * <p>HSV: <b>45,97,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        AMBER_500: number;

        /**
         * <p style="background-color: #FFB300">#FFB300</p>
         * <p>Name (en): <b>Amber (600)</b></p>
         * <p>Name (zh): <b>琥珀 (600)</b></p>
         * <p>Hex: <b>#FFB300</b></p>
         * <p>RGB: <b>255,179,0</b></p>
         * <p>HSV: <b>42,100,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        AMBER_600: number;

        /**
         * <p style="background-color: #FFA000">#FFA000</p>
         * <p>Name (en): <b>Amber (700)</b></p>
         * <p>Name (zh): <b>琥珀 (700)</b></p>
         * <p>Hex: <b>#FFA000</b></p>
         * <p>RGB: <b>255,160,0</b></p>
         * <p>HSV: <b>38,100,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        AMBER_700: number;

        /**
         * <p style="background-color: #FF8F00">#FF8F00</p>
         * <p>Name (en): <b>Amber (800)</b></p>
         * <p>Name (zh): <b>琥珀 (800)</b></p>
         * <p>Hex: <b>#FF8F00</b></p>
         * <p>RGB: <b>255,143,0</b></p>
         * <p>HSV: <b>34,100,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        AMBER_800: number;

        /**
         * <p style="background-color: #FF6F00">#FF6F00</p>
         * <p>Name (en): <b>Amber (900)</b></p>
         * <p>Name (zh): <b>琥珀 (900)</b></p>
         * <p>Hex: <b>#FF6F00</b></p>
         * <p>RGB: <b>255,111,0</b></p>
         * <p>HSV: <b>26,100,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        AMBER_900: number;

        /**
         * <p style="background-color: #FFE57F">#FFE57F</p>
         * <p>Name (en): <b>Amber (A100)</b></p>
         * <p>Name (zh): <b>琥珀 (A100)</b></p>
         * <p>Hex: <b>#FFE57F</b></p>
         * <p>RGB: <b>255,229,127</b></p>
         * <p>HSV: <b>48,50,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        AMBER_A100: number;

        /**
         * <p style="background-color: #FFD740">#FFD740</p>
         * <p>Name (en): <b>Amber (A200)</b></p>
         * <p>Name (zh): <b>琥珀 (A200)</b></p>
         * <p>Hex: <b>#FFD740</b></p>
         * <p>RGB: <b>255,215,64</b></p>
         * <p>HSV: <b>47,75,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        AMBER_A200: number;

        /**
         * <p style="background-color: #FFC400">#FFC400</p>
         * <p>Name (en): <b>Amber (A400)</b></p>
         * <p>Name (zh): <b>琥珀 (A400)</b></p>
         * <p>Hex: <b>#FFC400</b></p>
         * <p>RGB: <b>255,196,0</b></p>
         * <p>HSV: <b>46,100,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        AMBER_A400: number;

        /**
         * <p style="background-color: #FFAB00">#FFAB00</p>
         * <p>Name (en): <b>Amber (A700)</b></p>
         * <p>Name (zh): <b>琥珀 (A700)</b></p>
         * <p>Hex: <b>#FFAB00</b></p>
         * <p>RGB: <b>255,171,0</b></p>
         * <p>HSV: <b>40,100,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        AMBER_A700: number;

        /**
         * <p style="background-color: #9966CC">#9966CC</p>
         * <p>Name (en): <b>Amethyst</b></p>
         * <p>Name (zh): <b>紫水晶</b></p>
         * <p>Hex: <b>#9966CC</b></p>
         * <p>RGB: <b>153,102,204</b></p>
         * <p>HSV: <b>270,50,80</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        AMETHYST: number;

        /**
         * <p style="background-color: #FAEBD7">#FAEBD7</p>
         * <p>Name (en): <b>Antique White</b></p>
         * <p>Name (zh): <b>古董白</b></p>
         * <p>Hex: <b>#FAEBD7</b></p>
         * <p>RGB: <b>250,235,215</b></p>
         * <p>HSV: <b>34,14,98</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        ANTIQUE_WHITE: number;

        /**
         * <p style="background-color: #8CE600">#8CE600</p>
         * <p>Name (en): <b>Apple Green</b></p>
         * <p>Name (zh): <b>苹果绿</b></p>
         * <p>Hex: <b>#8CE600</b></p>
         * <p>RGB: <b>140,230,0</b></p>
         * <p>HSV: <b>83,100,90</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        APPLE_GREEN: number;

        /**
         * <p style="background-color: #E69966">#E69966</p>
         * <p>Name (en): <b>Apricot</b></p>
         * <p>Name (zh): <b>杏黄</b></p>
         * <p>Hex: <b>#E69966</b></p>
         * <p>RGB: <b>230,153,102</b></p>
         * <p>HSV: <b>24,56,90</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        APRICOT: number;

        /**
         * <p style="background-color: #00FFFF">#00FFFF</p>
         * <p>Name (en): <b>Aqua</b></p>
         * <p>Name (zh): <b>青</b></p>
         * <p>Hex: <b>#00FFFF</b></p>
         * <p>RGB: <b>0,255,255</b></p>
         * <p>HSV: <b>180,100,100</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        AQUA: number;

        /**
         * <p style="background-color: #7FFFD4">#7FFFD4</p>
         * <p>Name (en): <b>Aquamarine</b></p>
         * <p>Name (zh): <b>蓝绿 / 碧蓝</b></p>
         * <p>Hex: <b>#7FFFD4</b></p>
         * <p>RGB: <b>127,255,212</b></p>
         * <p>HSV: <b>160,50,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        AQUAMARINE: number;

        /**
         * <p style="background-color: #66FFE6">#66FFE6</p>
         * <p>Name (en): <b>Aqua Blue</b></p>
         * <p>Name (zh): <b>水蓝</b></p>
         * <p>Hex: <b>#66FFE6</b></p>
         * <p>RGB: <b>102,255,230</b></p>
         * <p>HSV: <b>170,60,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        AQUA_BLUE: number;

        /**
         * <p style="background-color: #F0FFFF">#F0FFFF</p>
         * <p>Name (en): <b>Azure</b></p>
         * <p>Name (zh): <b>湛蓝</b></p>
         * <p>Hex: <b>#F0FFFF</b></p>
         * <p>RGB: <b>240,255,255</b></p>
         * <p>HSV: <b>210,100,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        AZURE: number;

        /**
         * <p style="background-color: #89CFF0">#89CFF0</p>
         * <p>Name (en): <b>Baby Blue</b></p>
         * <p>Name (zh): <b>浅蓝</b></p>
         * <p>Hex: <b>#89CFF0</b></p>
         * <p>RGB: <b>137,207,240</b></p>
         * <p>HSV: <b>199,43,94</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        BABY_BLUE: number;

        /**
         * <p style="background-color: #FFD9E6">#FFD9E6</p>
         * <p>Name (en): <b>Baby Pink</b></p>
         * <p>Name (zh): <b>浅粉红</b></p>
         * <p>Hex: <b>#FFD9E6</b></p>
         * <p>RGB: <b>255,217,230</b></p>
         * <p>HSV: <b>339,15,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        BABY_PINK: number;

        /**
         * <p style="background-color: #F5F5DC">#F5F5DC</p>
         * <p>Name (en): <b>Beige</b></p>
         * <p>Name (zh): <b>米黄</b></p>
         * <p>Hex: <b>#F5F5DC</b></p>
         * <p>RGB: <b>245,245,220</b></p>
         * <p>HSV: <b>60,10,96</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        BEIGE: number;

        /**
         * <p style="background-color: #FFE4C4">#FFE4C4</p>
         * <p>Name (en): <b>Bisque</b></p>
         * <p>Name (zh): <b>陶坯黄</b></p>
         * <p>Hex: <b>#FFE4C4</b></p>
         * <p>RGB: <b>255,228,196</b></p>
         * <p>HSV: <b>33,23,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        BISQUE: number;

        /**
         * <p style="background-color: #000000">#000000</p>
         * <p>Name (en): <b>Black</b></p>
         * <p>Name (zh): <b>黑</b></p>
         * <p>Hex: <b>#000000</b></p>
         * <p>RGB: <b>0,0,0</b></p>
         * <p>HSV: <b>0,0,0</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        BLACK: number;

        /**
         * <p style="background-color: #000000">#000000</p>
         * <p>Name (en): <b>Black 1000</b></p>
         * <p>Name (zh): <b>黑</b></p>
         * <p>Hex: <b>#000000</b></p>
         * <p>RGB: <b>0,0,0</b></p>
         * <p>HSV: <b>0,0,0</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLACK_1000: number;

        /**
         * <p style="background-color: #FFEBCD">#FFEBCD</p>
         * <p>Name (en): <b>Blanched Almond</b></p>
         * <p>Name (zh): <b>杏仁白</b></p>
         * <p>Hex: <b>#FFEBCD</b></p>
         * <p>RGB: <b>255,235,205</b></p>
         * <p>HSV: <b>36,20,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        BLANCHED_ALMOND: number;

        /**
         * <p style="background-color: #0000FF">#0000FF</p>
         * <p>Name (en): <b>Blue</b></p>
         * <p>Name (zh): <b>蓝</b></p>
         * <p>Hex: <b>#0000FF</b></p>
         * <p>RGB: <b>0,0,255</b></p>
         * <p>HSV: <b>240,100,100</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        BLUE: number;

        /**
         * <p style="background-color: #E3F2FD">#E3F2FD</p>
         * <p>Name (en): <b>Blue (50)</b></p>
         * <p>Name (zh): <b>蓝 (50)</b></p>
         * <p>Hex: <b>#E3F2FD</b></p>
         * <p>RGB: <b>227,242,253</b></p>
         * <p>HSV: <b>205,10,99</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_50: number;

        /**
         * <p style="background-color: #BBDEFB">#BBDEFB</p>
         * <p>Name (en): <b>Blue (100)</b></p>
         * <p>Name (zh): <b>蓝 (100)</b></p>
         * <p>Hex: <b>#BBDEFB</b></p>
         * <p>RGB: <b>187,222,251</b></p>
         * <p>HSV: <b>207,25,98</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_100: number;

        /**
         * <p style="background-color: #90CAF9">#90CAF9</p>
         * <p>Name (en): <b>Blue (200)</b></p>
         * <p>Name (zh): <b>蓝 (200)</b></p>
         * <p>Hex: <b>#90CAF9</b></p>
         * <p>RGB: <b>144,202,249</b></p>
         * <p>HSV: <b>207,42,98</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_200: number;

        /**
         * <p style="background-color: #64B5F6">#64B5F6</p>
         * <p>Name (en): <b>Blue (300)</b></p>
         * <p>Name (zh): <b>蓝 (300)</b></p>
         * <p>Hex: <b>#64B5F6</b></p>
         * <p>RGB: <b>100,181,246</b></p>
         * <p>HSV: <b>207,59,96</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_300: number;

        /**
         * <p style="background-color: #42A5F5">#42A5F5</p>
         * <p>Name (en): <b>Blue (400)</b></p>
         * <p>Name (zh): <b>蓝 (400)</b></p>
         * <p>Hex: <b>#42A5F5</b></p>
         * <p>RGB: <b>66,165,245</b></p>
         * <p>HSV: <b>207,73,96</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_400: number;

        /**
         * <p style="background-color: #2196F3">#2196F3</p>
         * <p>Name (en): <b>Blue (500)</b></p>
         * <p>Name (zh): <b>蓝 (500)</b></p>
         * <p>Hex: <b>#2196F3</b></p>
         * <p>RGB: <b>33,150,243</b></p>
         * <p>HSV: <b>207,86,95</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_500: number;

        /**
         * <p style="background-color: #1E88E5">#1E88E5</p>
         * <p>Name (en): <b>Blue (600)</b></p>
         * <p>Name (zh): <b>蓝 (600)</b></p>
         * <p>Hex: <b>#1E88E5</b></p>
         * <p>RGB: <b>30,136,229</b></p>
         * <p>HSV: <b>208,87,90</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_600: number;

        /**
         * <p style="background-color: #1976D2">#1976D2</p>
         * <p>Name (en): <b>Blue (700)</b></p>
         * <p>Name (zh): <b>蓝 (700)</b></p>
         * <p>Hex: <b>#1976D2</b></p>
         * <p>RGB: <b>25,118,210</b></p>
         * <p>HSV: <b>210,88,82</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_700: number;

        /**
         * <p style="background-color: #1565C0">#1565C0</p>
         * <p>Name (en): <b>Blue (800)</b></p>
         * <p>Name (zh): <b>蓝 (800)</b></p>
         * <p>Hex: <b>#1565C0</b></p>
         * <p>RGB: <b>21,101,192</b></p>
         * <p>HSV: <b>212,89,75</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_800: number;

        /**
         * <p style="background-color: #0D47A1">#0D47A1</p>
         * <p>Name (en): <b>Blue (900)</b></p>
         * <p>Name (zh): <b>蓝 (900)</b></p>
         * <p>Hex: <b>#0D47A1</b></p>
         * <p>RGB: <b>13,71,161</b></p>
         * <p>HSV: <b>216,92,63</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_900: number;

        /**
         * <p style="background-color: #82B1FF">#82B1FF</p>
         * <p>Name (en): <b>Blue (A100)</b></p>
         * <p>Name (zh): <b>蓝 (A100)</b></p>
         * <p>Hex: <b>#82B1FF</b></p>
         * <p>RGB: <b>130,177,255</b></p>
         * <p>HSV: <b>217,49,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_A100: number;

        /**
         * <p style="background-color: #448AFF">#448AFF</p>
         * <p>Name (en): <b>Blue (A200)</b></p>
         * <p>Name (zh): <b>蓝 (A200)</b></p>
         * <p>Hex: <b>#448AFF</b></p>
         * <p>RGB: <b>68,138,255</b></p>
         * <p>HSV: <b>218,73,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_A200: number;

        /**
         * <p style="background-color: #2979FF">#2979FF</p>
         * <p>Name (en): <b>Blue (A400)</b></p>
         * <p>Name (zh): <b>蓝 (A400)</b></p>
         * <p>Hex: <b>#2979FF</b></p>
         * <p>RGB: <b>41,121,255</b></p>
         * <p>HSV: <b>218,84,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_A400: number;

        /**
         * <p style="background-color: #2962FF">#2962FF</p>
         * <p>Name (en): <b>Blue (A700)</b></p>
         * <p>Name (zh): <b>蓝 (A700)</b></p>
         * <p>Hex: <b>#2962FF</b></p>
         * <p>RGB: <b>41,98,255</b></p>
         * <p>HSV: <b>224,84,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_A700: number;

        /**
         * <p style="background-color: #607D8B">#607D8B</p>
         * <p>Name (en): <b>Blue Grey (500)</b></p>
         * <p>Name (zh): <b>蓝灰 (500)</b></p>
         * <p>Hex: <b>#607D8B</b></p>
         * <p>RGB: <b>96,125,139</b></p>
         * <p>HSV: <b>200,31,55</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_GREY: number;
        BLUE_GRAY: number;

        /**
         * <p style="background-color: #ECEFF1">#ECEFF1</p>
         * <p>Name (en): <b>Blue Grey (50)</b></p>
         * <p>Name (zh): <b>蓝灰 (50)</b></p>
         * <p>Hex: <b>#ECEFF1</b></p>
         * <p>RGB: <b>236,239,241</b></p>
         * <p>HSV: <b>204,2,95</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_GREY_50: number;
        BLUE_GRAY_50: number;

        /**
         * <p style="background-color: #CFD8DC">#CFD8DC</p>
         * <p>Name (en): <b>Blue Grey (100)</b></p>
         * <p>Name (zh): <b>蓝灰 (100)</b></p>
         * <p>Hex: <b>#CFD8DC</b></p>
         * <p>RGB: <b>207,216,220</b></p>
         * <p>HSV: <b>198,6,86</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_GREY_100: number;
        BLUE_GRAY_100: number;

        /**
         * <p style="background-color: #B0BBC5">#B0BBC5</p>
         * <p>Name (en): <b>Blue Grey (200)</b></p>
         * <p>Name (zh): <b>蓝灰 (200)</b></p>
         * <p>Hex: <b>#B0BBC5</b></p>
         * <p>RGB: <b>176,187,197</b></p>
         * <p>HSV: <b>209,11,77</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_GREY_200: number;
        BLUE_GRAY_200: number;

        /**
         * <p style="background-color: #90A4AE">#90A4AE</p>
         * <p>Name (en): <b>Blue Grey (300)</b></p>
         * <p>Name (zh): <b>蓝灰 (300)</b></p>
         * <p>Hex: <b>#90A4AE</b></p>
         * <p>RGB: <b>144,164,174</b></p>
         * <p>HSV: <b>200,17,68</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_GREY_300: number;
        BLUE_GRAY_300: number;

        /**
         * <p style="background-color: #78909C">#78909C</p>
         * <p>Name (en): <b>Blue Grey (400)</b></p>
         * <p>Name (zh): <b>蓝灰 (400)</b></p>
         * <p>Hex: <b>#78909C</b></p>
         * <p>RGB: <b>120,144,156</b></p>
         * <p>HSV: <b>200,23,61</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_GREY_400: number;
        BLUE_GRAY_400: number;

        /**
         * <p style="background-color: #607D8B">#607D8B</p>
         * <p>Name (en): <b>Blue Grey (500)</b></p>
         * <p>Name (zh): <b>蓝灰 (500)</b></p>
         * <p>Hex: <b>#607D8B</b></p>
         * <p>RGB: <b>96,125,139</b></p>
         * <p>HSV: <b>200,31,55</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_GREY_500: number;
        BLUE_GRAY_500: number;

        /**
         * <p style="background-color: #546E7A">#546E7A</p>
         * <p>Name (en): <b>Blue Grey (600)</b></p>
         * <p>Name (zh): <b>蓝灰 (600)</b></p>
         * <p>Hex: <b>#546E7A</b></p>
         * <p>RGB: <b>84,110,122</b></p>
         * <p>HSV: <b>199,31,48</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_GREY_600: number;
        BLUE_GRAY_600: number;

        /**
         * <p style="background-color: #455A64">#455A64</p>
         * <p>Name (en): <b>Blue Grey (700)</b></p>
         * <p>Name (zh): <b>蓝灰 (700)</b></p>
         * <p>Hex: <b>#455A64</b></p>
         * <p>RGB: <b>69,90,100</b></p>
         * <p>HSV: <b>199,31,39</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_GREY_700: number;
        BLUE_GRAY_700: number;

        /**
         * <p style="background-color: #37474F">#37474F</p>
         * <p>Name (en): <b>Blue Grey (800)</b></p>
         * <p>Name (zh): <b>蓝灰 (800)</b></p>
         * <p>Hex: <b>#37474F</b></p>
         * <p>RGB: <b>55,71,79</b></p>
         * <p>HSV: <b>200,30,31</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_GREY_800: number;
        BLUE_GRAY_800: number;

        /**
         * <p style="background-color: #263238">#263238</p>
         * <p>Name (en): <b>Blue Grey (900)</b></p>
         * <p>Name (zh): <b>蓝灰 (900)</b></p>
         * <p>Hex: <b>#263238</b></p>
         * <p>RGB: <b>38,50,56</b></p>
         * <p>HSV: <b>200,32,22</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BLUE_GREY_900: number;
        BLUE_GRAY_900: number;

        /**
         * <p style="background-color: #8A2BE2">#8A2BE2</p>
         * <p>Name (en): <b>Blue Violet</b></p>
         * <p>Name (zh): <b>蓝紫</b></p>
         * <p>Hex: <b>#8A2BE2</b></p>
         * <p>RGB: <b>138,43,226</b></p>
         * <p>HSV: <b>271,81,89</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        BLUE_VIOLET: number;

        /**
         * <p style="background-color: #66FF00">#66FF00</p>
         * <p>Name (en): <b>Bright Green</b></p>
         * <p>Name (zh): <b>黄绿 / 明绿</b></p>
         * <p>Hex: <b>#66FF00</b></p>
         * <p>RGB: <b>102,255,0</b></p>
         * <p>HSV: <b>96,100,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        BRIGHT_GREEN: number;

        /**
         * <p style="background-color: #CD7F32">#CD7F32</p>
         * <p>Name (en): <b>Bronze</b></p>
         * <p>Name (zh): <b>铜</b></p>
         * <p>Hex: <b>#CD7F32</b></p>
         * <p>RGB: <b>184,115,51</b></p>
         * <p>HSV: <b>29,72,72</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        BRONZE: number;

        /**
         * <p style="background-color: #A52A2A">#A52A2A</p>
         * <p>Name (en): <b>Brown</b></p>
         * <p>Name (zh): <b>褐</b></p>
         * <p>Hex: <b>#A52A2A</b></p>
         * <p>RGB: <b>165,42,42</b></p>
         * <p>HSV: <b>0,75,65</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        BROWN: number;

        /**
         * <p style="background-color: #EFEBE9">#EFEBE9</p>
         * <p>Name (en): <b>Brown (50)</b></p>
         * <p>Name (zh): <b>棕 (50)</b></p>
         * <p>Hex: <b>#EFEBE9</b></p>
         * <p>RGB: <b>239,235,233</b></p>
         * <p>HSV: <b>20,3,94</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BROWN_50: number;

        /**
         * <p style="background-color: #D7CCC8">#D7CCC8</p>
         * <p>Name (en): <b>Brown (100)</b></p>
         * <p>Name (zh): <b>棕 (100)</b></p>
         * <p>Hex: <b>#D7CCC8</b></p>
         * <p>RGB: <b>215,204,200</b></p>
         * <p>HSV: <b>16,7,84</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BROWN_100: number;

        /**
         * <p style="background-color: #BCAAA4">#BCAAA4</p>
         * <p>Name (en): <b>Brown (200)</b></p>
         * <p>Name (zh): <b>棕 (200)</b></p>
         * <p>Hex: <b>#BCAAA4</b></p>
         * <p>RGB: <b>188,170,164</b></p>
         * <p>HSV: <b>15,13,74</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BROWN_200: number;

        /**
         * <p style="background-color: #A1887F">#A1887F</p>
         * <p>Name (en): <b>Brown (300)</b></p>
         * <p>Name (zh): <b>棕 (300)</b></p>
         * <p>Hex: <b>#A1887F</b></p>
         * <p>RGB: <b>161,136,127</b></p>
         * <p>HSV: <b>16,21,63</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BROWN_300: number;

        /**
         * <p style="background-color: #8D6E63">#8D6E63</p>
         * <p>Name (en): <b>Brown (400)</b></p>
         * <p>Name (zh): <b>棕 (400)</b></p>
         * <p>Hex: <b>#8D6E63</b></p>
         * <p>RGB: <b>141,110,99</b></p>
         * <p>HSV: <b>16,30,55</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BROWN_400: number;

        /**
         * <p style="background-color: #795548">#795548</p>
         * <p>Name (en): <b>Brown (500)</b></p>
         * <p>Name (zh): <b>棕 (500)</b></p>
         * <p>Hex: <b>#795548</b></p>
         * <p>RGB: <b>121,85,72</b></p>
         * <p>HSV: <b>16,40,47</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BROWN_500: number;

        /**
         * <p style="background-color: #6D4C41">#6D4C41</p>
         * <p>Name (en): <b>Brown (600)</b></p>
         * <p>Name (zh): <b>棕 (600)</b></p>
         * <p>Hex: <b>#6D4C41</b></p>
         * <p>RGB: <b>109,76,65</b></p>
         * <p>HSV: <b>15,40,43</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BROWN_600: number;

        /**
         * <p style="background-color: #5D4037">#5D4037</p>
         * <p>Name (en): <b>Brown (700)</b></p>
         * <p>Name (zh): <b>棕 (700)</b></p>
         * <p>Hex: <b>#5D4037</b></p>
         * <p>RGB: <b>93,64,55</b></p>
         * <p>HSV: <b>14,41,36</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BROWN_700: number;

        /**
         * <p style="background-color: #4E342E">#4E342E</p>
         * <p>Name (en): <b>Brown (800)</b></p>
         * <p>Name (zh): <b>棕 (800)</b></p>
         * <p>Hex: <b>#4E342E</b></p>
         * <p>RGB: <b>78,52,46</b></p>
         * <p>HSV: <b>11,41,31</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BROWN_800: number;

        /**
         * <p style="background-color: #3E2723">#3E2723</p>
         * <p>Name (en): <b>Brown (900)</b></p>
         * <p>Name (zh): <b>棕 (900)</b></p>
         * <p>Hex: <b>#3E2723</b></p>
         * <p>RGB: <b>62,39,35</b></p>
         * <p>HSV: <b>9,44,24</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        BROWN_900: number;

        /**
         * <p style="background-color: #800020">#800020</p>
         * <p>Name (en): <b>Burgundy</b></p>
         * <p>Name (zh): <b>勃艮第酒红</b></p>
         * <p>Hex: <b>#800020</b></p>
         * <p>RGB: <b>128,0,32</b></p>
         * <p>HSV: <b>345,100,50</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        BURGUNDY: number;

        /**
         * <p style="background-color: #DEB887">#DEB887</p>
         * <p>Name (en): <b>Burly Wood</b></p>
         * <p>Name (zh): <b>硬木</b></p>
         * <p>Hex: <b>#DEB887</b></p>
         * <p>RGB: <b>222,184,135</b></p>
         * <p>HSV: <b>34,39,87</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        BURLY_WOOD: number;

        /**
         * <p style="background-color: #CC5500">#CC5500</p>
         * <p>Name (en): <b>Burnt Orange</b></p>
         * <p>Name (zh): <b>燃橙</b></p>
         * <p>Hex: <b>#CC5500</b></p>
         * <p>RGB: <b>204,85,0</b></p>
         * <p>HSV: <b>25,100,80</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        BURNT_ORANGE: number;

        /**
         * <p style="background-color: #5F9EA0">#5F9EA0</p>
         * <p>Name (en): <b>Cadet Blue</b></p>
         * <p>Name (zh): <b>军服蓝</b></p>
         * <p>Hex: <b>#5F9EA0</b></p>
         * <p>RGB: <b>95,158,160</b></p>
         * <p>HSV: <b>182,41,63</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        CADET_BLUE: number;

        /**
         * <p style="background-color: #A16B47">#A16B47</p>
         * <p>Name (en): <b>Camel</b></p>
         * <p>Name (zh): <b>驼</b></p>
         * <p>Hex: <b>#A16B47</b></p>
         * <p>RGB: <b>161,107,71</b></p>
         * <p>HSV: <b>24,56,63</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        CAMEL: number;

        /**
         * <p style="background-color: #E63995">#E63995</p>
         * <p>Name (en): <b>Camellia</b></p>
         * <p>Name (zh): <b>山茶红</b></p>
         * <p>Hex: <b>#E63995</b></p>
         * <p>RGB: <b>230,57,149</b></p>
         * <p>HSV: <b>328,75,90</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        CAMELLIA: number;

        /**
         * <p style="background-color: #FFEF00">#FFEF00</p>
         * <p>Name (en): <b>Canary Yellow</b></p>
         * <p>Name (zh): <b>鲜黄</b></p>
         * <p>Hex: <b>#FFEF00</b></p>
         * <p>RGB: <b>255,239,0</b></p>
         * <p>HSV: <b>56,100,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        CANARY_YELLOW: number;

        /**
         * <p style="background-color: #990036">#990036</p>
         * <p>Name (en): <b>Cardinal Red</b></p>
         * <p>Name (zh): <b>枢机红</b></p>
         * <p>Hex: <b>#990036</b></p>
         * <p>RGB: <b>153,0,54</b></p>
         * <p>HSV: <b>339,100,60</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        CARDINAL_RED: number;

        /**
         * <p style="background-color: #E6005C">#E6005C</p>
         * <p>Name (en): <b>Carmine</b></p>
         * <p>Name (zh): <b>胭脂红</b></p>
         * <p>Hex: <b>#E6005C</b></p>
         * <p>RGB: <b>230,0,92</b></p>
         * <p>HSV: <b>336,100,90</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        CARMINE: number;

        /**
         * <p style="background-color: #ACE1AF">#ACE1AF</p>
         * <p>Name (en): <b>Celadon</b></p>
         * <p>Name (zh): <b>青瓷绿</b></p>
         * <p>Hex: <b>#ACE1AF</b></p>
         * <p>RGB: <b>172,225,175</b></p>
         * <p>HSV: <b>123,24,88</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        CELADON: number;

        /**
         * <p style="background-color: #DE3163">#DE3163</p>
         * <p>Name (en): <b>Cerise</b></p>
         * <p>Name (zh): <b>樱桃红 / 樱桃</b></p>
         * <p>Hex: <b>#DE3163</b></p>
         * <p>RGB: <b>222,49,99</b></p>
         * <p>HSV: <b>343,78,87</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        CERISE: number;

        /**
         * <p style="background-color: #2A52BE">#2A52BE</p>
         * <p>Name (en): <b>Cerulean Blue</b></p>
         * <p>Name (zh): <b>蔚蓝 / 天青蓝</b></p>
         * <p>Hex: <b>#2A52BE</b></p>
         * <p>RGB: <b>42,82,190</b></p>
         * <p>HSV: <b>224,78,75</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        CERULEAN_BLUE: number;

        /**
         * <p style="background-color: #FFFF99">#FFFF99</p>
         * <p>Name (en): <b>Champagne Yellow</b></p>
         * <p>Name (zh): <b>香槟黄</b></p>
         * <p>Hex: <b>#FFFF99</b></p>
         * <p>RGB: <b>255,255,153</b></p>
         * <p>HSV: <b>60,40,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        CHAMPAGNE_YELLOW: number;

        /**
         * <p style="background-color: #7FFF00">#7FFF00</p>
         * <p>Name (en): <b>Chartreuse</b></p>
         * <p>Name (zh): <b>查特酒绿</b></p>
         * <p>Hex: <b>#7FFF00</b></p>
         * <p>RGB: <b>127,255,0</b></p>
         * <p>HSV: <b>90,100,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        CHARTREUSE: number;

        /**
         * <p style="background-color: #D2691E">#D2691E</p>
         * <p>Name (en): <b>Chocolate</b></p>
         * <p>Name (zh): <b>巧克力</b></p>
         * <p>Hex: <b>#D2691E</b></p>
         * <p>RGB: <b>210,105,30</b></p>
         * <p>HSV: <b>25,86,82</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        CHOCOLATE: number;

        /**
         * <p style="background-color: #E6B800">#E6B800</p>
         * <p>Name (en): <b>Chrome Yellow</b></p>
         * <p>Name (zh): <b>铬黄</b></p>
         * <p>Hex: <b>#E6B800</b></p>
         * <p>RGB: <b>230,184,0</b></p>
         * <p>HSV: <b>48,100,90</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        CHROME_YELLOW: number;

        /**
         * <p style="background-color: #CCA3CC">#CCA3CC</p>
         * <p>Name (en): <b>Clematis</b></p>
         * <p>Name (zh): <b>铁线莲紫</b></p>
         * <p>Hex: <b>#CCA3CC</b></p>
         * <p>RGB: <b>204,163,204</b></p>
         * <p>HSV: <b>300,20,80</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        CLEMATIS: number;

        /**
         * <p style="background-color: #0047AB">#0047AB</p>
         * <p>Name (en): <b>Cobalt Blue</b></p>
         * <p>Name (zh): <b>钴蓝</b></p>
         * <p>Hex: <b>#0047AB</b></p>
         * <p>RGB: <b>0,71,171</b></p>
         * <p>HSV: <b>215,100,67</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        COBALT_BLUE: number;

        /**
         * <p style="background-color: #66FF59">#66FF59</p>
         * <p>Name (en): <b>Cobalt Green</b></p>
         * <p>Name (zh): <b>钴绿</b></p>
         * <p>Hex: <b>#66FF59</b></p>
         * <p>RGB: <b>102,255,89</b></p>
         * <p>HSV: <b>115,65,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        COBALT_GREEN: number;

        /**
         * <p style="background-color: #4D1F00">#4D1F00</p>
         * <p>Name (en): <b>Coconut Brown</b></p>
         * <p>Name (zh): <b>椰褐</b></p>
         * <p>Hex: <b>#4D1F00</b></p>
         * <p>RGB: <b>77,31,0</b></p>
         * <p>HSV: <b>24,100,30</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        COCONUT_BROWN: number;

        /**
         * <p style="background-color: #4D3900">#4D3900</p>
         * <p>Name (en): <b>Coffee</b></p>
         * <p>Name (zh): <b>咖啡</b></p>
         * <p>Hex: <b>#4D3900</b></p>
         * <p>RGB: <b>77,57,0</b></p>
         * <p>HSV: <b>44,100,30</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        COFFEE: number;

        /**
         * <p style="background-color: #FF7F50">#FF7F50</p>
         * <p>Name (en): <b>Coral</b></p>
         * <p>Name (zh): <b>珊瑚红</b></p>
         * <p>Hex: <b>#FF7F50</b></p>
         * <p>RGB: <b>255,127,80</b></p>
         * <p>HSV: <b>16,69,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        CORAL: number;

        /**
         * <p style="background-color: #FF80BF">#FF80BF</p>
         * <p>Name (en): <b>Coral Pink</b></p>
         * <p>Name (zh): <b>浅珊瑚红</b></p>
         * <p>Hex: <b>#FF80BF</b></p>
         * <p>RGB: <b>255,128,191</b></p>
         * <p>HSV: <b>330,50,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        CORAL_PINK: number;

        /**
         * <p style="background-color: #6495ED">#6495ED</p>
         * <p>Name (en): <b>Cornflower Blue</b></p>
         * <p>Name (zh): <b>矢车菊蓝</b></p>
         * <p>Hex: <b>#6495ED</b></p>
         * <p>RGB: <b>100,149,237</b></p>
         * <p>HSV: <b>219,58,93</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        CORNFLOWER_BLUE: number;

        /**
         * <p style="background-color: #FFF8DC">#FFF8DC</p>
         * <p>Name (en): <b>Corn Silk</b></p>
         * <p>Name (zh): <b>玉米丝</b></p>
         * <p>Hex: <b>#FFF8DC</b></p>
         * <p>RGB: <b>255,248,220</b></p>
         * <p>HSV: <b>48,14,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        CORN_SILK: number;

        /**
         * <p style="background-color: #FFFDD0">#FFFDD0</p>
         * <p>Name (en): <b>Cream</b></p>
         * <p>Name (zh): <b>奶油</b></p>
         * <p>Hex: <b>#FFFDD0</b></p>
         * <p>RGB: <b>255,253,208</b></p>
         * <p>HSV: <b>57,18,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        CREAM: number;

        /**
         * <p style="background-color: #DC143C">#DC143C</p>
         * <p>Name (en): <b>Crimson</b></p>
         * <p>Name (zh): <b>绯红</b></p>
         * <p>Hex: <b>#DC143C</b></p>
         * <p>RGB: <b>220,20,60</b></p>
         * <p>HSV: <b>348,91,86</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        CRIMSON: number;

        /**
         * <p style="background-color: #00FFFF">#00FFFF</p>
         * <p>Name (en): <b>Cyan</b></p>
         * <p>Name (zh): <b>青</b></p>
         * <p>Hex: <b>#00FFFF</b></p>
         * <p>RGB: <b>0,255,255</b></p>
         * <p>HSV: <b>180,100,100</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        CYAN: number;

        /**
         * <p style="background-color: #E0F7FA">#E0F7FA</p>
         * <p>Name (en): <b>Cyan (50)</b></p>
         * <p>Name (zh): <b>青 (50)</b></p>
         * <p>Hex: <b>#E0F7FA</b></p>
         * <p>RGB: <b>224,247,250</b></p>
         * <p>HSV: <b>187,10,98</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        CYAN_50: number;

        /**
         * <p style="background-color: #B2EBF2">#B2EBF2</p>
         * <p>Name (en): <b>Cyan (100)</b></p>
         * <p>Name (zh): <b>青 (100)</b></p>
         * <p>Hex: <b>#B2EBF2</b></p>
         * <p>RGB: <b>178,235,242</b></p>
         * <p>HSV: <b>187,26,95</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        CYAN_100: number;

        /**
         * <p style="background-color: #80DEEA">#80DEEA</p>
         * <p>Name (en): <b>Cyan (200)</b></p>
         * <p>Name (zh): <b>青 (200)</b></p>
         * <p>Hex: <b>#80DEEA</b></p>
         * <p>RGB: <b>128,222,234</b></p>
         * <p>HSV: <b>187,45,92</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        CYAN_200: number;

        /**
         * <p style="background-color: #4DD0E1">#4DD0E1</p>
         * <p>Name (en): <b>Cyan (300)</b></p>
         * <p>Name (zh): <b>青 (300)</b></p>
         * <p>Hex: <b>#4DD0E1</b></p>
         * <p>RGB: <b>77,208,225</b></p>
         * <p>HSV: <b>187,66,88</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        CYAN_300: number;

        /**
         * <p style="background-color: #26C6DA">#26C6DA</p>
         * <p>Name (en): <b>Cyan (400)</b></p>
         * <p>Name (zh): <b>青 (400)</b></p>
         * <p>Hex: <b>#26C6DA</b></p>
         * <p>RGB: <b>38,198,218</b></p>
         * <p>HSV: <b>187,83,85</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        CYAN_400: number;

        /**
         * <p style="background-color: #00BCD4">#00BCD4</p>
         * <p>Name (en): <b>Cyan (500)</b></p>
         * <p>Name (zh): <b>青 (500)</b></p>
         * <p>Hex: <b>#00BCD4</b></p>
         * <p>RGB: <b>0,188,212</b></p>
         * <p>HSV: <b>187,100,83</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        CYAN_500: number;

        /**
         * <p style="background-color: #00ACC1">#00ACC1</p>
         * <p>Name (en): <b>Cyan (600)</b></p>
         * <p>Name (zh): <b>青 (600)</b></p>
         * <p>Hex: <b>#00ACC1</b></p>
         * <p>RGB: <b>0,172,193</b></p>
         * <p>HSV: <b>187,100,76</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        CYAN_600: number;

        /**
         * <p style="background-color: #0097A7">#0097A7</p>
         * <p>Name (en): <b>Cyan (700)</b></p>
         * <p>Name (zh): <b>青 (700)</b></p>
         * <p>Hex: <b>#0097A7</b></p>
         * <p>RGB: <b>0,151,167</b></p>
         * <p>HSV: <b>186,100,65</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        CYAN_700: number;

        /**
         * <p style="background-color: #00838F">#00838F</p>
         * <p>Name (en): <b>Cyan (800)</b></p>
         * <p>Name (zh): <b>青 (800)</b></p>
         * <p>Hex: <b>#00838F</b></p>
         * <p>RGB: <b>0,131,143</b></p>
         * <p>HSV: <b>185,100,56</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        CYAN_800: number;

        /**
         * <p style="background-color: #006064">#006064</p>
         * <p>Name (en): <b>Cyan (900)</b></p>
         * <p>Name (zh): <b>青 (900)</b></p>
         * <p>Hex: <b>#006064</b></p>
         * <p>RGB: <b>0,96,100</b></p>
         * <p>HSV: <b>182,100,39</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        CYAN_900: number;

        /**
         * <p style="background-color: #84FFFF">#84FFFF</p>
         * <p>Name (en): <b>Cyan (A100)</b></p>
         * <p>Name (zh): <b>青 (A100)</b></p>
         * <p>Hex: <b>#84FFFF</b></p>
         * <p>RGB: <b>132,255,255</b></p>
         * <p>HSV: <b>180,48,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        CYAN_A100: number;

        /**
         * <p style="background-color: #18FFFF">#18FFFF</p>
         * <p>Name (en): <b>Cyan (A200)</b></p>
         * <p>Name (zh): <b>青 (A200)</b></p>
         * <p>Hex: <b>#18FFFF</b></p>
         * <p>RGB: <b>24,255,255</b></p>
         * <p>HSV: <b>180,91,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        CYAN_A200: number;

        /**
         * <p style="background-color: #00E5FF">#00E5FF</p>
         * <p>Name (en): <b>Cyan (A400)</b></p>
         * <p>Name (zh): <b>青 (A400)</b></p>
         * <p>Hex: <b>#00E5FF</b></p>
         * <p>RGB: <b>0,229,255</b></p>
         * <p>HSV: <b>186,100,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        CYAN_A400: number;

        /**
         * <p style="background-color: #00B8D4">#00B8D4</p>
         * <p>Name (en): <b>Cyan (A700)</b></p>
         * <p>Name (zh): <b>青 (A700)</b></p>
         * <p>Hex: <b>#00B8D4</b></p>
         * <p>RGB: <b>0,184,212</b></p>
         * <p>HSV: <b>188,100,83</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        CYAN_A700: number;

        /**
         * <p style="background-color: #0DBF8C">#0DBF8C</p>
         * <p>Name (en): <b>Cyan Blue</b></p>
         * <p>Name (zh): <b>青蓝</b></p>
         * <p>Hex: <b>#0DBF8C</b></p>
         * <p>RGB: <b>13,191,140</b></p>
         * <p>HSV: <b>163,93,75</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        CYAN_BLUE: number;

        /**
         * <p style="background-color: #00008B">#00008B</p>
         * <p>Name (en): <b>Dark Blue</b></p>
         * <p>Name (zh): <b>暗蓝</b></p>
         * <p>Hex: <b>#00008B</b></p>
         * <p>RGB: <b>0,0,139</b></p>
         * <p>HSV: <b>240,100,55</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DARK_BLUE: number;

        /**
         * <p style="background-color: #008B8B">#008B8B</p>
         * <p>Name (en): <b>Dark Cyan</b></p>
         * <p>Name (zh): <b>暗青</b></p>
         * <p>Hex: <b>#008B8B</b></p>
         * <p>RGB: <b>0,139,139</b></p>
         * <p>HSV: <b>180,100,55</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DARK_CYAN: number;

        /**
         * <p style="background-color: #B8860B">#B8860B</p>
         * <p>Name (en): <b>Dark Goldenrod</b></p>
         * <p>Name (zh): <b>暗金菊</b></p>
         * <p>Hex: <b>#B8860B</b></p>
         * <p>RGB: <b>184,134,11</b></p>
         * <p>HSV: <b>43,94,72</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DARK_GOLDENROD: number;

        /**
         * <p style="background-color: #444444">#444444</p>
         * <p>Name (en): <b>Dark Gray</b></p>
         * <p>Name (zh): <b>暗灰</b></p>
         * <p>Hex: <b>#444444</b></p>
         * <p>RGB: <b>68,68,68</b></p>
         * <p>HSV: <b>0,0,27</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        DARK_GRAY: number;

        /**
         * <p style="background-color: #006400">#006400</p>
         * <p>Name (en): <b>Dark Green</b></p>
         * <p>Name (zh): <b>暗绿</b></p>
         * <p>Hex: <b>#006400</b></p>
         * <p>RGB: <b>0,100,0</b></p>
         * <p>HSV: <b>120,100,39</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DARK_GREEN: number;

        /**
         * <p style="background-color: #444444">#444444</p>
         * <p>Name (en): <b>Dark Grey</b></p>
         * <p>Name (zh): <b>暗灰</b></p>
         * <p>Hex: <b>#444444</b></p>
         * <p>RGB: <b>68,68,68</b></p>
         * <p>HSV: <b>0,0,27</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        DARK_GREY: number;

        /**
         * <p style="background-color: #BDB76B">#BDB76B</p>
         * <p>Name (en): <b>Dark Khaki</b></p>
         * <p>Name (zh): <b>暗卡其</b></p>
         * <p>Hex: <b>#BDB76B</b></p>
         * <p>RGB: <b>189,183,107</b></p>
         * <p>HSV: <b>56,43,74</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DARK_KHAKI: number;

        /**
         * <p style="background-color: #8B008B">#8B008B</p>
         * <p>Name (en): <b>Dark Magenta</b></p>
         * <p>Name (zh): <b>暗洋红</b></p>
         * <p>Hex: <b>#8B008B</b></p>
         * <p>RGB: <b>139,0,139</b></p>
         * <p>HSV: <b>300,100,55</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DARK_MAGENTA: number;

        /**
         * <p style="background-color: #24367D">#24367D</p>
         * <p>Name (en): <b>Dark Mineral Blue</b></p>
         * <p>Name (zh): <b>暗矿蓝</b></p>
         * <p>Hex: <b>#24367D</b></p>
         * <p>RGB: <b>36,54,125</b></p>
         * <p>HSV: <b>228,71,49</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        DARK_MINERAL_BLUE: number;

        /**
         * <p style="background-color: #556B2F">#556B2F</p>
         * <p>Name (en): <b>Dark Olive Green</b></p>
         * <p>Name (zh): <b>暗橄榄绿</b></p>
         * <p>Hex: <b>#556B2F</b></p>
         * <p>RGB: <b>85,107,47</b></p>
         * <p>HSV: <b>82,56,42</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DARK_OLIVE_GREEN: number;

        /**
         * <p style="background-color: #FF8C00">#FF8C00</p>
         * <p>Name (en): <b>Dark Orange</b></p>
         * <p>Name (zh): <b>暗橙</b></p>
         * <p>Hex: <b>#FF8C00</b></p>
         * <p>RGB: <b>255,140,0</b></p>
         * <p>HSV: <b>33,100,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DARK_ORANGE: number;

        /**
         * <p style="background-color: #9932CC">#9932CC</p>
         * <p>Name (en): <b>Dark Orchid</b></p>
         * <p>Name (zh): <b>暗兰紫</b></p>
         * <p>Hex: <b>#9932CC</b></p>
         * <p>RGB: <b>153,50,204</b></p>
         * <p>HSV: <b>280,75,80</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DARK_ORCHID: number;

        /**
         * <p style="background-color: #003399">#003399</p>
         * <p>Name (en): <b>Dark Powder Blue</b></p>
         * <p>Name (zh): <b>暗粉蓝</b></p>
         * <p>Hex: <b>#003399</b></p>
         * <p>RGB: <b>0,51,153</b></p>
         * <p>HSV: <b>220,100,60</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        DARK_POWDER_BLUE: number;

        /**
         * <p style="background-color: #8B0000">#8B0000</p>
         * <p>Name (en): <b>Dark Red</b></p>
         * <p>Name (zh): <b>暗红</b></p>
         * <p>Hex: <b>#8B0000</b></p>
         * <p>RGB: <b>139,0,0</b></p>
         * <p>HSV: <b>0,100,55</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DARK_RED: number;

        /**
         * <p style="background-color: #E9967A">#E9967A</p>
         * <p>Name (en): <b>Dark Salmon</b></p>
         * <p>Name (zh): <b>暗鲑红</b></p>
         * <p>Hex: <b>#E9967A</b></p>
         * <p>RGB: <b>233,150,122</b></p>
         * <p>HSV: <b>15,48,91</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DARK_SALMON: number;

        /**
         * <p style="background-color: #8FBC8F">#8FBC8F</p>
         * <p>Name (en): <b>Dark Sea Green</b></p>
         * <p>Name (zh): <b>暗海绿</b></p>
         * <p>Hex: <b>#8FBC8F</b></p>
         * <p>RGB: <b>143,188,143</b></p>
         * <p>HSV: <b>120,24,74</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DARK_SEA_GREEN: number;

        /**
         * <p style="background-color: #483D8B">#483D8B</p>
         * <p>Name (en): <b>Dark Slate Blue</b></p>
         * <p>Name (zh): <b>暗岩蓝</b></p>
         * <p>Hex: <b>#483D8B</b></p>
         * <p>RGB: <b>72,61,139</b></p>
         * <p>HSV: <b>248,56,55</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DARK_SLATE_BLUE: number;

        /**
         * <p style="background-color: #2F4F4F">#2F4F4F</p>
         * <p>Name (en): <b>Dark Slate Gray</b></p>
         * <p>Name (zh): <b>暗岩灰</b></p>
         * <p>Hex: <b>#2F4F4F</b></p>
         * <p>RGB: <b>47,79,79</b></p>
         * <p>HSV: <b>180,41,31</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DARK_SLATE_GRAY: number;

        /**
         * <p style="background-color: #2F4F4F">#2F4F4F</p>
         * <p>Name (en): <b>Dark Slate Grey</b></p>
         * <p>Name (zh): <b>暗岩灰</b></p>
         * <p>Hex: <b>#2F4F4F</b></p>
         * <p>RGB: <b>47,79,79</b></p>
         * <p>HSV: <b>180,41,31</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DARK_SLATE_GREY: number;

        /**
         * <p style="background-color: #00CED1">#00CED1</p>
         * <p>Name (en): <b>Dark Turquoise</b></p>
         * <p>Name (zh): <b>暗绿松石</b></p>
         * <p>Hex: <b>#00CED1</b></p>
         * <p>RGB: <b>0,206,209</b></p>
         * <p>HSV: <b>181,100,82</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DARK_TURQUOISE: number;

        /**
         * <p style="background-color: #9400D3">#9400D3</p>
         * <p>Name (en): <b>Dark Violet</b></p>
         * <p>Name (zh): <b>暗紫</b></p>
         * <p>Hex: <b>#9400D3</b></p>
         * <p>RGB: <b>148,0,211</b></p>
         * <p>HSV: <b>282,100,83</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DARK_VIOLET: number;

        /**
         * <p style="background-color: #FF5722">#FF5722</p>
         * <p>Name (en): <b>Deep Orange</b></p>
         * <p>Name (zh): <b>深橙 (500)</b></p>
         * <p>Hex: <b>#FF5722</b></p>
         * <p>RGB: <b>255,87,34</b></p>
         * <p>HSV: <b>14,87,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_ORANGE: number;

        /**
         * <p style="background-color: #FBE9A7">#FBE9A7</p>
         * <p>Name (en): <b>Deep Orange (50)</b></p>
         * <p>Name (zh): <b>深橙 (50)</b></p>
         * <p>Hex: <b>#FBE9A7</b></p>
         * <p>RGB: <b>251,233,167</b></p>
         * <p>HSV: <b>47,33,98</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_ORANGE_50: number;

        /**
         * <p style="background-color: #FFCCBC">#FFCCBC</p>
         * <p>Name (en): <b>Deep Orange (100)</b></p>
         * <p>Name (zh): <b>深橙 (100)</b></p>
         * <p>Hex: <b>#FFCCBC</b></p>
         * <p>RGB: <b>255,204,188</b></p>
         * <p>HSV: <b>14,26,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_ORANGE_100: number;

        /**
         * <p style="background-color: #FFAB91">#FFAB91</p>
         * <p>Name (en): <b>Deep Orange (200)</b></p>
         * <p>Name (zh): <b>深橙 (200)</b></p>
         * <p>Hex: <b>#FFAB91</b></p>
         * <p>RGB: <b>255,171,145</b></p>
         * <p>HSV: <b>14,43,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_ORANGE_200: number;

        /**
         * <p style="background-color: #FF8A65">#FF8A65</p>
         * <p>Name (en): <b>Deep Orange (300)</b></p>
         * <p>Name (zh): <b>深橙 (300)</b></p>
         * <p>Hex: <b>#FF8A65</b></p>
         * <p>RGB: <b>255,138,101</b></p>
         * <p>HSV: <b>14,60,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_ORANGE_300: number;

        /**
         * <p style="background-color: #FF7043">#FF7043</p>
         * <p>Name (en): <b>Deep Orange (400)</b></p>
         * <p>Name (zh): <b>深橙 (400)</b></p>
         * <p>Hex: <b>#FF7043</b></p>
         * <p>RGB: <b>255,112,67</b></p>
         * <p>HSV: <b>14,74,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_ORANGE_400: number;

        /**
         * <p style="background-color: #FF5722">#FF5722</p>
         * <p>Name (en): <b>Deep Orange (500)</b></p>
         * <p>Name (zh): <b>深橙 (500)</b></p>
         * <p>Hex: <b>#FF5722</b></p>
         * <p>RGB: <b>255,87,34</b></p>
         * <p>HSV: <b>14,87,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_ORANGE_500: number;

        /**
         * <p style="background-color: #F4511E">#F4511E</p>
         * <p>Name (en): <b>Deep Orange (600)</b></p>
         * <p>Name (zh): <b>深橙 (600)</b></p>
         * <p>Hex: <b>#F4511E</b></p>
         * <p>RGB: <b>244,81,30</b></p>
         * <p>HSV: <b>14,88,96</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_ORANGE_600: number;

        /**
         * <p style="background-color: #E64A19">#E64A19</p>
         * <p>Name (en): <b>Deep Orange (700)</b></p>
         * <p>Name (zh): <b>深橙 (700)</b></p>
         * <p>Hex: <b>#E64A19</b></p>
         * <p>RGB: <b>230,74,25</b></p>
         * <p>HSV: <b>14,89,90</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_ORANGE_700: number;

        /**
         * <p style="background-color: #D84315">#D84315</p>
         * <p>Name (en): <b>Deep Orange (800)</b></p>
         * <p>Name (zh): <b>深橙 (800)</b></p>
         * <p>Hex: <b>#D84315</b></p>
         * <p>RGB: <b>216,67,21</b></p>
         * <p>HSV: <b>14,90,85</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_ORANGE_800: number;

        /**
         * <p style="background-color: #BF360C">#BF360C</p>
         * <p>Name (en): <b>Deep Orange (900)</b></p>
         * <p>Name (zh): <b>深橙 (900)</b></p>
         * <p>Hex: <b>#BF360C</b></p>
         * <p>RGB: <b>191,54,12</b></p>
         * <p>HSV: <b>14,94,75</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_ORANGE_900: number;

        /**
         * <p style="background-color: #FF9E80">#FF9E80</p>
         * <p>Name (en): <b>Deep Orange (A100)</b></p>
         * <p>Name (zh): <b>深橙 (A100)</b></p>
         * <p>Hex: <b>#FF9E80</b></p>
         * <p>RGB: <b>255,158,128</b></p>
         * <p>HSV: <b>14,50,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_ORANGE_A100: number;

        /**
         * <p style="background-color: #FF6E40">#FF6E40</p>
         * <p>Name (en): <b>Deep Orange (A200)</b></p>
         * <p>Name (zh): <b>深橙 (A200)</b></p>
         * <p>Hex: <b>#FF6E40</b></p>
         * <p>RGB: <b>255,110,64</b></p>
         * <p>HSV: <b>14,75,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_ORANGE_A200: number;

        /**
         * <p style="background-color: #FF3D00">#FF3D00</p>
         * <p>Name (en): <b>Deep Orange (A400)</b></p>
         * <p>Name (zh): <b>深橙 (A400)</b></p>
         * <p>Hex: <b>#FF3D00</b></p>
         * <p>RGB: <b>255,61,0</b></p>
         * <p>HSV: <b>14,100,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_ORANGE_A400: number;

        /**
         * <p style="background-color: #DD2600">#DD2600</p>
         * <p>Name (en): <b>Deep Orange (A700)</b></p>
         * <p>Name (zh): <b>深橙 (A700)</b></p>
         * <p>Hex: <b>#DD2600</b></p>
         * <p>RGB: <b>221,38,0</b></p>
         * <p>HSV: <b>10,100,87</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_ORANGE_A700: number;

        /**
         * <p style="background-color: #FF1493">#FF1493</p>
         * <p>Name (en): <b>Deep Pink</b></p>
         * <p>Name (zh): <b>深粉红</b></p>
         * <p>Hex: <b>#FF1493</b></p>
         * <p>RGB: <b>255,20,147</b></p>
         * <p>HSV: <b>328,92,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DEEP_PINK: number;

        /**
         * <p style="background-color: #673AB7">#673AB7</p>
         * <p>Name (en): <b>Deep Purple (500)</b></p>
         * <p>Name (zh): <b>深紫 (500)</b></p>
         * <p>Hex: <b>#673AB7</b></p>
         * <p>RGB: <b>103,58,183</b></p>
         * <p>HSV: <b>262,68,72</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_PURPLE: number;

        /**
         * <p style="background-color: #EDE7F6">#EDE7F6</p>
         * <p>Name (en): <b>Deep Purple (50)</b></p>
         * <p>Name (zh): <b>深紫 (50)</b></p>
         * <p>Hex: <b>#EDE7F6</b></p>
         * <p>RGB: <b>237,231,246</b></p>
         * <p>HSV: <b>264,6,96</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_PURPLE_50: number;

        /**
         * <p style="background-color: #D1C4E9">#D1C4E9</p>
         * <p>Name (en): <b>Deep Purple (100)</b></p>
         * <p>Name (zh): <b>深紫 (100)</b></p>
         * <p>Hex: <b>#D1C4E9</b></p>
         * <p>RGB: <b>209,196,233</b></p>
         * <p>HSV: <b>261,16,91</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_PURPLE_100: number;

        /**
         * <p style="background-color: #B39DDB">#B39DDB</p>
         * <p>Name (en): <b>Deep Purple (200)</b></p>
         * <p>Name (zh): <b>深紫 (200)</b></p>
         * <p>Hex: <b>#B39DDB</b></p>
         * <p>RGB: <b>179,157,219</b></p>
         * <p>HSV: <b>261,28,86</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_PURPLE_200: number;

        /**
         * <p style="background-color: #9575CD">#9575CD</p>
         * <p>Name (en): <b>Deep Purple (300)</b></p>
         * <p>Name (zh): <b>深紫 (300)</b></p>
         * <p>Hex: <b>#9575CD</b></p>
         * <p>RGB: <b>149,117,205</b></p>
         * <p>HSV: <b>262,43,80</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_PURPLE_300: number;

        /**
         * <p style="background-color: #7E57C2">#7E57C2</p>
         * <p>Name (en): <b>Deep Purple (400)</b></p>
         * <p>Name (zh): <b>深紫 (400)</b></p>
         * <p>Hex: <b>#7E57C2</b></p>
         * <p>RGB: <b>126,87,194</b></p>
         * <p>HSV: <b>262,55,76</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_PURPLE_400: number;

        /**
         * <p style="background-color: #673AB7">#673AB7</p>
         * <p>Name (en): <b>Deep Purple (500)</b></p>
         * <p>Name (zh): <b>深紫 (500)</b></p>
         * <p>Hex: <b>#673AB7</b></p>
         * <p>RGB: <b>103,58,183</b></p>
         * <p>HSV: <b>262,68,72</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_PURPLE_500: number;

        /**
         * <p style="background-color: #5E35B1">#5E35B1</p>
         * <p>Name (en): <b>Deep Purple (600)</b></p>
         * <p>Name (zh): <b>深紫 (600)</b></p>
         * <p>Hex: <b>#5E35B1</b></p>
         * <p>RGB: <b>94,53,177</b></p>
         * <p>HSV: <b>260,70,69</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_PURPLE_600: number;

        /**
         * <p style="background-color: #512DA8">#512DA8</p>
         * <p>Name (en): <b>Deep Purple (700)</b></p>
         * <p>Name (zh): <b>深紫 (700)</b></p>
         * <p>Hex: <b>#512DA8</b></p>
         * <p>RGB: <b>81,45,168</b></p>
         * <p>HSV: <b>258,73,66</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_PURPLE_700: number;

        /**
         * <p style="background-color: #4527A0">#4527A0</p>
         * <p>Name (en): <b>Deep Purple (800)</b></p>
         * <p>Name (zh): <b>深紫 (800)</b></p>
         * <p>Hex: <b>#4527A0</b></p>
         * <p>RGB: <b>69,39,160</b></p>
         * <p>HSV: <b>255,76,63</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_PURPLE_800: number;

        /**
         * <p style="background-color: #311B92">#311B92</p>
         * <p>Name (en): <b>Deep Purple (900)</b></p>
         * <p>Name (zh): <b>深紫 (900)</b></p>
         * <p>Hex: <b>#311B92</b></p>
         * <p>RGB: <b>49,27,146</b></p>
         * <p>HSV: <b>251,82,57</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_PURPLE_900: number;

        /**
         * <p style="background-color: #B388FF">#B388FF</p>
         * <p>Name (en): <b>Deep Purple (A100)</b></p>
         * <p>Name (zh): <b>深紫 (A100)</b></p>
         * <p>Hex: <b>#B388FF</b></p>
         * <p>RGB: <b>179,136,255</b></p>
         * <p>HSV: <b>262,47,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_PURPLE_A100: number;

        /**
         * <p style="background-color: #7C4DFF">#7C4DFF</p>
         * <p>Name (en): <b>Deep Purple (A200)</b></p>
         * <p>Name (zh): <b>深紫 (A200)</b></p>
         * <p>Hex: <b>#7C4DFF</b></p>
         * <p>RGB: <b>124,77,255</b></p>
         * <p>HSV: <b>256,70,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_PURPLE_A200: number;

        /**
         * <p style="background-color: #651FFF">#651FFF</p>
         * <p>Name (en): <b>Deep Purple (A400)</b></p>
         * <p>Name (zh): <b>深紫 (A400)</b></p>
         * <p>Hex: <b>#651FFF</b></p>
         * <p>RGB: <b>101,31,255</b></p>
         * <p>HSV: <b>259,88,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_PURPLE_A400: number;

        /**
         * <p style="background-color: #6200EA">#6200EA</p>
         * <p>Name (en): <b>Deep Purple (A700)</b></p>
         * <p>Name (zh): <b>深紫 (A700)</b></p>
         * <p>Hex: <b>#6200EA</b></p>
         * <p>RGB: <b>98,0,234</b></p>
         * <p>HSV: <b>265,100,92</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        DEEP_PURPLE_A700: number;

        /**
         * <p style="background-color: #00BFFF">#00BFFF</p>
         * <p>Name (en): <b>Deep Sky Blue</b></p>
         * <p>Name (zh): <b>深天蓝</b></p>
         * <p>Hex: <b>#00BFFF</b></p>
         * <p>RGB: <b>0,191,255</b></p>
         * <p>HSV: <b>195,100,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DEEP_SKY_BLUE: number;

        /**
         * <p style="background-color: #696969">#696969</p>
         * <p>Name (en): <b>Dim Gray</b></p>
         * <p>Name (zh): <b>昏灰</b></p>
         * <p>Hex: <b>#696969</b></p>
         * <p>RGB: <b>105,105,105</b></p>
         * <p>HSV: <b>0,0,41</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DIM_GRAY: number;

        /**
         * <p style="background-color: #696969">#696969</p>
         * <p>Name (en): <b>Dim Grey</b></p>
         * <p>Name (zh): <b>昏灰</b></p>
         * <p>Hex: <b>#696969</b></p>
         * <p>RGB: <b>105,105,105</b></p>
         * <p>HSV: <b>0,0,41</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DIM_GREY: number;

        /**
         * <p style="background-color: #444444">#444444</p>
         * <p>Name (en): <b>Dkgray</b></p>
         * <p>Name (zh): <b>暗灰</b></p>
         * <p>Hex: <b>#444444</b></p>
         * <p>RGB: <b>68,68,68</b></p>
         * <p>HSV: <b>0,0,27</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        DKGRAY: number;

        /**
         * <p style="background-color: #1E90FF">#1E90FF</p>
         * <p>Name (en): <b>Dodger Blue</b></p>
         * <p>Name (zh): <b>道奇蓝</b></p>
         * <p>Hex: <b>#1E90FF</b></p>
         * <p>RGB: <b>30,144,255</b></p>
         * <p>HSV: <b>210,88,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        DODGER_BLUE: number;

        /**
         * <p style="background-color: #50C878">#50C878</p>
         * <p>Name (en): <b>Emerald</b></p>
         * <p>Name (zh): <b>碧绿</b></p>
         * <p>Hex: <b>#50C878</b></p>
         * <p>RGB: <b>80,200,120</b></p>
         * <p>HSV: <b>140,60,78</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        EMERALD: number;

        /**
         * <p style="background-color: #B22222">#B22222</p>
         * <p>Name (en): <b>Fire Brick</b></p>
         * <p>Name (zh): <b>砖红</b></p>
         * <p>Hex: <b>#B22222</b></p>
         * <p>RGB: <b>178,34,34</b></p>
         * <p>HSV: <b>0,81,70</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        FIRE_BRICK: number;

        /**
         * <p style="background-color: #E68AB8">#E68AB8</p>
         * <p>Name (en): <b>Flamingo</b></p>
         * <p>Name (zh): <b>火鹤红</b></p>
         * <p>Hex: <b>#E68AB8</b></p>
         * <p>RGB: <b>230,138,184</b></p>
         * <p>HSV: <b>330,40,90</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        FLAMINGO: number;

        /**
         * <p style="background-color: #FFFAF0">#FFFAF0</p>
         * <p>Name (en): <b>Floral White</b></p>
         * <p>Name (zh): <b>花卉白</b></p>
         * <p>Hex: <b>#FFFAF0</b></p>
         * <p>RGB: <b>255,250,240</b></p>
         * <p>HSV: <b>40,6,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        FLORAL_WHITE: number;

        /**
         * <p style="background-color: #73B839">#73B839</p>
         * <p>Name (en): <b>Foliage Green</b></p>
         * <p>Name (zh): <b>叶绿</b></p>
         * <p>Hex: <b>#73B839</b></p>
         * <p>RGB: <b>115,184,57</b></p>
         * <p>HSV: <b>93,69,72</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        FOLIAGE_GREEN: number;

        /**
         * <p style="background-color: #228B22">#228B22</p>
         * <p>Name (en): <b>Forest Green</b></p>
         * <p>Name (zh): <b>森林绿</b></p>
         * <p>Hex: <b>#228B22</b></p>
         * <p>RGB: <b>34,139,34</b></p>
         * <p>HSV: <b>120,76,55</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        FOREST_GREEN: number;

        /**
         * <p style="background-color: #99FF4D">#99FF4D</p>
         * <p>Name (en): <b>Fresh Leaves</b></p>
         * <p>Name (zh): <b>嫩绿</b></p>
         * <p>Hex: <b>#99FF4D</b></p>
         * <p>RGB: <b>153,255,77</b></p>
         * <p>HSV: <b>94,70,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        FRESH_LEAVES: number;

        /**
         * <p style="background-color: #FF00FF">#FF00FF</p>
         * <p>Name (en): <b>Fuchsia</b></p>
         * <p>Name (zh): <b>品红 / 洋红</b></p>
         * <p>Hex: <b>#FF00FF</b></p>
         * <p>RGB: <b>255,0,255</b></p>
         * <p>HSV: <b>300,100,100</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        FUCHSIA: number;

        /**
         * <p style="background-color: #DCDCDC">#DCDCDC</p>
         * <p>Name (en): <b>Gainsboro</b></p>
         * <p>Name (zh): <b>庚斯博罗灰</b></p>
         * <p>Hex: <b>#DCDCDC</b></p>
         * <p>RGB: <b>220,220,220</b></p>
         * <p>HSV: <b>0,0,86</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        GAINSBORO: number;

        /**
         * <p style="background-color: #F8F8FF">#F8F8FF</p>
         * <p>Name (en): <b>Ghost White</b></p>
         * <p>Name (zh): <b>幽灵白</b></p>
         * <p>Hex: <b>#F8F8FF</b></p>
         * <p>RGB: <b>248,248,255</b></p>
         * <p>HSV: <b>240,3,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        GHOST_WHITE: number;

        /**
         * <p style="background-color: #FFD700">#FFD700</p>
         * <p>Name (en): <b>Gold</b></p>
         * <p>Name (zh): <b>金</b></p>
         * <p>Hex: <b>#FFD700</b></p>
         * <p>RGB: <b>255,215,0</b></p>
         * <p>HSV: <b>51,100,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        GOLD: number;

        /**
         * <p style="background-color: #FFD700">#FFD700</p>
         * <p>Name (en): <b>Golden</b></p>
         * <p>Name (zh): <b>金</b></p>
         * <p>Hex: <b>#FFD700</b></p>
         * <p>RGB: <b>255,215,0</b></p>
         * <p>HSV: <b>51,100,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        GOLDEN: number;

        /**
         * <p style="background-color: #DAA520">#DAA520</p>
         * <p>Name (en): <b>Goldenrod</b></p>
         * <p>Name (zh): <b>金菊</b></p>
         * <p>Hex: <b>#DAA520</b></p>
         * <p>RGB: <b>218,165,32</b></p>
         * <p>HSV: <b>43,85,85</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        GOLDENROD: number;

        /**
         * <p style="background-color: #99E64D">#99E64D</p>
         * <p>Name (en): <b>Grass Green</b></p>
         * <p>Name (zh): <b>草绿</b></p>
         * <p>Hex: <b>#99E64D</b></p>
         * <p>RGB: <b>153,230,77</b></p>
         * <p>HSV: <b>90,67,90</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        GRASS_GREEN: number;

        /**
         * <p style="background-color: #888888">#888888</p>
         * <p>Name (en): <b>Gray</b></p>
         * <p>Name (zh): <b>灰</b></p>
         * <p>Hex: <b>#888888</b></p>
         * <p>RGB: <b>136,136,136</b></p>
         * <p>HSV: <b>0,0,53</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        GRAY: number;

        /**
         * <p style="background-color: #8674A1">#8674A1</p>
         * <p>Name (en): <b>Grayish Purple</b></p>
         * <p>Name (zh): <b>浅灰紫</b></p>
         * <p>Hex: <b>#8674A1</b></p>
         * <p>RGB: <b>134,116,161</b></p>
         * <p>HSV: <b>264,28,63</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        GRAYISH_PURPLE: number;

        /**
         * <p style="background-color: #00FF00">#00FF00</p>
         * <p>Name (en): <b>Green</b></p>
         * <p>Name (zh): <b>绿</b></p>
         * <p>Hex: <b>#00FF00</b></p>
         * <p>RGB: <b>0,255,0</b></p>
         * <p>HSV: <b>120,100,100</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        GREEN: number;

        /**
         * <p style="background-color: #E8F5E9">#E8F5E9</p>
         * <p>Name (en): <b>Green (50)</b></p>
         * <p>Name (zh): <b>绿 (50)</b></p>
         * <p>Hex: <b>#E8F5E9</b></p>
         * <p>RGB: <b>232,245,233</b></p>
         * <p>HSV: <b>125,5,96</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREEN_50: number;

        /**
         * <p style="background-color: #C8E6C9">#C8E6C9</p>
         * <p>Name (en): <b>Green (100)</b></p>
         * <p>Name (zh): <b>绿 (100)</b></p>
         * <p>Hex: <b>#C8E6C9</b></p>
         * <p>RGB: <b>200,230,201</b></p>
         * <p>HSV: <b>122,13,90</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREEN_100: number;

        /**
         * <p style="background-color: #A5D6A7">#A5D6A7</p>
         * <p>Name (en): <b>Green (200)</b></p>
         * <p>Name (zh): <b>绿 (200)</b></p>
         * <p>Hex: <b>#A5D6A7</b></p>
         * <p>RGB: <b>165,214,167</b></p>
         * <p>HSV: <b>122,23,84</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREEN_200: number;

        /**
         * <p style="background-color: #81C784">#81C784</p>
         * <p>Name (en): <b>Green (300)</b></p>
         * <p>Name (zh): <b>绿 (300)</b></p>
         * <p>Hex: <b>#81C784</b></p>
         * <p>RGB: <b>129,199,132</b></p>
         * <p>HSV: <b>123,35,78</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREEN_300: number;

        /**
         * <p style="background-color: #66BB6A">#66BB6A</p>
         * <p>Name (en): <b>Green (400)</b></p>
         * <p>Name (zh): <b>绿 (400)</b></p>
         * <p>Hex: <b>#66BB6A</b></p>
         * <p>RGB: <b>102,187,106</b></p>
         * <p>HSV: <b>123,45,73</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREEN_400: number;

        /**
         * <p style="background-color: #4CAF50">#4CAF50</p>
         * <p>Name (en): <b>Green (500)</b></p>
         * <p>Name (zh): <b>绿 (500)</b></p>
         * <p>Hex: <b>#4CAF50</b></p>
         * <p>RGB: <b>76,175,80</b></p>
         * <p>HSV: <b>122,57,69</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREEN_500: number;

        /**
         * <p style="background-color: #43A047">#43A047</p>
         * <p>Name (en): <b>Green (600)</b></p>
         * <p>Name (zh): <b>绿 (600)</b></p>
         * <p>Hex: <b>#43A047</b></p>
         * <p>RGB: <b>67,160,71</b></p>
         * <p>HSV: <b>123,58,63</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREEN_600: number;

        /**
         * <p style="background-color: #388E3C">#388E3C</p>
         * <p>Name (en): <b>Green (700)</b></p>
         * <p>Name (zh): <b>绿 (700)</b></p>
         * <p>Hex: <b>#388E3C</b></p>
         * <p>RGB: <b>56,142,60</b></p>
         * <p>HSV: <b>123,61,56</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREEN_700: number;

        /**
         * <p style="background-color: #2E7D32">#2E7D32</p>
         * <p>Name (en): <b>Green (800)</b></p>
         * <p>Name (zh): <b>绿 (800)</b></p>
         * <p>Hex: <b>#2E7D32</b></p>
         * <p>RGB: <b>46,125,50</b></p>
         * <p>HSV: <b>123,63,49</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREEN_800: number;

        /**
         * <p style="background-color: #1B5E20">#1B5E20</p>
         * <p>Name (en): <b>Green (900)</b></p>
         * <p>Name (zh): <b>绿 (900)</b></p>
         * <p>Hex: <b>#1B5E20</b></p>
         * <p>RGB: <b>27,94,32</b></p>
         * <p>HSV: <b>124,71,37</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREEN_900: number;

        /**
         * <p style="background-color: #B9F6CA">#B9F6CA</p>
         * <p>Name (en): <b>Green (A100)</b></p>
         * <p>Name (zh): <b>绿 (A100)</b></p>
         * <p>Hex: <b>#B9F6CA</b></p>
         * <p>RGB: <b>185,246,202</b></p>
         * <p>HSV: <b>137,25,96</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREEN_A100: number;

        /**
         * <p style="background-color: #69F0AE">#69F0AE</p>
         * <p>Name (en): <b>Green (A200)</b></p>
         * <p>Name (zh): <b>绿 (A200)</b></p>
         * <p>Hex: <b>#69F0AE</b></p>
         * <p>RGB: <b>105,240,174</b></p>
         * <p>HSV: <b>151,56,94</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREEN_A200: number;

        /**
         * <p style="background-color: #00E676">#00E676</p>
         * <p>Name (en): <b>Green (A400)</b></p>
         * <p>Name (zh): <b>绿 (A400)</b></p>
         * <p>Hex: <b>#00E676</b></p>
         * <p>RGB: <b>0,230,118</b></p>
         * <p>HSV: <b>151,100,90</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREEN_A400: number;

        /**
         * <p style="background-color: #00C853">#00C853</p>
         * <p>Name (en): <b>Green (A700)</b></p>
         * <p>Name (zh): <b>绿 (A700)</b></p>
         * <p>Hex: <b>#00C853</b></p>
         * <p>RGB: <b>0,200,83</b></p>
         * <p>HSV: <b>145,100,78</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREEN_A700: number;

        /**
         * <p style="background-color: #ADFF2F">#ADFF2F</p>
         * <p>Name (en): <b>Green Yellow</b></p>
         * <p>Name (zh): <b>绿黄</b></p>
         * <p>Hex: <b>#ADFF2F</b></p>
         * <p>RGB: <b>173,255,47</b></p>
         * <p>HSV: <b>84,82,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        GREEN_YELLOW: number;

        /**
         * <p style="background-color: #888888">#888888</p>
         * <p>Name (en): <b>Grey</b></p>
         * <p>Name (zh): <b>灰</b></p>
         * <p>Hex: <b>#888888</b></p>
         * <p>RGB: <b>136,136,136</b></p>
         * <p>HSV: <b>0,0,53</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        GREY: number;

        /**
         * <p style="background-color: #FAFAFA">#FAFAFA</p>
         * <p>Name (en): <b>Grey (50)</b></p>
         * <p>Name (zh): <b>灰 (50)</b></p>
         * <p>Hex: <b>#FAFAFA</b></p>
         * <p>RGB: <b>250,250,250</b></p>
         * <p>HSV: <b>0,0,98</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREY_50: number;
        GRAY_50: number;

        /**
         * <p style="background-color: #F5F5F5">#F5F5F5</p>
         * <p>Name (en): <b>Grey (100)</b></p>
         * <p>Name (zh): <b>灰 (100)</b></p>
         * <p>Hex: <b>#F5F5F5</b></p>
         * <p>RGB: <b>245,245,245</b></p>
         * <p>HSV: <b>0,0,96</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREY_100: number;
        GRAY_100: number;

        /**
         * <p style="background-color: #EEEEEE">#EEEEEE</p>
         * <p>Name (en): <b>Grey (200)</b></p>
         * <p>Name (zh): <b>灰 (200)</b></p>
         * <p>Hex: <b>#EEEEEE</b></p>
         * <p>RGB: <b>238,238,238</b></p>
         * <p>HSV: <b>0,0,93</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREY_200: number;
        GRAY_200: number;

        /**
         * <p style="background-color: #E0E0E0">#E0E0E0</p>
         * <p>Name (en): <b>Grey (300)</b></p>
         * <p>Name (zh): <b>灰 (300)</b></p>
         * <p>Hex: <b>#E0E0E0</b></p>
         * <p>RGB: <b>224,224,224</b></p>
         * <p>HSV: <b>0,0,88</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREY_300: number;
        GRAY_300: number;

        /**
         * <p style="background-color: #BDBDBD">#BDBDBD</p>
         * <p>Name (en): <b>Grey (400)</b></p>
         * <p>Name (zh): <b>灰 (400)</b></p>
         * <p>Hex: <b>#BDBDBD</b></p>
         * <p>RGB: <b>189,189,189</b></p>
         * <p>HSV: <b>0,0,74</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREY_400: number;
        GRAY_400: number;

        /**
         * <p style="background-color: #9E9E9E">#9E9E9E</p>
         * <p>Name (en): <b>Grey (500)</b></p>
         * <p>Name (zh): <b>灰 (500)</b></p>
         * <p>Hex: <b>#9E9E9E</b></p>
         * <p>RGB: <b>158,158,158</b></p>
         * <p>HSV: <b>0,0,62</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREY_500: number;
        GRAY_500: number;

        /**
         * <p style="background-color: #757575">#757575</p>
         * <p>Name (en): <b>Grey (600)</b></p>
         * <p>Name (zh): <b>灰 (600)</b></p>
         * <p>Hex: <b>#757575</b></p>
         * <p>RGB: <b>117,117,117</b></p>
         * <p>HSV: <b>0,0,46</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREY_600: number;
        GRAY_600: number;

        /**
         * <p style="background-color: #616161">#616161</p>
         * <p>Name (en): <b>Grey (700)</b></p>
         * <p>Name (zh): <b>灰 (700)</b></p>
         * <p>Hex: <b>#616161</b></p>
         * <p>RGB: <b>97,97,97</b></p>
         * <p>HSV: <b>0,0,38</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREY_700: number;
        GRAY_700: number;

        /**
         * <p style="background-color: #424242">#424242</p>
         * <p>Name (en): <b>Grey (800)</b></p>
         * <p>Name (zh): <b>灰 (800)</b></p>
         * <p>Hex: <b>#424242</b></p>
         * <p>RGB: <b>66,66,66</b></p>
         * <p>HSV: <b>0,0,26</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREY_800: number;
        GRAY_800: number;

        /**
         * <p style="background-color: #212121">#212121</p>
         * <p>Name (en): <b>Grey (900)</b></p>
         * <p>Name (zh): <b>灰 (900)</b></p>
         * <p>Hex: <b>#212121</b></p>
         * <p>RGB: <b>33,33,33</b></p>
         * <p>HSV: <b>0,0,13</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        GREY_900: number;
        GRAY_900: number;

        /**
         * <p style="background-color: #DF73FF">#DF73FF</p>
         * <p>Name (en): <b>Heliotrope</b></p>
         * <p>Name (zh): <b>缬草紫</b></p>
         * <p>Hex: <b>#DF73FF</b></p>
         * <p>RGB: <b>223,115,255</b></p>
         * <p>HSV: <b>286,55,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        HELIOTROPE: number;

        /**
         * <p style="background-color: #F0FFF0">#F0FFF0</p>
         * <p>Name (en): <b>Honeydew</b></p>
         * <p>Name (zh): <b>蜜瓜绿</b></p>
         * <p>Hex: <b>#F0FFF0</b></p>
         * <p>RGB: <b>240,255,240</b></p>
         * <p>HSV: <b>120,6,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        HONEYDEW: number;

        /**
         * <p style="background-color: #FFB366">#FFB366</p>
         * <p>Name (en): <b>Honey Orange</b></p>
         * <p>Name (zh): <b>蜜橙</b></p>
         * <p>Hex: <b>#FFB366</b></p>
         * <p>RGB: <b>255,179,102</b></p>
         * <p>HSV: <b>30,60,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        HONEY_ORANGE: number;

        /**
         * <p style="background-color: #B8DDC8">#B8DDC8</p>
         * <p>Name (en): <b>Horizon Blue</b></p>
         * <p>Name (zh): <b>苍</b></p>
         * <p>Hex: <b>#B8DDC8</b></p>
         * <p>RGB: <b>184,221,200</b></p>
         * <p>HSV: <b>146,35,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        HORIZON_BLUE: number;

        /**
         * <p style="background-color: #FF69B4">#FF69B4</p>
         * <p>Name (en): <b>Hot Pink</b></p>
         * <p>Name (zh): <b>暖粉红</b></p>
         * <p>Hex: <b>#FF69B4</b></p>
         * <p>RGB: <b>255,105,180</b></p>
         * <p>HSV: <b>330,59,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        HOT_PINK: number;

        /**
         * <p style="background-color: #CD5C5C">#CD5C5C</p>
         * <p>Name (en): <b>Indian Red</b></p>
         * <p>Name (zh): <b>印度红</b></p>
         * <p>Hex: <b>#CD5C5C</b></p>
         * <p>RGB: <b>205,92,92</b></p>
         * <p>HSV: <b>0,55,80</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        INDIAN_RED: number;

        /**
         * <p style="background-color: #4B0082">#4B0082</p>
         * <p>Name (en): <b>Indigo</b></p>
         * <p>Name (zh): <b>靛</b></p>
         * <p>Hex: <b>#4B0082</b></p>
         * <p>RGB: <b>7,0,130</b></p>
         * <p>HSV: <b>275,100,51</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        INDIGO: number;

        /**
         * <p style="background-color: #E8EAF6">#E8EAF6</p>
         * <p>Name (en): <b>Indigo (50)</b></p>
         * <p>Name (zh): <b>靛蓝 (50)</b></p>
         * <p>Hex: <b>#E8EAF6</b></p>
         * <p>RGB: <b>232,234,246</b></p>
         * <p>HSV: <b>231,6,96</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        INDIGO_50: number;

        /**
         * <p style="background-color: #C5CAE9">#C5CAE9</p>
         * <p>Name (en): <b>Indigo (100)</b></p>
         * <p>Name (zh): <b>靛蓝 (100)</b></p>
         * <p>Hex: <b>#C5CAE9</b></p>
         * <p>RGB: <b>197,202,233</b></p>
         * <p>HSV: <b>232,15,91</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        INDIGO_100: number;

        /**
         * <p style="background-color: #9FA8DA">#9FA8DA</p>
         * <p>Name (en): <b>Indigo (200)</b></p>
         * <p>Name (zh): <b>靛蓝 (200)</b></p>
         * <p>Hex: <b>#9FA8DA</b></p>
         * <p>RGB: <b>159,168,218</b></p>
         * <p>HSV: <b>231,27,85</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        INDIGO_200: number;

        /**
         * <p style="background-color: #7986CB">#7986CB</p>
         * <p>Name (en): <b>Indigo (300)</b></p>
         * <p>Name (zh): <b>靛蓝 (300)</b></p>
         * <p>Hex: <b>#7986CB</b></p>
         * <p>RGB: <b>121,134,203</b></p>
         * <p>HSV: <b>230,40,80</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        INDIGO_300: number;

        /**
         * <p style="background-color: #5C6BC0">#5C6BC0</p>
         * <p>Name (en): <b>Indigo (400)</b></p>
         * <p>Name (zh): <b>靛蓝 (400)</b></p>
         * <p>Hex: <b>#5C6BC0</b></p>
         * <p>RGB: <b>92,107,192</b></p>
         * <p>HSV: <b>231,52,75</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        INDIGO_400: number;

        /**
         * <p style="background-color: #3F51B5">#3F51B5</p>
         * <p>Name (en): <b>Indigo (500)</b></p>
         * <p>Name (zh): <b>靛蓝 (500)</b></p>
         * <p>Hex: <b>#3F51B5</b></p>
         * <p>RGB: <b>63,81,181</b></p>
         * <p>HSV: <b>231,65,71</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        INDIGO_500: number;

        /**
         * <p style="background-color: #3949AB">#3949AB</p>
         * <p>Name (en): <b>Indigo (600)</b></p>
         * <p>Name (zh): <b>靛蓝 (600)</b></p>
         * <p>Hex: <b>#3949AB</b></p>
         * <p>RGB: <b>57,73,171</b></p>
         * <p>HSV: <b>232,67,67</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        INDIGO_600: number;

        /**
         * <p style="background-color: #303F9F">#303F9F</p>
         * <p>Name (en): <b>Indigo (700)</b></p>
         * <p>Name (zh): <b>靛蓝 (700)</b></p>
         * <p>Hex: <b>#303F9F</b></p>
         * <p>RGB: <b>48,63,159</b></p>
         * <p>HSV: <b>232,70,62</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        INDIGO_700: number;

        /**
         * <p style="background-color: #283593">#283593</p>
         * <p>Name (en): <b>Indigo (800)</b></p>
         * <p>Name (zh): <b>靛蓝 (800)</b></p>
         * <p>Hex: <b>#283593</b></p>
         * <p>RGB: <b>40,53,147</b></p>
         * <p>HSV: <b>233,73,58</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        INDIGO_800: number;

        /**
         * <p style="background-color: #1A237E">#1A237E</p>
         * <p>Name (en): <b>Indigo (900)</b></p>
         * <p>Name (zh): <b>靛蓝 (900)</b></p>
         * <p>Hex: <b>#1A237E</b></p>
         * <p>RGB: <b>26,35,126</b></p>
         * <p>HSV: <b>235,79,49</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        INDIGO_900: number;

        /**
         * <p style="background-color: #8C9EFF">#8C9EFF</p>
         * <p>Name (en): <b>Indigo (A100)</b></p>
         * <p>Name (zh): <b>靛蓝 (A100)</b></p>
         * <p>Hex: <b>#8C9EFF</b></p>
         * <p>RGB: <b>140,158,255</b></p>
         * <p>HSV: <b>231,45,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        INDIGO_A100: number;

        /**
         * <p style="background-color: #536DFE">#536DFE</p>
         * <p>Name (en): <b>Indigo (A200)</b></p>
         * <p>Name (zh): <b>靛蓝 (A200)</b></p>
         * <p>Hex: <b>#536DFE</b></p>
         * <p>RGB: <b>83,109,254</b></p>
         * <p>HSV: <b>231,67,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        INDIGO_A200: number;

        /**
         * <p style="background-color: #3D5AFE">#3D5AFE</p>
         * <p>Name (en): <b>Indigo (A400)</b></p>
         * <p>Name (zh): <b>靛蓝 (A400)</b></p>
         * <p>Hex: <b>#3D5AFE</b></p>
         * <p>RGB: <b>61,90,254</b></p>
         * <p>HSV: <b>231,76,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        INDIGO_A400: number;

        /**
         * <p style="background-color: #304FFE">#304FFE</p>
         * <p>Name (en): <b>Indigo (A700)</b></p>
         * <p>Name (zh): <b>靛蓝 (A700)</b></p>
         * <p>Hex: <b>#304FFE</b></p>
         * <p>RGB: <b>48,79,254</b></p>
         * <p>HSV: <b>231,81,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        INDIGO_A700: number;

        /**
         * <p style="background-color: #002FA7">#002FA7</p>
         * <p>Name (en): <b>International Klein Blue</b></p>
         * <p>Name (zh): <b>国际奇连蓝</b></p>
         * <p>Hex: <b>#002FA7</b></p>
         * <p>RGB: <b>0,47,167</b></p>
         * <p>HSV: <b>223,100,65</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        INTERNATIONAL_KLEIN_BLUE: number;

        /**
         * <p style="background-color: #625B57">#625B57</p>
         * <p>Name (en): <b>Iron Gray</b></p>
         * <p>Name (zh): <b>铁灰</b></p>
         * <p>Hex: <b>#625B57</b></p>
         * <p>RGB: <b>98,91,87</b></p>
         * <p>HSV: <b>21,12,39</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        IRON_GRAY: number;

        /**
         * <p style="background-color: #FFFFF0">#FFFFF0</p>
         * <p>Name (en): <b>Ivory</b></p>
         * <p>Name (zh): <b>象牙</b></p>
         * <p>Hex: <b>#FFFFF0</b></p>
         * <p>RGB: <b>255,255,240</b></p>
         * <p>HSV: <b>60,6,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        IVORY: number;

        /**
         * <p style="background-color: #36BF36">#36BF36</p>
         * <p>Name (en): <b>Ivy Green</b></p>
         * <p>Name (zh): <b>常春藤绿</b></p>
         * <p>Hex: <b>#36BF36</b></p>
         * <p>RGB: <b>54,191,54</b></p>
         * <p>HSV: <b>120,72,75</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        IVY_GREEN: number;

        /**
         * <p style="background-color: #E6C35C">#E6C35C</p>
         * <p>Name (en): <b>Jasmine</b></p>
         * <p>Name (zh): <b>茉莉黄</b></p>
         * <p>Hex: <b>#E6C35C</b></p>
         * <p>RGB: <b>230,195,92</b></p>
         * <p>HSV: <b>45,60,90</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        JASMINE: number;

        /**
         * <p style="background-color: #F0E68C">#F0E68C</p>
         * <p>Name (en): <b>Khaki</b></p>
         * <p>Name (zh): <b>卡其</b></p>
         * <p>Hex: <b>#F0E68C</b></p>
         * <p>RGB: <b>240,230,140</b></p>
         * <p>HSV: <b>54,42,94</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        KHAKI: number;

        /**
         * <p style="background-color: #26619C">#26619C</p>
         * <p>Name (en): <b>Lapis Lazuli</b></p>
         * <p>Name (zh): <b>天青石蓝</b></p>
         * <p>Hex: <b>#26619C</b></p>
         * <p>RGB: <b>38,97,156</b></p>
         * <p>HSV: <b>210,76,61</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        LAPIS_LAZULI: number;

        /**
         * <p style="background-color: #E6E6FA">#E6E6FA</p>
         * <p>Name (en): <b>Lavender</b></p>
         * <p>Name (zh): <b>薰衣草</b></p>
         * <p>Hex: <b>#E6E6FA</b></p>
         * <p>RGB: <b>230,230,250</b></p>
         * <p>HSV: <b>240,8,98</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LAVENDER: number;

        /**
         * <p style="background-color: #CCCCFF">#CCCCFF</p>
         * <p>Name (en): <b>Lavender Blue</b></p>
         * <p>Name (zh): <b>薰衣草蓝 / 长春花</b></p>
         * <p>Hex: <b>#CCCCFF</b></p>
         * <p>RGB: <b>204,204,255</b></p>
         * <p>HSV: <b>240,20,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        LAVENDER_BLUE: number;

        /**
         * <p style="background-color: #FFF0F5">#FFF0F5</p>
         * <p>Name (en): <b>Lavender Blush</b></p>
         * <p>Name (zh): <b>薰衣草紫红</b></p>
         * <p>Hex: <b>#FFF0F5</b></p>
         * <p>RGB: <b>255,240,245</b></p>
         * <p>HSV: <b>340,6,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LAVENDER_BLUSH: number;

        /**
         * <p style="background-color: #EE82EE">#EE82EE</p>
         * <p>Name (en): <b>Lavender Magenta</b></p>
         * <p>Name (zh): <b>亮紫</b></p>
         * <p>Hex: <b>#EE82EE</b></p>
         * <p>RGB: <b>238,130,238</b></p>
         * <p>HSV: <b>300,45,93</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        LAVENDER_MAGENTA: number;

        /**
         * <p style="background-color: #E6E6FA">#E6E6FA</p>
         * <p>Name (en): <b>Lavender Mist</b></p>
         * <p>Name (zh): <b>薰衣草雾</b></p>
         * <p>Hex: <b>#E6E6FA</b></p>
         * <p>RGB: <b>230,230,250</b></p>
         * <p>HSV: <b>240,8,98</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        LAVENDER_MIST: number;

        /**
         * <p style="background-color: #7CFC00">#7CFC00</p>
         * <p>Name (en): <b>Lawn Green</b></p>
         * <p>Name (zh): <b>草坪绿</b></p>
         * <p>Hex: <b>#7CFC00</b></p>
         * <p>RGB: <b>124,252,0</b></p>
         * <p>HSV: <b>90,100,99</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LAWN_GREEN: number;

        /**
         * <p style="background-color: #FFFACD">#FFFACD</p>
         * <p>Name (en): <b>Lemon Chiffon</b></p>
         * <p>Name (zh): <b>柠檬绸</b></p>
         * <p>Hex: <b>#FFFACD</b></p>
         * <p>RGB: <b>255,250,205</b></p>
         * <p>HSV: <b>54,20,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LEMON_CHIFFON: number;

        /**
         * <p style="background-color: #ADD8E6">#ADD8E6</p>
         * <p>Name (en): <b>Light Blue</b></p>
         * <p>Name (zh): <b>亮蓝</b></p>
         * <p>Hex: <b>#ADD8E6</b></p>
         * <p>RGB: <b>173,216,230</b></p>
         * <p>HSV: <b>195,25,90</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LIGHT_BLUE: number;

        /**
         * <p style="background-color: #E1F5FE">#E1F5FE</p>
         * <p>Name (en): <b>Light Blue (50)</b></p>
         * <p>Name (zh): <b>浅蓝 (50)</b></p>
         * <p>Hex: <b>#E1F5FE</b></p>
         * <p>RGB: <b>225,245,254</b></p>
         * <p>HSV: <b>199,11,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_BLUE_50: number;

        /**
         * <p style="background-color: #B3E5FC">#B3E5FC</p>
         * <p>Name (en): <b>Light Blue (100)</b></p>
         * <p>Name (zh): <b>浅蓝 (100)</b></p>
         * <p>Hex: <b>#B3E5FC</b></p>
         * <p>RGB: <b>179,229,252</b></p>
         * <p>HSV: <b>199,29,99</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_BLUE_100: number;

        /**
         * <p style="background-color: #81D4FA">#81D4FA</p>
         * <p>Name (en): <b>Light Blue (200)</b></p>
         * <p>Name (zh): <b>浅蓝 (200)</b></p>
         * <p>Hex: <b>#81D4FA</b></p>
         * <p>RGB: <b>129,212,250</b></p>
         * <p>HSV: <b>199,48,98</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_BLUE_200: number;

        /**
         * <p style="background-color: #4FC3F7">#4FC3F7</p>
         * <p>Name (en): <b>Light Blue (300)</b></p>
         * <p>Name (zh): <b>浅蓝 (300)</b></p>
         * <p>Hex: <b>#4FC3F7</b></p>
         * <p>RGB: <b>79,195,247</b></p>
         * <p>HSV: <b>199,68,97</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_BLUE_300: number;

        /**
         * <p style="background-color: #29B6FC">#29B6FC</p>
         * <p>Name (en): <b>Light Blue (400)</b></p>
         * <p>Name (zh): <b>浅蓝 (400)</b></p>
         * <p>Hex: <b>#29B6FC</b></p>
         * <p>RGB: <b>41,182,252</b></p>
         * <p>HSV: <b>200,84,99</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_BLUE_400: number;

        /**
         * <p style="background-color: #03A9F4">#03A9F4</p>
         * <p>Name (en): <b>Light Blue (500)</b></p>
         * <p>Name (zh): <b>浅蓝 (500)</b></p>
         * <p>Hex: <b>#03A9F4</b></p>
         * <p>RGB: <b>3,169,244</b></p>
         * <p>HSV: <b>199,99,96</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_BLUE_500: number;

        /**
         * <p style="background-color: #039BE5">#039BE5</p>
         * <p>Name (en): <b>Light Blue (600)</b></p>
         * <p>Name (zh): <b>浅蓝 (600)</b></p>
         * <p>Hex: <b>#039BE5</b></p>
         * <p>RGB: <b>3,155,229</b></p>
         * <p>HSV: <b>200,99,90</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_BLUE_600: number;

        /**
         * <p style="background-color: #0288D1">#0288D1</p>
         * <p>Name (en): <b>Light Blue (700)</b></p>
         * <p>Name (zh): <b>浅蓝 (700)</b></p>
         * <p>Hex: <b>#0288D1</b></p>
         * <p>RGB: <b>2,136,209</b></p>
         * <p>HSV: <b>201,99,82</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_BLUE_700: number;

        /**
         * <p style="background-color: #0277BD">#0277BD</p>
         * <p>Name (en): <b>Light Blue (800)</b></p>
         * <p>Name (zh): <b>浅蓝 (800)</b></p>
         * <p>Hex: <b>#0277BD</b></p>
         * <p>RGB: <b>2,119,189</b></p>
         * <p>HSV: <b>202,99,74</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_BLUE_800: number;

        /**
         * <p style="background-color: #01579B">#01579B</p>
         * <p>Name (en): <b>Light Blue (900)</b></p>
         * <p>Name (zh): <b>浅蓝 (900)</b></p>
         * <p>Hex: <b>#01579B</b></p>
         * <p>RGB: <b>1,87,155</b></p>
         * <p>HSV: <b>206,99,61</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_BLUE_900: number;

        /**
         * <p style="background-color: #80D8FF">#80D8FF</p>
         * <p>Name (en): <b>Light Blue (A100)</b></p>
         * <p>Name (zh): <b>浅蓝 (A100)</b></p>
         * <p>Hex: <b>#80D8FF</b></p>
         * <p>RGB: <b>128,216,255</b></p>
         * <p>HSV: <b>198,50,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_BLUE_A100: number;

        /**
         * <p style="background-color: #40C4FF">#40C4FF</p>
         * <p>Name (en): <b>Light Blue (A200)</b></p>
         * <p>Name (zh): <b>浅蓝 (A200)</b></p>
         * <p>Hex: <b>#40C4FF</b></p>
         * <p>RGB: <b>64,196,255</b></p>
         * <p>HSV: <b>199,75,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_BLUE_A200: number;

        /**
         * <p style="background-color: #00B0FF">#00B0FF</p>
         * <p>Name (en): <b>Light Blue (A400)</b></p>
         * <p>Name (zh): <b>浅蓝 (A400)</b></p>
         * <p>Hex: <b>#00B0FF</b></p>
         * <p>RGB: <b>0,176,255</b></p>
         * <p>HSV: <b>199,100,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_BLUE_A400: number;

        /**
         * <p style="background-color: #0091EA">#0091EA</p>
         * <p>Name (en): <b>Light Blue (A700)</b></p>
         * <p>Name (zh): <b>浅蓝 (A700)</b></p>
         * <p>Hex: <b>#0091EA</b></p>
         * <p>RGB: <b>0,145,234</b></p>
         * <p>HSV: <b>203,100,92</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_BLUE_A700: number;

        /**
         * <p style="background-color: #F08080">#F08080</p>
         * <p>Name (en): <b>Light Coral</b></p>
         * <p>Name (zh): <b>亮珊瑚</b></p>
         * <p>Hex: <b>#F08080</b></p>
         * <p>RGB: <b>240,128,128</b></p>
         * <p>HSV: <b>0,47,94</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LIGHT_CORAL: number;

        /**
         * <p style="background-color: #E0FFFF">#E0FFFF</p>
         * <p>Name (en): <b>Light Cyan</b></p>
         * <p>Name (zh): <b>亮青</b></p>
         * <p>Hex: <b>#E0FFFF</b></p>
         * <p>RGB: <b>224,255,255</b></p>
         * <p>HSV: <b>180,12,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LIGHT_CYAN: number;

        /**
         * <p style="background-color: #FAFAD2">#FAFAD2</p>
         * <p>Name (en): <b>Light Goldenrod Yellow</b></p>
         * <p>Name (zh): <b>亮金菊黄</b></p>
         * <p>Hex: <b>#FAFAD2</b></p>
         * <p>RGB: <b>250,250,210</b></p>
         * <p>HSV: <b>60,16,98</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LIGHT_GOLDENROD_YELLOW: number;

        /**
         * <p style="background-color: #CCCCCC">#CCCCCC</p>
         * <p>Name (en): <b>Light Gray</b></p>
         * <p>Name (zh): <b>亮灰</b></p>
         * <p>Hex: <b>#CCCCCC</b></p>
         * <p>RGB: <b>204,204,204</b></p>
         * <p>HSV: <b>0,0,80</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        LIGHT_GRAY: number;

        /**
         * <p style="background-color: #90EE90">#90EE90</p>
         * <p>Name (en): <b>Light Green</b></p>
         * <p>Name (zh): <b>亮绿</b></p>
         * <p>Hex: <b>#90EE90</b></p>
         * <p>RGB: <b>144,238,144</b></p>
         * <p>HSV: <b>120,39,93</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LIGHT_GREEN: number;

        /**
         * <p style="background-color: #F1F8E9">#F1F8E9</p>
         * <p>Name (en): <b>Light Green (50)</b></p>
         * <p>Name (zh): <b>浅绿 (50)</b></p>
         * <p>Hex: <b>#F1F8E9</b></p>
         * <p>RGB: <b>241,248,233</b></p>
         * <p>HSV: <b>88,6,97</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_GREEN_50: number;

        /**
         * <p style="background-color: #DCEDC8">#DCEDC8</p>
         * <p>Name (en): <b>Light Green (100)</b></p>
         * <p>Name (zh): <b>浅绿 (100)</b></p>
         * <p>Hex: <b>#DCEDC8</b></p>
         * <p>RGB: <b>220,237,200</b></p>
         * <p>HSV: <b>88,16,93</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_GREEN_100: number;

        /**
         * <p style="background-color: #C5E1A5">#C5E1A5</p>
         * <p>Name (en): <b>Light Green (200)</b></p>
         * <p>Name (zh): <b>浅绿 (200)</b></p>
         * <p>Hex: <b>#C5E1A5</b></p>
         * <p>RGB: <b>197,225,165</b></p>
         * <p>HSV: <b>88,27,88</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_GREEN_200: number;

        /**
         * <p style="background-color: #AED581">#AED581</p>
         * <p>Name (en): <b>Light Green (300)</b></p>
         * <p>Name (zh): <b>浅绿 (300)</b></p>
         * <p>Hex: <b>#AED581</b></p>
         * <p>RGB: <b>174,213,129</b></p>
         * <p>HSV: <b>88,39,84</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_GREEN_300: number;

        /**
         * <p style="background-color: #9CCC65">#9CCC65</p>
         * <p>Name (en): <b>Light Green (400)</b></p>
         * <p>Name (zh): <b>浅绿 (400)</b></p>
         * <p>Hex: <b>#9CCC65</b></p>
         * <p>RGB: <b>156,204,101</b></p>
         * <p>HSV: <b>88,50,80</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_GREEN_400: number;

        /**
         * <p style="background-color: #8BC34A">#8BC34A</p>
         * <p>Name (en): <b>Light Green (500)</b></p>
         * <p>Name (zh): <b>浅绿 (500)</b></p>
         * <p>Hex: <b>#8BC34A</b></p>
         * <p>RGB: <b>139,195,74</b></p>
         * <p>HSV: <b>88,62,76</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_GREEN_500: number;

        /**
         * <p style="background-color: #7CB342">#7CB342</p>
         * <p>Name (en): <b>Light Green (600)</b></p>
         * <p>Name (zh): <b>浅绿 (600)</b></p>
         * <p>Hex: <b>#7CB342</b></p>
         * <p>RGB: <b>124,179,66</b></p>
         * <p>HSV: <b>89,63,70</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_GREEN_600: number;

        /**
         * <p style="background-color: #689F38">#689F38</p>
         * <p>Name (en): <b>Light Green (700)</b></p>
         * <p>Name (zh): <b>浅绿 (700)</b></p>
         * <p>Hex: <b>#689F38</b></p>
         * <p>RGB: <b>104,159,56</b></p>
         * <p>HSV: <b>92,65,62</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_GREEN_700: number;

        /**
         * <p style="background-color: #558B2F">#558B2F</p>
         * <p>Name (en): <b>Light Green (800)</b></p>
         * <p>Name (zh): <b>浅绿 (800)</b></p>
         * <p>Hex: <b>#558B2F</b></p>
         * <p>RGB: <b>85,139,47</b></p>
         * <p>HSV: <b>95,66,55</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_GREEN_800: number;

        /**
         * <p style="background-color: #33691E">#33691E</p>
         * <p>Name (en): <b>Light Green (900)</b></p>
         * <p>Name (zh): <b>浅绿 (900)</b></p>
         * <p>Hex: <b>#33691E</b></p>
         * <p>RGB: <b>51,105,30</b></p>
         * <p>HSV: <b>103,71,41</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_GREEN_900: number;

        /**
         * <p style="background-color: #CCFF90">#CCFF90</p>
         * <p>Name (en): <b>Light Green (A100)</b></p>
         * <p>Name (zh): <b>浅绿 (A100)</b></p>
         * <p>Hex: <b>#CCFF90</b></p>
         * <p>RGB: <b>204,255,144</b></p>
         * <p>HSV: <b>88,44,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_GREEN_A100: number;

        /**
         * <p style="background-color: #B2FF59">#B2FF59</p>
         * <p>Name (en): <b>Light Green (A200)</b></p>
         * <p>Name (zh): <b>浅绿 (A200)</b></p>
         * <p>Hex: <b>#B2FF59</b></p>
         * <p>RGB: <b>178,255,89</b></p>
         * <p>HSV: <b>88,65,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_GREEN_A200: number;

        /**
         * <p style="background-color: #76FF03">#76FF03</p>
         * <p>Name (en): <b>Light Green (A400)</b></p>
         * <p>Name (zh): <b>浅绿 (A400)</b></p>
         * <p>Hex: <b>#76FF03</b></p>
         * <p>RGB: <b>118,255,3</b></p>
         * <p>HSV: <b>93,99,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_GREEN_A400: number;

        /**
         * <p style="background-color: #64DD17">#64DD17</p>
         * <p>Name (en): <b>Light Green (A700)</b></p>
         * <p>Name (zh): <b>浅绿 (A700)</b></p>
         * <p>Hex: <b>#64DD17</b></p>
         * <p>RGB: <b>100,221,23</b></p>
         * <p>HSV: <b>97,90,87</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIGHT_GREEN_A700: number;

        /**
         * <p style="background-color: #CCCCCC">#CCCCCC</p>
         * <p>Name (en): <b>Light Grey</b></p>
         * <p>Name (zh): <b>亮灰</b></p>
         * <p>Hex: <b>#CCCCCC</b></p>
         * <p>RGB: <b>204,204,204</b></p>
         * <p>HSV: <b>0,0,80</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        LIGHT_GREY: number;

        /**
         * <p style="background-color: #F0E68C">#F0E68C</p>
         * <p>Name (en): <b>Light Khaki</b></p>
         * <p>Name (zh): <b>亮卡其</b></p>
         * <p>Hex: <b>#F0E68C</b></p>
         * <p>RGB: <b>240,230,140</b></p>
         * <p>HSV: <b>54,42,94</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        LIGHT_KHAKI: number;

        /**
         * <p style="background-color: #CCFF00">#CCFF00</p>
         * <p>Name (en): <b>Light Lime</b></p>
         * <p>Name (zh): <b>柠檬绿 / 亮柠檬绿</b></p>
         * <p>Hex: <b>#CCFF00</b></p>
         * <p>RGB: <b>204,255,0</b></p>
         * <p>HSV: <b>72,100,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        LIGHT_LIME: number;

        /**
         * <p style="background-color: #FFB6C1">#FFB6C1</p>
         * <p>Name (en): <b>Light Pink</b></p>
         * <p>Name (zh): <b>亮粉红</b></p>
         * <p>Hex: <b>#FFB6C1</b></p>
         * <p>RGB: <b>255,182,193</b></p>
         * <p>HSV: <b>351,29,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LIGHT_PINK: number;

        /**
         * <p style="background-color: #FFA07A">#FFA07A</p>
         * <p>Name (en): <b>Light Salmon</b></p>
         * <p>Name (zh): <b>亮鲑红</b></p>
         * <p>Hex: <b>#FFA07A</b></p>
         * <p>RGB: <b>255,160,122</b></p>
         * <p>HSV: <b>17,52,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LIGHT_SALMON: number;

        /**
         * <p style="background-color: #20B2AA">#20B2AA</p>
         * <p>Name (en): <b>Light Sea Green</b></p>
         * <p>Name (zh): <b>亮海绿</b></p>
         * <p>Hex: <b>#20B2AA</b></p>
         * <p>RGB: <b>32,178,170</b></p>
         * <p>HSV: <b>177,82,70</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LIGHT_SEA_GREEN: number;

        /**
         * <p style="background-color: #87CEFA">#87CEFA</p>
         * <p>Name (en): <b>Light Sky Blue</b></p>
         * <p>Name (zh): <b>浅天蓝</b></p>
         * <p>Hex: <b>#87CEFA</b></p>
         * <p>RGB: <b>135,206,250</b></p>
         * <p>HSV: <b>203,46,98</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LIGHT_SKY_BLUE: number;

        /**
         * <p style="background-color: #778899">#778899</p>
         * <p>Name (en): <b>Light Slate Gray</b></p>
         * <p>Name (zh): <b>亮岩灰</b></p>
         * <p>Hex: <b>#778899</b></p>
         * <p>RGB: <b>119,136,153</b></p>
         * <p>HSV: <b>210,22,60</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LIGHT_SLATE_GRAY: number;

        /**
         * <p style="background-color: #778899">#778899</p>
         * <p>Name (en): <b>Light Slate Grey</b></p>
         * <p>Name (zh): <b>亮岩灰</b></p>
         * <p>Hex: <b>#778899</b></p>
         * <p>RGB: <b>119,136,153</b></p>
         * <p>HSV: <b>210,22,60</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LIGHT_SLATE_GREY: number;

        /**
         * <p style="background-color: #B0C4DE">#B0C4DE</p>
         * <p>Name (en): <b>Light Steel Blue</b></p>
         * <p>Name (zh): <b>亮钢蓝</b></p>
         * <p>Hex: <b>#B0C4DE</b></p>
         * <p>RGB: <b>176,196,222</b></p>
         * <p>HSV: <b>214,21,87</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LIGHT_STEEL_BLUE: number;

        /**
         * <p style="background-color: #B09DB9">#B09DB9</p>
         * <p>Name (en): <b>Light Violet</b></p>
         * <p>Name (zh): <b>亮紫</b></p>
         * <p>Hex: <b>#B09DB9</b></p>
         * <p>RGB: <b>176,157,185</b></p>
         * <p>HSV: <b>281,15,73</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        LIGHT_VIOLET: number;

        /**
         * <p style="background-color: #FFFFE0">#FFFFE0</p>
         * <p>Name (en): <b>Light Yellow</b></p>
         * <p>Name (zh): <b>亮黄</b></p>
         * <p>Hex: <b>#FFFFE0</b></p>
         * <p>RGB: <b>255,255,224</b></p>
         * <p>HSV: <b>60,12,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LIGHT_YELLOW: number;

        /**
         * <p style="background-color: #C8A2C8">#C8A2C8</p>
         * <p>Name (en): <b>Lilac</b></p>
         * <p>Name (zh): <b>紫丁香</b></p>
         * <p>Hex: <b>#C8A2C8</b></p>
         * <p>RGB: <b>200,162,200</b></p>
         * <p>HSV: <b>300,19,78</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        LILAC: number;

        /**
         * <p style="background-color: #00FF00">#00FF00</p>
         * <p>Name (en): <b>Lime</b></p>
         * <p>Name (zh): <b>绿</b></p>
         * <p>Hex: <b>#00FF00</b></p>
         * <p>RGB: <b>0,255,0</b></p>
         * <p>HSV: <b>120,100,100</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        LIME: number;

        /**
         * <p style="background-color: #F9FBE7">#F9FBE7</p>
         * <p>Name (en): <b>Lime (50)</b></p>
         * <p>Name (zh): <b>绿黄 (50)</b></p>
         * <p>Hex: <b>#F9FBE7</b></p>
         * <p>RGB: <b>249,251,231</b></p>
         * <p>HSV: <b>66,8,98</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIME_50: number;

        /**
         * <p style="background-color: #F0F4C3">#F0F4C3</p>
         * <p>Name (en): <b>Lime (100)</b></p>
         * <p>Name (zh): <b>绿黄 (100)</b></p>
         * <p>Hex: <b>#F0F4C3</b></p>
         * <p>RGB: <b>240,244,195</b></p>
         * <p>HSV: <b>65,20,96</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIME_100: number;

        /**
         * <p style="background-color: #E6EE9C">#E6EE9C</p>
         * <p>Name (en): <b>Lime (200)</b></p>
         * <p>Name (zh): <b>绿黄 (200)</b></p>
         * <p>Hex: <b>#E6EE9C</b></p>
         * <p>RGB: <b>230,238,156</b></p>
         * <p>HSV: <b>66,34,93</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIME_200: number;

        /**
         * <p style="background-color: #DCE775">#DCE775</p>
         * <p>Name (en): <b>Lime (300)</b></p>
         * <p>Name (zh): <b>绿黄 (300)</b></p>
         * <p>Hex: <b>#DCE775</b></p>
         * <p>RGB: <b>220,231,117</b></p>
         * <p>HSV: <b>66,49,91</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIME_300: number;

        /**
         * <p style="background-color: #D4E157">#D4E157</p>
         * <p>Name (en): <b>Lime (400)</b></p>
         * <p>Name (zh): <b>绿黄 (400)</b></p>
         * <p>Hex: <b>#D4E157</b></p>
         * <p>RGB: <b>212,225,87</b></p>
         * <p>HSV: <b>66,61,88</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIME_400: number;

        /**
         * <p style="background-color: #CDDC39">#CDDC39</p>
         * <p>Name (en): <b>Lime (500)</b></p>
         * <p>Name (zh): <b>绿黄 (500)</b></p>
         * <p>Hex: <b>#CDDC39</b></p>
         * <p>RGB: <b>205,220,57</b></p>
         * <p>HSV: <b>66,74,86</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIME_500: number;

        /**
         * <p style="background-color: #C0CA33">#C0CA33</p>
         * <p>Name (en): <b>Lime (600)</b></p>
         * <p>Name (zh): <b>绿黄 (600)</b></p>
         * <p>Hex: <b>#C0CA33</b></p>
         * <p>RGB: <b>192,202,51</b></p>
         * <p>HSV: <b>64,75,79</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIME_600: number;

        /**
         * <p style="background-color: #A4B42B">#A4B42B</p>
         * <p>Name (en): <b>Lime (700)</b></p>
         * <p>Name (zh): <b>绿黄 (700)</b></p>
         * <p>Hex: <b>#A4B42B</b></p>
         * <p>RGB: <b>164,180,43</b></p>
         * <p>HSV: <b>67,76,71</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIME_700: number;

        /**
         * <p style="background-color: #9E9D24">#9E9D24</p>
         * <p>Name (en): <b>Lime (800)</b></p>
         * <p>Name (zh): <b>绿黄 (800)</b></p>
         * <p>Hex: <b>#9E9D24</b></p>
         * <p>RGB: <b>158,157,36</b></p>
         * <p>HSV: <b>60,77,62</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIME_800: number;

        /**
         * <p style="background-color: #827717">#827717</p>
         * <p>Name (en): <b>Lime (900)</b></p>
         * <p>Name (zh): <b>绿黄 (900)</b></p>
         * <p>Hex: <b>#827717</b></p>
         * <p>RGB: <b>130,119,23</b></p>
         * <p>HSV: <b>54,82,51</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIME_900: number;

        /**
         * <p style="background-color: #F4FF81">#F4FF81</p>
         * <p>Name (en): <b>Lime (A100)</b></p>
         * <p>Name (zh): <b>绿黄 (A100)</b></p>
         * <p>Hex: <b>#F4FF81</b></p>
         * <p>RGB: <b>244,255,129</b></p>
         * <p>HSV: <b>65,49,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIME_A100: number;

        /**
         * <p style="background-color: #EEFF41">#EEFF41</p>
         * <p>Name (en): <b>Lime (A200)</b></p>
         * <p>Name (zh): <b>绿黄 (A200)</b></p>
         * <p>Hex: <b>#EEFF41</b></p>
         * <p>RGB: <b>238,255,65</b></p>
         * <p>HSV: <b>65,75,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIME_A200: number;

        /**
         * <p style="background-color: #C6FF00">#C6FF00</p>
         * <p>Name (en): <b>Lime (A400)</b></p>
         * <p>Name (zh): <b>绿黄 (A400)</b></p>
         * <p>Hex: <b>#C6FF00</b></p>
         * <p>RGB: <b>198,255,0</b></p>
         * <p>HSV: <b>73,100,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIME_A400: number;

        /**
         * <p style="background-color: #AEEA00">#AEEA00</p>
         * <p>Name (en): <b>Lime (A700)</b></p>
         * <p>Name (zh): <b>绿黄 (A700)</b></p>
         * <p>Hex: <b>#AEEA00</b></p>
         * <p>RGB: <b>174,234,0</b></p>
         * <p>HSV: <b>75,100,92</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        LIME_A700: number;

        /**
         * <p style="background-color: #32CD32">#32CD32</p>
         * <p>Name (en): <b>Lime Green</b></p>
         * <p>Name (zh): <b>柠檬绿</b></p>
         * <p>Hex: <b>#32CD32</b></p>
         * <p>RGB: <b>50,205,50</b></p>
         * <p>HSV: <b>120,76,80</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LIME_GREEN: number;

        /**
         * <p style="background-color: #FAF0E6">#FAF0E6</p>
         * <p>Name (en): <b>Linen</b></p>
         * <p>Name (zh): <b>亚麻</b></p>
         * <p>Hex: <b>#FAF0E6</b></p>
         * <p>RGB: <b>250,240,230</b></p>
         * <p>HSV: <b>30,8,98</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        LINEN: number;

        /**
         * <p style="background-color: #CCCCCC">#CCCCCC</p>
         * <p>Name (en): <b>Ltgray</b></p>
         * <p>Name (zh): <b>亮灰</b></p>
         * <p>Hex: <b>#CCCCCC</b></p>
         * <p>RGB: <b>204,204,204</b></p>
         * <p>HSV: <b>0,0,80</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        LTGRAY: number;

        /**
         * <p style="background-color: #FF00FF">#FF00FF</p>
         * <p>Name (en): <b>Magenta</b></p>
         * <p>Name (zh): <b>品红 / 洋红</b></p>
         * <p>Hex: <b>#FF00FF</b></p>
         * <p>RGB: <b>255,0,255</b></p>
         * <p>HSV: <b>300,100,100</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        MAGENTA: number;

        /**
         * <p style="background-color: #FF0DA6">#FF0DA6</p>
         * <p>Name (en): <b>Magenta Rose</b></p>
         * <p>Name (zh): <b>洋玫瑰红</b></p>
         * <p>Hex: <b>#FF0DA6</b></p>
         * <p>RGB: <b>255,13,166</b></p>
         * <p>HSV: <b>322,95,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        MAGENTA_ROSE: number;

        /**
         * <p style="background-color: #22C32E">#22C32E</p>
         * <p>Name (en): <b>Malachite</b></p>
         * <p>Name (zh): <b>孔雀石绿</b></p>
         * <p>Hex: <b>#22C32E</b></p>
         * <p>RGB: <b>34,195,46</b></p>
         * <p>HSV: <b>124,83,76</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        MALACHITE: number;

        /**
         * <p style="background-color: #D94DFF">#D94DFF</p>
         * <p>Name (en): <b>Mallow</b></p>
         * <p>Name (zh): <b>锦葵紫</b></p>
         * <p>Hex: <b>#D94DFF</b></p>
         * <p>RGB: <b>217,77,255</b></p>
         * <p>HSV: <b>287,70,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        MALLOW: number;

        /**
         * <p style="background-color: #FF9900">#FF9900</p>
         * <p>Name (en): <b>Marigold</b></p>
         * <p>Name (zh): <b>万寿菊黄</b></p>
         * <p>Hex: <b>#FF9900</b></p>
         * <p>RGB: <b>255,153,0</b></p>
         * <p>HSV: <b>36,100,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        MARIGOLD: number;

        /**
         * <p style="background-color: #00477D">#00477D</p>
         * <p>Name (en): <b>Marine Blue</b></p>
         * <p>Name (zh): <b>水手蓝</b></p>
         * <p>Hex: <b>#00477D</b></p>
         * <p>RGB: <b>0,71,125</b></p>
         * <p>HSV: <b>206,100,49</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        MARINE_BLUE: number;

        /**
         * <p style="background-color: #800000">#800000</p>
         * <p>Name (en): <b>Maroon</b></p>
         * <p>Name (zh): <b>栗</b></p>
         * <p>Hex: <b>#800000</b></p>
         * <p>RGB: <b>128,0,0</b></p>
         * <p>HSV: <b>0,100,50</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        MAROON: number;

        /**
         * <p style="background-color: #E0B0FF">#E0B0FF</p>
         * <p>Name (en): <b>Mauve</b></p>
         * <p>Name (zh): <b>木槿紫</b></p>
         * <p>Hex: <b>#E0B0FF</b></p>
         * <p>RGB: <b>224,176,255</b></p>
         * <p>HSV: <b>276,31,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        MAUVE: number;

        /**
         * <p style="background-color: #66CDAA">#66CDAA</p>
         * <p>Name (en): <b>Medium Aquamarine</b></p>
         * <p>Name (zh): <b>中碧蓝</b></p>
         * <p>Hex: <b>#66CDAA</b></p>
         * <p>RGB: <b>102,205,170</b></p>
         * <p>HSV: <b>160,50,80</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        MEDIUM_AQUAMARINE: number;

        /**
         * <p style="background-color: #0000CD">#0000CD</p>
         * <p>Name (en): <b>Medium Blue</b></p>
         * <p>Name (zh): <b>中蓝</b></p>
         * <p>Hex: <b>#0000CD</b></p>
         * <p>RGB: <b>0,0,205</b></p>
         * <p>HSV: <b>240,100,80</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        MEDIUM_BLUE: number;

        /**
         * <p style="background-color: #DDA0DD">#DDA0DD</p>
         * <p>Name (en): <b>Medium Lavender Magenta</b></p>
         * <p>Name (zh): <b>梅红</b></p>
         * <p>Hex: <b>#DDA0DD</b></p>
         * <p>RGB: <b>221,160,221</b></p>
         * <p>HSV: <b>300,28,87</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        MEDIUM_LAVENDER_MAGENTA: number;

        /**
         * <p style="background-color: #BA55D3">#BA55D3</p>
         * <p>Name (en): <b>Medium Orchid</b></p>
         * <p>Name (zh): <b>中兰紫</b></p>
         * <p>Hex: <b>#BA55D3</b></p>
         * <p>RGB: <b>186,85,211</b></p>
         * <p>HSV: <b>288,60,83</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        MEDIUM_ORCHID: number;

        /**
         * <p style="background-color: #9370DB">#9370DB</p>
         * <p>Name (en): <b>Medium Purple</b></p>
         * <p>Name (zh): <b>中紫</b></p>
         * <p>Hex: <b>#9370DB</b></p>
         * <p>RGB: <b>147,112,219</b></p>
         * <p>HSV: <b>260,49,86</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        MEDIUM_PURPLE: number;

        /**
         * <p style="background-color: #3CB371">#3CB371</p>
         * <p>Name (en): <b>Medium Sea Green</b></p>
         * <p>Name (zh): <b>中海绿</b></p>
         * <p>Hex: <b>#3CB371</b></p>
         * <p>RGB: <b>60,179,113</b></p>
         * <p>HSV: <b>147,66,70</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        MEDIUM_SEA_GREEN: number;

        /**
         * <p style="background-color: #7B68EE">#7B68EE</p>
         * <p>Name (en): <b>Medium Slate Blue</b></p>
         * <p>Name (zh): <b>中岩蓝</b></p>
         * <p>Hex: <b>#7B68EE</b></p>
         * <p>RGB: <b>123,104,238</b></p>
         * <p>HSV: <b>249,56,93</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        MEDIUM_SLATE_BLUE: number;

        /**
         * <p style="background-color: #00FA9A">#00FA9A</p>
         * <p>Name (en): <b>Medium Spring Green</b></p>
         * <p>Name (zh): <b>中春绿</b></p>
         * <p>Hex: <b>#00FA9A</b></p>
         * <p>RGB: <b>0,250,154</b></p>
         * <p>HSV: <b>157,100,98</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        MEDIUM_SPRING_GREEN: number;

        /**
         * <p style="background-color: #48D1CC">#48D1CC</p>
         * <p>Name (en): <b>Medium Turquoise</b></p>
         * <p>Name (zh): <b>中绿松石</b></p>
         * <p>Hex: <b>#48D1CC</b></p>
         * <p>RGB: <b>72,209,204</b></p>
         * <p>HSV: <b>178,66,82</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        MEDIUM_TURQUOISE: number;

        /**
         * <p style="background-color: #C71585">#C71585</p>
         * <p>Name (en): <b>Medium Violet Red</b></p>
         * <p>Name (zh): <b>中青紫红</b></p>
         * <p>Hex: <b>#C71585</b></p>
         * <p>RGB: <b>199,21,133</b></p>
         * <p>HSV: <b>322,89,78</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        MEDIUM_VIOLET_RED: number;

        /**
         * <p style="background-color: #191970">#191970</p>
         * <p>Name (en): <b>Midnight Blue</b></p>
         * <p>Name (zh): <b>午夜蓝</b></p>
         * <p>Hex: <b>#191970</b></p>
         * <p>RGB: <b>25,25,112</b></p>
         * <p>HSV: <b>240,78,44</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        MIDNIGHT_BLUE: number;

        /**
         * <p style="background-color: #E6D933">#E6D933</p>
         * <p>Name (en): <b>Mimosa</b></p>
         * <p>Name (zh): <b>含羞草黄</b></p>
         * <p>Hex: <b>#E6D933</b></p>
         * <p>RGB: <b>230,217,51</b></p>
         * <p>HSV: <b>56,78,90</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        MIMOSA: number;

        /**
         * <p style="background-color: #004D99">#004D99</p>
         * <p>Name (en): <b>Mineral Blue</b></p>
         * <p>Name (zh): <b>矿蓝</b></p>
         * <p>Hex: <b>#004D99</b></p>
         * <p>RGB: <b>0,77,153</b></p>
         * <p>HSV: <b>210,100,60</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        MINERAL_BLUE: number;

        /**
         * <p style="background-color: #A39DAE">#A39DAE</p>
         * <p>Name (en): <b>Mineral Violet</b></p>
         * <p>Name (zh): <b>矿紫</b></p>
         * <p>Hex: <b>#A39DAE</b></p>
         * <p>RGB: <b>163,157,174</b></p>
         * <p>HSV: <b>261,10,68</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        MINERAL_VIOLET: number;

        /**
         * <p style="background-color: #16982B">#16982B</p>
         * <p>Name (en): <b>Mint</b></p>
         * <p>Name (zh): <b>薄荷绿</b></p>
         * <p>Hex: <b>#16982B</b></p>
         * <p>RGB: <b>22,152,43</b></p>
         * <p>HSV: <b>130,86,60</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        MINT: number;

        /**
         * <p style="background-color: #F5FFFA">#F5FFFA</p>
         * <p>Name (en): <b>Mint Cream</b></p>
         * <p>Name (zh): <b>薄荷奶油</b></p>
         * <p>Hex: <b>#F5FFFA</b></p>
         * <p>RGB: <b>245,255,250</b></p>
         * <p>HSV: <b>150,4,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        MINT_CREAM: number;

        /**
         * <p style="background-color: #FFE4E1">#FFE4E1</p>
         * <p>Name (en): <b>Misty Rose</b></p>
         * <p>Name (zh): <b>雾玫瑰</b></p>
         * <p>Hex: <b>#FFE4E1</b></p>
         * <p>RGB: <b>255,228,225</b></p>
         * <p>HSV: <b>6,12,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        MISTY_ROSE: number;

        /**
         * <p style="background-color: #FFE4B5">#FFE4B5</p>
         * <p>Name (en): <b>Moccasin</b></p>
         * <p>Name (zh): <b>鹿皮鞋</b></p>
         * <p>Hex: <b>#FFE4B5</b></p>
         * <p>RGB: <b>255,228,181</b></p>
         * <p>HSV: <b>38,29,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        MOCCASIN: number;

        /**
         * <p style="background-color: #FFFF4D">#FFFF4D</p>
         * <p>Name (en): <b>Moon Yellow</b></p>
         * <p>Name (zh): <b>月黄</b></p>
         * <p>Hex: <b>#FFFF4D</b></p>
         * <p>RGB: <b>255,255,77</b></p>
         * <p>HSV: <b>60,70,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        MOON_YELLOW: number;

        /**
         * <p style="background-color: #697723">#697723</p>
         * <p>Name (en): <b>Moss Green</b></p>
         * <p>Name (zh): <b>苔藓绿</b></p>
         * <p>Hex: <b>#697723</b></p>
         * <p>RGB: <b>105,119,35</b></p>
         * <p>HSV: <b>70,71,47</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        MOSS_GREEN: number;

        /**
         * <p style="background-color: #CCCC4D">#CCCC4D</p>
         * <p>Name (en): <b>Mustard</b></p>
         * <p>Name (zh): <b>芥末黄</b></p>
         * <p>Hex: <b>#CCCC4D</b></p>
         * <p>RGB: <b>204,204,77</b></p>
         * <p>HSV: <b>60,62,80</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        MUSTARD: number;

        /**
         * <p style="background-color: #FFDEAD">#FFDEAD</p>
         * <p>Name (en): <b>Navajo White</b></p>
         * <p>Name (zh): <b>那瓦霍白</b></p>
         * <p>Hex: <b>#FFDEAD</b></p>
         * <p>RGB: <b>255,222,173</b></p>
         * <p>HSV: <b>36,32,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        NAVAJO_WHITE: number;

        /**
         * <p style="background-color: #000080">#000080</p>
         * <p>Name (en): <b>Navy</b></p>
         * <p>Name (zh): <b>海军蓝 / 藏青</b></p>
         * <p>Hex: <b>#000080</b></p>
         * <p>RGB: <b>0,0,128</b></p>
         * <p>HSV: <b>240,100,50</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        NAVY: number;

        /**
         * <p style="background-color: #000080">#000080</p>
         * <p>Name (en): <b>Navy Blue</b></p>
         * <p>Name (zh): <b>海军蓝 / 藏青</b></p>
         * <p>Hex: <b>#000080</b></p>
         * <p>RGB: <b>0,0,128</b></p>
         * <p>HSV: <b>240,100,50</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        NAVY_BLUE: number;

        /**
         * <p style="background-color: #CC7722">#CC7722</p>
         * <p>Name (en): <b>Ocher</b></p>
         * <p>Name (zh): <b>赭</b></p>
         * <p>Hex: <b>#CC7722</b></p>
         * <p>RGB: <b>204,119,34</b></p>
         * <p>HSV: <b>30,83,80</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        OCHER: number;

        /**
         * <p style="background-color: #FDF5E6">#FDF5E6</p>
         * <p>Name (en): <b>Old Lace</b></p>
         * <p>Name (zh): <b>旧蕾丝</b></p>
         * <p>Hex: <b>#FDF5E6</b></p>
         * <p>RGB: <b>253,245,230</b></p>
         * <p>HSV: <b>39,9,99</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        OLD_LACE: number;

        /**
         * <p style="background-color: #C08081">#C08081</p>
         * <p>Name (en): <b>Old Rose</b></p>
         * <p>Name (zh): <b>陈玫红</b></p>
         * <p>Hex: <b>#C08081</b></p>
         * <p>RGB: <b>192,128,129</b></p>
         * <p>HSV: <b>359,33,75</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        OLD_ROSE: number;

        /**
         * <p style="background-color: #808000">#808000</p>
         * <p>Name (en): <b>Olive</b></p>
         * <p>Name (zh): <b>橄榄</b></p>
         * <p>Hex: <b>#808000</b></p>
         * <p>RGB: <b>128,128,0</b></p>
         * <p>HSV: <b>60,100,50</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        OLIVE: number;

        /**
         * <p style="background-color: #6B8E23">#6B8E23</p>
         * <p>Name (en): <b>Olive Drab</b></p>
         * <p>Name (zh): <b>橄榄军服绿</b></p>
         * <p>Hex: <b>#6B8E23</b></p>
         * <p>RGB: <b>107,142,35</b></p>
         * <p>HSV: <b>80,75,56</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        OLIVE_DRAB: number;

        /**
         * <p style="background-color: #B784A7">#B784A7</p>
         * <p>Name (en): <b>Opera Mauve</b></p>
         * <p>Name (zh): <b>优品紫红</b></p>
         * <p>Hex: <b>#B784A7</b></p>
         * <p>RGB: <b>183,132,167</b></p>
         * <p>HSV: <b>319,28,72</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        OPERA_MAUVE: number;

        /**
         * <p style="background-color: #FFA500">#FFA500</p>
         * <p>Name (en): <b>Orange</b></p>
         * <p>Name (zh): <b>橙</b></p>
         * <p>Hex: <b>#FFA500</b></p>
         * <p>RGB: <b>255,165,0</b></p>
         * <p>HSV: <b>39,100,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        ORANGE: number;

        /**
         * <p style="background-color: #FFF3E0">#FFF3E0</p>
         * <p>Name (en): <b>Orange (50)</b></p>
         * <p>Name (zh): <b>橙 (50)</b></p>
         * <p>Hex: <b>#FFF3E0</b></p>
         * <p>RGB: <b>255,243,224</b></p>
         * <p>HSV: <b>37,12,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        ORANGE_50: number;

        /**
         * <p style="background-color: #FFE0B2">#FFE0B2</p>
         * <p>Name (en): <b>Orange (100)</b></p>
         * <p>Name (zh): <b>橙 (100)</b></p>
         * <p>Hex: <b>#FFE0B2</b></p>
         * <p>RGB: <b>255,224,178</b></p>
         * <p>HSV: <b>36,30,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        ORANGE_100: number;

        /**
         * <p style="background-color: #FFCC80">#FFCC80</p>
         * <p>Name (en): <b>Orange (200)</b></p>
         * <p>Name (zh): <b>橙 (200)</b></p>
         * <p>Hex: <b>#FFCC80</b></p>
         * <p>RGB: <b>255,204,128</b></p>
         * <p>HSV: <b>36,50,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        ORANGE_200: number;

        /**
         * <p style="background-color: #FFB74D">#FFB74D</p>
         * <p>Name (en): <b>Orange (300)</b></p>
         * <p>Name (zh): <b>橙 (300)</b></p>
         * <p>Hex: <b>#FFB74D</b></p>
         * <p>RGB: <b>255,183,77</b></p>
         * <p>HSV: <b>36,70,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        ORANGE_300: number;

        /**
         * <p style="background-color: #FFA726">#FFA726</p>
         * <p>Name (en): <b>Orange (400)</b></p>
         * <p>Name (zh): <b>橙 (400)</b></p>
         * <p>Hex: <b>#FFA726</b></p>
         * <p>RGB: <b>255,167,38</b></p>
         * <p>HSV: <b>36,85,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        ORANGE_400: number;

        /**
         * <p style="background-color: #FF9800">#FF9800</p>
         * <p>Name (en): <b>Orange (500)</b></p>
         * <p>Name (zh): <b>橙 (500)</b></p>
         * <p>Hex: <b>#FF9800</b></p>
         * <p>RGB: <b>255,152,0</b></p>
         * <p>HSV: <b>36,100,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        ORANGE_500: number;

        /**
         * <p style="background-color: #FB8C00">#FB8C00</p>
         * <p>Name (en): <b>Orange (600)</b></p>
         * <p>Name (zh): <b>橙 (600)</b></p>
         * <p>Hex: <b>#FB8C00</b></p>
         * <p>RGB: <b>251,140,0</b></p>
         * <p>HSV: <b>33,100,98</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        ORANGE_600: number;

        /**
         * <p style="background-color: #F57C00">#F57C00</p>
         * <p>Name (en): <b>Orange (700)</b></p>
         * <p>Name (zh): <b>橙 (700)</b></p>
         * <p>Hex: <b>#F57C00</b></p>
         * <p>RGB: <b>245,124,0</b></p>
         * <p>HSV: <b>30,100,96</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        ORANGE_700: number;

        /**
         * <p style="background-color: #EF6C00">#EF6C00</p>
         * <p>Name (en): <b>Orange (800)</b></p>
         * <p>Name (zh): <b>橙 (800)</b></p>
         * <p>Hex: <b>#EF6C00</b></p>
         * <p>RGB: <b>239,108,0</b></p>
         * <p>HSV: <b>27,100,94</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        ORANGE_800: number;

        /**
         * <p style="background-color: #E65100">#E65100</p>
         * <p>Name (en): <b>Orange (900)</b></p>
         * <p>Name (zh): <b>橙 (900)</b></p>
         * <p>Hex: <b>#E65100</b></p>
         * <p>RGB: <b>230,81,0</b></p>
         * <p>HSV: <b>21,100,90</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        ORANGE_900: number;

        /**
         * <p style="background-color: #FFD180">#FFD180</p>
         * <p>Name (en): <b>Orange (A100)</b></p>
         * <p>Name (zh): <b>橙 (A100)</b></p>
         * <p>Hex: <b>#FFD180</b></p>
         * <p>RGB: <b>255,209,128</b></p>
         * <p>HSV: <b>38,50,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        ORANGE_A100: number;

        /**
         * <p style="background-color: #FFAB40">#FFAB40</p>
         * <p>Name (en): <b>Orange (A200)</b></p>
         * <p>Name (zh): <b>橙 (A200)</b></p>
         * <p>Hex: <b>#FFAB40</b></p>
         * <p>RGB: <b>255,171,64</b></p>
         * <p>HSV: <b>34,75,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        ORANGE_A200: number;

        /**
         * <p style="background-color: #FF9100">#FF9100</p>
         * <p>Name (en): <b>Orange (A400)</b></p>
         * <p>Name (zh): <b>橙 (A400)</b></p>
         * <p>Hex: <b>#FF9100</b></p>
         * <p>RGB: <b>255,145,0</b></p>
         * <p>HSV: <b>34,100,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        ORANGE_A400: number;

        /**
         * <p style="background-color: #FF6D00">#FF6D00</p>
         * <p>Name (en): <b>Orange (A700)</b></p>
         * <p>Name (zh): <b>橙 (A700)</b></p>
         * <p>Hex: <b>#FF6D00</b></p>
         * <p>RGB: <b>255,109,0</b></p>
         * <p>HSV: <b>26,100,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        ORANGE_A700: number;

        /**
         * <p style="background-color: #FF4500">#FF4500</p>
         * <p>Name (en): <b>Orange Red</b></p>
         * <p>Name (zh): <b>橙红</b></p>
         * <p>Hex: <b>#FF4500</b></p>
         * <p>RGB: <b>255,69,0</b></p>
         * <p>HSV: <b>16,100,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        ORANGE_RED: number;

        /**
         * <p style="background-color: #DA70D6">#DA70D6</p>
         * <p>Name (en): <b>Orchid</b></p>
         * <p>Name (zh): <b>兰花 / 兰紫</b></p>
         * <p>Hex: <b>#DA70D6</b></p>
         * <p>RGB: <b>218,112,214</b></p>
         * <p>HSV: <b>302,49,85</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        ORCHID: number;

        /**
         * <p style="background-color: #E6CFE6">#E6CFE6</p>
         * <p>Name (en): <b>Pail Lilac</b></p>
         * <p>Name (zh): <b>淡紫丁香</b></p>
         * <p>Hex: <b>#E6CFE6</b></p>
         * <p>RGB: <b>230,207,230</b></p>
         * <p>HSV: <b>300,10,90</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        PAIL_LILAC: number;

        /**
         * <p style="background-color: #D1EDF2">#D1EDF2</p>
         * <p>Name (en): <b>Pale Blue</b></p>
         * <p>Name (zh): <b>灰蓝</b></p>
         * <p>Hex: <b>#D1EDF2</b></p>
         * <p>RGB: <b>209,237,242</b></p>
         * <p>HSV: <b>189,14,95</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        PALE_BLUE: number;

        /**
         * <p style="background-color: #5E86C1">#5E86C1</p>
         * <p>Name (en): <b>Pale Denim</b></p>
         * <p>Name (zh): <b>灰丁宁蓝 / 白牛仔布</b></p>
         * <p>Hex: <b>#5E86C1</b></p>
         * <p>RGB: <b>94,134,193</b></p>
         * <p>HSV: <b>216,51,76</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        PALE_DENIM: number;

        /**
         * <p style="background-color: #EEE8AA">#EEE8AA</p>
         * <p>Name (en): <b>Pale Goldenrod</b></p>
         * <p>Name (zh): <b>灰金菊</b></p>
         * <p>Hex: <b>#EEE8AA</b></p>
         * <p>RGB: <b>238,232,170</b></p>
         * <p>HSV: <b>55,29,93</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        PALE_GOLDENROD: number;

        /**
         * <p style="background-color: #98FB98">#98FB98</p>
         * <p>Name (en): <b>Pale Green</b></p>
         * <p>Name (zh): <b>灰绿</b></p>
         * <p>Hex: <b>#98FB98</b></p>
         * <p>RGB: <b>152,251,152</b></p>
         * <p>HSV: <b>120,39,98</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        PALE_GREEN: number;

        /**
         * <p style="background-color: #CCB38C">#CCB38C</p>
         * <p>Name (en): <b>Pale Ochre</b></p>
         * <p>Name (zh): <b>灰土</b></p>
         * <p>Hex: <b>#CCB38C</b></p>
         * <p>RGB: <b>204,179,140</b></p>
         * <p>HSV: <b>37,31,80</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        PALE_OCHRE: number;

        /**
         * <p style="background-color: #AFEEEE">#AFEEEE</p>
         * <p>Name (en): <b>Pale Turquoise</b></p>
         * <p>Name (zh): <b>灰绿松石</b></p>
         * <p>Hex: <b>#AFEEEE</b></p>
         * <p>RGB: <b>175,238,238</b></p>
         * <p>HSV: <b>180,26,93</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        PALE_TURQUOISE: number;

        /**
         * <p style="background-color: #DB7093">#DB7093</p>
         * <p>Name (en): <b>Pale Violet Red</b></p>
         * <p>Name (zh): <b>灰紫红</b></p>
         * <p>Hex: <b>#DB7093</b></p>
         * <p>RGB: <b>219,112,147</b></p>
         * <p>HSV: <b>340,49,86</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        PALE_VIOLET_RED: number;

        /**
         * <p style="background-color: #7400A1">#7400A1</p>
         * <p>Name (en): <b>Pansy</b></p>
         * <p>Name (zh): <b>三色堇紫</b></p>
         * <p>Hex: <b>#7400A1</b></p>
         * <p>RGB: <b>116,0,161</b></p>
         * <p>HSV: <b>283,100,63</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        PANSY: number;

        /**
         * <p style="background-color: #FFEFD5">#FFEFD5</p>
         * <p>Name (en): <b>Papaya Whip</b></p>
         * <p>Name (zh): <b>蕃木瓜</b></p>
         * <p>Hex: <b>#FFEFD5</b></p>
         * <p>RGB: <b>255,239,213</b></p>
         * <p>HSV: <b>37,16,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        PAPAYA_WHIP: number;

        /**
         * <p style="background-color: #800080">#800080</p>
         * <p>Name (en): <b>Patriarch</b></p>
         * <p>Name (zh): <b>宗主教</b></p>
         * <p>Hex: <b>#800080</b></p>
         * <p>RGB: <b>128,0,128</b></p>
         * <p>HSV: <b>300,100,50</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        PATRIARCH: number;

        /**
         * <p style="background-color: #FFE5B4">#FFE5B4</p>
         * <p>Name (en): <b>Peach</b></p>
         * <p>Name (zh): <b>桃</b></p>
         * <p>Hex: <b>#FFE5B4</b></p>
         * <p>RGB: <b>255,229,180</b></p>
         * <p>HSV: <b>39,29,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        PEACH: number;

        /**
         * <p style="background-color: #FBBEA1">#FBBEA1</p>
         * <p>Name (en): <b>Peach Pearl</b></p>
         * <p>Name (zh): <b>珍珠桃</b></p>
         * <p>Hex: <b>#FBBEA1</b></p>
         * <p>RGB: <b>251,190,161</b></p>
         * <p>HSV: <b>40,29,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        PEACH_PEARL: number;

        /**
         * <p style="background-color: #FFDAB9">#FFDAB9</p>
         * <p>Name (en): <b>Peach Puff</b></p>
         * <p>Name (zh): <b>粉扑桃</b></p>
         * <p>Hex: <b>#FFDAB9</b></p>
         * <p>RGB: <b>255,218,185</b></p>
         * <p>HSV: <b>28,27,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        PEACH_PUFF: number;

        /**
         * <p style="background-color: #00808C">#00808C</p>
         * <p>Name (en): <b>Peacock Blue</b></p>
         * <p>Name (zh): <b>孔雀蓝</b></p>
         * <p>Hex: <b>#00808C</b></p>
         * <p>RGB: <b>0,128,140</b></p>
         * <p>HSV: <b>185,100,55</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        PEACOCK_BLUE: number;

        /**
         * <p style="background-color: #00A15C">#00A15C</p>
         * <p>Name (en): <b>Peacock Green</b></p>
         * <p>Name (zh): <b>孔雀绿</b></p>
         * <p>Hex: <b>#00A15C</b></p>
         * <p>RGB: <b>0,161,92</b></p>
         * <p>HSV: <b>154,100,63</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        PEACOCK_GREEN: number;

        /**
         * <p style="background-color: #FFB3E6">#FFB3E6</p>
         * <p>Name (en): <b>Pearl Pink</b></p>
         * <p>Name (zh): <b>浅珍珠红</b></p>
         * <p>Hex: <b>#FFB3E6</b></p>
         * <p>RGB: <b>255,179,230</b></p>
         * <p>HSV: <b>320,30,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        PEARL_PINK: number;

        /**
         * <p style="background-color: #CCCCFF">#CCCCFF</p>
         * <p>Name (en): <b>Periwinkle</b></p>
         * <p>Name (zh): <b>薰衣草蓝 / 长春花</b></p>
         * <p>Hex: <b>#CCCCFF</b></p>
         * <p>RGB: <b>204,204,255</b></p>
         * <p>HSV: <b>240,20,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        PERIWINKLE: number;

        /**
         * <p style="background-color: #FF4D40">#FF4D40</p>
         * <p>Name (en): <b>Persimmon</b></p>
         * <p>Name (zh): <b>柿子橙</b></p>
         * <p>Hex: <b>#FF4D40</b></p>
         * <p>RGB: <b>255,77,64</b></p>
         * <p>HSV: <b>4,75,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        PERSIMMON: number;

        /**
         * <p style="background-color: #CD853F">#CD853F</p>
         * <p>Name (en): <b>Peru</b></p>
         * <p>Name (zh): <b>秘鲁</b></p>
         * <p>Hex: <b>#CD853F</b></p>
         * <p>RGB: <b>205,133,63</b></p>
         * <p>HSV: <b>30,69,80</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        PERU: number;

        /**
         * <p style="background-color: #FFC0CB">#FFC0CB</p>
         * <p>Name (en): <b>Pink</b></p>
         * <p>Name (zh): <b>粉红</b></p>
         * <p>Hex: <b>#FFC0CB</b></p>
         * <p>RGB: <b>255,192,203</b></p>
         * <p>HSV: <b>350,25,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        PINK: number;

        /**
         * <p style="background-color: #FCE4EC">#FCE4EC</p>
         * <p>Name (en): <b>Pink (50)</b></p>
         * <p>Name (zh): <b>粉红 (50)</b></p>
         * <p>Hex: <b>#FCE4EC</b></p>
         * <p>RGB: <b>252,228,236</b></p>
         * <p>HSV: <b>340,10,99</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PINK_50: number;

        /**
         * <p style="background-color: #F8BBD0">#F8BBD0</p>
         * <p>Name (en): <b>Pink (100)</b></p>
         * <p>Name (zh): <b>粉红 (100)</b></p>
         * <p>Hex: <b>#F8BBD0</b></p>
         * <p>RGB: <b>248,187,208</b></p>
         * <p>HSV: <b>339,25,97</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PINK_100: number;

        /**
         * <p style="background-color: #F48FB1">#F48FB1</p>
         * <p>Name (en): <b>Pink (200)</b></p>
         * <p>Name (zh): <b>粉红 (200)</b></p>
         * <p>Hex: <b>#F48FB1</b></p>
         * <p>RGB: <b>244,143,177</b></p>
         * <p>HSV: <b>340,41,96</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PINK_200: number;

        /**
         * <p style="background-color: #F06292">#F06292</p>
         * <p>Name (en): <b>Pink (300)</b></p>
         * <p>Name (zh): <b>粉红 (300)</b></p>
         * <p>Hex: <b>#F06292</b></p>
         * <p>RGB: <b>240,98,146</b></p>
         * <p>HSV: <b>340,59,94</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PINK_300: number;

        /**
         * <p style="background-color: #EC407A">#EC407A</p>
         * <p>Name (en): <b>Pink (400)</b></p>
         * <p>Name (zh): <b>粉红 (400)</b></p>
         * <p>Hex: <b>#EC407A</b></p>
         * <p>RGB: <b>236,64,122</b></p>
         * <p>HSV: <b>340,73,93</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PINK_400: number;

        /**
         * <p style="background-color: #E91E63">#E91E63</p>
         * <p>Name (en): <b>Pink (500)</b></p>
         * <p>Name (zh): <b>粉红 (500)</b></p>
         * <p>Hex: <b>#E91E63</b></p>
         * <p>RGB: <b>233,30,99</b></p>
         * <p>HSV: <b>340,87,91</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PINK_500: number;

        /**
         * <p style="background-color: #D81B60">#D81B60</p>
         * <p>Name (en): <b>Pink (600)</b></p>
         * <p>Name (zh): <b>粉红 (600)</b></p>
         * <p>Hex: <b>#D81B60</b></p>
         * <p>RGB: <b>216,27,96</b></p>
         * <p>HSV: <b>338,88,85</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PINK_600: number;

        /**
         * <p style="background-color: #C2185B">#C2185B</p>
         * <p>Name (en): <b>Pink (700)</b></p>
         * <p>Name (zh): <b>粉红 (700)</b></p>
         * <p>Hex: <b>#C2185B</b></p>
         * <p>RGB: <b>194,24,91</b></p>
         * <p>HSV: <b>336,88,76</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PINK_700: number;

        /**
         * <p style="background-color: #AD1457">#AD1457</p>
         * <p>Name (en): <b>Pink (800)</b></p>
         * <p>Name (zh): <b>粉红 (800)</b></p>
         * <p>Hex: <b>#AD1457</b></p>
         * <p>RGB: <b>173,20,87</b></p>
         * <p>HSV: <b>334,88,68</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PINK_800: number;

        /**
         * <p style="background-color: #880E4F">#880E4F</p>
         * <p>Name (en): <b>Pink (900)</b></p>
         * <p>Name (zh): <b>粉红 (900)</b></p>
         * <p>Hex: <b>#880E4F</b></p>
         * <p>RGB: <b>136,14,79</b></p>
         * <p>HSV: <b>328,90,53</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PINK_900: number;

        /**
         * <p style="background-color: #FF80AB">#FF80AB</p>
         * <p>Name (en): <b>Pink (A100)</b></p>
         * <p>Name (zh): <b>粉红 (A100)</b></p>
         * <p>Hex: <b>#FF80AB</b></p>
         * <p>RGB: <b>255,128,171</b></p>
         * <p>HSV: <b>340,50,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PINK_A100: number;

        /**
         * <p style="background-color: #FF4081">#FF4081</p>
         * <p>Name (en): <b>Pink (A200)</b></p>
         * <p>Name (zh): <b>粉红 (A200)</b></p>
         * <p>Hex: <b>#FF4081</b></p>
         * <p>RGB: <b>255,64,129</b></p>
         * <p>HSV: <b>340,75,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PINK_A200: number;

        /**
         * <p style="background-color: #F50057">#F50057</p>
         * <p>Name (en): <b>Pink (A400)</b></p>
         * <p>Name (zh): <b>粉红 (A400)</b></p>
         * <p>Hex: <b>#F50057</b></p>
         * <p>RGB: <b>245,0,87</b></p>
         * <p>HSV: <b>339,100,96</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PINK_A400: number;

        /**
         * <p style="background-color: #C51162">#C51162</p>
         * <p>Name (en): <b>Pink (A700)</b></p>
         * <p>Name (zh): <b>粉红 (A700)</b></p>
         * <p>Hex: <b>#C51162</b></p>
         * <p>RGB: <b>197,17,98</b></p>
         * <p>HSV: <b>333,91,77</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PINK_A700: number;

        /**
         * <p style="background-color: #8E4585">#8E4585</p>
         * <p>Name (en): <b>Plum</b></p>
         * <p>Name (zh): <b>梅红</b></p>
         * <p>Hex: <b>#8E4585</b></p>
         * <p>RGB: <b>142,69,133</b></p>
         * <p>HSV: <b>307,51,56</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        PLUM: number;

        /**
         * <p style="background-color: #B0E0E6">#B0E0E6</p>
         * <p>Name (en): <b>Powder Blue</b></p>
         * <p>Name (zh): <b>粉蓝</b></p>
         * <p>Hex: <b>#B0E0E6</b></p>
         * <p>RGB: <b>176,224,230</b></p>
         * <p>HSV: <b>187,23,90</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        POWDER_BLUE: number;

        /**
         * <p style="background-color: #003153">#003153</p>
         * <p>Name (en): <b>Prussian Blue</b></p>
         * <p>Name (zh): <b>普鲁士蓝</b></p>
         * <p>Hex: <b>#003153</b></p>
         * <p>RGB: <b>0,49,83</b></p>
         * <p>HSV: <b>205,100,43</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        PRUSSIAN_BLUE: number;

        /**
         * <p style="background-color: #800080">#800080</p>
         * <p>Name (en): <b>Purple</b></p>
         * <p>Name (zh): <b>紫</b></p>
         * <p>Hex: <b>#800080</b></p>
         * <p>RGB: <b>128,0,128</b></p>
         * <p>HSV: <b>300,100,50</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        PURPLE: number;

        /**
         * <p style="background-color: #F3E5F5">#F3E5F5</p>
         * <p>Name (en): <b>Purple (50)</b></p>
         * <p>Name (zh): <b>紫 (50)</b></p>
         * <p>Hex: <b>#F3E5F5</b></p>
         * <p>RGB: <b>243,229,245</b></p>
         * <p>HSV: <b>293,7,96</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PURPLE_50: number;

        /**
         * <p style="background-color: #E1BEE7">#E1BEE7</p>
         * <p>Name (en): <b>Purple (100)</b></p>
         * <p>Name (zh): <b>紫 (100)</b></p>
         * <p>Hex: <b>#E1BEE7</b></p>
         * <p>RGB: <b>225,190,231</b></p>
         * <p>HSV: <b>291,18,91</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PURPLE_100: number;

        /**
         * <p style="background-color: #CE93D8">#CE93D8</p>
         * <p>Name (en): <b>Purple (200)</b></p>
         * <p>Name (zh): <b>紫 (200)</b></p>
         * <p>Hex: <b>#CE93D8</b></p>
         * <p>RGB: <b>206,147,216</b></p>
         * <p>HSV: <b>291,32,85</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PURPLE_200: number;

        /**
         * <p style="background-color: #BA68C8">#BA68C8</p>
         * <p>Name (en): <b>Purple (300)</b></p>
         * <p>Name (zh): <b>紫 (300)</b></p>
         * <p>Hex: <b>#BA68C8</b></p>
         * <p>RGB: <b>186,104,200</b></p>
         * <p>HSV: <b>291,48,78</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PURPLE_300: number;

        /**
         * <p style="background-color: #AB47BC">#AB47BC</p>
         * <p>Name (en): <b>Purple (400)</b></p>
         * <p>Name (zh): <b>紫 (400)</b></p>
         * <p>Hex: <b>#AB47BC</b></p>
         * <p>RGB: <b>171,71,188</b></p>
         * <p>HSV: <b>291,62,74</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PURPLE_400: number;

        /**
         * <p style="background-color: #9C27B0">#9C27B0</p>
         * <p>Name (en): <b>Purple (500)</b></p>
         * <p>Name (zh): <b>紫 (500)</b></p>
         * <p>Hex: <b>#9C27B0</b></p>
         * <p>RGB: <b>156,39,176</b></p>
         * <p>HSV: <b>291,78,69</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PURPLE_500: number;

        /**
         * <p style="background-color: #8E24AA">#8E24AA</p>
         * <p>Name (en): <b>Purple (600)</b></p>
         * <p>Name (zh): <b>紫 (600)</b></p>
         * <p>Hex: <b>#8E24AA</b></p>
         * <p>RGB: <b>142,36,170</b></p>
         * <p>HSV: <b>287,79,67</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PURPLE_600: number;

        /**
         * <p style="background-color: #7B1FA2">#7B1FA2</p>
         * <p>Name (en): <b>Purple (700)</b></p>
         * <p>Name (zh): <b>紫 (700)</b></p>
         * <p>Hex: <b>#7B1FA2</b></p>
         * <p>RGB: <b>123,31,162</b></p>
         * <p>HSV: <b>282,81,64</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PURPLE_700: number;

        /**
         * <p style="background-color: #6A1B9A">#6A1B9A</p>
         * <p>Name (en): <b>Purple (800)</b></p>
         * <p>Name (zh): <b>紫 (800)</b></p>
         * <p>Hex: <b>#6A1B9A</b></p>
         * <p>RGB: <b>106,27,154</b></p>
         * <p>HSV: <b>277,82,60</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PURPLE_800: number;

        /**
         * <p style="background-color: #4A148C">#4A148C</p>
         * <p>Name (en): <b>Purple (900)</b></p>
         * <p>Name (zh): <b>紫 (900)</b></p>
         * <p>Hex: <b>#4A148C</b></p>
         * <p>RGB: <b>74,20,140</b></p>
         * <p>HSV: <b>267,86,55</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PURPLE_900: number;

        /**
         * <p style="background-color: #EA80FC">#EA80FC</p>
         * <p>Name (en): <b>Purple (A100)</b></p>
         * <p>Name (zh): <b>紫 (A100)</b></p>
         * <p>Hex: <b>#EA80FC</b></p>
         * <p>RGB: <b>234,128,252</b></p>
         * <p>HSV: <b>291,49,99</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PURPLE_A100: number;

        /**
         * <p style="background-color: #E040FB">#E040FB</p>
         * <p>Name (en): <b>Purple (A200)</b></p>
         * <p>Name (zh): <b>紫 (A200)</b></p>
         * <p>Hex: <b>#E040FB</b></p>
         * <p>RGB: <b>224,64,251</b></p>
         * <p>HSV: <b>291,75,98</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PURPLE_A200: number;

        /**
         * <p style="background-color: #D500F9">#D500F9</p>
         * <p>Name (en): <b>Purple (A400)</b></p>
         * <p>Name (zh): <b>紫 (A400)</b></p>
         * <p>Hex: <b>#D500F9</b></p>
         * <p>RGB: <b>213,0,249</b></p>
         * <p>HSV: <b>291,100,98</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PURPLE_A400: number;

        /**
         * <p style="background-color: #AA00FF">#AA00FF</p>
         * <p>Name (en): <b>Purple (A700)</b></p>
         * <p>Name (zh): <b>紫 (A700)</b></p>
         * <p>Hex: <b>#AA00FF</b></p>
         * <p>RGB: <b>170,0,255</b></p>
         * <p>HSV: <b>280,100,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        PURPLE_A700: number;

        /**
         * <p style="background-color: #663399">#663399</p>
         * <p>Name (en): <b>Rebecca Purple</b></p>
         * <p>Name (zh): <b>丽贝卡紫</b></p>
         * <p>Hex: <b>#663399</b></p>
         * <p>RGB: <b>102,51,153</b></p>
         * <p>HSV: <b>270,67,60</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        REBECCA_PURPLE: number;

        /**
         * <p style="background-color: #FF0000">#FF0000</p>
         * <p>Name (en): <b>Red</b></p>
         * <p>Name (zh): <b>红</b></p>
         * <p>Hex: <b>#FF0000</b></p>
         * <p>RGB: <b>255,0,0</b></p>
         * <p>HSV: <b>0,100,100</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        RED: number;

        /**
         * <p style="background-color: #FFEBEE">#FFEBEE</p>
         * <p>Name (en): <b>Red (50)</b></p>
         * <p>Name (zh): <b>红 (50)</b></p>
         * <p>Hex: <b>#FFEBEE</b></p>
         * <p>RGB: <b>255,235,238</b></p>
         * <p>HSV: <b>351,8,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        RED_50: number;

        /**
         * <p style="background-color: #FFCDD2">#FFCDD2</p>
         * <p>Name (en): <b>Red (100)</b></p>
         * <p>Name (zh): <b>红 (100)</b></p>
         * <p>Hex: <b>#FFCDD2</b></p>
         * <p>RGB: <b>255,205,210</b></p>
         * <p>HSV: <b>354,20,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        RED_100: number;

        /**
         * <p style="background-color: #EF9A9A">#EF9A9A</p>
         * <p>Name (en): <b>Red (200)</b></p>
         * <p>Name (zh): <b>红 (200)</b></p>
         * <p>Hex: <b>#EF9A9A</b></p>
         * <p>RGB: <b>239,154,154</b></p>
         * <p>HSV: <b>0,36,94</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        RED_200: number;

        /**
         * <p style="background-color: #E57373">#E57373</p>
         * <p>Name (en): <b>Red (300)</b></p>
         * <p>Name (zh): <b>红 (300)</b></p>
         * <p>Hex: <b>#E57373</b></p>
         * <p>RGB: <b>229,115,115</b></p>
         * <p>HSV: <b>0,50,90</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        RED_300: number;

        /**
         * <p style="background-color: #EF5350">#EF5350</p>
         * <p>Name (en): <b>Red (400)</b></p>
         * <p>Name (zh): <b>红 (400)</b></p>
         * <p>Hex: <b>#EF5350</b></p>
         * <p>RGB: <b>239,83,80</b></p>
         * <p>HSV: <b>1,67,94</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        RED_400: number;

        /**
         * <p style="background-color: #F44336">#F44336</p>
         * <p>Name (en): <b>Red (500)</b></p>
         * <p>Name (zh): <b>红 (500)</b></p>
         * <p>Hex: <b>#F44336</b></p>
         * <p>RGB: <b>244,67,54</b></p>
         * <p>HSV: <b>4,78,96</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        RED_500: number;

        /**
         * <p style="background-color: #E53935">#E53935</p>
         * <p>Name (en): <b>Red (600)</b></p>
         * <p>Name (zh): <b>红 (600)</b></p>
         * <p>Hex: <b>#E53935</b></p>
         * <p>RGB: <b>229,57,53</b></p>
         * <p>HSV: <b>1,77,90</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        RED_600: number;

        /**
         * <p style="background-color: #D32F2F">#D32F2F</p>
         * <p>Name (en): <b>Red (700)</b></p>
         * <p>Name (zh): <b>红 (700)</b></p>
         * <p>Hex: <b>#D32F2F</b></p>
         * <p>RGB: <b>211,47,47</b></p>
         * <p>HSV: <b>0,78,83</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        RED_700: number;

        /**
         * <p style="background-color: #C62828">#C62828</p>
         * <p>Name (en): <b>Red (800)</b></p>
         * <p>Name (zh): <b>红 (800)</b></p>
         * <p>Hex: <b>#C62828</b></p>
         * <p>RGB: <b>198,40,40</b></p>
         * <p>HSV: <b>0,80,78</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        RED_800: number;

        /**
         * <p style="background-color: #B71C1C">#B71C1C</p>
         * <p>Name (en): <b>Red (900)</b></p>
         * <p>Name (zh): <b>红 (900)</b></p>
         * <p>Hex: <b>#B71C1C</b></p>
         * <p>RGB: <b>183,28,28</b></p>
         * <p>HSV: <b>0,85,72</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        RED_900: number;

        /**
         * <p style="background-color: #FF8A80">#FF8A80</p>
         * <p>Name (en): <b>Red (A100)</b></p>
         * <p>Name (zh): <b>红 (A100)</b></p>
         * <p>Hex: <b>#FF8A80</b></p>
         * <p>RGB: <b>255,138,128</b></p>
         * <p>HSV: <b>5,50,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        RED_A100: number;

        /**
         * <p style="background-color: #FF5252">#FF5252</p>
         * <p>Name (en): <b>Red (A200)</b></p>
         * <p>Name (zh): <b>红 (A200)</b></p>
         * <p>Hex: <b>#FF5252</b></p>
         * <p>RGB: <b>255,82,82</b></p>
         * <p>HSV: <b>0,68,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        RED_A200: number;

        /**
         * <p style="background-color: #FF1744">#FF1744</p>
         * <p>Name (en): <b>Red (A400)</b></p>
         * <p>Name (zh): <b>红 (A400)</b></p>
         * <p>Hex: <b>#FF1744</b></p>
         * <p>RGB: <b>255,23,68</b></p>
         * <p>HSV: <b>348,91,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        RED_A400: number;

        /**
         * <p style="background-color: #D50000">#D50000</p>
         * <p>Name (en): <b>Red (A700)</b></p>
         * <p>Name (zh): <b>红 (A700)</b></p>
         * <p>Hex: <b>#D50000</b></p>
         * <p>RGB: <b>213,0,0</b></p>
         * <p>HSV: <b>0,100,84</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        RED_A700: number;

        /**
         * <p style="background-color: #FF007F">#FF007F</p>
         * <p>Name (en): <b>Rose</b></p>
         * <p>Name (zh): <b>玫瑰红</b></p>
         * <p>Hex: <b>#FF007F</b></p>
         * <p>RGB: <b>255,0,127</b></p>
         * <p>HSV: <b>330,100,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        ROSE: number;

        /**
         * <p style="background-color: #FF66CC">#FF66CC</p>
         * <p>Name (en): <b>Rose Pink</b></p>
         * <p>Name (zh): <b>浅玫瑰红</b></p>
         * <p>Hex: <b>#FF66CC</b></p>
         * <p>RGB: <b>255,102,204</b></p>
         * <p>HSV: <b>320,60,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        ROSE_PINK: number;

        /**
         * <p style="background-color: #BC8F8F">#BC8F8F</p>
         * <p>Name (en): <b>Rosy Brown</b></p>
         * <p>Name (zh): <b>玫瑰褐</b></p>
         * <p>Hex: <b>#BC8F8F</b></p>
         * <p>RGB: <b>188,143,143</b></p>
         * <p>HSV: <b>0,24,74</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        ROSY_BROWN: number;

        /**
         * <p style="background-color: #4169E1">#4169E1</p>
         * <p>Name (en): <b>Royal Blue</b></p>
         * <p>Name (zh): <b>品蓝 / 皇室蓝</b></p>
         * <p>Hex: <b>#4169E1</b></p>
         * <p>RGB: <b>65,105,225</b></p>
         * <p>HSV: <b>225,71,88</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        ROYAL_BLUE: number;

        /**
         * <p style="background-color: #CC0080">#CC0080</p>
         * <p>Name (en): <b>Ruby</b></p>
         * <p>Name (zh): <b>红宝石</b></p>
         * <p>Hex: <b>#CC0080</b></p>
         * <p>RGB: <b>204,0,128</b></p>
         * <p>HSV: <b>322,100,80</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        RUBY: number;

        /**
         * <p style="background-color: #8B4513">#8B4513</p>
         * <p>Name (en): <b>Saddle Brown</b></p>
         * <p>Name (zh): <b>鞍褐</b></p>
         * <p>Hex: <b>#8B4513</b></p>
         * <p>RGB: <b>139,69,19</b></p>
         * <p>HSV: <b>25,86,55</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        SADDLE_BROWN: number;

        /**
         * <p style="background-color: #FA8072">#FA8072</p>
         * <p>Name (en): <b>Salmon</b></p>
         * <p>Name (zh): <b>鲑红</b></p>
         * <p>Hex: <b>#FA8072</b></p>
         * <p>RGB: <b>250,128,114</b></p>
         * <p>HSV: <b>6,54,98</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        SALMON: number;

        /**
         * <p style="background-color: #FF8099">#FF8099</p>
         * <p>Name (en): <b>Salmon Pink</b></p>
         * <p>Name (zh): <b>浅鲑红</b></p>
         * <p>Hex: <b>#FF8099</b></p>
         * <p>RGB: <b>255,128,153</b></p>
         * <p>HSV: <b>348,50,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        SALMON_PINK: number;

        /**
         * <p style="background-color: #4D80E6">#4D80E6</p>
         * <p>Name (en): <b>Salvia Blue</b></p>
         * <p>Name (zh): <b>鼠尾草蓝</b></p>
         * <p>Hex: <b>#4D80E6</b></p>
         * <p>RGB: <b>77,128,230</b></p>
         * <p>HSV: <b>220,67,90</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        SALVIA_BLUE: number;

        /**
         * <p style="background-color: #E6C3C3">#E6C3C3</p>
         * <p>Name (en): <b>Sand Beige</b></p>
         * <p>Name (zh): <b>沙棕</b></p>
         * <p>Hex: <b>#E6C3C3</b></p>
         * <p>RGB: <b>230,195,195</b></p>
         * <p>HSV: <b>0,15,90</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        SAND_BEIGE: number;

        /**
         * <p style="background-color: #F4A460">#F4A460</p>
         * <p>Name (en): <b>Sand Brown</b></p>
         * <p>Name (zh): <b>沙褐</b></p>
         * <p>Hex: <b>#F4A460</b></p>
         * <p>RGB: <b>244,164,96</b></p>
         * <p>HSV: <b>28,61,96</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        SAND_BROWN: number;

        /**
         * <p style="background-color: #082567">#082567</p>
         * <p>Name (en): <b>Sapphire</b></p>
         * <p>Name (zh): <b>蓝宝石 / 青玉</b></p>
         * <p>Hex: <b>#082567</b></p>
         * <p>RGB: <b>8,37,103</b></p>
         * <p>HSV: <b>222,92,40</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        SAPPHIRE: number;

        /**
         * <p style="background-color: #4798B3">#4798B3</p>
         * <p>Name (en): <b>Saxe Blue</b></p>
         * <p>Name (zh): <b>萨克斯蓝</b></p>
         * <p>Hex: <b>#4798B3</b></p>
         * <p>RGB: <b>71,152,179</b></p>
         * <p>HSV: <b>195,60,70</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        SAXE_BLUE: number;

        /**
         * <p style="background-color: #FF2400">#FF2400</p>
         * <p>Name (en): <b>Scarlet</b></p>
         * <p>Name (zh): <b>猩红 / 腥红</b></p>
         * <p>Hex: <b>#FF2400</b></p>
         * <p>RGB: <b>255,36,0</b></p>
         * <p>HSV: <b>8,100,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        SCARLET: number;

        /**
         * <p style="background-color: #FFF5EE">#FFF5EE</p>
         * <p>Name (en): <b>Seashell</b></p>
         * <p>Name (zh): <b>海贝</b></p>
         * <p>Hex: <b>#FFF5EE</b></p>
         * <p>RGB: <b>255,245,238</b></p>
         * <p>HSV: <b>25,7,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        SEASHELL: number;

        /**
         * <p style="background-color: #2E8B57">#2E8B57</p>
         * <p>Name (en): <b>Sea Green</b></p>
         * <p>Name (zh): <b>海绿</b></p>
         * <p>Hex: <b>#2E8B57</b></p>
         * <p>RGB: <b>46,139,87</b></p>
         * <p>HSV: <b>146,67,55</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        SEA_GREEN: number;

        /**
         * <p style="background-color: #704214">#704214</p>
         * <p>Name (en): <b>Sepia</b></p>
         * <p>Name (zh): <b>深褐 / 乌贼墨</b></p>
         * <p>Hex: <b>#704214</b></p>
         * <p>RGB: <b>112,66,20</b></p>
         * <p>HSV: <b>30,82,44</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        SEPIA: number;

        /**
         * <p style="background-color: #FFB3BF">#FFB3BF</p>
         * <p>Name (en): <b>Shell Pink</b></p>
         * <p>Name (zh): <b>壳黄红</b></p>
         * <p>Hex: <b>#FFB3BF</b></p>
         * <p>RGB: <b>255,179,191</b></p>
         * <p>HSV: <b>351,30,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        SHELL_PINK: number;

        /**
         * <p style="background-color: #A0522D">#A0522D</p>
         * <p>Name (en): <b>Sienna</b></p>
         * <p>Name (zh): <b>赭黄</b></p>
         * <p>Hex: <b>#A0522D</b></p>
         * <p>RGB: <b>160,82,45</b></p>
         * <p>HSV: <b>19,72,63</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        SIENNA: number;

        /**
         * <p style="background-color: #C0C0C0">#C0C0C0</p>
         * <p>Name (en): <b>Silver</b></p>
         * <p>Name (zh): <b>银</b></p>
         * <p>Hex: <b>#C0C0C0</b></p>
         * <p>RGB: <b>192,192,192</b></p>
         * <p>HSV: <b>0,0,75</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        SILVER: number;

        /**
         * <p style="background-color: #87CEEB">#87CEEB</p>
         * <p>Name (en): <b>Sky Blue</b></p>
         * <p>Name (zh): <b>天空蓝</b></p>
         * <p>Hex: <b>#87CEEB</b></p>
         * <p>RGB: <b>135,206,235</b></p>
         * <p>HSV: <b>197,43,92</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        SKY_BLUE: number;

        /**
         * <p style="background-color: #6A5ACD">#6A5ACD</p>
         * <p>Name (en): <b>Slate Blue</b></p>
         * <p>Name (zh): <b>岩蓝</b></p>
         * <p>Hex: <b>#6A5ACD</b></p>
         * <p>RGB: <b>106,90,205</b></p>
         * <p>HSV: <b>248,56,80</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        SLATE_BLUE: number;

        /**
         * <p style="background-color: #708090">#708090</p>
         * <p>Name (en): <b>Slate Gray</b></p>
         * <p>Name (zh): <b>岩灰</b></p>
         * <p>Hex: <b>#708090</b></p>
         * <p>RGB: <b>112,128,144</b></p>
         * <p>HSV: <b>210,22,56</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        SLATE_GRAY: number;

        /**
         * <p style="background-color: #708090">#708090</p>
         * <p>Name (en): <b>Slate Grey</b></p>
         * <p>Name (zh): <b>岩灰</b></p>
         * <p>Hex: <b>#708090</b></p>
         * <p>RGB: <b>112,128,144</b></p>
         * <p>HSV: <b>210,22,56</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        SLATE_GREY: number;

        /**
         * <p style="background-color: #FFFAFA">#FFFAFA</p>
         * <p>Name (en): <b>Snow</b></p>
         * <p>Name (zh): <b>雪</b></p>
         * <p>Hex: <b>#FFFAFA</b></p>
         * <p>RGB: <b>255,250,250</b></p>
         * <p>HSV: <b>0,2,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        SNOW: number;

        /**
         * <p style="background-color: #FF73B3">#FF73B3</p>
         * <p>Name (en): <b>Spinel Red</b></p>
         * <p>Name (zh): <b>尖晶石红</b></p>
         * <p>Hex: <b>#FF73B3</b></p>
         * <p>RGB: <b>255,115,179</b></p>
         * <p>HSV: <b>333,55,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        SPINEL_RED: number;

        /**
         * <p style="background-color: #00FF7F">#00FF7F</p>
         * <p>Name (en): <b>Spring Green</b></p>
         * <p>Name (zh): <b>春绿</b></p>
         * <p>Hex: <b>#00FF7F</b></p>
         * <p>RGB: <b>0,255,127</b></p>
         * <p>HSV: <b>150,100,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        SPRING_GREEN: number;

        /**
         * <p style="background-color: #4682B4">#4682B4</p>
         * <p>Name (en): <b>Steel Blue</b></p>
         * <p>Name (zh): <b>钢青</b></p>
         * <p>Hex: <b>#4682B4</b></p>
         * <p>RGB: <b>70,130,180</b></p>
         * <p>HSV: <b>207,61,71</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        STEEL_BLUE: number;

        /**
         * <p style="background-color: #006374">#006374</p>
         * <p>Name (en): <b>Strong Blue</b></p>
         * <p>Name (zh): <b>浓蓝</b></p>
         * <p>Hex: <b>#006374</b></p>
         * <p>RGB: <b>0,99,116</b></p>
         * <p>HSV: <b>189,100,45</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        STRONG_BLUE: number;

        /**
         * <p style="background-color: #E60000">#E60000</p>
         * <p>Name (en): <b>Strong Red</b></p>
         * <p>Name (zh): <b>鲜红</b></p>
         * <p>Hex: <b>#E60000</b></p>
         * <p>RGB: <b>230,0,0</b></p>
         * <p>HSV: <b>0,100,90</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        STRONG_RED: number;

        /**
         * <p style="background-color: #FF7300">#FF7300</p>
         * <p>Name (en): <b>Sun Orange</b></p>
         * <p>Name (zh): <b>阳橙</b></p>
         * <p>Hex: <b>#FF7300</b></p>
         * <p>RGB: <b>255,115,0</b></p>
         * <p>HSV: <b>27,100,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        SUN_ORANGE: number;

        /**
         * <p style="background-color: #D2B48C">#D2B48C</p>
         * <p>Name (en): <b>Tan</b></p>
         * <p>Name (zh): <b>日晒</b></p>
         * <p>Hex: <b>#D2B48C</b></p>
         * <p>RGB: <b>210,180,140</b></p>
         * <p>HSV: <b>34,33,82</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        TAN: number;

        /**
         * <p style="background-color: #F28500">#F28500</p>
         * <p>Name (en): <b>Tangerine</b></p>
         * <p>Name (zh): <b>橘</b></p>
         * <p>Hex: <b>#F28500</b></p>
         * <p>RGB: <b>242,133,0</b></p>
         * <p>HSV: <b>33,100,95</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        TANGERINE: number;

        /**
         * <p style="background-color: #FFCC00">#FFCC00</p>
         * <p>Name (en): <b>Tangerine Yellow</b></p>
         * <p>Name (zh): <b>橙黄</b></p>
         * <p>Hex: <b>#FFCC00</b></p>
         * <p>RGB: <b>255,204,0</b></p>
         * <p>HSV: <b>48,100,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        TANGERINE_YELLOW: number;

        /**
         * <p style="background-color: #008080">#008080</p>
         * <p>Name (en): <b>Teal</b></p>
         * <p>Name (zh): <b>鸭绿 / 凫绿</b></p>
         * <p>Hex: <b>#008080</b></p>
         * <p>RGB: <b>0,128,128</b></p>
         * <p>HSV: <b>180,100,50</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        TEAL: number;

        /**
         * <p style="background-color: #E0F2F1">#E0F2F1</p>
         * <p>Name (en): <b>Teal (50)</b></p>
         * <p>Name (zh): <b>蓝绿 (50)</b></p>
         * <p>Hex: <b>#E0F2F1</b></p>
         * <p>RGB: <b>224,242,241</b></p>
         * <p>HSV: <b>177,7,95</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        TEAL_50: number;

        /**
         * <p style="background-color: #B2DFDB">#B2DFDB</p>
         * <p>Name (en): <b>Teal (100)</b></p>
         * <p>Name (zh): <b>蓝绿 (100)</b></p>
         * <p>Hex: <b>#B2DFDB</b></p>
         * <p>RGB: <b>178,223,219</b></p>
         * <p>HSV: <b>175,20,87</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        TEAL_100: number;

        /**
         * <p style="background-color: #80CBC4">#80CBC4</p>
         * <p>Name (en): <b>Teal (200)</b></p>
         * <p>Name (zh): <b>蓝绿 (200)</b></p>
         * <p>Hex: <b>#80CBC4</b></p>
         * <p>RGB: <b>128,203,196</b></p>
         * <p>HSV: <b>174,37,80</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        TEAL_200: number;

        /**
         * <p style="background-color: #4DB6AC">#4DB6AC</p>
         * <p>Name (en): <b>Teal (300)</b></p>
         * <p>Name (zh): <b>蓝绿 (300)</b></p>
         * <p>Hex: <b>#4DB6AC</b></p>
         * <p>RGB: <b>77,182,172</b></p>
         * <p>HSV: <b>174,58,71</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        TEAL_300: number;

        /**
         * <p style="background-color: #26A69A">#26A69A</p>
         * <p>Name (en): <b>Teal (400)</b></p>
         * <p>Name (zh): <b>蓝绿 (400)</b></p>
         * <p>Hex: <b>#26A69A</b></p>
         * <p>RGB: <b>38,166,154</b></p>
         * <p>HSV: <b>174,77,65</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        TEAL_400: number;

        /**
         * <p style="background-color: #009688">#009688</p>
         * <p>Name (en): <b>Teal (500)</b></p>
         * <p>Name (zh): <b>蓝绿 (500)</b></p>
         * <p>Hex: <b>#009688</b></p>
         * <p>RGB: <b>0,150,136</b></p>
         * <p>HSV: <b>174,100,59</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        TEAL_500: number;

        /**
         * <p style="background-color: #00897B">#00897B</p>
         * <p>Name (en): <b>Teal (600)</b></p>
         * <p>Name (zh): <b>蓝绿 (600)</b></p>
         * <p>Hex: <b>#00897B</b></p>
         * <p>RGB: <b>0,137,123</b></p>
         * <p>HSV: <b>174,100,54</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        TEAL_600: number;

        /**
         * <p style="background-color: #00796B">#00796B</p>
         * <p>Name (en): <b>Teal (700)</b></p>
         * <p>Name (zh): <b>蓝绿 (700)</b></p>
         * <p>Hex: <b>#00796B</b></p>
         * <p>RGB: <b>0,121,107</b></p>
         * <p>HSV: <b>173,100,47</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        TEAL_700: number;

        /**
         * <p style="background-color: #00695C">#00695C</p>
         * <p>Name (en): <b>Teal (800)</b></p>
         * <p>Name (zh): <b>蓝绿 (800)</b></p>
         * <p>Hex: <b>#00695C</b></p>
         * <p>RGB: <b>0,105,92</b></p>
         * <p>HSV: <b>173,100,41</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        TEAL_800: number;

        /**
         * <p style="background-color: #004D40">#004D40</p>
         * <p>Name (en): <b>Teal (900)</b></p>
         * <p>Name (zh): <b>蓝绿 (900)</b></p>
         * <p>Hex: <b>#004D40</b></p>
         * <p>RGB: <b>0,77,64</b></p>
         * <p>HSV: <b>170,100,30</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        TEAL_900: number;

        /**
         * <p style="background-color: #A7FFEB">#A7FFEB</p>
         * <p>Name (en): <b>Teal (A100)</b></p>
         * <p>Name (zh): <b>蓝绿 (A100)</b></p>
         * <p>Hex: <b>#A7FFEB</b></p>
         * <p>RGB: <b>167,255,235</b></p>
         * <p>HSV: <b>166,35,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        TEAL_A100: number;

        /**
         * <p style="background-color: #64FFDA">#64FFDA</p>
         * <p>Name (en): <b>Teal (A200)</b></p>
         * <p>Name (zh): <b>蓝绿 (A200)</b></p>
         * <p>Hex: <b>#64FFDA</b></p>
         * <p>RGB: <b>100,255,218</b></p>
         * <p>HSV: <b>166,61,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        TEAL_A200: number;

        /**
         * <p style="background-color: #1DE9B6">#1DE9B6</p>
         * <p>Name (en): <b>Teal (A400)</b></p>
         * <p>Name (zh): <b>蓝绿 (A400)</b></p>
         * <p>Hex: <b>#1DE9B6</b></p>
         * <p>RGB: <b>29,233,182</b></p>
         * <p>HSV: <b>165,88,91</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        TEAL_A400: number;

        /**
         * <p style="background-color: #00BFA5">#00BFA5</p>
         * <p>Name (en): <b>Teal (A700)</b></p>
         * <p>Name (zh): <b>蓝绿 (A700)</b></p>
         * <p>Hex: <b>#00BFA5</b></p>
         * <p>RGB: <b>0,191,165</b></p>
         * <p>HSV: <b>172,100,75</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        TEAL_A700: number;

        /**
         * <p style="background-color: #D8BFD8">#D8BFD8</p>
         * <p>Name (en): <b>Thistle</b></p>
         * <p>Name (zh): <b>蓟紫</b></p>
         * <p>Hex: <b>#D8BFD8</b></p>
         * <p>RGB: <b>216,191,216</b></p>
         * <p>HSV: <b>300,12,85</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        THISTLE: number;

        /**
         * <p style="background-color: #FF6347">#FF6347</p>
         * <p>Name (en): <b>Tomato</b></p>
         * <p>Name (zh): <b>蕃茄红</b></p>
         * <p>Hex: <b>#FF6347</b></p>
         * <p>RGB: <b>255,99,71</b></p>
         * <p>HSV: <b>9,72,100</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        TOMATO: number;

        /**
         * <p style="background-color: #00000000">#00000000　　</
         * <p>Name (en): <b>Transparent</b></p>
         * <p>Name (zh): <b>透明</b></p>
         * <p>Hex: <b>#00000000</b></p>
         * <p>RGB: <b>0,0,0,0 (RGBA)</b></p>
         * <p>HSV: <b>0,0,0,0 (HSVA)</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        TRANSPARENT: number;

        /**
         * <p style="background-color: #FF8033">#FF8033</p>
         * <p>Name (en): <b>Tropical Orange</b></p>
         * <p>Name (zh): <b>热带橙</b></p>
         * <p>Hex: <b>#FF8033</b></p>
         * <p>RGB: <b>255,128,51</b></p>
         * <p>HSV: <b>23,80,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        TROPICAL_ORANGE: number;

        /**
         * <p style="background-color: #40E0D0">#40E0D0</p>
         * <p>Name (en): <b>Turquoise</b></p>
         * <p>Name (zh): <b>绿松</b></p>
         * <p>Hex: <b>#40E0D0</b></p>
         * <p>RGB: <b>64,224,208</b></p>
         * <p>HSV: <b>174,71,88</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        TURQUOISE: number;

        /**
         * <p style="background-color: #00FFEF">#00FFEF</p>
         * <p>Name (en): <b>Turquoise Blue</b></p>
         * <p>Name (zh): <b>土耳其蓝</b></p>
         * <p>Hex: <b>#00FFEF</b></p>
         * <p>RGB: <b>0,255,239</b></p>
         * <p>HSV: <b>176,100,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        TURQUOISE_BLUE: number;

        /**
         * <p style="background-color: #4DE680">#4DE680</p>
         * <p>Name (en): <b>Turquoise Green</b></p>
         * <p>Name (zh): <b>绿松石绿</b></p>
         * <p>Hex: <b>#4DE680</b></p>
         * <p>RGB: <b>77,230,128</b></p>
         * <p>HSV: <b>140,67,90</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        TURQUOISE_GREEN: number;

        /**
         * <p style="background-color: #0033FF">#0033FF</p>
         * <p>Name (en): <b>Ultramarine</b></p>
         * <p>Name (zh): <b>极浓海蓝</b></p>
         * <p>Hex: <b>#0033FF</b></p>
         * <p>RGB: <b>0,51,255</b></p>
         * <p>HSV: <b>228,100,100</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        ULTRAMARINE: number;

        /**
         * <p style="background-color: #E34234">#E34234</p>
         * <p>Name (en): <b>Vermilion</b></p>
         * <p>Name (zh): <b>朱红</b></p>
         * <p>Hex: <b>#E34234</b></p>
         * <p>RGB: <b>227,66,52</b></p>
         * <p>HSV: <b>5,77,89</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        VERMILION: number;

        /**
         * <p style="background-color: #73E68C">#73E68C</p>
         * <p>Name (en): <b>Very Light Malachite Green</b></p>
         * <p>Name (zh): <b>孔雀石绿</b></p>
         * <p>Hex: <b>#73E68C</b></p>
         * <p>RGB: <b>115,230,140</b></p>
         * <p>HSV: <b>133,50,90</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        VERY_LIGHT_MALACHITE_GREEN: number;

        /**
         * <p style="background-color: #EE82EE">#EE82EE</p>
         * <p>Name (en): <b>Violet</b></p>
         * <p>Name (zh): <b>紫罗兰</b></p>
         * <p>Hex: <b>#EE82EE</b></p>
         * <p>RGB: <b>238,130,238</b></p>
         * <p>HSV: <b>300,45,93</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        VIOLET: number;

        /**
         * <p style="background-color: #127436">#127436</p>
         * <p>Name (en): <b>Viridian</b></p>
         * <p>Name (zh): <b>铬绿</b></p>
         * <p>Hex: <b>#127436</b></p>
         * <p>RGB: <b>18,116,54</b></p>
         * <p>HSV: <b>142,84,45</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        VIRIDIAN: number;

        /**
         * <p style="background-color: #5686BF">#5686BF</p>
         * <p>Name (en): <b>Wedgwood Blue</b></p>
         * <p>Name (zh): <b>韦奇伍德瓷蓝</b></p>
         * <p>Hex: <b>#5686BF</b></p>
         * <p>RGB: <b>86,134,191</b></p>
         * <p>HSV: <b>213,55,75</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        WEDGWOOD_BLUE: number;

        /**
         * <p style="background-color: #F5DEB3">#F5DEB3</p>
         * <p>Name (en): <b>Wheat</b></p>
         * <p>Name (zh): <b>小麦</b></p>
         * <p>Hex: <b>#F5DEB3</b></p>
         * <p>RGB: <b>245,222,179</b></p>
         * <p>HSV: <b>39,27,96</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        WHEAT: number;

        /**
         * <p style="background-color: #FFFFFF">#FFFFFF</p>
         * <p>Name (en): <b>White</b></p>
         * <p>Name (zh): <b>白</b></p>
         * <p>Hex: <b>#FFFFFF</b></p>
         * <p>RGB: <b>255,255,255</b></p>
         * <p>HSV: <b>0,0,100</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        WHITE: number;

        /**
         * <p style="background-color: #FFFFFF">#FFFFFF</p>
         * <p>Name (en): <b>White 1000</b></p>
         * <p>Name (zh): <b>白</b></p>
         * <p>Hex: <b>#FFFFFF</b></p>
         * <p>RGB: <b>255,255,255</b></p>
         * <p>HSV: <b>0,0,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        WHITE_1000: number;

        /**
         * <p style="background-color: #F5F5F5">#F5F5F5</p>
         * <p>Name (en): <b>White Smoke</b></p>
         * <p>Name (zh): <b>白烟</b></p>
         * <p>Hex: <b>#F5F5F5</b></p>
         * <p>RGB: <b>245,245,245</b></p>
         * <p>HSV: <b>0,0,96</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        WHITE_SMOKE: number;

        /**
         * <p style="background-color: #C9A0DC">#C9A0DC</p>
         * <p>Name (en): <b>Wisteria</b></p>
         * <p>Name (zh): <b>紫藤</b></p>
         * <p>Hex: <b>#C9A0DC</b></p>
         * <p>RGB: <b>201,160,220</b></p>
         * <p>HSV: <b>281,27,86</b></p>
         * <p>Table: <b>ColorTable.Web</b></p>
         */
        WISTERIA: number;

        /**
         * <p style="background-color: #FFFF00">#FFFF00</p>
         * <p>Name (en): <b>Yellow</b></p>
         * <p>Name (zh): <b>黄</b></p>
         * <p>Hex: <b>#FFFF00</b></p>
         * <p>RGB: <b>255,255,0</b></p>
         * <p>HSV: <b>60,100,100</b></p>
         * <p>Table: <b>ColorTable.Android</b></p>
         */
        YELLOW: number;

        /**
         * <p style="background-color: #FFFDE7">#FFFDE7</p>
         * <p>Name (en): <b>Yellow (50)</b></p>
         * <p>Name (zh): <b>黄 (50)</b></p>
         * <p>Hex: <b>#FFFDE7</b></p>
         * <p>RGB: <b>255,253,231</b></p>
         * <p>HSV: <b>55,9,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        YELLOW_50: number;

        /**
         * <p style="background-color: #FFF9C4">#FFF9C4</p>
         * <p>Name (en): <b>Yellow (100)</b></p>
         * <p>Name (zh): <b>黄 (100)</b></p>
         * <p>Hex: <b>#FFF9C4</b></p>
         * <p>RGB: <b>255,249,196</b></p>
         * <p>HSV: <b>54,23,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        YELLOW_100: number;

        /**
         * <p style="background-color: #FFF590">#FFF590</p>
         * <p>Name (en): <b>Yellow (200)</b></p>
         * <p>Name (zh): <b>黄 (200)</b></p>
         * <p>Hex: <b>#FFF590</b></p>
         * <p>RGB: <b>255,245,144</b></p>
         * <p>HSV: <b>55,44,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        YELLOW_200: number;

        /**
         * <p style="background-color: #FFF176">#FFF176</p>
         * <p>Name (en): <b>Yellow (300)</b></p>
         * <p>Name (zh): <b>黄 (300)</b></p>
         * <p>Hex: <b>#FFF176</b></p>
         * <p>RGB: <b>255,241,118</b></p>
         * <p>HSV: <b>54,54,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        YELLOW_300: number;

        /**
         * <p style="background-color: #FFEE58">#FFEE58</p>
         * <p>Name (en): <b>Yellow (400)</b></p>
         * <p>Name (zh): <b>黄 (400)</b></p>
         * <p>Hex: <b>#FFEE58</b></p>
         * <p>RGB: <b>255,238,88</b></p>
         * <p>HSV: <b>54,65,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        YELLOW_400: number;

        /**
         * <p style="background-color: #FFEB3B">#FFEB3B</p>
         * <p>Name (en): <b>Yellow (500)</b></p>
         * <p>Name (zh): <b>黄 (500)</b></p>
         * <p>Hex: <b>#FFEB3B</b></p>
         * <p>RGB: <b>255,235,59</b></p>
         * <p>HSV: <b>54,77,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        YELLOW_500: number;

        /**
         * <p style="background-color: #FDD835">#FDD835</p>
         * <p>Name (en): <b>Yellow (600)</b></p>
         * <p>Name (zh): <b>黄 (600)</b></p>
         * <p>Hex: <b>#FDD835</b></p>
         * <p>RGB: <b>253,216,53</b></p>
         * <p>HSV: <b>49,79,99</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        YELLOW_600: number;

        /**
         * <p style="background-color: #FBC02D">#FBC02D</p>
         * <p>Name (en): <b>Yellow (700)</b></p>
         * <p>Name (zh): <b>黄 (700)</b></p>
         * <p>Hex: <b>#FBC02D</b></p>
         * <p>RGB: <b>251,192,45</b></p>
         * <p>HSV: <b>43,82,98</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        YELLOW_700: number;

        /**
         * <p style="background-color: #F9A825">#F9A825</p>
         * <p>Name (en): <b>Yellow (800)</b></p>
         * <p>Name (zh): <b>黄 (800)</b></p>
         * <p>Hex: <b>#F9A825</b></p>
         * <p>RGB: <b>249,168,37</b></p>
         * <p>HSV: <b>37,85,98</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        YELLOW_800: number;

        /**
         * <p style="background-color: #F57F17">#F57F17</p>
         * <p>Name (en): <b>Yellow (900)</b></p>
         * <p>Name (zh): <b>黄 (900)</b></p>
         * <p>Hex: <b>#F57F17</b></p>
         * <p>RGB: <b>245,127,23</b></p>
         * <p>HSV: <b>28,91,96</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        YELLOW_900: number;

        /**
         * <p style="background-color: #FFFF82">#FFFF82</p>
         * <p>Name (en): <b>Yellow (A100)</b></p>
         * <p>Name (zh): <b>黄 (A100)</b></p>
         * <p>Hex: <b>#FFFF82</b></p>
         * <p>RGB: <b>255,255,130</b></p>
         * <p>HSV: <b>60,49,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        YELLOW_A100: number;

        /**
         * <p style="background-color: #FFFF00">#FFFF00</p>
         * <p>Name (en): <b>Yellow (A200)</b></p>
         * <p>Name (zh): <b>黄 (A200)</b></p>
         * <p>Hex: <b>#FFFF00</b></p>
         * <p>RGB: <b>255,255,0</b></p>
         * <p>HSV: <b>60,100,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        YELLOW_A200: number;

        /**
         * <p style="background-color: #FFEA00">#FFEA00</p>
         * <p>Name (en): <b>Yellow (A400)</b></p>
         * <p>Name (zh): <b>黄 (A400)</b></p>
         * <p>Hex: <b>#FFEA00</b></p>
         * <p>RGB: <b>255,234,0</b></p>
         * <p>HSV: <b>55,100,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        YELLOW_A400: number;

        /**
         * <p style="background-color: #FFD600">#FFD600</p>
         * <p>Name (en): <b>Yellow (A700)</b></p>
         * <p>Name (zh): <b>黄 (A700)</b></p>
         * <p>Hex: <b>#FFD600</b></p>
         * <p>RGB: <b>255,214,0</b></p>
         * <p>HSV: <b>50,100,100</b></p>
         * <p>Table: <b>ColorTable.Material</b></p>
         */
        YELLOW_A700: number;

        /**
         * <p style="background-color: #9ACD32">#9ACD32</p>
         * <p>Name (en): <b>Yellow Green</b></p>
         * <p>Name (zh): <b>黄绿</b></p>
         * <p>Hex: <b>#9ACD32</b></p>
         * <p>RGB: <b>154,205,50</b></p>
         * <p>HSV: <b>80,76,80</b></p>
         * <p>Table: <b>ColorTable.Css</b></p>
         */
        YELLOW_GREEN: number;

        /**
         * @example
         * // 0 - 255
         * console.log(colors.alpha("#EE12CC89")); // 238
         * @example Source code summary (zh-CN: 源代码摘要)
         * colors.alpha = function (color) {
         *     color = parseColor(color);
         *     return color >>> 24;
         * };
         * function parseColor(color) {
         *     if (typeof (color) == 'string') {
         *         color = colors.parseColor(color);
         *     }
         *     return color;
         * }
         */
        alpha(color: OmniColor, options?: ComponentOptions): number;
        getAlpha(color: OmniColor, options?: ComponentOptions): number;

        alphaDouble(color: OmniColor): number;
        getAlphaDouble(color: OmniColor): number;

        /**
         * @example
         * // 0 - 255
         * console.log(colors.red("#EE12CC89")); // 18
         * @example Source code summary (zh-CN: 源代码摘要)
         * colors.red = function (color) {
         *     color = parseColor(color);
         *     return (color >> 16) & 0xFF;
         * };
         * function parseColor(color) {
         *     if (typeof (color) == 'string') {
         *         color = colors.parseColor(color);
         *     }
         *     return color;
         * }
         */
        red(color: OmniColor, options?: ComponentOptions): number;
        getRed(color: OmniColor, options?: ComponentOptions): number;

        redDouble(color: OmniColor): number;
        getRedDouble(color: OmniColor): number;

        /**
         * @example
         * // 0 - 255
         * console.log(colors.green("#EE12CC89")); // 204
         * @example Source code summary (zh-CN: 源代码摘要)
         * colors.green = function (color) {
         *     color = parseColor(color);
         *     return (color >> 8) & 0xFF;
         * };
         * function parseColor(color) {
         *     if (typeof (color) == 'string') {
         *         color = colors.parseColor(color);
         *     }
         *     return color;
         * }
         */
        green(color: OmniColor, options?: ComponentOptions): number;
        getGreen(color: OmniColor, options?: ComponentOptions): number;

        greenDouble(color: OmniColor): number;
        getGreenDouble(color: OmniColor): number;

        /**
         * @example
         * // 0 - 255
         * console.log(colors.blue("#EE12CC89")); // 137
         * @example Source code summary (zh-CN: 源代码摘要)
         * colors.blue = function (color) {
         *     color = parseColor(color);
         *     return color & 0xFF;
         * };
         * function parseColor(color) {
         *     if (typeof (color) == 'string') {
         *         color = colors.parseColor(color);
         *     }
         *     return color;
         * }
         */
        blue(color: OmniColor, options?: ComponentOptions): number;
        getBlue(color: OmniColor, options?: ComponentOptions): number;

        blueDouble(color: OmniColor): number;
        getBlueDouble(color: OmniColor): number;

        setAlpha(color: OmniColor, alpha: ColorComponent): number;

        setAlphaRelative(color: OmniColor, percentage: number | PercentString): number;

        removeAlpha(color: OmniColor): number;

        setRed(color: OmniColor, red: ColorComponent): number;

        setRedRelative(color: OmniColor, percentage: number | PercentString): number;

        removeRed(color: OmniColor): number;

        setGreen(color: OmniColor, green: ColorComponent): number;

        setGreenRelative(color: OmniColor, percentage: number | PercentString): number;

        removeGreen(color: OmniColor): number;

        setBlue(color: OmniColor, blue: ColorComponent): number;

        setBlueRelative(color: OmniColor, percentage: number | PercentString): number;

        removeBlue(color: OmniColor): number;

        toInt(color: OmniColor): number;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public int parseColor(String colorString) {
         *     return Color.parseColor(colorString);
         * }
         * @see android.graphics.Color.parseColor
         */
        parseColor(colorString: string): number;

        /**
         * Get hex code string of a color.
         */
        toHex(color: OmniColor, alpha?: boolean | 'auto' | 'none' | 'keep'): string;
        /**
         * Get hex code string of a color.
         */
        toHex(color: OmniColor, length: 8 | 6 | 3): string;

        /**
         * Get hex code string of a color.
         *
         * @deprecated
         * @replaceWith colors.toHex
         */
        // @ts-ignore
        toString(color: OmniColor, alpha?: boolean | 'auto' | 'none' | 'keep'): string;
        /**
         * Get hex code string of a color.
         *
         * @deprecated
         * @replaceWith colors.toHex
         */
        // @ts-ignore
        toString(color: OmniColor, length: 8 | 6 | 3): string;

        toFullHex(color: OmniColor): string;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public int rgb(int red, int green, int blue) {
         *     return Color.rgb(red, green, blue);
         * }
         * @see android.graphics.Color
         */
        rgb(red: ColorComponent, green: ColorComponent, blue: ColorComponent): number;
        rgb(rgb: [ColorComponent, ColorComponent, ColorComponent]): number;
        rgb(color: OmniColor): number;

        /**
         * @example Source code summary (zh-CN: 源代码摘要)
         * public int argb(int alpha, int red, int green, int blue) {
         *     return Color.argb(alpha, red, green, blue);
         * }
         * @see android.graphics.Color
         */
        argb(alpha: ColorComponent, red: ColorComponent, green: ColorComponent, blue: ColorComponent): number;
        argb(components: ColorComponent[]): number;
        /**
         * @example
         * colors.argb('#aarrggbb') -> colors.argb('aa', 'rr', 'gg', 'bb')
         */
        argb(colorHex: '#AARRGGBB' | string): number;

        /**
         * @example
         * colors.rgba('rr', 'gg', 'bb', 'aa') -> colors.argb('aa', 'rr', 'gg', 'bb')
         */
        rgba(red: ColorComponent, green: ColorComponent, blue: ColorComponent, alpha: ColorComponent): number;
        rgba(rgb: ColorComponent[], alpha: ColorComponent): number;
        rgba(components: ColorComponent[]): number;
        /**
         * Useful for IDE like WebStorm which shows a color preview as a gutter icon.
         * @example
         * colors.rgba('#rrggbbaa') -> colors.toInt('#aarrggbb')
         */
        rgba(colorHex: '#RRGGBBAA' | string): number;

        hsv(h: ColorHueComponent, s: ColorComponent, v: ColorComponent): number;
        hsv(components: (ColorComponent | ColorHueComponent)[]): number;

        hsva(h: ColorHueComponent, s: ColorComponent, v: ColorComponent, a: ColorComponent): number;
        hsva(hsv: (ColorHueComponent | ColorComponent)[], a: ColorComponent): number;
        hsva(components: (ColorComponent | ColorHueComponent)[]): number;

        hsl(h: ColorHueComponent, s: ColorComponent, l: ColorComponent): number;
        hsl(components: (ColorComponent | ColorHueComponent)[]): number;

        hsla(h: ColorHueComponent, s: ColorComponent, l: ColorComponent, a: ColorComponent): number;
        hsla(hsl: (ColorHueComponent | ColorComponent)[], a: ColorComponent): number;
        hsla(components: (ColorComponent | ColorHueComponent)[]): number;

        toRgb(color: OmniColor): number[];

        toRgba(color: OmniColor, options?: ToComponentOptions): number[];

        toArgb(color: OmniColor, options?: ToComponentOptions): number[];

        toHsv(color: OmniColor): number[];
        toHsv(color: OmniColor, hsvResultContainer: FloatArray): number[];
        toHsv(r: ColorComponent, g: ColorComponent, b: ColorComponent): number[];
        toHsv(r: ColorComponent, g: ColorComponent, b: ColorComponent, hsvResultContainer: FloatArray): number[];

        toHsva(color: OmniColor): number[];
        toHsva(color: OmniColor, hsvaResultContainer: FloatArray): number[];
        toHsva(r: ColorComponent, g: ColorComponent, b: ColorComponent, a: ColorComponent): number[];
        toHsva(r: ColorComponent, g: ColorComponent, b: ColorComponent, a: ColorComponent, hsvaResultContainer: FloatArray): number[];

        toHsl(color: OmniColor): number[];
        toHsl(color: OmniColor, hslResultContainer: FloatArray): number[];
        toHsl(r: ColorComponent, g: ColorComponent, b: ColorComponent): number[];
        toHsl(r: ColorComponent, g: ColorComponent, b: ColorComponent, hslResultContainer: FloatArray): number[];

        toHsla(color: OmniColor): number[];
        toHsla(r: ColorComponent, g: ColorComponent, b: ColorComponent, a: ColorComponent): number[];

        toRgbString(color: OmniColor): string;
        toRgbString(r: ColorComponent, g: ColorComponent, b: ColorComponent): string;

        toRgbaString(color: OmniColor, options?: boolean | StringOptions): string;

        toArgbString(color: OmniColor, options?: boolean | StringOptions): string;

        toHsvString(color: OmniColor): string;
        toHsvString(r: ColorComponent, g: ColorComponent, b: ColorComponent): string;

        toHsvaString(color: OmniColor, options?: boolean | StringOptions): string;

        toHslString(color: OmniColor): string;
        toHslString(r: ColorComponent, g: ColorComponent, b: ColorComponent): string;

        toHslaString(color: OmniColor, options?: boolean | StringOptions): string;

        /**
         * @example
         * console.log(colors.isSimilar("#FFFFFF", -2)); // true
         * @example Source code summary (zh-CN: 源代码摘要)
         * colors.isSimilar = function (c1, c2, threshold, algorithm) {
         *     c1 = parseColor(c1);
         *     c2 = parseColor(c2);
         *     threshold = threshold == undefined ? 4 : threshold;
         *     algorithm = algorithm == undefined ? "diff" : algorithm;
         *     var colorDetector = getColorDetector(c1, algorithm, threshold);
         *     return colorDetector.detectsColor(colors.red(c2), colors.green(c2), colors.blue(c2));
         * };
         * function getColorDetector(color, algorithm, threshold) {
         *     switch (algorithm) {
         *         case "rgb":
         *             return new org.autojs.autojs.core.image.ColorDetector.RGBDistanceDetector(color, threshold);
         *         case "equal":
         *             return new org.autojs.autojs.core.image.ColorDetector.EqualityDetector(color);
         *         case "diff":
         *             return new org.autojs.autojs.core.image.ColorDetector.DifferenceDetector(color, threshold);
         *         case "rgb+":
         *             return new org.autojs.autojs.core.image.ColorDetector.WeightedRGBDistanceDetector(color, threshold);
         *         case "hs":
         *             return new org.autojs.autojs.core.image.ColorDetector.HSDistanceDetector(color, threshold);
         *     }
         *     throw new Error("Unknown algorithm: " + algorithm);
         * }
         * function parseColor(color) {
         *     if (typeof (color) == 'string') {
         *         color = colors.parseColor(color);
         *     }
         *     return color;
         * }
         * @param colorA
         * @param colorB
         * @param [threshold=4]
         * @param [algorithm="diff"]
         */
        isSimilar(colorA: OmniColor, colorB: OmniColor, threshold?: number, algorithm?: DetectionAlgorithm): boolean;
        isSimilar(colorA: OmniColor, colorB: OmniColor, options: {
            threshold?: number; /* [0..255] */
            similarity?: number; /* [0..1] */
            algorithm?: DetectionAlgorithm;
        }): boolean;

        isEqual(colorA: OmniColor, colorB: OmniColor, alphaMatters?: boolean): boolean;

        /**
         * @deprecated Use `colors.isEqual` instead.
         * @example Source code summary (zh-CN: 源代码摘要)
         * public boolean equals(int c1, int c2) {
         *     return (c1 & 0xFFFFFF) == (c2 & 0xFFFFFF);
         * }
         * public boolean equals(int c1, String c2) {
         *     return equals(c1, parseColor(c2));
         * }
         * public boolean equals(String c1, int c2) {
         *     return equals(parseColor(c1), c2);
         * }
         * public boolean equals(String c1, String c2) {
         *     return equals(parseColor(c1), parseColor(c2));
         * }
         * public int parseColor(String colorString) {
         *     return Color.parseColor(colorString);
         * }
         * @see android.graphics.Color
         */
        // @ts-ignore
        equals(colorA: OmniColor, colorB: OmniColor): boolean;

        /**
         * @description Luminance is defined using a standard model of human vision
         * @description zh-CN: 基于人眼感知的亮度值 (灰度值)
         * @example
         * console.log(colors.luminance(colors.parseColor("#888888"))); // 0.2462013214826584
         * console.log(colors.luminance(colors.parseColor("#FFFFFF"))); // 1
         * console.log(colors.luminance(colors.parseColor("#FFFFEE"))); // 0.9895304441452026
         * @example
         * // Gray1 = (Red * 0.3 + Green * 0.59 + Blue * 0.11);
         * // Gray2 = (Red * 0.2126 + Green * 0.7152 + Blue * 0.0722);
         * // Gray3 = (Red * 0.299 + Green * 0.587 + Blue * 0.114);
         * @example Source code summary (zh-CN: 源代码摘要)
         * -- @RequiresApi(api = Build.VERSION_CODES.N)
         * public float luminance(int color) {
         *     double red = Color.red(color) / 255.0;
         *     red = red < 0.03928 ? red / 12.92 : Math.pow((red + 0.055) / 1.055, 2.4);
         *     double green = Color.green(color) / 255.0;
         *     green = green < 0.03928 ? green / 12.92 : Math.pow((green + 0.055) / 1.055, 2.4);
         *     double blue = Color.blue(color) / 255.0;
         *     blue = blue < 0.03928 ? blue / 12.92 : Math.pow((blue + 0.055) / 1.055, 2.4);
         *     return (float) ((0.2126 * red) + (0.7152 * green) + (0.0722 * blue));
         * }
         * @see android.graphics.Color
         * @see android.os.Build
         * @see https://developer.mozilla.org/en-US/docs/Web/Accessibility/Understanding_Colors_and_Luminance
         * @see https://en.wikipedia.org/wiki/Luma_(video)
         * @see https://en.wikipedia.org/wiki/Grayscale
         */
        // @ts-ignore
        luminance(color: OmniColor): number;

        toColorStateList(...color: (OmniColor)[]): android.content.res.ColorStateList;

        setPaintColor(paint: Paint, color: OmniColor): void;

        build(): Color;
        build(color: OmniColor): Color;
        build(r: ColorComponent, g: ColorComponent, b: ColorComponent): Color;
        build(r: ColorComponent, g: ColorComponent, b: ColorComponent, a: ColorComponent): Color;

        summary(color: OmniColor): string;

    }

    interface Color {

        new(color?: OmniColor);
        new(r: ColorComponent, g: ColorComponent, b: ColorComponent, a?: ColorComponent);

        (color?: OmniColor): Color;
        (r: ColorComponent, g: ColorComponent, b: ColorComponent, a?: ColorComponent): Color;

        color: ColorInt;

        summary(): string;

        toHex(alpha?: boolean | 'auto' | 'none' | 'keep'): string;
        toHex(length: 8 | 6 | 3): string;

        toFullHex(): string;

        toInt(): number;

        alpha(options?: ComponentOptions): number;
        getAlpha(options?: ComponentOptions): number;

        alphaDouble(): number;
        getAlphaDouble(): number;

        red(options?: ComponentOptions): number;
        getRed(options?: ComponentOptions): number;

        redDouble(): number;
        getRedDouble(): number;

        green(options?: ComponentOptions): number;
        getGreen(options?: ComponentOptions): number;

        greenDouble(): number;
        getGreenDouble(): number;

        blue(options?: ComponentOptions): number;
        getBlue(options?: ComponentOptions): number;

        blueDouble(): number;
        getBlueDouble(): number;

        setAlpha(alpha: ColorComponent): this;

        setAlphaRelative(percentage: number | PercentString): this;

        removeAlpha(): this;

        setRed(red: ColorComponent): this;

        setRedRelative(percentage: number | PercentString): this;

        removeRed(): this;

        setGreen(green: ColorComponent): this;

        setGreenRelative(percentage: number | PercentString): this;

        removeGreen(): this;

        setBlue(blue: ColorComponent): this;

        setBlueRelative(percentage: number | PercentString): this;

        removeBlue(): this;

        setRgb(red: ColorComponent, green: ColorComponent, blue: ColorComponent): this;
        setRgb(rgb: [ColorComponent, ColorComponent, ColorComponent]): this;
        setRgb(color: OmniColor): this;

        setArgb(alpha: ColorComponent, red: ColorComponent, green: ColorComponent, blue: ColorComponent): this;
        setArgb(components: ColorComponent[]): this;
        setArgb(colorHex: '#AARRGGBB' | string): this;

        setRgba(red: ColorComponent, green: ColorComponent, blue: ColorComponent, alpha: ColorComponent): this;
        setRgba(rgb: ColorComponent[], alpha: ColorComponent): this;
        setRgba(components: ColorComponent[]): this;
        setRgba(colorHex: '#RRGGBBAA' | string): this;

        setHsv(h: ColorHueComponent, s: ColorComponent, v: ColorComponent): this;
        setHsv(components: (ColorComponent | ColorHueComponent)[]): this;

        setHsva(h: ColorHueComponent, s: ColorComponent, v: ColorComponent, a: ColorComponent): this;
        setHsva(hsv: (ColorHueComponent | ColorComponent)[], a: ColorComponent): this;
        setHsva(components: (ColorComponent | ColorHueComponent)[]): this;

        setHsl(h: ColorHueComponent, s: ColorComponent, l: ColorComponent): this;
        setHsl(components: (ColorComponent | ColorHueComponent)[]): this;

        setHsla(h: ColorHueComponent, s: ColorComponent, l: ColorComponent, a: ColorComponent): this;
        setHsla(hsl: (ColorHueComponent | ColorComponent)[], a: ColorComponent): this;
        setHsla(components: (ColorComponent | ColorHueComponent)[]): this;

        toRgb(): number[];

        toRgba(options?: ToComponentOptions): number[];

        toArgb(options?: ToComponentOptions): number[];

        toHsv(hsvResultContainer?: FloatArray): number[];

        toHsva(hsvaResultContainer?: FloatArray): number[];

        toHsl(hslResultContainer?: FloatArray): number[];

        toHsla(): number[];

        toRgbString(): string;

        toRgbaString(options?: boolean | StringOptions): string;

        toArgbString(options?: boolean | StringOptions): string;

        toHsvString(): string;

        toHsvaString(options?: boolean | StringOptions): string;

        toHslString(): string;

        toHslaString(options?: boolean | StringOptions): string;

        isSimilar(other: OmniColor, threshold?: number, algorithm?: DetectionAlgorithm): boolean;
        isSimilar(other: OmniColor, options: {
            threshold?: number; /* [0..255] */
            similarity?: number; /* [0..1] */
            algorithm?: DetectionAlgorithm;
        }): boolean;

        isEqual(other: OmniColor, alphaMatters?: boolean): boolean;

        // @ts-ignore
        equals(other: OmniColor): boolean;

        // @ts-ignore
        luminance(): number;

        toColorStateList(): android.content.res.ColorStateList;

        setPaintColor(paint: Paint): this;

    }

}

declare namespace Colors {

    type ColorComponent = number /* 0..255 | 0..1 */ | PercentString | string /* percent */;

    type ColorHueComponent = number /* any number */ | PercentString | string /* percent */;

    interface ToComponentOptions {
        maxAlpha?: 1 | 255;
    }

    interface ComponentOptions {
        max?: 1 | 255;
    }

    interface StringOptions {
        keepTrailingZeroForFullAlpha?: boolean;
    }

    type FloatArray = number[];

    type ColorMap = {
        readonly [name: string]: ColorInt;
    };

    /** @IntRange (from = -Infinity, to = Infinity) */
    type ColorInt = number;

    type ColorHex = '#RGB' | '#RRGGBB' | '#AARRGGBB' | string;

    type ColorName = 'ALICE_BLUE' | 'ALIZARIN_CRIMSON' | 'AMBER' | 'AMBER_50' | 'AMBER_100' | 'AMBER_200' | 'AMBER_300' | 'AMBER_400' | 'AMBER_500' | 'AMBER_600' | 'AMBER_700' | 'AMBER_800'
        | 'AMBER_900' | 'AMBER_A100' | 'AMBER_A200' | 'AMBER_A400' | 'AMBER_A700' | 'AMETHYST' | 'ANTIQUE_WHITE' | 'APPLE_GREEN' | 'APRICOT' | 'AQUA' | 'AQUAMARINE' | 'AQUA_BLUE'
        | 'AZURE' | 'BABY_BLUE' | 'BABY_PINK' | 'BEIGE' | 'BISQUE' | 'BLACK' | 'BLACK_1000' | 'BLANCHED_ALMOND' | 'BLUE' | 'BLUE_50' | 'BLUE_100' | 'BLUE_200' | 'BLUE_300' | 'BLUE_400'
        | 'BLUE_500' | 'BLUE_600' | 'BLUE_700' | 'BLUE_800' | 'BLUE_900' | 'BLUE_A100' | 'BLUE_A200' | 'BLUE_A400' | 'BLUE_A700' | 'BLUE_GREY' | 'BLUE_GRAY' | 'BLUE_GREY_50' | 'BLUE_GREY_100'
        | 'BLUE_GREY_200' | 'BLUE_GREY_300' | 'BLUE_GREY_400' | 'BLUE_GREY_500' | 'BLUE_GREY_600' | 'BLUE_GREY_700' | 'BLUE_GREY_800' | 'BLUE_GREY_900' | 'BLUE_GRAY_50'
        | 'BLUE_GRAY_100' | 'BLUE_GRAY_200' | 'BLUE_GRAY_300' | 'BLUE_GRAY_400' | 'BLUE_GRAY_500' | 'BLUE_GRAY_600' | 'BLUE_GRAY_700' | 'BLUE_GRAY_800' | 'BLUE_GRAY_900' | 'BLUE_VIOLET'
        | 'BRIGHT_GREEN' | 'BRONZE' | 'BROWN' | 'BROWN_50' | 'BROWN_100' | 'BROWN_200' | 'BROWN_300' | 'BROWN_400' | 'BROWN_500' | 'BROWN_600' | 'BROWN_700' | 'BROWN_800' | 'BROWN_900'
        | 'BURGUNDY' | 'BURLY_WOOD' | 'BURNT_ORANGE' | 'CADET_BLUE' | 'CAMEL' | 'CAMELLIA' | 'CANARY_YELLOW' | 'CARDINAL_RED' | 'CARMINE' | 'CELADON' | 'CERISE' | 'CERULEAN_BLUE'
        | 'CHAMPAGNE_YELLOW' | 'CHARTREUSE' | 'CHOCOLATE' | 'CHROME_YELLOW' | 'CLEMATIS' | 'COBALT_BLUE' | 'COBALT_GREEN' | 'COCONUT_BROWN' | 'COFFEE' | 'CORAL' | 'CORAL_PINK'
        | 'CORNFLOWER_BLUE' | 'CORN_SILK' | 'CREAM' | 'CRIMSON' | 'CYAN' | 'CYAN_50' | 'CYAN_100' | 'CYAN_200' | 'CYAN_300' | 'CYAN_400' | 'CYAN_500' | 'CYAN_600' | 'CYAN_700' | 'CYAN_800'
        | 'CYAN_900' | 'CYAN_A100' | 'CYAN_A200' | 'CYAN_A400' | 'CYAN_A700' | 'CYAN_BLUE' | 'DARK_BLUE' | 'DARK_CYAN' | 'DARK_GOLDENROD' | 'DARK_GRAY' | 'DARK_GREEN' | 'DARK_GREY'
        | 'DARK_KHAKI' | 'DARK_MAGENTA' | 'DARK_MINERAL_BLUE' | 'DARK_OLIVE_GREEN' | 'DARK_ORANGE' | 'DARK_ORCHID' | 'DARK_POWDER_BLUE' | 'DARK_RED' | 'DARK_SALMON'
        | 'DARK_SEA_GREEN' | 'DARK_SLATE_BLUE' | 'DARK_SLATE_GRAY' | 'DARK_SLATE_GREY' | 'DARK_TURQUOISE' | 'DARK_VIOLET' | 'DEEP_ORANGE' | 'DEEP_ORANGE_50' | 'DEEP_ORANGE_100'
        | 'DEEP_ORANGE_200' | 'DEEP_ORANGE_300' | 'DEEP_ORANGE_400' | 'DEEP_ORANGE_500' | 'DEEP_ORANGE_600' | 'DEEP_ORANGE_700' | 'DEEP_ORANGE_800' | 'DEEP_ORANGE_900'
        | 'DEEP_ORANGE_A100' | 'DEEP_ORANGE_A200' | 'DEEP_ORANGE_A400' | 'DEEP_ORANGE_A700' | 'DEEP_PINK' | 'DEEP_PURPLE' | 'DEEP_PURPLE_50' | 'DEEP_PURPLE_100'
        | 'DEEP_PURPLE_200' | 'DEEP_PURPLE_300' | 'DEEP_PURPLE_400' | 'DEEP_PURPLE_500' | 'DEEP_PURPLE_600' | 'DEEP_PURPLE_700' | 'DEEP_PURPLE_800' | 'DEEP_PURPLE_900'
        | 'DEEP_PURPLE_A100' | 'DEEP_PURPLE_A200' | 'DEEP_PURPLE_A400' | 'DEEP_PURPLE_A700' | 'DEEP_SKY_BLUE' | 'DIM_GRAY' | 'DIM_GREY' | 'DKGRAY' | 'DODGER_BLUE' | 'EMERALD'
        | 'FIRE_BRICK' | 'FLAMINGO' | 'FLORAL_WHITE' | 'FOLIAGE_GREEN' | 'FOREST_GREEN' | 'FRESH_LEAVES' | 'FUCHSIA' | 'GAINSBORO' | 'GHOST_WHITE' | 'GOLD' | 'GOLDEN' | 'GOLDENROD'
        | 'GRASS_GREEN' | 'GRAY' | 'GRAY_50' | 'GRAY_100' | 'GRAY_200' | 'GRAY_300' | 'GRAY_400' | 'GRAY_500' | 'GRAY_600' | 'GRAY_700' | 'GRAY_800' | 'GRAY_900'
        | 'GRAYISH_PURPLE' | 'GREEN' | 'GREEN_50' | 'GREEN_100' | 'GREEN_200' | 'GREEN_300' | 'GREEN_400' | 'GREEN_500' | 'GREEN_600' | 'GREEN_700'
        | 'GREEN_800' | 'GREEN_900' | 'GREEN_A100' | 'GREEN_A200' | 'GREEN_A400' | 'GREEN_A700' | 'GREEN_YELLOW' | 'GREY' | 'GREY_50' | 'GREY_100' | 'GREY_200' | 'GREY_300'
        | 'GREY_400' | 'GREY_500' | 'GREY_600' | 'GREY_700' | 'GREY_800' | 'GREY_900' | 'HELIOTROPE' | 'HONEYDEW' | 'HONEY_ORANGE' | 'HORIZON_BLUE' | 'HOT_PINK' | 'INDIAN_RED'
        | 'INDIGO' | 'INDIGO_50' | 'INDIGO_100' | 'INDIGO_200' | 'INDIGO_300' | 'INDIGO_400' | 'INDIGO_500' | 'INDIGO_600' | 'INDIGO_700' | 'INDIGO_800' | 'INDIGO_900'
        | 'INDIGO_A100' | 'INDIGO_A200' | 'INDIGO_A400' | 'INDIGO_A700' | 'INTERNATIONAL_KLEIN_BLUE' | 'IRON_GRAY' | 'IVORY' | 'IVY_GREEN' | 'JASMINE' | 'KHAKI' | 'LAPIS_LAZULI'
        | 'LAVENDER' | 'LAVENDER_BLUE' | 'LAVENDER_BLUSH' | 'LAVENDER_MAGENTA' | 'LAVENDER_MIST' | 'LAWN_GREEN' | 'LEMON_CHIFFON' | 'LIGHT_BLUE' | 'LIGHT_BLUE_50'
        | 'LIGHT_BLUE_100' | 'LIGHT_BLUE_200' | 'LIGHT_BLUE_300' | 'LIGHT_BLUE_400' | 'LIGHT_BLUE_500' | 'LIGHT_BLUE_600' | 'LIGHT_BLUE_700' | 'LIGHT_BLUE_800'
        | 'LIGHT_BLUE_900' | 'LIGHT_BLUE_A100' | 'LIGHT_BLUE_A200' | 'LIGHT_BLUE_A400' | 'LIGHT_BLUE_A700' | 'LIGHT_CORAL' | 'LIGHT_CYAN' | 'LIGHT_GOLDENROD_YELLOW'
        | 'LIGHT_GRAY' | 'LIGHT_GREEN' | 'LIGHT_GREEN_50' | 'LIGHT_GREEN_100' | 'LIGHT_GREEN_200' | 'LIGHT_GREEN_300' | 'LIGHT_GREEN_400' | 'LIGHT_GREEN_500' | 'LIGHT_GREEN_600'
        | 'LIGHT_GREEN_700' | 'LIGHT_GREEN_800' | 'LIGHT_GREEN_900' | 'LIGHT_GREEN_A100' | 'LIGHT_GREEN_A200' | 'LIGHT_GREEN_A400' | 'LIGHT_GREEN_A700' | 'LIGHT_GREY'
        | 'LIGHT_KHAKI' | 'LIGHT_LIME' | 'LIGHT_PINK' | 'LIGHT_SALMON' | 'LIGHT_SEA_GREEN' | 'LIGHT_SKY_BLUE' | 'LIGHT_SLATE_GRAY' | 'LIGHT_SLATE_GREY' | 'LIGHT_STEEL_BLUE'
        | 'LIGHT_VIOLET' | 'LIGHT_YELLOW' | 'LILAC' | 'LIME' | 'LIME_50' | 'LIME_100' | 'LIME_200' | 'LIME_300' | 'LIME_400' | 'LIME_500' | 'LIME_600' | 'LIME_700' | 'LIME_800'
        | 'LIME_900' | 'LIME_A100' | 'LIME_A200' | 'LIME_A400' | 'LIME_A700' | 'LIME_GREEN' | 'LINEN' | 'LTGRAY' | 'MAGENTA' | 'MAGENTA_ROSE' | 'MALACHITE' | 'MALLOW' | 'MARIGOLD'
        | 'MARINE_BLUE' | 'MAROON' | 'MAUVE' | 'MEDIUM_AQUAMARINE' | 'MEDIUM_BLUE' | 'MEDIUM_LAVENDER_MAGENTA' | 'MEDIUM_ORCHID' | 'MEDIUM_PURPLE' | 'MEDIUM_SEA_GREEN'
        | 'MEDIUM_SLATE_BLUE' | 'MEDIUM_SPRING_GREEN' | 'MEDIUM_TURQUOISE' | 'MEDIUM_VIOLET_RED' | 'MIDNIGHT_BLUE' | 'MIMOSA' | 'MINERAL_BLUE' | 'MINERAL_VIOLET' | 'MINT'
        | 'MINT_CREAM' | 'MISTY_ROSE' | 'MOCCASIN' | 'MOON_YELLOW' | 'MOSS_GREEN' | 'MUSTARD' | 'NAVAJO_WHITE' | 'NAVY' | 'NAVY_BLUE' | 'OCHER' | 'OLD_LACE' | 'OLD_ROSE' | 'OLIVE'
        | 'OLIVE_DRAB' | 'OPERA_MAUVE' | 'ORANGE' | 'ORANGE_50' | 'ORANGE_100' | 'ORANGE_200' | 'ORANGE_300' | 'ORANGE_400' | 'ORANGE_500' | 'ORANGE_600' | 'ORANGE_700' | 'ORANGE_800'
        | 'ORANGE_900' | 'ORANGE_A100' | 'ORANGE_A200' | 'ORANGE_A400' | 'ORANGE_A700' | 'ORANGE_RED' | 'ORCHID' | 'PAIL_LILAC' | 'PALE_BLUE' | 'PALE_DENIM' | 'PALE_GOLDENROD'
        | 'PALE_GREEN' | 'PALE_OCHRE' | 'PALE_TURQUOISE' | 'PALE_VIOLET_RED' | 'PANSY' | 'PAPAYA_WHIP' | 'PATRIARCH' | 'PEACH' | 'PEACH_PEARL' | 'PEACH_PUFF' | 'PEACOCK_BLUE'
        | 'PEACOCK_GREEN' | 'PEARL_PINK' | 'PERIWINKLE' | 'PERSIMMON' | 'PERU' | 'PINK' | 'PINK_50' | 'PINK_100' | 'PINK_200' | 'PINK_300' | 'PINK_400' | 'PINK_500' | 'PINK_600'
        | 'PINK_700' | 'PINK_800' | 'PINK_900' | 'PINK_A100' | 'PINK_A200' | 'PINK_A400' | 'PINK_A700' | 'PLUM' | 'POWDER_BLUE' | 'PRUSSIAN_BLUE' | 'PURPLE' | 'PURPLE_50' | 'PURPLE_100'
        | 'PURPLE_200' | 'PURPLE_300' | 'PURPLE_400' | 'PURPLE_500' | 'PURPLE_600' | 'PURPLE_700' | 'PURPLE_800' | 'PURPLE_900' | 'PURPLE_A100' | 'PURPLE_A200' | 'PURPLE_A400'
        | 'PURPLE_A700' | 'REBECCA_PURPLE' | 'RED' | 'RED_50' | 'RED_100' | 'RED_200' | 'RED_300' | 'RED_400' | 'RED_500' | 'RED_600' | 'RED_700' | 'RED_800' | 'RED_900' | 'RED_A100'
        | 'RED_A200' | 'RED_A400' | 'RED_A700' | 'ROSE' | 'ROSE_PINK' | 'ROSY_BROWN' | 'ROYAL_BLUE' | 'RUBY' | 'SADDLE_BROWN' | 'SALMON' | 'SALMON_PINK' | 'SALVIA_BLUE' | 'SAND_BEIGE'
        | 'SAND_BROWN' | 'SAPPHIRE' | 'SAXE_BLUE' | 'SCARLET' | 'SEASHELL' | 'SEA_GREEN' | 'SEPIA' | 'SHELL_PINK' | 'SIENNA' | 'SILVER' | 'SKY_BLUE' | 'SLATE_BLUE' | 'SLATE_GRAY'
        | 'SLATE_GREY' | 'SNOW' | 'SPINEL_RED' | 'SPRING_GREEN' | 'STEEL_BLUE' | 'STRONG_BLUE' | 'STRONG_RED' | 'SUN_ORANGE' | 'TAN' | 'TANGERINE' | 'TANGERINE_YELLOW' | 'TEAL'
        | 'TEAL_50' | 'TEAL_100' | 'TEAL_200' | 'TEAL_300' | 'TEAL_400' | 'TEAL_500' | 'TEAL_600' | 'TEAL_700' | 'TEAL_800' | 'TEAL_900' | 'TEAL_A100' | 'TEAL_A200' | 'TEAL_A400'
        | 'TEAL_A700' | 'THISTLE' | 'TOMATO' | 'TRANSPARENT' | 'TROPICAL_ORANGE' | 'TURQUOISE' | 'TURQUOISE_BLUE' | 'TURQUOISE_GREEN' | 'ULTRAMARINE' | 'VERMILION'
        | 'VERY_LIGHT_MALACHITE_GREEN' | 'VIOLET' | 'VIRIDIAN' | 'WEDGWOOD_BLUE' | 'WHEAT' | 'WHITE' | 'WHITE_1000' | 'WHITE_SMOKE' | 'WISTERIA' | 'YELLOW' | 'YELLOW_50'
        | 'YELLOW_100' | 'YELLOW_200' | 'YELLOW_300' | 'YELLOW_400' | 'YELLOW_500' | 'YELLOW_600' | 'YELLOW_700' | 'YELLOW_800' | 'YELLOW_900' | 'YELLOW_A100' | 'YELLOW_A200'
        | 'YELLOW_A400' | 'YELLOW_A700' | 'YELLOW_GREEN'

        | 'alice-blue' | 'alizarin-crimson' | 'amber' | 'amber-50' | 'amber-100' | 'amber-200' | 'amber-300' | 'amber-400' | 'amber-500' | 'amber-600' | 'amber-700' | 'amber-800'
        | 'amber-900' | 'amber-a100' | 'amber-a200' | 'amber-a400' | 'amber-a700' | 'amethyst' | 'antique-white' | 'apple-green' | 'apricot' | 'aqua' | 'aquamarine' | 'aqua-blue'
        | 'azure' | 'baby-blue' | 'baby-pink' | 'beige' | 'bisque' | 'black' | 'black-1000' | 'blanched-almond' | 'blue' | 'blue-50' | 'blue-100' | 'blue-200' | 'blue-300' | 'blue-400'
        | 'blue-500' | 'blue-600' | 'blue-700' | 'blue-800' | 'blue-900' | 'blue-a100' | 'blue-a200' | 'blue-a400' | 'blue-a700' | 'blue-grey' | 'blue-gray' | 'blue-grey-50' | 'blue-grey-100'
        | 'blue-grey-200' | 'blue-grey-300' | 'blue-grey-400' | 'blue-grey-500' | 'blue-grey-600' | 'blue-grey-700' | 'blue-grey-800' | 'blue-grey-900' | 'blue-gray-50'
        | 'blue-gray-100' | 'blue-gray-200' | 'blue-gray-300' | 'blue-gray-400' | 'blue-gray-500' | 'blue-gray-600' | 'blue-gray-700' | 'blue-gray-800' | 'blue-gray-900' | 'blue-violet'
        | 'bright-green' | 'bronze' | 'brown' | 'brown-50' | 'brown-100' | 'brown-200' | 'brown-300' | 'brown-400' | 'brown-500' | 'brown-600' | 'brown-700' | 'brown-800' | 'brown-900'
        | 'burgundy' | 'burly-wood' | 'burnt-orange' | 'cadet-blue' | 'camel' | 'camellia' | 'canary-yellow' | 'cardinal-red' | 'carmine' | 'celadon' | 'cerise' | 'cerulean-blue'
        | 'champagne-yellow' | 'chartreuse' | 'chocolate' | 'chrome-yellow' | 'clematis' | 'cobalt-blue' | 'cobalt-green' | 'coconut-brown' | 'coffee' | 'coral' | 'coral-pink'
        | 'cornflower-blue' | 'corn-silk' | 'cream' | 'crimson' | 'cyan' | 'cyan-50' | 'cyan-100' | 'cyan-200' | 'cyan-300' | 'cyan-400' | 'cyan-500' | 'cyan-600' | 'cyan-700' | 'cyan-800'
        | 'cyan-900' | 'cyan-a100' | 'cyan-a200' | 'cyan-a400' | 'cyan-a700' | 'cyan-blue' | 'dark-blue' | 'dark-cyan' | 'dark-goldenrod' | 'dark-gray' | 'dark-green' | 'dark-grey'
        | 'dark-khaki' | 'dark-magenta' | 'dark-mineral-blue' | 'dark-olive-green' | 'dark-orange' | 'dark-orchid' | 'dark-powder-blue' | 'dark-red' | 'dark-salmon'
        | 'dark-sea-green' | 'dark-slate-blue' | 'dark-slate-gray' | 'dark-slate-grey' | 'dark-turquoise' | 'dark-violet' | 'deep-orange' | 'deep-orange-50' | 'deep-orange-100'
        | 'deep-orange-200' | 'deep-orange-300' | 'deep-orange-400' | 'deep-orange-500' | 'deep-orange-600' | 'deep-orange-700' | 'deep-orange-800' | 'deep-orange-900'
        | 'deep-orange-a100' | 'deep-orange-a200' | 'deep-orange-a400' | 'deep-orange-a700' | 'deep-pink' | 'deep-purple' | 'deep-purple-50' | 'deep-purple-100'
        | 'deep-purple-200' | 'deep-purple-300' | 'deep-purple-400' | 'deep-purple-500' | 'deep-purple-600' | 'deep-purple-700' | 'deep-purple-800' | 'deep-purple-900'
        | 'deep-purple-a100' | 'deep-purple-a200' | 'deep-purple-a400' | 'deep-purple-a700' | 'deep-sky-blue' | 'dim-gray' | 'dim-grey' | 'dkgray' | 'dodger-blue' | 'emerald'
        | 'fire-brick' | 'flamingo' | 'floral-white' | 'foliage-green' | 'forest-green' | 'fresh-leaves' | 'fuchsia' | 'gainsboro' | 'ghost-white' | 'gold' | 'golden' | 'goldenrod'
        | 'grass-green' | 'gray' | 'gray-50' | 'gray-100' | 'gray-200' | 'gray-300' | 'gray-400' | 'gray-500' | 'gray-600' | 'gray-700' | 'gray-800' | 'gray-900'
        | 'grayish-purple' | 'green' | 'green-50' | 'green-100' | 'green-200' | 'green-300' | 'green-400' | 'green-500' | 'green-600' | 'green-700'
        | 'green-800' | 'green-900' | 'green-a100' | 'green-a200' | 'green-a400' | 'green-a700' | 'green-yellow' | 'grey' | 'grey-50' | 'grey-100' | 'grey-200' | 'grey-300'
        | 'grey-400' | 'grey-500' | 'grey-600' | 'grey-700' | 'grey-800' | 'grey-900' | 'heliotrope' | 'honeydew' | 'honey-orange' | 'horizon-blue' | 'hot-pink' | 'indian-red'
        | 'indigo' | 'indigo-50' | 'indigo-100' | 'indigo-200' | 'indigo-300' | 'indigo-400' | 'indigo-500' | 'indigo-600' | 'indigo-700' | 'indigo-800' | 'indigo-900'
        | 'indigo-a100' | 'indigo-a200' | 'indigo-a400' | 'indigo-a700' | 'international-klein-blue' | 'iron-gray' | 'ivory' | 'ivy-green' | 'jasmine' | 'khaki' | 'lapis-lazuli'
        | 'lavender' | 'lavender-blue' | 'lavender-blush' | 'lavender-magenta' | 'lavender-mist' | 'lawn-green' | 'lemon-chiffon' | 'light-blue' | 'light-blue-50'
        | 'light-blue-100' | 'light-blue-200' | 'light-blue-300' | 'light-blue-400' | 'light-blue-500' | 'light-blue-600' | 'light-blue-700' | 'light-blue-800'
        | 'light-blue-900' | 'light-blue-a100' | 'light-blue-a200' | 'light-blue-a400' | 'light-blue-a700' | 'light-coral' | 'light-cyan' | 'light-goldenrod-yellow'
        | 'light-gray' | 'light-green' | 'light-green-50' | 'light-green-100' | 'light-green-200' | 'light-green-300' | 'light-green-400' | 'light-green-500' | 'light-green-600'
        | 'light-green-700' | 'light-green-800' | 'light-green-900' | 'light-green-a100' | 'light-green-a200' | 'light-green-a400' | 'light-green-a700' | 'light-grey'
        | 'light-khaki' | 'light-lime' | 'light-pink' | 'light-salmon' | 'light-sea-green' | 'light-sky-blue' | 'light-slate-gray' | 'light-slate-grey' | 'light-steel-blue'
        | 'light-violet' | 'light-yellow' | 'lilac' | 'lime' | 'lime-50' | 'lime-100' | 'lime-200' | 'lime-300' | 'lime-400' | 'lime-500' | 'lime-600' | 'lime-700' | 'lime-800'
        | 'lime-900' | 'lime-a100' | 'lime-a200' | 'lime-a400' | 'lime-a700' | 'lime-green' | 'linen' | 'ltgray' | 'magenta' | 'magenta-rose' | 'malachite' | 'mallow' | 'marigold'
        | 'marine-blue' | 'maroon' | 'mauve' | 'medium-aquamarine' | 'medium-blue' | 'medium-lavender-magenta' | 'medium-orchid' | 'medium-purple' | 'medium-sea-green'
        | 'medium-slate-blue' | 'medium-spring-green' | 'medium-turquoise' | 'medium-violet-red' | 'midnight-blue' | 'mimosa' | 'mineral-blue' | 'mineral-violet' | 'mint'
        | 'mint-cream' | 'misty-rose' | 'moccasin' | 'moon-yellow' | 'moss-green' | 'mustard' | 'navajo-white' | 'navy' | 'navy-blue' | 'ocher' | 'old-lace' | 'old-rose' | 'olive'
        | 'olive-drab' | 'opera-mauve' | 'orange' | 'orange-50' | 'orange-100' | 'orange-200' | 'orange-300' | 'orange-400' | 'orange-500' | 'orange-600' | 'orange-700' | 'orange-800'
        | 'orange-900' | 'orange-a100' | 'orange-a200' | 'orange-a400' | 'orange-a700' | 'orange-red' | 'orchid' | 'pail-lilac' | 'pale-blue' | 'pale-denim' | 'pale-goldenrod'
        | 'pale-green' | 'pale-ochre' | 'pale-turquoise' | 'pale-violet-red' | 'pansy' | 'papaya-whip' | 'patriarch' | 'peach' | 'peach-pearl' | 'peach-puff' | 'peacock-blue'
        | 'peacock-green' | 'pearl-pink' | 'periwinkle' | 'persimmon' | 'peru' | 'pink' | 'pink-50' | 'pink-100' | 'pink-200' | 'pink-300' | 'pink-400' | 'pink-500' | 'pink-600'
        | 'pink-700' | 'pink-800' | 'pink-900' | 'pink-a100' | 'pink-a200' | 'pink-a400' | 'pink-a700' | 'plum' | 'powder-blue' | 'prussian-blue' | 'purple' | 'purple-50' | 'purple-100'
        | 'purple-200' | 'purple-300' | 'purple-400' | 'purple-500' | 'purple-600' | 'purple-700' | 'purple-800' | 'purple-900' | 'purple-a100' | 'purple-a200' | 'purple-a400'
        | 'purple-a700' | 'rebecca-purple' | 'red' | 'red-50' | 'red-100' | 'red-200' | 'red-300' | 'red-400' | 'red-500' | 'red-600' | 'red-700' | 'red-800' | 'red-900' | 'red-a100'
        | 'red-a200' | 'red-a400' | 'red-a700' | 'rose' | 'rose-pink' | 'rosy-brown' | 'royal-blue' | 'ruby' | 'saddle-brown' | 'salmon' | 'salmon-pink' | 'salvia-blue' | 'sand-beige'
        | 'sand-brown' | 'sapphire' | 'saxe-blue' | 'scarlet' | 'seashell' | 'sea-green' | 'sepia' | 'shell-pink' | 'sienna' | 'silver' | 'sky-blue' | 'slate-blue' | 'slate-gray'
        | 'slate-grey' | 'snow' | 'spinel-red' | 'spring-green' | 'steel-blue' | 'strong-blue' | 'strong-red' | 'sun-orange' | 'tan' | 'tangerine' | 'tangerine-yellow' | 'teal'
        | 'teal-50' | 'teal-100' | 'teal-200' | 'teal-300' | 'teal-400' | 'teal-500' | 'teal-600' | 'teal-700' | 'teal-800' | 'teal-900' | 'teal-a100' | 'teal-a200' | 'teal-a400'
        | 'teal-a700' | 'thistle' | 'tomato' | 'transparent' | 'tropical-orange' | 'turquoise' | 'turquoise-blue' | 'turquoise-green' | 'ultramarine' | 'vermilion'
        | 'very-light-malachite-green' | 'violet' | 'viridian' | 'wedgwood-blue' | 'wheat' | 'white' | 'white-1000' | 'white-smoke' | 'wisteria' | 'yellow' | 'yellow-50'
        | 'yellow-100' | 'yellow-200' | 'yellow-300' | 'yellow-400' | 'yellow-500' | 'yellow-600' | 'yellow-700' | 'yellow-800' | 'yellow-900' | 'yellow-a100' | 'yellow-a200'
        | 'yellow-a400' | 'yellow-a700' | 'yellow-green';

    type DetectionAlgorithm = 'diff' | 'rgb' | 'equal' | 'rgb+' | 'hs' | 'h';

    // noinspection SpellCheckingInspection
    type ConversionCodes =
        'BGR2BGRA'
        | 'RGB2RGBA'
        | 'BGRA2BGR'
        | 'RGBA2RGB'
        | 'BGR2RGBA'
        | 'RGB2BGRA'
        | 'RGBA2BGR'
        | 'BGRA2RGB'
        | 'BGR2RGB'
        | 'RGB2BGR'
        | 'BGRA2RGBA'
        | 'RGBA2BGRA'
        | 'BGR2GRAY'
        | 'RGB2GRAY'
        | 'GRAY2BGR'
        | 'GRAY2RGB'
        | 'GRAY2BGRA'
        | 'GRAY2RGBA'
        | 'BGRA2GRAY'
        | 'RGBA2GRAY'
        | 'BGR2BGR565'
        | 'RGB2BGR565'
        | 'BGR5652BGR'
        | 'BGR5652RGB'
        | 'BGRA2BGR565'
        | 'RGBA2BGR565'
        | 'BGR5652BGRA'
        | 'BGR5652RGBA'
        | 'GRAY2BGR565'
        | 'BGR5652GRAY'
        | 'BGR2BGR555'
        | 'RGB2BGR555'
        | 'BGR5552BGR'
        | 'BGR5552RGB'
        | 'BGRA2BGR555'
        | 'RGBA2BGR555'
        | 'BGR5552BGRA'
        | 'BGR5552RGBA'
        | 'GRAY2BGR555'
        | 'BGR5552GRAY'
        | 'BGR2XYZ'
        | 'RGB2XYZ'
        | 'XYZ2BGR'
        | 'XYZ2RGB'
        | 'BGR2YCrCb'
        | 'RGB2YCrCb'
        | 'YCrCb2BGR'
        | 'YCrCb2RGB'
        | 'BGR2HSV'
        | 'RGB2HSV'
        | 'BGR2Lab'
        | 'RGB2Lab'
        | 'BGR2Luv'
        | 'RGB2Luv'
        | 'BGR2HLS'
        | 'RGB2HLS'
        | 'HSV2BGR'
        | 'HSV2RGB'
        | 'Lab2BGR'
        | 'Lab2RGB'
        | 'Luv2BGR'
        | 'Luv2RGB'
        | 'HLS2BGR'
        | 'HLS2RGB'
        | 'BGR2HSV_FULL'
        | 'RGB2HSV_FULL'
        | 'BGR2HLS_FULL'
        | 'RGB2HLS_FULL'
        | 'HSV2BGR_FULL'
        | 'HSV2RGB_FULL'
        | 'HLS2BGR_FULL'
        | 'HLS2RGB_FULL'
        | 'LBGR2Lab'
        | 'LRGB2Lab'
        | 'LBGR2Luv'
        | 'LRGB2Luv'
        | 'Lab2LBGR'
        | 'Lab2LRGB'
        | 'Luv2LBGR'
        | 'Luv2LRGB'
        | 'BGR2YUV'
        | 'RGB2YUV'
        | 'YUV2BGR'
        | 'YUV2RGB'
        | 'YUV2RGB_NV12'
        | 'YUV2BGR_NV12'
        | 'YUV2RGB_NV21'
        | 'YUV2BGR_NV21'
        | 'YUV420sp2RGB'
        | 'YUV420sp2BGR'
        | 'YUV2RGBA_NV12'
        | 'YUV2BGRA_NV12'
        | 'YUV2RGBA_NV21'
        | 'YUV2BGRA_NV21'
        | 'YUV420sp2RGBA'
        | 'YUV420sp2BGRA'
        | 'YUV2RGB_YV12'
        | 'YUV2BGR_YV12'
        | 'YUV2RGB_IYUV'
        | 'YUV2BGR_IYUV'
        | 'YUV2RGB_I420'
        | 'YUV2BGR_I420'
        | 'YUV420p2RGB'
        | 'YUV420p2BGR'
        | 'YUV2RGBA_YV12'
        | 'YUV2BGRA_YV12'
        | 'YUV2RGBA_IYUV'
        | 'YUV2BGRA_IYUV'
        | 'YUV2RGBA_I420'
        | 'YUV2BGRA_I420'
        | 'YUV420p2RGBA'
        | 'YUV420p2BGRA'
        | 'YUV2GRAY_420'
        | 'YUV2GRAY_NV21'
        | 'YUV2GRAY_NV12'
        | 'YUV2GRAY_YV12'
        | 'YUV2GRAY_IYUV'
        | 'YUV2GRAY_I420'
        | 'YUV420sp2GRAY'
        | 'YUV420p2GRAY'
        | 'YUV2RGB_UYVY'
        | 'YUV2BGR_UYVY'
        | 'YUV2RGB_Y422'
        | 'YUV2BGR_Y422'
        | 'YUV2RGB_UYNV'
        | 'YUV2BGR_UYNV'
        | 'YUV2RGBA_UYVY'
        | 'YUV2BGRA_UYVY'
        | 'YUV2RGBA_Y422'
        | 'YUV2BGRA_Y422'
        | 'YUV2RGBA_UYNV'
        | 'YUV2BGRA_UYNV'
        | 'YUV2RGB_YUY2'
        | 'YUV2BGR_YUY2'
        | 'YUV2RGB_YVYU'
        | 'YUV2BGR_YVYU'
        | 'YUV2RGB_YUYV'
        | 'YUV2BGR_YUYV'
        | 'YUV2RGB_YUNV'
        | 'YUV2BGR_YUNV'
        | 'YUV2RGBA_YUY2'
        | 'YUV2BGRA_YUY2'
        | 'YUV2RGBA_YVYU'
        | 'YUV2BGRA_YVYU'
        | 'YUV2RGBA_YUYV'
        | 'YUV2BGRA_YUYV'
        | 'YUV2RGBA_YUNV'
        | 'YUV2BGRA_YUNV'
        | 'YUV2GRAY_UYVY'
        | 'YUV2GRAY_YUY2'
        | 'YUV2GRAY_Y422'
        | 'YUV2GRAY_UYNV'
        | 'YUV2GRAY_YVYU'
        | 'YUV2GRAY_YUYV'
        | 'YUV2GRAY_YUNV'
        | 'RGBA2mRGBA'
        | 'mRGBA2RGBA'
        | 'RGB2YUV_I420'
        | 'BGR2YUV_I420'
        | 'RGB2YUV_IYUV'
        | 'BGR2YUV_IYUV'
        | 'RGBA2YUV_I420'
        | 'BGRA2YUV_I420'
        | 'RGBA2YUV_IYUV'
        | 'BGRA2YUV_IYUV'
        | 'RGB2YUV_YV12'
        | 'BGR2YUV_YV12'
        | 'RGBA2YUV_YV12'
        | 'BGRA2YUV_YV12'
        | 'BayerBG2BGR'
        | 'BayerGB2BGR'
        | 'BayerRG2BGR'
        | 'BayerGR2BGR'
        | 'BayerBG2RGB'
        | 'BayerGB2RGB'
        | 'BayerRG2RGB'
        | 'BayerGR2RGB'
        | 'BayerBG2GRAY'
        | 'BayerGB2GRAY'
        | 'BayerRG2GRAY'
        | 'BayerGR2GRAY'
        | 'BayerBG2BGR_VNG'
        | 'BayerGB2BGR_VNG'
        | 'BayerRG2BGR_VNG'
        | 'BayerGR2BGR_VNG'
        | 'BayerBG2RGB_VNG'
        | 'BayerGB2RGB_VNG'
        | 'BayerRG2RGB_VNG'
        | 'BayerGR2RGB_VNG'
        | 'BayerBG2BGR_EA'
        | 'BayerGB2BGR_EA'
        | 'BayerRG2BGR_EA'
        | 'BayerGR2BGR_EA'
        | 'BayerBG2RGB_EA'
        | 'BayerGB2RGB_EA'
        | 'BayerRG2RGB_EA'
        | 'BayerGR2RGB_EA'
        | 'BayerBG2BGRA'
        | 'BayerGB2BGRA'
        | 'BayerRG2BGRA'
        | 'BayerGR2BGRA'
        | 'BayerBG2RGBA'
        | 'BayerGB2RGBA'
        | 'BayerRG2RGBA'
        | 'BayerGR2RGBA'
        | 'COLORCVT_MAX';
}

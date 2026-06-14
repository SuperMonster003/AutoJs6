// Type definitions for AutoJs6 internal module opencc
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 5.7.3
//
// Last modified: Jun 14, 2026

/// <reference path="../index.d.ts" />

/**
 * @Source /src/main/java/org/autojs/autojs/runtime/api/augment/opencc/OpenCC.kt
 */

declare namespace Internal {

    import ConversionType = com.zqc.opencc.android.lib.ConversionType;

    interface OpenCC {

        (s: string, type: OpenCC.ConversionTypeName | ConversionType): string;

        convert(s: string, type: OpenCC.ConversionTypeName | ConversionType): string;

        /**
         * ### HK2S
         * - Traditional Chinese (Hong Kong Standard) to Simplified Chinese
         * - 香港繁体 (香港小学学习字词表标准) 到简体
         * - 香港繁體 (香港小學學習字詞表標準) 到簡體
         */
        hk2s(s: string): string;

        /**
         * ### HK2T
         * - Traditional Chinese (Hong Kong variant) to Traditional Chinese
         * - 香港繁体 (香港小学学习字词表标准) 到繁体
         * - 香港繁體 (香港小學學習字詞表標準) 到繁體
         */
        hk2t(s: string): string;

        /**
         * ### JP2T
         * - New Japanese Kanji (Shinjitai) to Traditional Chinese Characters (Kyūjitai)
         * - 日本汉字到繁体
         * - 日本漢字到繁體
         */
        jp2t(s: string): string;

        /**
         * ### S2HK
         * - Simplified Chinese to Traditional Chinese (Hong Kong Standard)
         * - 简体到香港繁体 (香港小学学习字词表标准)
         * - 簡體到香港繁體 (香港小學學習字詞表標準)
         */
        s2hk(s: string): string;

        /**
         * ### S2T
         * - Simplified Chinese to Traditional Chinese
         * - 简体到繁体
         * - 簡體到繁體
         */
        s2t(s: string): string;

        /**
         * ### S2TW
         * - Simplified Chinese to Traditional Chinese (Taiwan Standard)
         * - 简体到台湾正体
         * - 簡體到臺灣正體
         */
        s2tw(s: string): string;

        /**
         * ### S2TWP
         * - Simplified Chinese to Traditional Chinese (Taiwan Standard) [with idiom]
         * - 简体到繁体 (台湾正体标准) [惯用词]
         * - 簡體到繁體 (臺灣正體標準) [慣用詞]
         *
         * @deprecated Use `s2twi` instead.
         */
        s2twp(s: string): string;

        /**
         * ### S2TWI
         * - Simplified Chinese to Traditional Chinese (Taiwan Standard) [with idiom]
         * - 简体到繁体 (台湾正体标准) [惯用词]
         * - 簡體到繁體 (臺灣正體標準) [慣用詞]
         */
        s2twi(s: string): string;

        /**
         * ### T2HK
         * - Traditional Chinese to Traditional Chinese (Hong Kong Standard)
         * - 繁体到香港繁体 (香港小学学习字词表标准)
         * - 繁體到香港繁體 (香港小學學習字詞表標準)
         */
        t2hk(s: string): string;

        /**
         * ### T2S
         * - Traditional Chinese to Simplified Chinese
         * - 繁体到简体
         * - 繁體到簡體
         */
        t2s(s: string): string;

        /**
         * ### T2TW
         * - Traditional Chinese to Traditional Chinese (Taiwan Standard)
         * - 繁体到台湾正体
         * - 繁體到臺灣正體
         */
        t2tw(s: string): string;

        /**
         * ### TW2S
         * - Traditional Chinese (Taiwan Standard) to Simplified Chinese
         * - 台湾正体到简体
         * - 臺灣正體到簡體
         */
        tw2s(s: string): string;

        /**
         * ### T2JP
         * - Traditional Chinese Characters (Kyūjitai) to New Japanese Kanji (Shinjitai)
         * - 繁体到日本汉字
         * - 繁體到日本漢字
         */
        t2jp(s: string): string;

        /**
         * ### TW2T
         * - Traditional Chinese (Taiwan standard) to Traditional Chinese
         * - 台湾正体到繁体
         * - 臺灣正體到繁體
         */
        tw2t(s: string): string;

        /**
         * ### TW2SP
         * - Traditional Chinese (Taiwan Standard) [with idiom] to Simplified Chinese
         * - 繁体 (台湾正体标准) [惯用词] 到简体
         * - 繁體 (臺灣正體標準) [慣用詞] 到簡體
         *
         * @deprecated Use `TWI2S` instead.
         */
        tw2sp(s: string): string;

        /**
         * ### TWI2S
         * - Traditional Chinese (Taiwan Standard) [with idiom] to Simplified Chinese
         * - 繁体 (台湾正体标准) [惯用词] 到简体
         * - 繁體 (臺灣正體標準) [慣用詞] 到簡體
         */
        twi2s(s: string): string;

        /**
         * ### S2JP
         * - Simplified Chinese to New Japanese Kanji (Shinjitai)
         * - 简体到日本汉字
         * - 簡體到日本漢字
         */
        s2jp(s: string): string;

        /**
         * ### T2TWI
         * - Traditional Chinese to Traditional Chinese (Taiwan Standard) [with idiom]
         * - 繁体到台湾正体 [惯用词]
         * - 繁體到臺灣正體 [慣用詞]
         */
        t2twi(s: string): string;

        /**
         * ### HK2TW
         * - Traditional Chinese (Hong Kong variant) to Traditional Chinese (Taiwan Standard)
         * - 香港繁体 (香港小学学习字词表标准) 到台湾正体
         * - 香港繁體 (香港小學學習字詞表標準) 到臺灣正體
         */
        hk2tw(s: string): string;

        /**
         * ### HK2TWI
         * - Traditional Chinese (Hong Kong variant) to Traditional Chinese (Taiwan Standard) [with idiom]
         * - 香港繁体 (香港小学学习字词表标准) 到台湾正体 [惯用词]
         * - 香港繁體 (香港小學學習字詞表標準) 到臺灣正體 [慣用詞]
         */
        hk2twi(s: string): string;

        /**
         * ### HK2JP
         * - Traditional Chinese (Hong Kong variant) to New Japanese Kanji (Shinjitai)
         * - 香港繁体 (香港小学学习字词表标准) 到日本汉字
         * - 香港繁體 (香港小學學習字詞表標準) 到日本漢字
         */
        hk2jp(s: string): string;

        /**
         * ### TW2HK
         * - Traditional Chinese (Taiwan Standard) to Traditional Chinese (Hong Kong Standard)
         * - 台湾正体到香港繁体 (香港小学学习字词表标准)
         * - 臺灣正體到香港繁體 (香港小學學習字詞表標準)
         */
        tw2hk(s: string): string;

        /**
         * ### TW2TWI
         * - Traditional Chinese (Taiwan Standard) to Traditional Chinese (Taiwan Standard) [with idiom]
         * - 台湾正体到台湾正体 [惯用词]
         * - 臺灣正體到臺灣正體 [慣用詞]
         */
        tw2twi(s: string): string;

        /**
         * ### TW2JP
         * - Traditional Chinese (Taiwan Standard) to New Japanese Kanji (Shinjitai)
         * - 台湾正体到日本汉字
         * - 臺灣正體到日本漢字
         */
        tw2jp(s: string): string;

        /**
         * ### TWI2T
         * - Traditional Chinese (Taiwan Standard) [with idiom] to Traditional Chinese
         * - 台湾正体 [惯用词] 到繁体
         * - 臺灣正體 [慣用詞] 到繁體
         */
        twi2t(s: string): string;

        /**
         * ### TWI2HK
         * - Traditional Chinese (Taiwan Standard) [with idiom] to Traditional Chinese (Hong Kong Standard)
         * - 台湾正体 [惯用词] 到香港繁体 (香港小学学习字词表标准)
         * - 臺灣正體 [慣用詞] 到香港繁體 (香港小學學習字詞表標準)
         */
        twi2hk(s: string): string;

        /**
         * ### TWI2TW
         * - Traditional Chinese (Taiwan Standard) [with idiom] to Traditional Chinese (Taiwan Standard)
         * - 台湾正体 [惯用词] 到台湾正体
         * - 臺灣正體 [慣用詞] 到臺灣正體
         */
        twi2tw(s: string): string;

        /**
         * ### TWI2JP
         * - Traditional Chinese (Taiwan Standard) [with idiom] to New Japanese Kanji (Shinjitai)
         * - 台湾正体 [惯用词] 到日本汉字
         * - 臺灣正體 [慣用詞] 到日本漢字
         */
        twi2jp(s: string): string;

        /**
         * ### JP2S
         * - New Japanese Kanji (Shinjitai) to Simplified Chinese
         * - 日本汉字到简体
         * - 日本漢字到簡體
         */
        jp2s(s: string): string;

        /**
         * ### JP2HK
         * - New Japanese Kanji (Shinjitai) to Traditional Chinese (Hong Kong Standard)
         * - 日本汉字到香港繁体 (香港小学学习字词表标准)
         * - 日本漢字到香港繁體 (香港小學學習字詞表標準)
         */
        jp2hk(s: string): string;

        /**
         * ### JP2TW
         * - New Japanese Kanji (Shinjitai) to Traditional Chinese (Taiwan Standard)
         * - 日本汉字到台湾正体
         * - 日本漢字到臺灣正體
         */
        jp2tw(s: string): string;

        /**
         * ### JP2TWI
         * - New Japanese Kanji (Shinjitai) to Traditional Chinese (Taiwan Standard) [with idiom]
         * - 日本汉字到台湾正体 [惯用词]
         * - 日本漢字到臺灣正體 [慣用詞]
         */
        jp2twi(s: string): string;

    }

}

declare namespace OpenCC {

    type ConversionTypeName = ConversionTypeNameOpenCC | ConversionTypeNameEncapsulation;

    type ConversionTypeNameOpenCC = 'HK2S' | 'HK2T' | 'JP2T' | 'S2HK' | 'S2T' | 'S2TW' | 'S2TWP' | 'T2HK' | 'T2S' | 'T2TW' | 'T2JP' | 'TW2S' | 'TW2T' | 'TW2SP';
    type ConversionTypeNameEncapsulation = 'S2TWI' | 'TWI2S' | 'S2JP' | 'T2TWI' | 'HK2TW' | 'HK2TWI' | 'HK2JP' | 'TW2HK' | 'TW2TWI' | 'TW2JP' | 'TWI2T' | 'TWI2HK' | 'TWI2TW' | 'TWI2JP' | 'JP2S' | 'JP2HK' | 'JP2TW' | 'JP2TWI';
}

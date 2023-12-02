package org.autojs.autojs.model.autocomplete;

import java.util.Arrays;

/**
 * Created by Stardust on Sep 28, 2017.
 * Modified by SuperMonster003 as of Aug 24, 2022.
 */
public class Symbols {

    private static final CodeCompletions sSymbols = CodeCompletions.just(Arrays.asList(
            ",", ".", "=", ";", "\"", "'", "/", "-", "_",
            "(", ")", "[", "]", "{", "}", "<", ">",
            "+", "*", "?", ":", "$", "#", "@", "`",
            "\\", "&", "|", "!", "%", "×", "÷",
            "∈", "∩", "∪", "∉", "⊙", "∅", "¥", "€",
            "°", "℃", "∵", "∴", "±", "≠", "≈",
            "α", "β", "γ", "λ", "μ", "π", "σ", "ω",
            "®", "©", "♂", "♀", "√", "×", "✔", "✘",
            "♥", "♠", "♦", "♣", "★", "◀", "▶", "●", "■", "▲", "◆"
    ));

    public static CodeCompletions getSymbols() {
        return sSymbols;
    }
}

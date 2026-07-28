package org.autojs.autojs.ui.edit.theme

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generates three opaque foreground colors for one opaque background:
 *
 *  - A / [Palette.categoryA]: category color, strongest
 *  - B / [Palette.titleB]: main-title color
 *  - C / [Palette.subtitleC]: subtitle color, most subdued
 *
 * The generator keeps the input B hue in OKLCH. It changes only OKLCH chroma
 * (used here as the perceptual saturation control) and lightness (therefore
 * background contrast). Exact neutral
 * input colors remain neutral and are changed only in lightness; callers may
 * optionally extend that treatment to near-neutral colors.
 *
 * `separation` is normalized to 0.0..1.0:
 *
 *  - 0.0: A == B == C
 *  - 1.0: use the largest requested hierarchy that the background and
 *         [Options.minContrast] permit
 *
 * The implementation is Android-independent and returns 0xFFRRGGBB Int values.
 */
object ColorThemeGenerator {

    enum class Polarity {
        LIGHTER_THAN_BACKGROUND,
        DARKER_THAN_BACKGROUND,
    }

    data class Options(
        /** WCAG contrast ratio against Z. 4.5 is suitable for normal-size text. */
        val minContrast: Double = 4.5,
        /** At separation=1, A targets up to 1.60 times B's contrast. */
        val categoryContrastFactor: Double = 1.60,
        /** At separation=1, B targets up to 1.40 times C's contrast. */
        val subtitleContrastFactor: Double = 1.40,
        /** At separation=1, A requests 45% more OKLCH chroma than input B. */
        val categorySaturationBoost: Double = 0.45,
        /** At separation=1, C loses 55% of input B's OKLCH chroma. */
        val subtitleSaturationReduction: Double = 0.55,
        /**
         * Optional near-gray coercion threshold. The default 0.0 means only exact
         * R == G == B colors are treated as gray, preserving every chromatic hue.
         */
        val achromaticChromaThreshold: Double = 0.0,
        /** 512 samples are enough to resolve final 8-bit colors reliably. */
        val lightnessSteps: Int = 512,
        /** Iterations used to find the in-gamut chroma boundary at a given L/h. */
        val gamutIterations: Int = 18,
    ) {
        init {
            require(minContrast in 1.0..21.0) {
                "minContrast must be in 1.0..21.0"
            }
            require(
                categoryContrastFactor.isFiniteNumber() &&
                    categoryContrastFactor >= 1.0,
            ) {
                "categoryContrastFactor must be >= 1.0"
            }
            require(
                subtitleContrastFactor.isFiniteNumber() &&
                    subtitleContrastFactor >= 1.0,
            ) {
                "subtitleContrastFactor must be >= 1.0"
            }
            require(categorySaturationBoost in 0.0..1.0) {
                "categorySaturationBoost must be in 0.0..1.0"
            }
            require(subtitleSaturationReduction in 0.0..1.0) {
                "subtitleSaturationReduction must be in 0.0..1.0"
            }
            require(achromaticChromaThreshold in 0.0..0.05) {
                "achromaticChromaThreshold must be in 0.0..0.05"
            }
            require(lightnessSteps in 64..4096) {
                "lightnessSteps must be in 64..4096"
            }
            require(gamutIterations in 8..30) {
                "gamutIterations must be in 8..30"
            }
        }
    }

    data class Swatch(
        /** Opaque Android ARGB Int: 0xFFRRGGBB. */
        val argb: Int,
        /** Actual WCAG contrast after 8-bit quantization. */
        val contrastAgainstBackground: Double,
        /** Contrast target used by the role search. */
        val targetContrast: Double,
        /** Actual quantized OKLab L. */
        val lightness: Double,
        /** Actual quantized OKLCH C. */
        val chroma: Double,
        /** Actual hue in degrees, or null for an achromatic palette. */
        val hueDegrees: Double?,
        /** Requested OKLCH chroma before fixed-hue sRGB gamut reduction. */
        val requestedChroma: Double,
    ) {
        val hex: String
            get() = toHex(argb)
    }

    data class Palette(
        val categoryA: Swatch,
        val titleB: Swatch,
        val subtitleC: Swatch,
        val backgroundZ: Int,
        val requestedSeparation: Double,
        /** 1.0 means the requested contrast gaps fit; smaller means they were compressed. */
        val contrastGapScale: Double,
        val polarity: Polarity,
        val achromatic: Boolean,
    ) {
        val a: Int
            get() = categoryA.argb
        val b: Int
            get() = titleB.argb
        val c: Int
            get() = subtitleC.argb
        val z: Int
            get() = backgroundZ
        val effectiveContrastSeparation: Double
            get() = requestedSeparation * contrastGapScale
        val contrastHierarchyCompressed: Boolean
            get() = contrastGapScale < 0.999999
        val actualCategoryTitleContrastFactor: Double
            get() = categoryA.contrastAgainstBackground /
                titleB.contrastAgainstBackground
        val actualTitleSubtitleContrastFactor: Double
            get() = titleB.contrastAgainstBackground /
                subtitleC.contrastAgainstBackground
        val colorsAreDistinct: Boolean
            get() = a != b && b != c && a != c
    }

    @JvmStatic
    @JvmOverloads
    fun generate(
        backgroundHex: String,
        titleHex: String,
        separation: Double,
        options: Options = Options(),
    ): Palette = generate(
        backgroundArgb = parseOpaqueHex(backgroundHex),
        titleArgb = parseOpaqueHex(titleHex),
        separation = separation,
        options = options,
    )

    @JvmStatic
    @JvmOverloads
    fun generate(
        backgroundArgb: Int,
        titleArgb: Int,
        separation: Double,
        options: Options = Options(),
    ): Palette {
        val background = opaque(backgroundArgb)
        val inputTitle = opaque(titleArgb)
        require(separation.isFiniteNumber()) {
            "separation must be finite"
        }
        val p = separation.coerceIn(0.0, 1.0)

        val backgroundY = relativeLuminance(background)
        val inputY = relativeLuminance(inputTitle)
        val inputContrast = contrastFromLuminances(inputY, backgroundY)
        val inputLab = argbToLab(inputTitle)
        val inputLch = inputLab.toLch()

        val exactGray = channel(inputTitle, 16) == channel(inputTitle, 8) &&
            channel(inputTitle, 8) == channel(inputTitle, 0)
        val achromatic = exactGray ||
            inputLch.c < options.achromaticChromaThreshold

        val hue = if (achromatic) 0.0 else inputLch.h
        val cosH = cos(hue)
        val sinH = sin(hue)
        // Keep B's absolute OKLCH chroma. A relative-to-gamut saturation would
        // turn a barely tinted near-white B into a vivid color when B is
        // darkened for contrast.
        val baseChroma = if (achromatic) 0.0 else inputLch.c

        val originalPolarity = if (inputY >= backgroundY) {
            Polarity.LIGHTER_THAN_BACKGROUND
        } else {
            Polarity.DARKER_THAN_BACKGROUND
        }

        // Preserve an already-readable B exactly when no role separation is requested.
        if (
            p <= EPSILON &&
            inputContrast >= options.minContrast &&
            (!achromatic || exactGray)
        ) {
            val swatch = swatchFromArgb(
                argb = inputTitle,
                background = background,
                targetContrast = inputContrast,
                requestedChroma = baseChroma,
                achromatic = achromatic,
            )
            return Palette(
                categoryA = swatch,
                titleB = swatch,
                subtitleC = swatch,
                backgroundZ = background,
                requestedSeparation = p,
                contrastGapScale = 1.0,
                polarity = originalPolarity,
                achromatic = achromatic,
            )
        }

        val cMaxCache = DoubleArray(options.lightnessSteps + 1)
        if (!achromatic) {
            for (i in cMaxCache.indices) {
                cMaxCache[i] = maxChromaAt(
                    lightness = i / options.lightnessSteps.toDouble(),
                    cosH = cosH,
                    sinH = sinH,
                    iterations = options.gamutIterations,
                )
            }
        }

        val plans = Polarity.values().mapNotNull { polarity ->
            buildPolarityPlan(
                polarity = polarity,
                background = background,
                backgroundY = backgroundY,
                inputContrast = inputContrast,
                inputLab = inputLab,
                inputLightness = inputLch.l,
                baseChroma = baseChroma,
                separation = p,
                achromatic = achromatic,
                cosH = cosH,
                sinH = sinH,
                cMaxCache = cMaxCache,
                originalPolarity = originalPolarity,
                options = options,
            )
        }

        require(plans.isNotEmpty()) {
            "No opaque sRGB foreground can reach contrast ${options.minContrast} " +
                "against ${toHex(background)}. Lower Options.minContrast."
        }

        val selected = plans.minWithOrNull(
            compareBy<PolarityPlan> { it.score }
                .thenBy { if (it.polarity == originalPolarity) 0 else 1 }
                .thenByDescending { it.maxContrast },
        )!!

        val chromaA = if (achromatic) {
            0.0
        } else {
            baseChroma * (1.0 + options.categorySaturationBoost * p)
        }
        val chromaB = baseChroma
        val chromaC = if (achromatic) {
            0.0
        } else {
            baseChroma * (1.0 - options.subtitleSaturationReduction * p)
        }.coerceAtLeast(0.0)

        val direction = if (selected.polarity == Polarity.LIGHTER_THAN_BACKGROUND) {
            1.0
        } else {
            -1.0
        }
        val anchorA = (inputLch.l + direction * 0.08 * p).coerceIn(0.0, 1.0)
        val anchorB = inputLch.l
        val anchorC = (inputLch.l - direction * 0.08 * p).coerceIn(0.0, 1.0)

        val subtitle = pickRole(
            targetContrast = selected.targets.subtitle,
            minimumContrast = options.minContrast,
            requestedChroma = chromaC,
            anchorLightness = anchorC,
            polarity = selected.polarity,
            background = background,
            backgroundY = backgroundY,
            achromatic = achromatic,
            cosH = cosH,
            sinH = sinH,
            cMaxCache = cMaxCache,
            options = options,
        )
        val title = pickRole(
            targetContrast = selected.targets.title,
            minimumContrast = max(
                options.minContrast,
                subtitle.contrastAgainstBackground,
            ),
            requestedChroma = chromaB,
            anchorLightness = anchorB,
            polarity = selected.polarity,
            background = background,
            backgroundY = backgroundY,
            achromatic = achromatic,
            cosH = cosH,
            sinH = sinH,
            cMaxCache = cMaxCache,
            options = options,
        )
        val category = pickRole(
            targetContrast = selected.targets.category,
            minimumContrast = max(
                options.minContrast,
                title.contrastAgainstBackground,
            ),
            requestedChroma = chromaA,
            anchorLightness = anchorA,
            polarity = selected.polarity,
            background = background,
            backgroundY = backgroundY,
            achromatic = achromatic,
            cosH = cosH,
            sinH = sinH,
            cMaxCache = cMaxCache,
            options = options,
        )

        return Palette(
            categoryA = category,
            titleB = title,
            subtitleC = subtitle,
            backgroundZ = background,
            requestedSeparation = p,
            contrastGapScale = selected.gapScale,
            polarity = selected.polarity,
            achromatic = achromatic,
        )
    }

    /**
     * Parses Android-style #RGB, #ARGB, #RRGGBB, or #AARRGGBB.
     * Alpha is intentionally ignored and the result is always opaque.
     */
    @JvmStatic
    fun parseOpaqueHex(input: String): Int {
        var value = input.trim()
        if (value.startsWith("#")) value = value.substring(1)
        if (value.startsWith("0x", ignoreCase = true)) value = value.substring(2)

        require(
            value.length == 3 || value.length == 4 ||
                value.length == 6 || value.length == 8,
        ) {
            "Expected #RGB, #ARGB, #RRGGBB, or #AARRGGBB: $input"
        }
        require(value.all(::isHexDigit)) {
            "Invalid hexadecimal color: $input"
        }

        val rgb = when (value.length) {
            3 -> buildString(6) {
                for (ch in value) {
                    append(ch)
                    append(ch)
                }
            }
            4 -> buildString(6) {
                // Android shorthand is #ARGB; discard A.
                for (i in 1..3) {
                    append(value[i])
                    append(value[i])
                }
            }
            6 -> value
            else -> value.substring(2) // #AARRGGBB; discard AA.
        }
        return 0xFF000000.toInt() or rgb.toInt(16)
    }

    @JvmStatic
    fun opaque(argb: Int): Int =
        0xFF000000.toInt() or (argb and 0x00FFFFFF)

    @JvmStatic
    fun toHex(argb: Int): String {
        val rgb = opaque(argb)
        val chars = CharArray(7)
        chars[0] = '#'
        for (i in 0 until 6) {
            val shift = (5 - i) * 4
            chars[i + 1] = HEX_DIGITS[(rgb ushr shift) and 0xF]
        }
        return String(chars)
    }

    @JvmStatic
    fun relativeLuminance(argb: Int): Double {
        val color = opaque(argb)
        val r = srgbToLinear(channel(color, 16) / 255.0)
        val g = srgbToLinear(channel(color, 8) / 255.0)
        val b = srgbToLinear(channel(color, 0) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    @JvmStatic
    fun contrastRatio(firstArgb: Int, secondArgb: Int): Double =
        contrastFromLuminances(
            relativeLuminance(firstArgb),
            relativeLuminance(secondArgb),
        )

    private data class Lab(val l: Double, val a: Double, val b: Double) {
        fun toLch(): Lch {
            val c = sqrt(a * a + b * b)
            var h = atan2(b, a)
            if (h < 0.0) h += 2.0 * PI
            return Lch(l, c, h)
        }
    }

    private data class Lch(val l: Double, val c: Double, val h: Double)

    private data class LinearRgb(val r: Double, val g: Double, val b: Double) {
        fun inGamut(): Boolean =
            r >= -GAMUT_EPSILON && r <= 1.0 + GAMUT_EPSILON &&
                g >= -GAMUT_EPSILON && g <= 1.0 + GAMUT_EPSILON &&
                b >= -GAMUT_EPSILON && b <= 1.0 + GAMUT_EPSILON
    }

    private data class ContrastTargets(
        val category: Double,
        val title: Double,
        val subtitle: Double,
    )

    private data class PolarityPlan(
        val polarity: Polarity,
        val maxContrast: Double,
        val gapScale: Double,
        val targets: ContrastTargets,
        val score: Double,
    )

    private fun buildPolarityPlan(
        polarity: Polarity,
        background: Int,
        backgroundY: Double,
        inputContrast: Double,
        inputLab: Lab,
        inputLightness: Double,
        baseChroma: Double,
        separation: Double,
        achromatic: Boolean,
        cosH: Double,
        sinH: Double,
        cMaxCache: DoubleArray,
        originalPolarity: Polarity,
        options: Options,
    ): PolarityPlan? {
        val endpoint = if (polarity == Polarity.LIGHTER_THAN_BACKGROUND) {
            OPAQUE_WHITE
        } else {
            OPAQUE_BLACK
        }
        val maxContrast = contrastRatio(endpoint, background)
        if (maxContrast < options.minContrast) return null

        val categoryGap = ln(options.categoryContrastFactor) * separation
        val subtitleGap = ln(options.subtitleContrastFactor) * separation
        val totalRequestedGap = categoryGap + subtitleGap
        val availableGap = ln(maxContrast / options.minContrast)
            .coerceAtLeast(0.0)
        val gapScale = if (totalRequestedGap <= EPSILON) {
            1.0
        } else {
            min(1.0, availableGap / totalRequestedGap)
        }

        val scaledCategoryGap = categoryGap * gapScale
        val scaledSubtitleGap = subtitleGap * gapScale
        val minCenter = ln(options.minContrast) + scaledSubtitleGap
        val maxCenter = ln(maxContrast) - scaledCategoryGap
        val preferredCenter = ln(max(inputContrast, options.minContrast))
        // At a fully compressed boundary, floating-point rounding can make
        // minCenter exceed maxCenter by ~1e-16. Treat that as one point.
        val center = if (minCenter <= maxCenter) {
            preferredCenter.coerceIn(minCenter, maxCenter)
        } else {
            (minCenter + maxCenter) * 0.5
        }

        val targets = ContrastTargets(
            category = exp(center + scaledCategoryGap),
            title = exp(center),
            subtitle = exp(center - scaledSubtitleGap),
        )

        val candidate = pickRole(
            targetContrast = targets.title,
            minimumContrast = options.minContrast,
            requestedChroma = baseChroma,
            anchorLightness = inputLightness,
            polarity = polarity,
            background = background,
            backgroundY = backgroundY,
            achromatic = achromatic,
            cosH = cosH,
            sinH = sinH,
            cMaxCache = cMaxCache,
            options = options,
        )
        val candidateLab = argbToLab(candidate.argb)
        val deltaE = sqrt(
            square(candidateLab.l - inputLab.l) +
                square(candidateLab.a - inputLab.a) +
                square(candidateLab.b - inputLab.b),
        )
        val compressionPenalty = 0.12 * separation * (1.0 - gapScale)
        val sidePenalty = if (polarity == originalPolarity) 0.0 else 1e-9

        return PolarityPlan(
            polarity = polarity,
            maxContrast = maxContrast,
            gapScale = gapScale,
            targets = targets,
            score = deltaE + compressionPenalty + sidePenalty,
        )
    }

    private fun pickRole(
        targetContrast: Double,
        minimumContrast: Double,
        requestedChroma: Double,
        anchorLightness: Double,
        polarity: Polarity,
        background: Int,
        backgroundY: Double,
        achromatic: Boolean,
        cosH: Double,
        sinH: Double,
        cMaxCache: DoubleArray,
        options: Options,
    ): Swatch {
        var bestArgb: Int? = null
        var bestContrast = 0.0
        var bestContrastError = Double.POSITIVE_INFINITY
        var bestAnchorError = Double.POSITIVE_INFINITY

        for (i in 0..options.lightnessSteps) {
            val lightness = i / options.lightnessSteps.toDouble()
            val chroma = if (achromatic) {
                0.0
            } else {
                min(requestedChroma, cMaxCache[i])
            }
            val linear = oklchToLinearRgb(lightness, chroma, cosH, sinH)
            val argb = linearRgbToArgb(linear)
            val foregroundY = relativeLuminance(argb)
            val onRequestedSide = if (polarity == Polarity.LIGHTER_THAN_BACKGROUND) {
                foregroundY + LUMINANCE_EPSILON >= backgroundY
            } else {
                foregroundY <= backgroundY + LUMINANCE_EPSILON
            }
            if (!onRequestedSide) continue

            val actualContrast = contrastFromLuminances(foregroundY, backgroundY)
            if (actualContrast < minimumContrast) continue

            val contrastError = abs(ln(actualContrast / targetContrast))
            val anchorError = abs(lightness - anchorLightness)
            val isBetter = contrastError < bestContrastError - SCORE_EPSILON ||
                (
                    abs(contrastError - bestContrastError) <= SCORE_EPSILON &&
                        anchorError < bestAnchorError - SCORE_EPSILON
                    )

            if (isBetter) {
                bestArgb = argb
                bestContrast = actualContrast
                bestContrastError = contrastError
                bestAnchorError = anchorError
            }
        }

        val argb = requireNotNull(bestArgb) {
            "Unable to find a ${polarity.name.lowercase()} foreground with " +
                "contrast >= $minimumContrast against ${toHex(background)}"
        }
        return swatchFromArgb(
            argb = argb,
            background = background,
            targetContrast = targetContrast,
            requestedChroma = requestedChroma,
            achromatic = achromatic,
            knownContrast = bestContrast,
        )
    }

    private fun swatchFromArgb(
        argb: Int,
        background: Int,
        targetContrast: Double,
        requestedChroma: Double,
        achromatic: Boolean,
        knownContrast: Double? = null,
    ): Swatch {
        val color = opaque(argb)
        val lab = argbToLab(color)
        val lch = lab.toLch()
        val hueDegrees = if (achromatic || lch.c < OUTPUT_HUE_CHROMA_THRESHOLD) {
            null
        } else {
            lch.h * 180.0 / PI
        }
        return Swatch(
            argb = color,
            contrastAgainstBackground = knownContrast ?: contrastRatio(color, background),
            targetContrast = targetContrast,
            lightness = lch.l,
            chroma = if (achromatic) 0.0 else lch.c,
            hueDegrees = hueDegrees,
            requestedChroma = if (achromatic) 0.0 else requestedChroma,
        )
    }

    private fun argbToLab(argb: Int): Lab {
        val color = opaque(argb)
        val r = srgbToLinear(channel(color, 16) / 255.0)
        val g = srgbToLinear(channel(color, 8) / 255.0)
        val b = srgbToLinear(channel(color, 0) / 255.0)

        val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
        val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
        val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b

        val lp = Math.cbrt(l)
        val mp = Math.cbrt(m)
        val sp = Math.cbrt(s)

        return Lab(
            l = 0.2104542553 * lp + 0.7936177850 * mp - 0.0040720468 * sp,
            a = 1.9779984951 * lp - 2.4285922050 * mp + 0.4505937099 * sp,
            b = 0.0259040371 * lp + 0.7827717662 * mp - 0.8086757660 * sp,
        )
    }

    private fun oklchToLinearRgb(
        lightness: Double,
        chroma: Double,
        cosH: Double,
        sinH: Double,
    ): LinearRgb {
        val a = chroma * cosH
        val b = chroma * sinH

        val lp = lightness + 0.3963377774 * a + 0.2158037573 * b
        val mp = lightness - 0.1055613458 * a - 0.0638541728 * b
        val sp = lightness - 0.0894841775 * a - 1.2914855480 * b

        val l = lp * lp * lp
        val m = mp * mp * mp
        val s = sp * sp * sp

        return LinearRgb(
            r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            b = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
    }

    private fun maxChromaAt(
        lightness: Double,
        cosH: Double,
        sinH: Double,
        iterations: Int,
    ): Double {
        if (lightness <= EPSILON || lightness >= 1.0 - EPSILON) return 0.0

        var low = 0.0
        var high = 0.5 // sRGB's OKLCH boundary is below 0.5.
        repeat(iterations) {
            val middle = (low + high) * 0.5
            if (oklchToLinearRgb(lightness, middle, cosH, sinH).inGamut()) {
                low = middle
            } else {
                high = middle
            }
        }
        return low
    }

    private fun linearRgbToArgb(rgb: LinearRgb): Int {
        val r = (linearToSrgb(rgb.r.coerceIn(0.0, 1.0)) * 255.0)
            .roundToInt()
            .coerceIn(0, 255)
        val g = (linearToSrgb(rgb.g.coerceIn(0.0, 1.0)) * 255.0)
            .roundToInt()
            .coerceIn(0, 255)
        val b = (linearToSrgb(rgb.b.coerceIn(0.0, 1.0)) * 255.0)
            .roundToInt()
            .coerceIn(0, 255)
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }

    private fun srgbToLinear(value: Double): Double =
        if (value <= 0.04045) {
            value / 12.92
        } else {
            Math.pow((value + 0.055) / 1.055, 2.4)
        }

    private fun linearToSrgb(value: Double): Double =
        if (value <= 0.0031308) {
            12.92 * value
        } else {
            1.055 * Math.pow(value, 1.0 / 2.4) - 0.055
        }

    private fun contrastFromLuminances(first: Double, second: Double): Double =
        (max(first, second) + 0.05) / (min(first, second) + 0.05)

    private fun channel(argb: Int, shift: Int): Int = (argb ushr shift) and 0xFF

    private fun square(value: Double): Double = value * value

    private fun isHexDigit(ch: Char): Boolean =
        ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F'

    private fun Double.isFiniteNumber(): Boolean = !isNaN() && !isInfinite()

    private const val OPAQUE_BLACK: Int = -0x1000000 // 0xFF000000
    private const val OPAQUE_WHITE: Int = -0x1 // 0xFFFFFFFF
    private const val EPSILON: Double = 1e-12
    private const val GAMUT_EPSILON: Double = 1e-7
    private const val LUMINANCE_EPSILON: Double = 1e-12
    private const val SCORE_EPSILON: Double = 1e-12
    private const val OUTPUT_HUE_CHROMA_THRESHOLD: Double = 1e-6
    private const val HEX_DIGITS: String = "0123456789ABCDEF"
}

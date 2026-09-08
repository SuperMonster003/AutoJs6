package org.autojs.autojs.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** QR matrix generated with qrcode 8.2, error correction M and a four-module quiet zone. */
internal object NodeImageRecognitionFixture {
    const val TEXT = "ROADMAP 2026"
    const val CODE = "AutoJs6 Node Roadmap 2026"
    private val rows = arrayOf(
        "000000000000000000000000000000000",
        "000000000000000000000000000000000",
        "000000000000000000000000000000000",
        "000000000000000000000000000000000",
        "000011111110110100100011111110000",
        "000010000010110111000010000010000",
        "000010111010101011110010111010000",
        "000010111010001111001010111010000",
        "000010111010110010101010111010000",
        "000010000010000000000010000010000",
        "000011111110101010101011111110000",
        "000000000000011001000000000000000",
        "000010011111101010111100101110000",
        "000000111101001010010001111100000",
        "000001010011000010111000010010000",
        "000001000101001010010001111010000",
        "000011100110111110101011110010000",
        "000011110001101001111000111100000",
        "000011001010011111011111000110000",
        "000010100001010100111010111110000",
        "000010010110111011111111110110000",
        "000000000000110110111000100110000",
        "000011111110110110111010110010000",
        "000010000010101101111000110110000",
        "000010111010110000011111100000000",
        "000010111010111110001110001110000",
        "000010111010010110110100101110000",
        "000010000010011010000010101110000",
        "000011111110101100111010100010000",
        "000000000000000000000000000000000",
        "000000000000000000000000000000000",
        "000000000000000000000000000000000",
        "000000000000000000000000000000000",
    )

    fun drawCode(canvas: Canvas, x: Int = 80, y: Int = 400, scale: Int = 8) {
        val paint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(x.toFloat(), y.toFloat(), (x + rows.size * scale).toFloat(), (y + rows.size * scale).toFloat(), paint)
        paint.color = Color.BLACK
        rows.forEachIndexed { row, bits -> bits.forEachIndexed { column, bit ->
            if (bit == '1') canvas.drawRect((x + column * scale).toFloat(), (y + row * scale).toFloat(),
                (x + (column + 1) * scale).toFloat(), (y + (row + 1) * scale).toFloat(), paint)
        } }
    }
}

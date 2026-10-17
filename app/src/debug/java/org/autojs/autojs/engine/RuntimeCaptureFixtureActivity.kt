package org.autojs.autojs.engine

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.View

/** Owned color surface for real MediaProjection tests, retained across display rotation. */
class RuntimeCaptureFixtureActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(object : View(this) {
            override fun onDraw(canvas: Canvas) { canvas.drawColor(Color.rgb(51, 102, 153)) }
        })
    }
}

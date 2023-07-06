package org.autojs.autojs.core.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.MediaController
import android.widget.VideoView

class JsVideoView : VideoView {

    private var mMediaController: MediaController

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        MediaController(context).let { controller ->
            controller.setAnchorView(this)
            mMediaController = controller
        }
    }

    fun clearMediaController() {
        setMediaController(null)
    }

    fun resetMediaController() {
        setMediaController(mMediaController)
    }

    fun setCustomMediaController(controller: MediaController?) {
        setMediaController(controller)
    }

    private fun resetVideoViewWidth(videoView: VideoView, newWidth: Int) {
        videoView.layoutParams = videoView.layoutParams.apply { width = newWidth }
    }

}
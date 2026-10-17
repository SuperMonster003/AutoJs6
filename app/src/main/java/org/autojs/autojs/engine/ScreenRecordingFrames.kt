package org.autojs.autojs.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import org.autojs.autojs.core.image.capture.ScreenCapturer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.min

/** One projection input, rendered to the video encoder and read back on demand for screenshots. */
internal class ScreenRecordingFrames(
    context: Context,
    private val capturer: ScreenCapturer,
    private val encoderSurface: Surface,
    private val videoWidth: Int,
    private val videoHeight: Int,
    private val frameRate: Int,
    private val originNs: Long,
    private val onFailure: (Throwable) -> Unit,
) : AutoCloseable {
    private val thread = HandlerThread("ScreenRecording-GL").apply { start() }
    private val handler = Handler(thread.looper)
    private val displays = context.getSystemService(DisplayManager::class.java)
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var window: EGLSurface = EGL14.EGL_NO_SURFACE
    private var offscreen: EGLSurface = EGL14.EGL_NO_SURFACE
    private lateinit var config: EGLConfig
    private var texture: SurfaceTexture? = null
    private var input: Surface? = null
    private var program = 0
    private var textureId = 0
    private var inputWidth = 0
    private var inputHeight = 0
    private var snapshotWidth = 0
    private var snapshotHeight = 0
    private var hasFrame = false
    private var pendingFrame = false
    @Volatile private var closing = false
    private var nextFrameNs = 0L
    private val matrix = FloatArray(16)
    private val vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f,0f,0f, 1f,-1f,1f,0f, -1f,1f,0f,1f, 1f,1f,1f,1f)); position(0)
    }
    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) = Unit
        override fun onDisplayRemoved(id: Int) = Unit
        override fun onDisplayChanged(id: Int) { if (id == Display.DEFAULT_DISPLAY) guard { resizeInput() } }
    }
    private val render = object : Runnable {
        override fun run() {
            if (closing) return
            guard {
                updateTexture()
                val now = System.nanoTime()
                if (hasFrame && now >= nextFrameNs) {
                    draw(window, videoWidth, videoHeight)
                    check(EGLExt.eglPresentationTimeANDROID(eglDisplay, window, now - originNs))
                    check(EGL14.eglSwapBuffers(eglDisplay, window)) { "Cannot submit recording frame." }
                    nextFrameNs = now + 1_000_000_000L / frameRate
                }
            }
            if (!closing) handler.postDelayed(this, (1000L + frameRate - 1) / frameRate)
        }
    }

    fun start() = runOnGl {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(EGL14.eglInitialize(eglDisplay, IntArray(2), 0, IntArray(2), 1))
        val configs = arrayOfNulls<EGLConfig>(1)
        check(EGL14.eglChooseConfig(eglDisplay, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
            0x3142,1,EGL14.EGL_NONE),0,configs,0,1,IntArray(1),0))
        config = checkNotNull(configs[0])
        eglContext = EGL14.eglCreateContext(eglDisplay,config,EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        check(eglContext != EGL14.EGL_NO_CONTEXT)
        window = EGL14.eglCreateWindowSurface(eglDisplay,config,encoderSurface,intArrayOf(EGL14.EGL_NONE),0)
        check(window != EGL14.EGL_NO_SURFACE)
        current(window)
        program = GLES20.glCreateProgram()
        val vertex = shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 p; attribute vec2 t; uniform mat4 m; varying vec2 uv; void main(){gl_Position=vec4(p,0.,1.);uv=(m*vec4(t,0.,1.)).xy;}")
        val fragment = shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float; varying vec2 uv; uniform samplerExternalOES image; void main(){gl_FragColor=texture2D(image,uv);}")
        GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program)
        val status=IntArray(1);GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(program) }
        GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment)
        val size=capturer.captureDimensions;inputWidth=size.x;inputHeight=size.y
        createInput()
        capturer.setRecordingSurface(input!!,inputWidth,inputHeight)
        displays.registerDisplayListener(listener,handler)
        nextFrameNs=0;handler.post(render)
    }

    private fun resizeInput() {
        val size=capturer.captureDimensions
        if(size.x==inputWidth && size.y==inputHeight)return
        capturer.detachRecordingSurface()
        val oldInput=input;val oldTexture=texture;val oldTextureId=textureId
        inputWidth=size.x;inputHeight=size.y;hasFrame=false;pendingFrame=false
        try {
            current(window);createInput()
            capturer.replaceRecordingSurface(checkNotNull(input),inputWidth,inputHeight)
        } finally {
            oldTexture?.setOnFrameAvailableListener(null);oldInput?.release();oldTexture?.release()
            GLES20.glDeleteTextures(1,intArrayOf(oldTextureId),0)
        }
    }

    private fun createInput() {
        val names=IntArray(1);GLES20.glGenTextures(1,names,0);textureId=names[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        texture=SurfaceTexture(textureId).also { source ->
            source.setDefaultBufferSize(inputWidth,inputHeight)
            source.setOnFrameAvailableListener({if(texture===source)pendingFrame=true},handler)
        }
        input=Surface(texture)
    }

    private fun updateTexture() {
        if(!pendingFrame)return
        current(window)
        texture!!.updateTexImage();texture!!.getTransformMatrix(matrix)
        pendingFrame=false;hasFrame=true
    }

    fun snapshot(): Bitmap {
        val result=CompletableFuture<Bitmap>();val deadline=SystemClock.uptimeMillis()+2500
        val copy=object:Runnable {
            override fun run() {
                if(result.isDone)return
                try {
                    check(!closing) { "Recording frame source is closed." }
                    resizeInput();updateTexture()
                    if(!hasFrame) {
                        check(SystemClock.uptimeMillis()<deadline) { "No recording screenshot frame is available." }
                        handler.postDelayed(this,20);return
                    }
                    if(offscreen==EGL14.EGL_NO_SURFACE || snapshotWidth!=inputWidth || snapshotHeight!=inputHeight) {
                        current(window)
                        if(offscreen!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(eglDisplay,offscreen)
                        snapshotWidth=inputWidth;snapshotHeight=inputHeight
                        offscreen=EGL14.eglCreatePbufferSurface(eglDisplay,config,intArrayOf(EGL14.EGL_WIDTH,inputWidth,EGL14.EGL_HEIGHT,inputHeight,EGL14.EGL_NONE),0)
                        check(offscreen!=EGL14.EGL_NO_SURFACE)
                    }
                    draw(offscreen,inputWidth,inputHeight)
                    val rgba=ByteBuffer.allocateDirect(inputWidth*inputHeight*4).order(ByteOrder.LITTLE_ENDIAN)
                    GLES20.glReadPixels(0,0,inputWidth,inputHeight,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,rgba)
                    check(GLES20.glGetError()==GLES20.GL_NO_ERROR) { "Cannot read recording screenshot pixels." }
                    val pixels=IntArray(inputWidth*inputHeight)
                    for(y in 0 until inputHeight) for(x in 0 until inputWidth) {
                        val abgr=rgba.getInt((y*inputWidth+x)*4)
                        pixels[(inputHeight-y-1)*inputWidth+x]=(abgr and -0x1000000) or ((abgr and 255) shl 16) or (abgr and 0xff00) or ((abgr ushr 16) and 255)
                    }
                    val bitmap=Bitmap.createBitmap(pixels,inputWidth,inputHeight,Bitmap.Config.ARGB_8888)
                    current(window)
                    if(!result.complete(bitmap))bitmap.recycle()
                } catch(error:Throwable) { result.completeExceptionally(error) }
            }
        }
        check(handler.post(copy))
        return try { result.get(3,TimeUnit.SECONDS) } catch(error:Exception) {result.cancel(false);throw error}
    }

    private fun draw(surface:EGLSurface,width:Int,height:Int) {
        current(surface)
        GLES20.glViewport(0,0,width,height);GLES20.glClearColor(0f,0f,0f,1f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val scale=min(width.toFloat()/inputWidth,height.toFloat()/inputHeight)
        val w=(inputWidth*scale).toInt();val h=(inputHeight*scale).toInt();GLES20.glViewport((width-w)/2,(height-h)/2,w,h)
        GLES20.glUseProgram(program)
        val p=GLES20.glGetAttribLocation(program,"p");val t=GLES20.glGetAttribLocation(program,"t")
        vertices.position(0);GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(p)
        vertices.position(2);GLES20.glVertexAttribPointer(t,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(t)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"m"),1,false,matrix,0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"image"),0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
    }

    private fun shader(type:Int,source:String):Int {
        val id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,source);GLES20.glCompileShader(id)
        val status=IntArray(1);GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,status,0)
        check(status[0]==GLES20.GL_TRUE) {GLES20.glGetShaderInfoLog(id)};return id
    }
    private fun current(surface:EGLSurface) { check(EGL14.eglMakeCurrent(eglDisplay,surface,surface,eglContext)) {"Cannot bind recording EGL surface."} }
    private fun guard(block:()->Unit) { try {block()}catch(error:Throwable){onFailure(error);closing=true} }
    private fun <T> runOnGl(block:()->T):T {
        val future=CompletableFuture<T>();check(handler.post{try{future.complete(block())}catch(error:Throwable){future.completeExceptionally(error)}})
        return future.get(3,TimeUnit.SECONDS)
    }
    override fun close() {
        closing=true
        try { runOnGl {
            handler.removeCallbacks(render);displays.unregisterDisplayListener(listener)
            runCatching {capturer.detachRecordingSurface()}
            texture?.setOnFrameAvailableListener(null);input?.release();input=null;texture?.release();texture=null
            if(eglDisplay!=EGL14.EGL_NO_DISPLAY) {
                if(eglContext!=EGL14.EGL_NO_CONTEXT && window!=EGL14.EGL_NO_SURFACE)runCatching{current(window);GLES20.glDeleteProgram(program);GLES20.glDeleteTextures(1,intArrayOf(textureId),0)}
                EGL14.eglMakeCurrent(eglDisplay,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
                if(offscreen!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(eglDisplay,offscreen)
                if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(eglDisplay,window)
                if(eglContext!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(eglDisplay,eglContext)
                EGL14.eglTerminate(eglDisplay);eglDisplay=EGL14.EGL_NO_DISPLAY
            }
        }} finally {thread.quitSafely();thread.join(1000)}
    }
}

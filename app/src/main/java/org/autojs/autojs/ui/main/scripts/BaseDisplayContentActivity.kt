package org.autojs.autojs.ui.main.scripts

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.TextView
import io.noties.markwon.syntax.Prism4jThemeBase
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.prism4j.GrammarLocator
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.autojs.autojs.theme.widget.ThemeColorFloatingActionButton
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityDisplayScrollableContentBinding
import kotlin.math.floor

abstract class BaseDisplayContentActivity : BaseActivity() {

    abstract var internalMenuResource: Int

    abstract var highlightGrammarLocator: GrammarLocator
    abstract var highlightGrammarName: String
    abstract var highlightThemeLanguage: String

    private lateinit var internalTextView: TextView
    private lateinit var internalFabView: ThemeColorFloatingActionButton

    protected val popMenuActionMap = mutableMapOf<Int, () -> Unit>()

    private var mIsContentLoaded = false
    private val mTextUpdateMutex = Mutex()

    protected val minTextSize = 4.0f
    protected val maxTextSize = 72.0f

    private var mLastScaleFactor = 1.0f
    private var mLastTextSize = 0.0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityDisplayScrollableContentBinding.inflate(layoutInflater).also {
            setContentView(it.root)
        }

        internalTextView = binding.textView
        internalFabView = binding.fab.apply {
            setOnClickListener { view -> showPopupMenu(view, internalTextView) }
            ViewUtils.excludeFloatingActionButtonFromNavigationBar(this)
        }

        val scaleGestureDetector = ScaleGestureDetector(this, ScaleListener(internalTextView))

        val innerScrollView = binding.innerScrollView.also {
            it.setOnTouchListener { view, event ->
                when (event.pointerCount) {
                    2 -> {
                        scaleGestureDetector.onTouchEvent(event)

                        // @Hint by SuperMonster003 on Nov 21, 2024.
                        //  ! Without short-circuit evaluation, `view.onTouchEvent(event)` will always be called.
                        //  ! zh-CN: 无短路求值, `view.onTouchEvent(event)` 始终会被调用.
                        //  !
                        // @Hint by SuperMonster003 on Nov 21, 2024.
                        //  ! This approach can temporarily solve the following two problems.
                        //  ! 1. During pinch-to-zoom, the view may immediately scroll to the end of the text.
                        //  ! 2. When the inner scroll view does not fill the screen, the pinch-to-zoom functionality
                        //  ! is ineffective within the inner scroll view's range.
                        //  ! zh-CN:
                        //  ! 这样处理可以临时解决以下两个问题.
                        //  ! 1. 双指缩放过程中可能出现视图立即滚动到文本末尾处.
                        //  ! 2. 内部滚动视图未占满屏幕时, 双指缩放功能在内部滚动视图范围内无效.
                        //  !
                        // @Dubious by SuperMonster003 on Nov 21, 2024.
                        //  ! Actually, I still do not figure this out.
                        //  ! zh-CN: 事实上, 此处依然存疑.
                        //  ! Reference: https://stackoverflow.com/questions/3866499/two-directional-scroll-view
                        //  !

                        super.onTouchEvent(event) or view.onTouchEvent(event)
                    }
                    else -> super.onTouchEvent(event)
                }
            }
        }

        binding.outerScrollView.also {
            it.innerHorizontalScrollView = innerScrollView
            it.setOnTouchListener { _, event ->
                when (event.pointerCount) {
                    2 -> {
                        scaleGestureDetector.onTouchEvent(event)
                        !scaleGestureDetector.isInProgress && super.onTouchEvent(event)
                    }
                    else -> super.onTouchEvent(event)
                }
            }
            ViewUtils.excludePaddingClippableViewFromNavigationBar(it)
        }

        // 使用协程加载内容
        CoroutineScope(Dispatchers.Main).launch {
            launch {
                delay(1200L)
                mTextUpdateMutex.withLock {
                    if (!mIsContentLoaded) {
                        internalTextView.text = getString(R.string.text_loading_with_dots)
                    }
                }
            }
            loadAndDisplayContent()
            mIsContentLoaded = true
        }
    }

    protected abstract suspend fun loadAndDisplayContent()

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            true.also { finish() }
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    protected suspend fun setTextWithLock(text: CharSequence) {
        mTextUpdateMutex.withLock {
            internalTextView.text = text
        }
    }

    protected fun setText(text: CharSequence) {
        internalTextView.text = text
    }

    protected fun highlightTextOrSelf(text: CharSequence): CharSequence {
        return highlightTextOrNull(text) ?: text
    }

    protected fun highlightTextOrNull(text: CharSequence, chunkSize: Int = 100_000): CharSequence? {
        val totalLength = text.length
        val result = SpannableStringBuilder()

        var startIndex = 0
        while (startIndex < totalLength) {
            val endIndex = (startIndex + chunkSize).coerceAtMost(totalLength)
            val chunk = text.subSequence(startIndex, endIndex)
            highlightTextOrNullInternal(chunk)?.let { result.append(it) }
            startIndex = endIndex
        }
        return result.takeIf { it.isNotEmpty() }
    }

    private fun highlightTextOrNullInternal(text: CharSequence): CharSequence? {
        val prism4j = Prism4j(highlightGrammarLocator)
        val grammar = prism4j.grammar(highlightGrammarName) ?: return null
        val nodes = runCatching { prism4j.tokenize(text.toString(), grammar) }.getOrNull() ?: return null
        val theme = when {
            ViewUtils.isNightModeYes(this) -> Prism4jThemeDarkula.create()
            else -> Prism4jThemeDefault.create()
        }
        return SpannableStringBuilder().also { stringBuilder ->
            nodes.forEach { node -> applySpan(stringBuilder, node, theme) }
        }
    }

    private fun applySpan(spannable: SpannableStringBuilder, node: Prism4j.Node, theme: Prism4jThemeBase) {
        when (node) {
            is Prism4j.Text -> spannable.append(node.literal())
            is Prism4j.Syntax -> {
                val start = spannable.length
                node.children().forEach { child ->
                    applySpan(spannable, child, theme)
                }
                val end = spannable.length
                theme.apply(highlightThemeLanguage, node, spannable, start, end)
            }
        }
    }

    private fun showPopupMenu(view: View, textView: TextView) {
        if (internalMenuResource == 0) {
            ViewUtils.showToast(view.context, "Menu resource is not set a valid resource id", true)
            return
        }
        val popupMenu = android.widget.PopupMenu(this, view).also {
            it.inflate(internalMenuResource)
        }

        // 通过反射启用图标显示功能
        runCatching {
            for (field in popupMenu.javaClass.declaredFields) {
                if ("mPopup" != field.name) continue
                field.isAccessible = true
                val menuPopupHelper = field.get(popupMenu)
                val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                val setForceShowIcon = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
                setForceShowIcon.invoke(menuPopupHelper, true)
                break
            }
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (val id = menuItem.itemId) {
                R.id.action_copy_all -> true.also { copyToClipboard(textView.text.toString()) }
                R.id.action_hide_button -> true.also { hideFabWithSnackbar() }
                R.id.action_exit -> true.also { finish() }
                in popMenuActionMap -> true.also { popMenuActionMap[id]!!.invoke() }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun hideFabWithSnackbar() {
        internalFabView.visibility = View.GONE
        ViewUtils.showSnack(internalTextView, getString(R.string.text_press_back_or_vol_down_to_close_window), true)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(javaClass.simpleName.removeSuffix("Activity"), text)
        clipboard.setPrimaryClip(clip)
        ViewUtils.showSnack(internalTextView, R.string.text_already_copied_to_clip, true)
    }

    // Scaling & Text Size Handling
    protected inner class ScaleListener(private val textView: TextView) : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val currentFactor = (floor((detector.scaleFactor * 10).toDouble()) / 10).toFloat()
            if (mLastTextSize <= 0) {
                mLastTextSize = getTextSize()
            }
            if (currentFactor > 0 && mLastScaleFactor != currentFactor) {
                val currentTextSize: Float = mLastTextSize + (if (currentFactor > mLastScaleFactor) 1 else -1)
                mLastTextSize = currentTextSize.coerceIn(minTextSize, maxTextSize)
                setTextSize(mLastTextSize)
                mLastScaleFactor = currentFactor
            }
            return super.onScale(detector)
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            mLastScaleFactor = 1.0f
            super.onScaleEnd(detector)
        }

        fun setTextSize(size: Float) {
            mLastTextSize = size
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        }

        fun getTextSize(): Float = DisplayUtils.pxToSp(textView.textSize)

    }

}
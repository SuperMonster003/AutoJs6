package org.autojs.autojs.ui.main.scripts

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.noties.markwon.syntax.Prism4jThemeBase
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.prism4j.GrammarLocator
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludeFloatingActionButtonFromBottomNavigationBar
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityDisplayScrollableContentBinding

abstract class BaseDisplayContentActivity : BaseActivity() {

    override val handleStatusBarThemeColorAutomatically = false

    abstract var highlightGrammarLocator: GrammarLocator
    abstract var highlightGrammarName: String
    abstract var highlightThemeLanguage: String

    open var internalMenuResource: Int = 0
    open var themeColorDayNight = R.color.md_blue_gray_50 to R.color.md_blue_gray_900
    protected open val deferTextRelayoutDuringScale = false

    private lateinit var internalTextView: TextView
    private lateinit var internalFabView: FloatingActionButton

    protected val popMenuActionMap = mutableMapOf<Int, () -> Unit>()

    private var mIsContentLoaded = false
    private val mTextUpdateMutex = Mutex()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityDisplayScrollableContentBinding.inflate(layoutInflater).also {
            setContentView(it.root)
        }

        internalTextView = binding.textView

        internalFabView = binding.fab.apply {
            if (internalMenuResource > 0) {
                setOnClickListener { view -> showPopupMenu(view, internalTextView) }
                excludeFloatingActionButtonFromBottomNavigationBar()
                visibility = View.VISIBLE
            }
        }

        val innerScrollView = binding.innerScrollView.also {
            it.excludePaddingClippableViewFromBottomNavigationBar()
        }

        val outerScrollView = binding.outerScrollView.also {
            it.innerHorizontalScrollView = innerScrollView
        }

        if (deferTextRelayoutDuringScale) {
            val scaleGestureDetector = ViewUtils.DeferredTextSizeScaleDetector(this, internalTextView)
            var handlingScaleGesture = false
            var lastEventTime = Long.MIN_VALUE
            var lastEventAction = Int.MIN_VALUE
            var lastEventPointerCount = Int.MIN_VALUE
            val scaleTouchListener = View.OnTouchListener { view, event ->
                // The same MotionEvent can travel through the outer scroll view, the horizontal
                // scroll view and the TextView. Feed it to ScaleGestureDetector exactly once.
                if (
                    event.eventTime != lastEventTime ||
                    event.action != lastEventAction ||
                    event.pointerCount != lastEventPointerCount
                ) {
                    scaleGestureDetector.onTouchEvent(event)
                    lastEventTime = event.eventTime
                    lastEventAction = event.action
                    lastEventPointerCount = event.pointerCount
                }
                if (
                    event.actionMasked == MotionEvent.ACTION_POINTER_DOWN &&
                    event.pointerCount >= 2
                ) {
                    handlingScaleGesture = true
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                val consume = handlingScaleGesture
                if (
                    event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    handlingScaleGesture = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                consume
            }
            outerScrollView.setOnTouchListener(scaleTouchListener)
            innerScrollView.setOnTouchListener(scaleTouchListener)
            internalTextView.setOnTouchListener(scaleTouchListener)
        } else {
            val scaleGestureDetector = ViewUtils.TextSizeScaleDetector(this, internalTextView)
            innerScrollView.setOnTouchListener { view, event ->
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
            outerScrollView.setOnTouchListener { _, event ->
                when (event.pointerCount) {
                    2 -> {
                        scaleGestureDetector.onTouchEvent(event)
                        !scaleGestureDetector.isInProgress && super.onTouchEvent(event)
                    }
                    else -> super.onTouchEvent(event)
                }
            }
        }

        // 使用协程加载内容
        lifecycleScope.launch {
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

    override fun onStart() {
        super.onStart()

        val (themeColorDayRes, themeColorNightRes) = themeColorDayNight

        val themeColorDay = getColor(themeColorDayRes)
        val themeColorNight = getColor(themeColorNightRes)

        if (ViewUtils.isNightModeYes(this)) {
            internalFabView.backgroundTintList = ColorStateList.valueOf(themeColorNight)
            internalFabView.imageTintList = ColorStateList.valueOf(if (ViewUtils.isLuminanceDark(themeColorNight)) getColor(R.color.night) else getColor(R.color.day))
            ViewUtils.setStatusBarBackgroundColor(this, themeColorNight)
            ViewUtils.setStatusBarIconLight(this, ViewUtils.isLuminanceDark(themeColorNight))
        } else {
            internalFabView.backgroundTintList = ColorStateList.valueOf(themeColorDay)
            internalFabView.imageTintList = ColorStateList.valueOf(if (ViewUtils.isLuminanceDark(themeColorDay)) getColor(R.color.night) else getColor(R.color.day))
            ViewUtils.setStatusBarBackgroundColor(this, themeColorDay)
            ViewUtils.setStatusBarIconLight(this, ViewUtils.isLuminanceDark(themeColorDay))
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

    protected fun setTextSelectionEnabled(enabled: Boolean) {
        internalTextView.setTextIsSelectable(enabled)
        internalTextView.isLongClickable = enabled
    }

    protected fun setContentActionsEnabled(enabled: Boolean) {
        internalFabView.isEnabled = enabled
        internalFabView.alpha = if (enabled) 1.0f else 0.5f
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

}

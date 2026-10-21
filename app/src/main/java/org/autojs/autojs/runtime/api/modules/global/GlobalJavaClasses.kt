package org.autojs.autojs.runtime.api.modules.global

import org.autojs.autojs.runtime.api.modules.ScriptNameLookup
import org.mozilla.javascript.NativeJavaClass
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.VarScope

/**
 * The Java classes readable as global names (`Intent`, `Bitmap`, `File`, ...), the name lookup prototype of the global
 * object (roadmap P3.0, the current `GlobalClasses` / `Classes`): no own properties, `in`, `hasOwnProperty` and
 * `Object.keys` see none of them, and each read wraps the class in a new `NativeJavaClass`, as the current getter proxy
 * does. A class the device cannot load (a class newer than its Android version) reads as not found, as the current
 * lookup's `runCatching` gives. [NAMES] lists the names in the order of `Classes`; `GlobalModuleTest` checks the list
 * and every class against the current fields.
 *
 * zh-CN: 可作为全局名称读取的 Java 类 (Intent, Bitmap, File 等), 即全局对象的名称查找原型 (P3.0, 对应现有 GlobalClasses / Classes): 没有自有属性,
 * in, hasOwnProperty 与 Object.keys 都看不到, 每次读取包装为新的 NativeJavaClass, 与现有 getter 代理相同. 设备无法加载的类 (比系统版本新的类)
 * 读取为不存在, 与现有查找的 runCatching 相同. [NAMES] 按 Classes 的顺序列出名称, GlobalModuleTest 核对名单与每个类.
 */
object GlobalJavaClasses : ScriptNameLookup {

    override fun lookup(scope: VarScope, name: String): Any? {
        val type = try {
            classOf(name)
        } catch (e: LinkageError) {
            null
        } ?: return Scriptable.NOT_FOUND
        return NativeJavaClass(scope, type)
    }

    /** The class of the global name [name], or null when it is not one of [NAMES]. */
    @JvmStatic
    fun classOf(name: String): Class<*>? = when (name) {
        "Manifest" -> android.Manifest::class.java
        "GestureResultCallback" -> android.accessibilityservice.AccessibilityService.GestureResultCallback::class.java
        "Notification" -> android.app.Notification::class.java
        "NotificationManager" -> android.app.NotificationManager::class.java
        "PendingIntent" -> android.app.PendingIntent::class.java
        "ComponentName" -> android.content.ComponentName::class.java
        "Context" -> android.content.Context::class.java
        "Intent" -> android.content.Intent::class.java
        "PackageManager" -> android.content.pm.PackageManager::class.java
        "ColorStateList" -> android.content.res.ColorStateList::class.java
        "Bitmap" -> android.graphics.Bitmap::class.java
        "BitmapFactory" -> android.graphics.BitmapFactory::class.java
        "Paint" -> android.graphics.Paint::class.java
        "PorterDuff" -> android.graphics.PorterDuff::class.java
        "Typeface" -> android.graphics.Typeface::class.java
        "ColorDrawable" -> android.graphics.drawable.ColorDrawable::class.java
        "Uri" -> android.net.Uri::class.java
        "Build" -> android.os.Build::class.java
        "Handler" -> android.os.Handler::class.java
        "Looper" -> android.os.Looper::class.java
        "Settings" -> android.provider.Settings::class.java
        "TextToSpeech" -> android.speech.tts.TextToSpeech::class.java
        "InputType" -> android.text.InputType::class.java
        "TextWatcher" -> android.text.TextWatcher::class.java
        "Linkify" -> android.text.util.Linkify::class.java
        "Base64" -> android.util.Base64::class.java
        "Log" -> android.util.Log::class.java
        "TypedValue" -> android.util.TypedValue::class.java
        "ContextThemeWrapper" -> android.view.ContextThemeWrapper::class.java
        "Gravity" -> android.view.Gravity::class.java
        "KeyEvent" -> android.view.KeyEvent::class.java
        "MotionEvent" -> android.view.MotionEvent::class.java
        "ScaleGestureDetector" -> android.view.ScaleGestureDetector::class.java
        "WindowManager" -> android.view.WindowManager::class.java
        "LayoutParams" -> android.view.WindowManager.LayoutParams::class.java
        "MimeTypeMap" -> android.webkit.MimeTypeMap::class.java
        "WebChromeClient" -> android.webkit.WebChromeClient::class.java
        "WebView" -> android.webkit.WebView::class.java
        "WebViewClient" -> android.webkit.WebViewClient::class.java
        "Toast" -> android.widget.Toast::class.java
        "BigTextStyle" -> androidx.core.app.NotificationCompat.BigTextStyle::class.java
        "NotificationCompat" -> androidx.core.app.NotificationCompat::class.java
        "FileProvider" -> androidx.core.content.FileProvider::class.java
        "ImageViewCompat" -> androidx.core.widget.ImageViewCompat::class.java
        "Snackbar" -> com.google.android.material.snackbar.Snackbar::class.java
        "MaterialDialog" -> com.afollestad.materialdialogs.MaterialDialog::class.java
        "LogConfigurator" -> org.autojs.autojs.core.console.log.LogConfigurator::class.java
        "Version" -> io.github.g00fy2.versioncompare.Version::class.java
        "ByteArrayOutputStream" -> java.io.ByteArrayOutputStream::class.java
        "File" -> java.io.File::class.java
        "Runnable" -> java.lang.Runnable::class.java
        "SecurityException" -> java.lang.SecurityException::class.java
        "System" -> java.lang.System::class.java
        "Thread" -> java.lang.Thread::class.java
        "Throwable" -> java.lang.Throwable::class.java
        "URI" -> java.net.URI::class.java
        "StandardCharsets" -> java.nio.charset.StandardCharsets::class.java
        "TimeUnit" -> java.util.concurrent.TimeUnit::class.java
        "Locale" -> java.util.Locale::class.java
        "AtomicLong" -> java.util.concurrent.atomic.AtomicLong::class.java
        "ReentrantLock" -> java.util.concurrent.locks.ReentrantLock::class.java
        "Callback" -> okhttp3.Callback::class.java
        "FormBody" -> okhttp3.FormBody::class.java
        "MediaType" -> okhttp3.MediaType::class.java
        "MultipartBody" -> okhttp3.MultipartBody::class.java
        "OkHttpClient" -> okhttp3.OkHttpClient::class.java
        "Request" -> okhttp3.Request::class.java
        "RequestBody" -> okhttp3.RequestBody::class.java
        "Level" -> org.autojs.autojs.core.console.log.Level::class.java
        "LogManager" -> org.autojs.autojs.core.console.log.LogManager::class.java
        "GlobalAppContext" -> org.autojs.autojs.app.GlobalAppContext::class.java
        "AccessibilityBridge" -> org.autojs.autojs.core.accessibility.AccessibilityBridge::class.java
        "UiSelector" -> org.autojs.autojs.core.accessibility.UiSelector::class.java
        "ApkBuilder" -> org.autojs.autojs.apkbuilder.ApkBuilder::class.java
        "UiObject" -> org.autojs.autojs.core.automator.UiObject::class.java
        "UiObjectCollection" -> org.autojs.autojs.core.automator.UiObjectCollection::class.java
        "Crypto" -> org.autojs.autojs.core.crypto.Crypto::class.java
        "EventEmitter" -> org.autojs.autojs.core.eventloop.EventEmitter::class.java
        "MutableOkHttp" -> org.autojs.autojs.core.http.MutableOkHttp::class.java
        "ColorDetector" -> org.autojs.autojs.core.image.ColorDetector::class.java
        "ColorTable" -> org.autojs.autojs.core.image.ColorTable::class.java
        "ImageWrapper" -> org.autojs.autojs.core.image.ImageWrapper::class.java
        "Mat" -> org.autojs.autojs.core.opencv.Mat::class.java
        "DynamicLayoutInflater" -> org.autojs.autojs.core.ui.inflater.DynamicLayoutInflater::class.java
        "ContinuationResult" -> org.autojs.autojs.rhino.continuation.Continuation.Result::class.java
        "ContinuationCreator" -> org.autojs.autojs.runtime.api.augment.continuation.Creator::class.java
        "JavaScriptEngine" -> org.autojs.autojs.engine.JavaScriptEngine::class.java
        "PFile" -> org.autojs.autojs.pio.PFile::class.java
        "Pref" -> org.autojs.autojs.core.pref.Pref::class.java
        "ProxyJavaObject" -> org.autojs.autojs.rhino.ProxyJavaObject::class.java
        "ProxyObject" -> org.autojs.autojs.rhino.ProxyObject::class.java
        "ScriptRuntime" -> org.autojs.autojs.runtime.ScriptRuntime::class.java
        "AppUtils" -> org.autojs.autojs.runtime.api.AppUtils::class.java
        "ScreenMetrics" -> org.autojs.autojs.runtime.api.ScreenMetrics::class.java
        "Shell" -> org.autojs.autojs.runtime.api.Shell::class.java
        "ScriptInterruptedException" -> org.autojs.autojs.runtime.exception.ScriptInterruptedException::class.java
        "JavaScriptSource" -> org.autojs.autojs.script.JavaScriptSource::class.java
        "ThemeColor" -> org.autojs.autojs.theme.ThemeColor::class.java
        "App" -> org.autojs.autojs.util.App::class.java
        "AndroidUtils" -> org.autojs.autojs.util.AndroidUtils::class.java
        "ArrayUtils" -> org.autojs.autojs.util.ArrayUtils::class.java
        "ColorUtils" -> org.autojs.autojs.util.ColorUtils::class.java
        "ConsoleUtils" -> org.autojs.autojs.util.ConsoleUtils::class.java
        "DeviceUtils" -> org.autojs.autojs.util.DeviceUtils::class.java
        "DisplayUtils" -> org.autojs.autojs.util.DisplayUtils::class.java
        "IntentUtils" -> org.autojs.autojs.util.IntentUtils::class.java
        "JavaUtils" -> org.autojs.autojs.util.JavaUtils::class.java
        "NetworkUtils" -> org.autojs.autojs.util.NetworkUtils::class.java
        "NotificationUtils" -> org.autojs.autojs.util.NotificationUtils::class.java
        "RhinoUtils" -> org.autojs.autojs.util.RhinoUtils::class.java
        "RootMode" -> org.autojs.autojs.util.RootUtils.RootMode::class.java
        "RootUtils" -> org.autojs.autojs.util.RootUtils::class.java
        "StringUtils" -> org.autojs.autojs.util.StringUtils::class.java
        "TextUtils" -> org.autojs.autojs.util.TextUtils::class.java
        "BuildConfig" -> org.autojs.autojs6.BuildConfig::class.java
        "VolatileBox" -> org.autojs.autojs.concurrent.VolatileBox::class.java
        "TopLevelScope" -> org.autojs.autojs.rhino.TopLevelScope::class.java
        "Imgproc" -> org.opencv.imgproc.Imgproc::class.java
        "Imgcodecs" -> org.opencv.imgcodecs.Imgcodecs::class.java
        "CvType" -> org.opencv.core.CvType::class.java
        "ScriptEngineService" -> org.autojs.autojs.engine.ScriptEngineService::class.java
        "JsAppBarLayout" -> org.autojs.autojs.core.ui.widget.JsAppBarLayout::class.java
        "JsButton" -> org.autojs.autojs.core.ui.widget.JsButton::class.java
        "JsCanvasView" -> org.autojs.autojs.core.ui.widget.JsCanvasView::class.java
        "JsCardView" -> org.autojs.autojs.core.ui.widget.JsCardView::class.java
        "JsCheckBox" -> org.autojs.autojs.core.ui.widget.JsCheckBox::class.java
        "JsConsoleView" -> org.autojs.autojs.core.ui.widget.JsConsoleView::class.java
        "JsDatePicker" -> org.autojs.autojs.core.ui.widget.JsDatePicker::class.java
        "JsDrawerLayout" -> org.autojs.autojs.core.ui.widget.JsDrawerLayout::class.java
        "JsEditText" -> org.autojs.autojs.core.ui.widget.JsEditText::class.java
        "JsFloatingActionButton" -> org.autojs.autojs.core.ui.widget.JsFloatingActionButton::class.java
        "JsFrameLayout" -> org.autojs.autojs.core.ui.widget.JsFrameLayout::class.java
        "JsGridView" -> org.autojs.autojs.core.ui.widget.JsGridView::class.java
        "JsImageButton" -> org.autojs.autojs.core.ui.widget.JsImageButton::class.java
        "JsImageView" -> org.autojs.autojs.core.ui.widget.JsImageView::class.java
        "JsLinearLayout" -> org.autojs.autojs.core.ui.widget.JsLinearLayout::class.java
        "JsListView" -> org.autojs.autojs.core.ui.widget.JsListView::class.java
        "JsProgressBar" -> org.autojs.autojs.core.ui.widget.JsProgressBar::class.java
        "JsRadioButton" -> org.autojs.autojs.core.ui.widget.JsRadioButton::class.java
        "JsRadioGroup" -> org.autojs.autojs.core.ui.widget.JsRadioGroup::class.java
        "JsRatingBar" -> org.autojs.autojs.core.ui.widget.JsRatingBar::class.java
        "JsRelativeLayout" -> org.autojs.autojs.core.ui.widget.JsRelativeLayout::class.java
        "JsScrollView" -> org.autojs.autojs.core.ui.widget.JsScrollView::class.java
        "JsSeekBar" -> org.autojs.autojs.core.ui.widget.JsSeekBar::class.java
        "JsSpinner" -> org.autojs.autojs.core.ui.widget.JsSpinner::class.java
        "JsSwitch" -> org.autojs.autojs.core.ui.widget.JsSwitch::class.java
        "JsTabLayout" -> org.autojs.autojs.core.ui.widget.JsTabLayout::class.java
        "JsTextClock" -> org.autojs.autojs.core.ui.widget.JsTextClock::class.java
        "JsTimePicker" -> org.autojs.autojs.core.ui.widget.JsTimePicker::class.java
        "JsToggleButton" -> org.autojs.autojs.core.ui.widget.JsToggleButton::class.java
        "JsToolbar" -> org.autojs.autojs.core.ui.widget.JsToolbar::class.java
        "JsViewPager" -> org.autojs.autojs.core.ui.widget.JsViewPager::class.java
        "JsWebView" -> org.autojs.autojs.core.ui.widget.JsWebView::class.java
        "EvaluatorException" -> org.mozilla.javascript.EvaluatorException::class.java
        // Documented as an alias of ImageWrapper; the current Classes.Image (`ImageWrapper::class` of the property)
        // is the class of the KClass value, kotlin.jvm.internal.ClassReference (ROADMAP appendix E, P3.0).
        "Image" -> org.autojs.autojs.core.image.ImageWrapper::class.java
        "JsTextView" -> org.autojs.autojs.core.ui.widget.JsTextView::class.java
        else -> null
    }

    @JvmField
    val NAMES: List<String> = listOf(
        "Manifest", "GestureResultCallback", "Notification", "NotificationManager", "PendingIntent", "ComponentName", "Context", "Intent",
        "PackageManager", "ColorStateList", "Bitmap", "BitmapFactory", "Paint", "PorterDuff", "Typeface", "ColorDrawable", "Uri", "Build", "Handler",
        "Looper", "Settings", "TextToSpeech", "InputType", "TextWatcher", "Linkify", "Base64", "Log", "TypedValue", "ContextThemeWrapper", "Gravity",
        "KeyEvent", "MotionEvent", "ScaleGestureDetector", "WindowManager", "LayoutParams", "MimeTypeMap", "WebChromeClient", "WebView",
        "WebViewClient", "Toast", "BigTextStyle", "NotificationCompat", "FileProvider", "ImageViewCompat", "Snackbar", "MaterialDialog",
        "LogConfigurator", "Version", "ByteArrayOutputStream", "File", "Runnable", "SecurityException", "System", "Thread", "Throwable", "URI",
        "StandardCharsets", "TimeUnit", "Locale", "AtomicLong", "ReentrantLock", "Callback", "FormBody", "MediaType", "MultipartBody",
        "OkHttpClient", "Request", "RequestBody", "Level", "LogManager", "GlobalAppContext", "AccessibilityBridge", "UiSelector", "ApkBuilder",
        "UiObject", "UiObjectCollection", "Crypto", "EventEmitter", "MutableOkHttp", "ColorDetector", "ColorTable", "ImageWrapper", "Mat",
        "DynamicLayoutInflater", "ContinuationResult", "ContinuationCreator", "JavaScriptEngine", "PFile", "Pref", "ProxyJavaObject", "ProxyObject",
        "ScriptRuntime", "AppUtils", "ScreenMetrics", "Shell", "ScriptInterruptedException", "JavaScriptSource", "ThemeColor", "App", "AndroidUtils",
        "ArrayUtils", "ColorUtils", "ConsoleUtils", "DeviceUtils", "DisplayUtils", "IntentUtils", "JavaUtils", "NetworkUtils", "NotificationUtils",
        "RhinoUtils", "RootMode", "RootUtils", "StringUtils", "TextUtils", "BuildConfig", "VolatileBox", "TopLevelScope", "Imgproc", "Imgcodecs",
        "CvType", "ScriptEngineService", "JsAppBarLayout", "JsButton", "JsCanvasView", "JsCardView", "JsCheckBox", "JsConsoleView", "JsDatePicker",
        "JsDrawerLayout", "JsEditText", "JsFloatingActionButton", "JsFrameLayout", "JsGridView", "JsImageButton", "JsImageView", "JsLinearLayout",
        "JsListView", "JsProgressBar", "JsRadioButton", "JsRadioGroup", "JsRatingBar", "JsRelativeLayout", "JsScrollView", "JsSeekBar", "JsSpinner",
        "JsSwitch", "JsTabLayout", "JsTextClock", "JsTimePicker", "JsToggleButton", "JsToolbar", "JsViewPager", "JsWebView", "EvaluatorException",
        "Image", "JsTextView",
    )

}

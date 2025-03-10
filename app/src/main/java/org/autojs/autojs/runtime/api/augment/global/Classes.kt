package org.autojs.autojs.runtime.api.augment.global

@Suppress("PropertyName", "unused")
class Classes {
    @JvmField
    val Manifest = android.Manifest::class

    @JvmField
    val GestureResultCallback = android.accessibilityservice.AccessibilityService.GestureResultCallback::class

    @JvmField
    val Notification = android.app.Notification::class

    @JvmField
    val NotificationManager = android.app.NotificationManager::class

    @JvmField
    val PendingIntent = android.app.PendingIntent::class

    @JvmField
    val Context = android.content.Context::class

    @JvmField
    val Intent = android.content.Intent::class

    @JvmField
    val PackageManager = android.content.pm.PackageManager::class

    @JvmField
    val ColorStateList = android.content.res.ColorStateList::class

    @JvmField
    val Bitmap = android.graphics.Bitmap::class

    @JvmField
    val BitmapFactory = android.graphics.BitmapFactory::class

    @JvmField
    val Paint = android.graphics.Paint::class

    @JvmField
    val PorterDuff = android.graphics.PorterDuff::class

    @JvmField
    val ColorDrawable = android.graphics.drawable.ColorDrawable::class

    @JvmField
    val Uri = android.net.Uri::class

    @JvmField
    val Build = android.os.Build::class

    @JvmField
    val Handler = android.os.Handler::class

    @JvmField
    val Looper = android.os.Looper::class

    @JvmField
    val Settings = android.provider.Settings::class

    @JvmField
    val TextToSpeech = android.speech.tts.TextToSpeech::class

    @JvmField
    val InputType = android.text.InputType::class

    @JvmField
    val TextWatcher = android.text.TextWatcher::class

    @JvmField
    val Linkify = android.text.util.Linkify::class

    @JvmField
    val Base64 = android.util.Base64::class

    @JvmField
    val Log = android.util.Log::class

    @JvmField
    val ContextThemeWrapper = android.view.ContextThemeWrapper::class

    @JvmField
    val Gravity = android.view.Gravity::class

    @JvmField
    val KeyEvent = android.view.KeyEvent::class

    @JvmField
    val MotionEvent = android.view.MotionEvent::class

    @JvmField
    val LayoutParams = android.view.WindowManager.LayoutParams::class

    @JvmField
    val MimeTypeMap = android.webkit.MimeTypeMap::class

    @JvmField
    val WebChromeClient = android.webkit.WebChromeClient::class

    @JvmField
    val WebView = android.webkit.WebView::class

    @JvmField
    val WebViewClient = android.webkit.WebViewClient::class

    @JvmField
    val Toast = android.widget.Toast::class

    @JvmField
    val BigTextStyle = androidx.core.app.NotificationCompat.BigTextStyle::class

    @JvmField
    val NotificationCompat = androidx.core.app.NotificationCompat::class

    @JvmField
    val FileProvider = androidx.core.content.FileProvider::class

    @JvmField
    val ImageViewCompat = androidx.core.widget.ImageViewCompat::class

    @JvmField
    val Snackbar = com.google.android.material.snackbar.Snackbar::class

    @JvmField
    val LogConfigurator = de.mindpipe.android.logging.log4j.LogConfigurator::class

    @JvmField
    val Version = io.github.g00fy2.versioncompare.Version::class

    @JvmField
    val ByteArrayOutputStream = java.io.ByteArrayOutputStream::class

    @JvmField
    val File = java.io.File::class

    @JvmField
    val Runnable = java.lang.Runnable::class

    @JvmField
    val SecurityException = java.lang.SecurityException::class

    @JvmField
    val System = java.lang.System::class

    @JvmField
    val Thread = java.lang.Thread::class

    @JvmField
    val Throwable = java.lang.Throwable::class

    @JvmField
    val URI = java.net.URI::class

    @JvmField
    val StandardCharsets = java.nio.charset.StandardCharsets::class

    @JvmField
    val TimeUnit = java.util.concurrent.TimeUnit::class

    @JvmField
    val Locale = java.util.Locale::class

    @JvmField
    val AtomicLong = java.util.concurrent.atomic.AtomicLong::class

    @JvmField
    val ReentrantLock = java.util.concurrent.locks.ReentrantLock::class

    @JvmField
    val Callback = okhttp3.Callback::class

    @JvmField
    val FormBody = okhttp3.FormBody::class

    @JvmField
    val MediaType = okhttp3.MediaType::class

    @JvmField
    val MultipartBody = okhttp3.MultipartBody::class

    @JvmField
    val OkHttpClient = okhttp3.OkHttpClient::class

    @JvmField
    val Request = okhttp3.Request::class

    @JvmField
    val RequestBody = okhttp3.RequestBody::class

    @JvmField
    val Level = org.apache.log4j.Level::class

    @JvmField
    val LogManager = org.apache.log4j.LogManager::class

    @JvmField
    val GlobalAppContext = org.autojs.autojs.app.GlobalAppContext::class

    @JvmField
    val AccessibilityBridge = org.autojs.autojs.core.accessibility.AccessibilityBridge::class

    @JvmField
    val UiSelector = org.autojs.autojs.core.accessibility.UiSelector::class

    @JvmField
    val ApkBuilder = org.autojs.autojs.apkbuilder.ApkBuilder::class

    @JvmField
    val UiObject = org.autojs.autojs.core.automator.UiObject::class

    @JvmField
    val UiObjectCollection = org.autojs.autojs.core.automator.UiObjectCollection::class

    @JvmField
    val Crypto = org.autojs.autojs.core.crypto.Crypto::class

    @JvmField
    val EventEmitter = org.autojs.autojs.core.eventloop.EventEmitter::class

    @JvmField
    val MutableOkHttp = org.autojs.autojs.core.http.MutableOkHttp::class

    @JvmField
    val ColorDetector = org.autojs.autojs.core.image.ColorDetector::class

    @JvmField
    val ColorTable = org.autojs.autojs.core.image.ColorTable::class

    @JvmField
    val ImageWrapper = org.autojs.autojs.core.image.ImageWrapper::class

    @JvmField
    val Mat = org.autojs.autojs.core.opencv.Mat::class

    @JvmField
    val DynamicLayoutInflater = org.autojs.autojs.core.ui.inflater.DynamicLayoutInflater::class

    @JvmField
    val ContinuationResult = org.autojs.autojs.rhino.continuation.Continuation.Result::class

    @JvmField
    val ContinuationCreator = org.autojs.autojs.runtime.api.augment.continuation.Creator::class

    @JvmField
    val JavaScriptEngine = org.autojs.autojs.engine.JavaScriptEngine::class

    @JvmField
    val PFile = org.autojs.autojs.pio.PFile::class

    @JvmField
    val Pref = org.autojs.autojs.core.pref.Pref::class

    @JvmField
    val ProxyJavaObject = org.autojs.autojs.rhino.ProxyJavaObject::class

    @JvmField
    val ProxyObject = org.autojs.autojs.rhino.ProxyObject::class

    @JvmField
    val ScriptRuntime = org.autojs.autojs.runtime.ScriptRuntime::class

    @JvmField
    val AppUtils = org.autojs.autojs.runtime.api.AppUtils::class

    @JvmField
    val ScreenMetrics = org.autojs.autojs.runtime.api.ScreenMetrics::class

    @JvmField
    val Shell = org.autojs.autojs.runtime.api.Shell::class

    @JvmField
    val ScriptInterruptedException = org.autojs.autojs.runtime.exception.ScriptInterruptedException::class

    @JvmField
    val JavaScriptSource = org.autojs.autojs.script.JavaScriptSource::class

    @JvmField
    val ThemeColor = org.autojs.autojs.theme.ThemeColor::class

    @JvmField
    val App = org.autojs.autojs.util.App::class

    @JvmField
    val AndroidUtils = org.autojs.autojs.util.AndroidUtils::class

    @JvmField
    val ArrayUtils = org.autojs.autojs.util.ArrayUtils::class

    @JvmField
    val ColorUtils = org.autojs.autojs.util.ColorUtils::class

    @JvmField
    val ConsoleUtils = org.autojs.autojs.util.ConsoleUtils::class

    @JvmField
    val DeviceUtils = org.autojs.autojs.util.DeviceUtils::class

    @JvmField
    val DisplayUtils = org.autojs.autojs.util.DisplayUtils::class

    @JvmField
    val JavaUtils = org.autojs.autojs.util.JavaUtils::class

    @JvmField
    val NetworkUtils = org.autojs.autojs.util.NetworkUtils::class

    @JvmField
    val NotificationUtils = org.autojs.autojs.util.NotificationUtils::class

    @JvmField
    val RhinoUtils = org.autojs.autojs.util.RhinoUtils::class

    @JvmField
    val RootMode = org.autojs.autojs.util.RootUtils.RootMode::class

    @JvmField
    val RootUtils = org.autojs.autojs.util.RootUtils::class

    @JvmField
    val StringUtils = org.autojs.autojs.util.StringUtils::class

    @JvmField
    val TextUtils = org.autojs.autojs.util.TextUtils::class

    @JvmField
    val BuildConfig = org.autojs.autojs6.BuildConfig::class

    @JvmField
    val VolatileBox = org.autojs.autojs.concurrent.VolatileBox::class

    @JvmField
    val TopLevelScope = org.autojs.autojs.rhino.TopLevelScope::class

    @JvmField
    val Imgproc = org.opencv.imgproc.Imgproc::class

    @JvmField
    val Imgcodecs = org.opencv.imgcodecs.Imgcodecs::class

    @JvmField
    val CvType = org.opencv.core.CvType::class

    @JvmField
    val ScriptEngineService = org.autojs.autojs.engine.ScriptEngineService::class

    @JvmField
    val JsAppBarLayout = org.autojs.autojs.core.ui.widget.JsAppBarLayout::class

    @JvmField
    val JsButton = org.autojs.autojs.core.ui.widget.JsButton::class

    @JvmField
    val JsCanvasView = org.autojs.autojs.core.ui.widget.JsCanvasView::class

    @JvmField
    val JsCardView = org.autojs.autojs.core.ui.widget.JsCardView::class

    @JvmField
    val JsCheckBox = org.autojs.autojs.core.ui.widget.JsCheckBox::class

    @JvmField
    val JsConsoleView = org.autojs.autojs.core.ui.widget.JsConsoleView::class

    @JvmField
    val JsDatePicker = org.autojs.autojs.core.ui.widget.JsDatePicker::class

    @JvmField
    val JsDrawerLayout = org.autojs.autojs.core.ui.widget.JsDrawerLayout::class

    @JvmField
    val JsEditText = org.autojs.autojs.core.ui.widget.JsEditText::class

    @JvmField
    val JsFloatingActionButton = org.autojs.autojs.core.ui.widget.JsFloatingActionButton::class

    @JvmField
    val JsFrameLayout = org.autojs.autojs.core.ui.widget.JsFrameLayout::class

    @JvmField
    val JsGridView = org.autojs.autojs.core.ui.widget.JsGridView::class

    @JvmField
    val JsImageButton = org.autojs.autojs.core.ui.widget.JsImageButton::class

    @JvmField
    val JsImageView = org.autojs.autojs.core.ui.widget.JsImageView::class

    @JvmField
    val JsLinearLayout = org.autojs.autojs.core.ui.widget.JsLinearLayout::class

    @JvmField
    val JsListView = org.autojs.autojs.core.ui.widget.JsListView::class

    @JvmField
    val JsProgressBar = org.autojs.autojs.core.ui.widget.JsProgressBar::class

    @JvmField
    val JsRadioButton = org.autojs.autojs.core.ui.widget.JsRadioButton::class

    @JvmField
    val JsRadioGroup = org.autojs.autojs.core.ui.widget.JsRadioGroup::class

    @JvmField
    val JsRatingBar = org.autojs.autojs.core.ui.widget.JsRatingBar::class

    @JvmField
    val JsRelativeLayout = org.autojs.autojs.core.ui.widget.JsRelativeLayout::class

    @JvmField
    val JsScrollView = org.autojs.autojs.core.ui.widget.JsScrollView::class

    @JvmField
    val JsSeekBar = org.autojs.autojs.core.ui.widget.JsSeekBar::class

    @JvmField
    val JsSpinner = org.autojs.autojs.core.ui.widget.JsSpinner::class

    @JvmField
    val JsSwitch = org.autojs.autojs.core.ui.widget.JsSwitch::class

    @JvmField
    val JsTabLayout = org.autojs.autojs.core.ui.widget.JsTabLayout::class

    @JvmField
    val JsTextClock = org.autojs.autojs.core.ui.widget.JsTextClock::class

    @JvmField
    val JsTimePicker = org.autojs.autojs.core.ui.widget.JsTimePicker::class

    @JvmField
    val JsToggleButton = org.autojs.autojs.core.ui.widget.JsToggleButton::class

    @JvmField
    val JsToolbar = org.autojs.autojs.core.ui.widget.JsToolbar::class

    @JvmField
    val JsViewPager = org.autojs.autojs.core.ui.widget.JsViewPager::class

    @JvmField
    val JsWebView = org.autojs.autojs.core.ui.widget.JsWebView::class

    @JvmField
    val Canvas = org.autojs.autojs.core.graphics.ScriptCanvas::class

    @JvmField
    val EvaluatorException = org.mozilla.javascript.EvaluatorException::class

    @JvmField
    val Image = ImageWrapper::class

    @JvmField
    val JsTextView = when {
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O -> {
            org.autojs.autojs.core.ui.widget.JsTextViewLegacy::class
        }
        else -> org.autojs.autojs.core.ui.widget.JsTextView::class
    }

}
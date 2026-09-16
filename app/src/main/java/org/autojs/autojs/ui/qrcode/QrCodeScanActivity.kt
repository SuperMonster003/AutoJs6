package org.autojs.autojs.ui.qrcode

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityQrCodeScanBinding

/**
 * Camera QR code scanner used to connect to the VSCode extension without typing the address.
 * zh-CN: 摄像头二维码扫描器, 用于免输入地址连接 VSCode 插件.
 *
 * Created by SuperMonster003 on Sep 16, 2026.
 */
class QrCodeScanActivity : BaseActivity() {

    private lateinit var binding: ActivityQrCodeScanBinding
    private lateinit var capture: CaptureManager
    private var delivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrCodeScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setToolbarAsBack(R.string.text_scan_qr_code)

        binding.hint.text = intent.getStringExtra(EXTRA_HINT) ?: getString(R.string.text_scan_qr_code_to_connect_hint)
        binding.barcodeScanner.setStatusText("")
        binding.barcodeScanner.barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))

        // CaptureManager handles camera lifecycle, orientation lock and the runtime permission request.
        // zh-CN: CaptureManager 负责摄像头生命周期, 方向锁定与运行时权限申请.
        capture = CaptureManager(this, binding.barcodeScanner)
        capture.initializeFromIntent(intent, savedInstanceState)
        capture.setShowMissingCameraPermissionDialog(true)

        binding.barcodeScanner.decodeSingle(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                deliver(result.text)
            }
        })
    }

    private fun deliver(text: String?) {
        if (delivered) return
        delivered = true
        val callback = pendingCallback
        pendingCallback = null
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT, text))
        finish()
        if (text != null) {
            callback?.invoke(text)
        }
    }

    override fun onResume() {
        super.onResume()
        capture.onResume()
    }

    override fun onPause() {
        super.onPause()
        capture.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        capture.onDestroy()
        if (!delivered) {
            pendingCallback = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capture.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        capture.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {

        const val EXTRA_HINT = "hint"
        const val EXTRA_RESULT = "result"

        @Volatile
        private var pendingCallback: ((String) -> Unit)? = null

        /**
         * Launch the scanner; `callback` receives the decoded text (invoked once, off the UI lifecycle of the caller).
         * zh-CN: 启动扫描器, `callback` 接收解码文本 (仅调用一次).
         */
        fun launch(context: Context, hint: String? = null, callback: (String) -> Unit) {
            pendingCallback = callback
            context.startActivity(Intent(context, QrCodeScanActivity::class.java).apply {
                hint?.let { putExtra(EXTRA_HINT, it) }
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
        }

    }

}

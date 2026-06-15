package org.autojs.autojs.ui.edit.editor.ace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.autojs.autojs.ui.edit.editor.ace.diagnostics.AceDiagnostics
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityAceEditorDebugBinding

class AceEditorDebugActivity : BaseActivity() {

    private lateinit var binding: ActivityAceEditorDebugBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sampleLoaded = false
    private var imeStressRunning = false
    private var imeStressStep = 0
    private val imeStressRunnable = Runnable {
        runImeStressStep()
    }

    private val sampleScript = """
        // AutoJs6 ACE debug
        toast("ACE editor is ready");
        console.log(device.width, device.height);

        for (let i = 0; i < 3; i++) {
            sleep(100);
            console.log("tick", i);
        }
    """.trimIndent()

    private val imeStressScript: String by lazy {
        buildString {
            appendLine("// AutoJs6 ACE IME stress sample")
            appendLine("toast(\"ACE IME stress sample\");")
            repeat(120) { index ->
                appendLine("console.log(\"ime-stress-line-${index + 1}\");")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAceEditorDebugBinding.inflate(layoutInflater).also { binding ->
            binding.aceEditor.listener = createAceListener()
            setContentView(binding.root)
        }
        setToolbarAsBack(R.string.text_ace_editor_debug)
        bindActions()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(imeStressRunnable)
        imeStressRunning = false
        binding.aceEditor.destroy()
        super.onDestroy()
    }

    private fun bindActions() {
        binding.actionSetSample.setOnClickListener {
            sampleLoaded = true
            binding.aceEditor.setText(sampleScript)
            binding.aceEditor.setDebuggingLine(1)
        }
        binding.actionGetText.setOnClickListener {
            binding.aceEditor.getText { text ->
                showToast(this, getString(R.string.text_ace_editor_debug_text_length, text.length))
            }
        }
        binding.actionInsert.setOnClickListener {
            binding.aceEditor.insert("\nconsole.log(\"inserted from Kotlin\");")
        }
        binding.actionJump.setOnClickListener {
            binding.aceEditor.jumpTo(1, 0)
            binding.aceEditor.setDebuggingLine(1)
        }
        binding.actionImeStress.setOnClickListener {
            startImeStress()
        }
        binding.actionUndo.setOnClickListener {
            binding.aceEditor.undo()
        }
        binding.actionRedo.setOnClickListener {
            binding.aceEditor.redo()
        }
        binding.actionBreakpoint.setOnClickListener {
            binding.aceEditor.addOrRemoveBreakpointAtCurrentLine()
        }
        binding.actionClearBreakpoints.setOnClickListener {
            binding.aceEditor.removeAllBreakpoints()
        }
        binding.actionCopyDiagnostics.setOnClickListener {
            ClipboardUtils.setClip(
                this,
                AceDiagnostics.format(
                    binding.aceEditor.createDiagnosticsSnapshot(
                        fileSizeBytes = binding.aceEditor.textSnapshot.length.toLong(),
                        largeFilePolicy = "debug",
                    ),
                ),
            )
            showToast(this, R.string.text_ace_diagnostics_copied)
        }
    }

    private fun startImeStress() {
        if (imeStressRunning) {
            return
        }
        sampleLoaded = true
        imeStressRunning = true
        imeStressStep = 0
        binding.actionImeStress.isEnabled = false
        binding.aceEditor.setText(imeStressScript)
        binding.aceEditor.setDebuggingLine(0)
        binding.aceStatus.text = getString(R.string.text_ace_ime_stress_running, 0, IME_STRESS_CYCLES)
        mainHandler.removeCallbacks(imeStressRunnable)
        mainHandler.postDelayed(imeStressRunnable, IME_STRESS_INITIAL_DELAY_MS)
    }

    private fun runImeStressStep() {
        if (!imeStressRunning) {
            return
        }
        val cycle = imeStressStep / 2
        if (cycle >= IME_STRESS_CYCLES) {
            finishImeStress()
            return
        }
        if (imeStressStep % 2 == 0) {
            val line = when (cycle % 3) {
                0 -> 1
                1 -> 48
                else -> 96
            }
            binding.aceEditor.jumpTo(line, 0)
            binding.aceEditor.setDebuggingLine(line)
            binding.aceEditor.requestEditorFocus()
            binding.aceEditor.ensureCursorVisible("debug_ime_stress_show_${cycle + 1}")
            binding.aceEditor.showSoftInput()
            binding.aceStatus.text = getString(R.string.text_ace_ime_stress_running, cycle + 1, IME_STRESS_CYCLES)
        } else {
            binding.aceEditor.hideSoftInput()
            binding.aceEditor.ensureCursorVisible("debug_ime_stress_hide_${cycle + 1}")
        }
        imeStressStep += 1
        mainHandler.postDelayed(imeStressRunnable, IME_STRESS_STEP_DELAY_MS)
    }

    private fun finishImeStress() {
        imeStressRunning = false
        binding.actionImeStress.isEnabled = true
        binding.aceEditor.hideSoftInput()
        binding.aceEditor.ensureCursorVisible("debug_ime_stress_done")
        binding.aceStatus.text = getString(R.string.text_ace_ime_stress_done, IME_STRESS_CYCLES)
        showToast(this, getString(R.string.text_ace_ime_stress_done, IME_STRESS_CYCLES))
    }

    private fun createAceListener(): AceCodeEditor.Listener {
        return object : AceCodeEditor.Listener {
            override fun onReady(state: AceCodeEditor.AceEditorState) {
                updateStatus("ready", state)
                if (!sampleLoaded) {
                    sampleLoaded = true
                    binding.aceEditor.setText(sampleScript)
                    binding.aceEditor.setDebuggingLine(1)
                }
            }

            override fun onStateChanged(state: AceCodeEditor.AceEditorState) {
                updateStatus("state", state)
            }

            override fun onTextChanged(text: String, state: AceCodeEditor.AceEditorState) {
                updateStatus("text ${text.length}", state)
            }

            override fun onCursorChanged(lineText: String, line: Int, column: Int, state: AceCodeEditor.AceEditorState) {
                updateStatus("cursor", state)
            }

            override fun onBreakpointChanged(line: Int, enabled: Boolean, state: AceCodeEditor.AceEditorState) {
                val prefix = if (enabled) "breakpoint ${line + 1}" else "clear ${line + 1}"
                updateStatus(prefix, state)
            }

            override fun onError(message: String) {
                binding.aceStatus.text = message
                showToast(this@AceEditorDebugActivity, message, true)
            }
        }
    }

    private fun updateStatus(prefix: String, state: AceCodeEditor.AceEditorState) {
        binding.aceStatus.text = getString(
            R.string.text_ace_editor_debug_status,
            prefix,
            state.lineCount,
            state.cursor.line + 1,
            state.cursor.column,
            state.breakpoints.size,
            state.dirty.toString(),
        )
    }

    companion object {
        private const val IME_STRESS_CYCLES = 20
        private const val IME_STRESS_INITIAL_DELAY_MS = 250L
        private const val IME_STRESS_STEP_DELAY_MS = 320L

        fun launch(context: Context) {
            context.startActivity(Intent(context, AceEditorDebugActivity::class.java))
        }
    }
}

package org.autojs.autojs.ui.edit.editor.ace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ViewUtils.showToast
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityAceEditorDebugBinding

class AceEditorDebugActivity : BaseActivity() {

    private lateinit var binding: ActivityAceEditorDebugBinding
    private var sampleLoaded = false

    private val sampleScript = """
        // AutoJs6 ACE debug
        toast("ACE editor is ready");
        console.log(device.width, device.height);

        for (let i = 0; i < 3; i++) {
            sleep(100);
            console.log("tick", i);
        }
    """.trimIndent()

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
        fun launch(context: Context) {
            context.startActivity(Intent(context, AceEditorDebugActivity::class.java))
        }
    }
}

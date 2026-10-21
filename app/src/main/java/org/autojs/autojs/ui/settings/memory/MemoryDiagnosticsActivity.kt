package org.autojs.autojs.ui.settings.memory

import android.os.Bundle
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ViewUtils.setToolbarAsBack
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityMemoryDiagnosticsBinding

/**
 * The memory diagnostics page (docs/memory/ROADMAP.md D8 / T10), opened from Developer options > Memory diagnostics; not exported.
 * zh-CN: 内存诊断页面 (docs/memory/ROADMAP.md D8 / T10), 从 开发者选项 > 内存诊断 打开; 未导出.
 */
class MemoryDiagnosticsActivity : BaseActivity() {

    private lateinit var binding: ActivityMemoryDiagnosticsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMemoryDiagnosticsBinding.inflate(layoutInflater).also { binding ->
            setContentView(binding.root)
            binding.toolbar.setNavigationOnClickListener { finish() }
        }

        setToolbarAsBack(this, R.string.text_memory_diagnostics)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_memory_diagnostics, MemoryDiagnosticsFragment())
                .disallowAddToBackStack()
                .commit()
        }
    }

    override fun onStart() {
        super.onStart()
        binding.toolbar.navigationIcon?.setTint(ThemeColorManager.getDayOrNightColorByLuminance(this))
    }

}

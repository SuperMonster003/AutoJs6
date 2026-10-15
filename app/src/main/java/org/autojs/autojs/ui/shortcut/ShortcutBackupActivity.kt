package org.autojs.autojs.ui.shortcut

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.autojs.autojs.external.shortcut.ShortcutBackupEntry
import org.autojs.autojs.external.shortcut.ShortcutBackupRepository
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityPreferencesBinding
import java.io.File

class ShortcutBackupPreference(context: Context, attrs: AttributeSet?) : MaterialPreference(context, attrs) {
    override fun onClick() {
        context.startActivity(Intent(context, ShortcutBackupActivity::class.java))
    }
}

class ShortcutBackupActivity : BaseActivity() {
    private lateinit var binding: ActivityPreferencesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        title = getString(R.string.shortcut_backup_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_preferences, ShortcutBackupFragment()).commit()
        }
    }

    override fun onStart() {
        super.onStart()
        binding.toolbar.navigationIcon?.setTint(ThemeColorManager.getDayOrNightColorByLuminance(this))
    }
}

class ShortcutBackupFragment : PreferenceFragmentCompat() {
    private val repository by lazy { ShortcutBackupRepository.get(requireContext()) }
    private val refreshMutex = Mutex()
    private var entries = emptyList<ShortcutBackupEntry>()
    private var queue = arrayListOf<String>()
    private var waiting: String? = null
    private var added = 0
    private var skipped = 0
    private var busy = false
    private var dialog: MaterialDialog? = null
    private var pendingImportSelection: Set<String>? = null
    private lateinit var restorePreference: Preference
    private lateinit var exportPreference: Preference
    private lateinit var importPreference: Preference

    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) operation {
            val resolver = requireContext().contentResolver
            withContext(Dispatchers.IO) {
                requireNotNull(resolver.openOutputStream(uri, "wt")).use(repository::export)
            }
            toast(R.string.shortcut_backup_exported)
        }
    }

    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) operation {
            val resolver = requireContext().contentResolver
            val ids = withContext(Dispatchers.IO) {
                requireNotNull(resolver.openInputStream(uri)).use(repository::import)
            }
            pendingImportSelection = ids.toSet()
            toast(R.string.shortcut_backup_imported)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        queue = savedInstanceState?.getStringArrayList("queue") ?: arrayListOf()
        waiting = savedInstanceState?.getString("waiting")
        added = savedInstanceState?.getInt("added") ?: 0
        skipped = savedInstanceState?.getInt("skipped") ?: 0
        pendingImportSelection = savedInstanceState?.getStringArrayList("importSelection")?.toSet()
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
        preference(R.string.shortcut_backup_description).apply { isSelectable = false }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            preference(R.string.shortcut_backup_legacy).apply { isSelectable = false }
        }
        restorePreference = preference(R.string.dialog_button_restore) { selectRestore() }
        exportPreference = preference(R.string.text_export) { exportFile.launch("AutoJs6-shortcuts.zip") }
        importPreference = preference(R.string.text_import) { importFile.launch(arrayOf("application/zip", "application/octet-stream")) }
    }

    private fun preference(titleRes: Int, action: (() -> Unit)? = null): Preference = MaterialPreference(requireContext()).apply {
        setTitle(titleRes)
        layoutResource = R.layout.preference_custom
        isSingleLineTitle = false
        isPersistent = false
        if (action != null) setOnPreferenceClickListener { action(); true }
        preferenceScreen.addPreference(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                repository.changes.collect { refresh() }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("queue", queue)
        outState.putString("waiting", waiting)
        outState.putInt("added", added)
        outState.putInt("skipped", skipped)
        pendingImportSelection?.let { outState.putStringArrayList("importSelection", ArrayList(it)) }
        super.onSaveInstanceState(outState)
    }

    private suspend fun refresh() = refreshMutex.withLock {
        if (busy || !isAdded) return@withLock
        try {
            entries = withContext(Dispatchers.IO) { repository.reconcile().entries }
            restorePreference.summary = getString(R.string.shortcut_backup_count, entries.size)
            val idle = queue.isEmpty()
            restorePreference.isEnabled = idle && entries.any { it.status != ShortcutBackupEntry.Status.PINNED }
            exportPreference.isEnabled = idle && entries.isNotEmpty()
            importPreference.isEnabled = idle
            if (queue.isNotEmpty()) advance()
            if (pendingImportSelection != null && idle && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                val ids = pendingImportSelection
                pendingImportSelection = null
                selectRestore(ids)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            showError(error)
        }
    }

    private fun selectRestore(ids: Set<String>? = null) {
        val candidates = entries.filter { it.status != ShortcutBackupEntry.Status.PINNED && (ids == null || it.id in ids) }
        if (candidates.isEmpty()) {
            toast(R.string.shortcut_backup_empty)
            return
        }
        val checked = candidates.indices.filter {
            candidates[it].status in setOf(ShortcutBackupEntry.Status.MISSING, ShortcutBackupEntry.Status.SAVED)
        }.toTypedArray()
        dialog?.dismiss()
        dialog = MaterialDialog.Builder(requireContext())
            .title(R.string.shortcut_backup_title)
            .items(candidates.mapIndexed { index, item -> "${index + 1}. ${item.shortLabel}\n${status(item)}${item.path?.let { path -> "\n$path" }.orEmpty()}" })
            .itemsCallbackMultiChoice(checked) { selection, which, _ ->
                selection.dismiss()
                dialog = null
                queue = ArrayList(which.map { candidates[it].id })
                added = 0
                skipped = 0
                waiting = null
                viewLifecycleOwner.lifecycleScope.launch { refresh() }
                true
            }
            .positiveText(R.string.dialog_button_restore)
            .negativeText(R.string.dialog_button_cancel)
            .neutralText(R.string.shortcut_backup_ignore)
            .onNeutral { selection, _ ->
                val ignored = selection.selectedIndices?.map { candidates[it].id }?.toSet().orEmpty()
                operation { withContext(Dispatchers.IO) { repository.ignore(ignored) } }
            }
            .show()
    }

    private fun status(entry: ShortcutBackupEntry): String = getString(when (entry.status) {
        ShortcutBackupEntry.Status.PINNED -> R.string.shortcut_backup_status_pinned
        ShortcutBackupEntry.Status.MISSING -> R.string.shortcut_backup_status_missing
        ShortcutBackupEntry.Status.SAVED -> R.string.shortcut_backup_status_saved
        ShortcutBackupEntry.Status.REMOVED -> R.string.shortcut_backup_status_removed
        ShortcutBackupEntry.Status.PENDING -> R.string.shortcut_backup_status_pending
    })

    /** Only one launcher confirmation is outstanding. Cancel has no platform callback. */
    private suspend fun advance() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        while (queue.isNotEmpty()) {
            val id = queue.first()
            val entry = entries.firstOrNull { it.id == id }
            if (entry == null || entry.status == ShortcutBackupEntry.Status.PINNED) {
                if (waiting == id && entry != null) added++ else skipped++
                queue.removeAt(0)
                waiting = null
                dialog?.dismiss()
                dialog = null
                continue
            }
            if (waiting == null) {
                try {
                    withContext(Dispatchers.IO) {
                        if (entry.path != null) {
                            check(File(entry.path).exists() && File(entry.path).canRead()) {
                                getString(R.string.shortcut_backup_missing_file, entry.path)
                            }
                        }
                    }
                    waiting = id
                    check(withContext(Dispatchers.IO) { repository.requestPin(id) }) {
                        getString(R.string.text_pin_shortcut_not_unsupported)
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        // Legacy launchers have no reliable pin state. Count as unconfirmed.
                        skipped++
                        queue.removeAt(0)
                        waiting = null
                        continue
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    waiting = null
                    queue.removeAt(0)
                    skipped++
                    showError(error)
                    continue
                }
            }
            if (dialog?.isShowing != true) {
                dialog = MaterialDialog.Builder(requireContext())
                    .title(entry.shortLabel)
                    .content(R.string.shortcut_backup_waiting)
                    .positiveText(R.string.text_skip)
                    .negativeText(R.string.text_stop)
                    .cancelable(false)
                    .onPositive { _, _ ->
                        queue.removeAt(0)
                        skipped++
                        waiting = null
                        dialog = null
                        if (queue.isEmpty()) reportCompleted()
                        viewLifecycleOwner.lifecycleScope.launch { refresh() }
                    }
                    .onNegative { _, _ -> queue.clear(); waiting = null; dialog = null; viewLifecycleOwner.lifecycleScope.launch { refresh() } }
                    .show()
            }
            return
        }
        restorePreference.isEnabled = true
        exportPreference.isEnabled = entries.isNotEmpty()
        importPreference.isEnabled = true
        reportCompleted()
    }

    private fun reportCompleted() = ViewUtils.showToast(requireContext(), getString(R.string.shortcut_backup_complete, added, skipped), true)

    private fun operation(block: suspend () -> Unit) {
        if (busy || queue.isNotEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            busy = true
            restorePreference.isEnabled = false
            importPreference.isEnabled = false
            exportPreference.isEnabled = false
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError(error)
            } finally {
                busy = false
            }
            refresh()
        }
    }

    private fun showError(error: Exception) {
        if (isAdded) ViewUtils.showToast(requireContext(), getString(R.string.shortcut_backup_error, error.message.orEmpty()), true)
    }

    private fun toast(resource: Int) = ViewUtils.showToast(requireContext(), getString(resource), true)

    override fun onDestroyView() {
        dialog?.dismiss()
        dialog = null
        super.onDestroyView()
    }
}

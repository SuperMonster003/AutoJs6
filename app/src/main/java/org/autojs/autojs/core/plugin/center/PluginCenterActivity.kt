package org.autojs.autojs.core.plugin.center

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Space
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.launch
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.widget.SearchViewItem
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils.onceGlobalLayout
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByThemeColorLuminance
import org.autojs.autojs.util.ViewUtils.setNavigationIconColorByThemeColorLuminance
import org.autojs.autojs.util.ViewUtils.setTitlesTextColorByThemeColorLuminance
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityPluginCenterBinding
import org.autojs.autojs6.databinding.DialogPluginCenterFilterBinding
import org.autojs.autojs6.databinding.DialogPluginCenterSortBinding

@SuppressLint("NotifyDataSetChanged")
class PluginCenterActivity : BaseActivity() {

    private lateinit var binding: ActivityPluginCenterBinding

    private var searchViewItem: SearchViewItem? = null
    private var sourceScope: PluginIndexSource? = null

    private val pickApkLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            PluginInstaller.installFromFileUriWithPrompt(this@PluginCenterActivity, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sourceScope = intent.getStringExtra(EXTRA_SOURCE_SCOPE)
            ?.runCatching { PluginIndexSource.valueOf(this) }
            ?.getOrNull()

        binding = ActivityPluginCenterBinding.inflate(layoutInflater).also {
            setContentView(it.root)
        }

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_plugin_center, PluginCenterFragment.newInstance(sourceScope))
            .commit()

        val titleRes = when (sourceScope) {
            PluginIndexSource.OFFICIAL -> R.string.text_plugin_library_official
            PluginIndexSource.THIRD_PARTY -> R.string.text_plugin_library_third_party
            null -> R.string.text_plugin_center
        }

        setToolbarAsBack(titleRes)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_plugin_center, menu)
        val scopedMode = sourceScope != null
        menu?.findItem(R.id.action_add)?.isVisible = !scopedMode
        menu?.findItem(R.id.action_filter)?.isVisible = !scopedMode
        menu?.findItem(R.id.action_global_settings)?.isVisible = !scopedMode
        setUpSearchMenuItem(menu)
        setUpToolbarColors()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val center = supportFragmentManager.findFragmentById(R.id.fragment_plugin_center) as? PluginCenterFragment

        return when (item.itemId) {
            R.id.action_install_from_official_library -> {
                launch(this, PluginIndexSource.OFFICIAL)
                true
            }
            R.id.action_install_from_third_party_library -> {
                launch(this, PluginIndexSource.THIRD_PARTY)
                true
            }
            R.id.action_install_from_local_file -> {
                PluginInstallActions.installFromLocalFile(pickApkLauncher)
                true
            }
            R.id.action_install_from_url -> {
                PluginInstallActions.showInstallFromUrlDialog(this, lifecycleScope)
                true
            }
            R.id.action_search -> {
                // Handled by SearchViewItem.
                // zh-CN: 由 SearchViewItem 处理.
                super.onOptionsItemSelected(item)
            }
            R.id.action_sort -> {
                showSortDialog(center)
                true
            }
            R.id.action_filter -> {
                showFilterDialog(center)
                true
            }
            R.id.action_global_settings -> {
                PluginCenterSettingsActivity.launch(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setUpSearchMenuItem(menu: Menu?) {
        val m = menu ?: return
        val searchMenuItem = m.findItem(R.id.action_search) ?: return

        searchViewItem = object : SearchViewItem(this, searchMenuItem) {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                binding.toolbar.onceGlobalLayout { setUpToolbarColors() }
                return super.onMenuItemActionExpand(item)
            }
        }.apply {
            setQueryCallback(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?) = true.also { submitQueryToFragment(query) }
                override fun onQueryTextChange(newText: String?) = true.also { submitQueryToFragment(newText) }
            })
        }
    }

    private fun submitQueryToFragment(query: String?) {
        val center = supportFragmentManager.findFragmentById(R.id.fragment_plugin_center) as? PluginCenterFragment ?: return
        center.setQuery(query)
    }

    private fun showSortDialog(center: PluginCenterFragment?) {
        if (center == null) return

        val themeColorStateList = ThemeColorHelper.getThemeColorStateList(this)

        val entries = PluginCenterFragment.Sort.primarySortDisplayOrder()
        val currentSort = loadPersistedSort()

        val binding = DialogPluginCenterSortBinding.inflate(LayoutInflater.from(this))
        val contentView = binding.root
        val sortFieldContainer = binding.pluginCenterSortGroup
        val sortOrderGroup = binding.pluginCenterSortOrderGroup
        val sortAscendingButton = binding.pluginCenterSortOrderAscendingRadio.apply {
            buttonTintList = themeColorStateList
        }
        val sortDescendingButton = binding.pluginCenterSortOrderDescendingRadio.apply {
            buttonTintList = themeColorStateList
        }
        val secondaryContainer = binding.pluginCenterSortSecondaryContainer
        val secondarySpinner = binding.pluginCenterSortSecondarySpinner
        var currentSecondaryCandidates: List<PluginCenterFragment.Sort> = emptyList()
        val radioIdToSort = linkedMapOf<Int, PluginCenterFragment.Sort>()
        val sortFieldButtons = mutableListOf<RadioButton>()
        var selectedSortFieldButtonId: Int = View.NO_ID
        var suppressSortSelectionCallback = false
        val itemSpacing = (8 * resources.displayMetrics.density).toInt()
        val rowSpacing = (4 * resources.displayMetrics.density).toInt()
        val controlMinHeight = (36 * resources.displayMetrics.density).toInt()
        val controlVerticalPadding = (4 * resources.displayMetrics.density).toInt()
        val radioTextSizeSp = 15f
        var refreshDirectionAndSecondary: (PluginCenterFragment.Sort) -> Unit = {}

        sortAscendingButton.apply {
            minHeight = controlMinHeight
            setPaddingRelative(0, controlVerticalPadding, 0, controlVerticalPadding)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, radioTextSizeSp)
        }
        sortDescendingButton.apply {
            minHeight = controlMinHeight
            setPaddingRelative(0, controlVerticalPadding, 0, controlVerticalPadding)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, radioTextSizeSp)
        }

        fun secondarySpinnerAdapter(labels: List<String>): ArrayAdapter<String> =
            object : ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                labels,
            ) {
                init {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }

                override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                    return super.getView(position, convertView, parent).apply {
                        (this as? TextView)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, radioTextSizeSp)
                    }
                }

                override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                    return super.getDropDownView(position, convertView, parent).apply {
                        (this as? TextView)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, radioTextSizeSp)
                    }
                }
            }

        fun calculateTextWidthWeight(text: CharSequence): Int =
            text.sumOf { ch -> if (ch.code <= 0x7F) 1 else 2 }

        fun shouldUseSingleFullRow(label: CharSequence): Boolean =
            calculateTextWidthWeight(label) > 14

        fun createSortFieldButton(sort: PluginCenterFragment.Sort): RadioButton =
            RadioButton(this).apply {
                id = View.generateViewId()
                text = getString(sort.titleRes)
                buttonTintList = themeColorStateList
                minHeight = controlMinHeight
                setPaddingRelative(0, controlVerticalPadding, 0, controlVerticalPadding)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, radioTextSizeSp)
                setLineSpacing(2f, 1f)
                setOnCheckedChangeListener { _, isChecked ->
                    if (!isChecked || suppressSortSelectionCallback) return@setOnCheckedChangeListener
                    selectedSortFieldButtonId = id
                    suppressSortSelectionCallback = true
                    sortFieldButtons.forEach { button ->
                        if (button.id != id) {
                            button.isChecked = false
                        }
                    }
                    suppressSortSelectionCallback = false
                    refreshDirectionAndSecondary(sort)
                }
            }

        fun addSortFieldRow(cells: List<Pair<PluginCenterFragment.Sort, Boolean>>) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            cells.forEachIndexed { index, (sort, isFullRow) ->
                val button = createSortFieldButton(sort)
                val params = if (isFullRow) {
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                } else {
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
                }.apply {
                    if (!isFullRow && index == 0 && cells.size > 1) {
                        marginEnd = itemSpacing
                    }
                }
                row.addView(button, params)
                radioIdToSort[button.id] = sort
                sortFieldButtons += button
            }

            if (cells.size == 1 && !cells.first().second) {
                row.addView(
                    Space(this),
                    LinearLayout.LayoutParams(0, 0, 1f),
                )
            }

            sortFieldContainer.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (sortFieldContainer.childCount > 0) {
                        topMargin = rowSpacing
                    }
                },
            )
        }

        run {
            var index = 0
            while (index < entries.size) {
                val current = entries[index]
                val currentLabel = getString(current.titleRes)
                val currentFull = shouldUseSingleFullRow(currentLabel)
                if (currentFull) {
                    addSortFieldRow(listOf(current to true))
                    index += 1
                    continue
                }

                val next = entries.getOrNull(index + 1)
                if (next == null) {
                    addSortFieldRow(listOf(current to false))
                    index += 1
                    continue
                }

                val nextFull = shouldUseSingleFullRow(getString(next.titleRes))
                if (nextFull) {
                    addSortFieldRow(listOf(current to false))
                    index += 1
                } else {
                    addSortFieldRow(listOf(current to false, next to false))
                    index += 2
                }
            }
        }

        fun selectedPrimarySort(): PluginCenterFragment.Sort {
            return radioIdToSort[selectedSortFieldButtonId] ?: currentSort
        }

        fun selectSortFieldButton(id: Int, notify: Boolean) {
            if (id == View.NO_ID || id !in radioIdToSort.keys) return
            val selectedSort = radioIdToSort[id] ?: return
            selectedSortFieldButtonId = id
            suppressSortSelectionCallback = true
            sortFieldButtons.forEach { button ->
                button.isChecked = button.id == id
            }
            suppressSortSelectionCallback = false
            if (notify) {
                refreshDirectionAndSecondary(selectedSort)
            }
        }

        fun loadPersistedSecondarySort(primarySort: PluginCenterFragment.Sort): PluginCenterFragment.Sort = when (primarySort) {
            PluginCenterFragment.Sort.MECHANISM -> loadPersistedMechanismSecondarySort()
            PluginCenterFragment.Sort.SOURCE -> loadPersistedSourceSecondarySort()
            else -> primarySort.defaultSecondarySort()
        }

        fun savePersistedSecondarySort(primarySort: PluginCenterFragment.Sort, secondarySort: PluginCenterFragment.Sort) {
            when (primarySort) {
                PluginCenterFragment.Sort.MECHANISM -> savePersistedMechanismSecondarySort(secondarySort)
                PluginCenterFragment.Sort.SOURCE -> savePersistedSourceSecondarySort(secondarySort)
                else -> Unit
            }
        }

        refreshDirectionAndSecondary = { sort ->
            val isSortAscending = loadPersistedSortAscending(sort)
            if (isSortAscending) {
                sortOrderGroup.check(sortAscendingButton.id)
            } else {
                sortOrderGroup.check(sortDescendingButton.id)
            }

            if (!sort.supportsSecondarySort()) {
                currentSecondaryCandidates = emptyList()
                secondaryContainer.isVisible = false
            } else {
                currentSecondaryCandidates = PluginCenterFragment.Sort.secondaryCandidatesFor(sort)
                val labels = currentSecondaryCandidates.map { getString(it.titleRes) }
                secondarySpinner.adapter = secondarySpinnerAdapter(labels)
                val persistedSecondary = loadPersistedSecondarySort(sort)
                    .takeUnless { it == sort }
                    ?: sort.defaultSecondarySort()
                val defaultSecondary = sort.defaultSecondarySort()
                val secondaryIndex = currentSecondaryCandidates.indexOf(persistedSecondary)
                    .takeIf { it >= 0 }
                    ?: currentSecondaryCandidates.indexOf(defaultSecondary).coerceAtLeast(0)
                secondarySpinner.setSelection(secondaryIndex, false)
                secondaryContainer.isVisible = true
            }
        }

        val initialRadioId = radioIdToSort.entries
            .firstOrNull { it.value == currentSort }
            ?.key
            ?: radioIdToSort.keys.firstOrNull()
            ?: View.NO_ID
        selectSortFieldButton(initialRadioId, notify = false)
        refreshDirectionAndSecondary(selectedPrimarySort())

        MaterialDialog.Builder(this)
            .title(R.string.text_sort)
            .options(
                listOf(
                    MaterialDialog.OptionMenuItemSpec(getString(R.string.dialog_button_use_default)) { _ ->
                        clearPersistedSortMemory()
                        val defaultSort = PluginCenterFragment.Sort.defaultPrimarySort()
                        center.setSort(defaultSort)
                        val sortFieldButtonId = radioIdToSort.entries
                            .firstOrNull { it.value == defaultSort }
                            ?.key
                            ?: radioIdToSort.keys.firstOrNull()
                            ?: View.NO_ID
                        selectSortFieldButton(sortFieldButtonId, notify = true)
                    },
                ),
            )
            .customView(contentView, false)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { d, _ ->
                d.dismiss()
                val selectedSort = selectedPrimarySort()
                savePersistedSort(selectedSort)
                savePersistedSortAscending(selectedSort, sortAscendingButton.isChecked)
                if (selectedSort.supportsSecondarySort()) {
                    val selectedSecondarySort = currentSecondaryCandidates
                        .getOrNull(secondarySpinner.selectedItemPosition)
                        ?.takeUnless { it == selectedSort }
                        ?: selectedSort.defaultSecondarySort()
                    savePersistedSecondarySort(selectedSort, selectedSecondarySort)
                }
                center.setSort(selectedSort)
            }
            .show()
    }

    private fun loadPersistedSort(): PluginCenterFragment.Sort = when (sourceScope) {
        PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.getSort(this)
        PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.getSort(this)
        null -> PluginSortStore.getSort(this)
    }

    private fun loadPersistedSortAscending(sort: PluginCenterFragment.Sort): Boolean = when (sourceScope) {
        PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.getAscending(this, sort)
        PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.getAscending(this, sort)
        null -> PluginSortStore.getAscending(this, sort)
    }

    private fun loadPersistedMechanismSecondarySort(): PluginCenterFragment.Sort = when (sourceScope) {
        PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.getMechanismSecondarySort(this)
        PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.getMechanismSecondarySort(this)
        null -> PluginSortStore.getMechanismSecondarySort(this)
    }

    private fun loadPersistedSourceSecondarySort(): PluginCenterFragment.Sort = when (sourceScope) {
        PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.getSourceSecondarySort(this)
        PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.getSourceSecondarySort(this)
        null -> PluginSortStore.getSourceSecondarySort(this)
    }

    private fun savePersistedSort(sort: PluginCenterFragment.Sort) {
        when (sourceScope) {
            PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.setSort(this, sort)
            PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.setSort(this, sort)
            null -> PluginSortStore.setSort(this, sort)
        }
    }

    private fun savePersistedSortAscending(sort: PluginCenterFragment.Sort, ascending: Boolean) {
        when (sourceScope) {
            PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.setAscending(this, sort, ascending)
            PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.setAscending(this, sort, ascending)
            null -> PluginSortStore.setAscending(this, sort, ascending)
        }
    }

    private fun savePersistedMechanismSecondarySort(sort: PluginCenterFragment.Sort) {
        when (sourceScope) {
            PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.setMechanismSecondarySort(this, sort)
            PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.setMechanismSecondarySort(this, sort)
            null -> PluginSortStore.setMechanismSecondarySort(this, sort)
        }
    }

    private fun savePersistedSourceSecondarySort(sort: PluginCenterFragment.Sort) {
        when (sourceScope) {
            PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.setSourceSecondarySort(this, sort)
            PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.setSourceSecondarySort(this, sort)
            null -> PluginSortStore.setSourceSecondarySort(this, sort)
        }
    }

    private fun clearPersistedSortMemory() {
        when (sourceScope) {
            PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.clearAllSortMemory(this)
            PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.clearAllSortMemory(this)
            null -> PluginSortStore.clearAllSortMemory(this)
        }
    }

    private fun showFilterDialog(center: PluginCenterFragment?) {
        if (center == null) return

        val themeColorStateList = ThemeColorHelper.getThemeColorStateList(this)
        val currentState = PluginFilterStore.getFilterState(this)
        val binding = DialogPluginCenterFilterBinding.inflate(LayoutInflater.from(this))
        val contentView = binding.root
        val itemSpacing = (8 * resources.displayMetrics.density).toInt()
        val rowSpacing = (4 * resources.displayMetrics.density).toInt()
        val controlMinHeight = (36 * resources.displayMetrics.density).toInt()
        val controlVerticalPadding = (4 * resources.displayMetrics.density).toInt()
        val radioTextSizeSp = 15f

        data class FilterOption<T>(
            val value: T,
            val titleRes: Int,
        )

        fun calculateTextWidthWeight(text: CharSequence): Int =
            text.sumOf { ch -> if (ch.code <= 0x7F) 1 else 2 }

        fun shouldUseSingleFullRow(label: CharSequence): Boolean =
            calculateTextWidthWeight(label) > 14

        class OptionGroupBinder<T>(
            private val container: LinearLayout,
            private val options: List<FilterOption<T>>,
            initialValue: T,
            private val forceSingleRowValue: T? = null,
        ) {
            private val buttonIdToValue = linkedMapOf<Int, T>()
            private val buttons = mutableListOf<RadioButton>()
            private var suppressSelectionCallback = false
            private var selectedButtonId: Int = View.NO_ID

            init {
                buildRows()
                select(initialValue)
            }

            fun selected(): T = buttonIdToValue[selectedButtonId] ?: options.first().value

            fun select(value: T) {
                val targetButtonId = buttonIdToValue.entries
                    .firstOrNull { it.value == value }
                    ?.key
                    ?: buttonIdToValue.keys.firstOrNull()
                    ?: View.NO_ID
                if (targetButtonId == View.NO_ID) return
                selectedButtonId = targetButtonId
                suppressSelectionCallback = true
                buttons.forEach { button -> button.isChecked = button.id == targetButtonId }
                suppressSelectionCallback = false
            }

            private fun createOptionButton(option: FilterOption<T>): RadioButton =
                RadioButton(this@PluginCenterActivity).apply {
                    id = View.generateViewId()
                    text = getString(option.titleRes)
                    buttonTintList = themeColorStateList
                    minHeight = controlMinHeight
                    setPaddingRelative(0, controlVerticalPadding, 0, controlVerticalPadding)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, radioTextSizeSp)
                    setLineSpacing(2f, 1f)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (!isChecked || suppressSelectionCallback) return@setOnCheckedChangeListener
                        selectedButtonId = id
                        suppressSelectionCallback = true
                        buttons.forEach { button ->
                            if (button.id != id) {
                                button.isChecked = false
                            }
                        }
                        suppressSelectionCallback = false
                    }
                }

            private fun addRow(cells: List<Pair<FilterOption<T>, Boolean>>) {
                val row = LinearLayout(this@PluginCenterActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                cells.forEachIndexed { index, (option, isFullRow) ->
                    val button = createOptionButton(option)
                    val params = if (isFullRow) {
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                    } else {
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f,
                        )
                    }.apply {
                        if (!isFullRow && index == 0 && cells.size > 1) {
                            marginEnd = itemSpacing
                        }
                    }
                    row.addView(button, params)
                    buttonIdToValue[button.id] = option.value
                    buttons += button
                }

                if (cells.size == 1 && !cells.first().second) {
                    row.addView(
                        Space(this@PluginCenterActivity),
                        LinearLayout.LayoutParams(0, 0, 1f),
                    )
                }

                container.addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (container.childCount > 0) {
                            topMargin = rowSpacing
                        }
                    },
                )
            }

            private fun buildRows() {
                var index = 0
                while (index < options.size) {
                    val current = options[index]
                    val currentFull = (forceSingleRowValue != null && current.value == forceSingleRowValue) ||
                            shouldUseSingleFullRow(getString(current.titleRes))
                    if (currentFull) {
                        addRow(listOf(current to true))
                        index += 1
                        continue
                    }

                    val next = options.getOrNull(index + 1)
                    if (next == null) {
                        addRow(listOf(current to false))
                        index += 1
                        continue
                    }

                    val nextFull = shouldUseSingleFullRow(getString(next.titleRes))
                    val nextForceSingle = forceSingleRowValue != null && next.value == forceSingleRowValue
                    if (nextFull || nextForceSingle) {
                        addRow(listOf(current to false))
                        index += 1
                    } else {
                        addRow(listOf(current to false, next to false))
                        index += 2
                    }
                }
            }
        }

        val installGroup = OptionGroupBinder(
            container = binding.pluginCenterFilterInstallGroup,
            options = listOf(
                FilterOption(PluginFilterStore.InstallState.ALL, PluginFilterStore.InstallState.ALL.titleRes),
                FilterOption(PluginFilterStore.InstallState.INSTALLED, PluginFilterStore.InstallState.INSTALLED.titleRes),
                FilterOption(PluginFilterStore.InstallState.NOT_INSTALLED, PluginFilterStore.InstallState.NOT_INSTALLED.titleRes),
            ),
            initialValue = currentState.installState,
            forceSingleRowValue = PluginFilterStore.InstallState.ALL,
        )
        val enabledGroup = OptionGroupBinder(
            container = binding.pluginCenterFilterEnabledGroup,
            options = listOf(
                FilterOption(PluginFilterStore.EnabledState.ALL, PluginFilterStore.EnabledState.ALL.titleRes),
                FilterOption(PluginFilterStore.EnabledState.ENABLED, PluginFilterStore.EnabledState.ENABLED.titleRes),
                FilterOption(PluginFilterStore.EnabledState.DISABLED, PluginFilterStore.EnabledState.DISABLED.titleRes),
            ),
            initialValue = currentState.enabledState,
            forceSingleRowValue = PluginFilterStore.EnabledState.ALL,
        )
        val mechanismGroup = OptionGroupBinder(
            container = binding.pluginCenterFilterMechanismGroup,
            options = listOf(
                FilterOption(PluginFilterStore.MechanismState.ALL, PluginFilterStore.MechanismState.ALL.titleRes),
                FilterOption(PluginFilterStore.MechanismState.AIDL, PluginFilterStore.MechanismState.AIDL.titleRes),
                FilterOption(PluginFilterStore.MechanismState.SDK, PluginFilterStore.MechanismState.SDK.titleRes),
            ),
            initialValue = currentState.mechanismState,
            forceSingleRowValue = PluginFilterStore.MechanismState.ALL,
        )
        val sourceGroup = OptionGroupBinder(
            container = binding.pluginCenterFilterSourceGroup,
            options = listOf(
                FilterOption(PluginFilterStore.SourceState.ALL, PluginFilterStore.SourceState.ALL.titleRes),
                FilterOption(PluginFilterStore.SourceState.OFFICIAL, PluginFilterStore.SourceState.OFFICIAL.titleRes),
                FilterOption(PluginFilterStore.SourceState.THIRD_PARTY, PluginFilterStore.SourceState.THIRD_PARTY.titleRes),
            ),
            initialValue = currentState.sourceState,
            forceSingleRowValue = PluginFilterStore.SourceState.ALL,
        )

        val updatableOnlyCheckbox = binding.pluginCenterFilterUpdatableOnlyCheckbox.apply {
            buttonTintList = themeColorStateList
            isChecked = currentState.updatableOnly
            minHeight = controlMinHeight
            setPaddingRelative(0, controlVerticalPadding, 0, controlVerticalPadding)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, radioTextSizeSp)
        }

        fun readFilterState(): PluginFilterStore.FilterState = PluginFilterStore.FilterState(
            installState = installGroup.selected(),
            enabledState = enabledGroup.selected(),
            mechanismState = mechanismGroup.selected(),
            sourceState = sourceGroup.selected(),
            updatableOnly = updatableOnlyCheckbox.isChecked,
        )

        fun applyFilterState(state: PluginFilterStore.FilterState) {
            installGroup.select(state.installState)
            enabledGroup.select(state.enabledState)
            mechanismGroup.select(state.mechanismState)
            sourceGroup.select(state.sourceState)
            updatableOnlyCheckbox.isChecked = state.updatableOnly
        }

        MaterialDialog.Builder(this)
            .title(R.string.text_filter)
            .options(
                listOf(
                    MaterialDialog.OptionMenuItemSpec(getString(R.string.dialog_button_use_default)) { _ ->
                        applyFilterState(PluginFilterStore.defaultFilterState())
                    },
                ),
            )
            .customView(contentView, false)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { d, _ ->
                d.dismiss()
                val selectedState = readFilterState()
                if (selectedState.isDefault()) {
                    PluginFilterStore.clearAllFilterMemory(this)
                } else {
                    PluginFilterStore.setFilterState(this, selectedState)
                }
                center.setFilterState(selectedState)
            }
            .show()
    }

    private fun setUpToolbarColors() {
        binding.toolbar.setMenuIconsColorByThemeColorLuminance(this)
        binding.toolbar.setNavigationIconColorByThemeColorLuminance(this)
        binding.toolbar.setTitlesTextColorByThemeColorLuminance(this)
        searchViewItem?.setColorsByThemeColorLuminance()
    }

    override fun onDestroy() {
        super.onDestroy()
        searchViewItem = null
    }

    companion object {
        private const val EXTRA_SOURCE_SCOPE = "extra_source_scope"

        fun launch(context: Context, sourceScope: PluginIndexSource? = null) {
            Intent(context, PluginCenterActivity::class.java).apply {
                if (sourceScope != null) {
                    putExtra(EXTRA_SOURCE_SCOPE, sourceScope.name)
                }
            }.startSafely(context)
        }
    }
}

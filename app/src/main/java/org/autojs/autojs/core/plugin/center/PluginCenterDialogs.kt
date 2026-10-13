package org.autojs.autojs.core.plugin.center

import android.text.util.Linkify
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogPluginCenterFilterBinding
import org.autojs.autojs6.databinding.DialogPluginCenterSortBinding

class PluginCenterDialogs(
    private val activity: FragmentActivity,
    private val sourceScope: PluginIndexSource? = null,
) {

    fun showSortDialog(center: PluginCenterFragment?) {
        if (center == null) return

        val themeColorStateList = ThemeColorHelper.getThemeColorStateList(activity)

        val entries = PluginCenterFragment.Sort.primarySortDisplayOrder()
        val currentSort = loadPersistedSort()

        val binding = DialogPluginCenterSortBinding.inflate(LayoutInflater.from(activity))
        val contentView = binding.root
        val sortFieldContainer = binding.pluginCenterSortGroup
        val sortOrderGroup = binding.pluginCenterSortOrderGroup
        val sortAscendingButton = binding.pluginCenterSortOrderAscendingRadio.apply {
            buttonTintList = themeColorStateList
        }
        val sortDescendingButton = binding.pluginCenterSortOrderDescendingRadio.apply {
            buttonTintList = themeColorStateList
        }
        val secondarySpinner = binding.pluginCenterSortSecondarySpinner
        val secondaryOrderGroup = binding.pluginCenterSortSecondaryOrderGroup
        val secondaryAscendingButton = binding.pluginCenterSortSecondaryOrderAscendingRadio.apply {
            buttonTintList = themeColorStateList
        }
        val secondaryDescendingButton = binding.pluginCenterSortSecondaryOrderDescendingRadio.apply {
            buttonTintList = themeColorStateList
        }
        var currentSecondaryCandidates: List<PluginCenterFragment.Sort> = emptyList()
        val radioIdToSort = linkedMapOf<Int, PluginCenterFragment.Sort>()
        val sortFieldButtons = mutableListOf<RadioButton>()
        var selectedSortFieldButtonId: Int = View.NO_ID
        var suppressSortSelectionCallback = false
        val itemSpacing = (8 * activity.resources.displayMetrics.density).toInt()
        val rowSpacing = (4 * activity.resources.displayMetrics.density).toInt()
        val controlMinHeight = (36 * activity.resources.displayMetrics.density).toInt()
        val controlVerticalPadding = (4 * activity.resources.displayMetrics.density).toInt()
        val radioTextSizeSp = 15f
        var refreshDirectionAndSecondary: (PluginCenterFragment.Sort) -> Unit = {}

        listOf(sortAscendingButton, sortDescendingButton, secondaryAscendingButton, secondaryDescendingButton).forEach { button ->
            button.minHeight = controlMinHeight
            button.setPaddingRelative(0, controlVerticalPadding, 0, controlVerticalPadding)
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, radioTextSizeSp)
        }

        fun refreshSecondaryDirection(sort: PluginCenterFragment.Sort) {
            secondaryOrderGroup.check(
                if (loadPersistedSortAscending(sort)) secondaryAscendingButton.id else secondaryDescendingButton.id,
            )
        }

        secondarySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentSecondaryCandidates.getOrNull(position)?.let(::refreshSecondaryDirection)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        fun secondarySpinnerAdapter(labels: List<String>): ArrayAdapter<String> =
            object : ArrayAdapter<String>(
                activity,
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
            RadioButton(activity).apply {
                id = View.generateViewId()
                text = activity.getString(sort.titleRes)
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
            val row = LinearLayout(activity).apply {
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
                    Space(activity),
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
                val currentLabel = activity.getString(current.titleRes)
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

                val nextFull = shouldUseSingleFullRow(activity.getString(next.titleRes))
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

        refreshDirectionAndSecondary = { sort ->
            val isSortAscending = loadPersistedSortAscending(sort)
            if (isSortAscending) {
                sortOrderGroup.check(sortAscendingButton.id)
            } else {
                sortOrderGroup.check(sortDescendingButton.id)
            }

            currentSecondaryCandidates = PluginCenterFragment.Sort.secondaryCandidatesFor(sort)
            val labels = currentSecondaryCandidates.map { activity.getString(it.titleRes) }
            secondarySpinner.adapter = secondarySpinnerAdapter(labels)
            val persistedSecondary = loadPersistedSecondarySort(sort)
            val secondaryIndex = currentSecondaryCandidates.indexOf(persistedSecondary)
                .takeIf { it >= 0 }
                ?: currentSecondaryCandidates.indexOf(sort.defaultSecondarySort()).coerceAtLeast(0)
            secondarySpinner.setSelection(secondaryIndex, false)
            refreshSecondaryDirection(currentSecondaryCandidates[secondaryIndex])
        }

        val initialRadioId = radioIdToSort.entries
            .firstOrNull { it.value == currentSort }
            ?.key
            ?: radioIdToSort.keys.firstOrNull()
            ?: View.NO_ID
        selectSortFieldButton(initialRadioId, notify = false)
        refreshDirectionAndSecondary(selectedPrimarySort())

        MaterialDialog.Builder(activity)
            .title(R.string.text_sort)
            .options(
                listOf(
                    MaterialDialog.OptionMenuItemSpec(activity.getString(R.string.dialog_button_use_default)) { _ ->
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
                val selectedSecondarySort = currentSecondaryCandidates
                    .getOrNull(secondarySpinner.selectedItemPosition)
                    ?: selectedSort.defaultSecondarySort()
                savePersistedSecondarySort(selectedSort, selectedSecondarySort)
                savePersistedSortAscending(selectedSecondarySort, secondaryAscendingButton.isChecked)
                center.setSort(selectedSort)
            }
            .show()
    }

    private fun loadPersistedSort(): PluginCenterFragment.Sort = when (sourceScope) {
        PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.getSort(activity)
        PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.getSort(activity)
        null -> PluginSortStore.getSort(activity)
    }

    private fun loadPersistedSortAscending(sort: PluginCenterFragment.Sort): Boolean = when (sourceScope) {
        PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.getAscending(activity, sort)
        PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.getAscending(activity, sort)
        null -> PluginSortStore.getAscending(activity, sort)
    }

    private fun loadPersistedSecondarySort(primary: PluginCenterFragment.Sort): PluginCenterFragment.Sort = when (sourceScope) {
        PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.getSecondarySort(activity, primary)
        PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.getSecondarySort(activity, primary)
        null -> PluginSortStore.getSecondarySort(activity, primary)
    }

    private fun savePersistedSort(sort: PluginCenterFragment.Sort) {
        when (sourceScope) {
            PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.setSort(activity, sort)
            PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.setSort(activity, sort)
            null -> PluginSortStore.setSort(activity, sort)
        }
    }

    private fun savePersistedSortAscending(sort: PluginCenterFragment.Sort, ascending: Boolean) {
        when (sourceScope) {
            PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.setAscending(activity, sort, ascending)
            PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.setAscending(activity, sort, ascending)
            null -> PluginSortStore.setAscending(activity, sort, ascending)
        }
    }

    private fun savePersistedSecondarySort(primary: PluginCenterFragment.Sort, secondary: PluginCenterFragment.Sort) {
        when (sourceScope) {
            PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.setSecondarySort(activity, primary, secondary)
            PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.setSecondarySort(activity, primary, secondary)
            null -> PluginSortStore.setSecondarySort(activity, primary, secondary)
        }
    }

    private fun clearPersistedSortMemory() {
        when (sourceScope) {
            PluginIndexSource.OFFICIAL -> PluginSortStoreOfficial.clearAllSortMemory(activity)
            PluginIndexSource.THIRD_PARTY -> PluginSortStoreThirdParty.clearAllSortMemory(activity)
            null -> PluginSortStore.clearAllSortMemory(activity)
        }
    }

    fun showFilterDialog(center: PluginCenterFragment?) {
        if (center == null) return

        val themeColorStateList = ThemeColorHelper.getThemeColorStateList(activity)
        val currentState = PluginFilterStore.getFilterState(activity)
        val binding = DialogPluginCenterFilterBinding.inflate(LayoutInflater.from(activity))
        val contentView = binding.root
        val itemSpacing = (8 * activity.resources.displayMetrics.density).toInt()
        val rowSpacing = (4 * activity.resources.displayMetrics.density).toInt()
        val controlMinHeight = (36 * activity.resources.displayMetrics.density).toInt()
        val controlVerticalPadding = (4 * activity.resources.displayMetrics.density).toInt()
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
                RadioButton(activity).apply {
                    id = View.generateViewId()
                    text = activity.getString(option.titleRes)
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
                val row = LinearLayout(activity).apply {
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
                        Space(activity),
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
                            shouldUseSingleFullRow(activity.getString(current.titleRes))
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

                    val nextFull = shouldUseSingleFullRow(activity.getString(next.titleRes))
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

        MaterialDialog.Builder(activity)
            .title(R.string.text_filter)
            .options(
                listOf(
                    MaterialDialog.OptionMenuItemSpec(activity.getString(R.string.dialog_button_use_default)) { _ ->
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
                    PluginFilterStore.clearAllFilterMemory(activity)
                } else {
                    PluginFilterStore.setFilterState(activity, selectedState)
                }
                center.setFilterState(selectedState)
            }
            .show()
    }

    fun showProcessLogs() {
        val dialog = MaterialDialog.Builder(activity)
            .title(R.string.text_process_log)
            .content(PluginCenterProcessLogger.dump().ifBlank { activity.getString(R.string.text_no_data) })
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_default)
            .show()

        val tv = dialog.contentView?.apply {
            autoLinkMask = Linkify.WEB_URLS
            text = text
        } ?: return

        val job = activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PluginCenterProcessLogger.flow.collect { newLine ->
                    if (!dialog.isShowing) {
                        this@repeatOnLifecycle.cancel()
                        return@collect
                    }
                    if (tv.text.toString() == activity.getString(R.string.text_no_data)) {
                        tv.text = newLine
                    } else {
                        tv.append("\n$newLine")
                    }

                    (tv.parent as? ScrollView)?.post {
                        (tv.parent as ScrollView).fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
        dialog.setOnDismissListener { job.cancel() }
    }

}

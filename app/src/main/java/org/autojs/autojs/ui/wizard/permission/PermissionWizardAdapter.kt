package org.autojs.autojs.ui.wizard.permission

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.ui.wizard.WizardProfile
import org.autojs.autojs.ui.wizard.WizardSelectionPolicy
import org.autojs.autojs.ui.wizard.WizardSelectionPolicy.CheckBoxState
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.PermissionWizardItemBinding

/**
 * Flat, checkable permission rows in drawer order. Granted permissions follow the same rules as
 * installed plugins in the plugin install wizard: a disabled, ticked check box while they belong to
 * the current profile, none otherwise, and they can be hidden altogether. A long press shows the
 * description of a permission.
 *
 * zh-CN: 按抽屉顺序平铺的可勾选权限行. 已授予的权限与插件安装向导中的已安装插件规则一致:
 * 属于当前配置时显示不可用且已勾选的复选框, 否则不显示复选框, 也可整体隐藏. 长按显示权限说明.
 */
class PermissionWizardAdapter(
    private val listener: Listener,
) : RecyclerView.Adapter<PermissionWizardAdapter.ViewHolder>() {

    interface Listener {
        fun onSelectionChanged(selectedIds: Set<String>)
        fun onDescriptionRequested(item: PermissionWizardItem)
    }

    private var items: List<PermissionWizardItem> = emptyList()
    private var rows: List<PermissionWizardItem> = emptyList()
    private val selected = LinkedHashSet<String>()
    private var profile: WizardProfile = WizardProfile.STANDARD
    private var showGranted = true

    val allItems: List<PermissionWizardItem>
        get() = items

    val selectedIds: Set<String>
        get() = LinkedHashSet(selected)

    /** Selected entries in drawer order. */
    fun selectedEntries(): List<PermissionWizardEntry> = items.filter { it.id in selected }.map { it.entry }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newItems: List<PermissionWizardItem>) {
        items = newItems
        rows = buildRows()
        selected.retainAll { id -> items.any { it.id == id && it.isSelectable } }
        notifyDataSetChanged()
        listener.onSelectionChanged(selectedIds)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setShowGranted(show: Boolean) {
        if (showGranted == show) return
        showGranted = show
        rows = buildRows()
        notifyDataSetChanged()
    }

    /** Makes [profile] the reference of the granted rows and ticks [ids]. */
    fun applyProfile(profile: WizardProfile, ids: Collection<String>) {
        this.profile = profile
        setSelection(ids)
    }

    fun setSelection(ids: Collection<String>) {
        selected.clear()
        ids.forEach { id -> if (items.any { it.id == id && it.isSelectable }) selected += id }
        publishSelection()
    }

    fun selectAll() = setSelection(items.filter { it.isSelectable }.map { it.id })

    fun clearSelection() = setSelection(emptyList())

    private fun buildRows() = items.filter { WizardSelectionPolicy.isRowVisible(it.isGranted, showGranted) }

    private fun toggle(item: PermissionWizardItem) {
        if (!item.isSelectable) return
        if (!selected.remove(item.id)) selected += item.id
        publishSelection()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun publishSelection() {
        notifyDataSetChanged()
        listener.onSelectionChanged(selectedIds)
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(PermissionWizardItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(rows[position])

    inner class ViewHolder(private val binding: PermissionWizardItemBinding) : RecyclerView.ViewHolder(binding.root) {

        private var item: PermissionWizardItem? = null

        init {
            binding.root.setOnClickListener { item?.let(::toggle) }
            binding.root.setOnLongClickListener { item?.let(listener::onDescriptionRequested); item != null }
        }

        fun bind(item: PermissionWizardItem) {
            this.item = item
            val context = binding.root.context
            binding.permissionWizardItemIcon.setImageResource(item.entry.iconRes)
            binding.permissionWizardItemTitle.setText(item.entry.titleRes)
            binding.permissionWizardItemSubtitle.text = when {
                item.isGranted -> context.getString(R.string.text_granted)
                item.unavailableReason != null -> item.unavailableReason
                else -> context.getString(R.string.text_not_granted)
            }
            val state = WizardSelectionPolicy.checkBoxState(
                isDone = item.isGranted,
                isSelectable = item.isSelectable,
                isMemberOfProfile = WizardSelectionPolicy.isMember(profile, item.toCandidate()),
            )
            binding.permissionWizardItemCheckbox.apply {
                isVisible = state != CheckBoxState.HIDDEN
                isEnabled = state == CheckBoxState.SELECTABLE
                isChecked = when (state) {
                    CheckBoxState.DONE_IN_PROFILE -> true
                    CheckBoxState.SELECTABLE -> item.id in selected
                    else -> false
                }
            }
            // Disabled rows still take long presses for the description.
            // zh-CN: 不可勾选的行仍可长按查看说明.
            binding.root.isClickable = state == CheckBoxState.SELECTABLE
            binding.root.alpha = if (state == CheckBoxState.UNAVAILABLE) 0.5f else 1f
        }

    }

}

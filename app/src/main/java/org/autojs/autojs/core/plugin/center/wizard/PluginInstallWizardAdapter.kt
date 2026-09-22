package org.autojs.autojs.core.plugin.center.wizard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import de.hdodenhof.circleimageview.CircleImageView
import org.autojs.autojs.core.plugin.center.PluginIconUrlResolver
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.PluginInstallWizardHeaderItemBinding
import org.autojs.autojs6.databinding.PluginInstallWizardPluginItemBinding

/**
 * Category headers plus checkable plugin rows. Icons load asynchronously through
 * Glide so the list renders immediately with placeholders.
 *
 * zh-CN: 分类标题与可勾选的插件行. 图标通过 Glide 异步加载, 列表先以占位图立即渲染.
 */
class PluginInstallWizardAdapter(
    private val listener: Listener,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Listener {
        fun onSelectionChanged(selectedPackageNames: Set<String>)
    }

    private var rows: List<PluginInstallWizardRow> = emptyList()
    private val itemsByPackage = LinkedHashMap<String, PluginInstallWizardItem>()
    private val selected = LinkedHashSet<String>()

    val allItems: List<PluginInstallWizardItem>
        get() = itemsByPackage.values.toList()

    val selectableItems: List<PluginInstallWizardItem>
        get() = itemsByPackage.values.filter { it.isSelectable }

    val selectedPackageNames: Set<String>
        get() = LinkedHashSet(selected)

    fun selectedItems(): List<PluginInstallWizardItem> = itemsByPackage.values.filter { it.packageName in selected }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(items: List<PluginInstallWizardItem>) {
        itemsByPackage.clear()
        items.forEach { itemsByPackage[it.packageName] = it }
        rows = PluginInstallWizardRow.build(items)
        selected.retainAll { itemsByPackage[it]?.isSelectable == true }
        notifyDataSetChanged()
        listener.onSelectionChanged(selectedPackageNames)
    }

    fun setSelection(packageNames: Collection<String>) {
        selected.clear()
        packageNames.forEach { packageName ->
            if (itemsByPackage[packageName]?.isSelectable == true) selected += packageName
        }
        publishSelection()
    }

    fun selectAll() = setSelection(selectableItems.map { it.packageName })

    fun clearSelection() = setSelection(emptyList())

    private fun toggle(item: PluginInstallWizardItem) {
        if (!item.isSelectable) return
        if (!selected.remove(item.packageName)) selected += item.packageName
        publishSelection()
    }

    private fun toggleCategory(header: PluginInstallWizardRow.Header) {
        val members = header.packageNames.mapNotNull { itemsByPackage[it] }.filter { it.isSelectable }
        if (members.isEmpty()) return
        if (members.all { it.packageName in selected }) {
            members.forEach { selected -= it.packageName }
        } else {
            members.forEach { selected += it.packageName }
        }
        publishSelection()
    }

    private fun publishSelection() {
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        listener.onSelectionChanged(selectedPackageNames)
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is PluginInstallWizardRow.Header -> TYPE_HEADER
        is PluginInstallWizardRow.Plugin -> TYPE_PLUGIN
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(PluginInstallWizardHeaderItemBinding.inflate(inflater, parent, false))
            else -> PluginViewHolder(PluginInstallWizardPluginItemBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is PluginInstallWizardRow.Header -> (holder as HeaderViewHolder).bind(row)
            is PluginInstallWizardRow.Plugin -> (holder as PluginViewHolder).bind(row.item)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty() || PAYLOAD_SELECTION !in payloads) {
            onBindViewHolder(holder, position)
            return
        }
        when (val row = rows[position]) {
            is PluginInstallWizardRow.Header -> (holder as HeaderViewHolder).bindSelection(row)
            is PluginInstallWizardRow.Plugin -> (holder as PluginViewHolder).bindSelection(row.item)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? PluginViewHolder)?.clearIcon()
    }

    private inner class HeaderViewHolder(
        private val binding: PluginInstallWizardHeaderItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var header: PluginInstallWizardRow.Header? = null

        init {
            binding.root.setOnClickListener { header?.let(::toggleCategory) }
        }

        fun bind(header: PluginInstallWizardRow.Header) {
            this.header = header
            binding.pluginInstallWizardHeaderTitle.setText(header.category.titleRes)
            bindSelection(header)
        }

        fun bindSelection(header: PluginInstallWizardRow.Header) {
            val members = header.packageNames.mapNotNull { itemsByPackage[it] }
            val selectable = members.filter { it.isSelectable }
            val selectedCount = selectable.count { it.packageName in selected }
            binding.pluginInstallWizardHeaderCount.text = "$selectedCount/${selectable.size}"
            binding.pluginInstallWizardHeaderCheckbox.apply {
                isEnabled = selectable.isNotEmpty()
                isChecked = selectable.isNotEmpty() && selectedCount == selectable.size
            }
            binding.root.isEnabled = selectable.isNotEmpty()
        }

    }

    private inner class PluginViewHolder(
        private val binding: PluginInstallWizardPluginItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val context: Context = binding.root.context
        private var item: PluginInstallWizardItem? = null
        private var hasIconRequest = false

        private val fallbackIcon: Drawable? by lazy { buildFallbackIcon(context) }

        init {
            binding.root.setOnClickListener { item?.let(::toggle) }
        }

        fun bind(item: PluginInstallWizardItem) {
            this.item = item
            binding.pluginInstallWizardItemTitle.text = item.title
            binding.pluginInstallWizardItemSubtitle.text = buildSubtitle(item)
            loadIcon(binding.pluginInstallWizardItemIcon, item)
            bindSelection(item)
        }

        fun bindSelection(item: PluginInstallWizardItem) {
            binding.pluginInstallWizardItemCheckbox.apply {
                isEnabled = item.isSelectable
                isChecked = item.isSelectable && item.packageName in selected
            }
            binding.root.isEnabled = item.isSelectable
            binding.root.alpha = if (item.isSelectable) 1f else 0.5f
        }

        fun clearIcon() {
            if (!hasIconRequest) return
            runCatching { Glide.with(binding.pluginInstallWizardItemIcon).clear(binding.pluginInstallWizardItemIcon) }
            hasIconRequest = false
        }

        private fun buildSubtitle(item: PluginInstallWizardItem): CharSequence {
            val parts = ArrayList<String>(3)
            when {
                item.isInstalled -> parts += context.getString(R.string.text_installed)
                !item.hasInstallableApk -> parts += context.getString(R.string.text_unavailable)
                else -> item.apkSizeBytes?.takeIf { it > 0 }?.let { parts += Formatter.formatFileSize(context, it) }
            }
            if (item.isPossiblyIncompatible) {
                parts += context.getString(R.string.text_plugin_install_wizard_incompatible_hint)
            }
            return parts.joinToString(" | ")
        }

        private fun loadIcon(iconView: CircleImageView, item: PluginInstallWizardItem) {
            val url = item.iconUrl?.takeIf { it.isNotBlank() }
            if (url == null) {
                clearIcon()
                iconView.setImageDrawable(fallbackIcon)
                return
            }
            val preferredUrl = PluginIconUrlResolver.resolveForCurrentMode(context, url, item.nightIconUrl) ?: url
            Glide.with(iconView)
                .load(preferredUrl)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .placeholder(fallbackIcon)
                .error(fallbackIcon)
                .into(iconView)
            hasIconRequest = true
        }

    }

    companion object {

        private const val TYPE_HEADER = 0
        private const val TYPE_PLUGIN = 1

        private val PAYLOAD_SELECTION = Any()

        private fun buildFallbackIcon(context: Context): Drawable? {
            val tint = ColorUtils.adjustColorForContrast(
                context.getColor(R.color.window_background),
                ThemeColorManager.colorPrimary,
                2.3,
            )
            return AppCompatResources.getDrawable(context, R.drawable.ic_plugin_center_default)
                ?.mutate()
                ?.also { drawable ->
                    DrawableCompat.setTint(drawable, tint)
                    DrawableCompat.setTintMode(drawable, PorterDuff.Mode.SRC_IN)
                }
        }

    }

}

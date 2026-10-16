package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityPluginVariantPriorityBinding
import org.autojs.autojs6.databinding.PluginVariantPriorityItemBinding

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 21, 2026.
 */
class PluginVariantPriorityActivity : BaseActivity() {

    private lateinit var binding: ActivityPluginVariantPriorityBinding
    private lateinit var adapter: VariantAdapter

    private lateinit var engine: String
    private var engineId: String? = null

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPluginVariantPriorityBinding.inflate(layoutInflater).also {
            setContentView(it.root)
        }

        engine = intent.getStringExtra(EXTRA_ENGINE).orEmpty()
        engineId = intent.getStringExtra(EXTRA_ENGINE_ID)
        val extraTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        binding.toolbar.subtitle = engine.takeIf { it.isNotBlank() } ?: extraTitle

        setToolbarAsBack(getString(R.string.text_plugin_variant_priority))

        adapter = VariantAdapter()
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        attachDragSupport()

        loadVariants()
    }

    override fun onPause() {
        super.onPause()
        saveOrder()
    }

    private fun loadVariants() {
        lifecycleScope.launch {
            val variants = withContext(Dispatchers.IO) {
                val installed = InstalledPluginRepository().discoverInstalled(this@PluginVariantPriorityActivity)
                val source = installed
                    .filter { plugin ->
                        matchesTargetEngine(
                            pluginEngine = plugin.engine,
                            pluginEngineId = plugin.engineId,
                        )
                    }
                    .map { plugin ->
                        VariantItem(
                            packageName = plugin.packageName,
                            title = plugin.title,
                            variant = plugin.variant,
                            enabled = PluginEnableStore.isEnabled(
                                this@PluginVariantPriorityActivity,
                                plugin.packageName,
                                PluginDefaultEnabledPolicy.isEnabledByDefault(this@PluginVariantPriorityActivity, plugin.engine, plugin.packageName),
                            ),
                        )
                    }
                applyStoredOrder(source)
            }

            adapter.submit(variants.toMutableList())
            binding.recycler.isVisible = variants.isNotEmpty()
        }
    }

    private fun applyStoredOrder(source: List<VariantItem>): List<VariantItem> {
        if (source.isEmpty()) return emptyList()
        val order = loadStoredOrder()
        val orderMap = order.withIndex().associate { it.value to it.index }
        return source.sortedWith(
            compareBy<VariantItem> {
                orderMap[it.packageName] ?: Int.MAX_VALUE
            }.thenByDescending {
                parseVariantWeight(it.variant)
            }.thenBy {
                it.packageName
            }
        )
    }

    private fun saveOrder() {
        if (!::engine.isInitialized) return
        val packageNames = adapter.items.map { it.packageName }
        priorityKeys().forEach { key ->
            PluginVariantPriorityStore.setOrder(key, packageNames)
        }
    }

    private fun loadStoredOrder(): List<String> {
        priorityKeys().forEach { key ->
            val order = PluginVariantPriorityStore.getOrder(key)
            if (order.isNotEmpty()) {
                return order
            }
        }
        return emptyList()
    }

    private fun priorityKeys(): List<String> {
        return PluginVariantPriorityStore.buildPriorityKeys(
            engine = engine,
            engineId = engineId,
        )
    }

    private fun attachDragSupport() {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0,
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from !in adapter.items.indices || to !in adapter.items.indices) return false
                val item = adapter.items.removeAt(from)
                adapter.items.add(to, item)
                adapter.notifyItemMoved(from, to)
                saveOrder()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        })
        helper.attachToRecyclerView(binding.recycler)
    }

    private fun parseVariantWeight(variant: String?): Int {
        return variant.orEmpty().replace(Regex("\\D"), "").toIntOrNull() ?: 0
    }

    private fun matchesTargetEngine(
        pluginEngine: String?,
        pluginEngineId: String?,
    ): Boolean {
        val targetEngineNorm = normalizeComparable(engine)
        val targetFamilyNorm = normalizeEngineFamily(engineId).ifBlank {
            normalizeEngineFamily(engine)
        }

        val pluginEngineNorm = normalizeComparable(pluginEngine)
        val pluginFamilyNorm = normalizeEngineFamily(pluginEngineId).ifBlank {
            normalizeEngineFamily(pluginEngine)
        }

        if (targetEngineNorm.isNotBlank() && pluginEngineNorm == targetEngineNorm) {
            return true
        }
        if (targetFamilyNorm.isNotBlank() && pluginFamilyNorm == targetFamilyNorm) {
            return true
        }
        return false
    }

    private fun normalizeComparable(raw: String?): String {
        return raw.orEmpty()
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "")
    }

    private fun normalizeEngineFamily(raw: String?): String {
        val normalized = raw.orEmpty()
            .trim()
            .lowercase()
        if (normalized.isBlank()) return ""
        return normalized
            .replace(Regex("v\\d+$"), "")
            .replace(Regex("[^a-z0-9]+"), "")
    }

    private data class VariantItem(
        val packageName: String,
        val title: String,
        val variant: String?,
        val enabled: Boolean,
    )

    private class VariantAdapter : RecyclerView.Adapter<VariantViewHolder>() {
        val items = mutableListOf<VariantItem>()

        fun submit(next: MutableList<VariantItem>) {
            items.clear()
            items.addAll(next)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VariantViewHolder {
            val binding = PluginVariantPriorityItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VariantViewHolder(binding)
        }

        override fun onBindViewHolder(holder: VariantViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }

    private class VariantViewHolder(
        private val binding: PluginVariantPriorityItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VariantItem) {
            val context = binding.root.context
            binding.title.text = item.title
            val stateSuffix = if (item.enabled) {
                ""
            } else {
                " | ${context.getString(R.string.text_disabled)}"
            }
            binding.subtitle.text = listOfNotNull(
                item.variant?.takeIf { it.isNotBlank() },
                item.packageName,
            ).joinToString(" | ").plus(stateSuffix)

            val alpha = if (item.enabled) 1f else 0.55f
            binding.root.alpha = alpha
        }
    }

    companion object {
        private const val EXTRA_ENGINE = "extra_engine"
        private const val EXTRA_ENGINE_ID = "extra_engine_id"
        private const val EXTRA_TITLE = "extra_title"

        fun launch(
            context: Context,
            engine: String,
            engineId: String?,
            title: String,
        ) {
            Intent(context, PluginVariantPriorityActivity::class.java)
                .putExtra(EXTRA_ENGINE, engine)
                .putExtra(EXTRA_ENGINE_ID, engineId)
                .putExtra(EXTRA_TITLE, title)
                .startSafely(context)
        }
    }
}

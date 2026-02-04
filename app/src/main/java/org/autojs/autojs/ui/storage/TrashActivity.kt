package org.autojs.autojs.ui.storage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.app.DialogUtils
import org.autojs.autojs.storage.history.HistoryDatabase
import org.autojs.autojs.storage.history.TrashEntities
import org.autojs.autojs.storage.history.TrashRestoreController
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs6.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Trash page.
 * zh-CN: 回收站页面.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 */
class TrashActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView

    private val adapter = TrashAdapter(
        onClick = { item ->
            TrashRestoreController(this).startRestoreFlow(item)
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.text_trash)

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@TrashActivity)
            adapter = this@TrashActivity.adapter
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        setContentView(recyclerView)

        load()
    }

    private fun load() {
        Schedulers.io().scheduleDirect {
            runCatching {
                val dao = HistoryDatabase.getInstance(applicationContext).historyDao()
                val items = dao.listTrashItemsDesc()

                AndroidSchedulers.mainThread().scheduleDirect {
                    adapter.submit(items)

                    if (items.isEmpty()) {
                        DialogUtils.buildAndShowAdaptive {
                            MaterialDialog.Builder(this)
                                .title(R.string.text_prompt)
                                .content(R.string.text_no_data)
                                .positiveText(R.string.dialog_button_dismiss)
                                .positiveColorRes(R.color.dialog_button_default)
                                .cancelable(true)
                                .build()
                        }
                    }
                }
            }.onFailure {
                it.printStackTrace()
                AndroidSchedulers.mainThread().scheduleDirect {
                    DialogUtils.buildAndShowAdaptive {
                        MaterialDialog.Builder(this)
                            .title(R.string.text_prompt)
                            .content(it.message ?: it.toString())
                            .positiveText(R.string.dialog_button_dismiss)
                            .positiveColorRes(R.color.dialog_button_default)
                            .cancelable(true)
                            .build()
                    }
                }
            }
        }
    }

    companion object {
        fun startActivity(context: Context) {
            context.startActivity(
                Intent(context, TrashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

private class TrashAdapter(
    private val onClick: (TrashEntities.TrashItem) -> Unit,
) : RecyclerView.Adapter<TrashViewHolder>() {

    private var items: List<TrashEntities.TrashItem> = emptyList()

    fun submit(newItems: List<TrashEntities.TrashItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashViewHolder {
        return TrashViewHolder.create(parent, onClick)
    }

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}

private class TrashViewHolder(
    itemView: View,
    private val onClick: (TrashEntities.TrashItem) -> Unit,
) : RecyclerView.ViewHolder(itemView) {

    private val titleView: TextView = (itemView as LinearLayout).getChildAt(0) as TextView
    private val subtitleView: TextView = (itemView as LinearLayout).getChildAt(1) as TextView

    private var boundItem: TrashEntities.TrashItem? = null

    fun bind(item: TrashEntities.TrashItem) {
        boundItem = item

        val name = File(item.originalPath).name
        titleView.text = name

        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeText = fmt.format(Date(item.trashedAt))
        subtitleView.text = "${item.originalPath}\n$timeText"

        itemView.setOnClickListener {
            boundItem?.let(onClick)
        }
    }

    companion object {
        fun create(parent: ViewGroup, onClick: (TrashEntities.TrashItem) -> Unit): TrashViewHolder {
            val ctx = parent.context

            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 24, 32, 24)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                gravity = Gravity.CENTER_VERTICAL
            }

            val title = TextView(ctx).apply {
                textSize = 16f
            }

            val subtitle = TextView(ctx).apply {
                textSize = 12f
                alpha = 0.75f
            }

            root.addView(title)
            root.addView(subtitle)

            return TrashViewHolder(root, onClick)
        }
    }
}

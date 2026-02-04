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
import org.autojs.autojs.storage.history.HistoryEntities
import org.autojs.autojs.storage.history.VersionHistoryController
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs6.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Version history page (all files).
 * zh-CN: 版本历史页面 (全部文件).
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 */
class VersionHistoryActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView

    private val adapter = VersionHistoryFileAdapter(
        onClick = { entry ->
            VersionHistoryController(this).showForFilePath(entry.logicalPath)
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.text_version_history)

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@VersionHistoryActivity)
            adapter = this@VersionHistoryActivity.adapter
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
                val files = dao.listAllFiles().sortedByDescending { it.lastSeenAt }

                AndroidSchedulers.mainThread().scheduleDirect {
                    adapter.submit(files)

                    if (files.isEmpty()) {
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
                Intent(context, VersionHistoryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

private class VersionHistoryFileAdapter(
    private val onClick: (HistoryEntities.FileEntry) -> Unit,
) : RecyclerView.Adapter<VersionHistoryFileViewHolder>() {

    private var items: List<HistoryEntities.FileEntry> = emptyList()

    fun submit(newItems: List<HistoryEntities.FileEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VersionHistoryFileViewHolder {
        return VersionHistoryFileViewHolder.create(parent, onClick)
    }

    override fun onBindViewHolder(holder: VersionHistoryFileViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}

private class VersionHistoryFileViewHolder(
    itemView: View,
    private val onClick: (HistoryEntities.FileEntry) -> Unit,
) : RecyclerView.ViewHolder(itemView) {

    private val titleView: TextView = (itemView as LinearLayout).getChildAt(0) as TextView
    private val subtitleView: TextView = (itemView as LinearLayout).getChildAt(1) as TextView

    private var boundItem: HistoryEntities.FileEntry? = null

    fun bind(item: HistoryEntities.FileEntry) {
        boundItem = item

        titleView.text = item.logicalPath

        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeText = fmt.format(Date(item.lastSeenAt))
        subtitleView.text = "${item.latestFingerprint}\n$timeText"

        itemView.setOnClickListener {
            boundItem?.let(onClick)
        }
    }

    companion object {
        fun create(parent: ViewGroup, onClick: (HistoryEntities.FileEntry) -> Unit): VersionHistoryFileViewHolder {
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
                textSize = 15f
            }

            val subtitle = TextView(ctx).apply {
                textSize = 11f
                alpha = 0.75f
            }

            root.addView(title)
            root.addView(subtitle)

            return VersionHistoryFileViewHolder(root, onClick)
        }
    }
}

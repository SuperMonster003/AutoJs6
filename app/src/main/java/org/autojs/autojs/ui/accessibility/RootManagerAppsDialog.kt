package org.autojs.autojs.ui.accessibility

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.widget.doAfterTextChanged
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.core.pref.keys.RunningKeys
import org.autojs.autojs.theme.widget.ThemeColorScrollView
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.OperationDialogItemBinding
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator

/** Cached candidates are displayed immediately; package scans and label loading run off the UI thread. */
class RootManagerAppsDialog private constructor(private val context: Context) {
    private val work = CompositeDisposable()
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val empty = TextView(context).apply {
        setText(R.string.text_root_manager_apps_empty)
        setTextColor(context.getColor(R.color.day_night_alpha_60))
        val padding = (24 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding / 2, padding, padding / 2)
    }
    private var picker: MaterialDialog? = null
    private var shown: List<Candidate>? = null
    private val dialog = MaterialDialog.Builder(context)
        .title(R.string.text_root_manager_apps)
        .customView(ThemeColorScrollView(context).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    setText(R.string.description_root_manager_apps)
                    setTextColor(context.getColor(R.color.day_night_alpha_60))
                    val padding = (24 * resources.displayMetrics.density).toInt()
                    setPadding(padding, 0, padding, padding / 2)
                })
                addView(list)
            })
        }, false)
        .options(listOf(MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_refresh)) { refresh(true) }))
        .neutralText(R.string.text_add_application)
        .neutralColorRes(R.color.dialog_button_attraction)
        .onNeutral { _, _ -> addApplication() }
        .negativeText(R.string.dialog_button_dismiss)
        .negativeColorRes(R.color.dialog_button_default)
        .onNegative { d, _ -> d.dismiss() }
        .autoDismiss(false)
        .dismissListener { work.clear(); picker?.dismiss() }
        .build()

    private fun render(candidates: List<Candidate>) {
        if (shown == candidates) return
        shown = candidates
        list.removeAllViews()
        if (candidates.isEmpty()) list.addView(empty)
        candidates.forEach { app ->
            OperationDialogItemBinding.inflate(LayoutInflater.from(context), list, true).apply {
                title.text = app.label
                subtitle.text = app.packageName
                subtitle.visibility = View.VISIBLE
                icon.setImageResource(R.drawable.ic_android_black_48dp)
                icon.imageTintList = ColorStateList.valueOf(context.getColor(R.color.day_night))
                val launchButton = ImageButton(context).apply {
                    val size = (44 * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size)
                    setImageResource(R.drawable.ic_launch_black_48dp)
                    imageTintList = ColorStateList.valueOf(context.getColor(R.color.day_night))
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    val padding = (11 * resources.displayMetrics.density).toInt()
                    setPadding(padding, padding, padding, padding)
                    val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
                    setBackgroundResource(attrs.getResourceId(0, 0))
                    attrs.recycle()
                    contentDescription = "${context.getString(R.string.text_open)} ${app.label}"
                    setOnClickListener { launch(app.packageName) }
                }
                root.addView(launchButton)
                root.setOnClickListener { launch(app.packageName) }
            }
        }
    }

    private fun launch(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        intent.startSafely(context)
    }

    private fun refresh(force: Boolean = false) {
        work.add(Single.fromCallable { installed(context, force) }
            .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe({ apps ->
                val custom = RunningKeys.ROOT_MANAGER_APPS_CUSTOM.get().toSet()
                val candidates = apps.filter { it.packageName in custom || isCandidate(it.packageName, it.label) }
                RunningKeys.ROOT_MANAGER_APPS_CACHE.set(JSONArray().apply {
                    candidates.forEach { put(JSONObject().put("package", it.packageName).put("label", it.label)) }
                }.toString())
                if (dialog.isShowing) render(candidates)
            }, { e -> if (dialog.isShowing) ViewUtils.showSnack(dialog.view, e.message) }))
    }

    private fun addApplication() {
        work.add(Single.fromCallable { installed(context, false) }
            .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe({ apps ->
                if (!dialog.isShowing) return@subscribe
                showAppPicker(apps)
            }, { e -> ViewUtils.showSnack(dialog.view, e.message) }))
    }

    private fun showAppPicker(apps: List<Candidate>) {
        picker?.dismiss()
        val adapter = object : ArrayAdapter<Candidate>(context, android.R.layout.simple_list_item_2, android.R.id.text1, apps.toMutableList()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = super.getView(position, convertView, parent).apply {
                val app = requireNotNull(getItem(position))
                findViewById<TextView>(android.R.id.text1).apply {
                    text = app.label
                    setTextColor(context.getColor(R.color.day_night))
                    textSize = 16f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                findViewById<TextView>(android.R.id.text2).apply {
                    text = app.packageName
                    setTextColor(context.getColor(R.color.day_night_alpha_60))
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
            }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
            addView(AppCompatEditText(context).apply {
                setHint(R.string.text_search)
                setSingleLine()
                doAfterTextChanged { text ->
                    val query = text?.toString().orEmpty().trim()
                    adapter.clear()
                    adapter.addAll(apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) })
                }
            })
            addView(ListView(context).apply {
                this.adapter = adapter
                setOnItemClickListener { _, _, position, _ ->
                    val app = adapter.getItem(position) ?: return@setOnItemClickListener
                    val custom = RunningKeys.ROOT_MANAGER_APPS_CUSTOM.get().toMutableSet()
                    custom.add(app.packageName)
                    RunningKeys.ROOT_MANAGER_APPS_CUSTOM.set(custom)
                    picker?.dismiss()
                    refresh()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * .35f).toInt()))
        }
        picker = MaterialDialog.Builder(context)
            .title(R.string.text_add_application)
            .customView(content, false)
            .negativeText(R.string.dialog_button_cancel)
            .showAdaptive()
    }

    private data class Candidate(val packageName: String, val label: String)

    companion object {
        private var apps: List<Candidate> = emptyList()
        private var refreshedAt = 0L
        private var language = ""

        private val names = Regex("magisk|kernelsu|kernel.?su|sukisu|apatch|supersu|superuser|super.?user|kingroot|kingo|root|超级用户|超級用戶|超级权限|超級權限", RegexOption.IGNORE_CASE)
        internal fun isCandidate(packageName: String, label: String) = names.containsMatchIn("$packageName $label") || packageName == "com.noshufou.android.su"

        @Synchronized
        private fun installed(context: Context, force: Boolean): List<Candidate> {
            val locale = context.resources.configuration.locales[0]
            val now = SystemClock.elapsedRealtime()
            if (!force && refreshedAt != 0L && now - refreshedAt < 300_000 && language == locale.toLanguageTag()) return apps
            val pm = context.applicationContext.packageManager
            val collator = Collator.getInstance(locale)
            @Suppress("DEPRECATION")
            val fresh = pm.getInstalledApplications(0).map { app ->
                Candidate(app.packageName, runCatching { app.loadLabel(pm).toString() }.getOrDefault(app.packageName))
            }.sortedWith { a, b -> collator.compare(a.label, b.label) }
            apps = fresh
            refreshedAt = now
            language = locale.toLanguageTag()
            return fresh
        }

        fun show(context: Context): MaterialDialog {
            val owner = RootManagerAppsDialog(context)
            val cached = runCatching {
                val json = JSONArray(RunningKeys.ROOT_MANAGER_APPS_CACHE.storedOrNull() ?: "[]")
                (0 until json.length()).map { json.getJSONObject(it).let { Candidate(it.getString("package"), it.getString("label")) } }
            }.getOrDefault(emptyList())
            owner.render(cached)
            owner.dialog.showAdaptive()
            owner.refresh()
            return owner.dialog
        }
    }
}

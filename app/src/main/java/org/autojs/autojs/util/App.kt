package org.autojs.autojs.util

import org.autojs.autojs.AutoJs
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.pref.Language
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on Jun 30, 2022.
 */
@Suppress("SpellCheckingInspection", "unused")
enum class App(private val appNameResId: Int, val packageName: String, alias: String? = null) {

    ACCUWEATHER(R.string.text_app_name_accuweather, "com.accuweather.android"),
    ADM(R.string.text_app_name_adm, "com.dv.adm"),
    ALIPAY(R.string.text_app_name_alipay, "com.eg.android.AlipayGphone"),
    AMAP(R.string.text_app_name_amap, "com.autonavi.minimap"),
    APPOPS(R.string.text_app_name_appops, "rikka.appops"),
    AQUAMAIL(R.string.text_app_name_aquamail, "org.kman.AquaMail"),
    AUTOJS(R.string.text_app_name_autojs, "org.autojs.autojs"),
    AUTOJS6(R.string.text_app_name_autojs6, "org.autojs.autojs6"),
    AUTOJSPRO(R.string.text_app_name_autojspro, "org.autojs.autojspro"),
    BAIDUMAP(R.string.text_app_name_baidumap, "com.baidu.BaiduMap"),
    BILIBILI(R.string.text_app_name_bilibili, "tv.danmaku.bili"),
    BREVENT(R.string.text_app_name_brevent, "mie.piebridge.brevent"),
    CALENDAR(R.string.text_app_name_calendar, "com.google.android.calendar"),
    CHROME(R.string.text_app_name_chrome, "com.android.chrome"),
    COOLAPK(R.string.text_app_name_coolapk, "com.coolapk.market"),
    DIANPING(R.string.text_app_name_dianping, "com.dianping.v1"),
    DIGICAL(R.string.text_app_name_digical, "com.digibites.calendar"),
    DRIVE(R.string.text_app_name_drive, "com.google.android.apps.docs"),
    ES(R.string.text_app_name_es, "com.estrongs.android.pop"),
    EUDIC(R.string.text_app_name_eudic, "com.qianyan.eudic"),
    EXCEL(R.string.text_app_name_excel, "com.microsoft.office.excel"),
    FIREFOX(R.string.text_app_name_firefox, "org.mozilla.firefox"),
    FX(R.string.text_app_name_fx, "nextapp.fx"),
    GEOMETRICWEATHER(R.string.text_app_name_geometric_weather, "wangdaye.com.geometricweather"),
    HTTPCANARY(R.string.text_app_name_httpcanary, "com.guoshi.httpcanary.premium"),
    IDLEFISH(R.string.text_app_name_idlefish, "com.taobao.idlefish"),
    IDMPLUS(R.string.text_app_name_idmplus, "idm.internet.download.manager.plus", "idm+"),
    JD(R.string.text_app_name_jd, "com.jingdong.app.mall"),
    KEEP(R.string.text_app_name_keep, "com.gotokeep.keep"),
    KEEPNOTES(R.string.text_app_name_keep_notes, "com.google.android.keep"),
    MAGISK(R.string.text_app_name_magisk, "com.topjohnwu.magisk"),
    MEITUAN(R.string.text_app_name_meituan, "com.sankuai.meituan"),
    MT(R.string.text_app_name_mt, "bin.mt.plus"),
    MXPRO(R.string.text_app_name_mxpro, "com.mxtech.videoplayer.pro"),
    ONEDRIVE(R.string.text_app_name_onedrive, "com.microsoft.skydrive"),
    PACKETCAPTURE(R.string.text_app_name_packet_capture, "app.greyshirts.sslcapture"),
    PARALLELSPACE(R.string.text_app_name_parallel_space, "com.lbe.parallel.intl"),
    POWERPOINT(R.string.text_app_name_powerpoint, "com.microsoft.office.powerpoint"),
    PULSARPLUS(R.string.text_app_name_pulsar_plus, "com.rhmsoft.pulsar.pro"),
    PUREWEATHER(R.string.text_app_name_pure_weather, "hanjie.app.pureweather"),
    QQ(R.string.text_app_name_qq, "com.tencent.mobileqq"),
    QQMUSIC(R.string.text_app_name_qqmusic, "com.tencent.qqmusic"),
    SDMAID(R.string.text_app_name_sdmaid, "eu.thedarken.sdm"),
    SHIZUKU(R.string.text_app_name_shizuku, "moe.shizuku.privileged.api"),
    STOPAPP(R.string.text_app_name_stopapp, "web1n.stopapp"),
    TAOBAO(R.string.text_app_name_taobao, "com.taobao.taobao"),
    TRAINNOTE(R.string.text_app_name_trainnote, "com.trainnote.rn"),
    TWITTER(R.string.text_app_name_twitter, "com.twitter.android"),
    UNIONPAY(R.string.text_app_name_unionpay, "com.unionpay"),
    VIA(R.string.text_app_name_via, "mark.via.gp"),
    VYSOR(R.string.text_app_name_vysor, "com.koushikdutta.vysor"),
    WECHAT(R.string.text_app_name_wechat, "com.tencent.mm"),
    WORD(R.string.text_app_name_word, "com.microsoft.office.word"),
    ZHIHU(R.string.text_app_name_zhihu, "com.zhihu.android"),
    ;

    val alias = alias ?: name.lowercase()

    private val appUtils by lazy { AutoJs.instance.appUtils }
    private val globalAppContext = GlobalAppContext.get()

    private val packageManager = globalAppContext.packageManager

    private fun getAppName(language: Language) = LocaleUtils.getResources(language.locale).getString(appNameResId)

    fun getAppName(): String {
        AppUtils.getInstalledApplications(globalAppContext).forEach {
            if (it.packageName == packageName) {
                return packageManager.getApplicationLabel(it).toString()
            }
        }
        return str(appNameResId)
    }

    @ScriptInterface
    fun getAppNameZh() = getAppName(Language.ZH_HANS)

    @ScriptInterface
    fun getAppNameEn() = getAppName(Language.EN)

    @ScriptInterface
    fun isInstalled() = appUtils.isInstalled(packageName)

    @ScriptInterface
    fun ensureInstalled() = appUtils.ensureInstalled(this)

    @ScriptInterface
    fun launch() = appUtils.launchPackage(packageName)

    @ScriptInterface
    fun launchSettings() = appUtils.launchSettings(packageName)

    @ScriptInterface
    fun uninstall() = appUtils.uninstall(packageName)

    override fun toString() = "{appName: \"${getAppName()}\", packageName: \"$packageName\", alias: \"$alias\"}"

    companion object {

        @Suppress("unused")
        @ScriptInterface
        @JvmStatic
        fun getAppByAlias(alias: String) = values().find { it.alias == alias }

    }

}
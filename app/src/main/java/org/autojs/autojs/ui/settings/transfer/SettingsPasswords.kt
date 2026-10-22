package org.autojs.autojs.ui.settings.transfer

import org.autojs.autojs.core.pref.archive.SettingsPassword
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The passwords of the running wizards (docs/settings/ROADMAP.md P7.7, D23 / T21), by the token the wizard state keeps
 * ([SettingsWizardState.password]). A password never goes into the state, which the page saves as text with its
 * instance state: a recreated page finds it here by its token, and once the process has ended the token finds nothing
 * (the export asks for the password again, the import shows the credentials locked again). A wizard that ends forgets
 * its password ([SettingsWizard.run]).
 *
 * zh-CN: 运行中向导的口令 (ROADMAP P7.7, D23 / T21), 以向导状态记录的令牌 ([SettingsWizardState.password]) 区分. 口令从不写入状态
 * (页面把状态作为文本保存在实例状态中): 重建后的页面凭令牌在此取回口令; 进程结束后令牌找不到口令 (导出重新询问口令, 导入时凭据重新
 * 显示为已锁定). 向导结束时忘记其口令 ([SettingsWizard.run]).
 */
object SettingsPasswords {

    private val passwords = ConcurrentHashMap<String, SettingsPassword>()

    /** Keeps [password] and returns its token. zh-CN: 保存 [password] 并返回其令牌. */
    @JvmStatic
    fun remember(password: SettingsPassword): String = UUID.randomUUID().toString().also { passwords[it] = password }

    /** The password of [token] in this process, or null. zh-CN: 本进程中令牌 [token] 对应的口令, 没有时为 null. */
    @JvmStatic
    fun find(token: String?): SettingsPassword? = token?.let { passwords[it] }

    @JvmStatic
    fun forget(token: String?) {
        token?.let { passwords.remove(it) }
    }
}

package org.autojs.autojs.ui.main.scripts

import android.content.Context
import android.content.Intent
import io.noties.prism4j.GrammarLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs6.R

class DisplayMediaInfoActivity : BaseDisplayContentActivity() {

    override var internalMenuResource = R.menu.menu_display_media_info_fab

    override var highlightGrammarLocator: GrammarLocator = MediaInfoGrammarLocator()
    override var highlightGrammarName = MediaInfoGrammarLocator.GRAMMAR_NAME
    override var highlightThemeLanguage = "json"

    override suspend fun loadAndDisplayContent() {
        setTextWithLock(withContext(Dispatchers.Default) {
            highlightTextOrSelf(intent.getStringExtra(INTENT_IDENTIFIER_MEDIA_INFO) ?: getString(R.string.text_no_content))
        })
    }

    companion object {

        private const val INTENT_IDENTIFIER_MEDIA_INFO = "MEDIA_INFO"

        @JvmStatic
        fun launch(context: Context, mediaInfo: String) {
            val intent = Intent(context, DisplayMediaInfoActivity::class.java)
            intent.putExtra(INTENT_IDENTIFIER_MEDIA_INFO, mediaInfo)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

    }

}
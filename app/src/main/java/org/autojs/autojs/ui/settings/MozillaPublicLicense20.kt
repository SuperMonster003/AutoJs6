package org.autojs.autojs.ui.settings

import android.content.Context
import de.psdev.licensesdialog.licenses.License
import org.autojs.autojs6.R

class MozillaPublicLicense20 : License() {

    override fun getName() = "Mozilla Public License 2.0"

    override fun getVersion() = "2.0"

    override fun getUrl() = "https://www.mozilla.org/en-US/MPL/2.0/"

    override fun readSummaryTextFromResources(context: Context): String = getContent(context, R.raw.mpl_20_summary)

    override fun readFullTextFromResources(context: Context): String = getContent(context, R.raw.mpl_20_full)

    companion object {
        val instance = MozillaPublicLicense20()
    }

}
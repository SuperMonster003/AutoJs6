package org.autojs.autojs.util

import android.content.Context
import org.autojs.autojs6.R

object UrlUtils {

    @JvmStatic
    private fun ensureUrl(context: Context, url: String?) {
        val regexUrl = "^(https?|ftp|file)://[-a-zA-Z\\d+&@#/%?=~_|!:,.;]*[-a-zA-Z\\d+&@#/%=~_|]"
        require(url != null && url.matches(regexUrl.toRegex())) {
            context.getString(R.string.error_illegal_url_argument)
        }
    }

}
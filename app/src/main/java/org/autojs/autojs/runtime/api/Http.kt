package org.autojs.autojs.runtime.api

import org.autojs.autojs.core.http.MutableOkHttp

/**
 * Created by SuperMonster003 on Jul 7, 2024.
 */
class Http {

    val okhttp = MutableOkHttp()

    fun client() = okhttp.client()

}

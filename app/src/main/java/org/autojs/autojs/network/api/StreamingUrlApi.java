package org.autojs.autojs.network.api;

import io.reactivex.Observable;
import okhttp3.ResponseBody;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Streaming;
import retrofit2.http.Url;

/**
 * Created by Stardust on Sep 20, 2017.
 * Modified by SuperMonster003 as of Feb 28, 2022.
 */
public interface StreamingUrlApi {

    @Streaming
    @GET()
    @Headers("Cache-Control: no-cache")
    Observable<ResponseBody> streamingUrl(@Url String url);

}
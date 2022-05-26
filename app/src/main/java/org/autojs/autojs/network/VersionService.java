package org.autojs.autojs.network;

import androidx.annotation.NonNull;

import com.google.gson.GsonBuilder;
import com.jakewharton.retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

import org.autojs.autojs.network.api.UpdateCheckApi;
import org.autojs.autojs.network.entity.VersionInfo;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by Stardust on 2017/9/20.
 * Modified by SuperMonster003 as of Feb 26, 2022.
 */
public class VersionService {

    private static final String baseUrl = "https://raw.githubusercontent.com/";

    private static Retrofit mRetrofit;

    public static Observable<VersionInfo> checkForUpdates() {
        Retrofit mRetrofit = getRetrofit();
        return mRetrofit.create(UpdateCheckApi.class)
                .checkForUpdates()
                .subscribeOn(Schedulers.io());
    }

    @NonNull
    private static Retrofit getRetrofit() {
        if (mRetrofit == null) {
            mRetrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().setLenient().create()))
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .build();
        }
        return mRetrofit;
    }

}

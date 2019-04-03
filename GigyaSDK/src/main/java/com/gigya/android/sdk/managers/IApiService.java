package com.gigya.android.sdk.managers;

import com.gigya.android.sdk.GigyaCallback;
import com.gigya.android.sdk.GigyaLoginCallback;
import com.gigya.android.sdk.network.GigyaApiResponse;

import java.util.Map;

public interface IApiService<R> {

    void send(String api, Map<String, Object> params, int requestMethod, final GigyaCallback<GigyaApiResponse> gigyaCallback);

    <V> void send(String api, Map<String, Object> params, int requestMethod, Class<V> scheme, final GigyaCallback<V> gigyaCallback);

    void getConfig(final String nextApiTag, final GigyaCallback<R> gigyaCallback);

    void logout();

    void login(Map<String, Object> params, final GigyaLoginCallback<R> loginCallback);

    void getAccount(final GigyaCallback<R> gigyaCallback);
}

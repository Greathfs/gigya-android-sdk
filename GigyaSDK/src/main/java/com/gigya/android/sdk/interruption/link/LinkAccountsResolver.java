package com.gigya.android.sdk.interruption.link;

import com.gigya.android.sdk.AccountManager;
import com.gigya.android.sdk.GigyaCallback;
import com.gigya.android.sdk.GigyaLoginCallback;
import com.gigya.android.sdk.SessionManager;
import com.gigya.android.sdk.api.account.FinalizeRegistrationApi;
import com.gigya.android.sdk.api.account.GetConflictingAccountApi;
import com.gigya.android.sdk.api.account.LoginApi;
import com.gigya.android.sdk.interruption.GigyaResolver;
import com.gigya.android.sdk.log.GigyaLogger;
import com.gigya.android.sdk.model.GigyaAccount;
import com.gigya.android.sdk.network.GigyaError;
import com.gigya.android.sdk.network.GigyaResponse;
import com.gigya.android.sdk.network.adapter.NetworkAdapter;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

public class LinkAccountsResolver<T extends GigyaAccount> extends GigyaResolver {

    private static final String LOG_TAG = "LinkAccountsResolver";

    final private SoftReference<GigyaLoginCallback<T>> loginCallback;

    private String regToken;
    private GigyaResponse originalResponse;
    private GetConflictingAccountApi.ConflictingAccount conflictingAccounts;

    public GetConflictingAccountApi.ConflictingAccount getConflictingAccounts() {
        return conflictingAccounts;
    }

    public LinkAccountsResolver(NetworkAdapter networkAdapter, SessionManager sessionManager,
                                AccountManager accountManager, GigyaLoginCallback<T> loginCallback) {
        super(networkAdapter, sessionManager, accountManager);
        this.loginCallback = new SoftReference<>(loginCallback);
    }

    public void setOriginalData(String regToken, GigyaResponse response) {
        this.regToken = regToken;
        this.originalResponse = response;
    }

    public void init() {
        new GetConflictingAccountApi(networkAdapter, sessionManager).call(this.regToken, new GigyaCallback<GigyaResponse>() {
            @Override
            public void onSuccess(GigyaResponse obj) {
                conflictingAccounts = obj.getField("conflictingAccount",
                        GetConflictingAccountApi.ConflictingAccount.class);
                if (loginCallback.get() != null) {
                    if (conflictingAccounts == null) {
                        loginCallback.get().onError(GigyaError.generalError());
                    } else {
                        loginCallback.get().onConflictingAccounts(originalResponse, LinkAccountsResolver.this);
                    }
                }
            }

            @Override
            public void onError(GigyaError error) {
                if (loginCallback.get() != null) {
                    loginCallback.get().onError(error);
                }
            }
        });
    }

    public void resolveForSiteProvider(String loginId, String password) {
        Map<String, Object> params = new HashMap<>();
        params.put("loginID", loginId);
        params.put("password", password);
        params.put("loginMode", "link");
        params.put("regToken", this.regToken);
        new LoginApi<T>(networkAdapter, sessionManager, accountManager)
                .call(params, loginCallback.get());
    }

    public void finalizeRegistration(String regToken) {
        if (loginCallback.get() != null) {
            GigyaLogger.debug(LOG_TAG, "Sending finalize registration");
            new FinalizeRegistrationApi<T>(networkAdapter, sessionManager, accountManager)
                    .call(regToken, loginCallback.get(), new Runnable() {
                        @Override
                        public void run() {
                            // Nullify all relevant fields of the resolver.
                            nullify();
                        }
                    });
        } else {
            GigyaLogger.error(LOG_TAG, "Login callback reference is null -> Not sending finalize registration");
        }
    }

    private void nullify() {
        regToken = null;
        originalResponse = null;
        loginCallback.clear();
    }

    @Override
    public void cancel() {
        if (loginCallback.get() != null) {
            loginCallback.get().onOperationCancelled();
        }
        nullify();
    }
}

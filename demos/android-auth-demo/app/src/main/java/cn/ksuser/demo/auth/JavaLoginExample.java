package cn.ksuser.demo.auth;

import android.app.Activity;
import cn.ksuser.auth.sdk.KsuserAuthCallback;
import cn.ksuser.auth.sdk.KsuserAuthClient;
import cn.ksuser.auth.sdk.KsuserAuthConfiguration;
import cn.ksuser.auth.sdk.KsuserAuthException;
import cn.ksuser.auth.sdk.KsuserAuthorizationMode;
import cn.ksuser.auth.sdk.KsuserAuthorizationResult;
import java.util.Arrays;

/** Compiled Java integration example. Retain the returned client and forward callbacks to it. */
public final class JavaLoginExample {
    private JavaLoginExample() {}

    public static KsuserAuthClient authorize(Activity activity, String clientId, String callbackUri,
                                            KsuserAuthCallback<KsuserAuthorizationResult> callback)
            throws KsuserAuthException {
        KsuserAuthClient client = new KsuserAuthClient(activity.getApplicationContext(),
                new KsuserAuthConfiguration(clientId, KsuserAuthorizationMode.OAUTH, callbackUri));
        client.authorize(activity, Arrays.asList("profile"), callback);
        return client;
    }
}

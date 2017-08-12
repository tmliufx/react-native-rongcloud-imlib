package com.casstime.ec.imlib;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

import org.json.JSONObject;

/**
 * Created by fengxuanliu on 2017/7/18.
 */
public class IntentDataModule extends ReactContextBaseJavaModule {

    private Intent intent;

    public IntentDataModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "IntentDataModule";
    }

    @ReactMethod
    public void dataToJS(final Promise promise) {
        try{
            intent = getCurrentActivity().getIntent();
            Log.i("rong intent msg", "接收到推送消息");
            if (intent != null && intent.getData() != null && intent.getData().getScheme().equals("rong")) {
                String dataStr = intent.getDataString();
                String path = intent.getData().getPath();
                String conversationType = path.substring(path.lastIndexOf("/") + 1).toUpperCase();
                String messageUId = intent.getData().getQueryParameter("messageUId");
                String content = intent.getData().getQueryParameter("content");
                String targetId = intent.getData().getQueryParameter("targetId");
                String extra = intent.getData().getQueryParameter("extra");
                //统计通知栏点击事件.
                if (TextUtils.isEmpty(dataStr)) {
                    promise.resolve(null);
                } else {
                    WritableMap map = Arguments.createMap();
                    map.putString("conversationType", conversationType);
                    map.putString("messageUId", messageUId);
                    map.putString("content", content);
                    map.putString("targetId", targetId);
                    map.putString("extra", extra);
                    promise.resolve(map);
                }
            }
        } catch (Exception e) {
            promise.reject("message", e.getMessage());
        }
    }
}

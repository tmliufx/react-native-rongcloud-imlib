package com.casstime.ec.imlib;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import org.json.JSONObject;

/**
 * Created by fengxuanliu on 2017/7/18.
 */
public class IntentDataModule extends ReactContextBaseJavaModule {
    public IntentDataModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "IntentDataModule";
    }

    @ReactMethod
    public void dataToJS(Callback successBack, Callback errorBack){
        try{
            Intent intent = getCurrentActivity().getIntent();
            Log.i("rong intent msg","接收到推送消息");
            if (intent != null && intent.getData() != null && intent.getData().getScheme().equals("rong")) {
                String dataStr = intent.getDataString();
                String conversationType = intent.getData().getPath();
                String targetId = intent.getData().getQueryParameter("targetId");
                String extra = intent.getData().getQueryParameter("extra");
                //统计通知栏点击事件.
                if (TextUtils.isEmpty(dataStr)) {
                    dataStr = "没有数据";
                }else {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("conversationType",conversationType);
                    jsonObject.put("targetId",targetId);
                    jsonObject.put("extra",extra);
                    dataStr = jsonObject.toString();
                }
                successBack.invoke(dataStr);
            }
        }catch (Exception e){
            errorBack.invoke(e.getMessage());
        }
    }
}

/**
 * Created By Javen Leung on June/30/2017
 * 融云 Android SDK 版本 2.8.12
 */
package com.casstime.ec.imlib;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import io.rong.imlib.RongIMClient;
import io.rong.imlib.model.Conversation;
import io.rong.imlib.model.Message;
import io.rong.imlib.model.MessageContent;
import io.rong.imlib.model.UserOnlineStatusInfo;
import io.rong.imlib.RongCommonDefine.GetMessageDirection;
import io.rong.imlib.IRongCallback;
import io.rong.message.TextMessage;
import io.rong.push.RongPushClient;


public class RongIMLibModule extends ReactContextBaseJavaModule implements RongIMClient.OnReceiveMessageListener, RongIMClient.ConnectionStatusListener, LifecycleEventListener {

  static boolean isIMClientInited = false;

  boolean hostActive = true;

  private static final String SUCCESS = "SUCCESS";
  private static final String IS_CONNECTED = "IS_CONNECTED";
  private static final String CLIENT_NONEXISTENT = "CLIENT_NONEXISTENT";
  private static final String TOKEN_INCORRECT = "TOKEN_INCORRECT";
  private static final String RONG_CONNECTION_STATUS_CHANGED = "RONG_CONNECTION_STATUS_CHANGED";
  private static final String RONG_MESSAGE_RECEIVED = "RONG_MESSAGE_RECEIVED";

  public RongIMLibModule(ReactApplicationContext reactContext) {
    super(reactContext);

    /*
    try {
      ApplicationInfo applicationInfo = reactContext.getPackageManager().getApplicationInfo(reactContext.getPackageName(), PackageManager.GET_META_DATA);
      String miAppId = applicationInfo.metaData.getString("MI_PUSH_APPID");
      String miAppKey = applicationInfo.metaData.getString("MI_PUSH_APPKEY");

      RongPushClient.registerHWPush(getReactApplicationContext());
      RongPushClient.registerMiPush(getReactApplicationContext(),
              miAppId,
              miAppKey);
//            RongPushClient.checkManifest(getApplicationContext());
    } catch (Exception e) {
      Log.e("config",e.getMessage());
    }
    */

    if (!isIMClientInited) {
      isIMClientInited = true;
      RongIMClient.init(reactContext.getApplicationContext());
    }

    reactContext.addLifecycleEventListener(this);
  }

  @Override
  public String getName() {
    return "RongIMLib";
  }

  @Override
  public void initialize() {
    RongIMClient.setOnReceiveMessageListener(this);
    RongIMClient.setConnectionStatusListener(this);
  }

  @Override
  public void onCatalystInstanceDestroy() {
    RongIMClient.setOnReceiveMessageListener(null);
    RongIMClient.getInstance().disconnect();
  }

  private void emitEvent(String type, WritableMap arg){
    ReactApplicationContext context = this.getReactApplicationContext();
    context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(type, arg);

  }

  @Override
  public boolean onReceived(Message message, int i) {
    emitEvent(RONG_MESSAGE_RECEIVED, Utils.convertMessage(message));

    String targetId = message.getTargetId();

    if (!hostActive || targetId.equals("system") ) {
      Context context = getReactApplicationContext();
      NotificationManager mNotificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
      NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context);
      MessageContent content = message.getContent();
      String title = content.getUserInfo() != null ? content.getUserInfo().getName() : "开思客服";

      String contentString = Utils.convertMessageContentToString(content);
      mBuilder.setSmallIcon(context.getApplicationInfo().icon)
              .setContentTitle(title)
              .setContentText(contentString)
              .setTicker(contentString)
              .setAutoCancel(true)
              .setDefaults(Notification.DEFAULT_ALL);

      Intent intent = new Intent();
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.addCategory(Intent.CATEGORY_DEFAULT);
      intent.setAction(Intent.ACTION_VIEW);
      intent.setClass(getReactApplicationContext(),getCurrentActivity().getClass());
      Uri.Builder builder = Uri.parse("rong://" + context.getPackageName()).buildUpon();

      TextMessage textContent = (TextMessage)message.getContent();
      builder.appendPath("conversation")
        .appendPath(message.getConversationType().getName())
        .appendQueryParameter("messageUId", message.getUId())
        .appendQueryParameter("content", textContent.getContent())
        .appendQueryParameter("targetId", message.getTargetId())
        .appendQueryParameter("extra", textContent.getExtra());

      intent.setData(builder.build());
      mBuilder.setContentIntent(PendingIntent.getActivity(getReactApplicationContext().getCurrentActivity(), 0, intent, 0));

      Notification notification = mBuilder.build();
      mNotificationManager.notify(1000, notification);
    }
    return true;
  }

  RongIMClient imClient = null;

  /**
   * 连接服务器，在整个应用程序全局，只需要调用一次，需在 init(Context) 之后调用。
   * 如果调用此接口遇到连接失败，SDK 会自动启动重连机制进行最多10次重连，分别是1, 2, 4, 8, 16, 32, 64, 128, 256, 512秒后。 在这之后如果仍没有连接成功，还会在当检测到设备网络状态变化时再次进行重连。
   * @param token
   * @param promise
   */
  @ReactMethod
  public void connect(String token, final Promise promise) {
    if (imClient != null) {
      promise.reject(IS_CONNECTED, "已经有连接上融云服务器的实例");
      return;
    }

    imClient = RongIMClient.connect(token, new RongIMClient.ConnectCallback() {
      /**
       * Token 错误，在线上环境下主要是因为Token已经过期，您需要向App Server重新请求一个新的Token
       */
      @Override
      public void onTokenIncorrect() {
        promise.reject(TOKEN_INCORRECT, "token不正确");
      }

      /**
       * 连接融云成功
       * @param userid
       */
      @Override
      public void onSuccess(String userid) {
        promise.resolve(userid);
      }

      /**
       * 连接融云失败
       * @param errorCode 错误码，可到官网查看错误码对应的注释
       */
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 断开与融云服务器的连接。当调用此接口断开连接后，仍然可以接收 Push 消息。
   * @param promise
   */
  @ReactMethod
  public void disconnect(final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.disconnect();
    promise.resolve(SUCCESS);
  }

  /**
   * 重新连接融云服务器，当连接中断时使用。比connect方法多一层保护，不会导致重复连接。
   * 融云 SDK 有自身的重连机制，能在最大程度上保证连接的可靠性，所以通常不需要用户来调用此接口。
   * @param promise
   */
  @ReactMethod
  public void reconnect(final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.reconnect(new RongIMClient.ConnectCallback() {
      /**
       * Token 错误，在线上环境下主要是因为Token已经过期，您需要向App Server重新请求一个新的Token
       */
      @Override
      public void onTokenIncorrect() {
        promise.reject(TOKEN_INCORRECT, "token不正确");
      }

      /**
       * 连接融云成功
       * @param userid
       */
      @Override
      public void onSuccess(String userid) {
        promise.resolve(userid);
      }

      /**
       * 连接融云失败
       * @param errorCode 错误码，可到官网查看错误码对应的注释
       */
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });

  }

  /**
   * 断开与融云服务器的连接，并且不再接收 Push 消息。
   * @param promise
   */
  @ReactMethod
  public void logout(final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.logout();
    imClient = null;
    promise.resolve(SUCCESS);
  }

  /**
   * 获取当前用户的本地会话列表。即显示所有本地数据库中收发过消息,并且未被删除的会话。
   * 当更换设备或者清除缓存后，能拉取到暂存在融云服务器中该账号当天收发过消息的会话。
   * @param promise
   */
  @ReactMethod
  public void getConversationList(final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getConversationList(new RongIMClient.ResultCallback<List<Conversation>>() {

      @Override
      public void onSuccess(List<Conversation> conversations) {
        promise.resolve(Utils.convertConversationList(conversations));
      }

      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 根据会话类型，获取当前用户的本地会话列表。即显示所有本地数据库中收发过消息,并且未被删除的会话。
   * 当更换设备或者清除缓存后，能拉取到暂存在融云服务器中该账号当天收发过消息的会话。
   * @param promise
   * @param type String类型，如"PRIVATE"
   */
  @ReactMethod
  public void getConversationListByType(String type, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getConversationList(new RongIMClient.ResultCallback<List<Conversation>>() {

      @Override
      public void onSuccess(List<Conversation> conversations) {
        promise.resolve(Utils.convertConversationList(conversations));
      }

      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    }, Conversation.ConversationType.valueOf(type));
  }

  /**
   * 获取单个会话
   * 根据不同会话类型的目标 Id，回调方式获取某一会话信息。
   * @param type 会话类型
   * @param targetId 会话对方的id
   * @param promise
   */
  @ReactMethod
  public void getConversation(String type, String targetId, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getConversation(Conversation.ConversationType.valueOf(type), targetId, new RongIMClient.ResultCallback<Conversation>() {
      @Override
      public void onSuccess(Conversation conversation) {
        if (null != conversation)
          promise.resolve(Utils.convertConversation(conversation));
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 更新会话信息
   * @param type 会话类型，如"PRIVATE"
   * @param targetId
   * @param title
   * @param portrait
   * @param promise
   */
  @ReactMethod
  public void updateConversationInfo(String type, String targetId, String title, String portrait, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.updateConversationInfo(Conversation.ConversationType.valueOf(type), targetId, title, portrait,
            // todo:返回类型
            new RongIMClient.ResultCallback<Object>() {

              // todo:返回类型
              @Override
              public void onSuccess(Object info) {
                // todo
                promise.resolve(SUCCESS);
              }
              @Override
              public void onError(RongIMClient.ErrorCode e) {
                promise.reject("" + e.getValue(), e.getMessage());
              }
            });
  }

  /**
   * 从会话列表中移除某一会话，但是不删除会话内的消息。
   * 如果此会话中有新的消息，该会话将重新在会话列表中显示，并显示最近的历史消息。
   * @param type
   * @param targetId
   * @param promise
   */
  @ReactMethod
  public void removeConversation(String type, String targetId, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.removeConversation(Conversation.ConversationType.valueOf(type), targetId, new RongIMClient.ResultCallback<Boolean>() {

      @Override
      public void onSuccess(Boolean result) {
        promise.resolve(result);
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 设置某一会话为置顶或者取消置顶，回调方式获取设置是否成功。
   * @param type
   * @param targetId
   * @param isTop
   * @param promise
   */
  @ReactMethod
  public void setConversationToTop(String type, String targetId, Boolean isTop, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.setConversationToTop(Conversation.ConversationType.valueOf(type), targetId, isTop, new RongIMClient.ResultCallback<Boolean>() {

      @Override
      public void onSuccess(Boolean result) {
        promise.resolve(result);
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 通过回调方式，获取所有未读消息数。
   * @param promise
   */
  @ReactMethod
  public void getTotalUnreadCount(final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getTotalUnreadCount(new RongIMClient.ResultCallback<Integer>() {

      @Override
      public void onSuccess(Integer count) {
        promise.resolve(count);
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 根据会话类型的目标 Id，回调方式获取来自某用户（某会话）的未读消息数。
   * @param type
   * @param targetId
   * @param promise
   */
  @ReactMethod
  public void getUnreadCount(String type, String targetId, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getUnreadCount(Conversation.ConversationType.valueOf(type), targetId, new RongIMClient.ResultCallback<Integer>() {

      @Override
      public void onSuccess(Integer count) {
        promise.resolve(count);
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 回调方式获取某会话类型的未读消息数。
   * @param type
   * @param promise
   */
  @ReactMethod
  public void getUnreadCountByConvType(String type, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getUnreadCount(new RongIMClient.ResultCallback<Integer>() {

      @Override
      public void onSuccess(Integer count) {
        promise.resolve(count);
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    }, Conversation.ConversationType.valueOf(type));
  }

  /**
   * 根据会话类型数组，回调方式获取某会话类型的未读消息数。
   * @param types
   * @param promise
   */
  @ReactMethod
  public void getUnreadCountByConvTypes(ReadableArray types, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    Conversation.ConversationType[] convTypes = new Conversation.ConversationType[types.size()];
    for (int i = 0; i < types.size(); i++) {
      convTypes[i] = Conversation.ConversationType.valueOf(types.getString(i));
    }
    imClient.getUnreadCount(convTypes, new RongIMClient.ResultCallback<Integer>() {

      @Override
      public void onSuccess(Integer count) {
        promise.resolve(count);
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 获取指定类型，targetId 的最新消息记录。通常在进入会话后，调用此接口拉取该会话的最近聊天记录。
   * @param type
   * @param targetId 根据不同的 conversationType，可能是用户 Id、讨论组 Id、群组 Id 或聊天室 Id。
   * @param count
   * @param promise
   */
  @ReactMethod
  public void getLatestMessages(String type, String targetId, int count, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getLatestMessages(Conversation.ConversationType.valueOf(type), targetId, count,
            new RongIMClient.ResultCallback<List<Message>>() {
              @Override
              public void onSuccess(List<Message> messages) {
                // 历史消息记录，按照时间顺序从新到旧排列
                promise.resolve(Utils.convertMessageList(messages));
              }
              @Override
              public void onError(RongIMClient.ErrorCode e) {
                promise.reject("" + e.getValue(), e.getMessage());
              }
            });
  }

  /**
   * 获取指定类型，targetId 的N条历史消息记录。通过此接口可以根据情况分段加载历史消息，节省网络资源，提高用户体验。
   * @param type
   * @param targetId
   * @param oldestMessageId 最后一条消息的 Id，获取此消息之前的 count 条消息，没有消息第一次调用应设置为:-1
   * @param count
   * @param promise
   */
  @ReactMethod
  public void getHistoryMessages(String type, String targetId, int oldestMessageId, int count, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getHistoryMessages(Conversation.ConversationType.valueOf(type), targetId, oldestMessageId, count,
            new RongIMClient.ResultCallback<List<Message>>() {
              @Override
              public void onSuccess(List<Message> messages) {
                promise.resolve(Utils.convertMessageList(messages));
              }
              @Override
              public void onError(RongIMClient.ErrorCode e) {
                promise.reject("" + e.getValue(), e.getMessage());
              }
            });
  }

  /**
   * 获取本地数据库中保存，特定类型，targetId 的N条历史消息记录。通过此接口可以根据情况分段加载历史消息，节省网络资源，提高用户体验。
   * @param type
   * @param targetId
   * @param objectName 消息类型标识。如RC:TxtMsg，RC:ImgMsg，RC:VcMsg等。
   * @param oldestMessageId
   * @param count
   * @param promise
   */
  @ReactMethod
  public void getHistoryMessagesByMsgType(String type, String targetId, String objectName, int oldestMessageId, int count, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getHistoryMessages(Conversation.ConversationType.valueOf(type), targetId, objectName, oldestMessageId, count,
            new RongIMClient.ResultCallback<List<Message>>() {
              @Override
              public void onSuccess(List<Message> messages) {
                promise.resolve(Utils.convertMessageList(messages));
              }
              @Override
              public void onError(RongIMClient.ErrorCode e) {
                promise.reject("" + e.getValue(), e.getMessage());
              }
            });
  }

  /**
   * 根据会话类型的目标 Id，回调方式获取某消息类型的某条消息之前或之后的N条历史消息记录。
   * @param type  会话类型。不支持传入 ConversationType.CHATROOM。
   * @param targetId  目标 Id。根据不同的 conversationType，可能是用户 Id、讨论组 Id、群组 Id。
   * @param objectName  消息类型标识。如RC:TxtMsg，RC:ImgMsg，RC:VcMsg等。
   * @param baseMessageId  最后一条消息的 Id，获取此消息之前的 count 条消息,没有消息第一次调用应设置为:-1。
   * @param count  要获取的消息数量
   * @param direction  要获取的消息相对于 oldestMessageId 的方向, 以message id 作为获取的起始点，时间早于该 id 则为front，晚于则为behind。
   * @param promise
   */
  @ReactMethod
  public void getHistoryMessagesByDirection(String type, String targetId, String objectName, int baseMessageId, int count, String direction, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getHistoryMessages(Conversation.ConversationType.valueOf(type), targetId, objectName, baseMessageId, count, GetMessageDirection.valueOf(direction.toUpperCase()),
            new RongIMClient.ResultCallback<List<Message>>() {
              @Override
              public void onSuccess(List<Message> messages) {
                promise.resolve(Utils.convertMessageList(messages));
              }
              @Override
              public void onError(RongIMClient.ErrorCode e) {
                promise.reject("" + e.getValue(), e.getMessage());
              }
            });
  }

  /**
   * 在会话中搜索指定消息的前 before 数量和 after 数量的消息。 返回的消息列表中会包含指定的消息。消息列表时间顺序从新到旧。
   * @param type
   * @param targetId
   * @param sentTime
   * @param before
   * @param after
   * @param promise
   */
  @ReactMethod
  public void getHistoryMessagesByRange(String type, String targetId, long sentTime, int before, int after, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getHistoryMessages(Conversation.ConversationType.valueOf(type), targetId, sentTime, before, after,
            new RongIMClient.ResultCallback<java.util.List<Message>>() {
              @Override
              public void onSuccess(List<Message> messages) {
                promise.resolve(Utils.convertMessageList(messages));
              }
              @Override
              public void onError(RongIMClient.ErrorCode e) {
                promise.reject("" + e.getValue(), e.getMessage());
              }
            });
  }

  /**
   * 获取融云服务器中暂存，特定类型，targetId 的N条（一次不超过40条）历史消息记录。通过此接口可以根据情况分段加载历史消息，节省网络资源，提高用户体验。
   * 该接口是从融云服务器中拉取。通常用于更换新设备后，拉取历史消息。
   * @param type
   * @param targetId
   * @param dateTime  从该时间点开始获取消息。即：消息中的 sentTime；第一次可传 0，获取最新 count 条
   * @param count  要获取的消息数量，最多 40 条
   * @param promise
   */
  @ReactMethod
  public void getRemoteHistoryMessages(String type, String targetId, long dateTime, int count, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getRemoteHistoryMessages(Conversation.ConversationType.valueOf(type), targetId, dateTime, count,
            new RongIMClient.ResultCallback<List<Message>>() {
              @Override
              public void onSuccess(List<Message> messages) {
                // 按照时间顺序从新到旧排列
                promise.resolve(Utils.convertMessageList(messages));
              }
              @Override
              public void onError(RongIMClient.ErrorCode e) {
                promise.reject("" + e.getValue(), e.getMessage());
              }
            });
  }

  /**
   * 根据会话,搜索本地历史消息。 搜索结果可分页返回
   * 如果需要自定义消息也能被搜索到,需要在自定义消息中实现 MessageContent.getSearchableWord() 方法
   * @param type
   * @param targetId
   * @param keyword
   * @param count
   * @param beginTime
   * @param promise
   */
  @ReactMethod
  public void searchMessages(String type, String targetId, String keyword, int count, long beginTime, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.searchMessages(Conversation.ConversationType.valueOf(type), targetId, keyword, count, beginTime,
            new RongIMClient.ResultCallback<List<Message>>() {
              @Override
              public void onSuccess(List<Message> messages) {
                promise.resolve(Utils.convertMessageList(messages));
              }
              @Override
              public void onError(RongIMClient.ErrorCode e) {
                promise.reject("" + e.getValue(), e.getMessage());
              }
            });
  }

  /**
   * 根据 messageId，删除指定的一条或者一组消息。
   * @param messageIds
   * @param promise
   */
  @ReactMethod
  public void deleteMessagesByIds(ReadableArray messageIds, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    int size = messageIds.size();
    int[] ids = new int[size];
    for(int i = 0; i < size; i++) {
      ids[i] = messageIds.getInt(i);
    }
    imClient.deleteMessages(ids, new RongIMClient.ResultCallback<Boolean>() {
      @Override
      public void onSuccess(Boolean success) {
        promise.resolve(SUCCESS);
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   *
   * 清除指定会话的消息
   * 此接口会删除指定会话中数据库的所有消息，同时，会清理数据库空间。 如果数据库特别大，超过几百 M，调用该接口会有少许耗时。
   * @param type
   * @param targetId
   * @param promise
   */
  @ReactMethod
  public void deleteMessagesByConversation(String type, String targetId, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.deleteMessages(Conversation.ConversationType.valueOf(type), targetId, new RongIMClient.ResultCallback<Boolean>() {
      @Override
      public void onSuccess(Boolean success) {
        promise.resolve(SUCCESS);
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 清空指定类型，targetId 的某一会话所有聊天消息记录。
   * @param type
   * @param targetId
   * @param promise
   */
  @ReactMethod
  public void clearMessages(String type, String targetId, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.clearMessages(Conversation.ConversationType.valueOf(type), targetId, new RongIMClient.ResultCallback<Boolean>() {
      @Override
      public void onSuccess(Boolean success) {
        promise.resolve(SUCCESS);
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 清除指定类型，targetId 的某一会话消息未读状态。
   * @param type
   * @param targetId
   * @param promise
   */
  @ReactMethod
  public void clearMessagesUnreadStatus(String type, String targetId, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.clearMessagesUnreadStatus(Conversation.ConversationType.valueOf(type), targetId, new RongIMClient.ResultCallback<Boolean>() {
      @Override
      public void onSuccess(Boolean success) {
        promise.resolve(SUCCESS);
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 根据 messageId 设置本地消息的附加信息，用于扩展消息的使用场景。
   * 只能用于本地使用，无法同步给远程用户。
   * @param messageId
   * @param value  消息附加信息，最大 1024 字节。
   * @param promise
   */
  @ReactMethod
  public void setMessageExtra(int messageId, String value, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.setMessageExtra(messageId, value, new RongIMClient.ResultCallback<Boolean>() {
      @Override
      public void onSuccess(Boolean success) {
        promise.resolve(SUCCESS);
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 根据 messageId 设置接收到的消息状态。用于UI标记消息为已读，已下载等状态。
   * @param messageId
   * @param status 整型，todo: 确认是否0或1，和分别的意义
   * @param promise
   */
  @ReactMethod
  public void setMessageReceivedStatus(int messageId, int status, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.setMessageReceivedStatus(messageId, new Message.ReceivedStatus(status), new RongIMClient.ResultCallback<Boolean>() {
      @Override
      public void onSuccess(Boolean success) {
        promise.resolve(SUCCESS);
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 根据 messageId 设置消息的发送状态。用于UI标记消息为正在发送，对方已接收等状态。
   * @param messageId
   * @param status CANCELED DESTORYED FIALED READ RECEIVED SENDING SENT
   * @param promise
   */
  @ReactMethod
  public void setMessageSentStatus(int messageId, String status, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.setMessageSentStatus(messageId, Message.SentStatus.valueOf(status), new RongIMClient.ResultCallback<Boolean>() {
      @Override
      public void onSuccess(Boolean success) {
        promise.resolve(SUCCESS);
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 根据消息类型，targetId 获取某一会话的文字消息草稿。用于获取用户输入但未发送的暂存消息。
   * @param type
   * @param targetId
   * @param promise
   */
  @ReactMethod
  public void getTextMessageDraft(String type, String targetId, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getTextMessageDraft(Conversation.ConversationType.valueOf(type), targetId, new RongIMClient.ResultCallback<String>() {
      @Override
      public void onSuccess(String draft) {
        promise.resolve(draft);
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 根据消息类型，targetId 保存某一会话的文字消息草稿。用于暂存用户输入但未发送的消息。
   * @param type
   * @param targetId
   * @param content  草稿的文字内容
   * @param promise
   */
  @ReactMethod
  public void saveTextMessageDraft(String type, String targetId, String content, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.saveTextMessageDraft(Conversation.ConversationType.valueOf(type), targetId, content, new RongIMClient.ResultCallback<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        promise.resolve(SUCCESS);
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 根据消息类型，targetId 清除某一会话的文字消息草稿。
   * @param type
   * @param targetId
   * @param promise
   */
  @ReactMethod
  public void clearTextMessageDraft(String type, String targetId, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.clearTextMessageDraft(Conversation.ConversationType.valueOf(type), targetId, new RongIMClient.ResultCallback<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        promise.resolve(SUCCESS);
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 根据 message id 获取消息体。
   * @param messageId
   * @param promise
   */
  @ReactMethod
  public void getMessage(int messageId, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getMessage(messageId, new RongIMClient.ResultCallback<Message>() {
      @Override
      public void onSuccess(Message message) {
        promise.resolve(Utils.convertMessage(message));
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 向本地会话中插入一条消息。这条消息只是插入本地会话，不会实际发送给服务器和对方。该消息不一定插入本地数据库，是否入库由消息的属性决定。
   * @param type
   * @param targetId
   * @param senderId
   * @param content
   * @param sentTime
   * @param promise
   */
  @ReactMethod
  public void insertMessageWithSentTime(String type, String targetId, String senderId, ReadableMap content, long sentTime, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    // todo:确定是否这样转换
    MessageContent msgContent = Utils.convertToMessageContent(content);
    imClient.insertMessage(Conversation.ConversationType.valueOf(type), targetId, senderId, msgContent, sentTime,
            new RongIMClient.ResultCallback<Message>() {
              @Override
              public void onSuccess(Message message) {
                promise.resolve(Utils.convertMessage(message));
              }
              @Override
              public void onError(RongIMClient.ErrorCode e) {
                promise.reject("" + e.getValue(), e.getMessage());
              }
            });
  }

  /**
   * 向本地会话中插入一条消息。这条消息只是插入本地会话，不会实际发送给服务器和对方。该消息不一定插入本地数据库，是否入库由消息的属性决定。
   * @param type
   * @param targetId
   * @param senderId
   * @param content
   * @param sentTime
   * @param promise
   */
  @ReactMethod
  public void insertMessage(String type, String targetId, String senderId, ReadableMap content, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    // todo:确定是否这样转换
    MessageContent msgContent = Utils.convertToMessageContent(content);
    imClient.insertMessage(Conversation.ConversationType.valueOf(type), targetId, senderId, msgContent,
            new RongIMClient.ResultCallback<Message>() {
              @Override
              public void onSuccess(Message message) {
                promise.resolve(Utils.convertMessage(message));
              }
              @Override
              public void onError(RongIMClient.ErrorCode e) {
                promise.reject("" + e.getValue(), e.getMessage());
              }
            });
  }

  /**
   * 根据会话类型，发送消息
   * @param type
   * @param targetId
   * @param content
   * @param pushContent
   * @param pushData
   * @param promise
   */
  @ReactMethod
  public void sendMessageByConvType(String type, String targetId, ReadableMap content, String pushContent, String pushData, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    // todo:确定是否这样转换
    MessageContent msgContent = Utils.convertToMessageContent(content);
    imClient.sendMessage(Conversation.ConversationType.valueOf(type), targetId, msgContent, pushContent, pushData,
            new IRongCallback.ISendMessageCallback() {
              // 消息已存储数据库
              @Override
              public void onAttached(Message message) {

              }
              // 消息发送成功
              @Override
              public void onSuccess(Message message) {
                promise.resolve(Utils.convertMessage(message));
              }
              // 消息发送失败
              @Override
              public void onError(Message message, RongIMClient.ErrorCode e) {
                promise.reject("" + e.getValue(), e.getMessage());
              }
            });
  }

  /**
   * 发送消息
   * @param message
   * @param pushContent
   * @param pushData
   * @param promise
   */
  @ReactMethod
  public void sendMessage(ReadableMap message, String pushContent, String pushData, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    // todo: message转换成融云Message类型
    Message msg = new Message();
    imClient.sendMessage(msg, pushContent, pushData, new IRongCallback.ISendMessageCallback() {
      // 消息已存储数据库
      @Override
      public void onAttached(Message message) {

      }
      // 消息发送成功
      @Override
      public void onSuccess(Message message) {
        promise.resolve(Utils.convertMessage(message));
      }
      // 消息发送失败
      @Override
      public void onError(Message message, RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 根据会话类型，发送图片消息。
   * @param type
   * @param targetId
   * @param content
   * @param pushContent 当下发 push 消息时，在通知栏里会显示这个字段, 如果发送 sdk 中默认的消息类型，例如 RC:TxtMsg, RC:VcMsg, RC:ImgMsg，则不需要填写，默认已经指定。
   * @param pushData 附加信息。如果设置该字段，用户在收到 push 消息时，能通过 PushNotificationMessage.getPushData() 方法获取。
   * @param promise
   */
  @ReactMethod
  public void sendImageMessageByConvType(String type, String targetId, ReadableMap content, String pushContent, String pushData, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    // todo:确定是否这样转换
    MessageContent msgContent = Utils.convertToMessageContent(content);
    imClient.sendImageMessage(Conversation.ConversationType.valueOf(type), targetId, msgContent, pushContent, pushData,
            new RongIMClient.SendImageMessageCallback() {
              // 消息已存储数据库
              @Override
              public void onAttached(Message message) {

              }
              // 消息发送进度
              @Override
              public void onProgress(Message message, int progress) {

              }
              // 消息发送成功
              @Override
              public void onSuccess(Message message) {
                promise.resolve(Utils.convertMessage(message));
              }
              // 消息发送失败
              @Override
              public void onError(Message message, RongIMClient.ErrorCode e) {
                promise.reject("" + e.getValue(), e.getMessage());
              }
            });
  }

  /**
   * 发送图片消息
   * @param message
   * @param pushContent
   * @param pushData
   * @param promise
   */
  @ReactMethod
  public void sendImageMessage(ReadableMap message, String pushContent, String pushData, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    // todo: message转换成融云Message类型
    Message msg = new Message();
    imClient.sendImageMessage(msg, pushContent, pushData, new RongIMClient.SendImageMessageCallback() {
      // 消息已存储数据库
      @Override
      public void onAttached(Message message) {

      }
      // 消息发送进度
      @Override
      public void onProgress(Message message, int progress) {

      }
      // 消息发送成功
      @Override
      public void onSuccess(Message message) {
        promise.resolve(Utils.convertMessage(message));
      }
      // 消息发送失败
      @Override
      public void onError(Message message, RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 发送图片消息，可以使用该方法将图片上传到自己的服务器发送，同时更新图片状态。
   * @param message
   * @param pushContent
   * @param pushData
   * @param promise
   */
  @ReactMethod
  public void sendImageMessageWithListener(ReadableMap message, String pushContent, String pushData, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    // todo: message转换成融云Message类型
    Message msg = new Message();
    imClient.sendImageMessage(msg, pushContent, pushData, new RongIMClient.SendImageMessageWithUploadListenerCallback() {
      // 消息已存储数据库
      @Override
      public void onAttached(Message message, RongIMClient.UploadImageStatusListener watcher) {

      }
      // 消息发送进度
      @Override
      public void onProgress(Message message, int progress) {

      }
      // 消息发送成功
      @Override
      public void onSuccess(Message message) {
        promise.resolve(Utils.convertMessage(message));
      }
      // 消息发送失败
      @Override
      public void onError(Message message, RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 获取会话消息提醒状态。
   * @param type
   * @param targetId
   * @param promise
   */
  @ReactMethod
  public void getConversationNotificationStatus(String type, String targetId, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getConversationNotificationStatus(Conversation.ConversationType.valueOf(type), targetId,
            new RongIMClient.ResultCallback<Conversation.ConversationNotificationStatus>() {
              @Override
              public void onSuccess(Conversation.ConversationNotificationStatus status) {
                // todo: 转换notificationStatus
                promise.resolve(SUCCESS);
              }
              @Override
              public void onError(RongIMClient.ErrorCode e) {
                promise.reject("" + e.getValue(), e.getMessage());
              }
            });
  }

  /**
   * 设置会话消息提醒状态
   * @param type
   * @param targetId
   * @param notificationStatus 是否屏蔽, DO_NOT_DISTURB 或 NOTIFY
   * @param promise
   */
  @ReactMethod
  public void setConversationNotificationStatus(String type, String targetId, String notificationStatus, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.setConversationNotificationStatus(Conversation.ConversationType.valueOf(type), targetId,
            Conversation.ConversationNotificationStatus.valueOf(notificationStatus.toUpperCase()),
            new RongIMClient.ResultCallback<Conversation.ConversationNotificationStatus>() {
              @Override
              public void onSuccess(Conversation.ConversationNotificationStatus status) {
                // todo: 转换notificationStatus
                promise.resolve(SUCCESS);
              }
              @Override
              public void onError(RongIMClient.ErrorCode e) {
                promise.reject("" + e.getValue(), e.getMessage());
              }
            });
  }

  /**
   * 获取当前连接用户的信息, 等同connect之后返回的userId
   */
  @ReactMethod
  public void getCurrentUserId(final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    promise.resolve(imClient.getCurrentUserId());
  }

  /**
   * 获取本地时间与服务器时间的差值。 消息发送成功后，sdk 会与服务器同步时间，消息所在数据库中存储的时间就是服务器时间
   * System.currentTimeMillis() - getDeltaTime()可以获取服务器当前时间。
   */
  @ReactMethod
  public void getDeltaTime(final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    promise.resolve(imClient.getDeltaTime());
  }

  /**
   * 根据时间戳清除指定类型，目标Id 的某一会话消息未读状态
   * @param type
   * @param targetId
   * @param timestamp
   * @param promise
   */
  @ReactMethod
  public void clearMessagesUnreadStatusByTime(String type, String targetId, long timestamp, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.clearMessagesUnreadStatus(Conversation.ConversationType.valueOf(type), targetId, timestamp,
            new RongIMClient.OperationCallback() {
              @Override
              public void onSuccess() {
                promise.resolve(SUCCESS);
              }
              @Override
              public void onError(RongIMClient.ErrorCode e) {
                promise.reject("" + e.getValue(), e.getMessage());
              }
            });
  }

  /**
   * 根据 messageId 获取消息发送时间
   * @param messageId
   * @param promise
   */
  @ReactMethod
  public void getSendTimeByMessageId(int messageId, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    long timestamp = imClient.getSendTimeByMessageId(messageId);
    promise.resolve(timestamp);
  }

  /**
   * 获取用户在线状态
   * @param userId
   * @param promise
   */
  @ReactMethod
  public void getUserOnlineStatus(String userId, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getUserOnlineStatus(userId, new IRongCallback.IGetUserOnlineStatusCallback() {
      @Override
      public void onSuccess(ArrayList<UserOnlineStatusInfo> userOnlineStatusInfoList) {
        // todo: parse userOnlineStatusInfoList
        promise.resolve(SUCCESS);
      }
      @Override
      public void onError(int errCode) {
        // todo
        promise.reject("" + errCode);
      }
    });
  }

  /**
   * 设置当前用户在线状态
   * @param status
   * @param promise
   */
  @ReactMethod
  public void setUserOnlineStatus(int status, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.setUserOnlineStatus(status, new IRongCallback.ISetUserOnlineStatusCallback() {
      @Override
      public void onSuccess() {
        // todo
        promise.resolve(SUCCESS);
      }
      @Override
      public void onError(int errCode) {
        // todo
        promise.reject("" + errCode);
      }
    });
  }

  /**
   * 获取登录者身份认证信息。 第三方厂商通过使用此接口获取 token，然后与厂商的注册信息一起去融云服务器做认证。
   * @param promise
   */
  @ReactMethod
  public void getVendorToken(final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getVendorToken(new RongIMClient.ResultCallback<String>() {
      @Override
      public void onSuccess(String token) {
        promise.resolve(token);
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }

  /**
   * 根据 uid 获取 message 对象
   * @param uid 发送 message 成功后，服务器会给每个 message 分配一个唯一 uid
   * @param promise
   */
  @ReactMethod
  public void getMessageByUid(String uid, final Promise promise) {
    if (imClient == null) {
      promise.reject(CLIENT_NONEXISTENT, "im客户端实例不存在");
      return;
    }
    imClient.getMessageByUid(uid, new RongIMClient.ResultCallback<Message>() {
      @Override
      public void onSuccess(Message message) {
        promise.resolve(Utils.convertMessage(message));
      }
      @Override
      public void onError(RongIMClient.ErrorCode e) {
        promise.reject("" + e.getValue(), e.getMessage());
      }
    });
  }


  @Override
  public void onChanged(ConnectionStatus connectionStatus) {
    WritableMap map = Arguments.createMap();
    map.putInt("code", connectionStatus.getValue());
    map.putString("message", connectionStatus.getMessage());
    emitEvent(RONG_CONNECTION_STATUS_CHANGED, map);
  }

  @Override
  public void onHostResume() {
    this.hostActive = true;
  }

  @Override
  public void onHostPause() {
    this.hostActive = false;
  }

  @Override
  public void onHostDestroy() {

  }
}

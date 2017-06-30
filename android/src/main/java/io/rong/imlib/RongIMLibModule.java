package io.rong.imlib;

import android.widget.Toast;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import io.rong.imlib.RongIMClient;
import io.rong.imlib.model.Conversation;
import io.rong.imlib.model.Message;
import io.rong.imlib.model.MessageContent;
import io.rong.imlib.RongCommonDefine.GetMessageDirection;

public class RongIMLibModule extends ReactContextBaseJavaModule
  implements RongIMClient.OnReceiveMessageListener, RongIMClient.ConnectionStatusListener, LifecycleEventListener {

  static boolean isIMClientInited = false;

  boolean hostActive = true;

  RongIMClient imClient = null;

  private static final String SUCCESS = "SUCCESS";
  private static final String FAIL = "FAIL";
  private static final String IS_CONNECTED = "IS_CONNECTED";
  private static final String CLIENT_NONEXISTENT = "CLIENT_NONEXISTENT";
  private static final String TOKEN_INCORRECT = "TOKEN_INCORRECT";
  private static final String RONG_CONNECTION_STATUS_CHANGED = "RONG_CONNECTION_STATUS_CHANGED";
  private static final String RONG_MESSAGE_RECEIVED = "RONG_MESSAGE_RECEIVED";

  public RongIMLibModule(ReactApplicationContext reactContext) {
    super(reactContext);
    if (!isIMClientInited) {
      isIMClientInited = true;
      RongIMClient.init(reactContext.getApplicationContext());
    }

    // reactContext.addLifecycleEventListener(this);
  }

  @Override
  public String getName() {
    return "RongIMLib";
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

  /**
   * 事件触发，java向js传递数据
   * @param type
   * @param data
   */
  protected void emitEvent(String type, Object data) {
    ReactApplicationContext context = this.getReactApplicationContext();
    context.getJSModule(RCTNativeAppEventEmitter.class)
            .emit(type, data);
  }

  /**
   * 连接状态变更
   * @param status
   */
  @Override
  public void onChanged(ConnectionStatus status) {
    WritableMap map = Arguments.createMap();
    map.putInt("code", status.getValue());
    map.putString("message", status.getMessage());
    this.emitEvent(RONG_CONNECTION_STATUS_CHANGED, map);
  }

  /**
   * 收到消息
   * @param message
   * @param left
   * @return
   */
  @Override
  public boolean onReceived(Message message, int left) {
    this.emitEvent(RONG_MESSAGE_RECEIVED, Utils.convertMessage(message));
    return true;
  }

  /**
   * 返回 RongIMClient IM 客户端核心类的实例
   * @return
   */
  @ReactMethod
  public RongIMClient getClient() {
    return imClient;
  }

  /**
   * 返回 IMLib 接口类实例
   * @return
   */
  @ReactMethod
  public RongIMClient getInstance() {
    return imClient.getInstance();
  }

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
    imClient.getLatestMessages(Conversation.ConversationType.valueOf(type), targetId, count, new RongIMClient.ResultCallback<List<Message>>() {
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
   * 获取本地数据库中保存，特定类型，targetId 的N条历史消息记录。通过此接口可以根据情况分段加载历史消息，节省网络资源，提高用户体验。
   * @param type
   * @param targetId
   * @param objectName
   * @param oldestMessageId
   * @param count
   * @param promise
   */
  @ReactMethod
  public void getHistoryMessages(String type, String targetId, String objectName, int oldestMessageId, int count, final Promise promise) {
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
  public void getHistoryMessagesByTypeAndDirection(String type, String targetId, String objectName, int baseMessageId, int count, String direction, final Promise promise) {
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

  @ReactMethod
  public void show(String message, int duration) {
    Toast.makeText(getReactApplicationContext(), message, duration).show();
  }
}
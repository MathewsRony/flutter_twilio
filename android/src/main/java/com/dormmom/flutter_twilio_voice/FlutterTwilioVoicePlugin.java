package com.dormmom.flutter_twilio_voice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.dormmom.flutter_twilio_voice.Utils.PreferencesUtils;
import com.dormmom.flutter_twilio_voice.Utils.TwilioConstants;
import com.dormmom.flutter_twilio_voice.Utils.TwilioRegistrationListener;
import com.dormmom.flutter_twilio_voice.Utils.TwilioUtils;
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;

import java.util.Map;
import java.util.Set;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

public class FlutterTwilioVoicePlugin implements
        FlutterPlugin,
        MethodChannel.MethodCallHandler,
        ActivityAware,
        PluginRegistry.NewIntentListener {

    private static final String TAG = "TwilioVoicePlugin";

    private Context context;
    private MethodChannel responseChannel;
    private CustomBroadcastReceiver broadcastReceiver;
    private boolean broadcastReceiverRegistered = false;

    public FlutterTwilioVoicePlugin() {
    }

    private void setupMethodChannel(BinaryMessenger messenger, Context context) {
        this.context = context;
        MethodChannel channel = new MethodChannel(messenger, "flutter_twilio_voice");
        channel.setMethodCallHandler(this);
        this.responseChannel = new MethodChannel(messenger, "flutter_twilio_voice_response");
    }

    private void registerReceiver() {
        if (!this.broadcastReceiverRegistered) {
            this.broadcastReceiverRegistered = true;

            Log.i(TAG, "Registered broadcast");
            this.broadcastReceiver = new CustomBroadcastReceiver(this);
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(TwilioConstants.ACTION_ACCEPT);
            LocalBroadcastManager.getInstance(this.context).registerReceiver(this.broadcastReceiver, intentFilter);
        }
    }

    private void unregisterReceiver() {
        if (this.broadcastReceiverRegistered) {
            this.broadcastReceiverRegistered = false;

            Log.i(TAG, "Unregistered broadcast");
            LocalBroadcastManager.getInstance(this.context).unregisterReceiver(this.broadcastReceiver);
        }
    }

    @Override
    public void onAttachedToActivity(ActivityPluginBinding activityPluginBinding) {
        Log.d(TAG, "onAttachedToActivity");
        activityPluginBinding.addOnNewIntentListener(this);
        this.registerReceiver();
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        Log.d(TAG, "onDetachedFromActivityForConfigChanges");
        this.unregisterReceiver();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(ActivityPluginBinding activityPluginBinding) {
        Log.d(TAG, "onReattachedToActivityForConfigChanges");
        activityPluginBinding.addOnNewIntentListener(this);
        this.registerReceiver();
    }

    @Override
    public void onDetachedFromActivity() {
        Log.d(TAG, "onDetachedFromActivity");
        this.unregisterReceiver();
    }

    @Override
    public boolean onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent");
        this.handleIncomingCallIntent(intent);
        return false;
    }

    private void handleIncomingCallIntent(Intent intent) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.i(TAG, "onReceive. Action: " + action);

            switch (action) {
                case TwilioConstants.ACTION_ACCEPT: {
                    CallInvite callInvite = intent.getParcelableExtra(TwilioConstants.EXTRA_INCOMING_CALL_INVITE);
                    answer(callInvite);
                }
                break;
            }
        }
    }

    @Override
    public void onMethodCall(MethodCall call, @NonNull Result result) {
        Log.i(TAG, "onMethodCall. Method: " + call.method);
        TwilioUtils t = TwilioUtils.getInstance(this.context);

        switch (call.method) {
            case "register": {
                String identity = call.argument("identity");
                String accessToken = call.argument("accessToken");
                String fcmToken = call.argument("fcmToken");

                try {
                    t.register(identity, accessToken, fcmToken, new TwilioRegistrationListener() {
                        @Override
                        public void onRegistered() {
                            result.success("");
                        }

                        @Override
                        public void onError() {
                            result.error("", "", "");
                        }
                    });
                } catch (Exception exception) {
                    exception.printStackTrace();
                    result.error("", "", "");
                }
            }
            break;

            case "unregister": {
                try {
                    t.unregister();
                } catch (Exception exception) {
                    exception.printStackTrace();
                }

                result.success("");
            }
            break;

            case "makeCall": {
                try {
                    String to = call.argument("to");
                    Map<String, Object> data = call.argument("data");
                    t.makeCall(to, data, getCallListener());
                    responseChannel.invokeMethod("callConnecting", t.getCallDetails());
                    result.success(t.getCallDetails());
                } catch (Exception exception) {
                    exception.printStackTrace();
                    result.error("", "", "");
                }
            }
            break;

            case "toggleMute": {
                try {
                    boolean isMuted = t.toggleMute();
                    responseChannel.invokeMethod(t.getCallStatus(), t.getCallDetails());
                    result.success(isMuted);
                } catch (Exception exception) {
                    exception.printStackTrace();
                    result.error("", "", "");
                }
            }
            break;

            case "isMuted": {
                try {
                    boolean isMuted = t.isMuted();
                    result.success(isMuted);
                } catch (Exception exception) {
                    exception.printStackTrace();
                    result.error("", "", "");
                }
            }
            break;

            case "toggleSpeaker": {
                try {
                    boolean isSpeaker = t.toggleSpeaker();
                    responseChannel.invokeMethod(t.getCallStatus(), t.getCallDetails());
                    result.success(isSpeaker);
                } catch (Exception exception) {
                    exception.printStackTrace();
                    result.error("", "", "");
                }
            }
            break;

            case "isSpeaker": {
                try {
                    boolean isSpeaker = t.isSpeaker();
                    result.success(isSpeaker);
                } catch (Exception exception) {
                    exception.printStackTrace();
                    result.error("", "", "");
                }
            }
            break;

            case "hangUp": {
                try {
                    t.disconnect();
                    result.success("");
                } catch (Exception exception) {
                    exception.printStackTrace();
                    result.error("", "", "");
                }
            }
            break;

            case "activeCall": {
                if (t.getActiveCall() == null) {
                    result.success("");
                } else {
                    result.success(t.getCallDetails());
                }
            }
            break;

            case "setContactData": {
                Map<String, Object> data = call.argument("contacts");
                String defaultDisplayName = call.argument("defaultDisplayName");
                PreferencesUtils.getInstance(this.context).setContacts(data, defaultDisplayName);
                result.success("");
            }
            break;
        }
    }

    @Override
    public void onAttachedToEngine(FlutterPlugin.FlutterPluginBinding binding) {
        setupMethodChannel(binding.getBinaryMessenger(), binding.getApplicationContext());
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPlugin.FlutterPluginBinding binding) {
    }

    private void answer(CallInvite callInvite) {
        TwilioUtils t = TwilioUtils.getInstance(this.context);
        t.acceptInvite(callInvite, getCallListener());
        responseChannel.invokeMethod("callConnecting", t.getCallDetails());
    }

    Call.Listener getCallListener() {
        TwilioUtils t = TwilioUtils.getInstance(this.context);

        return new Call.Listener() {
            @Override
            public void onConnectFailure(@NonNull Call call, @NonNull CallException error) {
                Log.d(TAG, "onConnectFailure. Error: " + error.getMessage());
                responseChannel.invokeMethod("callDisconnected", "");
            }

            @Override
            public void onRinging(@NonNull Call call) {
                Log.d(TAG, "onRinging");
                responseChannel.invokeMethod("callRinging", t.getCallDetails());
            }

            @Override
            public void onConnected(@NonNull Call call) {
                Log.d(TAG, "onConnected");
                responseChannel.invokeMethod("callConnected", t.getCallDetails());
            }

            @Override
            public void onReconnecting(@NonNull Call call, @NonNull CallException e) {
                Log.d(TAG, "onReconnecting. Error: " + e.getMessage());
                responseChannel.invokeMethod("callReconnecting", t.getCallDetails());
            }

            @Override
            public void onReconnected(@NonNull Call call) {
                Log.d(TAG, "onReconnected");
                responseChannel.invokeMethod("callReconnected", t.getCallDetails());
            }

            @Override
            public void onDisconnected(@NonNull Call call, CallException e) {
                if (e != null) {
                    Log.d(TAG, "onDisconnected. Error: " + e.getMessage());
                } else {
                    Log.d(TAG, "onDisconnected");
                }
                Log.d(TAG, call.getState().toString());
                responseChannel.invokeMethod("callDisconnected", null);
            }

            @Override
            public void onCallQualityWarningsChanged(
                    @NonNull Call call,
                    @NonNull Set<Call.CallQualityWarning> currentWarnings,
                    @NonNull Set<Call.CallQualityWarning> previousWarnings
            ) {
                Log.d(TAG, "onCallQualityWarningsChanged");
            }
        };
    }

    private static class CustomBroadcastReceiver extends BroadcastReceiver {

        private final FlutterTwilioVoicePlugin plugin;

        private CustomBroadcastReceiver(FlutterTwilioVoicePlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            plugin.handleIncomingCallIntent(intent);
        }
    }


}



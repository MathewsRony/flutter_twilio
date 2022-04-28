package federico.amura.flutter_twilio;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.Lifecycle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;

import java.util.List;
import java.util.Map;

import federico.amura.flutter_twilio.Utils.AppForegroundStateUtils;
import federico.amura.flutter_twilio.Utils.NotificationUtils;
import federico.amura.flutter_twilio.Utils.PreferencesUtils;
import federico.amura.flutter_twilio.Utils.SoundUtils;
import federico.amura.flutter_twilio.Utils.TwilioConstants;
import federico.amura.flutter_twilio.Utils.TwilioUtils;
import androidx.lifecycle.ProcessLifecycleOwner;

public class    IncomingCallNotificationService extends Service {

    private static final String TAG = IncomingCallNotificationService.class.getSimpleName();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        Log.i(TAG, "onStartCommand " + action);
        if (action != null) {
            switch (action) {
                case TwilioConstants.ACTION_INCOMING_CALL: {
                    Log.e("*Twilio onStartCommand ", "TwilioConstants.ACTION_INCOMING_CALL case");
                    CallInvite callInvite = intent.getParcelableExtra(TwilioConstants.EXTRA_INCOMING_CALL_INVITE);
                    Log.e(TAG, "ACTION_INCOMING_CALL call Invite "+ callInvite.getCallSid());
                    handleIncomingCall(callInvite);
                }
                break;

                case TwilioConstants.ACTION_ACCEPT: {
                    Log.e("*Twilio onStartCommand ", "TwilioConstants.ACTION_ACCEPT case");

                    CallInvite callInvite = intent.getParcelableExtra(TwilioConstants.EXTRA_INCOMING_CALL_INVITE);
                    Log.e(TAG, "ACTION_ACCEPT call Invite "+ callInvite.getCallSid());
                    accept(callInvite);
                }
                break;
                case TwilioConstants.ACTION_REJECT: {
                    Log.e("*Twilio onStartCommand ", "TwilioConstants.ACTION_REJECT case");
                    CallInvite callInvite = intent.getParcelableExtra(TwilioConstants.EXTRA_INCOMING_CALL_INVITE);
                    reject(callInvite);
                }
                break;
                case TwilioConstants.ACTION_CANCEL_CALL: {
                    Log.e("*Twilio onStartCommand ", "TwilioConstants.ACTION_CANCEL_CALL case");

//                    CancelledCallInvite cancelledCallInvite = intent.getParcelableExtra(TwilioConstants.EXTRA_CANCELLED_CALL_INVITE);
                    handleCancelledCall(intent);
//
//                    CallInvite callInvite = intent.getParcelableExtra(TwilioConstants.EXTRA_INCOMING_CALL_INVITE);
//                    this.startServiceMissedCall(callInvite,cancelledCallInvite);
                }
                break;

                case TwilioConstants.ACTION_STOP_SERVICE: {
                    Log.e("*Twilio onStartCommand ", "TwilioConstants.ACTION_STOP_SERVICE case");

                    stopServiceIncomingCall();
                }
                break;

                case TwilioConstants.ACTION_RETURN_CALL:
                    returnCall(intent);
                    break;
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void handleIncomingCall(CallInvite callInvite) {
        if (callInvite == null) {
            Log.e(TAG, "Incoming call. No call invite");
            return;
        }

        Log.e(TAG, "Incoming call. App visible: " + isAppVisible() + ". Locked: " + isLocked());
        this.startServiceIncomingCall(callInvite);
    }

    private void accept(CallInvite callInvite) {
        Log.e(TAG, "****************************accept start****************");
        Log.e(TAG, "Accept call invite. App visible: " + isAppVisible() + ". Locked: " + isLocked());
        this.stopServiceIncomingCall();

        Log.e(TAG, "****************************accept**************** else part");
        Log.e(TAG, "Answering from custom UI");
        Log.e(TAG, "Answering from call Invite "+ callInvite.getCallSid());
        this.openBackgroundCallActivityForAcceptCall(callInvite);
        Log.e(TAG, "****************************accept end****************");
    }

    private void reject(CallInvite callInvite) {
        Log.e(TAG, "Reject call invite. App visible: " + isAppVisible() + ". Locked: " + isLocked());
        this.stopServiceIncomingCall();

        // Reject call
        try {
            TwilioUtils.getInstance(this).rejectInvite(callInvite);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void handleCancelledCall(Intent intent) {
        SoundUtils.getInstance(this).stopRinging();
        CancelledCallInvite cancelledCallInvite = intent.getParcelableExtra(TwilioConstants.EXTRA_CANCELLED_CALL_INVITE);
        Log.i(TAG, "Call canceled. App visible: " + isAppVisible() + ". Locked: " + isLocked());
        buildMissedCallNotification(cancelledCallInvite.getFrom(), cancelledCallInvite.getTo(),cancelledCallInvite);

        stopForeground(true);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
//        this.stopServiceIncomingCall();

//        if (cancelledCallInvite == null) return;
//        if (cancelledCallInvite.getFrom() == null) return;
//
//        Log.i(TAG, "From: " + cancelledCallInvite.getFrom() + ". To: " + cancelledCallInvite.getTo());
//        this.informAppCancelCall();

    }

    private void buildMissedCallNotification(String callerId, String to, CancelledCallInvite cancelledCallInvite) {

        String fromId = callerId.replace("client:", "");
        Context context = getApplicationContext();
        String callerName = callerId;
//        for (Map.Entry<String, String> entry : callInvite.getCustomParameters().entrySet()) {
//            if (entry.getKey().equals("fromDisplayName")) {
//                callerName = entry.getValue();
//            }
//        }
//        if (callerName == null || callerName.trim().isEmpty()) {
//            final String contactName = PreferencesUtils.getInstance(getApplicationContext()).findContactName(callInvite.getFrom());
//            if (contactName != null && !contactName.trim().isEmpty()) {
//                callerName = contactName;
//            } else {
//                callerName = "Unknown name";
//            }
//        }

        String title = getApplicationContext().getString(R.string.notification_missed_call_title+R.string.notification_missed_call_text,callerName);

        Intent returnCallIntent = new Intent(getApplicationContext(), IncomingCallNotificationService.class);
        returnCallIntent.setAction(TwilioConstants.ACTION_RETURN_CALL);
        returnCallIntent.putExtra(cancelledCallInvite.getTo(), to);
        returnCallIntent.putExtra(cancelledCallInvite.getFrom(), callerId);
        PendingIntent piReturnCallIntent = PendingIntent.getService(getApplicationContext(), 0, returnCallIntent, PendingIntent.FLAG_IMMUTABLE);


        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(this, createChannel(getApplicationContext(), true))


                            .setSmallIcon(R.drawable.ic_call_end)
                            .setContentTitle(title)
                            .setCategory(Notification.CATEGORY_CALL)
                            .setAutoCancel(true)
                            .addAction(android.R.drawable.ic_menu_call,"Call Back", piReturnCallIntent)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setContentTitle(getApplicationName(getApplicationContext()))
                            .setContentText(title)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            notification = builder.build();
        } else {
            notification = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_call_end)
                    .setContentTitle(getApplicationName(getApplicationContext()))
                    .setContentText(title)
                    .setAutoCancel(true)
                    .setOngoing(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .addAction(android.R.drawable.ic_menu_call, "Decline", piReturnCallIntent)
                    .setColor(Color.rgb(20, 10, 200)).build();
        }
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(100, notification);
    }
    private void startServiceMissedCall(CallInvite callInvite, CancelledCallInvite cancelledCallInvite) {
        Log.d("!!!!!!!!!!!!!0", callInvite.getCallSid());


        Log.d("!!!!!!!!!!!!!1", callInvite.getCallSid());
        String callerName = null;
        for (Map.Entry<String, String> entry : callInvite.getCustomParameters().entrySet()) {
            if (entry.getKey().equals("fromDisplayName")) {
                callerName = entry.getValue();
            }
        }
        if (callerName == null || callerName.trim().isEmpty()) {
            final String contactName = PreferencesUtils.getInstance(getApplicationContext()).findContactName(callInvite.getFrom());
            if (contactName != null && !contactName.trim().isEmpty()) {
                callerName = contactName;
            } else {
                callerName = "Unknown name";
            }
        }

        Log.d("!!!!!!!!!!!!!2", callerName);
        String title = getApplicationContext().getString(R.string.notification_missed_call_title+R.string.notification_missed_call_text,callerName);
        String fromId = callerName;

        Log.d("!!!!!!!!!!!!!3", title);


        Intent returnCallIntent = new Intent(getApplicationContext(), IncomingCallNotificationService.class);

        returnCallIntent.setAction(TwilioConstants.ACTION_RETURN_CALL);
        returnCallIntent.putExtra(cancelledCallInvite.getFrom(), callInvite.getFrom());
        returnCallIntent.putExtra(cancelledCallInvite.getTo(), callInvite.getTo());
        PendingIntent piReturnCallIntent = PendingIntent.getService(getApplicationContext(), 0, returnCallIntent, PendingIntent.FLAG_IMMUTABLE);


        Log.d("!!!!!!!!!!!!!4", title);

        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(this, createChannel(getApplicationContext(), true))


                            .setSmallIcon(R.drawable.ic_call_end)
                            .setContentTitle(title)
                            .setCategory(Notification.CATEGORY_CALL)
                            .setAutoCancel(true)
                            .addAction(android.R.drawable.ic_menu_call,"Call Back", piReturnCallIntent)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setContentTitle(getApplicationName(getApplicationContext()))
                            .setContentText(title)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            notification = builder.build();
        } else {
            notification = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_call_end)
                    .setContentTitle(getApplicationName(getApplicationContext()))
                    .setContentText(title)
                    .setAutoCancel(true)
                    .setOngoing(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .addAction(android.R.drawable.ic_menu_call, "Decline", piReturnCallIntent)
                    .setColor(Color.rgb(20, 10, 200)).build();
        }
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(100, notification);

    }
    public static String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }
    private static String createChannel(Context context, boolean highPriority) {
        String id = highPriority ? TwilioConstants.VOICE_CHANNEL_HIGH_IMPORTANCE : TwilioConstants.VOICE_CHANNEL_LOW_IMPORTANCE;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel;
            if (highPriority) {
                channel = new NotificationChannel(
                        TwilioConstants.VOICE_CHANNEL_HIGH_IMPORTANCE,
                        "Bivo high importance notification call channel",
                        NotificationManager.IMPORTANCE_HIGH
                );
            } else {
                channel = new NotificationChannel(
                        TwilioConstants.VOICE_CHANNEL_LOW_IMPORTANCE,
                        "Bivo low importance notification call channel",
                        NotificationManager.IMPORTANCE_LOW
                );
            }
            channel.setLightColor(Color.GREEN);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }

        return id;
    }
    private void startServiceIncomingCall(CallInvite callInvite) {
        Log.e(TAG, "Start service incoming call");
        SoundUtils.getInstance(this).playRinging();
        Notification notification = NotificationUtils.createIncomingCallNotification(getApplicationContext(), callInvite, true);
        startForeground(TwilioConstants.NOTIFICATION_INCOMING_CALL, notification);
    }

    private void stopServiceIncomingCall() {
        Log.e(TAG, "Stop service incoming call");
        stopForeground(true);
        NotificationUtils.cancel(this, TwilioConstants.NOTIFICATION_INCOMING_CALL);
        SoundUtils.getInstance(this).stopRinging();
    }

    private boolean isLocked() {
        KeyguardManager myKM = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return myKM.inKeyguardRestrictedInputMode();
    }

    private boolean isAppVisible() {
        return ProcessLifecycleOwner
                .get()
                .getLifecycle()
                .getCurrentState()
                .isAtLeast(Lifecycle.State.STARTED);
    }

    // UTILS

    private void informAppAcceptCall(CallInvite callInvite) {
        Intent intent = new Intent();
        intent.putExtra(TwilioConstants.EXTRA_INCOMING_CALL_INVITE, callInvite);
        intent.setAction(TwilioConstants.ACTION_ACCEPT);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void informAppCancelCall() {
        Intent intent = new Intent();
        intent.setAction(TwilioConstants.ACTION_CANCEL_CALL);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void openBackgroundCallActivityForAcceptCall(CallInvite callInvite) {
        try {
            Log.e(TAG, "openBackgroundCallActivityForAcceptCall function inside");
            Intent intent = new Intent(getApplicationContext(), BackgroundCallJavaActivity.class);
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK |
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            );
            intent.putExtra(TwilioConstants.EXTRA_INCOMING_CALL_INVITE, callInvite);

            Log.e(TAG, "openBackgroundCallActivityForAcceptCall callInvite  "+callInvite.getCallSid());
            intent.setAction(TwilioConstants.ACTION_ACCEPT);
            getApplicationContext().startActivity(intent);

            Log.e(TAG, "openBackgroundCallActivityForAcceptCall function after startActivity");
        } catch (Exception e) {
            Log.e(TAG, "openBackgroundCallActivityForAcceptCall " + e.toString());
        }

    }
    private void returnCall(Intent intent) {
        stopForeground(true);
        Log.i(TAG, "returning call!!!!");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(100);
    }
}

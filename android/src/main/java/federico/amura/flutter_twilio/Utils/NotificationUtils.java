package federico.amura.flutter_twilio.Utils;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.Lifecycle;

import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;


import java.util.Map;

import federico.amura.flutter_twilio.BackgroundCallJavaActivity;
import federico.amura.flutter_twilio.IncomingCallNotificationService;
import federico.amura.flutter_twilio.R;
import androidx.lifecycle.ProcessLifecycleOwner;

public class NotificationUtils {

    public static Notification createIncomingCallNotification(Context context, CallInvite callInvite, boolean showHeadsUp) {
        if (callInvite == null) return null;

        String fromDisplayName = null;
        for (Map.Entry<String, String> entry : callInvite.getCustomParameters().entrySet()) {
            if (entry.getKey().equals("fromDisplayName")) {
                fromDisplayName = entry.getValue();
            }
        }
        if (fromDisplayName == null || fromDisplayName.trim().isEmpty()) {
            final String contactName = PreferencesUtils.getInstance(context).findContactName(callInvite.getFrom());
            if (contactName != null && !contactName.trim().isEmpty()) {
                fromDisplayName = contactName;
            } else {
                fromDisplayName = "Unknown name";
            }
        }

        String notificationTitle = context.getString(R.string.notification_incoming_call_title);
        String notificationText = fromDisplayName;

        /*
         * Pass the notification id and call sid to use as an identifier to cancel the
         * notification later
         */
        Bundle extras = new Bundle();
        Log.d(" call Invite 2", callInvite.getCallSid());
        extras.putString(TwilioConstants.CALL_SID_KEY, callInvite.getCallSid());

        // Click intent
        Intent intent = new Intent(context, BackgroundCallJavaActivity.class);
//        intent.setAction(Intent.ACTION_MAIN);
//        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setAction(TwilioConstants.ACTION_INCOMING_CALL);
        intent.putExtra(TwilioConstants.EXTRA_INCOMING_CALL_INVITE, callInvite);
        Log.d(" call Invite 3", callInvite.getCallSid());
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK |
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        );
        @SuppressLint("UnspecifiedImmutableFlag")
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT
        );


        //Reject intent
        Intent rejectIntent = new Intent(context, IncomingCallNotificationService.class);
//        rejectIntent.setAction(Intent.ACTION_MAIN);
//        rejectIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        rejectIntent.setAction(TwilioConstants.ACTION_REJECT);
        rejectIntent.putExtra(TwilioConstants.EXTRA_INCOMING_CALL_INVITE, callInvite);
        @SuppressLint("UnspecifiedImmutableFlag")
        PendingIntent piRejectIntent = PendingIntent.getService(
                context,
                0,
                rejectIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Accept intent
        Intent acceptIntent = new Intent(context, IncomingCallNotificationService.class);
//        acceptIntent.setAction(Intent.ACTION_MAIN);
//        acceptIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        acceptIntent.setAction(TwilioConstants.ACTION_ACCEPT);
        acceptIntent.putExtra(TwilioConstants.EXTRA_INCOMING_CALL_INVITE, callInvite);
        @SuppressLint("UnspecifiedImmutableFlag")
        PendingIntent piAcceptIntent = PendingIntent.getService(
                context,
                0,
                acceptIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, createChannel(context, showHeadsUp));
        builder.setSmallIcon(R.drawable.ic_phone_call);
        builder.setContentTitle(notificationTitle);
        builder.setContentText(notificationText);
        builder.setCategory(NotificationCompat.CATEGORY_CALL);
        builder.setAutoCancel(true);
        builder.setExtras(extras);
        builder.setVibrate(new long[]{0, 400, 400, 400, 400, 400, 400, 400});
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
//        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) || isAppVisible())
        builder.addAction(android.R.drawable.ic_menu_delete, context.getString(R.string.btn_reject), piRejectIntent);
//        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) || isAppVisible())
        builder.addAction(android.R.drawable.ic_menu_call, context.getString(R.string.btn_accept), piAcceptIntent);
        builder.setFullScreenIntent(pendingIntent, true);
        builder.setColor(Color.rgb(20, 10, 200));
        builder.setOngoing(true);
        builder.setPriority(NotificationCompat.PRIORITY_MAX);
        builder.setContentIntent(pendingIntent);
        return builder.build();
    }
    public static Notification createMissedCallNotification(Context context,  CancelledCallInvite cancelledCallInvite, boolean showHeadsUp) {

        Log.i("TAG", "Call canceled. buildMissedCallNotification 2 " );
        Intent returnCallIntent = new Intent(context, BackgroundCallJavaActivity.class);
        returnCallIntent.setAction(TwilioConstants.ACTION_RETURN_CALL);
        returnCallIntent.putExtra(cancelledCallInvite.getTo(), "to");
        returnCallIntent.putExtra(cancelledCallInvite.getFrom(), "callerId");
        returnCallIntent.putExtra(TwilioConstants.EXTRA_CANCELLED_CALL_INVITE, cancelledCallInvite);
//        returnCallIntent.setFlags(
//                Intent.FLAG_ACTIVITY_NEW_TASK |
//                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
//                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK |
//                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
//        );
        Log.i("TAG", "Call canceled. buildMissedCallNotification 3 " );
        @SuppressLint("UnspecifiedImmutableFlag")
        PendingIntent piReturnCallIntent = PendingIntent.getActivity(
                context,
                0,
                returnCallIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT
        );

//        Log.i("TAG", "Call canceled. buildMissedCallNotification 4 " );
//        Intent intent = new Intent(context, BackgroundCallJavaActivity.class);
////        intent.setAction(Intent.ACTION_MAIN);
////        intent.addCategory(Intent.CATEGORY_LAUNCHER);
//        Log.i("TAG", "Call canceled. buildMissedCallNotification 41 " );
//        intent.setAction(TwilioConstants.ACTION_INCOMING_CALL);
//        Log.i("TAG", "Call canceled. buildMissedCallNotification 42 " );
//        intent.putExtra(TwilioConstants.EXTRA_INCOMING_CALL_INVITE, callInvite);
//        Log.i("TAG", "Call canceled. buildMissedCallNotification 43 " );
////        Log.d(" call Invite 3", callInvite.getCallSid());
////        Log.i("TAG", "Call canceled. buildMissedCallNotification 44 " );
//        intent.setFlags(
//                Intent.FLAG_ACTIVITY_NEW_TASK |
//                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
//                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK |
//                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
//        );
//        Log.i("TAG", "Call canceled. buildMissedCallNotification 5 " );
//        @SuppressLint("UnspecifiedImmutableFlag")
//        PendingIntent pendingIntent = PendingIntent.getActivity(
//                context,
//                0,
//                intent,
//                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
//                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT
//        );
        Log.i("TAG", "Call canceled. buildMissedCallNotification 6  " );
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, createChannel(context, showHeadsUp));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setSmallIcon(R.drawable.ic_call_end);
            builder.setContentTitle("title");
            builder.setCategory(Notification.CATEGORY_CALL);
            builder.setAutoCancel(true);
            builder.addAction(android.R.drawable.ic_menu_call, "Call Back", piReturnCallIntent);
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);
            builder.setContentTitle(getApplicationName(context));
            builder.setContentText("title");
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
//            builder.setContentIntent(pendingIntent);
            return builder.build();
        } else {
//            notification = new NotificationCompat.Builder(context)
            builder.setSmallIcon(R.drawable.ic_call_end);
            builder.setContentTitle(getApplicationName(context));
            builder.setContentText("title");
            builder.setAutoCancel(true);
            builder.setOngoing(true);
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            builder.setPriority(NotificationCompat.PRIORITY_MAX);
            builder.addAction(android.R.drawable.ic_menu_call,"Decline", piReturnCallIntent);
            builder.setColor(Color.rgb(20, 10, 200));
//            builder.setContentIntent(pendingIntent);
            return  builder.build();
        }
//        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
//        notificationManager.notify(100, notification);

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

    public static void cancel(Context context, int id) {
        NotificationManagerCompat.from(context).cancel(id);
    }

    private static boolean isAppVisible() {
        return ProcessLifecycleOwner
                .get()
                .getLifecycle()
                .getCurrentState()
                .isAtLeast(Lifecycle.State.STARTED);
    }
}

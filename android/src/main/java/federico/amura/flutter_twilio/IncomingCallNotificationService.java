package federico.amura.flutter_twilio;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;

import java.util.List;

import federico.amura.flutter_twilio.Utils.AppForegroundStateUtils;
import federico.amura.flutter_twilio.Utils.NotificationUtils;
import federico.amura.flutter_twilio.Utils.SoundUtils;
import federico.amura.flutter_twilio.Utils.TwilioConstants;
import federico.amura.flutter_twilio.Utils.TwilioUtils;

public class IncomingCallNotificationService extends Service {

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
                    handleIncomingCall(callInvite);
                }
                break;

                case TwilioConstants.ACTION_ACCEPT: {
                    Log.e("*Twilio onStartCommand ", "TwilioConstants.ACTION_ACCEPT case");

                    CallInvite callInvite = intent.getParcelableExtra(TwilioConstants.EXTRA_INCOMING_CALL_INVITE);
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

                    CancelledCallInvite cancelledCallInvite = intent.getParcelableExtra(TwilioConstants.EXTRA_CANCELLED_CALL_INVITE);
                    handleCancelledCall(cancelledCallInvite);
                }
                break;

                case TwilioConstants.ACTION_STOP_SERVICE: {
                    Log.e("*Twilio onStartCommand ", "TwilioConstants.ACTION_STOP_SERVICE case");

                    stopServiceIncomingCall();
                }
                break;
            }
        }
        return START_NOT_STICKY;
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
        Log.e(TAG, "Accept call invite. App visible: " + isAppVisible() + ". Locked: " + isLocked());
        this.stopServiceIncomingCall();

        if (!isLocked() && isAppVisible()) {
            // Inform call accepted
            Log.e(TAG, "Answering from APP");
            this.informAppAcceptCall(callInvite);
        } else {
            Log.e(TAG, "Answering from custom UI");
            this.openBackgroundCallActivityForAcceptCall(callInvite);
        }
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

    private void handleCancelledCall(CancelledCallInvite cancelledCallInvite) {
        Log.i(TAG, "Call canceled. App visible: " + isAppVisible() + ". Locked: " + isLocked());
        this.stopServiceIncomingCall();

        if (cancelledCallInvite == null) return;
        if (cancelledCallInvite.getFrom() == null) return;

        Log.i(TAG, "From: " + cancelledCallInvite.getFrom() + ". To: " + cancelledCallInvite.getTo());
        this.informAppCancelCall();
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
        return AppForegroundStateUtils.getInstance().isForeground();
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
            Intent intent = new Intent(this, BackgroundCallJavaActivity.class);
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK |
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            );
            intent.putExtra(TwilioConstants.EXTRA_INCOMING_CALL_INVITE, callInvite);
            intent.setAction(TwilioConstants.ACTION_ACCEPT);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "openBackgroundCallActivityForAcceptCall " + e.toString());
        }

    }
}

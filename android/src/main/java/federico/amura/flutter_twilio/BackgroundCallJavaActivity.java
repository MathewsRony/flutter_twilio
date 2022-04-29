package federico.amura.flutter_twilio;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.squareup.picasso.Picasso;
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import federico.amura.flutter_twilio.Utils.PreferencesUtils;
import federico.amura.flutter_twilio.Utils.TwilioConstants;
import federico.amura.flutter_twilio.Utils.TwilioUtils;

public class BackgroundCallJavaActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "BackgroundCallActivity";
    private static final int MIC_PERMISSION_REQUEST_CODE = 17893;
    private PowerManager.WakeLock wakeLock;
    private ViewGroup container;
    private ImageView image;
    private TextView textDisplayName;
    private TextView textPhoneNumber;
    private TextView textCallStatus;
    private TextView textTimer;
    private ImageView btnMute;
    private ImageView btnSpeaker;
    private View containerIncomingCall;
    private View containerActiveCall;
    private View containerLock;
    private CustomBroadCastReceiver customBroadCastReceiver;
    private boolean broadcastReceiverRegistered = false;
    private CallInvite callInvite;
    private boolean exited = false;
    private SensorManager sensorManager;
    private Sensor sensor;
    private boolean previouslySpeaker = false;
    private Timer timer;
    private int seconds = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_background_call);
        Log.e(TAG, "******* BackgroundCallJavaActivity onCreate");
        this.container = findViewById(R.id.container);
        this.image = findViewById(R.id.image);
        this.textDisplayName = findViewById(R.id.textDisplayName);
        this.textPhoneNumber = findViewById(R.id.textPhoneNumber);
        this.textCallStatus = findViewById(R.id.textStatus);
        this.textCallStatus.setVisibility(View.GONE);
        this.textTimer = findViewById(R.id.textTimer);
        this.textTimer.setVisibility(View.GONE);

        this.btnMute = findViewById(R.id.btnMute);
        this.btnMute.setOnClickListener((v) -> this.toggleMute());

        this.btnSpeaker = findViewById(R.id.btnSpeaker);
        this.btnSpeaker.setOnClickListener((v) -> this.toggleSpeaker());

        ImageView btnHangUp = findViewById(R.id.btnHangUp);
        btnHangUp.setOnClickListener(v -> this.hangUp());

        ImageView btnAccept = findViewById(R.id.btnAccept);
        btnAccept.setOnClickListener(v -> this.acceptCall());

        ImageView btnReject = findViewById(R.id.btnReject);
        btnReject.setOnClickListener(v -> this.rejectCall());

        this.containerActiveCall = findViewById(R.id.containerActiveCall);
        this.containerActiveCall.setVisibility(View.GONE);
        this.containerLock = findViewById(R.id.containerLock);

        this.containerIncomingCall = findViewById(R.id.containerIncomingCall);
        this.containerIncomingCall.setVisibility(View.GONE);

        applyColors();
        applyColorToButton(this.btnSpeaker, false);
        applyColorToButton(this.btnMute, false);

        this.sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        this.sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        this.turnScreenOnAndKeyguardOff();

        Log.e(TAG, "*******************************************11");
        handleIntent(getIntent());
        registerReceiver();
    }

    private boolean isSpeaker() {
        AudioManager audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        return audioManager.isSpeakerphoneOn();
    }

    @Override
    public void onBackPressed() {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wakeLock != null) {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }

        this.unregisterReceiver();
    }


    @Override
    protected void onResume() {
        super.onResume();
        this.sensorManager.registerListener(this, this.sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        float distance = event.values[0];
        if (distance == 0.0) {
            this.lockScreen();
        } else {
            this.unlockScreen();
        }
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void turnScreenOnAndKeyguardOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true);
            setShowWhenLocked(true);
        } else {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            //noinspection deprecation
            this.wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "lock:" + TAG);
            this.wakeLock.acquire(60 * 24 * 60 * 1000L /*24hs*/);

            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN |
                            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            );
        }

        KeyguardManager kgm = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        Log.d(TAG, "isKeyguardUp $isKeyguardUp");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            kgm.requestDismissKeyguard(this, null);
        }
    }

    private void lockScreen() {
        this.previouslySpeaker = this.isSpeaker();
        if (this.previouslySpeaker) {
            this.toggleSpeaker();
        }
        this.containerLock.setVisibility(View.VISIBLE);
    }

    private void unlockScreen() {
        if (this.previouslySpeaker) {
            this.toggleSpeaker();
        }
        this.containerLock.setVisibility(View.GONE);
    }

    private void registerReceiver() {
        if (broadcastReceiverRegistered) return;

        this.broadcastReceiverRegistered = true;
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TwilioConstants.ACTION_CANCEL_CALL);
        intentFilter.addAction(TwilioConstants.ACTION_REJECT);
        this.customBroadCastReceiver = new CustomBroadCastReceiver(this);
        LocalBroadcastManager.getInstance(this).registerReceiver(customBroadCastReceiver, intentFilter);
    }

    private void unregisterReceiver() {
        if (!broadcastReceiverRegistered) return;
        this.broadcastReceiverRegistered = false;
        LocalBroadcastManager.getInstance(this).unregisterReceiver(customBroadCastReceiver);
    }

    private void handleIntent(Intent intent) {
        Log.e(TAG, "*******************************************12");
        if (intent == null || intent.getAction() == null) {
            close();
            return;
        }

        switch (intent.getAction()) {
            case TwilioConstants.ACTION_INCOMING_CALL: {
                Log.e(TAG, "*******************************************13");
                this.callInvite = intent.getParcelableExtra(TwilioConstants.EXTRA_INCOMING_CALL_INVITE);
                containerIncomingCall.setVisibility(View.VISIBLE);
                containerActiveCall.setVisibility(View.GONE);
                updateCallDetails();
            }
            break;

            case TwilioConstants.ACTION_ACCEPT: {
                Log.e(TAG, "*******************************************14");
                this.callInvite = intent.getParcelableExtra(TwilioConstants.EXTRA_INCOMING_CALL_INVITE);
                containerIncomingCall.setVisibility(View.GONE);
                containerActiveCall.setVisibility(View.VISIBLE);
                updateCallDetails();
                this.checkPermissionsAndAccept();
            }
            break;

            case TwilioConstants.ACTION_CANCEL_CALL: {
                Log.e(TAG, "*******************************************15");
                onCallCanceled();
            }

            break;
            case TwilioConstants.ACTION_RETURN_CALL:
                Log.e(TAG, "*******************************************23");
                Log.e("*Twilio onStartCommand ", "TwilioConstants.ACTION_RETURN_CALL case");
                CancelledCallInvite callInvite = intent.getParcelableExtra(TwilioConstants.EXTRA_CANCELLED_CALL_INVITE);
                returnCall(intent,callInvite);
                break;

        }
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null || intent.getAction() == null) return;

        if (TwilioConstants.ACTION_CANCEL_CALL.equals(intent.getAction())) {
            onCallCanceled();
        }
    }

    private void stopServiceIncomingCall() {
        Intent intent = new Intent(this, IncomingCallNotificationService.class);
        intent.setAction(TwilioConstants.ACTION_STOP_SERVICE);
        startService(intent);
    }
    private void checkPermissionsAndAccept(){
        Log.d(TAG, "Clicked accept");
        if (!checkPermissionForMicrophone()) {
            Log.d(TAG, "configCallUI-requestAudioPermissions");
            requestAudioPermissions();
        } else {
            Log.d(TAG, "configCallUI-newAnswerCallClickListener");
            acceptCall();
        }
    }
    private Boolean checkPermissionForMicrophone() {
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return resultMic == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioPermissions() {
        String[] permissions = {Manifest.permission.RECORD_AUDIO};
        Log.d(TAG, "requestAudioPermissions");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                ActivityCompat.requestPermissions(this, permissions, MIC_PERMISSION_REQUEST_CODE);
            } else {
                ActivityCompat.requestPermissions(this, permissions, MIC_PERMISSION_REQUEST_CODE);
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "requestAudioPermissions-> permission granted->newAnswerCallClickListener");
            acceptCall();
        }
    }
    private void acceptCall() {
        Log.e(TAG, "*******************************************16");
        stopServiceIncomingCall();

        if (this.callInvite == null) {
            Log.e(TAG, "*******************************************17");
            Log.i(TAG, "No call invite");
            this.close();
            return;
        }
        Log.e(TAG, "*******************************************18");
        this.containerActiveCall.setVisibility(View.VISIBLE);
        this.containerIncomingCall.setVisibility(View.GONE);

        try {
            Log.e(TAG, "*******************************************19");
            TwilioUtils.getInstance(this).acceptInvite(this.callInvite, getListener());
            Log.e(TAG, "*******************************************20");
        } catch (Exception exception) {
            Log.e(TAG, "*******************************************21");
            exception.printStackTrace();
            this.close();
        }
    }

    private void rejectCall() {
        stopServiceIncomingCall();

        if (this.callInvite == null) {
            Log.i(TAG, "No call invite");
            this.close();
            return;
        }

        try {
            this.callInvite.reject(this);
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        this.close();
    }


    private void hangUp() {
        try {
            TwilioUtils.getInstance(this).disconnect();
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        this.close();
    }

    private void onCallCanceled() {
        try {
            TwilioUtils.getInstance(this).disconnect();
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        this.close();
    }

    private void toggleMute() {
        try {
            boolean muted = TwilioUtils.getInstance(this).toggleMute();
            applyColorToButton(this.btnMute, muted);
            this.btnMute.setImageResource(muted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void toggleSpeaker() {
        try {
            boolean speaker = TwilioUtils.getInstance(this).toggleSpeaker();
            applyColorToButton(this.btnSpeaker, speaker);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void applyColors() {
        final PreferencesUtils preferencesUtils = PreferencesUtils.getInstance(this);
        final int backgroundColor = preferencesUtils.getCallBackgroundColor();
        this.container.setBackgroundColor(backgroundColor);

        final int textColor = preferencesUtils.getCallTextColor();
        this.textDisplayName.setTextColor(textColor);
        this.textPhoneNumber.setTextColor(textColor);
        this.textCallStatus.setTextColor(textColor);
        this.textTimer.setTextColor(textColor);
    }

    private void applyColorToButton(ImageView view, boolean fill) {
        final PreferencesUtils preferencesUtils = PreferencesUtils.getInstance(this);
        int backgroundColor;
        int iconColor;
        if (fill) {
            backgroundColor = preferencesUtils.getCallButtonFocusColor();
            iconColor = preferencesUtils.getCallButtonFocusIconColor();
        } else {
            backgroundColor = preferencesUtils.getCallButtonColor();
            iconColor = preferencesUtils.getCallButtonIconColor();
        }

        Drawable background = view.getBackground();
        DrawableCompat.setTint(background, backgroundColor);
        ImageViewCompat.setImageTintList(view, ColorStateList.valueOf(iconColor));
    }

    private void updateCallDetails() {
        HashMap<String, Object> call = TwilioUtils.getInstance(this).getCallDetails();

        String status = (String) call.get("status");
        if (status != null && !status.trim().equals("")) {
            switch (status) {
                case "callRinging": {
                    this.textCallStatus.setVisibility(View.VISIBLE);
                    textCallStatus.setText(R.string.call_status_ringing);
                }
                break;

                case "callReconnecting": {
                    this.textCallStatus.setVisibility(View.VISIBLE);
                    textCallStatus.setText(R.string.call_status_reconnecting);
                }
                break;

                default: {
                    this.textCallStatus.setVisibility(View.GONE);
                }
                break;
            }
        } else {
            this.textCallStatus.setVisibility(View.VISIBLE);
            textCallStatus.setText(R.string.call_status_connecting);
        }

        // Display name
        String fromDisplayName = null;
        if (this.callInvite != null) {
            for (Map.Entry<String, String> entry : callInvite.getCustomParameters().entrySet()) {
                if (entry.getKey().equals("fromDisplayName")) {
                    fromDisplayName = entry.getValue();
                }
            }

            if (fromDisplayName == null || fromDisplayName.trim().isEmpty()) {
                final String contactName = PreferencesUtils.getInstance(this).findContactName(this.callInvite.getFrom());
                if (contactName != null && !contactName.trim().isEmpty()) {
                    fromDisplayName = contactName;
                } else {
                    fromDisplayName = "Unknown name";
                }
            }
        } else {
            fromDisplayName = "Unknown name";
        }
        this.textDisplayName.setText(fromDisplayName);

        // Phone number
        this.textPhoneNumber.setText("");
//        String phoneNumber;
//        if (from != null && !from.trim().equals("")) {
//            phoneNumber = from;
//        } else {
//            phoneNumber = (String) call.get("to");
//        }
//
//        if (phoneNumber != null && !phoneNumber.trim().equals("")) {
//            this.textPhoneNumber.setText(phoneNumber);
//        } else {
//            this.textPhoneNumber.setText("");
//        }

        // Image
        Picasso.get().load("https://stonegatesl.com/wp-content/uploads/2021/01/avatar-300x300.jpg").into(this.image);

//        String imageURL = null;
//        if (from != null && !from.trim().equals("")) {
//            imageURL = PreferencesUtils.getInstance(this).findPhotoURL(from);
//        } else {
//            imageURL = (String) call.get("toPhotoURL");
//        }

//        if (imageURL != null && !imageURL.trim().equals("")) {
//            Picasso.get().load(imageURL).into(this.image);
//        } else {
//            Picasso.get().load("https://stonegatesl.com/wp-content/uploads/2021/01/avatar-300x300.jpg").into(this.image);
//        }

        // Timer
        if (status != null && status.equals("callConnected")) {
            this.startTimer();
        } else {
            this.stopTimer();
        }
    }

    private void close() {
        if (this.exited) return;

        if (this.wakeLock != null && this.wakeLock.isHeld()) {
            this.wakeLock.release();
        }

        this.stopTimer();
        this.exited = true;
        this.finish();
    }

    @Override
    public void finish() {
        this.stopTimer();
        super.finish();
    }


    private void startTimer() {
        this.textTimer.setVisibility(View.VISIBLE);
        this.textTimer.setText(DateUtils.formatElapsedTime(0));

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                seconds += 1;
                runOnUiThread(new TimerTask() {
                    @Override
                    public void run() {
                        textTimer.setText(DateUtils.formatElapsedTime(seconds));
                    }
                });
            }
        }, 0, 1000);
    }

    private void stopTimer() {
        if (this.timer != null) {
            this.timer.cancel();
            this.timer = null;
        }
        this.textTimer.setVisibility(View.GONE);
    }


    Call.Listener getListener() {
        return new Call.Listener() {
            @Override
            public void onConnectFailure(@NonNull Call call, @NonNull CallException callException) {
                updateCallDetails();
                close();
            }

            @Override
            public void onRinging(@NonNull Call call) {
                updateCallDetails();
            }

            @Override
            public void onConnected(@NonNull Call call) {
                updateCallDetails();
            }

            @Override
            public void onReconnecting(@NonNull Call call, @NonNull CallException callException) {
                updateCallDetails();
            }

            @Override
            public void onReconnected(@NonNull Call call) {
                updateCallDetails();
            }

            @Override
            public void onDisconnected(@NonNull Call call, @Nullable CallException callException) {
                updateCallDetails();
                close();
            }
        };
    }


    private static class CustomBroadCastReceiver extends BroadcastReceiver {

        private final BackgroundCallJavaActivity activity;

        private CustomBroadCastReceiver(BackgroundCallJavaActivity activity) {
            this.activity = activity;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Received broadcast for action " + action);

            if (action == null) return;
            if (TwilioConstants.ACTION_CANCEL_CALL.equals(action)) {
                activity.onCallCanceled();
            }

            if (TwilioConstants.ACTION_REJECT.equals(action)) {
                activity.onCallCanceled();
            }
        }
    }
    private void returnCall(Intent intent, CancelledCallInvite callInvite) {
//        stopForeground(true);
        Log.i(TAG, "returning call!!!!");
        Log.e(TAG, "*******************************************19");

        Map<String, Object> data  = new HashMap<String, Object>();
        data.put("To", callInvite.getTo());
        data.put("From", callInvite.getFrom());
        this.containerActiveCall.setVisibility(View.VISIBLE);
        this.containerIncomingCall.setVisibility(View.GONE);

        try {
            Log.e(TAG, "*******************************************1"+callInvite.getTo());
            TwilioUtils.getInstance(this).makeCall(callInvite.getTo(),data, getListener());
            Log.e(TAG, "*******************************************2"+callInvite.getFrom());
        } catch (Exception exception) {
            Log.e(TAG, "*******************************************21");
            exception.printStackTrace();
            this.close();
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.e(TAG, "*******************************************3");
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        Log.e(TAG, "*******************************************4");
        notificationManager.cancel(100);
    }
}
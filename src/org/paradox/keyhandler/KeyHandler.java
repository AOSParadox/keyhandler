package com.paradox.keyhandler;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.KeyEvent;

import com.android.internal.os.DeviceKeyHandler;
import com.android.internal.util.ArrayUtils;

public class KeyHandler implements DeviceKeyHandler {
    private static final String TAG = KeyHandler.class.getSimpleName();
    private static final boolean DEBUG = true;
    private static final int GESTURE_REQUEST = 1;
    private static final int GESTURE_WAKELOCK_DURATION = 3000;
	
    // Supported scancodes
    private static final int GESTURE_V_SCANCODE = 252;
    private static final int MODE_TOTAL_SILENCE = 600;
    private static final int MODE_ALARMS_ONLY = 601;
    private static final int MODE_PRIORITY_ONLY = 602;
    private static final int MODE_NONE = 603;
	
    private static final int[] sSupportedGestures = new int[]{
        GESTURE_V_SCANCODE,
    };
	
    private static final SparseIntArray sSupportedSliderModes = new SparseIntArray();
    static {
        sSupportedSliderModes.put(MODE_TOTAL_SILENCE, Settings.Global.ZEN_MODE_NO_INTERRUPTIONS);
        sSupportedSliderModes.put(MODE_ALARMS_ONLY, Settings.Global.ZEN_MODE_ALARMS);
        sSupportedSliderModes.put(MODE_PRIORITY_ONLY, Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS);
        sSupportedSliderModes.put(MODE_NONE, Settings.Global.ZEN_MODE_OFF);
    }
	
    private final Context mContext;
    private final PowerManager mPowerManager;
    private final NotificationManager mNotificationManager;
    private EventHandler mEventHandler;
    private CameraManager mCameraManager;
    private String mRearCameraId;
    private boolean mTorchEnabled;
    private WakeLock mGestureWakeLock;
    private Handler mHandler = new Handler();
    private Vibrator mVibrator;

    public KeyHandler(Context context) {
        mContext = context;
        mEventHandler = new EventHandler();
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
	mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mGestureWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GestureWakeLock");

	mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
	if (mVibrator == null || !mVibrator.hasVibrator()) {
            mVibrator = null;
        }

	mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        mCameraManager.registerTorchCallback(new MyTorchCallback(), mEventHandler);
    }
	
    private class MyTorchCallback extends CameraManager.TorchCallback {
        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (!cameraId.equals(mRearCameraId))
                return;
            mTorchEnabled = enabled;
        }

        @Override
        public void onTorchModeUnavailable(String cameraId) {
            if (!cameraId.equals(mRearCameraId))
                return;
            mTorchEnabled = false;
        }
    }
	
    private String getRearCameraId() {
        if (mRearCameraId == null) {
            try {
                for(final String cameraId : mCameraManager.getCameraIdList()) {
                    CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                    int cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if(cOrientation == CameraCharacteristics.LENS_FACING_BACK) {
                        mRearCameraId = cameraId;
                        break;
                    }
                }
            } catch (CameraAccessException e) {
                // Ignore
            }
        }
        return mRearCameraId;
    }
	
    private class EventHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.arg1) {
		case GESTURE_V_SCANCODE:
		    String rearCameraId = getRearCameraId();
		    if (rearCameraId != null) {
			mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
			try {
			    mCameraManager.setTorchMode(rearCameraId, !mTorchEnabled);
			    mTorchEnabled = !mTorchEnabled;
			} catch (CameraAccessException e) {
			    // Ignore
			}
			doHapticFeedback();
		    }
		    break;
            }
        }
    }
	
    @Override
    public boolean handleKeyEvent(KeyEvent event) {
	int scanCode = event.getScanCode();
        boolean isKeySupported = ArrayUtils.contains(sSupportedGestures, scanCode);
        boolean isSliderModeSupported = sSupportedSliderModes.indexOfKey(scanCode) >= 0;
	if(!isKeySupported && !isSliderModeSupported) {
            return false;
        }
		
        if(event.getAction() != KeyEvent.ACTION_UP) {
            return true;
        }
        
	if(isSliderModeSupported) {
            mNotificationManager.setZenMode(sSupportedSliderModes.get(scanCode), null, TAG);
            doHapticFeedback();
	} else if(!mEventHandler.hasMessages(GESTURE_REQUEST)) {
	    Message msg = getMessageForKeyEvent(scanCode);
	    mEventHandler.sendMessage(msg);
	}
	return true;
    }

    private Message getMessageForKeyEvent(int scancode) {
        Message msg = mEventHandler.obtainMessage(GESTURE_REQUEST);
        msg.arg1 = scancode;
        return msg;
    }
	
    private void doHapticFeedback() {
	if(mVibrator == null) {
	    return;
	}
	mVibrator.vibrate(50);
    }
}

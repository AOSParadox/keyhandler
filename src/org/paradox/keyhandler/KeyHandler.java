/*
* Copyright (C) 2016 The AOSParadox Project
* Copyright (C) 2016 OmniROM Project
* Copyright (C) 2016 CyanogenMod Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/

package com.paradox.keyhandler;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.UserHandle;
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
	private static final int GESTURE_CIRCLE_SCANCODE = 62;
	private static final int GESTURE_V_SCANCODE = 63;
	private static final int MODE_TOTAL_SILENCE = 600;
	private static final int MODE_ALARMS_ONLY = 601;
	private static final int MODE_PRIORITY_ONLY = 602;
	private static final int MODE_NONE = 603;
	
	private static final int[] sSupportedGestures = new int[]{
        	GESTURE_CIRCLE_SCANCODE,
        	GESTURE_V_SCANCODE,
		MODE_TOTAL_SILENCE,
		MODE_ALARMS_ONLY,
		MODE_PRIORITY_ONLY,
		MODE_NONE,
    	};
	
	private static final int[] sHandledGestures = new int[]{
		GESTURE_V_SCANCODE,
	};
	
	private static final SparseIntArray sSupportedSliderModes = new SparseIntArray();
	static {
        	sSupportedSliderModes.put(MODE_TOTAL_SILENCE, Settings.Global.ZEN_MODE_NO_INTERRUPTIONS);
		sSupportedSliderModes.put(MODE_ALARMS_ONLY, Settings.Global.ZEN_MODE_ALARMS);
		sSupportedSliderModes.put(MODE_PRIORITY_ONLY, Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS);
		sSupportedSliderModes.put(MODE_NONE, Settings.Global.ZEN_MODE_OFF);
   	};
	
	private final Context mContext;
	private final PowerManager mPowerManager;
	private final NotificationManager mNotificationManager;
	private EventHandler mEventHandler;
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
    	}
	
	private class EventHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            KeyEvent event = (KeyEvent) msg.obj;
            switch(event.getScanCode()) {
           			 case GESTURE_CIRCLE_SCANCODE:
           			 	if (DEBUG) Log.i(TAG, "GESTURE_CIRCLE_SCANCODE");
           			 	ensureKeyguardManager();
           			 	String action = null;
           			 	mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
           			 	if (mKeyguardManager.isKeyguardSecure() && mKeyguardManager.isKeyguardLocked()) {
           			 		action = MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE;
           			 	} else {
           			 		try {
            			 			WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
           			 	} catch (RemoteException e) {
           			 		// Ignore
           			 	}
           			 		action = MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA;
           			 	}
           			 	mPowerManager.wakeUp(SystemClock.uptimeMillis(), "android.policy:GESTURE");
           			 	Intent intent = new Intent(action, null);
           			 	startActivitySafely(intent);
                			break;
				case GESTURE_V_SCANCODE:
					if (DEBUG) Log.i(TAG, "GESTURE_V_SCANCODE");
					mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
					Intent torchIntent = new Intent("com.android.systemui.TOGGLE_FLASHLIGHT");
					torchIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
					UserHandle user = new UserHandle(UserHandle.USER_CURRENT);
					mContext.sendBroadcastAsUser(torchIntent, user);
					break;
				case MODE_TOTAL_SILENCE:
				case MODE_ALARMS_ONLY:
				case MODE_PRIORITY_ONLY:
				case MODE_NONE:
					if (DEBUG) Log.i(TAG, "ZEN_MODE=" + sSupportedSliderModes.get(scanCode));
					mNotificationManager.setZenMode(sSupportedSliderModes.get(scanCode), null, TAG);
					mVibrator.vibrate(50);
            }
        }
    }
	
	@Override
    public boolean handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return false;
        }
        if (DEBUG) Log.i(TAG, "scanCode=" + event.getScanCode());
        boolean isKeySupported = ArrayUtils.contains(sSupportedGestures, event.getScanCode());
        if (isKeySupported && !mEventHandler.hasMessages(GESTURE_REQUEST)) {
            Message msg = getMessageForKeyEvent(event);
            mEventHandler.sendMessage(msg);
        }
        return isKeySupported;
    }
	
	@Override
    public boolean canHandleKeyEvent(KeyEvent event) {
        return ArrayUtils.contains(sSupportedGestures, event.getScanCode());
    }

    private Message getMessageForKeyEvent(KeyEvent keyEvent) {
        Message msg = mEventHandler.obtainMessage(GESTURE_REQUEST);
        msg.obj = keyEvent;
        return msg;
    }
}

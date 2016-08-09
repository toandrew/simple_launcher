package com.xiaoyezi.classroom.launcher;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.xiaoyezi.classroom.launcher.utils.Utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Home app for classroom
 */
public class FullscreenActivity extends AppCompatActivity {
    private static final String TAG = "XiaoyeziHome";

    private static final String CLASSROOM_PACKAGE_NAME = "com.theonepiano.classroom";

    private static final int MSG_ID_UPDATE          = 0;
    private static final int MSG_ID_START_CLASSROOM = 1;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private TextView mNetworkInfo;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mNetworkInfo.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };

    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    BroadcastReceiver mUiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            String action = intent.getAction();
            if (action == null) {
                return;
            }

            if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
                final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wifiManager.getConnectionInfo();
                SupplicantState state = info.getSupplicantState();

                String wifiInfo = getResources().getString(
                        R.string.network_setup)
                        + "(" + state + ")";
                mNetworkInfo.setText(wifiInfo);
            } else {
                mHandler.sendEmptyMessage(MSG_ID_UPDATE);
            }
        }
    };

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ID_UPDATE:
                    removeMessages(MSG_ID_UPDATE);
                    updateUi();
                    break;
                case MSG_ID_START_CLASSROOM:
                    startClassroom();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        mNetworkInfo = (TextView)findViewById(R.id.fullscreen_content);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "onResume");
        monitorNetwork();

        mHandler.sendEmptyMessageDelayed(MSG_ID_UPDATE, 100);
    }

    @Override
    protected void onPause() {
        super.onPause();

        unMonitorNetwork();

        Log.d(TAG, "onPause");
    }

    @Override
    protected void onStart() {
        super.onStart();

        Log.d(TAG, "onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mHandler.removeCallbacksAndMessages(null);

        Log.d(TAG, "onDestroy");
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    /**
     * Hide some useless ui
     */
    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHandler.removeCallbacks(mHideRunnable);
        mHandler.postDelayed(mHideRunnable, delayMillis);
    }

    /**
     * Monitor wifi/ethernet network
     */
    private void monitorNetwork() {
        IntentFilter filter = new IntentFilter(
                WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);

        registerReceiver(mUiReceiver, filter);
    }

    /**
     * Remove monitor for wifi/ethernet network
     */
    private void unMonitorNetwork() {
        if (mUiReceiver != null) {
            unregisterReceiver(mUiReceiver);
        }
    }

    /**
     * Update ui
     */
    private void updateUi() {
        int network = Utils.checkNet(this);

        String ipAddress = null;
        if (network == ConnectivityManager.TYPE_ETHERNET) {
            ipAddress = Utils.getLocalIpAddress();
        } else {
            if (Utils.getWifiIp(this) != null) {
                ipAddress = Utils.getWifiIp(this).getHostAddress().toString();
            }
        }

        if (ipAddress != null) {
            mNetworkInfo.setText(getString(R.string.network_ok) + ":" + ipAddress);

            // start classroom
            mHandler.sendEmptyMessage(MSG_ID_START_CLASSROOM);
        }
        Log.e(TAG, "ipAddress: " + ipAddress);
    }

    /**
     * Start classroom application
     */
    private void startClassroom() {
        // kill it
        forceStopProgress(this, CLASSROOM_PACKAGE_NAME);

        Intent LaunchIntent = getPackageManager().getLaunchIntentForPackage(CLASSROOM_PACKAGE_NAME);
        LaunchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(LaunchIntent);
    }

    /**
     * Kill some app
     */
    public void forceStopProgress(Context context, String pkgName) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        try {
            Method forceStopPackage = am.getClass().getDeclaredMethod("forceStopPackage", String.class);
            forceStopPackage.setAccessible(true);
            forceStopPackage.invoke(am, pkgName);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}

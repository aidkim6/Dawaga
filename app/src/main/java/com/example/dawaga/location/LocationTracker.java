package com.example.dawaga.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * 앱이 화면에 떠 있는 동안만 주기적으로 내 위치를 받는다.
 * 백그라운드 수집(Foreground Service)은 하지 않는다 (기획서 v2 "위치 수집").
 *
 * 화면의 onResume에서 start, onPause에서 stop을 부르는 것을 전제로 한다.
 */
public class LocationTracker {

    /** 기획서 v2의 2~5초 범위 중 3초 */
    public static final long DEFAULT_INTERVAL_MS = 3000L;

    public interface Callback {
        void onLocation(double latitude, double longitude, float accuracyM);
    }

    private final FusedLocationProviderClient client;
    private final Context appContext;
    private final long intervalMs;
    private Callback callback;
    private boolean running;

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult result) {
            Location loc = result.getLastLocation();
            if (loc != null && callback != null) {
                callback.onLocation(loc.getLatitude(), loc.getLongitude(),
                        loc.hasAccuracy() ? loc.getAccuracy() : 0f);
            }
        }
    };

    public LocationTracker(@NonNull Context context) {
        this(context, DEFAULT_INTERVAL_MS);
    }

    public LocationTracker(@NonNull Context context, long intervalMs) {
        this.appContext = context.getApplicationContext();
        this.client = LocationServices.getFusedLocationProviderClient(appContext);
        this.intervalMs = intervalMs;
    }

    /** 정확한 위치 또는 대략적 위치 권한 중 하나라도 있는지 */
    public static boolean hasPermission(@NonNull Context context) {
        return hasFinePermission(context)
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** 정확한 위치 권한. 대략적 위치만 있으면 50m 도착 판정이 사실상 불가능하다 */
    public static boolean hasFinePermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 위치 수신 시작. 이미 돌고 있으면 콜백만 바꾼다.
     *
     * @return 권한이 없어 시작하지 못하면 false
     */
    public boolean start(@NonNull Callback callback) {
        this.callback = callback;
        if (running) {
            return true;
        }
        if (!hasPermission(appContext)) {
            return false;
        }

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(intervalMs)
                .build();
        try {
            client.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            // 확인 직후 권한이 회수된 경우
            return false;
        }
        running = true;
        return true;
    }

    public void stop() {
        if (!running) {
            return;
        }
        client.removeLocationUpdates(locationCallback);
        running = false;
    }

    public boolean isRunning() {
        return running;
    }
}

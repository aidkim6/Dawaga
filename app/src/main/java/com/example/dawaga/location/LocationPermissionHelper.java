package com.example.dawaga.location;

import android.Manifest;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import java.util.Map;

/**
 * 위치 권한 요청. 백그라운드 위치 권한은 요청하지 않는다.
 *
 * 액티비티의 onCreate에서 만들어야 한다 (registerForActivityResult 제약).
 * <pre>
 * permissionHelper = new LocationPermissionHelper(this);
 * ...
 * permissionHelper.request((granted, precise) -> {
 *     if (!granted) { 안내 문구 표시; return; }
 *     if (!precise) { "정확한 위치를 켜야 도착 판정이 됩니다" 안내 }
 *     session.start();
 * });
 * </pre>
 */
public class LocationPermissionHelper {

    public interface Result {
        /**
         * @param granted 위치 권한(정확 또는 대략) 허용 여부
         * @param precise 정확한 위치 허용 여부
         */
        void onResult(boolean granted, boolean precise);
    }

    private static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private final ComponentActivity activity;
    private final ActivityResultLauncher<String[]> launcher;
    private Result pending;

    public LocationPermissionHelper(@NonNull ComponentActivity activity) {
        this.activity = activity;
        this.launcher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::handleResult);
    }

    public void request(@NonNull Result result) {
        if (LocationTracker.hasPermission(activity)) {
            result.onResult(true, LocationTracker.hasFinePermission(activity));
            return;
        }
        pending = result;
        launcher.launch(PERMISSIONS);
    }

    private void handleResult(Map<String, Boolean> granted) {
        boolean fine = Boolean.TRUE.equals(granted.get(Manifest.permission.ACCESS_FINE_LOCATION));
        boolean coarse = Boolean.TRUE.equals(granted.get(Manifest.permission.ACCESS_COARSE_LOCATION));
        if (pending != null) {
            Result r = pending;
            pending = null;
            r.onResult(fine || coarse, fine);
        }
    }
}

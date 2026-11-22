package com.example.dawaga.location;

import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.location.LocationManagerCompat;

/** 위치 공유를 시작할 수 있는 상태인지 (권한, 기기 위치 서비스) 확인 */
public final class LocationReadiness {

    public enum State {
        /** 권한도 있고 위치 서비스도 켜져 있음 */
        READY,
        /** 위치 권한 없음 */
        NO_PERMISSION,
        /** 기기 위치 서비스(GPS)가 꺼져 있음 */
        LOCATION_OFF
    }

    private LocationReadiness() {
    }

    @NonNull
    public static State check(@NonNull Context context) {
        if (!LocationTracker.hasPermission(context)) {
            return State.NO_PERMISSION;
        }
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null || !LocationManagerCompat.isLocationEnabled(lm)) {
            return State.LOCATION_OFF;
        }
        return State.READY;
    }

    /** 앱 상세 설정 화면 (권한을 직접 켜는 곳) */
    @NonNull
    public static Intent appSettingsIntent(@NonNull Context context) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.getPackageName(), null));
    }

    /** 기기 위치 서비스(GPS) 설정 화면 */
    @NonNull
    public static Intent locationSettingsIntent() {
        return new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
    }
}

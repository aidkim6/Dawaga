package com.example.dawaga;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.KakaoMapReadyCallback;
import com.kakao.vectormap.KakaoMapSdk;
import com.kakao.vectormap.MapLifeCycleCallback;
import com.kakao.vectormap.MapView;

import java.security.MessageDigest;

public class MainActivity extends AppCompatActivity {

    private MapView mapView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 카카오 지도 SDK 초기화 (BuildConfig에서 키 값을 안전하게 가져옵니다)
        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY);

        setContentView(R.layout.activity_main);

        // 2. Logcat 출력용 디버그 키 해시 확인
        String keyHash = getDebugKeyHash();
        Log.d("KakaoMapTest", "★ 현재 앱의 디버그 키 해시(KeyHash): " + keyHash);

        // 3. MapView 바인딩 및 시작
        mapView = findViewById(R.id.map_view);
        mapView.start(new MapLifeCycleCallback() {
            @Override
            public void onMapDestroy() {
                Log.d("KakaoMapTest", "onMapDestroy: 지도가 종료되었습니다.");
            }

            @Override
            public void onMapError(Exception error) {
                Log.e("KakaoMapTest", "onMapError 발생: " + error.getMessage(), error);
                Toast.makeText(MainActivity.this, "지도 인증 오류: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, new KakaoMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull KakaoMap kakaoMap) {
                Log.d("KakaoMapTest", "onMapReady: 카카오맵 연동 성공!");
                Toast.makeText(MainActivity.this, "카카오맵 연동 성공!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 디버그 키 해시 추출 메서드
    private String getDebugKeyHash() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(
                    getPackageName(),
                    PackageManager.GET_SIGNATURES
            );
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                return Base64.encodeToString(md.digest(), Base64.NO_WRAP);
            }
        } catch (Exception e) {
            Log.e("KakaoMapTest", "KeyHash 추출 실패", e);
        }
        return "KeyHash 확인 불가";
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.pause();
        }
    }
}
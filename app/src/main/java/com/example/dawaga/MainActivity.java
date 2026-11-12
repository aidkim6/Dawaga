package com.example.dawaga;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.KakaoMapReadyCallback;
import com.kakao.vectormap.KakaoMapSdk;
import com.kakao.vectormap.LatLng;
import com.kakao.vectormap.MapLifeCycleCallback;
import com.kakao.vectormap.MapView;
import com.kakao.vectormap.camera.CameraUpdateFactory;
import com.kakao.vectormap.label.LabelLayer;
import com.kakao.vectormap.label.LabelOptions;
import com.kakao.vectormap.label.LabelStyle;
import com.kakao.vectormap.label.LabelStyles;
import com.kakao.vectormap.label.LabelTextBuilder;

import java.security.MessageDigest;

public class MainActivity extends AppCompatActivity {

    private MapView mapView;
    private DawagaFirebaseManager firebaseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 카카오 지도 SDK 초기화
        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY);
        setContentView(R.layout.activity_main);

        // 2. Firebase 매니저 초기화
        firebaseManager = new DawagaFirebaseManager();

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

                // [지원 담당] 1) 약속 장소 지도 핀 찍기 & 카메라 이동 (예시: 성신여대입구역 37.5926, 127.0164)
                addDestinationPin(kakaoMap, 37.5926, 127.0164, "약속 장소");

                // [지원 담당] 2) Firestore DB 실시간 쓰기/읽기 검증
                testFirebaseIntegration();
            }
        });
    }

    // 약속 장소 핀(Label) 찍기 + 카메라 이동 메서드 (팀원 공용 사용 가능)
    public void addDestinationPin(KakaoMap kakaoMap, double destLat, double destLng, String placeName) {
        LatLng destPos = LatLng.from(destLat, destLng);

        LabelStyles styles = kakaoMap.getLabelManager()
                .addLabelStyles(LabelStyles.from(
                        LabelStyle.from(R.mipmap.ic_launcher)
                                .setTextStyles(24, Color.BLACK)
                ));

        LabelOptions options = LabelOptions.from(destPos)
                .setStyles(styles)
                .setTexts(new LabelTextBuilder().setTexts(placeName));

        LabelLayer layer = kakaoMap.getLabelManager().getLayer();
        if (layer != null) {
            layer.addLabel(options);
        }

        kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(destPos, 16));
    }

    // Firestore DB 생성 및 실시간 수신 테스트
    private void testFirebaseIntegration() {
        // 실시간 수신 리스너 먼저 등록
        firebaseManager.listenRoomMembers("TEST01", members -> {
            Log.d("DawagaFirebase", "실시간 멤버 수신 완료! 인원수: " + members.size() + " / 데이터: " + members);
        });

        // 테스트 위치 데이터 전송
        firebaseManager.updateMyLocation("TEST01", "user_jiwon", "김지원", 37.5926, 127.0164, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firebaseManager != null) {
            firebaseManager.stopListening();
        }
    }
}
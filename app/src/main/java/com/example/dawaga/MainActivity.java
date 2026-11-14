package com.example.dawaga;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
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

import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private MapView mapView;
    private DawagaFirebaseManager firebaseManager;
    private String currentRoomCode = "TEST01"; // 기본 테스트 방 코드

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

                // 약속 장소 지도 핀 찍기 & 카메라 이동 (성신여대입구역 37.5926, 127.0164)
                addDestinationPin(kakaoMap, 37.5926, 127.0164, "약속 장소");

                // Firestore 실시간 멤버 수신 리스너 등록
                startListeningRoom(currentRoomCode);
            }
        });

        // 4. XML에 추가된 버튼 바인딩 및 이벤트 연결
        Button btnCreateRoom = findViewById(R.id.btn_create_room);
        Button btnSimulateMove = findViewById(R.id.btn_simulate_move);

        // [방 생성] 버튼 클릭 시 createRoom() 실행
        btnCreateRoom.setOnClickListener(v -> {
            long meetTime = System.currentTimeMillis() + 3600000; // 1시간 뒤 약속 시간 예시
            firebaseManager.createRoom("성신여대 모임", 37.5926, 127.0164, meetTime, new DawagaFirebaseManager.OnRoomCallback() {
                @Override
                public void onSuccess(Map<String, Object> roomData) {
                    currentRoomCode = (String) roomData.get("roomCode");
                    Toast.makeText(MainActivity.this, "방 생성 성공! 코드: " + currentRoomCode, Toast.LENGTH_SHORT).show();
                    Log.d("DawagaFirebase", "방 생성 성공 코드: " + currentRoomCode);

                    // 생성된 방 코드로 실시간 리스너 재등록
                    startListeningRoom(currentRoomCode);
                }

                @Override
                public void onFailure(Exception e) {
                    Toast.makeText(MainActivity.this, "방 생성 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("DawagaFirebase", "방 생성 실패", e);
                }
            });
        });

        // [시뮬레이터 이동] 버튼 클릭 시 updateMyLocation() 실행
        btnSimulateMove.setOnClickListener(v -> {
            // 시뮬레이션용 가상 좌표 (성신여대입구역 부근)
            double mockLat = 37.5900;
            double mockLng = 127.0150;

            firebaseManager.updateMyLocation(
                    currentRoomCode,
                    "user_yewon",
                    "이예원(시뮬)",
                    mockLat,
                    mockLng,
                    false
            );
            Toast.makeText(MainActivity.this, "시뮬레이터 위치 전송 완료!", Toast.LENGTH_SHORT).show();
        });
    }

    // 방 멤버 실시간 수신 리스너 등록 메서드
    private void startListeningRoom(String roomCode) {
        firebaseManager.listenRoomMembers(roomCode, members -> {
            Log.d("DawagaFirebase", "실시간 멤버 수신 완료! 인원수: " + members.size() + " / 데이터: " + members);
        });
    }

    // 약속 장소 핀(Label) 찍기 + 카메라 이동 메서드
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
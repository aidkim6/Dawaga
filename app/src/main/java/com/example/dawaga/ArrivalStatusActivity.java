
package com.example.dawaga;

import static android.content.Intent.getIntent;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArrivalStatusActivity extends AppCompatActivity {

    private MapView mapView;
    private DawagaFirebaseManager firebaseManager;
    private MemberStatusAdapter adapter;

    private String roomCode;
    private String placeName;

    private double destinationLat;
    private double destinationLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 전달받은 약속 정보
        roomCode = getIntent().getStringExtra("ROOM_CODE");
        destinationLat = getIntent().getDoubleExtra(
                "DEST_LAT", 37.5926
        );
        destinationLng = getIntent().getDoubleExtra(
                "DEST_LNG", 127.0164
        );
        placeName = getIntent().getStringExtra("PLACE_NAME");

        if (roomCode == null || roomCode.isEmpty()) {
            roomCode = "TEST01";
        }

        if (placeName == null || placeName.isEmpty()) {
            placeName = "약속 장소";
        }

        // 2. 카카오 지도 초기화
        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY);

        setContentView(R.layout.activity_arrival_status);


        // 뒤로가기 버튼
        findViewById(R.id.btn_back).setOnClickListener(v -> {
            mapView.finish();
        });

        // 3. 지도 연결
        mapView = findViewById(R.id.arrival_map_view);

        // 4. 친구 목록 연결
        RecyclerView recyclerView =
                findViewById(R.id.member_recycler_view);

        recyclerView.setLayoutManager(
                new LinearLayoutManager(this)
        );


// 내 정보 가져오기
        android.content.SharedPreferences prefs =
                getSharedPreferences("DawagaPrefs", MODE_PRIVATE);

        String myUserId = prefs.getString("userId", null);

// 친구 목록 Adapter 연결
        adapter = new MemberStatusAdapter(
                destinationLat,
                destinationLng,
                myUserId
        );

        recyclerView.setAdapter(adapter);

        // 5. Firebase 초기화
        firebaseManager = new DawagaFirebaseManager();

        // 6. 카카오 지도 시작
        mapView.start(new MapLifeCycleCallback() {

            @Override
            public void onMapDestroy() {
                Log.d("ArrivalStatus", "지도가 종료되었습니다.");
            }

            @Override
            public void onMapError(Exception error) {
                Log.e("ArrivalStatus",
                        "지도 오류: " + error.getMessage(), error);

                Toast.makeText(
                        ArrivalStatusActivity.this,
                        "지도 오류가 발생했습니다.",
                        Toast.LENGTH_LONG
                ).show();
            }

        }, new KakaoMapReadyCallback() {

            @Override
            public void onMapReady(@NonNull KakaoMap kakaoMap) {

                Log.d("ArrivalStatus", "지도 연결 성공!");

                // 약속 장소 마커 표시
                addDestinationPin(
                        kakaoMap,
                        destinationLat,
                        destinationLng,
                        placeName
                );
            }
        });

        // 7. Firestore 실시간 친구 정보 수신
        listenRoomMembers();
    }

    // 약속 장소 마커 표시
    private void addDestinationPin(
            KakaoMap kakaoMap,
            double lat,
            double lng,
            String name
    ) {

        LatLng destination = LatLng.from(lat, lng);

        LabelStyles styles = kakaoMap.getLabelManager()
                .addLabelStyles(
                        LabelStyles.from(
                                LabelStyle.from(R.mipmap.ic_launcher)
                                        .setTextStyles(24, Color.BLACK)
                        )
                );

        LabelOptions options = LabelOptions.from(destination)
                .setStyles(styles)
                .setTexts(new LabelTextBuilder().setTexts(name));

        LabelLayer layer = kakaoMap.getLabelManager().getLayer();

        if (layer != null) {
            layer.addLabel(options);
        }

        kakaoMap.moveCamera(
                CameraUpdateFactory.newCenterPosition(
                        destination, 16
                )
        );
    }

    // Firestore 실시간 멤버 수신
    private void listenRoomMembers() {

        firebaseManager.listenRoomMembers(roomCode, members -> {

            List<Map<String, Object>> memberList =
                    new ArrayList<>();

            // Firestore에서 받은 데이터를 Map 형태로 변환
            for (Object item : members) {

                if (item instanceof Map) {

                    Map<?, ?> rawData = (Map<?, ?>) item;

                    Map<String, Object> memberData =
                            new HashMap<>();

                    for (Map.Entry<?, ?> entry :
                            rawData.entrySet()) {

                        if (entry.getKey() instanceof String) {
                            memberData.put(
                                    (String) entry.getKey(),
                                    entry.getValue()
                            );
                        }
                    }

                    memberList.add(memberData);
                }
            }

            // 화면 목록 갱신
            runOnUiThread(() -> {
                adapter.updateMembers(memberList);

                Log.d(
                        "ArrivalStatus",
                        "실시간 멤버 수신: " + memberList.size() + "명"
                );
            });
        });
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

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (firebaseManager != null) {
            firebaseManager.stopListening();
        }
    }
}

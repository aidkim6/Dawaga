
package com.example.dawaga;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.dawaga.location.LocationPermissionHelper;
import com.example.dawaga.location.LocationRules;
import com.example.dawaga.location.LocationShareSession;
import com.example.dawaga.location.LocationTracker;

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

    private static final String TAG = "ArrivalStatus";

    private MapView mapView;
    private DawagaFirebaseManager firebaseManager;
    private MemberStatusAdapter adapter;

    private LocationPermissionHelper permissionHelper;
    private LocationShareSession shareSession;

    private String roomCode;
    private String placeName;
    private String myUserId;
    private String myNickname;

    private double destinationLat;
    private double destinationLng;

    private long meetTimeMillis;

    private boolean permissionRequestAttempted = false;

    private TextView tvMemberCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 이전 화면에서 전달받은 약속 정보
        roomCode = getIntent().getStringExtra("ROOM_CODE");

        destinationLat = getIntent().getDoubleExtra(
                "DEST_LAT", 0.0
        );

        destinationLng = getIntent().getDoubleExtra(
                "DEST_LNG", 0.0
        );

        placeName = getIntent().getStringExtra("PLACE_NAME");

        meetTimeMillis = getIntent().getLongExtra(
                "MEET_TIME", 0L
        );

        if (roomCode == null || roomCode.trim().isEmpty()) {
            Toast.makeText(this,
                    "약속방 정보를 찾을 수 없습니다.",
                    Toast.LENGTH_LONG).show();

            finish();
            return;
        }

        if (placeName == null) {
            placeName = "약속 장소";
        }

        // 2. 내 사용자 정보 가져오기
        android.content.SharedPreferences prefs =
                getSharedPreferences("DawagaPrefs", MODE_PRIVATE);

        myUserId = prefs.getString("userId", null);
        myNickname = prefs.getString("nickname", "나");

        if (getIntent().hasExtra("user_id")) {
            myUserId = getIntent().getStringExtra("user_id");
        }

        if (getIntent().hasExtra("nickname")) {
            myNickname = getIntent().getStringExtra("nickname");
        }

        // 3. 카카오맵 초기화
        KakaoMapSdk.init(
                this,
                BuildConfig.KAKAO_NATIVE_APP_KEY
        );

        setContentView(R.layout.activity_arrival_status);

        // 4. 화면 요소 연결
        mapView = findViewById(R.id.arrival_map_view);

        tvMemberCount = findViewById(R.id.tv_member_count);

        ImageButton btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());

        // 5. 친구 목록 RecyclerView
        RecyclerView recyclerView =
                findViewById(R.id.member_recycler_view);

        recyclerView.setLayoutManager(
                new LinearLayoutManager(this)
        );

        adapter = new MemberStatusAdapter(
                destinationLat,
                destinationLng,
                myUserId
        );

        recyclerView.setAdapter(adapter);

        // 6. Firebase 및 위치 권한 객체 생성
        firebaseManager = new DawagaFirebaseManager();

        permissionHelper = new LocationPermissionHelper(this);

        // 7. 지도 실행
        mapView.start(
                new MapLifeCycleCallback() {

                    @Override
                    public void onMapDestroy() {
                        Log.d(TAG, "지도 종료");
                    }

                    @Override
                    public void onMapError(Exception error) {
                        Log.e(TAG, "지도 오류", error);

                        Toast.makeText(
                                ArrivalStatusActivity.this,
                                "지도를 불러오지 못했습니다.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                },

                new KakaoMapReadyCallback() {

                    @Override
                    public void onMapReady(@NonNull KakaoMap map) {

                        addDestinationPin(
                                map,
                                destinationLat,
                                destinationLng,
                                placeName
                        );
                    }
                }
        );

        // 8. 친구들의 위치 실시간 수신
        listenRoomMembers();

        // 9. 내 위치 공유 객체 생성
        createLocationSession();
    }

    // 내 위치 공유 세션 생성
    private void createLocationSession() {

        if (myUserId == null || myUserId.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "사용자 정보가 없습니다.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        shareSession = new LocationShareSession(
                this,
                firebaseManager,
                roomCode,
                myUserId,
                myNickname,
                destinationLat,
                destinationLng,

                new LocationShareSession.Listener() {

                    @Override
                    public void onMyProgress(
                            double distanceM,
                            int etaMinutes
                    ) {

                        Log.d(TAG,
                                "남은 거리: " + Math.round(distanceM)
                                        + "m / 예상 시간: "
                                        + etaMinutes + "분"
                        );
                    }

                    @Override
                    public void onArrived() {

                        runOnUiThread(() -> {

                            Toast.makeText(
                                    ArrivalStatusActivity.this,
                                    "약속 장소에 도착했어요!",
                                    Toast.LENGTH_SHORT
                            ).show();

                        });
                    }

                    @Override
                    public void onPermissionMissing() {

                        runOnUiThread(() -> Toast.makeText(
                                ArrivalStatusActivity.this,
                                "위치 권한을 허용해 주세요.",
                                Toast.LENGTH_LONG
                        ).show());
                    }
                }
        );
    }

    // 위치 공유 시작
    private void startLocationSharingIfReady() {

        if (shareSession == null || shareSession.isArrived()) {
            return;
        }

        // 약속 10분 전부터 공유
        if (meetTimeMillis > 0 &&
                !LocationRules.isShareWindow(
                        System.currentTimeMillis(),
                        meetTimeMillis
                )) {

            Log.d(TAG, "아직 위치 공유 시작 전입니다.");
            return;
        }

        // 최초 권한 요청
        if (!permissionRequestAttempted) {

            permissionRequestAttempted = true;

            permissionHelper.request((granted, precise) -> {

                if (!granted) {

                    Toast.makeText(
                            this,
                            "위치 권한을 허용해 주세요.",
                            Toast.LENGTH_LONG
                    ).show();

                    return;
                }

                if (!precise) {

                    Toast.makeText(
                            this,
                            "정확한 위치 권한을 허용해 주세요.",
                            Toast.LENGTH_LONG
                    ).show();
                }

                shareSession.start();
            });

        } else if (LocationTracker.hasPermission(this)) {

            // 화면에 다시 돌아왔을 때 위치 공유 재시작
            shareSession.start();
        }
    }

    // 약속 장소 핀 표시
    private void addDestinationPin(
            KakaoMap map,
            double lat,
            double lng,
            String name
    ) {

        LatLng destination = LatLng.from(lat, lng);

        LabelStyles styles =
                map.getLabelManager().addLabelStyles(
                        LabelStyles.from(
                                LabelStyle.from(R.mipmap.ic_launcher)
                                        .setTextStyles(24, Color.BLACK)
                        )
                );

        LabelOptions options =
                LabelOptions.from(destination)
                        .setStyles(styles)
                        .setTexts(
                                new LabelTextBuilder().setTexts(name)
                        );

        LabelLayer layer =
                map.getLabelManager().getLayer();

        if (layer != null) {
            layer.addLabel(options);
        }

        // 카메라를 약속 장소로 이동
        map.moveCamera(
                CameraUpdateFactory.newCenterPosition(
                        destination,
                        16
                )
        );
    }

    // Firebase 친구 위치 실시간 수신
    private void listenRoomMembers() {

        firebaseManager.listenRoomMembers(
                roomCode,
                members -> {

                    List<Map<String, Object>> memberList =
                            new ArrayList<>();

                    for (Object item : members) {

                        if (!(item instanceof Map)) {
                            continue;
                        }

                        Map<String, Object> member =
                                new HashMap<>();

                        for (Map.Entry<?, ?> entry :
                                ((Map<?, ?>) item).entrySet()) {

                            if (entry.getKey() instanceof String) {

                                member.put(
                                        (String) entry.getKey(),
                                        entry.getValue()
                                );
                            }
                        }

                        memberList.add(member);
                    }

                    runOnUiThread(() -> {

                        // 친구 목록 갱신
                        adapter.updateMembers(memberList);

                        // 전체 인원 및 도착 인원 계산
                        int arrivedCount = 0;

                        for (Map<String, Object> member :
                                memberList) {

                            if ("ARRIVED".equals(
                                    member.get("status"))) {

                                arrivedCount++;
                            }
                        }

                        // 상단 도착 인원 표시
                        tvMemberCount.setText(
                                "도착 " + arrivedCount
                                        + " / " + memberList.size()
                                        + "명"
                        );

                        Log.d(TAG,
                                "실시간 멤버 수: "
                                        + memberList.size()
                        );
                    });
                }
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mapView != null) {
            mapView.resume();
        }

        startLocationSharingIfReady();
    }

    @Override
    protected void onPause() {

        if (shareSession != null) {
            shareSession.stop();
        }

        if (mapView != null) {
            mapView.pause();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        if (shareSession != null) {
            shareSession.stop();
        }

        if (firebaseManager != null) {
            firebaseManager.stopListening();
        }

        if (mapView != null) {
            mapView.finish();
        }

        super.onDestroy();
    }
}

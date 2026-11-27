package com.example.dawaga;

import com.example.dawaga.location.LocationShareSession;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.KakaoMapReadyCallback;
import com.kakao.vectormap.LatLng;
import com.kakao.vectormap.MapLifeCycleCallback;
import com.kakao.vectormap.MapView;
import com.kakao.vectormap.camera.CameraUpdateFactory;

import java.util.List;
import java.util.Map;

public class ArrivalStatusActivity extends AppCompatActivity {

    private static final String TAG = "ArrivalStatusActivity";

    private MapView mapView;
    private KakaoMap kakaoMap;

    private TextView tvMemberCount;
    private Button btnCloseRoom;

    private RecyclerView recyclerView;
    private MemberStatusAdapter adapter;

    private DawagaFirebaseManager firebaseManager;
    private LocationShareSession shareSession;

    private String roomCode;
    private String myUserId;
    private String myNickname;
    private String placeName;

    private double destinationLat;
    private double destinationLng;

    private boolean isRoomEnded = false;
    private boolean isHandlingRoomEnd = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arrival_status);

        // 전달받은 정보
        Intent intent = getIntent();

        roomCode = intent.getStringExtra("ROOM_CODE");
        destinationLat = intent.getDoubleExtra("DEST_LAT", 0);
        destinationLng = intent.getDoubleExtra("DEST_LNG", 0);
        placeName = intent.getStringExtra("PLACE_NAME");

        myUserId = intent.getStringExtra("user_id");
        myNickname = intent.getStringExtra("nickname");

        // 뷰 연결
        mapView = findViewById(R.id.arrival_map_view);
        tvMemberCount = findViewById(R.id.tv_member_count);
        btnCloseRoom = findViewById(R.id.btn_close_room);

        recyclerView = findViewById(R.id.member_recycler_view);

        // Firebase 초기화
        firebaseManager = new DawagaFirebaseManager();

        // 방장 전용 버튼은 처음에 숨김
        btnCloseRoom.setVisibility(View.GONE);

        // RecyclerView 설정
        recyclerView.setLayoutManager(
                new LinearLayoutManager(this)
        );

        adapter = new MemberStatusAdapter(
                destinationLat,
                destinationLng,
                myUserId
        );

        recyclerView.setAdapter(adapter);

        // 필수 정보 확인
        if (roomCode == null || myUserId == null) {
            Toast.makeText(
                    this,
                    "약속방 정보를 불러오지 못했습니다.",
                    Toast.LENGTH_SHORT
            ).show();

            finish();
            return;
        }

        // 방장 확인
        checkRoomHost();

        // 방 상태 실시간 감지
        listenRoomStatus();

        // 참여자 목록 실시간 감지
        listenRoomMembers();

        // 지도 시작
        startMap();

        // 약속 마감 버튼
        btnCloseRoom.setOnClickListener(v ->
                showCloseRoomDialog()
        );

        // 위치 공유 세션 생성
        createLocationSession();
    }

    // 방장 확인
    private void checkRoomHost() {

        firebaseManager.getRoomHostId(
                roomCode,
                new DawagaFirebaseManager.OnRoomHostListener() {

                    @Override
                    public void onSuccess(String hostId) {

                        if (isRoomEnded || isFinishing()) {
                            return;
                        }

                        if (myUserId.equals(hostId)) {
                            btnCloseRoom.setVisibility(View.VISIBLE);
                        } else {
                            btnCloseRoom.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "방장 확인 실패", e);
                    }
                }
        );
    }

    // 방 상태 실시간 감지
    private void listenRoomStatus() {

        firebaseManager.listenRoomStatus(
                roomCode,
                status -> handleRoomStatus(status)
        );
    }

    // 방 상태에 따른 화면 이동
    private void handleRoomStatus(String status) {

        if (isFinishing() || isDestroyed() || isRoomEnded) {
            return;
        }

        if ("CLOSED".equals(status)) {

            isRoomEnded = true;

            stopRoomActivity();

            Intent intent = new Intent(
                    ArrivalStatusActivity.this,
                    ArrivalResultActivity.class
            );

            intent.putExtra("ROOM_CODE", roomCode);

            startActivity(intent);
            finish();

        } else if ("EXPIRED".equals(status)) {

            isRoomEnded = true;

            stopRoomActivity();

            new AlertDialog.Builder(this)
                    .setTitle("약속 종료")
                    .setMessage("약속 시간이 지나 약속방이 종료되었습니다.")
                    .setCancelable(false)
                    .setPositiveButton("확인", (dialog, which) -> {

                        Intent intent = new Intent(
                                ArrivalStatusActivity.this,
                                MainActivity.class
                        );

                        intent.addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                        Intent.FLAG_ACTIVITY_NEW_TASK
                        );

                        startActivity(intent);
                        finish();
                    })
                    .show();
        }
    }

    // 약속 마감 확인창
    private void showCloseRoomDialog() {

        if (isRoomEnded || isFinishing()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("약속 마감")
                .setMessage(
                        "약속을 마감하시겠습니까?\n" +
                                "마감하면 모든 참여자의 도착 결과가 저장됩니다."
                )
                .setNegativeButton("취소", null)
                .setPositiveButton("마감하기", (dialog, which) -> {

                    btnCloseRoom.setEnabled(false);

                    firebaseManager.closeRoom(
                            roomCode,
                            myUserId,
                            new DawagaFirebaseManager.OnRoomClosedListener() {

                                @Override
                                public void onSuccess() {

                                    if (isFinishing() || isDestroyed()) {
                                        return;
                                    }

                                    Toast.makeText(
                                            ArrivalStatusActivity.this,
                                            "약속이 마감되었습니다.",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }

                                @Override
                                public void onFailure(Exception e) {

                                    if (isFinishing() || isDestroyed()) {
                                        return;
                                    }

                                    btnCloseRoom.setEnabled(true);

                                    Toast.makeText(
                                            ArrivalStatusActivity.this,
                                            "약속 마감에 실패했습니다.",
                                            Toast.LENGTH_SHORT
                                    ).show();

                                    Log.e(TAG, "약속 마감 실패", e);
                                }
                            }
                    );
                })
                .show();
    }

    // 참여자 목록 실시간 감지
    private void listenRoomMembers() {

        firebaseManager.listenRoomMembers(
                roomCode,
                members -> {

                    if (isRoomEnded || isFinishing()) {
                        return;
                    }

                    adapter.updateMembers(members);

                    if (tvMemberCount != null) {
                        tvMemberCount.setText(
                                "참여자 " + members.size() + "명"
                        );
                    }
                }
        );
    }

    // 위치 공유 세션 생성
    private void createLocationSession() {

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
                        Log.d(
                                TAG,
                                "현재 거리: " + distanceM +
                                        "m, 예상 도착: " + etaMinutes + "분"
                        );
                    }

                    @Override
                    public void onArrived() {
                        Log.d(TAG, "도착 처리 완료");
                    }

                    @Override
                    public void onPermissionMissing() {
                        Toast.makeText(
                                ArrivalStatusActivity.this,
                                "위치 권한을 확인해 주세요.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    // 지도 시작
    // 지도 시작
    private void startMap() {

        mapView.start(
                new MapLifeCycleCallback() {

                    @Override
                    public void onMapDestroy() {
                        Log.d(TAG, "지도 종료");
                    }

                    @Override
                    public void onMapError(Exception error) {
                        Log.e(TAG, "지도 오류", error);
                    }
                },

                new KakaoMapReadyCallback() {

                    @Override
                    public void onMapReady(KakaoMap kakaoMap) {

                        ArrivalStatusActivity.this.kakaoMap = kakaoMap;

                        kakaoMap.moveCamera(
                                CameraUpdateFactory.newCenterPosition(
                                        LatLng.from(
                                                destinationLat,
                                                destinationLng
                                        ),
                                        16
                                )
                        );
                    }

                    @Override
                    public LatLng getPosition() {
                        return LatLng.from(
                                destinationLat,
                                destinationLng
                        );
                    }

                    @Override
                    public int getZoomLevel() {
                        return 16;
                    }
                }
        );
    }

    // 위치 공유 시작
    private void startLocationSharingIfReady() {

        if (isRoomEnded || isFinishing() || isDestroyed()) {
            return;
        }

        if (shareSession != null) {
            shareSession.start();
        }
    }

    // 방 종료 시 모든 작업 중지
    private void stopRoomActivity() {

        if (shareSession != null) {
            shareSession.stop();
        }

        if (firebaseManager != null) {
            firebaseManager.stopListening();
        }

        if (btnCloseRoom != null) {
            btnCloseRoom.setEnabled(false);
            btnCloseRoom.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mapView != null) {
            mapView.resume();
        }

        if (!isRoomEnded) {
            startLocationSharingIfReady();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (shareSession != null) {
            shareSession.stop();
        }

        if (mapView != null) {
            mapView.pause();
        }
    }

    @Override
    protected void onDestroy() {

        isRoomEnded = true;

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

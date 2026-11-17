package com.example.dawaga;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
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
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private MapView mapView;
    private DawagaFirebaseManager firebaseManager;

    private static final String TAG = "SoyeonAccountTimer";
    private static final long TEN_MINUTES_MILLIS = 10 * 60 * 1000L;

    private SharedPreferences prefs;
    private CountDownTimer countDownTimer;

    private TextView tvAuthStatus, tvUserInfo, tvTimer;
    private EditText etNickname, etRoomCode;

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
        // [소연 담정] 계정 & 타이머 기능 초기화 호출
        initSoyeonAccountAndTimer();
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
    // [소연 담당] 계정 등록 수정, 방 코드 생성, 타이머 & 화면 전환
    private void initSoyeonAccountAndTimer() {
        tvAuthStatus = findViewById(R.id.tv_soyeon_auth_status);
        tvUserInfo = findViewById(R.id.tv_soyeon_user_info);
        etNickname = findViewById(R.id.et_soyeon_nickname);
        Button btnRegister = findViewById(R.id.btn_soyeon_register);
        Button btnRename = findViewById(R.id.btn_soyeon_rename);

        Button btnCreateRoom = findViewById(R.id.btn_soyeon_create_room);
        etRoomCode = findViewById(R.id.et_soyeon_room_code);
        Button btnJoinRoom = findViewById(R.id.btn_soyeon_join_room);

        Button btnFastTimer = findViewById(R.id.btn_soyeon_fast_timer);
        tvTimer = findViewById(R.id.tv_soyeon_timer);

        prefs = getSharedPreferences("DawagaPrefs", MODE_PRIVATE);

        // 기기에 저장된 기존 계정 불러오기
        loadSavedUserProfile();

        // 1. 닉네임 등록 (새 userId 발급 + SharedPreferences 저장)
        btnRegister.setOnClickListener(v -> {
            String nickname = etNickname.getText().toString().trim();
            if (nickname.isEmpty()) {
                tvUserInfo.setText("등록할 닉네임을 입력해주세요.");
                return;
            }

            firebaseManager.registerUser(nickname, new DawagaFirebaseManager.OnUserRegisteredListener() {
                @Override
                public void onSuccess(String userId, String savedNickname) {
                    prefs.edit()
                            .putString("userId", userId)
                            .putString("nickname", savedNickname)
                            .apply();

                    loadSavedUserProfile();
                    Log.d(TAG, "신규 등록 -> userId: " + userId + ", nickname: " + savedNickname);
                }

                @Override
                public void onFailure(Exception e) {
                    tvUserInfo.setText("등록 실패: " + e.getMessage());
                    Log.e(TAG, "등록 실패", e);
                }
            });
        });

        // 2. 닉네임 수정 (기존 userId 유지 + Firestore 및 SharedPreferences 닉네임만 변경)
        btnRename.setOnClickListener(v -> {
            String currentUserId = prefs.getString("userId", null);
            if (currentUserId == null) {
                tvUserInfo.setText("먼저 [1. 닉네임 등록]으로 계정을 생성해주세요.");
                return;
            }

            String newNickname = etNickname.getText().toString().trim();
            if (newNickname.isEmpty()) {
                tvUserInfo.setText("수정할 닉네임을 입력해주세요.");
                return;
            }

            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUserId)
                    .update("nickname", newNickname)
                    .addOnSuccessListener(aVoid -> {
                        prefs.edit().putString("nickname", newNickname).apply();
                        loadSavedUserProfile();
                        Log.d(TAG, "닉네임 수정 -> userId: " + currentUserId + " (유지), newNickname: " + newNickname);
                    })
                    .addOnFailureListener(e -> {
                        tvUserInfo.setText("닉네임 수정 실패: " + e.getMessage());
                        Log.e(TAG, "닉네임 수정 실패", e);
                    });
        });

        // 3. 방 코드 생성 (친구 초대용 코드 발급)
        btnCreateRoom.setOnClickListener(v -> {
            long defaultMeetTime = System.currentTimeMillis() + TEN_MINUTES_MILLIS;
            firebaseManager.createRoom("성신여대 모임", 37.5926, 127.0164, defaultMeetTime, new DawagaFirebaseManager.OnRoomCallback() {
                @Override
                public void onSuccess(Map<String, Object> roomData) {
                    String roomCode = (String) roomData.get("roomCode");
                    etRoomCode.setText(roomCode);
                    Log.d(TAG, "초대용 방 코드 생성 완료: " + roomCode);
                }

                @Override
                public void onFailure(Exception e) {
                    etRoomCode.setHint("방 생성 실패: " + e.getMessage());
                    Log.e(TAG, "방 생성 실패", e);
                }
            });
        });

        // 4. 방 코드 검증 (동작 없이 버튼 상태만 유지)
        btnJoinRoom.setOnClickListener(v -> {
            // 추후 방 코드 검증 연동 시 작성
        });

        // 5. 타이머 카운트다운 & 자동 화면 전환 (방 코드와 별개로 독립 실행)
        btnFastTimer.setOnClickListener(v -> {
            String code = etRoomCode.getText().toString().trim();
            if (code.isEmpty()) code = "TEST01";
            startCountdownTimer(5 * 1000L, code);
        });
    }

    private void loadSavedUserProfile() {
        String savedUserId = prefs.getString("userId", null);
        String savedNickname = prefs.getString("nickname", null);
        if (savedUserId != null && savedNickname != null) {
            tvAuthStatus.setText("익명 로그인 상태: 인증됨 (" + savedUserId + ")");
            tvUserInfo.setText("저장된 계정: " + savedNickname);
            etNickname.setText(savedNickname);
        } else {
            tvAuthStatus.setText("익명 로그인 상태: 미등록");
            tvUserInfo.setText("저장된 계정: 없음");
        }
    }

    private void startCountdownTimer(long durationMillis, String roomCode) {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(durationMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long minutes = (millisUntilFinished / 1000) / 60;
                long seconds = (millisUntilFinished / 1000) % 60;
                tvTimer.setText(String.format(Locale.getDefault(), "남은 시간: %02d:%02d", minutes, seconds));
            }

            @Override
            public void onFinish() {
                tvTimer.setText("00:00 - 타이머 종료! 화면 자동 전환");
                navigateToMapScreen(roomCode);
            }
        }.start();
    }

    private void navigateToMapScreen(String roomCode) {
        String userId = prefs.getString("userId", "user_guest");
        String nickname = prefs.getString("nickname", "게스트");

        Log.d(TAG, "자동 화면 전환 실행 -> roomCode: " + roomCode + ", userId: " + userId + ", nickname: " + nickname);

        Intent intent = new Intent(MainActivity.this, MainActivity.class);
        intent.putExtra("roomCode", roomCode);
        intent.putExtra("userId", userId);
        intent.putExtra("nickname", nickname);
        intent.putExtra("isAutoEntered", true);
        startActivity(intent);
        finish();
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
package com.example.dawaga;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private MapView mapView;
    private DawagaFirebaseManager firebaseManager;
    private String currentRoomCode = "TEST01"; // 기본 테스트 방 코드

    private static final String TAG = "SoyeonAccountTimer";
    private static final long TEN_MINUTES_MILLIS = 10 * 60 * 1000L; // 연산자 수정 완료

    private SharedPreferences prefs;
    private CountDownTimer countDownTimer;

    private TextView tvAuthStatus, tvUserInfo, tvTimer;
    private EditText etNickname, etRoomCode;

    // [추가] 홈 화면 약속 카드 영역
    private LinearLayout cardEmptyPromise;
    private LinearLayout layoutActivePromiseList;

    // [추가] 약속 만들기 화면 결과 수신 런처
    private final ActivityResultLauncher<Intent> createPromiseLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Intent data = result.getData();
                            String title = data.getStringExtra("promise_title");
                            String date = data.getStringExtra("promise_date");
                            String time = data.getStringExtra("promise_time");
                            String place = data.getStringExtra("promise_place");
                            String roomCode = data.getStringExtra("room_code");

                            addActivePromiseCard(title, date, time, place, roomCode);
                            if (data.getBooleanExtra("open_waiting", false)) {
                                openPromiseWaiting(data);
                            }
                        }
                    }
            );

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

        // 4. [예원 파트] 하단 테스트 버튼 (방 생성 및 시뮬레이터 이동) 바인딩 및 이벤트 연결
        Button btnCreateRoomMain = findViewById(R.id.btn_create_room);
        Button btnSimulateMove = findViewById(R.id.btn_simulate_move);

        if (btnCreateRoomMain != null) {
            btnCreateRoomMain.setOnClickListener(v -> {
                long meetTime = System.currentTimeMillis() + 3600000;
                firebaseManager.createRoom("성신여대 모임", 37.5926, 127.0164, meetTime, new DawagaFirebaseManager.OnRoomCallback() {
                    @Override
                    public void onSuccess(Map<String, Object> roomData) {
                        currentRoomCode = (String) roomData.get("roomCode");
                        Toast.makeText(MainActivity.this, "방 생성 성공! 코드: " + currentRoomCode, Toast.LENGTH_SHORT).show();
                        startListeningRoom(currentRoomCode);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(MainActivity.this, "방 생성 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        if (btnSimulateMove != null) {
            btnSimulateMove.setOnClickListener(v -> {
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

        // [추가] 홈 화면 약속 만들기 이동 연결
        initHomePromiseFlow();

        // 5. [소연 파트] 계정 & 타이머 기능 초기화 호출
        initSoyeonAccountAndTimer();
    }

    // [추가] 홈 화면 빈 상태 초기화 및 '새로운 약속 만들기' 화면 연결
    private void initHomePromiseFlow() {
        findViewById(R.id.card_hero_nickname).setOnClickListener(
                v -> startActivity(new Intent(this, ProfileActivity.class)));
        cardEmptyPromise = findViewById(R.id.card_empty_promise);
        layoutActivePromiseList = findViewById(R.id.layout_active_promise_list);

        if (cardEmptyPromise != null && layoutActivePromiseList != null) {
            if (layoutActivePromiseList.getChildCount() == 0) {
                cardEmptyPromise.setVisibility(View.VISIBLE);
                layoutActivePromiseList.setVisibility(View.GONE);
            }
        }

        LinearLayout cardCreatePromise = findViewById(R.id.card_create_promise);
        if (cardCreatePromise != null) {
            cardCreatePromise.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, CreatePromiseActivity.class);
                createPromiseLauncher.launch(intent);
            });
        }
    }

    private void openPromiseWaiting(Intent data) {
        startActivity(new Intent(this, PromiseWaitingActivity.class).putExtras(data));
    }

    // [추가] 생성/참여된 약속 카드를 홈 화면 '참여 중인 약속'에 추가
    private void addActivePromiseCard(String title, String date, String time, String place, String roomCode) {
        if (cardEmptyPromise != null) {
            cardEmptyPromise.setVisibility(View.GONE);
        }
        if (layoutActivePromiseList == null) {
            return;
        }
        layoutActivePromiseList.setVisibility(View.VISIBLE);

        float density = getResources().getDisplayMetrics().density;
        int padH = (int) (20 * density);
        int padV = (int) (18 * density);
        int marginBottom = (int) (12 * density);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_home_card);
        card.setPadding(padH, padV, padH, padV);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = marginBottom;
        card.setLayoutParams(cardParams);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(16f);
        tvTitle.setTextColor(Color.parseColor("#0F172A"));
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvBadge = new TextView(this);
        tvBadge.setText("대기 중");
        tvBadge.setTextSize(12f);
        tvBadge.setTextColor(Color.parseColor("#475569"));
        tvBadge.setTypeface(null, Typeface.BOLD);
        tvBadge.setBackgroundResource(R.drawable.bg_badge_soft_blue);
        tvBadge.setPadding((int) (14 * density), (int) (6 * density),
                (int) (14 * density), (int) (6 * density));

        topRow.addView(tvTitle);
        topRow.addView(tvBadge);

        TextView tvMeta = new TextView(this);
        tvMeta.setText(date + " " + time + " · " + place);
        tvMeta.setTextSize(13f);
        tvMeta.setTextColor(Color.parseColor("#64748B"));
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        metaParams.topMargin = (int) (8 * density);
        tvMeta.setLayoutParams(metaParams);

        card.addView(topRow);
        card.addView(tvMeta);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PromiseWaitingActivity.class);
            intent.putExtra("promise_title", title);
            intent.putExtra("promise_date", date);
            intent.putExtra("promise_time", time);
            intent.putExtra("promise_place", place);
            intent.putExtra("room_code", roomCode);
            startActivity(intent);
        });

        layoutActivePromiseList.addView(card, 0);
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

    // [소연 담당] 계정 등록 수정, 방 코드 생성, 타이머 & 화면 전환
    private void initSoyeonAccountAndTimer() {
        tvAuthStatus = findViewById(R.id.tv_soyeon_auth_status);
        tvUserInfo = findViewById(R.id.tv_soyeon_user_info);
        etNickname = findViewById(R.id.et_soyeon_nickname);
        Button btnRegister = findViewById(R.id.btn_soyeon_register);
        Button btnRename = findViewById(R.id.btn_soyeon_rename);

        Button btnCreateRoomSoyeon = findViewById(R.id.btn_soyeon_create_room);
        etRoomCode = findViewById(R.id.et_soyeon_room_code);
        Button btnJoinRoom = findViewById(R.id.btn_soyeon_join_room);

        Button btnFastTimer = findViewById(R.id.btn_soyeon_fast_timer);
        tvTimer = findViewById(R.id.tv_soyeon_timer);

        prefs = getSharedPreferences("DawagaPrefs", MODE_PRIVATE);

        // 기기에 저장된 기존 계정 불러오기
        loadSavedUserProfile();

        // 1. 닉네임 등록
        if (btnRegister != null) {
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
        }

        // 2. 닉네임 수정
        if (btnRename != null) {
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
                            Log.d(TAG, "닉네임 수정 완료");
                        })
                        .addOnFailureListener(e -> {
                            tvUserInfo.setText("닉네임 수정 실패: " + e.getMessage());
                        });
            });
        }

        // 3. 방 코드 생성
        if (btnCreateRoomSoyeon != null) {
            btnCreateRoomSoyeon.setOnClickListener(v -> {
                long defaultMeetTime = System.currentTimeMillis() + TEN_MINUTES_MILLIS;
                firebaseManager.createRoom("성신여대 모임", 37.5926, 127.0164, defaultMeetTime, new DawagaFirebaseManager.OnRoomCallback() {
                    @Override
                    public void onSuccess(Map<String, Object> roomData) {
                        String roomCode = (String) roomData.get("roomCode");
                        if (etRoomCode != null) {
                            etRoomCode.setText(roomCode);
                        }
                        currentRoomCode = roomCode;
                        startListeningRoom(roomCode);
                        Log.d(TAG, "초대용 방 코드 생성 완료: " + roomCode);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "방 생성 실패", e);
                    }
                });
            });
        }

        // 4. 방 코드 검증
        if (btnJoinRoom != null) {
            btnJoinRoom.setOnClickListener(v -> {
                // 추후 방 검증 로직 연결
            });
        }

        // 5. 타이머 카운트다운 & 자동 화면 전환
        if (btnFastTimer != null) {
            btnFastTimer.setOnClickListener(v -> {
                String code = (etRoomCode != null) ? etRoomCode.getText().toString().trim() : "";
                if (code.isEmpty()) code = "TEST01";
                startCountdownTimer(5000L, code); // 5초 시연용
            });
        }
    }

    private void loadSavedUserProfile() {
        String savedUserId = prefs.getString("userId", null);
        String savedNickname = prefs.getString("nickname", null);
        if (savedUserId != null && savedNickname != null) {
            if (tvAuthStatus != null) tvAuthStatus.setText("익명 로그인 상태: 인증됨 (" + savedUserId + ")");
            if (tvUserInfo != null) tvUserInfo.setText(savedNickname);
            if (etNickname != null) etNickname.setText(savedNickname);
        } else {
            if (tvAuthStatus != null) tvAuthStatus.setText("익명 로그인 상태: 미등록");
            if (tvUserInfo != null) tvUserInfo.setText("내 닉네임");
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
                if (tvTimer != null) {
                    tvTimer.setText(String.format(Locale.getDefault(), "남은 시간: %02d:%02d", minutes, seconds));
                }
            }

            @Override
            public void onFinish() {
                if (tvTimer != null) {
                    tvTimer.setText("00:00 - 타이머 종료! 화면 자동 전환");
                }
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
        if (prefs != null) loadSavedUserProfile();
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
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        if (firebaseManager != null) {
            firebaseManager.stopListening();
        }
    }
}

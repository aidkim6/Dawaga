package com.example.dawaga;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConfirmJoinActivity extends AppCompatActivity {

    private String roomCode;
    private String title;
    private String date;
    private String time;
    private String place;

    private TextView tvRoomCode;
    private TextView tvTitle;
    private TextView tvDatetimePlace;
    private LinearLayout layoutMembers;
    private Button btnJoin;

    private final List<String> memberNicknames = new ArrayList<>();
    private DawagaFirebaseManager firebaseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm_join);

        firebaseManager = new DawagaFirebaseManager();

        ImageButton btnBack = findViewById(R.id.btn_back_confirm_join);
        tvRoomCode = findViewById(R.id.tv_confirm_room_code);
        tvTitle = findViewById(R.id.tv_confirm_title);
        tvDatetimePlace = findViewById(R.id.tv_confirm_datetime_place);
        layoutMembers = findViewById(R.id.layout_confirm_members);
        btnJoin = findViewById(R.id.btn_confirm_join);

        roomCode = getIntent().getStringExtra("room_code");
        title = getIntent().getStringExtra("promise_title");
        date = getIntent().getStringExtra("promise_date");
        time = getIntent().getStringExtra("promise_time");
        place = getIntent().getStringExtra("promise_place");

        if (roomCode == null || roomCode.trim().isEmpty()) {
            Toast.makeText(this, "초대 코드가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        roomCode = roomCode.trim().toUpperCase(Locale.ROOT);
        bindRoomInfo();
        loadRoomDetailsAndMembers();

        // 상단 뒤로가기 버튼 -> 이전 화면으로 돌아가기
        btnBack.setOnClickListener(v -> finish());

        // 참여 확정 버튼 -> 유저 등록/위치 초기 등록 후 대기실(PromiseWaitingActivity)로 이동
        btnJoin.setOnClickListener(v -> confirmAndGoWaitingRoom());
    }

    private void bindRoomInfo() {
        String safeTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : "약속 정보 불러오는 중...";
        String safeDate = (date != null) ? date.trim() : "";
        String safeTime = (time != null) ? time.trim() : "";
        String safePlace = (place != null) ? place.trim() : "";

        tvRoomCode.setText("초대 코드 " + roomCode);
        tvTitle.setText(safeTitle);

        String dateTime = (safeDate + " " + safeTime).trim();
        String metaText;
        if (!dateTime.isEmpty() && !safePlace.isEmpty()) {
            metaText = dateTime + " · " + safePlace;
        } else if (!dateTime.isEmpty()) {
            metaText = dateTime;
        } else if (!safePlace.isEmpty()) {
            metaText = safePlace;
        } else {
            metaText = "일시 및 장소 정보를 확인 중입니다.";
        }
        tvDatetimePlace.setText(metaText);
    }

    private void loadRoomDetailsAndMembers() {
        ArrayList<String> initialMembers = getIntent().getStringArrayListExtra("participants");
        if (initialMembers != null && !initialMembers.isEmpty()) {
            memberNicknames.clear();
            memberNicknames.addAll(initialMembers);
            renderMemberChips();
        }

        // Firestore에서 실제 방 정보 조회
        firebaseManager.joinRoom(roomCode, new DawagaFirebaseManager.OnRoomCallback() {
            @Override
            public void onSuccess(Map<String, Object> roomData) {
                runOnUiThread(() -> {
                    applyRoomData(roomData);
                    bindRoomInfo();
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    if (title == null || title.trim().isEmpty()) {
                        title = "약속";
                        bindRoomInfo();
                    }
                });
            }
        });

        // Firestore 실시간 참여자 목록 수신
        firebaseManager.listenRoomMembers(roomCode, memberList -> {
            if (memberList == null) return;

            List<String> updated = new ArrayList<>();
            for (Object item : memberList) {
                if (item instanceof Map) {
                    Object nickObj = ((Map<?, ?>) item).get("nickname");
                    if (nickObj != null && !String.valueOf(nickObj).trim().isEmpty()) {
                        updated.add(String.valueOf(nickObj).trim());
                    }
                }
            }

            runOnUiThread(() -> {
                memberNicknames.clear();
                memberNicknames.addAll(updated);
                renderMemberChips();
            });
        });
    }

    private void applyRoomData(Map<String, Object> roomData) {
        if (roomData == null) return;

        Object roomNameObj = roomData.get("roomName");
        Object titleObj = roomData.get("title");
        if (roomNameObj != null && !String.valueOf(roomNameObj).trim().isEmpty()) {
            title = String.valueOf(roomNameObj).trim();
        } else if (titleObj != null && !String.valueOf(titleObj).trim().isEmpty()) {
            title = String.valueOf(titleObj).trim();
        }

        Object dateObj = roomData.get("date");
        Object timeObj = roomData.get("time");
        Object meetTimeObj = roomData.get("meetTime");

        if (dateObj != null && !String.valueOf(dateObj).trim().isEmpty()) {
            date = String.valueOf(dateObj).trim();
        }
        if (timeObj != null && !String.valueOf(timeObj).trim().isEmpty()) {
            time = String.valueOf(timeObj).trim();
        }

        if ((date == null || date.isEmpty() || time == null || time.isEmpty()) && meetTimeObj instanceof Number) {
            long millis = ((Number) meetTimeObj).longValue();
            if (millis > 0L) {
                Date d = new Date(millis);
                if (date == null || date.isEmpty()) {
                    date = new SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(d);
                }
                if (time == null || time.isEmpty()) {
                    time = new SimpleDateFormat("HH:mm", Locale.KOREAN).format(d);
                }
            }
        }

        Object placeObj = roomData.get("place");
        if (placeObj != null && !String.valueOf(placeObj).trim().isEmpty()) {
            place = String.valueOf(placeObj).trim();
        }
    }

    private void renderMemberChips() {
        layoutMembers.removeAllViews();

        float density = getResources().getDisplayMetrics().density;
        int padH = Math.round(14 * density);
        int padV = Math.round(8 * density);
        int marginEnd = Math.round(8 * density);

        if (memberNicknames.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("아직 참여 중인 멤버가 없습니다.");
            emptyView.setTextColor(Color.parseColor("#64748B"));
            emptyView.setTextSize(13f);
            layoutMembers.addView(emptyView);
            return;
        }

        for (String nickname : memberNicknames) {
            TextView chip = new TextView(this);
            chip.setText(nickname);
            chip.setTextColor(Color.parseColor("#2E74E8"));
            chip.setTextSize(13f);
            chip.setBackgroundResource(R.drawable.bg_badge_soft_blue);
            chip.setPadding(padH, padV, padH, padV);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.rightMargin = marginEnd;
            chip.setLayoutParams(params);
            layoutMembers.addView(chip);
        }
    }

    private void confirmAndGoWaitingRoom() {
        btnJoin.setEnabled(false);

        SharedPreferences prefs = getSharedPreferences("DawagaPrefs", MODE_PRIVATE);
        String savedUserId = prefs.getString("userId", null);
        String savedNickname = prefs.getString("nickname", "참여자");

        if (savedUserId != null && !savedUserId.isEmpty()) {
            saveJoinedPromiseToPrefs(prefs, roomCode, title, date, time, place);
            firebaseManager.updateMyLocation(roomCode, savedUserId, savedNickname, 0.0, 0.0, false);

            ArrayList<String> nextMembers = new ArrayList<>(memberNicknames);
            if (!nextMembers.contains(savedNickname)) {
                nextMembers.add(savedNickname);
            }

            Intent intent = new Intent(ConfirmJoinActivity.this, PromiseWaitingActivity.class);
            intent.putExtra("room_code", roomCode);
            intent.putExtra("user_id", savedUserId);
            intent.putExtra("nickname", savedNickname);
            intent.putExtra("promise_title", title);
            intent.putExtra("promise_date", date);
            intent.putExtra("promise_time", time);
            intent.putExtra("promise_place", place);
            intent.putStringArrayListExtra("participants", nextMembers);
            startActivity(intent);
            finish();
            return;
        }

        firebaseManager.registerUser(ConfirmJoinActivity.this, savedNickname, new DawagaFirebaseManager.OnUserRegisteredListener() {
            @Override
            public void onSuccess(String userId, String nickname) {
                prefs.edit()
                        .putString("userId", userId)
                        .putString("nickname", nickname)
                        .apply();
                saveJoinedPromiseToPrefs(prefs, roomCode, title, date, time, place);

                firebaseManager.updateMyLocation(roomCode, userId, nickname, 0.0, 0.0, false);

                runOnUiThread(() -> {
                    ArrayList<String> nextMembers = new ArrayList<>(memberNicknames);
                    if (!nextMembers.contains(nickname)) {
                        nextMembers.add(nickname);
                    }

                    Intent intent = new Intent(ConfirmJoinActivity.this, PromiseWaitingActivity.class);
                    intent.putExtra("room_code", roomCode);
                    intent.putExtra("user_id", userId);
                    intent.putExtra("nickname", nickname);
                    intent.putExtra("promise_title", title);
                    intent.putExtra("promise_date", date);
                    intent.putExtra("promise_time", time);
                    intent.putExtra("promise_place", place);
                    intent.putStringArrayListExtra("participants", nextMembers);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    btnJoin.setEnabled(true);
                    Toast.makeText(
                            ConfirmJoinActivity.this,
                            "약속 참여에 실패했습니다.",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }

    private void saveJoinedPromiseToPrefs(
            SharedPreferences prefs,
            String code, String pTitle, String pDate, String pTime, String pPlace
    ) {
        try {
            String raw = prefs.getString("saved_promises", "[]");
            JSONArray arr = new JSONArray(raw);
            JSONArray updated = new JSONArray();

            JSONObject newItem = new JSONObject();
            newItem.put("room_code", code);
            newItem.put("promise_title", pTitle != null ? pTitle : "약속");
            newItem.put("promise_date", pDate != null ? pDate : "");
            newItem.put("promise_time", pTime != null ? pTime : "");
            newItem.put("promise_place", pPlace != null ? pPlace : "");
            updated.put(newItem);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (!code.equalsIgnoreCase(obj.optString("room_code"))) {
                    updated.put(obj);
                }
            }
            prefs.edit().putString("saved_promises", updated.toString()).apply();
        } catch (Exception ignored) {
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
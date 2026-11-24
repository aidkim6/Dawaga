package com.example.dawaga;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConfirmJoinActivity extends AppCompatActivity {

    private String roomCode;
    private String roomName = "";
    private String dateText = "";
    private String timeText = "";
    private String placeText = "";
    private long meetTimeMillis = 0L;
    private double destLat = 0.0;
    private double destLng = 0.0;

    private TextView tvRoomName;
    private TextView tvRoomMeta;
    private TextView tvRoomBadge;
    private TextView tvMemberCount;
    private LinearLayout layoutMembers;
    private Button btnJoinThisPromise;

    private final List<String> currentMembers = new ArrayList<>();
    private DawagaFirebaseManager firebaseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm_join);

        firebaseManager = new DawagaFirebaseManager();

        ImageButton btnBack = findViewById(R.id.btn_back_confirm_join);
        tvRoomName = findViewById(R.id.tv_confirm_room_name);
        tvRoomMeta = findViewById(R.id.tv_confirm_room_meta);
        tvRoomBadge = findViewById(R.id.tv_confirm_room_code);
        tvMemberCount = findViewById(R.id.tv_confirm_member_count);
        layoutMembers = findViewById(R.id.layout_confirm_members);
        btnJoinThisPromise = findViewById(R.id.btn_join_this_promise);

        String rawCode = getIntent().getStringExtra("room_code");
        roomCode = rawCode != null ? rawCode.trim().toUpperCase(Locale.ROOT) : "";

        if (roomCode.isEmpty()) {
            Toast.makeText(this, "초대 코드가 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String passedName = getIntent().getStringExtra("room_name");
        if (passedName != null && !passedName.trim().isEmpty()) {
            roomName = passedName.trim();
        }
        String passedDate = getIntent().getStringExtra("promise_date");
        String passedTime = getIntent().getStringExtra("promise_time");
        String passedPlace = getIntent().getStringExtra("promise_place");
        if (passedDate != null) dateText = passedDate;
        if (passedTime != null) timeText = passedTime;
        if (passedPlace != null) placeText = passedPlace;

        meetTimeMillis = getIntent().getLongExtra("meet_time", 0L);
        destLat = getIntent().getDoubleExtra("dest_lat", 0.0);
        destLng = getIntent().getDoubleExtra("dest_lng", 0.0);

        formatMetaFromRoomFields();
        bindRoomInfo();

        loadRoomDetailsFromFirestore();
        loadMembersFromFirestore();

        btnBack.setOnClickListener(v -> finish());
        btnJoinThisPromise.setOnClickListener(v -> joinRoomAndGoWaiting());
    }

    private void loadRoomDetailsFromFirestore() {
        firebaseManager.joinRoom(roomCode, new DawagaFirebaseManager.OnRoomCallback() {
            @Override
            public void onSuccess(Map<String, Object> roomData) {
                Object nameObj = roomData.get("roomName");
                if (nameObj != null && !String.valueOf(nameObj).trim().isEmpty()) {
                    roomName = String.valueOf(nameObj).trim();
                }

                Object meetObj = roomData.get("meetTime");
                if (meetObj instanceof Number) {
                    meetTimeMillis = ((Number) meetObj).longValue();
                }

                Object latObj = roomData.get("destLatitude");
                if (latObj instanceof Number) {
                    destLat = ((Number) latObj).doubleValue();
                }

                Object lngObj = roomData.get("destLongitude");
                if (lngObj instanceof Number) {
                    destLng = ((Number) lngObj).doubleValue();
                }

                formatMetaFromRoomFields();
                bindRoomInfo();
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(ConfirmJoinActivity.this,
                        "약속 정보를 불러오지 못했습니다.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void formatMetaFromRoomFields() {
        if ((dateText.isEmpty() || timeText.isEmpty()) && meetTimeMillis > 0L) {
            Date dateObj = new Date(meetTimeMillis);
            if (dateText.isEmpty()) {
                dateText = new SimpleDateFormat("yyyy.MM.dd (E)", Locale.KOREAN).format(dateObj);
            }
            if (timeText.isEmpty()) {
                timeText = new SimpleDateFormat("HH:mm", Locale.KOREAN).format(dateObj);
            }
        }

        if (placeText.isEmpty() && (destLat != 0.0 || destLng != 0.0)) {
            placeText = String.format(Locale.KOREAN, "%.4f, %.4f", destLat, destLng);
        }
    }

    private void bindRoomInfo() {
        tvRoomName.setText(roomName.isEmpty() ? roomCode : roomName);

        StringBuilder metaBuilder = new StringBuilder();
        String dateTimePart = (dateText + " " + timeText).trim();
        if (!dateTimePart.isEmpty()) {
            metaBuilder.append(dateTimePart);
        }
        if (!placeText.isEmpty()) {
            if (metaBuilder.length() > 0) {
                metaBuilder.append(" · ");
            }
            metaBuilder.append(placeText);
        }
        tvRoomMeta.setText(metaBuilder.toString());
        tvRoomBadge.setText("초대받음");
    }

    private void loadMembersFromFirestore() {
        FirebaseFirestore.getInstance()
                .collection("rooms")
                .document(roomCode)
                .collection("members")
                .get()
                .addOnSuccessListener(snapshots -> {
                    currentMembers.clear();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String nick = doc.getString("nickname");
                        if (nick != null && !nick.trim().isEmpty()) {
                            currentMembers.add(nick.trim());
                        }
                    }
                    renderMemberChips();
                })
                .addOnFailureListener(e -> renderMemberChips());
    }

    private void renderMemberChips() {
        tvMemberCount.setText("참여자 " + currentMembers.size() + "명");
        layoutMembers.removeAllViews();

        if (currentMembers.isEmpty()) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int chipHeight = (int) (56 * density);
        int gap = (int) (10 * density);
        int colsPerRow = 4;

        LinearLayout currentRow = null;

        for (int i = 0; i < currentMembers.size(); i++) {
            if (i % colsPerRow == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setWeightSum(colsPerRow);

                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                if (i > 0) {
                    rowParams.topMargin = gap;
                }
                layoutMembers.addView(currentRow, rowParams);
            }

            TextView chip = new TextView(this);
            chip.setText(currentMembers.get(i));
            chip.setTextColor(Color.parseColor("#0F172A"));
            chip.setTextSize(14f);
            chip.setGravity(Gravity.CENTER);
            chip.setBackgroundResource(R.drawable.bg_icon_tile_soft);

            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    0,
                    chipHeight,
                    1f
            );
            if (i % colsPerRow > 0) {
                chipParams.leftMargin = gap;
            }
            currentRow.addView(chip, chipParams);
        }
    }

    private void joinRoomAndGoWaiting() {
        btnJoinThisPromise.setEnabled(false);

        SharedPreferences prefs = getSharedPreferences("dawaga_prefs", MODE_PRIVATE);
        String userId = prefs.getString("user_id", null);
        String nickname = prefs.getString("nickname", null);

        ArrayList<String> nextMembers = new ArrayList<>(currentMembers);

        if (userId != null && !userId.isEmpty() && nickname != null && !nickname.trim().isEmpty()) {
            String cleanNick = nickname.trim();
            firebaseManager.updateMyLocation(roomCode, userId, cleanNick, destLat, destLng, false);
            if (!nextMembers.contains(cleanNick)) {
                nextMembers.add(cleanNick);
            }
        }

        Intent intent = new Intent(ConfirmJoinActivity.this, PromiseWaitingActivity.class);
        intent.putExtra("promise_title", roomName);
        intent.putExtra("promise_date", dateText);
        intent.putExtra("promise_time", timeText);
        intent.putExtra("promise_place", placeText);
        intent.putExtra("room_code", roomCode);
        intent.putStringArrayListExtra("participants", nextMembers);
        startActivity(intent);
        finish();
    }
}
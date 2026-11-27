package com.example.dawaga;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.dawaga.location.LocationShareWaitingActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PromiseWaitingActivity extends AppCompatActivity {

    private TextView tvParticipantCount;
    private LinearLayout layoutParticipantList;
    private Button btnConfirmInfo;
    private final List<String> participants = new ArrayList<>();

    private String title;
    private String date;
    private String time;
    private String place;
    private String roomCode;

    private long meetTimeMillis = 0L;
    private double destLat = 37.5926;
    private double destLng = 127.0164;

    private DawagaFirebaseManager firebaseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_promise_waiting);

        firebaseManager = new DawagaFirebaseManager();

        ImageButton btnBack = findViewById(R.id.btn_back_waiting);
        TextView tvTitle = findViewById(R.id.tv_waiting_title);
        TextView tvDatetimePlace = findViewById(R.id.tv_waiting_datetime_place);
        tvParticipantCount = findViewById(R.id.tv_participant_count);
        layoutParticipantList = findViewById(R.id.layout_participant_list);
        btnConfirmInfo = findViewById(R.id.btn_confirm_promise_info);
        TextView btnMore = findViewById(R.id.btn_waiting_more);

        btnConfirmInfo.setText("약속 위치공유 타이머");

        title = getIntent().getStringExtra("promise_title");
        date = getIntent().getStringExtra("promise_date");
        time = getIntent().getStringExtra("promise_time");
        place = getIntent().getStringExtra("promise_place");
        roomCode = getIntent().getStringExtra("room_code");
        meetTimeMillis = getIntent().getLongExtra("meet_time", 0L);
        destLat = getIntent().getDoubleExtra("dest_lat", 37.5926);
        destLng = getIntent().getDoubleExtra("dest_lng", 127.0164);

        if (title == null || title.isEmpty()) title = "새 약속";
        if (date == null) date = "";
        if (time == null) time = "";
        if (place == null || place.isEmpty()) place = "장소 미정";
        if (roomCode == null || roomCode.isEmpty()) roomCode = "ABC123";

        tvTitle.setText(title);
        String meta = (date + " " + time).trim();
        tvDatetimePlace.setText(meta.isEmpty() ? place : meta + " · " + place);

        ArrayList<String> incomingMembers = getIntent().getStringArrayListExtra("participants");
        if (incomingMembers != null && !incomingMembers.isEmpty()) {
            participants.addAll(incomingMembers);
        } else {
            participants.add("나");
        }
        renderParticipants();

        loadRoomMeta();
        startListeningMembers();

        btnBack.setOnClickListener(v -> finish());
        btnMore.setOnClickListener(this::showPromiseSettingsPopup);
        btnConfirmInfo.setOnClickListener(v -> openLocationShareWaiting());
    }

    private void showPromiseSettingsPopup(View anchor) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_promise_settings, null);
        float density = getResources().getDisplayMetrics().density;
        int popupWidth = (int) (180 * density);

        PopupWindow popupWindow = new PopupWindow(
                popupView,
                popupWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(8 * density);

        Button tvShowPromiseInfo = popupView.findViewById(R.id.tv_show_promise_info);
        Button btnCopyPromiseCode = popupView.findViewById(R.id.btn_copy_promise_code);

        tvShowPromiseInfo.setOnClickListener(v -> {
            popupWindow.dismiss();
            showPromiseDetailDialog();
        });

        btnCopyPromiseCode.setOnClickListener(v -> {
            popupWindow.dismiss();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("invite_code", roomCode));
                Toast.makeText(this, "초대 코드(" + roomCode + ")가 복사되었어요.", Toast.LENGTH_SHORT).show();
            }
        });

        popupWindow.showAsDropDown(anchor, -(popupWidth - anchor.getWidth()), 0);
    }

    private void loadRoomMeta() {
        if (roomCode == null || roomCode.isEmpty()) return;
        firebaseManager.joinRoom(roomCode, new DawagaFirebaseManager.OnRoomCallback() {
            @Override
            public void onSuccess(Map<String, Object> roomData) {
                if (roomData == null) return;
                if (roomData.get("meetTime") instanceof Number) {
                    meetTimeMillis = ((Number) roomData.get("meetTime")).longValue();
                }
                if (roomData.get("destLatitude") instanceof Number) {
                    destLat = ((Number) roomData.get("destLatitude")).doubleValue();
                }
                if (roomData.get("destLongitude") instanceof Number) {
                    destLng = ((Number) roomData.get("destLongitude")).doubleValue();
                }
                if (roomData.get("place") != null) {
                    String p = String.valueOf(roomData.get("place")).trim();
                    if (!p.isEmpty()) place = p;
                }
            }

            @Override
            public void onFailure(Exception e) {
            }
        });
    }

    private void openLocationShareWaiting() {
        if (meetTimeMillis > 0L) {
            LocationShareWaitingActivity.start(this, roomCode, meetTimeMillis, destLat, destLng, place);
            return;
        }

        btnConfirmInfo.setEnabled(false);
        firebaseManager.joinRoom(roomCode, new DawagaFirebaseManager.OnRoomCallback() {
            @Override
            public void onSuccess(Map<String, Object> roomData) {
                runOnUiThread(() -> {
                    btnConfirmInfo.setEnabled(true);
                    long mTime = (roomData != null && roomData.get("meetTime") instanceof Number)
                            ? ((Number) roomData.get("meetTime")).longValue()
                            : System.currentTimeMillis() + (15 * 60 * 1000L);
                    double lat = (roomData != null && roomData.get("destLat") instanceof Number)
                            ? ((Number) roomData.get("destLatitude")).doubleValue()
                            : destLat;
                    double lng = (roomData != null && roomData.get("destLng") instanceof Number)
                            ? ((Number) roomData.get("destLongitude")).doubleValue()
                            : destLng;
                    String pName = (roomData != null && roomData.get("place") != null)
                            ? String.valueOf(roomData.get("place")).trim()
                            : place;
                    if (pName.isEmpty()) pName = place;

                    LocationShareWaitingActivity.start(
                            PromiseWaitingActivity.this, roomCode, mTime, lat, lng, pName
                    );
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    btnConfirmInfo.setEnabled(true);
                    Toast.makeText(
                            PromiseWaitingActivity.this,
                            "약속 정보를 불러오지 못했어요. 다시 시도해주세요.",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }

    private void startListeningMembers() {
        if (roomCode == null || roomCode.isEmpty()) return;

        firebaseManager.listenRoomMembers(roomCode, memberList -> {
            if (memberList == null) return;

            participants.clear();

            for (Object item : memberList) {
                if (item instanceof Map) {
                    Object nickObj = ((Map<?, ?>) item).get("nickname");

                    if (nickObj != null) {
                        participants.add(String.valueOf(nickObj));
                    }
                }
            }

            runOnUiThread(this::renderParticipants);
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firebaseManager != null) {
            firebaseManager.stopListening();
        }
    }

    private void renderParticipants() {
        tvParticipantCount.setText("참여자 " + participants.size() + "명");
        layoutParticipantList.removeAllViews();

        float density = getResources().getDisplayMetrics().density;
        int padH = (int) (20 * density);
        int padV = (int) (16 * density);
        int marginBottom = (int) (10 * density);

        for (String name : participants) {
            TextView itemView = new TextView(this);
            itemView.setText(name);
            itemView.setTextSize(15f);
            itemView.setTextColor(Color.parseColor("#0F172A"));
            if (name.contains("나")) {
                itemView.setTypeface(null, Typeface.BOLD);
            }
            itemView.setBackgroundResource(R.drawable.bg_home_card);
            itemView.setPadding(padH, padV, padH, padV);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.bottomMargin = marginBottom;
            itemView.setLayoutParams(params);

            layoutParticipantList.addView(itemView);
        }
    }

    private void showPromiseDetailDialog() {
        String infoMessage =
                "• 약속 이름: " + title + "\n" +
                        "• 일시: " + (date + " " + time).trim() + "\n" +
                        "• 장소: " + place + "\n" +
                        "• 초대 코드: " + roomCode + "\n\n" +
                        "약속 시간 10분 전부터 실시간 위치 공유가 시작됩니다.";

        new AlertDialog.Builder(this)
                .setTitle("약속 확인")
                .setMessage(infoMessage)
                .setPositiveButton("확인", null)
                .show();
    }
}
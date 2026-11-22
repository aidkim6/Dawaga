package com.example.dawaga;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class PromiseWaitingActivity extends AppCompatActivity {

    private TextView tvParticipantCount;
    private LinearLayout layoutParticipantList;
    private final List<String> participants = new ArrayList<>();

    private String title;
    private String date;
    private String time;
    private String place;
    private String roomCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_promise_waiting);

        ImageButton btnBack = findViewById(R.id.btn_back_waiting);
        TextView tvTitle = findViewById(R.id.tv_waiting_title);
        TextView tvDatetimePlace = findViewById(R.id.tv_waiting_datetime_place);
        tvParticipantCount = findViewById(R.id.tv_participant_count);
        layoutParticipantList = findViewById(R.id.layout_participant_list);
        Button btnConfirmInfo = findViewById(R.id.btn_confirm_promise_info);
        TextView btnMore = findViewById(R.id.btn_waiting_more);

        // 홈 화면 카드에서 전달받은 약속 데이터 세팅
        title = getIntent().getStringExtra("promise_title");
        date = getIntent().getStringExtra("promise_date");
        time = getIntent().getStringExtra("promise_time");
        place = getIntent().getStringExtra("promise_place");
        roomCode = getIntent().getStringExtra("room_code");

        if (title == null) title = "새 약속";
        if (date == null) date = "오늘";
        if (time == null) time = "19:00";
        if (place == null) place = "장소 미정";
        if (roomCode == null) roomCode = "ABC123";

        tvTitle.setText(title);
        tvDatetimePlace.setText(date + " " + time + " · " + place);

        // 처음 방 생성 시에는 나(방장) 1명으로 시작
        ArrayList<String> incomingMembers = getIntent().getStringArrayListExtra("participants");
        if (incomingMembers != null && !incomingMembers.isEmpty()) {
            participants.addAll(incomingMembers);
        } else {
            participants.add("나 · 방장");
        }
        renderParticipants();

        // 좌측 상단 '<' 화살표 클릭 시 이전 화면(홈)으로 복귀
        btnBack.setOnClickListener(v -> finish());

        // 우측 상단 '•••' 클릭 시 초대 코드 안내
        btnMore.setOnClickListener(v -> showPromiseDetailDialog());

        // 하단 '약속 정보 확인' 버튼 클릭
        btnConfirmInfo.setOnClickListener(v -> showPromiseDetailDialog());
    }

    /**
     * 방 코드로 새로운 참여자가 입장했을 때 호출하면 인원수가 1명씩 증가하며 카드가 추가됨
     */
    public void addParticipant(String memberName) {
        participants.add(memberName);
        renderParticipants();
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
                        "• 일시: " + date + " " + time + "\n" +
                        "• 장소: " + place + "\n" +
                        "• 초대 코드: " + roomCode + "\n\n" +
                        "약속 시간 10분 전부터 실시간 위치 공유가 시작됩니다.";

        new AlertDialog.Builder(this)
                .setTitle("약속 정보 확인")
                .setMessage(infoMessage)
                .setPositiveButton("확인", null)
                .show();
    }
}
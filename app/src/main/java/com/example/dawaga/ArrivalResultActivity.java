
package com.example.dawaga;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArrivalResultActivity extends AppCompatActivity {

    private DawagaFirebaseManager firebaseManager;
    private ArrivalRankAdapter adapter;

    private TextView tvResultSummary;
    private String roomCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arrival_result);

        // 이전 화면에서 전달받은 방 코드
        roomCode = getIntent().getStringExtra("ROOM_CODE");

        if (roomCode == null || roomCode.isEmpty()) {
            Toast.makeText(
                    this,
                    "약속 정보를 찾을 수 없습니다.",
                    Toast.LENGTH_SHORT
            ).show();

            finish();
            return;
        }

        // 순위 목록 연결
        RecyclerView recyclerView =
                findViewById(R.id.rv_final_ranking);

        recyclerView.setLayoutManager(
                new LinearLayoutManager(this)
        );

        adapter = new ArrivalRankAdapter();
        recyclerView.setAdapter(adapter);

        tvResultSummary = findViewById(R.id.tv_result_summary);

        // Firebase 매니저 생성
        firebaseManager = new DawagaFirebaseManager();

        // 홈으로 돌아가기 버튼
        Button btnHome = findViewById(R.id.btn_result_home);

        btnHome.setOnClickListener(v -> goHome());

        // 약속 종료 확인 버튼
        Button btnEndMeeting = findViewById(R.id.btn_end_meeting);

        btnEndMeeting.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("약속 종료")
                    .setMessage("약속을 종료하고 홈으로 돌아갈까요?")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("확인", (dialog, which) -> goHome())
                    .show();
        });

        // Firestore에서 실시간 도착 정보 받기
        listenArrivalResults();
    }

    private void listenArrivalResults() {

        firebaseManager.listenRoomMembers(roomCode, members -> {

            List<Map<String, Object>> memberList =
                    new ArrayList<>();

            // Firestore에서 받은 멤버 데이터를 Map으로 변환
            for (Object item : members) {

                if (item instanceof Map) {

                    Map<?, ?> rawData = (Map<?, ?>) item;
                    Map<String, Object> memberData =
                            new HashMap<>();

                    for (Map.Entry<?, ?> entry : rawData.entrySet()) {

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

            // 화면 갱신
            runOnUiThread(() -> {

                adapter.updateMembers(memberList);

                int totalCount = memberList.size();
                int arrivedCount = 0;

                for (Map<String, Object> member : memberList) {
                    if ("ARRIVED".equals(member.get("status"))) {
                        arrivedCount++;
                    }
                }

                tvResultSummary.setText(
                        "참여자 " + totalCount
                                + "명 · 도착 완료 " + arrivedCount + "명"
                );
            });
        });
    }

    // 메인 화면으로 이동
    private void goHome() {

        Intent intent = new Intent(
                ArrivalResultActivity.this,
                MainActivity.class
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (firebaseManager != null) {
            firebaseManager.stopListening();
        }
    }
}

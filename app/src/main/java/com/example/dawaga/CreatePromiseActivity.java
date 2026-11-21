package com.example.dawaga;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

public class CreatePromiseActivity extends AppCompatActivity {

    private EditText etPromiseName;
    private TextView tvSelectedDate;
    private TextView tvSelectedTime;

    private String selectedDateStr = "";
    private String selectedTimeStr = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_promise);

        ImageButton btnBack = findViewById(R.id.btn_back_create);
        etPromiseName = findViewById(R.id.et_promise_name);
        LinearLayout boxSelectDate = findViewById(R.id.box_select_date);
        LinearLayout boxSelectTime = findViewById(R.id.box_select_time);
        tvSelectedDate = findViewById(R.id.tv_selected_date);
        tvSelectedTime = findViewById(R.id.tv_selected_time);
        Button btnNextPlace = findViewById(R.id.btn_next_place);

        // 좌측 상단 '<' 화살표 클릭 시 이전 화면(홈)으로 복귀
        btnBack.setOnClickListener(v -> finish());

        // 약속 날짜 선택 (DatePickerDialog)
        boxSelectDate.setOnClickListener(v -> showDatePicker());

        // 약속 시간 선택 (TimePickerDialog)
        boxSelectTime.setOnClickListener(v -> showTimePicker());

        // 하단 '다음: 장소 선택' 버튼 클릭
        btnNextPlace.setOnClickListener(v -> handleNextPlaceStep());
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(year, month, dayOfMonth);
                    String[] dayNames = {"일", "월", "화", "수", "목", "금", "토"};
                    String dayOfWeek = dayNames[picked.get(Calendar.DAY_OF_WEEK) - 1];

                    selectedDateStr = String.format(Locale.KOREA, "%d.%02d.%02d (%s)",
                            year, month + 1, dayOfMonth, dayOfWeek);
                    tvSelectedDate.setText(selectedDateStr);
                    tvSelectedDate.setTextColor(Color.parseColor("#0F172A"));
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        // 오늘 이전 날짜 선택 방지
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    private void showTimePicker() {
        Calendar cal = Calendar.getInstance();
        TimePickerDialog dialog = new TimePickerDialog(
                this,
                android.R.style.Theme_Holo_Light_Dialog_NoActionBar,
                (view, hourOfDay, minute) -> {
                    selectedTimeStr = String.format(Locale.KOREA, "%02d:%02d", hourOfDay, minute);
                    tvSelectedTime.setText(selectedTimeStr);
                    tvSelectedTime.setTextColor(Color.parseColor("#0F172A"));
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
        );
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private void handleNextPlaceStep() {
        String title = etPromiseName.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "약속 이름을 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedDateStr.isEmpty()) {
            Toast.makeText(this, "약속 날짜를 선택해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedTimeStr.isEmpty()) {
            Toast.makeText(this, "약속 시간을 선택해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 장소 선택 화면이 연동되기 전까지 장소명을 입력받아 약속 생성을 완료하는 다이얼로그
        // (추후 장소 선택 Activity가 완성되면 해당 Activity로 Intent 연결)
        EditText etPlaceInput = new EditText(this);
        etPlaceInput.setHint("예: 성신여대 정문");
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        etPlaceInput.setPadding(pad, pad / 2, pad, pad / 2);

        new AlertDialog.Builder(this)
                .setTitle("약속 장소 입력")
                .setMessage("만날 장소를 입력하면 약속이 생성됩니다.")
                .setView(etPlaceInput)
                .setPositiveButton("약속 생성 완료", (dialog, which) -> {
                    String place = etPlaceInput.getText().toString().trim();
                    if (place.isEmpty()) {
                        place = "장소 미정";
                    }
                    String roomCode = generateRoomCode();

                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("promise_title", title);
                    resultIntent.putExtra("promise_date", selectedDateStr);
                    resultIntent.putExtra("promise_time", selectedTimeStr);
                    resultIntent.putExtra("promise_place", place);
                    resultIntent.putExtra("room_code", roomCode);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
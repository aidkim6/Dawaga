package com.example.dawaga;

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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.util.Calendar;
import java.util.Locale;

public class CreatePromiseActivity extends AppCompatActivity {

    private EditText etPromiseName;
    private TextView tvSelectedDate;
    private TextView tvSelectedTime;

    private String selectedDateStr = "";
    private String selectedTimeStr = "";
    private final Calendar meeting = Calendar.getInstance();
    private final ActivityResultLauncher<Intent> setupLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    setResult(RESULT_OK, result.getData());
                    finish();
                }
            });

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
        if (savedInstanceState != null) {
            selectedDateStr = savedInstanceState.getString("date", "");
            selectedTimeStr = savedInstanceState.getString("time", "");
            meeting.setTimeInMillis(savedInstanceState.getLong("meeting"));
            if (!selectedDateStr.isEmpty()) tvSelectedDate.setText(selectedDateStr);
            if (!selectedTimeStr.isEmpty()) tvSelectedTime.setText(selectedTimeStr);
        }

        // 좌측 상단 '<' 화살표 클릭 시 이전 화면(홈)으로 복귀
        btnBack.setOnClickListener(v -> finish());

        // 약속 날짜 선택 (DatePickerDialog)
        boxSelectDate.setOnClickListener(v -> showDatePicker());

        // 약속 시간 선택 (TimePickerDialog)
        boxSelectTime.setOnClickListener(v -> showTimePicker());

        // 하단 '다음: 장소 선택' 버튼 클릭
        btnNextPlace.setOnClickListener(v -> handleNextPlaceStep());
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("date", selectedDateStr);
        state.putString("time", selectedTimeStr);
        state.putLong("meeting", meeting.getTimeInMillis());
    }

    private void showDatePicker() {
        Calendar cal = meeting;
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.set(year, month, dayOfMonth);
                    meeting.set(year, month, dayOfMonth);
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
        Calendar cal = meeting;
        TimePickerDialog dialog = new TimePickerDialog(
                this,
                android.R.style.Theme_Holo_Light_Dialog_NoActionBar,
                (view, hourOfDay, minute) -> {
                    meeting.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    meeting.set(Calendar.MINUTE, minute);
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

        meeting.set(Calendar.SECOND, 0);
        meeting.set(Calendar.MILLISECOND, 0);
        setupLauncher.launch(new Intent(this, PromiseSetupActivity.class)
                .putExtra("promise_title", title)
                .putExtra("promise_date", selectedDateStr)
                .putExtra("promise_time", selectedTimeStr)
                .putExtra("meet_time", meeting.getTimeInMillis()));
    }
}

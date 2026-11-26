package com.example.dawaga;

import android.content.Intent;
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
            if (!selectedDateStr.isEmpty()) showSelection(tvSelectedDate, selectedDateStr);
            if (!selectedTimeStr.isEmpty()) showSelection(tvSelectedTime, selectedTimeStr);
        }

        getSupportFragmentManager().setFragmentResultListener(PromiseDatePicker.TAG, this, (key, result) ->
                applyDate(result.getInt("year"), result.getInt("month"), result.getInt("day")));
        getSupportFragmentManager().setFragmentResultListener(PromiseTimePicker.TAG, this, (key, result) ->
                applyTime(result.getInt("hour"), result.getInt("minute")));

        // 좌측 상단 '<' 화살표 클릭 시 이전 화면(홈)으로 복귀
        btnBack.setOnClickListener(v -> finish());

        boxSelectDate.setOnClickListener(v -> showDatePicker());

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
        if (getSupportFragmentManager().findFragmentByTag(PromiseDatePicker.TAG) != null) return;
        PromiseDatePicker.newInstance(meeting).showNow(getSupportFragmentManager(), PromiseDatePicker.TAG);
    }

    private void applyDate(int year, int month, int day) {
        meeting.set(year, month - 1, day);
        String[] dayNames = {"일", "월", "화", "수", "목", "금", "토"};
        selectedDateStr = String.format(Locale.KOREA, "%d.%02d.%02d (%s)",
                year, month, day, dayNames[meeting.get(Calendar.DAY_OF_WEEK) - 1]);
        showSelection(tvSelectedDate, selectedDateStr);
    }

    private void showTimePicker() {
        if (getSupportFragmentManager().findFragmentByTag(PromiseTimePicker.TAG) != null) return;
        PromiseTimePicker.newInstance(meeting.get(Calendar.HOUR_OF_DAY), meeting.get(Calendar.MINUTE))
                .showNow(getSupportFragmentManager(), PromiseTimePicker.TAG);
    }

    private void applyTime(int hour, int minute) {
        meeting.set(Calendar.HOUR_OF_DAY, hour);
        meeting.set(Calendar.MINUTE, minute);
        selectedTimeStr = String.format(Locale.KOREA, "%02d:%02d", hour, minute);
        showSelection(tvSelectedTime, selectedTimeStr);
    }

    private void showSelection(TextView field, String value) {
        field.setText(value);
        field.setTextColor(getColor(R.color.text_primary));
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

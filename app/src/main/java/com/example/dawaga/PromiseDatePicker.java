package com.example.dawaga;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import java.util.Calendar;
import java.util.Locale;

public class PromiseDatePicker extends DialogFragment {
    static final String TAG = "promiseDatePicker";
    private final Calendar today = Calendar.getInstance();
    private NumberPicker year;
    private NumberPicker month;
    private NumberPicker day;

    static PromiseDatePicker newInstance(Calendar date) {
        PromiseDatePicker picker = new PromiseDatePicker();
        picker.setArguments(values(date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1,
                date.get(Calendar.DAY_OF_MONTH)));
        return picker;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedState) {
        Bundle initial = savedState == null ? requireArguments() : savedState;
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_promise_date, null);
        year = content.findViewById(R.id.picker_year);
        month = content.findViewById(R.id.picker_month);
        day = content.findViewById(R.id.picker_day);
        styleWheel(year);
        styleWheel(month);
        styleWheel(day);
        month.setFormatter(value -> String.format(Locale.KOREA, "%02d", value));
        day.setFormatter(value -> String.format(Locale.KOREA, "%02d", value));
        int currentYear = today.get(Calendar.YEAR);
        setRange(year, currentYear, Math.max(2100, currentYear + 1), initial.getInt("year"));
        updateMonthAndDay(initial.getInt("month"), initial.getInt("day"));
        year.setOnValueChangedListener((view, oldValue, newValue) ->
                updateMonthAndDay(month.getValue(), day.getValue()));
        month.setOnValueChangedListener((view, oldValue, newValue) ->
                updateDay(day.getValue()));

        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(content);
        content.findViewById(R.id.picker_date_cancel).setOnClickListener(view -> dismiss());
        content.findViewById(R.id.picker_date_confirm).setOnClickListener(view -> {
            getParentFragmentManager().setFragmentResult(TAG, selection());
            dismiss();
        });
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = requireDialog().getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.45f);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putAll(selection());
    }

    private void updateMonthAndDay(int selectedMonth, int selectedDay) {
        int firstMonth = year.getValue() == today.get(Calendar.YEAR) ? today.get(Calendar.MONTH) + 1 : 1;
        setRange(month, firstMonth, 12, selectedMonth);
        updateDay(selectedDay);
    }

    private void updateDay(int selectedDay) {
        Calendar selectedMonth = new java.util.GregorianCalendar(year.getValue(), month.getValue() - 1, 1);
        boolean isCurrentMonth = year.getValue() == today.get(Calendar.YEAR)
                && month.getValue() == today.get(Calendar.MONTH) + 1;
        setRange(day, isCurrentMonth ? today.get(Calendar.DAY_OF_MONTH) : 1,
                selectedMonth.getActualMaximum(Calendar.DAY_OF_MONTH), selectedDay);
    }

    private static void setRange(NumberPicker picker, int first, int last, int selected) {
        picker.setWrapSelectorWheel(false);
        picker.setMinValue(0);
        picker.setMaxValue(last);
        picker.setMinValue(first);
        picker.setValue(Math.max(first, Math.min(last, selected)));
    }

    private void styleWheel(NumberPicker picker) {
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            picker.setSelectionDividerHeight(0);
            picker.setTextColor(requireContext().getColor(R.color.text_primary));
            picker.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 24,
                    getResources().getDisplayMetrics()));
        }
    }

    private Bundle selection() {
        return values(year.getValue(), month.getValue(), day.getValue());
    }

    private static Bundle values(int year, int month, int day) {
        Bundle values = new Bundle();
        values.putInt("year", year);
        values.putInt("month", month);
        values.putInt("day", day);
        return values;
    }
}

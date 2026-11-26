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

import java.util.Locale;

public class PromiseTimePicker extends DialogFragment {
    static final String TAG = "promiseTimePicker";

    private NumberPicker hour;
    private NumberPicker minute;

    static PromiseTimePicker newInstance(int hour, int minute) {
        PromiseTimePicker picker = new PromiseTimePicker();
        picker.setArguments(values(hour, minute));
        return picker;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedState) {
        Bundle initial = savedState == null ? requireArguments() : savedState;
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_promise_time, null);
        hour = content.findViewById(R.id.picker_hour);
        minute = content.findViewById(R.id.picker_minute);
        configure(hour, 23, initial.getInt("hour"));
        configure(minute, 59, initial.getInt("minute"));
        Dialog dialog = new Dialog(requireContext());
        dialog.setContentView(content);
        content.findViewById(R.id.picker_time_cancel).setOnClickListener(view -> dismiss());
        content.findViewById(R.id.picker_time_confirm).setOnClickListener(view -> {
            getParentFragmentManager().setFragmentResult(TAG, values(hour.getValue(), minute.getValue()));
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
        outState.putAll(values(hour.getValue(), minute.getValue()));
    }

    private void configure(NumberPicker picker, int max, int value) {
        picker.setMinValue(0);
        picker.setMaxValue(max);
        String[] labels = new String[max + 1];
        for (int index = 0; index <= max; index++) {
            labels[index] = String.format(Locale.KOREA, "%02d", index);
        }
        picker.setDisplayedValues(labels);
        picker.setWrapSelectorWheel(true);
        picker.setValue(value);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            picker.setSelectionDividerHeight(0);
            picker.setTextColor(requireContext().getColor(R.color.text_primary));
            picker.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 24,
                    getResources().getDisplayMetrics()));
        }
    }

    private static Bundle values(int hour, int minute) {
        Bundle values = new Bundle();
        values.putInt("hour", hour);
        values.putInt("minute", minute);
        return values;
    }
}

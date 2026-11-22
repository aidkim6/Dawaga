package com.example.dawaga;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

final class SetupScreen {
    static final int BACKGROUND = 0xfff3f6fb;
    static final int INK = 0xff1d294b;
    static final int MUTED = 0xff5e6d88;
    static final int BLUE = 0xff5875e7;
    static final int PALE = 0xffeaf0fc;
    private static final int BORDER = 0xffd5dff1;

    final LinearLayout content;
    final LinearLayout footer;
    private final AppCompatActivity activity;

    SetupScreen(AppCompatActivity activity, String title, String description, Runnable back) {
        this.activity = activity;
        LinearLayout root = column();
        root.setBackgroundColor(BACKGROUND);
        root.setFocusableInTouchMode(true);
        root.setPadding(dp(20), dp(12), dp(20), dp(16));
        activity.getWindow().setStatusBarColor(BACKGROUND);
        activity.getWindow().setNavigationBarColor(BACKGROUND);
        activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        activity.setContentView(root);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content = column();
        content.setPadding(0, 0, 0, dp(16));
        scroll.addView(content);
        footer = column();
        root.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(48));
        if (back != null) {
            ImageButton arrow = new ImageButton(activity);
            arrow.setBackgroundColor(Color.TRANSPARENT);
            arrow.setImageResource(R.drawable.ic_arrow_back);
            arrow.setColorFilter(INK);
            arrow.setContentDescription("뒤로 가기");
            arrow.setOnClickListener(v -> back.run());
            LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(48), dp(48));
            arrowParams.setMarginStart(-dp(12));
            header.addView(arrow, arrowParams);
        }
        header.addView(label(title, 24, INK, true));
        add(content, header, -2, 0);
        add(content, label(description, 14, INK, false), -2, 6);
    }

    LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    Button button(String text, int fill, Runnable action) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(16);
        button.setTypeface(null, Typeface.BOLD);
        button.setAllCaps(false);
        button.setTextColor(fill == BLUE ? Color.WHITE : INK);
        button.setBackgroundTintList(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),
                shape(fill, fill == Color.WHITE), null));
        button.setStateListAnimator(null);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    Drawable shape(int color, boolean outline) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(16));
        if (outline) drawable.setStroke(dp(1), BORDER);
        return drawable;
    }

    void add(LinearLayout parent, View child, int height, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,
                height < 0 ? height : dp(height));
        params.topMargin = dp(top);
        parent.addView(child, params);
    }

    int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}

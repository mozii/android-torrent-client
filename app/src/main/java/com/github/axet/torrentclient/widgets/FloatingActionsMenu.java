package com.github.axet.torrentclient.widgets;

import android.content.Context;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.graphics.drawable.shapes.RectShape;
import android.graphics.drawable.shapes.Shape;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.torrentclient.R;

/*
 old phones do not allow to set ?attrs in background. create class.

<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="?attr/secondBackground" />
    <stroke
        android:width="1dip"
        android:color="?attr/secondBackground" />
    <corners android:radius="10dip" />
    <padding
        android:bottom="0dip"
        android:left="0dip"
        android:right="0dip"
        android:top="0dip" />
</shape>

*/

public class FloatingActionsMenu extends com.getbase.floatingactionbutton.FloatingActionsMenu {

    public FloatingActionsMenu(Context context) {
        this(context, null);
    }

    public FloatingActionsMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FloatingActionsMenu(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void addView(View v) {
        super.addView(v);

        if (v instanceof TextView) {
            GradientDrawable d = new GradientDrawable();

            int c = ThemeUtils.getThemeColor(getContext(), R.attr.secondBackground);

            d.setColor(c);
            d.setStroke(ThemeUtils.dp2px(getContext(), 1), c);
            d.setCornerRadius(ThemeUtils.dp2px(getContext(), 10));

            if (Build.VERSION.SDK_INT >= 16)
                v.setBackground(d);
            else
                v.setBackgroundDrawable(d);
        }
    }
}

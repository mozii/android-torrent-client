package com.github.axet.torrentclient.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.View;

import com.github.axet.androidlibrary.widgets.ThemeUtils;

/**
 * Created by axet on 19/06/16.
 */
public class Peices extends View {

    public final static int CELLS = 18;

    int cellSize = 0;
    int borderSize = 0;
    int stepSize;

    public Peices(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public Peices(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        cellSize = ThemeUtils.dp2px(getContext(), 5);
        borderSize = ThemeUtils.dp2px(getContext(), 1);
        stepSize = cellSize + borderSize;

        int w = CELLS * stepSize;
        int h = w;
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(Color.BLUE);

        Paint p = new Paint();
        p.setColor(Color.GRAY);

        for (int i = 0; i < CELLS; i++) {
            // vertical
            canvas.drawLine(i * stepSize, 0, i * stepSize, getHeight(), p);
            // horizontal
            canvas.drawLine(0, i * stepSize, getWidth(), i * stepSize, p);
        }
    }
}

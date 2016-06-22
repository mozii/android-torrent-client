package com.github.axet.torrentclient.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.View;

import com.github.axet.androidlibrary.widgets.ThemeUtils;

import java.util.ArrayList;

import go.libtorrent.Libtorrent;

public class Pieces extends View {

    public static int CELLS = 18;

    int cellSize = 0;
    int borderSize = 0;
    int stepSize;

    enum Status {
        EMPTY,
        CHECKING,
        PARTIAL,
        WRITING,
        COMPLETE
    }

    Paint empty = new Paint();
    Paint checking = new Paint();
    Paint partial = new Paint();
    Paint complete = new Paint();
    Paint writing = new Paint();

    {
        empty.setColor(Color.GRAY);
        checking.setColor(Color.YELLOW);
        partial.setColor(Color.GREEN);
        writing.setColor(Color.RED);
        complete.setColor(Color.BLUE);
    }

    ArrayList<Status> pieces;

    public Pieces(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public Pieces(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setTorrent(long t) {
        int len = CELLS * CELLS;

        long l = Libtorrent.TorrentPiecesCount(t);

        if (l < len) {
            int c = (int) Math.pow(l, 0.5);
            if (c == 0)
                return;
            CELLS = c;

            len = CELLS * CELLS;
        }

        long step = l / len;

        long pos = 0;

        pieces = new ArrayList<>();

        for (long i = 0; i < len; i++) {
            boolean checking = false;
            boolean empty = false;
            boolean complete = false;
            boolean partial = false;
            for (int s = 0; s < step && pos < l; s++) {
                Libtorrent.PieceStatus a = Libtorrent.TorrentPieces(t, pos);
                if (a.getPartial()) {
                    partial = true;
                }

                if (!a.getComplete())
                    empty = true;
                else
                    complete = true;

                if (a.getChecking())
                    checking = true;
                pos++;
            }
            if (checking) {
                pieces.add(Status.CHECKING);
            } else if (partial) {
                pieces.add(Status.WRITING);
            } else if (empty && complete) {
                pieces.add(Status.PARTIAL);
            } else if (complete) {
                pieces.add(Status.COMPLETE);
            } else {
                pieces.add(Status.EMPTY);
            }
        }

        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        cellSize = ThemeUtils.dp2px(getContext(), 4);
        borderSize = ThemeUtils.dp2px(getContext(), 1);
        stepSize = cellSize + borderSize;

        int w = CELLS * stepSize;
        int h = w;
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(empty.getColor());

        Paint p = new Paint();
        p.setColor(Color.GRAY);

        int pos = 0;

        for (int i = 0; i < CELLS; i++) {
            // vertical
            canvas.drawLine(i * stepSize, 0, i * stepSize, getHeight(), p);
            // horizontal
            canvas.drawLine(0, i * stepSize, getWidth(), i * stepSize, p);

            if (pieces != null) {
                for (int x = 0; x < CELLS; x++) {
                    Paint paint = null;

                    Status s = pieces.get(pos);
                    switch (s) {
                        case WRITING:
                            paint = writing;
                            break;
                        case PARTIAL:
                            paint = partial;
                            break;
                        case CHECKING:
                            paint = checking;
                            break;
                        case COMPLETE:
                            paint = complete;
                            break;
                        case EMPTY:
                            paint = empty;
                            break;
                    }

                    int left = x * stepSize;
                    int top = i * stepSize;
                    int right = left + stepSize;
                    int bottom = top + stepSize;
                    canvas.drawRect(left, top, right, bottom, paint);

                    pos++;
                }
            }
        }
    }
}

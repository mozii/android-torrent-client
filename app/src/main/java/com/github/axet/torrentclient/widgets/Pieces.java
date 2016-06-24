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

    Paint border = new Paint();

    Paint empty = new Paint();
    Paint checking = new Paint();
    Paint partial = new Paint();
    Paint complete = new Paint();
    Paint writing = new Paint();

    ArrayList<Status> pieces;

    public Pieces(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    public Pieces(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    void init() {
        cellSize = ThemeUtils.dp2px(getContext(), 4);
        borderSize = ThemeUtils.dp2px(getContext(), 0.5f);
        stepSize = cellSize + borderSize;

        border.setStrokeWidth(borderSize);
        border.setColor(Color.LTGRAY);

        empty.setColor(Color.GRAY);
        checking.setColor(Color.YELLOW);
        partial.setColor(Color.GREEN);
        writing.setColor(Color.RED);
        complete.setColor(Color.BLUE);
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

        long step = l / len + 1;

        long pos = 0;

        pieces = new ArrayList<>();

        for (long i = 0; i < len && pos < l; i++) {
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

        int w = CELLS * stepSize + borderSize * 2;
        int h = w;
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isInEditMode()) {
            pieces = new ArrayList<>();
            for (int i = 0; i < CELLS * CELLS - 10; i++) {
                Status s = Status.values()[(int) (Math.random() * Status.values().length)];
                pieces.add(s);
            }
        }

        canvas.drawColor(0);

        canvas.drawRect(0, 0, getWidth(), getBottom(), border);

        int pos = 0;

        for (int i = 0; i < CELLS; i++) {
//            {
//                // vertical
//                int s = i * stepSize;
//                canvas.drawLine(s, 0, s, getHeight(), p);
//                // horizontal
//                canvas.drawLine(0, s, getWidth(), s, p);
//            }

            if (pieces != null) {
                for (int x = 0; x < CELLS && pos < pieces.size(); x++) {
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

                    int left = x * stepSize + borderSize;
                    int top = i * stepSize + borderSize;
                    int right = left + stepSize - 2*borderSize;
                    int bottom = top + stepSize - 2*borderSize;

                    canvas.drawRect(left+borderSize, top+borderSize, right+borderSize, bottom+borderSize, paint);

                    pos++;
                }
            }
        }
    }
}

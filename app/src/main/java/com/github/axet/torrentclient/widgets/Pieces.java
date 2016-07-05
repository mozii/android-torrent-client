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

    public final static int CELLS = 18;

    int cells = CELLS;
    int cellSize = 0;
    int borderSize = 0;
    int stepSize;

    Paint border = new Paint();

    Paint unpended = new Paint();
    Paint empty = new Paint();
    Paint checking = new Paint();
    Paint partial = new Paint();
    Paint complete = new Paint();
    Paint writing = new Paint();

    ArrayList<Integer> pieces;

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

        unpended.setColor(0xFFAAAAAA); // mid between LTGRAY - GRAY
        empty.setColor(Color.GRAY);
        checking.setColor(Color.YELLOW);
        partial.setColor(Color.GREEN);
        writing.setColor(Color.RED);
        complete.setColor(Color.BLUE);
    }

    public void setTorrent(long t) {
        if(!Libtorrent.MetaTorrent(t))
            return;

        cells = CELLS;

        int len = cells * cells;

        long l = Libtorrent.TorrentPiecesCount(t);

        if (l < len) {
            int c = (int) Math.pow(l, 0.5);
            if (c == 0)
                return;
            cells = c;

            len = cells * cells;
        }

        long step = l / len + 1;

        pieces = new ArrayList<>();

        l = Libtorrent.TorrentPiecesCompactCount(t, step);

        for (long i = 0; i < l; i++) {
            pieces.add(Libtorrent.TorrentPiecesCompact(t, i));
        }

        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int w = cells * stepSize + borderSize * 2;
        int h = w;
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isInEditMode()) {
            pieces = new ArrayList<>();
            int[] status = new int[]{
                    Libtorrent.PieceEmpty,
                    Libtorrent.PieceComplete,
                    Libtorrent.PieceChecking,
                    Libtorrent.PiecePartial,
                    Libtorrent.PieceWriting,
            };
            for (int i = 0; i < cells * cells - 10; i++) {
                int s = status[(int) (Math.random() * status.length)];
                pieces.add(s);
            }
        }

        canvas.drawColor(0);

        canvas.drawRect(0, 0, getWidth(), getBottom(), border);

        int pos = 0;

        for (int i = 0; i < cells; i++) {
//            {
//                // vertical
//                int s = i * stepSize;
//                canvas.drawLine(s, 0, s, getHeight(), p);
//                // horizontal
//                canvas.drawLine(0, s, getWidth(), s, p);
//            }

            if (pieces != null) {
                for (int x = 0; x < cells && pos < pieces.size(); x++) {
                    Paint paint = null;

                    int s = pieces.get(pos);
                    switch (s) {
                        case Libtorrent.PieceWriting:
                            paint = writing;
                            break;
                        case Libtorrent.PiecePartial:
                            paint = partial;
                            break;
                        case Libtorrent.PieceChecking:
                            paint = checking;
                            break;
                        case Libtorrent.PieceComplete:
                            paint = complete;
                            break;
                        case Libtorrent.PieceUnpended:
                            paint = unpended;
                            break;
                        case Libtorrent.PieceEmpty:
                            paint = empty;
                            break;
                    }

                    int left = x * stepSize + borderSize;
                    int top = i * stepSize + borderSize;
                    int right = left + stepSize - 2 * borderSize;
                    int bottom = top + stepSize - 2 * borderSize;

                    canvas.drawRect(left + borderSize, top + borderSize, right + borderSize, bottom + borderSize, paint);

                    pos++;
                }
            }
        }
    }
}

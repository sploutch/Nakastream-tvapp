package com.perso.nakastream;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Pointeur affiché par-dessus la page, déplacé avec les flèches de la télécommande. */
public class CursorView extends View {
    float x = -1, y = -1;
    private boolean shown = true;
    private final float radius;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CursorView(Context context) {
        super(context);
        float d = context.getResources().getDisplayMetrics().density;
        radius = 11 * d;
        fill.setColor(Color.argb(150, 255, 255, 255));
        ring.setColor(Color.argb(230, 20, 20, 20));
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2.5f * d);
        dot.setColor(Color.rgb(230, 40, 60));
        setFocusable(false);
        setClickable(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (x < 0 || x >= w || y >= h) {
            x = w / 2f;
            y = h / 2f;
        }
    }

    void setShown(boolean value) {
        if (shown != value) {
            shown = value;
            invalidate();
        }
    }

    boolean isShown2() {
        return shown;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!shown) return;
        canvas.drawCircle(x, y, radius, fill);
        canvas.drawCircle(x, y, radius, ring);
        canvas.drawCircle(x, y, radius / 4f, dot);
    }
}

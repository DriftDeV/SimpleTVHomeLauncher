package dev.drift.homelauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/** Avatar prefatti: sfondo a gradiente e simbolo bianco disegnati con Canvas, niente file e niente rete. */
final class AvatarArt {
    private static final int[][] COLORS = {
            {0xFFFFB347, 0xFFFF5E62}, {0xFF6A82FB, 0xFF1D2B64}, {0xFFFFE259, 0xFFFFA751},
            {0xFFFF758C, 0xFFFF7EB3}, {0xFF00C6FB, 0xFF5E5CE6}, {0xFF56AB2F, 0xFF1E5799},
            {0xFF36D1DC, 0xFF5B86E5}, {0xFFFF512F, 0xFFDD2476}, {0xFF9ED67B, 0xFF1D976C},
            {0xFFBF5AF2, 0xFF5E5CE6}, {0xFF485563, 0xFF29323C}, {0xFFFDC830, 0xFFF37335}};
    static final int COUNT = COLORS.length;

    private AvatarArt() {
    }

    /** Avatar quadrato size x size con cerchio pieno: chi lo disegna lo ritaglia come preferisce. */
    static Bitmap bitmap(int idx, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] col = COLORS[idx % COUNT];
        p.setShader(new LinearGradient(0, 0, size, size, col[0], col[1], Shader.TileMode.CLAMP));
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setShader(null);
        c.save();
        c.translate(size / 2f, size / 2f);
        float s = size * 0.27f; // unita' del disegno: simboli in [-1,1] x [-1,1]
        c.scale(s, s);
        glyph(c, idx % COUNT);
        c.restore();
        return b;
    }

    private static Paint fill() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xF2FFFFFF);
        return p;
    }

    private static Paint stroke(float w) {
        Paint p = fill();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }

    private static void glyph(Canvas c, int i) {
        Paint f = fill();
        Path p = new Path();
        switch (i) {
            case 0: // stella
                for (int k = 0; k < 10; k++) {
                    double a = -Math.PI / 2 + k * Math.PI / 5;
                    float r = k % 2 == 0 ? 1.15f : 0.5f;
                    float x = (float) Math.cos(a) * r, y = (float) Math.sin(a) * r;
                    if (k == 0) p.moveTo(x, y); else p.lineTo(x, y);
                }
                p.close();
                c.drawPath(p, f);
                break;
            case 1: // luna
                p.addCircle(0, 0, 1.05f, Path.Direction.CW);
                Path cut = new Path();
                cut.addCircle(0.55f, -0.3f, 0.9f, Path.Direction.CW);
                p.op(cut, Path.Op.DIFFERENCE);
                c.drawPath(p, f);
                break;
            case 2: // fulmine
                p.moveTo(0.25f, -1.2f);
                p.lineTo(-0.75f, 0.2f);
                p.lineTo(-0.05f, 0.2f);
                p.lineTo(-0.3f, 1.2f);
                p.lineTo(0.8f, -0.3f);
                p.lineTo(0.1f, -0.3f);
                p.close();
                c.drawPath(p, f);
                break;
            case 3: // cuore
                p.moveTo(0, 1.05f);
                p.cubicTo(-1.7f, -0.1f, -1.0f, -1.3f, 0, -0.45f);
                p.cubicTo(1.0f, -1.3f, 1.7f, -0.1f, 0, 1.05f);
                p.close();
                c.drawPath(p, f);
                break;
            case 4: // pianeta con anello
                c.drawCircle(0, 0, 0.7f, f);
                c.save();
                c.rotate(-25);
                c.drawOval(new RectF(-1.35f, -0.35f, 1.35f, 0.35f), stroke(0.16f));
                c.restore();
                break;
            case 5: // montagne e sole
                p.moveTo(-1.2f, 0.9f);
                p.lineTo(-0.35f, -0.3f);
                p.lineTo(0.2f, 0.5f);
                p.lineTo(0.55f, -0.05f);
                p.lineTo(1.2f, 0.9f);
                p.close();
                c.drawPath(p, f);
                c.drawCircle(0.6f, -0.75f, 0.28f, f);
                break;
            case 6: // onda
                p.moveTo(-1.2f, 0);
                p.cubicTo(-0.8f, -0.9f, -0.4f, -0.9f, 0, 0);
                p.cubicTo(0.4f, 0.9f, 0.8f, 0.9f, 1.2f, 0);
                c.drawPath(p, stroke(0.3f));
                break;
            case 7: // fiamma
                p.moveTo(0, -1.25f);
                p.cubicTo(0.3f, -0.5f, 1.1f, -0.2f, 0.85f, 0.55f);
                p.cubicTo(0.7f, 1.1f, -0.7f, 1.1f, -0.85f, 0.45f);
                p.cubicTo(-0.95f, 0, -0.45f, -0.25f, -0.3f, -0.7f);
                p.cubicTo(-0.1f, -0.5f, 0.1f, -0.9f, 0, -1.25f);
                p.close();
                c.drawPath(p, f);
                break;
            case 8: // foglia
                p.moveTo(-1.0f, 1.0f);
                p.cubicTo(-1.2f, -0.6f, 0.2f, -1.2f, 1.1f, -1.1f);
                p.cubicTo(1.2f, -0.1f, 0.6f, 1.1f, -1.0f, 1.0f);
                p.close();
                c.drawPath(p, f);
                break;
            case 9: // nota musicale
                c.drawOval(new RectF(-0.85f, 0.35f, -0.05f, 1.0f), f);
                c.drawRect(-0.15f, -1.1f, 0.1f, 0.7f, f);
                p.moveTo(0.1f, -1.1f);
                p.cubicTo(0.9f, -0.9f, 1.1f, -0.3f, 0.9f, 0.2f);
                p.cubicTo(0.8f, -0.4f, 0.5f, -0.5f, 0.1f, -0.5f);
                p.close();
                c.drawPath(p, f);
                break;
            case 10: // controller di gioco
                c.drawRoundRect(new RectF(-1.25f, -0.65f, 1.25f, 0.65f), 0.6f, 0.6f, f);
                Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
                dark.setColor(0xFF29323C);
                c.drawRect(-0.8f, -0.1f, -0.2f, 0.1f, dark);
                c.drawRect(-0.6f, -0.3f, -0.4f, 0.3f, dark);
                c.drawCircle(0.5f, 0.15f, 0.14f, dark);
                c.drawCircle(0.85f, -0.15f, 0.14f, dark);
                break;
            default: // sorriso
                c.drawCircle(0, 0, 1.1f, stroke(0.16f));
                c.drawCircle(-0.4f, -0.3f, 0.13f, f);
                c.drawCircle(0.4f, -0.3f, 0.13f, f);
                p.addArc(new RectF(-0.6f, -0.2f, 0.6f, 0.7f), 20, 140);
                c.drawPath(p, stroke(0.14f));
                break;
        }
    }
}

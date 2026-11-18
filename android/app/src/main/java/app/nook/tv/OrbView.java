package app.nook.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import java.util.Random;

/**
 * The BAY65 Assistant presence orb, ported from Rupert's canvas orb.
 * Colour carries state: calm blue when idle, teal while listening, amber while thinking or speaking.
 */
final class OrbView extends View {
    enum Mode { QUIET, MIC, SHIMMER, SPEECH }

    private static final float TWO_PI = (float) (Math.PI * 2);

    /** deep, mid, hot, glassLight, glassMid, glassEdge as RGB. */
    private static final int[] CYAN = {0x0854c8, 0x3eacff, 0xaae2ff, 0xd4f0ff, 0x2882dc, 0x061c38};
    private static final int[] TEAL = {0x027896, 0x02a4ca, 0x8ce6f0, 0xc8ebf5, 0x1e8caa, 0x051928};
    private static final int[] AMBER = {0x965e16, 0xd88e1c, 0xffd696, 0xffebc8, 0xb48232, 0x281e0f};

    private static final class Config {
        final float floor, sphere, pulse, scale; final int[] palette;
        Config(float floor, float sphere, float pulse, float scale, int[] palette) {
            this.floor = floor; this.sphere = sphere; this.pulse = pulse; this.scale = scale; this.palette = palette;
        }
    }

    private static Config config(Mode mode) {
        switch (mode) {
            case MIC: return new Config(.1f, .6f, .08f, 1f, TEAL);
            case SHIMMER: return new Config(.35f, .35f, .04f, 1f, AMBER);
            case SPEECH: return new Config(.15f, .9f, .07f, 1f, AMBER);
            default: return new Config(.3f, .16f, .02f, .65f, CYAN);
        }
    }

    private final float[][] particles; // angle, distance, speed, radius, phase, twinkle
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Path clip = new Path();
    private final long epoch = SystemClock.uptimeMillis();
    private final boolean reduceMotion;
    private Mode mode = Mode.QUIET;
    private Config current = config(Mode.QUIET);
    private float level, target;
    private long last = SystemClock.uptimeMillis();
    private Shader vignette, bloom, glass, core;
    private Config shaded;
    private float shadedSize = -1;

    OrbView(Context context) {
        super(context);
        float scale = 1f;
        try { scale = Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f); } catch (RuntimeException ignored) {}
        reduceMotion = scale == 0f;
        Random random = new Random();
        particles = new float[40][];
        for (int i = 0; i < particles.length; i++) {
            boolean outer = random.nextFloat() > .3f;
            particles[i] = new float[] {
                    random.nextFloat() * TWO_PI,
                    outer ? 1.04f + random.nextFloat() * .42f : .5f + random.nextFloat() * .42f,
                    (.08f + random.nextFloat() * .3f) * (random.nextBoolean() ? 1 : -1),
                    .5f + random.nextFloat() * 1.3f,
                    random.nextFloat() * TWO_PI,
                    .6f + random.nextFloat() * 2.2f };
        }
        setWillNotDraw(false);
    }

    void setMode(Mode next) {
        if (next == mode) return;
        mode = next; current = config(next);
        if (next != Mode.MIC && next != Mode.SPEECH) target = 0;
        invalidate();
    }

    /** Microphone RMS (0..1) or a speech pulse. Smoothed per frame like Rupert's level store. */
    void setLevel(float value) { target = Math.max(0f, Math.min(1f, value)); invalidate(); }

    private static int argb(int rgb, float alpha) {
        return (Math.round(Math.max(0f, Math.min(1f, alpha)) * 255) << 24) | (rgb & 0xffffff);
    }

    private void ring(Canvas canvas, float cx, float cy, float radius, float width, int rgb, float alpha) {
        paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setColor(argb(rgb, alpha));
        canvas.drawCircle(cx, cy, radius, paint);
    }

    /** Gradients are built once per size and mode; per-frame intensity comes from the paint's alpha. */
    private void prepare(float size, float cx, float cy) {
        if (size == shadedSize && current == shaded) return;
        shadedSize = size; shaded = current;
        int[] p = current.palette;
        float sr = size * .27f * .8f * current.scale;
        vignette = new RadialGradient(cx, cy, size / 2f, new int[] {0xf0030c16, 0x80030c16, 0x00030c16}, new float[] {0, .46f, .72f}, Shader.TileMode.CLAMP);
        bloom = new RadialGradient(cx, cy, size * .48f, new int[] {argb(p[0], 1), argb(p[0], 0)}, new float[] {.2f, 1}, Shader.TileMode.CLAMP);
        glass = new RadialGradient(cx - sr * .35f, cy - sr * .4f, sr * 1.3f, new int[] {argb(p[3], .35f), argb(p[4], .17f), argb(p[5], .06f)}, new float[] {.08f, .55f, 1}, Shader.TileMode.CLAMP);
        core = new RadialGradient(cx, cy, sr * .55f, argb(0xc6ecff, 1), argb(0xc6ecff, 0), Shader.TileMode.CLAMP);
    }

    private void shade(Shader shader, float alpha) {
        paint.setStyle(Paint.Style.FILL); paint.setShader(shader); paint.setColor(argb(0, alpha));
    }

    @Override protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(.05f, (now - last) / 1000f); last = now;
        float t = reduceMotion ? 0 : (now - epoch) / 1000f;
        float motion = reduceMotion ? 0 : 1, pulse = reduceMotion ? .45f : 1;
        int[] p = current.palette;
        float size = Math.min(getWidth(), getHeight()), cx = getWidth() / 2f, cy = getHeight() / 2f, density = getResources().getDisplayMetrics().density;
        if (size <= 0) return;

        level += (target - level) * (target > level ? .4f : .06f);
        float breath = (float) Math.sin(t * TWO_PI / 7) * motion;
        float autonomous = mode == Mode.SHIMMER ? .28f + (float) Math.sin(t * 1.7) * .12f : current.floor * (1 + breath * .3f);
        float energy = Math.min(1, Math.max(level, autonomous)) * pulse;
        float base = size * .27f;
        float ringRadius = base * current.scale * (1 + energy * current.pulse + breath * .015f);

        prepare(size, cx, cy);
        // Near-black vignette so the glow reads against bright photo wallpapers.
        shade(vignette, 1);
        canvas.drawCircle(cx, cy, size / 2f, paint);

        shade(bloom, .1f + energy * .45f);
        canvas.drawCircle(cx, cy, size * .48f, paint);

        ring(canvas, cx, cy, ringRadius, base * .18f, p[1], .05f + energy * .2f);
        ring(canvas, cx, cy, ringRadius, base * .09f, p[1], .14f + energy * .4f);
        ring(canvas, cx, cy, ringRadius, base * .035f, p[2], .4f + energy * .5f);

        float rotation = t * (.35f + energy * .8f) * motion;
        oval.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius);
        paint.setStrokeWidth(base * .05f); paint.setColor(argb(p[2], .12f + energy * .5f));
        for (int i = 0; i < 3; i++) {
            float span = (.9f + .55f * Math.abs((float) Math.sin(t * .5f + i * 1.9f))) * motion + .3f;
            float start = (i % 2 == 1 ? -.6f : 1f) * rotation + i * TWO_PI / 3;
            canvas.drawArc(oval, (float) Math.toDegrees(start), (float) Math.toDegrees(span), false, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        float dotScale = Math.max(1f, size / (120f * density)) * density * .7f;
        for (float[] d : particles) {
            d[0] += d[2] * dt * motion * (1 + energy * 1.6f);
            float twinkle = .5f + .5f * (float) Math.sin(t * d[5] + d[4]);
            paint.setColor(argb(d[3] > 1.1f ? p[2] : p[1], (.1f + .45f * twinkle) * (.4f + .7f * energy)));
            canvas.drawCircle(cx + (float) Math.cos(d[0]) * d[1] * ringRadius, cy + (float) Math.sin(d[0]) * d[1] * ringRadius, d[3] * dotScale, paint);
        }

        float sphereAlpha = Math.min(1, current.sphere * (.55f + energy * .6f));
        float sr = base * .8f * current.scale * (1 + energy * .05f);
        if (sphereAlpha > .01f) {
            canvas.save();
            clip.reset(); clip.addCircle(cx, cy, sr, Path.Direction.CW); canvas.clipPath(clip);
            shade(glass, sphereAlpha);
            canvas.drawRect(cx - sr, cy - sr, cx + sr, cy + sr, paint);
            paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(sr * .05f); paint.setColor(argb(p[4], .22f * sphereAlpha));
            float swirl = t * .4f * motion;
            for (int i = 0; i < 2; i++) {
                canvas.save();
                canvas.rotate((float) Math.toDegrees(swirl * (i == 1 ? -.7f : 1) + i * 1.8f), cx, cy);
                oval.set(cx - sr * .72f, cy - sr * .3f, cx + sr * .72f, cy + sr * .3f);
                canvas.drawArc(oval, 0, 288, false, paint);
                canvas.restore();
            }
            paint.setStyle(Paint.Style.FILL); paint.setColor(argb(0xecfaff, .3f * sphereAlpha));
            canvas.save(); canvas.rotate(-34, cx - sr * .38f, cy - sr * .42f);
            oval.set(cx - sr * .54f, cy - sr * .51f, cx - sr * .22f, cy - sr * .33f); canvas.drawOval(oval, paint);
            canvas.restore();
            canvas.restore();
            ring(canvas, cx, cy, sr, sr * .055f, p[1], sphereAlpha * (.3f + energy * .4f));
            if (energy > .05f) {
                float core = energy * (mode == Mode.SPEECH ? .5f : .3f);
                shade(this.core, core);
                canvas.drawCircle(cx, cy, sr * .55f, paint);
            }
        }
        paint.setShader(null);

        if (isFocused()) ring(canvas, cx, cy, size * .47f, 2 * density, 0xffffff, .9f);
        if (isShown() && !(reduceMotion && mode == Mode.QUIET && level < .01f)) postInvalidateOnAnimation();
    }

    @Override protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        invalidate();
    }

    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        last = SystemClock.uptimeMillis();
        if (visibility == VISIBLE) invalidate();
    }
}

package com.my.SlitherArena;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentDebugSettings;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.FormError;
import com.google.android.ump.UserMessagingPlatform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Random;

/**
 * SLITHER ARENA  -  pure Java + SurfaceView port of the HTML game.
 *  - UI (menu, HUD, game over)  : res/layout/main.xml
 *  - Game world / rendering     : SurfaceView drawn by a game thread (Canvas)
 *  - No AndroidX, no lambdas, no drawable resources, Java 7 safe.
 *  Controls: touch + drag = steer, 2nd finger or BOOST button = sprint.
 */
public class MainActivity extends Activity {

    // =====================================================================
    // CONSTANTS
    // =====================================================================
    static final int MAXSEG = 260, BOTS = 32, FOODN = 100, FOODMAX = 900, PN = 900;
    static final float LIVE = 55f;
    static final int ST_MENU = 0, ST_PLAY = 1, ST_PAUSED = 2, ST_OVER = 3;
    static final int P_SOLID = 0, P_MULTI = 1, P_STRIPE = 2, P_FADE = 3, P_DOTS = 4;
    static final Random RND = new Random();

    // =====================================================================
    // AD SETTINGS (AdMob)  -  edit these
    // =====================================================================
    static final boolean ADS_TEST_MODE = false;  // false = real AdMob (needs gradle dependency). true = fake ads without SDK, for quick testing only.
    static final String AD_REWARDED_ID = "ca-app-pub-3242350797621523/4016678651";       // Google TEST id - replace with your own REWARDED unit id before release
    static final String AD_INTERSTITIAL_ID = "ca-app-pub-3242350797621523/1757346822";   // Google TEST id - replace with your own
    static final String UMP_TEST_DEVICE_HASH = "";   // optional: your test device hash (see logcat "Use new ConsentDebugSettings...") to force the EEA consent form while testing. Leave "" for release.
    // ---- SIDEBAR LINKS - edit these ----
    static final String PRIVACY_URL = "https://YOUR-PRIVACY-POLICY-URL-HERE";   // TODO: put your privacy policy page URL
    static final String PLAY_PACKAGE = "com.my.Slither";                         // must match your Play Store package name
    static final String PLAY_URL = "https://play.google.com/store/apps/details?id=" + PLAY_PACKAGE;
    static final int INTERSTITIAL_EVERY = 3;    // interstitial on every Nth tap of Play Again / Menu (2 or 3 recommended, 0 = never)
    static final float GIANT_MASS = 50f;         // "Start Giant" mass (normal Play = 15)
    static final boolean LOCK_FLAGS = true;      // flag skins need "Watch Ad to Unlock"
    static final String[] PREMIUM_HATS = {"Crown", "Halo", "Viking", "Wizard", "Peacock", "Phoenix", "Angel", "Chief", "Ice Crown", "Laurel"};   // hats that need "Watch Ad to Unlock" (rewarded ad is the ONLY way to unlock them)
    static final long INTERSTITIAL_MIN_GAP_MS = 90000L;   // never two full-screen ads closer than this (AdMob: no back-to-back / surprise ads)
    static final int SEG_CAP_PLAYER = 220, SEG_CAP_BOT = 60;   // max body segments - keeps the game smooth at any size
    static final float MASS_CAP = 6500f;      // body thickness stops growing here (score keeps counting)
    static final float LONG_FROM = 2790f;     // once the segment cap is reached the body keeps stretching (up to +35%) until MASS_CAP

    static float rand() { return RND.nextFloat(); }
    static float rnd(float a, float b) { return a + RND.nextFloat() * (b - a); }
    static float cosf(float a) { return (float) Math.cos(a); }
    static float sinf(float a) { return (float) Math.sin(a); }
    static float atan2f(float y, float x) { return (float) Math.atan2(y, x); }
    static float sqrtf(float v) { return (float) Math.sqrt(v); }
    static float hypotf(float a, float b) { return (float) Math.sqrt(a * a + b * b); }
    static float expf(float v) { return (float) Math.exp(v); }
    static float powf(float a, float b) { return (float) Math.pow(a, b); }
    static float sq(float v) { return v * v; }
    static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
    static float angDiff(float a, float b) {
        float d = b - a;
        while (d > 3.14159265f) d -= 6.2831853f;
        while (d < -3.14159265f) d += 6.2831853f;
        return d;
    }

    // =====================================================================
    // DATA CLASSES
    // =====================================================================
    static class Skin {
        int p, w;
        int[] c;
        Skin(int p, int w, int... c) { this.p = p; this.w = w; this.c = c; }
    }

    static class Flag {
        String n;
        int[] c;
        Flag(String n, int... c) { this.n = n; this.c = c; }
    }

    static class HatPart {
        float x, y, h, s, hot, a;
        int col;
        Bitmap bmp;
    }

    static class Hat {
        String n;
        boolean f;
        int kind;   // 0 = glow-circle parts, 1 = vector crown (pure canvas)
        ArrayList<HatPart> p = new ArrayList<HatPart>();
        Hat(String n, boolean f) { this.n = n; this.f = f; }
    }

    static class Bg {
        String n;
        int color, cw, ch;
        float w, h;
        Bitmap tile;
        Bg(String n, int color, float w, float h, int cw, int ch) {
            this.n = n; this.color = color; this.w = w; this.h = h; this.cw = cw; this.ch = ch;
        }
    }

    static class Snake {
        boolean bot, alive, boost;
        String name = "";
        Skin skin;
        int hat, idx = -1, kills, n;
        float x, y, a, ta, mass = 15, r = .5f, bt, drop, t, respawn, spd = 1, bw, v = 11, dist, p0, agg = -1;
        float[] sx = new float[MAXSEG], sy = new float[MAXSEG], rp = new float[MAXSEG * 3];
        int[] bands = new int[MAXSEG];
        int bestC = 0xFFFFFFFF, hc = 0xFFFFFFFF;
    }

    static class Food {
        float x, y, v, p, vx, vy, k, b, sh = 1;
        int c, d;
    }

    static class Cell {
        float x, y, v;
        int n;
        ArrayList<Food> it = new ArrayList<Food>();
    }

    static class Rng {
        long seed;
        Rng(long s) { seed = s; }
        float next() { seed = (seed * 16807L) % 2147483647L; return seed / 2147483647f; }
    }

    // =====================================================================
    // STATIC TABLES (skins, flags, hats, backgrounds, names)
    // =====================================================================
    static final Skin[] SKINS = {
            new Skin(P_SOLID, 0, 0xFFff2bd6), new Skin(P_SOLID, 0, 0xFFff3b30), new Skin(P_SOLID, 0, 0xFF42e04a),
            new Skin(P_SOLID, 0, 0xFF2f7bff), new Skin(P_SOLID, 0, 0xFFff9a1f), new Skin(P_SOLID, 0, 0xFFffe14d),
            new Skin(P_SOLID, 0, 0xFF14e0d0), new Skin(P_SOLID, 0, 0xFF9b5de5),
            new Skin(P_MULTI, 0, 0xFFff3b30, 0xFFff9a1f, 0xFFffe14d, 0xFF42e04a, 0xFF2f7bff, 0xFF9b5de5),
            new Skin(P_MULTI, 0, 0xFFe8453c, 0xFFffffff, 0xFF3b5bdb, 0xFFf7c948, 0xFF0c7a43, 0xFFffffff),
            new Skin(P_STRIPE, 0, 0xFFff9a1f, 0xFF1a1a1a), new Skin(P_STRIPE, 0, 0xFFff8fd0, 0xFFffffff),
            new Skin(P_STRIPE, 0, 0xFF8cff3a, 0xFF1a1a1a), new Skin(P_STRIPE, 0, 0xFFffffff, 0xFF1a1a1a),
            new Skin(P_FADE, 0, 0xFFff9a1f, 0xFFff2b8a), new Skin(P_FADE, 0, 0xFF37e6e6, 0xFF3b4bff),
            new Skin(P_FADE, 0, 0xFFa855f7, 0xFF22d3ee), new Skin(P_FADE, 0, 0xFFffe14d, 0xFFff7a00),
            new Skin(P_DOTS, 0, 0xFFff3b30, 0xFFffffff), new Skin(P_DOTS, 0, 0xFF3ee08f, 0xFFfff27a),
            new Skin(P_MULTI, 0, 0xFFd62828, 0xFF151515, 0xFFf2b01e, 0xFF151515),
            new Skin(P_MULTI, 0, 0xFFd62828, 0xFFffffff, 0xFF1b8a45, 0xFFffffff),
            new Skin(P_SOLID, 0, 0xFF3fc1c9), new Skin(P_SOLID, 0, 0xFFff7f7f)
    };

    static final Flag[] FLAGS = {
            new Flag("USA", 0xFFd62839, 0xFFffffff, 0xFF1f4fd8), new Flag("China", 0xFFe0201c, 0xFFffde00),
            new Flag("Japan", 0xFFffffff, 0xFFe60026), new Flag("Germany", 0xFF2a2a2a, 0xFFe00000, 0xFFffce00),
            new Flag("India", 0xFFff9933, 0xFFffffff, 0xFF138808), new Flag("UK", 0xFF1d3c9c, 0xFFffffff, 0xFFcf142b),
            new Flag("France", 0xFF0055a4, 0xFFffffff, 0xFFef4135), new Flag("Italy", 0xFF009246, 0xFFffffff, 0xFFce2b37),
            new Flag("Brazil", 0xFF009c3b, 0xFFffdf00, 0xFF002776), new Flag("Canada", 0xFFe8212b, 0xFFffffff),
            new Flag("Russia", 0xFFffffff, 0xFF0039a6, 0xFFd52b1e), new Flag("S. Korea", 0xFFffffff, 0xFFcd2e3a, 0xFF0047a0),
            new Flag("Australia", 0xFF00247d, 0xFFcf142b, 0xFFffffff), new Flag("Mexico", 0xFF006847, 0xFFffffff, 0xFFce1126),
            new Flag("Spain", 0xFFaa151b, 0xFFf1bf00, 0xFFaa151b), new Flag("Indonesia", 0xFFff0000, 0xFFffffff),
            new Flag("Netherlands", 0xFFae1c28, 0xFFffffff, 0xFF21468b), new Flag("Saudi Arabia", 0xFF006c35, 0xFFffffff),
            new Flag("Turkey", 0xFFe30a17, 0xFFffffff), new Flag("Switzerland", 0xFFd52b1e, 0xFFffffff),
            new Flag("Pakistan", 0xFF0b9a45, 0xFFffffff)
    };

    static final String[] BN = {"catch me if you can", "navysnake", "kat", "what ever girl", "lolipop", "Otto the Otter",
            "pork", "Alejo", "SARAAWESOME", "Lightning Bolt", "Im a noob", "can't sleep", "sol", "Kota", "Mochi", "Zigzag",
            "Pixel", "Nova", "Jelly", "Echo", "Tango", "Comet", "Fizz", "Sprout", "Ripple", "Biscuit", "Moss", "Zed",
            "Blaze", "Pogo", "Luna", "Taco"};

    static final int[] FCOL = {0xFFff3b5c, 0xFF37e6e6, 0xFFffe45e, 0xFF6dff8a, 0xFFb08cff, 0xFFff9f43, 0xFFff4de0, 0xFF7da2ff};

    static final Bg[] BGS = {
            new Bg("Blue Hex", 0xFF6c7fe6, 4.6f, 4.6f * 1.7320508f, 128, 256),
            new Bg("Dark Hex", 0xFF0d0e13, 2.7f, 2.7f * 1.7320508f, 128, 256),
            new Bg("Light Hex", 0xFFdfe3ee, 2.7f, 2.7f * 1.7320508f, 128, 256),
            new Bg("Neon Grid", 0xFF070813, 4f, 4f, 128, 128),
            new Bg("Grass", 0xFF3f9d41, 8f, 8f, 256, 256),
            new Bg("Ocean", 0xFF0b5394, 10f, 10f, 256, 256),
            new Bg("Desert", 0xFFe8cd92, 8f, 8f, 256, 256),
            new Bg("Candy", 0xFFff9ec9, 8f, 8f, 256, 256),
            new Bg("Space", 0xFF04050e, 30f, 30f, 512, 512),
            new Bg("Pure White", 0xFFffffff, 3f, 3f, 128, 128),
            new Bg("Pure Black", 0xFF000000, 3f, 3f, 128, 128)
    };

    // ---- hats ----
    static void hp(Hat h, float x, float y, float hh, float s, String hex, float hot, float a) {
        HatPart q = new HatPart();
        q.x = x; q.y = y; q.h = hh; q.s = s; q.col = Color.parseColor(hex); q.hot = hot; q.a = a;
        h.p.add(q);
    }
    static void hp(Hat h, float x, float y, float hh, float s, String hex) { hp(h, x, y, hh, s, hex, .25f, 1f); }
    static void hp(Hat h, float x, float y, float hh, float s, String hex, float hot) { hp(h, x, y, hh, s, hex, hot, 1f); }
    static void ring(Hat h, float cx, float rad, int n, float hh, float s, String hex, float hot, float a, float st) {
        for (int k = 0; k < n; k++) {
            float t = st + k * 6.2832f / n;
            hp(h, cx + cosf(t) * rad, sinf(t) * rad, hh, s, hex, hot, a);
        }
    }
    static void ring(Hat h, float cx, float rad, int n, float hh, float s, String hex) { ring(h, cx, rad, n, hh, s, hex, .25f, 1f, 0f); }
    static void ring(Hat h, float cx, float rad, int n, float hh, float s, String hex, float hot) { ring(h, cx, rad, n, hh, s, hex, hot, 1f, 0f); }

    static Hat[] buildHats() {
        ArrayList<Hat> L = new ArrayList<Hat>();
        Hat h = new Hat("None", false); L.add(h);
        h = new Hat("Crown", false); h.kind = 1; L.add(h);
        h = new Hat("Top hat", false);
        ring(h, -.3f, .65f, 10, 1.8f, .6f, "#14141c", .1f); hp(h, -.3f, 0, 2.2f, 1.05f, "#2a2a38", .25f);
        ring(h, -.3f, .42f, 6, 2.3f, .28f, "#d62839", .3f); L.add(h);
        h = new Hat("Party hat", false);
        hp(h, -.15f, 0, 1.9f, 1.15f, "#ff4fa3", .2f); ring(h, -.15f, .38f, 5, 2.2f, .26f, "#ffe14d", .4f);
        hp(h, -.15f, 0, 2.7f, .4f, "#ffffff", .8f); L.add(h);
        h = new Hat("Cap", false);
        hp(h, .95f, -.28f, 1.7f, .5f, "#1b4fc0"); hp(h, .95f, 0, 1.7f, .55f, "#1b4fc0"); hp(h, .95f, .28f, 1.7f, .5f, "#1b4fc0");
        hp(h, -.1f, 0, 1.95f, 1.25f, "#2f7bff", .3f); hp(h, -.1f, 0, 2.35f, .28f, "#ff4d4d", .6f); L.add(h);
        h = new Hat("Cowboy", false);
        ring(h, -.4f, .8f, 10, 1.75f, .75f, "#7a4a1f"); hp(h, -.4f, 0, 1.75f, 1.0f, "#8b5a2b");
        hp(h, -.4f, 0, 2.2f, .95f, "#a8702f", .3f); ring(h, -.4f, .45f, 6, 2.0f, .26f, "#3b2410", .1f); L.add(h);
        h = new Hat("Viking", false);
        hp(h, -.2f, 0, 1.95f, 1.3f, "#aab2bd", .35f); hp(h, -.2f, 0, 2.3f, .4f, "#d9dee5", .6f);
        hp(h, -.1f, .95f, 1.9f, .55f, "#f3e9c8"); hp(h, -.1f, -.95f, 1.9f, .55f, "#f3e9c8");
        hp(h, .1f, 1.3f, 1.9f, .32f, "#fff6dc"); hp(h, .1f, -1.3f, 1.9f, .32f, "#fff6dc"); L.add(h);
        h = new Hat("Halo", false);
        hp(h, -.1f, 0, 2.7f, 2.3f, "#ffe66d", 0f, .22f); ring(h, -.1f, .8f, 12, 2.7f, .3f, "#ffe66d", .8f); L.add(h);
        h = new Hat("Devil horns", false);
        hp(h, 0, .7f, 1.9f, .55f, "#e01e37"); hp(h, 0, -.7f, 1.9f, .55f, "#e01e37");
        hp(h, .35f, .95f, 2.0f, .3f, "#ff6b6b", .5f); hp(h, .35f, -.95f, 2.0f, .3f, "#ff6b6b", .5f); L.add(h);
        h = new Hat("Headband", false);
        float[] ys = {-.8f, -.4f, 0f, .4f, .8f};
        for (int i = 0; i < ys.length; i++) hp(h, -.05f, ys[i], 1.7f, .4f, "#ff2a2a");
        hp(h, -.7f, .35f, 1.6f, .34f, "#ff2a2a"); hp(h, -.7f, -.35f, 1.6f, .34f, "#ff2a2a");
        hp(h, -1f, .6f, 1.5f, .28f, "#ff2a2a"); hp(h, -1f, -.6f, 1.5f, .28f, "#ff2a2a"); L.add(h);
        h = new Hat("Sunglasses", true);
        hp(h, .55f, -.62f, 1.55f, .75f, "#0a0a10", .1f); hp(h, .55f, .62f, 1.55f, .75f, "#0a0a10", .1f);
        hp(h, .55f, 0, 1.55f, .3f, "#0a0a10", .1f);
        hp(h, .72f, -.8f, 1.65f, .16f, "#9fd8ff", .9f, .8f); hp(h, .72f, .44f, 1.65f, .16f, "#9fd8ff", .9f, .8f); L.add(h);
        h = new Hat("Wizard", false);
        ring(h, -.4f, .85f, 9, 1.75f, .65f, "#4a22b0"); hp(h, -.4f, 0, 1.8f, 1.05f, "#6a3ad6");
        hp(h, -.4f, 0, 2.3f, .8f, "#7d4df0", .3f); hp(h, -.4f, 0, 2.7f, .3f, "#ffe14d", .8f); L.add(h);
        // premium vector hats (pure 2D canvas, drawn by HatGfx) - kind 2..7
        h = new Hat("Peacock", false); h.kind = 2; L.add(h);
        h = new Hat("Phoenix", false); h.kind = 3; L.add(h);
        h = new Hat("Angel", false); h.kind = 4; L.add(h);
        h = new Hat("Chief", false); h.kind = 5; L.add(h);
        h = new Hat("Ice Crown", false); h.kind = 6; L.add(h);
        h = new Hat("Laurel", false); h.kind = 7; L.add(h);
        return L.toArray(new Hat[L.size()]);
    }
    static final Hat[] HATS = buildHats();
    /** hats bots may wear (premium ones stay exclusive to players who unlocked them) */
    static final int[] FREE_HATS = buildFreeHats();
    static int[] buildFreeHats() {
        int[] tmp = new int[HATS.length];
        int m = 0;
        for (int i = 1; i < HATS.length; i++) {
            boolean prem = false;
            for (int k = 0; k < PREMIUM_HATS.length; k++) if (PREMIUM_HATS[k].equals(HATS[i].n)) prem = true;
            if (!prem) tmp[m++] = i;
        }
        int[] o = new int[m];
        System.arraycopy(tmp, 0, o, 0, m);
        return o;
    }

    // =====================================================================
    // COLOR HELPERS
    // =====================================================================
    static float hue2(float p, float q, float t) {
        t = ((t % 1f) + 1f) % 1f;
        if (t < 1f / 6f) return p + (q - p) * 6f * t;
        if (t < .5f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f;
        return p;
    }
    static int hsl(float h, float s, float l) {
        h = ((h % 1f) + 1f) % 1f;
        float q = l < .5f ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
        return Color.rgb(Math.round(clamp(hue2(p, q, h + 1f / 3f), 0, 1) * 255), Math.round(clamp(hue2(p, q, h), 0, 1) * 255),
                Math.round(clamp(hue2(p, q, h - 1f / 3f), 0, 1) * 255));
    }
    static void rgb2hsl(int c, float[] o) {
        float r = Color.red(c) / 255f, g = Color.green(c) / 255f, b = Color.blue(c) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f, h, s;
        if (max == min) { h = 0; s = 0; }
        else {
            float d = max - min;
            s = l > .5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) h = ((g - b) / d + (g < b ? 6f : 0f)) / 6f;
            else if (max == g) h = ((b - r) / d + 2f) / 6f;
            else h = ((r - g) / d + 4f) / 6f;
        }
        o[0] = h; o[1] = s; o[2] = l;
    }
    static int neon(int c) {
        float[] o = new float[3];
        rgb2hsl(c, o);
        if (o[2] < .13f || o[1] < .12f) return c;
        return hsl(o[0], 1f, Math.min(.6f, Math.max(.5f, o[2])));
    }
    static int lerpColor(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
    static int skinColor(Skin sk, int i) {
        int n = sk.c.length;
        switch (sk.p) {
            case P_STRIPE: return sk.c[(i / 8) % n];
            case P_MULTI: return sk.c[(i / (sk.w > 0 ? sk.w : 6)) % n];
            case P_DOTS: return (i % 10 < 3) ? sk.c[1 % n] : sk.c[0];
            case P_FADE: return lerpColor(sk.c[0], sk.c[1 % n], (sinf(i * .09f) + 1f) / 2f);
            default: return sk.c[0];
        }
    }
    static Skin flagSkin(int i) { return new Skin(P_MULTI, 5, FLAGS[i].c); }
    static Skin randSkin() {
        if (rand() < .4f) return SKINS[RND.nextInt(SKINS.length)];
        float h = rand();
        int[] pal = {hsl(h, 1f, .55f), hsl(h + .17f, 1f, .58f), hsl(h + .34f, 1f, .55f), hsl(h + .5f, 1f, .6f),
                hsl(h + .67f, 1f, .55f), hsl(h + .84f, 1f, .6f)};
        int pick = RND.nextInt(5);
        int p = pick < 2 ? P_MULTI : (pick == 2 ? P_STRIPE : (pick == 3 ? P_FADE : P_DOTS));
        if (p == P_MULTI) return new Skin(p, 0, pal);
        return new Skin(p, 0, pal[0], pal[1], pal[2]);
    }

    // =====================================================================
    // SPRITES (shared, created once)
    // =====================================================================
    static Bitmap GLOW, GLOSS_S, GLOSS_F;

    static synchronized void ensureSprites() {
        if (GLOW != null) return;
        int S = 64;
        GLOW = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(GLOW);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(S / 2f, S / 2f, S / 2f, new int[]{0xFFFFFFFF, 0x8CFFFFFF, 0x00FFFFFF},
                new float[]{0f, .3f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(S / 2f, S / 2f, S / 2f, p);
        GLOSS_S = makeGloss(0x99, 0x20);
        GLOSS_F = makeGloss(0xEB, 0x59);
    }
    static Bitmap makeGloss(int peakA, int midA) {
        int S = 64;
        Bitmap b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(S / 2f - .28f * S / 2f, S / 2f - .32f * S / 2f, S / 2f * 1.25f,
                new int[]{(peakA << 24) | 0xFFFFFF, (midA << 24) | 0xFFFFFF, 0x14000000}, new float[]{0f, .22f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(S / 2f, S / 2f, S / 2f, p);
        return b;
    }
    static Bitmap partBmp(HatPart q) {
        if (q.bmp == null) {
            int S = 64;
            Bitmap b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            int center = q.hot > .5f ? 0xFFFFFFFF : q.col;
            p.setShader(new RadialGradient(S / 2f, S / 2f, S / 2f, new int[]{center, q.col, q.col, q.col & 0xFFFFFF},
                    new float[]{0f, .35f, .62f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(S / 2f, S / 2f, S / 2f, p);
            q.bmp = b;
        }
        return q.bmp;
    }
    static final RectF TMPR = new RectF();
    static void drawHatPart(Canvas c, Paint pt, HatPart q, float cx, float cy, float r) { drawHatPart(c, pt, q, cx, cy, r, TMPR); }
    static void drawHatPart(Canvas c, Paint pt, HatPart q, float cx, float cy, float r, RectF tmp) {
        pt.setAlpha(Math.round(q.a * 255));
        tmp.set(cx - r, cy - r, cx + r, cy + r);
        c.drawBitmap(partBmp(q), null, tmp, pt);
    }

    // ---- vector crown (pure 2D canvas). cx = horizontal centre, by = bottom edge, s = half-width ----
    static void crownBody(Path q) {
        q.reset();
        q.moveTo(-.92f, 0f);
        q.lineTo(-1f, -1.05f);
        q.lineTo(-.72f, -.68f);
        q.lineTo(-.5f, -1.22f);
        q.lineTo(-.26f, -.74f);
        q.lineTo(0f, -1.5f);
        q.lineTo(.26f, -.74f);
        q.lineTo(.5f, -1.22f);
        q.lineTo(.72f, -.68f);
        q.lineTo(1f, -1.05f);
        q.lineTo(.92f, 0f);
        q.close();
    }
    static final float[] CROWN_TX = {-1f, -.5f, 0f, .5f, 1f};
    static final float[] CROWN_TY = {-1.05f, -1.22f, -1.5f, -1.22f, -1.05f};
    static void crownGem(Canvas c, Paint p, float x, float y, float r, int col) {
        p.setStyle(Paint.Style.FILL); p.setColor(col);
        c.drawCircle(x, y, r, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.035f); p.setColor(0xFF7a4700);
        c.drawCircle(x, y, r, p);
        p.setStyle(Paint.Style.FILL); p.setColor(0xCCFFFFFF);
        c.drawCircle(x - r * .35f, y - r * .35f, r * .3f, p);
    }
    static void drawCrown(Canvas c, Paint p, Path q, float cx, float by, float s) {
        if (s < .8f) return;
        c.save();
        c.translate(cx, by);
        c.scale(s, s);
        p.setShader(null); p.setColorFilter(null); p.setAlpha(255);
        p.setStrokeJoin(Paint.Join.ROUND); p.setStrokeCap(Paint.Cap.ROUND);
        // soft shadow
        crownBody(q);
        p.setStyle(Paint.Style.FILL); p.setColor(0x44000000);
        c.save(); c.translate(.05f, .09f); c.drawPath(q, p); c.restore();
        // gold body
        p.setColor(0xFFFFC62E);
        c.drawPath(q, p);
        // faceted prongs (light left, dark right) clipped to the body
        c.save();
        c.clipPath(q);
        for (int i = 0; i < 5; i++) {
            float tx = CROWN_TX[i], ty = CROWN_TY[i];
            q.reset(); q.moveTo(tx, ty); q.lineTo(tx - .24f, -.5f); q.lineTo(tx, -.5f); q.close();
            p.setColor(0x80FFF3A8); c.drawPath(q, p);
            q.reset(); q.moveTo(tx, ty); q.lineTo(tx, -.5f); q.lineTo(tx + .24f, -.5f); q.close();
            p.setColor(0x38A05A00); c.drawPath(q, p);
        }
        c.restore();
        // band
        p.setColor(0xFFE8A112); c.drawRect(-.94f, -.44f, .94f, 0f, p);
        p.setColor(0xFFFFE27A); c.drawRect(-.94f, -.44f, .94f, -.35f, p);
        p.setColor(0xFFB36E00); c.drawRect(-.93f, -.07f, .93f, 0f, p);
        // outline
        crownBody(q);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.07f); p.setColor(0xFF8a5200);
        c.drawPath(q, p);
        p.setStrokeWidth(.04f);
        c.drawLine(-.94f, -.44f, .94f, -.44f, p);
        // jewels on band
        crownGem(c, p, 0f, -.22f, .15f, 0xFFE62E4D);
        crownGem(c, p, -.5f, -.22f, .1f, 0xFF2EA8FF);
        crownGem(c, p, .5f, -.22f, .1f, 0xFF2EA8FF);
        crownGem(c, p, -.78f, -.22f, .07f, 0xFF45E07A);
        crownGem(c, p, .78f, -.22f, .07f, 0xFF45E07A);
        // pearls on prong tips
        for (int i = 0; i < 5; i++)
            crownGem(c, p, CROWN_TX[i], CROWN_TY[i] - .02f, i == 2 ? .17f : .13f, i == 2 ? 0xFFFF4D6A : 0xFFFFFFFF);
        // sparkle
        p.setStyle(Paint.Style.FILL); p.setColor(0xE6FFFFFF);
        q.reset();
        q.moveTo(-.62f, -1.12f); q.lineTo(-.56f, -.98f); q.lineTo(-.42f, -.92f); q.lineTo(-.56f, -.86f);
        q.lineTo(-.62f, -.72f); q.lineTo(-.68f, -.86f); q.lineTo(-.82f, -.92f); q.lineTo(-.68f, -.98f); q.close();
        c.drawPath(q, p);
        c.restore();
        p.setStyle(Paint.Style.FILL); p.setStrokeWidth(0f);
    }

    // =====================================================================
    // PREMIUM HATS - pure 2D canvas vectors (feathers, wings, crystals, laurel). No bitmaps, nothing
    // allocated per frame. kind: 2 Peacock  3 Phoenix  4 Angel  5 Chief  6 Ice Crown  7 Laurel
    // Unit space like the crown: x = -1..1 (half width), y = 0 is the base, negative y = up.
    // =====================================================================
    static final long HAT_T0 = System.nanoTime();
    static volatile int HAT_LOD = 0;   // 0 = full detail, 2 = lighter (set by the adaptive quality in the game loop)
    static final ThreadLocal<HatGfx> HAT_GFX = new ThreadLocal<HatGfx>() {
        @Override protected HatGfx initialValue() { return new HatGfx(); }
    };

    static final class HatGfx {
        final Path v = new Path(), w = new Path(), s = new Path();
        final RectF r = new RectF();
        final float[] bl = new float[72];
        final float[] lx = new float[10], ly = new float[10], rx = new float[10], ry = new float[10];
        final Paint pg = new Paint(Paint.FILTER_BITMAP_FLAG);
        final HashMap<Integer, ColorFilter> cfs = new HashMap<Integer, ColorFilter>();
        HatGfx() { pg.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD)); }

        // ---- small helpers ----
        void glow(Canvas c, float x, float y, float size, int col, int alpha) {
            if (GLOW == null) return;
            int k = col | 0xFF000000;
            ColorFilter cf = cfs.get(k);
            if (cf == null) { cf = new PorterDuffColorFilter(k, PorterDuff.Mode.SRC_IN); cfs.put(k, cf); }
            pg.setColorFilter(cf);
            pg.setAlpha(Math.max(0, Math.min(255, alpha)));
            r.set(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f);
            c.drawBitmap(GLOW, null, r, pg);
        }

        void oval(Canvas c, Paint p, float x, float y, float rx2, float ry2, int col) {
            p.setStyle(Paint.Style.FILL); p.setColor(col);
            r.set(x - rx2, y - ry2, x + rx2, y + ry2);
            c.drawOval(r, p);
        }

        void dot(Canvas c, Paint p, float x, float y, float rad, int col, int edge) {
            p.setStyle(Paint.Style.FILL); p.setColor(col);
            c.drawCircle(x, y, rad, p);
            if (edge != 0) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.035f); p.setColor(edge);
                c.drawCircle(x, y, rad, p);
            }
        }

        void gem(Canvas c, Paint p, float x, float y, float rad, int col) {
            dot(c, p, x, y, rad, col, 0xFF5a3300);
            p.setStyle(Paint.Style.FILL); p.setColor(0xCCFFFFFF);
            c.drawCircle(x - rad * .33f, y - rad * .35f, rad * .3f, p);
        }

        void star(Canvas c, Paint p, float x, float y, float rr, int col) {
            if (rr < .02f) return;
            s.reset();
            s.moveTo(x, y - rr);
            s.lineTo(x + rr * .22f, y - rr * .22f); s.lineTo(x + rr, y);
            s.lineTo(x + rr * .22f, y + rr * .22f); s.lineTo(x, y + rr);
            s.lineTo(x - rr * .22f, y + rr * .22f); s.lineTo(x - rr, y);
            s.lineTo(x - rr * .22f, y - rr * .22f); s.close();
            p.setStyle(Paint.Style.FILL); p.setColor(col);
            c.drawPath(s, p);
        }

        static float pkExp(float pk) { return (float) (-0.6931472 / Math.log(pk)); }

        static float shape(float u, float ex) {
            if (u <= 0f || u >= 1f) return 0f;
            return (float) Math.pow(Math.sin(3.14159265 * Math.pow(u, ex)), .8);
        }

        /** feather outline: length L, half width W, tip sideways bend, widest point pk (0..1), edge flutter wob */
        void vane(Path q, float L, float W, float bend, float pk, float wob, float t, float u0, float u1) {
            float ex = pkExp(pk);
            int n = 9;
            for (int k = 0; k < n; k++) {
                float u = u0 + (u1 - u0) * k / (n - 1f);
                float h = W * shape(u, ex) * (1f + wob * sinf(u * 13f - t * 8f));
                float sxp = bend * L * u * u, syp = -L * u;
                lx[k] = sxp - h; ly[k] = syp; rx[k] = sxp + h; ry[k] = syp;
            }
            q.reset();
            q.moveTo(lx[0], ly[0]);
            for (int k = 1; k < n; k++) q.quadTo(lx[k - 1], ly[k - 1], (lx[k - 1] + lx[k]) * .5f, (ly[k - 1] + ly[k]) * .5f);
            q.lineTo(lx[n - 1], ly[n - 1]);
            q.lineTo(rx[n - 1], ry[n - 1]);
            for (int k = n - 2; k >= 0; k--) q.quadTo(rx[k + 1], ry[k + 1], (rx[k + 1] + rx[k]) * .5f, (ry[k + 1] + ry[k]) * .5f);
            q.close();
        }

        /** one feather: base at (x,y), rotated rot degrees (0 = up, + = clockwise). Layers: vane, soft inner vane, coloured tip,
         *  outline, barbs, spine and (peacock) an eye. */
        void feather(Canvas c, Paint p, float x, float y, float rot, float L, float W, float bend, float pk, float wob, float t,
                     int main, int edge, int hi, int tip, float tipFrom, boolean eye, boolean barbs) {
            boolean full = HAT_LOD < 2;
            c.save();
            c.translate(x, y);
            c.rotate(rot);
            vane(v, L, W, bend, pk, wob, t, 0f, 1f);
            p.setStyle(Paint.Style.FILL); p.setColor(main);
            c.drawPath(v, p);
            if (full && hi != 0) {
                vane(w, L * .94f, W * .52f, bend, pk, wob, t, .05f, 1f);
                p.setColor((hi & 0xFFFFFF) | 0x80000000);
                c.drawPath(w, p);
            }
            if (tip != 0) {
                vane(w, L, W, bend, pk, wob, t, tipFrom, 1f);
                p.setColor(tip);
                c.drawPath(w, p);
            }
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.05f); p.setColor(edge);
            c.drawPath(v, p);
            if (full && barbs) {
                float ex = pkExp(pk);
                int m = 0;
                for (int j = 1; j <= 6; j++) {
                    float u = .1f + j * .125f;
                    float h = W * shape(u, ex) * (1f + wob * sinf(u * 13f - t * 8f)) * .92f;
                    float sxp = bend * L * u * u, syp = -L * u;
                    bl[m++] = sxp; bl[m++] = syp; bl[m++] = sxp - h; bl[m++] = syp - L * .07f;
                    bl[m++] = sxp; bl[m++] = syp; bl[m++] = sxp + h; bl[m++] = syp - L * .07f;
                }
                p.setStrokeWidth(.022f); p.setColor((edge & 0xFFFFFF) | 0x90000000);
                c.drawLines(bl, 0, m, p);
            }
            s.reset();
            s.moveTo(0f, 0f);
            s.quadTo(0f, -L * .48f, bend * L * .9216f, -L * .96f);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.035f); p.setColor((edge & 0xFFFFFF) | 0xC0000000);
            c.drawPath(s, p);
            if (eye) {
                float u = .76f, ex2 = bend * L * u * u, ey = -L * u;
                oval(c, p, ex2, ey, W * .80f, W * 1.0f, 0xFFE7B83A);
                oval(c, p, ex2, ey, W * .62f, W * .78f, 0xFF18A39C);
                oval(c, p, ex2, ey, W * .43f, W * .55f, 0xFF1C45C8);
                oval(c, p, ex2, ey - W * .02f, W * .23f, W * .31f, 0xFF081447);
                oval(c, p, ex2 - W * .09f, ey - W * .17f, W * .09f, W * .12f, 0xCCFFFFFF);
            }
            c.restore();
        }

        // ---- 2 : PEACOCK - fan of eyed feathers, golden crest, sapphire cap ----
        static final float[] PK_A = {-64f, -44f, -22f, 0f, 22f, 44f, 64f};
        static final float[] PK_L = {1.45f, 1.85f, 2.2f, 2.55f, 2.2f, 1.85f, 1.45f};
        static final int[] PK_O = {0, 6, 1, 5, 2, 4, 3};
        void peacock(Canvas c, Paint p, float t) {
            glow(c, 0f, -1.1f, 3.8f, 0xFF2FE0B0, 70);
            for (int o = 0; o < 7; o++) {
                int i = PK_O[o];
                float rot = PK_A[i] + 3.2f * sinf(t * 2.1f + i * .9f);
                feather(c, p, PK_A[i] / 64f * .34f, -.06f, rot, PK_L[i], .34f, PK_A[i] / 64f * .10f, .74f, .05f, t,
                        0xFF1FA871, 0xFF0A4A35, 0xFF86F0BE, 0, 0f, true, true);
            }
            // golden crest stalks with pearls
            for (int k = -1; k <= 1; k++) {
                float tx = k * .22f + .05f * sinf(t * 3f + k), ty = -1.0f - (k == 0 ? .14f : 0f);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.035f); p.setColor(0xFFC98B12);
                c.drawLine(0f, -.2f, tx, ty, p);
                dot(c, p, tx, ty, .065f, 0xFFFFE27A, 0xFF8a5200);
            }
            // gold cap + sapphire
            v.reset(); v.moveTo(-.52f, 0f); v.lineTo(-.4f, -.26f); v.lineTo(.4f, -.26f); v.lineTo(.52f, 0f); v.close();
            p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFC62E); c.drawPath(v, p);
            p.setColor(0xFFFFE27A); c.drawRect(-.4f, -.26f, .4f, -.19f, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.05f); p.setColor(0xFF8a5200); c.drawPath(v, p);
            gem(c, p, 0f, -.12f, .115f, 0xFF2EA8FF);
            star(c, p, -.62f, -1.55f + .05f * sinf(t * 3f), .2f * (.55f + .45f * sinf(t * 4.5f)), 0xE6FFFFFF);
        }

        // ---- 3 : PHOENIX - flickering flame plumes, hot core, rising embers ----
        static final float[] PH_A = {-56f, -30f, 0f, 30f, 56f};
        static final float[] PH_L = {1.7f, 2.25f, 2.7f, 2.25f, 1.7f};
        static final int[] PH_O = {0, 4, 1, 3, 2};
        void phoenix(Canvas c, Paint p, float t) {
            glow(c, 0f, -1.3f, 4.4f, 0xFFFF7A1A, 110 + (int) (45f * sinf(t * 6f)));
            for (int o = 0; o < 5; o++) {
                int i = PH_O[o];
                float bend = PH_A[i] / 56f * .18f + .22f * sinf(t * 3.1f + i * 1.3f);
                feather(c, p, PH_A[i] / 56f * .3f, -.06f, PH_A[i] * .9f + 4f * sinf(t * 2.6f + i), PH_L[i], .37f, bend, .5f, .22f, t,
                        0xFFE8281C, 0xFF7E0E10, 0xFFFF9B2B, 0xFFFFD84A, .5f, false, false);
            }
            feather(c, p, 0f, -.06f, 2f * sinf(t * 3.3f), 1.75f, .2f, .12f * sinf(t * 3.9f), .5f, .2f, t,
                    0xFFFFA52E, 0xFFE8281C, 0xFFFFF1A6, 0xFFFFFFFF, .45f, false, false);
            for (int k = 0; k < 6; k++) {
                float ph = (t * .6f + k * .167f) % 1f;
                float ex = sinf(k * 2.4f + t * 2f) * .75f * (.4f + ph), ey = -1.1f - ph * 1.9f;
                p.setStyle(Paint.Style.FILL);
                p.setColor((((int) ((1f - ph) * 230f)) << 24) | 0xFFD84A);
                c.drawCircle(ex, ey, .07f * (1f - ph * .6f), p);
            }
            v.reset(); v.moveTo(-.5f, 0f); v.lineTo(-.38f, -.24f); v.lineTo(.38f, -.24f); v.lineTo(.5f, 0f); v.close();
            p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFC62E); c.drawPath(v, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.05f); p.setColor(0xFF8a5200); c.drawPath(v, p);
            gem(c, p, 0f, -.12f, .11f, 0xFFE62E4D);
        }

        // ---- 4 : ANGEL - feathered wings both sides + golden halo ----
        void angel(Canvas c, Paint p, float t) {
            float flap = sinf(t * 3.3f);
            glow(c, 0f, -1.4f, 4.6f, 0xFFBFE6FF, 70);
            for (int side = -1; side <= 1; side += 2) {
                for (int k = 6; k >= 0; k--) {
                    float a = 16f + k * 13f;
                    float rot = side * (a + 4f * flap + k * .6f);
                    feather(c, p, side * .66f, -.1f, rot, 2.35f - k * .17f, .27f, side * .10f, .55f, 0f, t,
                            0xFFF4F8FF, 0xFF8FA6CF, 0xFFFFFFFF, k > 3 ? 0xFFBFD9FF : 0, .72f, false, true);
                }
            }
            float hy = -1.05f + .05f * sinf(t * 2.4f);
            glow(c, 0f, hy, 2.3f, 0xFFFFE066, 150);
            r.set(-.62f, hy - .2f, .62f, hy + .2f);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.14f); p.setColor(0xFFFFC62E); c.drawOval(r, p);
            p.setStrokeWidth(.06f); p.setColor(0xFFFFF2A8); c.drawOval(r, p);
            star(c, p, -1.0f, -2.0f, .2f * (.55f + .45f * sinf(t * 4.2f)), 0xE6FFFFFF);
            star(c, p, 1.0f, -1.75f, .17f * (.55f + .45f * sinf(t * 4.2f + 2f)), 0xE6FFFFFF);
        }

        // ---- 5 : CHIEF - war bonnet: fan of eagle feathers over a beaded headband ----
        void chief(Canvas c, Paint p, float t) {
            for (int o = 0; o < 11; o++) {
                int k = (o % 2 == 0) ? o / 2 : 10 - o / 2;
                float a = (k - 5) * 15f, rad = a * .0174533f;
                float bx = sinf(rad) * .78f, by = -.3f + (1f - cosf(rad)) * .28f;
                float L = 1.55f + 1.05f * cosf(rad * .95f);
                boolean white = (k & 1) == 0;
                feather(c, p, bx, by, a * .92f + 2.2f * sinf(t * 2f + k), L, .23f, a / 80f * .12f, .55f, 0f, t,
                        white ? 0xFFFFF4DC : 0xFFE03A2C, white ? 0xFF5C3A1C : 0xFF6F1410, white ? 0xFFFFFFFF : 0xFFFF8F7A,
                        white ? 0xFF2A1B12 : 0xFFFFF4DC, .62f, false, true);
            }
            r.set(-.96f, -.36f, .96f, 0f);
            p.setStyle(Paint.Style.FILL); p.setColor(0xFFB5301F); c.drawRoundRect(r, .08f, .08f, p);
            p.setColor(0xFFFFC73A); c.drawRect(-.94f, -.35f, .94f, -.29f, p);
            p.setColor(0xFF1FB5B0); c.drawRect(-.94f, -.07f, .94f, -.01f, p);
            for (int j = -3; j <= 3; j++) dot(c, p, j * .26f, -.18f, .065f, (j & 1) == 0 ? 0xFFFFF4DC : 0xFF1FB5B0, 0xFF4B140D);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.05f); p.setColor(0xFF4B140D); c.drawRoundRect(r, .08f, .08f, p);
            dot(c, p, 0f, -.18f, .2f, 0xFFFFC73A, 0xFF8a5200);
            gem(c, p, 0f, -.18f, .1f, 0xFFE62E4D);
        }

        // ---- 6 : ICE CROWN - faceted crystal spikes, snowy band, twinkling glints ----
        static final float[] IC_X = {-.9f, -.6f, -.3f, 0f, .3f, .6f, .9f};
        static final float[] IC_H = {1.0f, 1.5f, 1.2f, 2.0f, 1.2f, 1.5f, 1.0f};
        void ice(Canvas c, Paint p, float t) {
            glow(c, 0f, -.9f, 3.4f, 0xFF7FD8FF, 80);
            for (int o = 0; o < 7; o++) {
                int k = (o % 2 == 0) ? o / 2 : 6 - o / 2;
                float x = IC_X[k], h = IC_H[k], wv = .19f + .02f * h;
                v.reset(); v.moveTo(x - .17f, -.2f); v.lineTo(x - wv, -h * .55f); v.lineTo(x, -h); v.lineTo(x + wv, -h * .5f); v.lineTo(x + .17f, -.2f); v.close();
                p.setStyle(Paint.Style.FILL); p.setColor(0xFF86D9FF); c.drawPath(v, p);
                v.reset(); v.moveTo(x, -h); v.lineTo(x - wv, -h * .55f); v.lineTo(x - .17f, -.2f); v.lineTo(x, -.2f); v.close();
                p.setColor(0xFFD2F5FF); c.drawPath(v, p);
                v.reset(); v.moveTo(x, -h); v.lineTo(x + wv, -h * .5f); v.lineTo(x + .17f, -.2f); v.lineTo(x, -.2f); v.close();
                p.setColor(0xFF3EA5EE); c.drawPath(v, p);
                v.reset(); v.moveTo(x - .17f, -.2f); v.lineTo(x - wv, -h * .55f); v.lineTo(x, -h); v.lineTo(x + wv, -h * .5f); v.lineTo(x + .17f, -.2f); v.close();
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.045f); p.setColor(0xFF1B6EB4); c.drawPath(v, p);
            }
            r.set(-.96f, -.3f, .96f, 0f);
            p.setStyle(Paint.Style.FILL); p.setColor(0xFFEAFBFF); c.drawRoundRect(r, .08f, .08f, p);
            p.setColor(0xFFA8DEF7); c.drawRect(-.94f, -.08f, .94f, -.01f, p);
            for (int j = -4; j <= 4; j++) dot(c, p, j * .2f, -.3f, .1f, 0xFFFFFFFF, 0);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.045f); p.setColor(0xFF5FB4E8); c.drawRoundRect(r, .08f, .08f, p);
            v.reset(); v.moveTo(0f, -.27f); v.lineTo(.12f, -.15f); v.lineTo(0f, -.03f); v.lineTo(-.12f, -.15f); v.close();
            p.setStyle(Paint.Style.FILL); p.setColor(0xFF2E8BFF); c.drawPath(v, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.035f); p.setColor(0xFF124C86); c.drawPath(v, p);
            for (int k = 1; k <= 5; k += 2)
                star(c, p, IC_X[k], -IC_H[k] - .05f, .24f * (.55f + .45f * sinf(t * 4f + k * 1.7f)), 0xF0FFFFFF);
        }

        // ---- 7 : LAUREL - golden leaf wreath with ruby ----
        void laurel(Canvas c, Paint p, float t) {
            glow(c, 0f, -.7f, 3.0f, 0xFFFFD34D, 55);
            r.set(-.96f, -1.15f, .96f, .95f);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(.06f); p.setColor(0xFF8A5A0A);
            c.drawArc(r, 180f, 180f, false, p);
            for (int side = -1; side <= 1; side += 2) {
                for (int k = 0; k < 7; k++) {
                    float phi = 3.14159f - k * (1.5708f / 6.6f);
                    float px = cosf(phi) * .96f, py = -.1f - sinf(phi) * 1.05f;
                    float tx = sinf(phi) * .96f, ty = cosf(phi) * 1.05f;
                    float th = atan2f(tx, -ty) * 57.29578f;
                    float sz = 1f - k * .045f;
                    float bx = side == -1 ? px : -px, base = side == -1 ? th : -th;
                    for (int d = -1; d <= 1; d += 2)
                        feather(c, p, bx, py, base + d * 36f + 1.5f * sinf(t * 2f + k), .8f * sz, .19f * sz, 0f, .5f, 0f, t,
                                0xFFE8B52E, 0xFF8A5A0A, 0xFFFFE58A, 0, 0f, false, false);
                }
            }
            gem(c, p, 0f, -1.15f, .16f, 0xFFE62E4D);
            star(c, p, .78f, -1.45f, .2f * (.55f + .45f * sinf(t * 4f)), 0xE6FFFFFF);
        }
    }

    /** draws a premium vector hat. cx = horizontal centre, by = base edge, s = half width (same anchor as the crown) */
    static void drawHatKind(Canvas c, Paint p, Path q, int kind, float cx, float by, float s) {
        if (kind == 1) { drawCrown(c, p, q, cx, by, s); return; }
        if (s < .8f) return;
        HatGfx g = HAT_GFX.get();
        float t = (System.nanoTime() - HAT_T0) / 1e9f;
        c.save();
        c.translate(cx, by);
        c.scale(s, s);
        p.setShader(null); p.setColorFilter(null); p.setXfermode(null); p.setAlpha(255);
        p.setStrokeJoin(Paint.Join.ROUND); p.setStrokeCap(Paint.Cap.ROUND);
        switch (kind) {
            case 2: g.peacock(c, p, t); break;
            case 3: g.phoenix(c, p, t); break;
            case 4: g.angel(c, p, t); break;
            case 5: g.chief(c, p, t); break;
            case 6: g.ice(c, p, t); break;
            case 7: g.laurel(c, p, t); break;
            default: break;
        }
        c.restore();
        p.setStyle(Paint.Style.FILL); p.setStrokeWidth(0f); p.setAlpha(255);
    }

    /** hat / glasses drawn on a right-facing head at (cx,cy) with head scale S (menu preview + thumbnails) */
    static void drawHatAt(Canvas c, Paint ps, Path q, RectF tmp, Hat h, float cx, float cy, float S) {
        if (h.kind >= 1) { drawHatKind(c, ps, q, h.kind, cx - 1.0f * S, cy - .45f * S, S * .8f); return; }
        for (int i = 0; i < h.p.size(); i++) {
            HatPart hq = h.p.get(i);
            float x = cx + (hq.x + (h.f ? 0f : -1.25f)) * S, y = cy + hq.y * S - hq.h * S * .28f, rad = hq.s * S * .85f;
            drawHatPart(c, ps, hq, x, y, rad, tmp);
        }
        ps.setAlpha(255);
    }

    // ---- live skin animation: travelling shimmer + breathing + moving sparkles ----
    static int liveColor(int base, int i, float t, float seed) {
        int c = base | 0xFF000000;
        float s = sinf(i * .3f - t * 12f + seed);              // fast white-hot band sweeping to the head
        if (s > .55f) c = lerpColor(c, 0xFFFFFFFF, (s - .55f) * 1.7f);
        float br = .16f * sinf(t * 7f + i * .17f + seed) + .06f * sinf(t * 5f + seed);   // glowing pulse
        if (br > .012f) c = lerpColor(c, 0xFFFFFFFF, br);
        else if (br < -.012f) c = lerpColor(c, 0xFF000000, -br * .6f);
        return c | 0xFF000000;
    }
    static boolean sparkle(int i, float t, int seed) { return ((i + seed + (int) (t * 18f)) % 13) == 0; }

    /** One snake look shared by the menu preview, the skin picker and hat thumbnails (same as gameplay). */
    static class SkinArt {
        static final int N = 44;
        final float[] xs = new float[N], ys = new float[N];
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ps = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        final RectF tmp = new RectF();
        final Path hp = new Path();
        final Paint pg = new Paint(Paint.FILTER_BITMAP_FLAG);
        final HashMap<Integer, ColorFilter> cfs = new HashMap<Integer, ColorFilter>();
        { pg.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD)); }

        void glowAt(Canvas c, float x, float y, float size, int col, int alpha) {
            int k = col | 0xFF000000;
            ColorFilter cf = cfs.get(k);
            if (cf == null) { cf = new PorterDuffColorFilter(k, PorterDuff.Mode.SRC_IN); cfs.put(k, cf); }
            pg.setColorFilter(cf);
            pg.setAlpha(alpha);
            tmp.set(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f);
            c.drawBitmap(GLOW, null, tmp, pg);
        }

        void head(Canvas c, float hx, float hy, float R, float a, int col, float outline, Hat hat, float t, float seed) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF000000); c.drawCircle(hx, hy, R + outline + R * .1f, p);
            p.setColor(col); c.drawCircle(hx, hy, R, p);
            ps.setAlpha(255);
            tmp.set(hx - R, hy - R, hx + R, hy + R);
            c.drawBitmap(GLOSS_S, null, tmp, ps);
            float ca = cosf(a), sa = sinf(a), er = R * .59f;
            boolean blink = ((t + seed) % 3.7f) < .12f;
            for (int zi = 0; zi < 2; zi++) {
                float z = zi == 0 ? -1f : 1f;
                float ex = hx + ca * R * .59f - sa * z * R * .38f, ey = hy + sa * R * .59f + ca * z * R * .38f;
                p.setColor(0xFFFFFFFF);
                if (blink) { tmp.set(ex - er, ey - er * .2f, ex + er, ey + er * .2f); c.drawOval(tmp, p); continue; }
                c.drawCircle(ex, ey, er, p);
                float px = ex + ca * R * .28f, py = ey + sa * R * .28f;
                p.setColor(0xFF07070d); c.drawCircle(px, py, er * .55f, p);
                p.setColor(0xFFFFFFFF); c.drawCircle(px + er * .22f, py - er * .18f, er * .22f, p);
            }
            if (hat != null) drawHatAt(c, ps, hp, tmp, hat, hx, hy, R * .85f);
        }

        void snake(Canvas c, Skin sk, float x0, float x1, float cy, float R, float amp, float freq, float t, float ph, float outline, boolean locked, Hat hat) {
            int shift = (int) (t * 12f), seed = (int) (ph * 10f);
            for (int i = 0; i < N; i++) {
                float u = i / (float) (N - 1);
                xs[i] = x1 - u * (x1 - x0);
                ys[i] = cy + amp * sinf(u * freq - t * 2.4f + ph);
            }
            p.setStyle(Paint.Style.FILL);
            if (!locked) {
                for (int i = N - 1; i >= 0; i -= 2) {
                    float u = i / (float) (N - 1), rr = R * (1f - .5f * u * u);
                    float pul = .65f + .35f * sinf(t * 9f + i * .35f + ph);
                    int gc = skinColor(sk, ((sk.p == P_FADE || sk.p == P_DOTS) ? i * 2 : i) + shift);
                    glowAt(c, xs[i], ys[i], rr * 3.2f * pul, gc, 190);
                }
            }
            for (int i = N - 1; i >= 1; i--) {
                float u = i / (float) (N - 1), rr = R * (1f - .5f * u * u);
                p.setColor(0x2E000000);
                tmp.set(xs[i] - rr * 1.1f, ys[i] + rr * .48f - rr * .42f, xs[i] + rr * .9f, ys[i] + rr * .48f + rr * .42f);
                c.drawOval(tmp, p);
            }
            p.setColor(0xFF000000);
            for (int i = N - 1; i >= 1; i--) {
                float u = i / (float) (N - 1), rr = R * (1f - .5f * u * u);
                c.drawCircle(xs[i], ys[i], rr + outline + rr * .1f, p);
            }
            ps.setAlpha(255);
            for (int i = N - 1; i >= 1; i--) {
                float u = i / (float) (N - 1), rr = R * (1f - .5f * u * u);
                int ci = ((sk.p == P_FADE || sk.p == P_DOTS) ? i * 2 : i) + shift;
                int col = liveColor(skinColor(sk, ci), i, t, ph);
                if (locked) col = lerpColor(col, 0xFF20202c, .6f) | 0xFF000000;
                p.setColor(col);
                c.drawCircle(xs[i], ys[i], rr, p);
                tmp.set(xs[i] - rr, ys[i] - rr, xs[i] + rr, ys[i] + rr);
                c.drawBitmap(GLOSS_S, null, tmp, ps);
                if (!locked && rr > 5f && sparkle(i, t, seed)) {
                    p.setColor(0xD0FFFFFF);
                    c.drawCircle(xs[i] - rr * .28f, ys[i] - rr * .32f, rr * .2f * (.6f + .4f * sinf(t * 12f + i)), p);
                }
            }
            int hc = liveColor(skinColor(sk, shift), 0, t, ph);
            if (locked) hc = lerpColor(hc, 0xFF20202c, .6f) | 0xFF000000;
            head(c, xs[0], ys[0], R * 1.08f, atan2f(ys[0] - ys[1], xs[0] - xs[1]), hc, outline, hat, t, ph);
        }
    }

    // live hat thumbnail (drawn on the current skin colour) used on the hat chips in the menu
    private static class HatThumb extends Drawable {
        final MainActivity act;
        final int idx;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ps = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        final Path q = new Path();
        final SkinArt art = new SkinArt();
        HatThumb(MainActivity a, int i) { act = a; idx = i; }
        @Override public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            c.save();
            c.translate(b.left, b.top);
            float sc = b.width() / 170f;
            c.scale(sc, sc);
            c.translate(-415f, -15f);
            art.head(c, 520f, 100f, 34f, 0f, skinColor(act.curSkin, 0) | 0xFF000000, 3f, HATS[idx], 1f, 0f);
            c.restore();
        }
        private final RectF TMPR_T = new RectF();
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    // live background preview (same tile + same zoom maths as the game)
    private static class BgPreview extends View {
        final MainActivity act;
        final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Paint pb = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Matrix m = new Matrix();
        final Path clip = new Path();
        final RectF r = new RectF();
        BitmapShader sh;
        int shFor = -1;
        BgPreview(MainActivity a) { super(a); act = a; }
        @Override protected void onMeasure(int wSpec, int hSpec) {
            setMeasuredDimension(View.MeasureSpec.getSize(wSpec), act.dp(130));
        }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = act.dp(14);
            Bg bg = BGS[act.bgIdx];
            r.set(0, 0, w, h);
            clip.reset();
            clip.addRoundRect(r, rad, rad, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            c.drawColor(bg.color);
            Bitmap t = bg.tile;
            if (t != null) {
                if (sh == null || shFor != act.bgIdx) {
                    sh = new BitmapShader(t, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                    shFor = act.bgIdx;
                    p.setShader(sh);
                }
                float u = w / act.camDist * act.bgZoom;
                m.setScale(bg.w * u / t.getWidth(), bg.h * u / t.getHeight());
                m.postTranslate(w / 2f, h / 2f);
                sh.setLocalMatrix(m);
                c.drawRect(0, 0, w, h, p);
            } else postInvalidateDelayed(150);
            c.restore();
            pb.setStyle(Paint.Style.STROKE); pb.setStrokeWidth(2); pb.setColor(0x66FFFFFF);
            r.set(1, 1, w - 1, h - 1);
            c.drawRoundRect(r, rad, rad, pb);
        }
    }

    // =====================================================================
    // FIELDS
    // =====================================================================
    private final Object lock = new Object();
    private SurfaceView surface;
    private SurfaceHolder holder;
    private Loop loop;
    private boolean useHw = true;
    private int W = 1, H = 1;
    private float ppu = 20f, density = 2f;
    private float camDist = 30f;
    private final long startNs = System.nanoTime();
    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());

    // views
    private View hudV, menuV, overV;
    private TextView tvPn, tvLen, tvRank, tvLb, tvBt, tvToast, btnPause, btnSnd, btnBg, boostBtn;
    private TextView btnResume, btnNext, btnPlay, btnAgain, btnToMenu;
    private TextView tvWhy, tvRl, tvRkl, tvRb, tvBn, tvZn;
    private FrameLayout bbar;
    private View bi;
    private EditText etName;
    private View[] pages = new View[4];
    private PreviewView preview;
    private SwatchView[] skinSw = new SwatchView[SKINS.length];
    private LinearLayout[] flagBtn = new LinearLayout[FLAGS.length];
    private TextView[] hatChips = new TextView[HATS.length];
    private View[] bgDots = new View[BGS.length];
    private LinearLayout custBox;
    private TextView[] patChips = new TextView[4];
    private View c1Chip, c2Chip;
    private int menuStep = 0;
    private float ppuT = 20f, shake = 0f;
    private TextView btnExit, btnExitOver, btnPrev;
    private TextView btnRevive, btnDouble, btnGiant, btnRefill, btnBackMenu, btnPrivacy;
    private TextView[] flagTxt = new TextView[FLAGS.length];
    private SeekBar sbZoomRef;
    private final float[] wx = new float[8], wy = new float[8], wt = new float[8];
    private final int[] wc = new int[8];
    private int wHead = 0;

    // game state
    private volatile int state = ST_MENU;
    private int best, ate, fr, playerRank = 1;
    private float fx, fz, gt, camX, camY, dtG = .016f, lbT, uiT, cullT, fgT = -9f, aVis;
    private float boostFuel = 10f, boostMax = 10f, boostRefillAt = 100f, boostMassMark = 15f, neonPower = 1f;
    private volatile boolean boostIn, btnOn;
    private int botN = 32, bgIdx = 0, curHat = 0;
    private volatile float bgZoom = 1f;   // background-only zoom (menu slider), independent of camera zoom
    private boolean muted = false;
    private Skin curSkin = SKINS[0];
    private Skin custom = new Skin(P_STRIPE, 0, 0xFFff3b9a, 0xFF3bd1ff);
    private Snake P, leader;
    private final ArrayList<Snake> snakes = new ArrayList<Snake>();
    private final ArrayList<Food> food = new ArrayList<Food>();
    private final HashMap<Integer, Cell> fgM = new HashMap<Integer, Cell>();
    private final ArrayList<Snake> big = new ArrayList<Snake>();
    private final ArrayList<Snake> pendS = new ArrayList<Snake>(), pendO = new ArrayList<Snake>();
    private String rankTxt = "Rank 1/1";
    private CharSequence lbText = "";

    // particles
    private final float[] px = new float[PN], py = new float[PN], pvx = new float[PN], pvy = new float[PN],
            ps = new float[PN], pl = new float[PN], pm = new float[PN];
    private final int[] pc = new int[PN];
    private int pHead = 0;

    // input
    private volatile int joyId = -1, btnId = -1;
    private float joyOx, joyOy, joyX, joyY;
    private final int[] sloc = new int[2];

    // render objects
    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCrown = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path crownPath = new Path();
    private final Path finPath = new Path();   // feather fin shape (reused, no per-frame allocation)
    private final ArrayList<View> bgPrevs = new ArrayList<View>();
    private final Paint pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBg = new Paint();
    private final Paint pGlow = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint pSpr = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rc = new RectF();
    private final Matrix bgM = new Matrix();
    private final Path arrow = new Path();
    private BitmapShader bgShader;
    private int bgShaderFor = -1;
    private RadialGradient fxShader;
    private int fxColor, fxW, fxH;
    private final Paint pFx = new Paint();
    private final SparseArray<ColorFilter> cfCache = new SparseArray<ColorFilter>();

    // sound
    private SoundPool sp;
    private MediaPlayer bgm;
    private boolean musicOn = true;
    private TextView btnMusic;
    private int sEat, sStartId, sDie, sKill, sGhost;
    private boolean boostWas;
    private long boostSfxMs;
    private long lastEatMs;

    // =====================================================================
    // LIFECYCLE
    // =====================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.main);
        density = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("slither_arena", MODE_PRIVATE);
        ensureSprites();
        loadPrefs();
        initWorld();
        initViews();
        initMenu();
        buildExtraUi();
        initAds();
        adH.postDelayed(adKeepWarm, 30000L);
        initSound();
        initMusic();
        pText.setTypeface(Typeface.DEFAULT_BOLD);
        pText.setTextAlign(Paint.Align.CENTER);
        pText.setTextSize(dp(10));
        pGlow.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        arrow.moveTo(17, 0); arrow.lineTo(-9, 12); arrow.lineTo(-3.5f, 0); arrow.lineTo(-9, -12); arrow.close();

        holder = surface.getHolder();
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder h) { startLoop(); }
            @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {
                synchronized (lock) { W = w; H = hh; ppuT = Math.min(W, H) / camDist; ppu = ppuT; }
            }
            @Override public void surfaceDestroyed(SurfaceHolder h) { stopLoop(); }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override protected void onPause() { super.onPause(); pauseGame(); pauseMusic(); }

    @Override protected void onResume() { super.onResume(); startMusic(); ensureAds(); }

    @Override
    protected void onDestroy() {
        adH.removeCallbacksAndMessages(null);
        stopLoop();
        if (sp != null) { sp.release(); sp = null; }
        releaseMusic();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (sideOpen) closeSide();
        else if (state == ST_PLAY) pauseGame();
        else if (state == ST_PAUSED) confirmExit();
        else if (state == ST_OVER) toMenu();   // no ad on the Back button (AdMob: no surprise ads)
        else if (screen == SC_HATS || screen == SC_BG || screen == SC_BUILD) goScreen(SC_SKIN);
        else if (screen == SC_GO) goScreen(SC_BG);
        else confirmExit();
    }

    private void startLoop() {
        stopLoop();
        loop = new Loop();
        loop.start();
    }

    private void stopLoop() {
        if (loop != null) {
            loop.run = false;
            try { loop.join(500); } catch (InterruptedException e) { }
            loop = null;
        }
    }

    private int dp(float v) { return (int) (v * density + .5f); }

    private void loadPrefs() {
        best = prefs.getInt("best", 0);
        curHat = Math.max(0, Math.min(HATS.length - 1, prefs.getInt("hat", 0)));
        if (hatLocked(curHat)) curHat = 0;
        bgIdx = Math.max(0, Math.min(BGS.length - 1, prefs.getInt("bg", 0)));
        botN = Math.max(0, Math.min(BOTS, prefs.getInt("bots", 32)));
        camDist = Math.max(12, Math.min(50, prefs.getInt("zoom", 30)));
        bgZoom = Math.max(50, Math.min(200, prefs.getInt("bgz", 100))) / 100f;
        muted = prefs.getBoolean("muted", false);
        musicOn = prefs.getBoolean("music", true);
    }

    // =====================================================================
    // GAME THREAD
    // =====================================================================
    private class Loop extends Thread {
        volatile boolean run = true;

        @Override
        public void run() {
            long last = System.nanoTime();
            while (run) {
                long now = System.nanoTime();
                float frameMs = (now - last) / 1e6f;
                float raw = Math.min(.033f, (now - last) / 1e9f);
                last = now;
                perfTick(frameMs, now);
                float t = (now - startNs) / 1e9f;
                synchronized (lock) {
                    dtG += (raw - dtG) * .35f;
                    if (state == ST_PLAY || state == ST_OVER) step(dtG);
                    if (state == ST_PLAY) tickHud(dtG);
                }
                Canvas c = null;
                try {
                    c = lockCanvasSafe();
                    if (c != null) {
                        synchronized (lock) { render(c, t); }
                    }
                } catch (Exception e) {
                    // skip frame
                } finally {
                    if (c != null) {
                        try { holder.unlockCanvasAndPost(c); } catch (Exception e2) { }
                    }
                }
                long spent = (System.nanoTime() - now) / 1000000L;
                if (spent < 6) {
                    try { Thread.sleep(6 - spent); } catch (InterruptedException ie) { }
                }
            }
        }
    }

    // ---- adaptive quality: if frames get slow the glow/gloss detail is thinned out, so any snake size stays smooth ----
    private volatile int perfQ = 0;      // 0 = full detail, 1 = lighter, 2 = lightest
    private float perfEma = 16.7f;
    private long perfT;

    private void perfTick(float ms, long now) {
        if (state != ST_PLAY || ms > 120f) return;
        perfEma += (ms - perfEma) * .05f;
        if (perfEma > 27f && perfQ < 2 && now - perfT > 1200000000L) { perfQ++; perfT = now; }
        else if (perfEma < 19f && perfQ > 0 && now - perfT > 6000000000L) { perfQ--; perfT = now; }
    }

    private Canvas lockCanvasSafe() {
        if (Build.VERSION.SDK_INT >= 26 && useHw) {
            try { return holder.lockHardwareCanvas(); } catch (Exception e) { useHw = false; }
        }
        return holder.lockCanvas();
    }

    // =====================================================================
    // WORLD / SNAKES / FOOD
    // =====================================================================
    private Snake makeSnake(boolean bot, String name) {
        Snake s = new Snake();
        s.bot = bot; s.name = name; s.p0 = rand() * 6.28f;
        snakes.add(s);
        return s;
    }

    private void initWorld() {
        P = makeSnake(false, "You");
        for (int i = 0; i < BOTS; i++) {
            Snake b = makeSnake(true, i < FLAGS.length ? FLAGS[i].n : BN[i % BN.length]);
            b.idx = i;
            botLook(b);
            b.respawn = 1e9f;
        }
        for (int i = 0; i < FOODN; i++) spawnFoodNear(false);
    }

    private void setSkin(Snake s, Skin sk) {
        s.skin = sk;
        for (int i = 0; i < MAXSEG; i++) s.bands[i] = skinColor(sk, i);
        int bc = s.bands[0];
        float bs = -1f;
        float[] o = new float[3];
        for (int i = 0; i < Math.min(MAXSEG, 60); i++) {
            rgb2hsl(s.bands[i], o);
            float v = o[1] * (.4f + o[2]);
            if (v > bs) { bs = v; bc = s.bands[i]; }
        }
        s.bestC = bc;
        s.hc = sk.p == P_DOTS ? sk.c[0] : s.bands[0];
    }

    private void botLook(Snake b) {
        setSkin(b, b.idx < FLAGS.length ? flagSkin(b.idx) : randSkin());
        b.hat = (FREE_HATS.length > 0 && rand() < .7f) ? FREE_HATS[RND.nextInt(FREE_HATS.length)] : 0;
    }

    private int foodCol(Snake s, int i) {
        int c = s.bands[i % MAXSEG];
        float lum = (Color.red(c) * .3f + Color.green(c) * .59f + Color.blue(c) * .11f) / 255f;
        return lum < .22f ? s.bestC : c;
    }

    // body grows with every bite: more segments (capped), thicker (capped), then stretches a little more - never unbounded
    private int segCount(Snake s) {
        int cap = s.bot ? SEG_CAP_BOT : SEG_CAP_PLAYER;
        return Math.min(cap, (int) Math.floor(16 + powf(Math.min(s.mass, MASS_CAP), .55f) * 2.6f));
    }
    private float radiusOf(Snake s) { return .52f + powf(Math.min(s.mass, MASS_CAP), .55f) * .022f; }
    private float gapOf(Snake s) {
        float g = s.r * .68f;
        if (!s.bot && s.mass > LONG_FROM) g *= 1f + .35f * clamp((s.mass - LONG_FROM) / (MASS_CAP - LONG_FROM), 0f, 1f);
        return g;
    }

    private void spawn(Snake s, float x, float y, float mass, float ang) {
        s.x = x; s.y = y;
        s.a = s.ta = Float.isNaN(ang) ? rand() * 6.28f : ang;
        s.mass = mass; s.r = radiusOf(s); s.kills = 0; s.boost = false; s.bt = 0; s.t = 0; s.alive = true; s.bw = 0;
        if (s.bot) s.spd = rnd(1f, 1.35f); else s.spd = 1f;
        s.v = 14f * s.spd;
        int n = segCount(s);
        float sp2 = gapOf(s);
        s.n = n;
        for (int i = 0; i < n; i++) {
            s.sx[i] = x - cosf(s.a) * sp2 * i;
            s.sy[i] = y - sinf(s.a) * sp2 * i;
        }
    }

    private float[] ringPos(float r0, float r1) {
        float q = rand() * 6.28f, r = rnd(r0, r1);
        return new float[]{fx + cosf(q) * r, fz + sinf(q) * r};
    }

    private float[] freeSpot(float r0, float r1) {
        float[] best2 = ringPos(r0, r1);
        float bd = -1f;
        for (int k = 0; k < 12; k++) {
            float[] p = ringPos(r0, r1);
            float md = 1e9f;
            for (int a = 0; a < snakes.size(); a++) {
                Snake o = snakes.get(a);
                if (!o.alive) continue;
                for (int j = 0; j < o.n; j += 4) {
                    float d = sq(o.sx[j] - p[0]) + sq(o.sy[j] - p[1]);
                    if (d < md) md = d;
                }
            }
            if (md > bd) { bd = md; best2 = p; }
        }
        return best2;
    }

    private void addFood(float x, float y, float v, int c, float vx, float vy, float k, float b, int d) {
        if (food.size() >= FOODMAX) return;
        Food f = new Food();
        f.x = x; f.y = y; f.v = v; f.c = c != 0 ? c : FCOL[RND.nextInt(FCOL.length)];
        f.p = rand() * 6.28f; f.vx = vx; f.vy = vy; f.k = k; f.b = b; f.d = d; f.sh = 1f;
        food.add(f);
    }

    private void spawnFoodNear(boolean an) {
        float a = rand() * 6.28f, r = sqrtf(rand()) * (LIVE - 3f);
        float x = fx + cosf(a) * r, y = fz + sinf(a) * r, b = an ? gt : 0f;
        if (rand() < .94f) addFood(x, y, 1.6f, 0, 0, 0, rnd(.3f, .4f), b, 0);
        else addFood(x, y, rnd(7f, 10f), 0, 0, 0, rnd(.8f, 1f), b, 0);
    }

    private void emit(float x, float y, int color, int n, float spd, float sz, float life, boolean flat) {
        for (int k = 0; k < n; k++) {
            int i = pHead;
            pHead = (pHead + 1) % PN;
            float a = rand() * 6.283f;
            float s = flat ? spd * (.75f + rand() * .5f) : spd * (.3f + rand());
            pvx[i] = cosf(a) * s; pvy[i] = sinf(a) * s;
            pl[i] = pm[i] = life * (.6f + rand() * .6f);
            ps[i] = sz * (.6f + rand() * .8f);
            px[i] = x; py[i] = y; pc[i] = color;
        }
    }

    private void ripple(float x, float y, int color) {
        int i = wHead; wHead = (wHead + 1) & 7;
        wx[i] = x; wy[i] = y; wc[i] = color; wt[i] = .7f;
    }

    private void stepP(float dt) {
        for (int i = 0; i < 8; i++) if (wt[i] > 0) wt[i] -= dt;
        float dk = expf(-1.8f * dt);
        for (int i = 0; i < PN; i++) {
            if (pl[i] <= 0) continue;
            pl[i] -= dt;
            px[i] += pvx[i] * dt; py[i] += pvy[i] * dt;
            pvx[i] *= dk; pvy[i] *= dk;
        }
    }

    private void newWorld() {
        food.clear();
        for (int i = 0; i < FOODN; i++) spawnFoodNear(false);
        for (int a = 0; a < snakes.size(); a++) {
            Snake b = snakes.get(a);
            if (!b.bot) continue;
            if (b.idx < botN) {
                float[] p = freeSpot(24f, LIVE - 6f);
                spawn(b, p[0], p[1], rnd(15f, 24f), Float.NaN);
                botLook(b);
                b.respawn = 0;
            } else b.alive = false;
        }
    }

    private void clearWorld() {
        for (int a = 0; a < snakes.size(); a++) snakes.get(a).alive = false;
        food.clear();
        fx = 0; fz = 0;
        for (int i = 0; i < FOODN; i++) spawnFoodNear(false);
    }

    private void applyBots() {
        if (state == ST_MENU) return;
        for (int a = 0; a < snakes.size(); a++) {
            Snake s = snakes.get(a);
            if (!s.bot) continue;
            if (s.idx >= botN) { s.alive = false; }
            else if (!s.alive) s.respawn = Math.min(s.respawn, rnd(0f, 1.2f));
        }
    }

    private String label(Snake s) { return s.bot ? s.name + " (bot)" : s.name; }

    // =====================================================================
    // KILL
    // =====================================================================
    private void killSnake(Snake s, Snake by) {
        if (!s.alive) return;
        s.alive = false;
        int n = s.n;
        int cnt = Math.max(6, Math.min(90, Math.round(s.mass / 3f)));
        float sz = Math.max(.34f, Math.min(2.2f, .3f + sqrtf(s.mass) * .045f));
        float ok = Math.min(sz, Math.max(.3f, n * gapOf(s) / cnt * .8f));
        float v = s.mass * 1.2f / cnt;
        for (int j = 0; j < cnt; j++) {
            int i = Math.min(n - 1, (int) Math.floor((double) j * n / cnt));
            float a = rand() * 6.283f, spd = rnd(0f, .25f);
            addFood(s.sx[i] + rnd(-.5f, .5f) * s.r, s.sy[i] + rnd(-.5f, .5f) * s.r, v * rnd(.85f, 1.15f), foodCol(s, i),
                    cosf(a) * spd, sinf(a) * spd, ok * rnd(.92f, 1.08f), 0f, 1);
        }
        emit(s.x, s.y, s.bestC, 42, 14f + s.r * 6f, 1.3f, .9f, true);
        emit(s.x, s.y, 0xFFFFFFFF, 18, 7f, 1.8f, .55f, true);
        emit(s.x, s.y, s.bestC, 30, 9f + s.r * 4f, 1.1f, 1.1f, false);
        for (int k = 0; k < 12; k++) {
            float a = k / 12f * 6.2832f, spd = 8f + s.r * 3f;
            emit(s.x + cosf(a) * s.r, s.y + sinf(a) * s.r, s.bestC, 3, spd, 1.4f, .8f, true);
        }
        if (by != null && by != s) by.kills++;
        ripple(s.x, s.y, s.bestC);
        if (s == P) shake = 1f;
        else if (by == P) shake = Math.max(shake, .55f);
        else if (hypotf(s.x - fx, s.y - fz) < 14f) shake = Math.max(shake, .25f);
        if (s == P) {
            playSfx(sDie, .9f, 1f);
            clearSave();
            state = ST_OVER;
            final int L = (int) Math.floor(s.mass);
            finalScore = L; deathMass = s.mass; deathKills = s.kills;
            if (L > best) { best = L; prefs.edit().putInt("best", best).apply(); }
            final String why = by != null ? "Popped by " + by.name + "." : "";
            final int kl = s.kills, bst = best;
            ui.post(new Runnable() {
                @Override public void run() {
                    hudV.setVisibility(View.GONE);
                    tvWhy.setText(why); tvRl.setText("" + L); tvRkl.setText("" + kl); tvRb.setText("" + bst);
                    btnRevive.setVisibility(reviveUsed ? View.GONE : View.VISIBLE);
                    btnDouble.setVisibility(View.VISIBLE);
                    showToast("You got popped!");
                }
            });
            ui.postDelayed(new Runnable() {
                @Override public void run() { if (state == ST_OVER) { overV.setAlpha(0f); overV.setVisibility(View.VISIBLE); overV.animate().alpha(1f).setDuration(350).start(); } }
            }, 1100);
        } else {
            s.respawn = rnd(2f, 4.5f);
            if (by == P) { playSfx(sKill, .8f, 1f); final String nm = s.name; ui.post(new Runnable() {
                @Override public void run() { showToast("You popped " + nm); } }); }
        }
    }

    // =====================================================================
    // BOT AI
    // =====================================================================
    private void foodGrid() {
        if (gt - fgT < .25f) return;
        fgT = gt;
        fgM.clear();
        for (int i = 0; i < food.size(); i++) {
            Food f = food.get(i);
            if (f.b > gt) continue;
            int k = ((int) Math.floor(f.x / 8f) + 512) * 1024 + (int) Math.floor(f.y / 8f) + 512;
            Cell c = fgM.get(k);
            if (c == null) { c = new Cell(); fgM.put(k, c); }
            c.x += f.x; c.y += f.y; c.v += f.v; c.n++; c.it.add(f);
        }
    }

    private boolean risky(float x, float y) {
        for (int i = 0; i < big.size(); i++) {
            Snake o = big.get(i);
            if (sq(o.x - x) + sq(o.y - y) < sq(16f + o.r * 3.3f)) return true;
        }
        return false;
    }

    private void ai(Snake s, float dt) {
        s.t -= dt; s.bt -= dt;
        float look = 4.8f + s.r * 3.4f, lx = s.x + cosf(s.a) * look, ly = s.y + sinf(s.a) * look;
        // immediate danger dodge
        for (int a = 0; a < snakes.size(); a++) {
            Snake o = snakes.get(a);
            if (o == s || !o.alive) continue;
            float reach = o.n * gapOf(o) + o.r + look + 5f;
            if (sq(o.x - s.x) + sq(o.y - s.y) > reach * reach) continue;
            float lim = sq(o.r + s.r + 2.4f);
            for (int j = 0; j < o.n; j += 2) {
                float qx = o.sx[j], qy = o.sy[j], dx = qx - lx, dy = qy - ly;
                if (dx * dx + dy * dy < lim) {
                    float cr = cosf(s.a) * (qy - s.y) - sinf(s.a) * (qx - s.x);
                    s.ta = s.a + (cr > 0 ? -1.9f : 1.9f);
                    s.t = .34f;
                    if (rand() < .15f) s.bt = .65f;
                    s.boost = s.bt > 0 && s.mass > 20;
                    return;
                }
            }
        }
        if (s.t > 0) { s.boost = s.bt > 0 && s.mass > 20; return; }
        s.t = rnd(.08f, .2f);
        foodGrid();
        if (s.agg < 0) s.agg = rnd(.55f, 1f);
        boolean isLeader = leader == s;
        float ag = isLeader ? s.agg * .35f : s.agg;
        Snake thr = null, prey = null, highTarget = null;
        float td = 26f * 26f, pk = -1f, hk = -1f;
        big.clear();
        boolean playerAlive = P != null && P.alive;
        float pDx = playerAlive ? P.x - s.x : 0f, pDy = playerAlive ? P.y - s.y : 0f;
        float pD2 = pDx * pDx + pDy * pDy, pD = sqrtf(pD2);
        int nearBots = 0;
        for (int a = 0; a < snakes.size(); a++) {
            Snake o = snakes.get(a);
            if (o == s || !o.alive || !o.bot) continue;
            if (sq(o.x - s.x) + sq(o.y - s.y) < 38f * 38f) nearBots++;
        }
        for (int a = 0; a < snakes.size(); a++) {
            Snake o = snakes.get(a);
            if (o == s || !o.alive) continue;
            float dx = o.x - s.x, dy = o.y - s.y, d2 = dx * dx + dy * dy;
            if (d2 > 62f * 62f) continue;
            float d = sqrtf(d2);
            if (o.mass > s.mass * 1.15f) {
                big.add(o);
                float oc = cosf(o.a), os = sinf(o.a);
                boolean approaching = -(dx * oc + dy * os) > 0;
                if (d2 < td && (approaching || d2 < 120f)) { td = d2; thr = o; }
            }
            if (s.mass >= 16f) {
                boolean isP = o == P;
                float scoreBonus = (o.mass > s.mass ? 1.45f : 1f) * (isP ? 3.8f : 1f) * (o == leader ? 1.6f : 1f)
                        * (o.mass > s.mass * .85f ? 1.3f : 1f);
                float side = Math.abs(cosf(s.a) * (o.y - s.y) - sinf(s.a) * (o.x - s.x));
                float chance = (side > d * .28f && d < 30f) ? 1.7f : 1f;
                float k = (o.mass + 22f) * scoreBonus * chance / (d + 3.5f);
                if ((isP || o.mass > s.mass * .65f) && k > hk) { hk = k; highTarget = o; }
                if (!isP && o.mass < s.mass * (isLeader ? .55f : .75f + .38f * ag) && k > pk) { pk = k; prey = o; }
            }
        }
        // 1. flee bigger threats
        if (thr != null && thr != P) {
            float oc = cosf(thr.a), os = sinf(thr.a);
            float side = (oc * (s.y - thr.y) - os * (s.x - thr.x)) > 0 ? 1f : -1f;
            s.ta = thr.a + side * 1.6f + rnd(-.25f, .25f);
            s.t = .22f;
            if (td < 150f && s.mass > 18f) s.bt = .7f;
            s.boost = s.bt > 0 && s.mass > 20;
            return;
        }
        // 2. always hunt the player
        if (playerAlive && pD2 < 72f * 72f) {
            boolean playerStrong = P.mass > s.mass * .9f || P.mass > 40f || P == leader;
            boolean packPressure = nearBots >= 2 || (nearBots >= 1 && pD < 28f);
            boolean wantKill = playerStrong || packPressure || rand() < .72f + .2f * ag;
            if (wantKill && !risky(P.x, P.y)) {
                float lead = Math.min(20f, P.v * pD / (8.5f * s.spd));
                float predX = P.x + cosf(P.a) * lead, predY = P.y + sinf(P.a) * lead;
                float sideDir = (cosf(s.a) * (P.y - s.y) - sinf(s.a) * (P.x - s.x)) > 0 ? 1f : -1f;
                float cut = Math.min(9f, 4f + s.r * 1.2f);
                predX += -sinf(P.a) * sideDir * cut;
                predY += cosf(P.a) * sideDir * cut;
                s.ta = atan2f(predY - s.y, predX - s.x);
                if (pD < 26f || (packPressure && pD < 40f)) {
                    if (s.mass > 18f) s.bt = Math.max(s.bt, .75f + rnd(0f, .35f));
                } else if (pD < 42f && s.mass > 22f && rand() < .55f) {
                    s.bt = Math.max(s.bt, .5f);
                }
                s.boost = s.bt > 0 && s.mass > 20;
                return;
            }
        }
        // 3. other high value targets
        if (highTarget != null && highTarget != P && !risky(highTarget.x, highTarget.y) && rand() < .4f + .5f * ag) {
            float d = hypotf(highTarget.x - s.x, highTarget.y - s.y);
            float lead = Math.min(16f, highTarget.v * d / (10f * s.spd));
            float predX = highTarget.x + cosf(highTarget.a) * lead, predY = highTarget.y + sinf(highTarget.a) * lead;
            s.ta = atan2f(predY - s.y, predX - s.x);
            if (d < 20f && s.mass > 22f) s.bt = .65f;
            s.boost = s.bt > 0 && s.mass > 20;
            return;
        }
        // 4. easy prey
        if (prey != null && !risky(prey.x, prey.y) && rand() < .32f + .55f * ag) {
            float d = hypotf(prey.x - s.x, prey.y - s.y);
            float sideAng = Math.abs(angDiff(s.a, atan2f(prey.y - s.y, prey.x - s.x)));
            if (sideAng > .8f || d < 16f) {
                float lead = Math.min(14f, prey.v * d / (11f * s.spd));
                s.ta = atan2f(prey.y + sinf(prey.a) * lead - s.y, prey.x + cosf(prey.a) * lead - s.x);
                if (d < 15f && s.mass > 20f && ag > .4f) s.bt = .5f;
                s.boost = s.bt > 0 && s.mass > 20;
                return;
            }
        }
        // 5. food / roaming
        int ix = (int) Math.floor(s.x / 8f), iy = (int) Math.floor(s.y / 8f);
        Food bf = null;
        float bs = 1e9f;
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++) {
                Cell c = fgM.get((ix + i + 512) * 1024 + iy + j + 512);
                if (c == null) continue;
                for (int q = 0; q < c.it.size(); q++) {
                    Food f = c.it.get(q);
                    float d = sq(f.x - s.x) + sq(f.y - s.y);
                    if (d > 230f) continue;
                    float k = d / (1f + f.v * f.v * .7f);
                    if (k < bs && !risky(f.x, f.y)) { bs = k; bf = f; }
                }
            }
        if (bf != null) {
            s.ta = atan2f(bf.y - s.y, bf.x - s.x);
            if (s.mass > 20f && rand() < .09f + .13f * ag) s.bt = Math.max(s.bt, .3f + rnd(0f, .4f));
            s.boost = s.bt > 0 && s.mass > 20;
            return;
        }
        float bk = 0, tx = 0, ty = 0, tv = 0, td2 = 0;
        float ca = cosf(s.a), sa = sinf(s.a);
        for (int i = -6; i <= 6; i++)
            for (int j = -6; j <= 6; j++) {
                Cell c = fgM.get((ix + i + 512) * 1024 + iy + j + 512);
                if (c == null) continue;
                float cx = c.x / c.n, cy = c.y / c.n, dx = cx - s.x, dy = cy - s.y, d = hypotf(dx, dy);
                float k = c.v / (d + 7f) * ((dx * ca + dy * sa < 0) ? .5f : 1.1f);
                if (k > bk && !risky(cx, cy)) { bk = k; tx = cx; ty = cy; tv = c.v; td2 = d; }
            }
        if (bk > 0) {
            s.ta = atan2f(ty - s.y, tx - s.x);
            if (tv >= 50f && td2 > 8f && s.mass > 26f && rand() < .3f * ag) s.bt = .5f;
        } else if (playerAlive && pD2 > 55f * 55f && pD2 < 120f * 120f && rand() < .4f) {
            s.ta = atan2f(P.y - s.y, P.x - s.x) + rnd(-.5f, .5f);
        } else if (hypotf(s.x - fx, s.y - fz) > 50f) {
            s.ta = atan2f(fz - s.y, fx - s.x) + rnd(-.45f, .45f);
        } else {
            s.ta = s.a + rnd(-1.2f, 1.2f);
        }
        if (s.mass > 20f && s.bt <= 0 && rand() < .05f + .08f * ag) s.bt = .35f + rnd(0f, .5f);
        s.boost = s.bt > 0 && s.mass > 20;
    }

    // =====================================================================
    // INPUT AIM
    // =====================================================================
    private float aimAngle() {
        if (joyId != -1) {
            float dx = joyX - joyOx, dy = joyY - joyOy;
            float thr = dp(3);
            if (dx * dx + dy * dy > thr * thr) return atan2f(dy, dx);
        }
        return P.ta;
    }

    private void moveJoy(float x, float y) {
        joyX = x; joyY = y;
        float dx = joyX - joyOx, dy = joyY - joyOy, d = hypotf(dx, dy), L = 46f * density;
        if (d > L) { joyOx += dx / d * (d - L); joyOy += dy / d * (d - L); }
    }

    // =====================================================================
    // STEP
    // =====================================================================
    private void step(float dt) {
        gt += dt; fr++;
        for (int i = 0; i < food.size(); i++) {
            Food f = food.get(i);
            f.sh = 1f;
            if (f.vx != 0 || f.vy != 0) {
                f.x += f.vx * dt; f.y += f.vy * dt;
                float dk = expf(-3f * dt);
                f.vx *= dk; f.vy *= dk;
                if (f.vx * f.vx + f.vy * f.vy < .01f) { f.vx = 0; f.vy = 0; }
            }
        }
        if (state == ST_PLAY && P.alive) {
            P.ta += angDiff(P.ta, aimAngle()) * (1f - expf(-dt * 60f));
            boolean canBoost = boostIn && P.mass > 20f && boostFuel > .05f;
            P.boost = canBoost;
            if (P.boost) {
                boostFuel = Math.max(0f, boostFuel - dt);
                if (boostFuel <= 0) { P.boost = false; boostIn = false; }
            }
            if (P.boost) {
                long bms = System.currentTimeMillis();
                if (!boostWas || bms - boostSfxMs > 850) { boostSfxMs = bms; playSfx(sGhost, .6f, 1f); }
            }
            boostWas = P.boost;
            if (P.mass >= boostMassMark + boostRefillAt) {
                int steps = (int) Math.floor((P.mass - boostMassMark) / boostRefillAt);
                boostFuel = Math.min(boostMax, boostFuel + steps * boostMax);
                boostMassMark += steps * boostRefillAt;
            }
        }
        for (int si = 0; si < snakes.size(); si++) {
            Snake s = snakes.get(si);
            if (!s.alive) {
                if (s.bot && s.idx < botN && (s.respawn -= dt) <= 0) {
                    float[] p = freeSpot(state == ST_MENU ? 0f : 36f, LIVE - 6f);
                    spawn(s, p[0], p[1], rnd(15f, 30f), Float.NaN);
                    botLook(s);
                }
                continue;
            }
            if (s.bot) {
                if (hypotf(s.x - fx, s.y - fz) > LIVE + 8f) {
                    float[] p = ringPos(LIVE * .65f, LIVE * .9f);
                    float dx = p[0] - s.x, dy = p[1] - s.y;
                    for (int i = 0; i < s.n; i++) { s.sx[i] += dx; s.sy[i] += dy; }
                    s.x = p[0]; s.y = p[1];
                }
                if (((fr + s.idx) % 3) == 0) ai(s, dt * 3f);
            }
            s.r = radiusOf(s);
            float df = angDiff(s.a, s.ta);
            if (s == P) {
                float mr = 22f / (1f + s.r * .05f) * dt;
                s.a += Math.max(-mr, Math.min(mr, df * (1f - expf(-dt * 38f))));
            } else {
                float mt = 8f * (s.spd > 1f ? 1.25f : 1f) / (1f + s.r * .5f) * dt;
                s.a += Math.max(-mt, Math.min(mt, df));
            }
            float tv = (s.boost ? 38f : 20f) * s.spd;
            s.v += (tv - s.v) * (1f - expf(-dt * 18f));
            s.x += cosf(s.a) * s.v * dt; s.y += sinf(s.a) * s.v * dt; s.dist += s.v * dt;
            boolean on = sq(s.x - fx) + sq(s.y - fz) < 48f * 48f;
            if (s.boost) {
                s.mass -= 5f * dt; s.drop += dt;
                if (on && rand() < dt * (s == P ? 28f : 8f)) {
                    int j = Math.min(s.n - 1, RND.nextInt(4));
                    emit(s.sx[j], s.sy[j], s.bands[j], s == P ? 2 : 1, 3.5f, .7f, .55f, false);
                }
                if (s == P && on && rand() < dt * 20f)
                    emit(s.x - cosf(s.a) * s.r, s.y - sinf(s.a) * s.r, s.bestC, 1, 2.2f, .8f, .4f, false);
                if (s.drop > .4f) {
                    s.drop = 0;
                    addFood(s.sx[s.n - 1], s.sy[s.n - 1], 2f, s.bestC, 0, 0, .42f, 0f, 1);
                }
                if (s.mass < 15f) s.boost = false;
            } else if (s == P && on && rand() < dt * 4f) {
                emit(s.x - cosf(s.a) * s.r * .8f, s.y - sinf(s.a) * s.r * .8f, s.bestC, 1, 1.2f, .35f, .3f, false);
            }
            int n = segCount(s);
            float gap = gapOf(s);
            s.sx[0] = s.x; s.sy[0] = s.y;
            while (s.n < n) { s.sx[s.n] = s.sx[s.n - 1]; s.sy[s.n] = s.sy[s.n - 1]; s.n++; }
            if (s.n > n) s.n = n;
            for (int i = 1; i < s.n; i++) {
                float dx = s.sx[i - 1] - s.sx[i], dy = s.sy[i - 1] - s.sy[i], d = hypotf(dx, dy);
                if (d > gap) { float k = (d - gap) / d; s.sx[i] += dx * k; s.sy[i] += dy * k; }
            }
            float ar = s.r * 2f + 2.6f, att = ar * ar, eat = sq(s.r + .8f);
            for (int i = food.size() - 1; i >= 0; i--) {
                Food f = food.get(i);
                if (f.b > gt) continue;
                float dx = s.x - f.x, dy = s.y - f.y, d2 = dx * dx + dy * dy;
                if (d2 < att) {
                    if (d2 < eat) {
                        s.mass += f.v * .7f;
                        if (on) { emit(f.x, f.y, f.c, s == P ? 6 : 3, 4f, .65f, .5f, false); emit(f.x, f.y, 0xFFFFFFFF, 2, 0f, 1.4f, .25f, true); }
                        int last = food.size() - 1;
                        food.set(i, food.get(last));
                        food.remove(last);
                        if (s == P) ate++;
                    } else {
                        float d = sqrtf(d2), k = Math.min(1f, (26f + s.v) * dt / d);
                        f.x += dx * k; f.y += dy * k; f.sh = .45f + .55f * d / ar;
                    }
                }
            }
        }
        // collisions
        pendS.clear(); pendO.clear();
        for (int si = 0; si < snakes.size(); si++) {
            Snake s = snakes.get(si);
            if (!s.alive) continue;
            if (s != P && (((fr + s.idx) & 1) != 0)) continue;
            float rr = s.r * .8f;
            for (int oi = 0; oi < snakes.size(); oi++) {
                Snake o = snakes.get(oi);
                if (o == s || !o.alive) continue;
                float reach = o.n * gapOf(o) + o.r + s.r + 2f;
                if (sq(o.x - s.x) + sq(o.y - s.y) > reach * reach) continue;
                float lim = sq(rr + o.r * .9f);
                int js = o.r > 1.5f ? 1 : 2;
                boolean hit = false;
                for (int j = 0; j < o.n; j += js) {
                    float dx = o.sx[j] - s.x, dy = o.sy[j] - s.y;
                    if (dx * dx + dy * dy < lim) { hit = true; break; }
                }
                if (hit) { pendS.add(s); pendO.add(o); break; }
            }
        }
        for (int i = 0; i < pendS.size(); i++) killSnake(pendS.get(i), pendO.get(i));
        stepP(dt);
        if (ate > 0) {
            long ms = System.currentTimeMillis();
            if (ms - lastEatMs > 90) { lastEatMs = ms; playSfx(sEat, .5f, .8f + rand() * .7f); }
            ate = 0;
        }
        int am = 0;
        for (int i = 0; i < food.size(); i++) if (food.get(i).d == 0) am++;
        int kk = 0;
        while (am < FOODN && kk++ < 6) { spawnFoodNear(true); am++; }
        if ((cullT -= dt) <= 0) {
            cullT = 1f;
            for (int i = food.size() - 1; i >= 0; i--) {
                Food f = food.get(i);
                if (sq(f.x - fx) + sq(f.y - fz) > sq(LIVE + 30f)) {
                    int last = food.size() - 1;
                    food.set(i, food.get(last));
                    food.remove(last);
                }
            }
        }
    }

    // =====================================================================
    // HUD (game thread computes, UI thread shows)
    // =====================================================================
    private void hud() {
        ArrayList<Snake> arr = new ArrayList<Snake>();
        for (int i = 0; i < snakes.size(); i++) if (snakes.get(i).alive) arr.add(snakes.get(i));
        Collections.sort(arr, new Comparator<Snake>() {
            @Override public int compare(Snake a, Snake b) { return Float.compare(b.mass, a.mass); }
        });
        int rank = arr.indexOf(P) + 1;
        playerRank = rank > 0 ? rank : arr.size();
        neonPower = Math.max(.25f, 1.15f - (playerRank - 1) * .08f);
        boostRefillAt = Math.min(220f, 100f + Math.max(0, (11 - playerRank) * 8));
        rankTxt = "Rank " + (rank > 0 ? "" + rank : "-") + "/" + arr.size();
        if (arr.size() > 0) leader = arr.get(0);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int cnt = rank > 10 ? 9 : Math.min(10, arr.size());
        for (int i = 0; i < cnt; i++) addLbLine(sb, arr.get(i), i);
        if (rank > 10) addLbLine(sb, P, rank - 1);
        lbText = sb;
    }

    private void addLbLine(SpannableStringBuilder sb, Snake s, int i) {
        if (sb.length() > 0) sb.append("\n");
        int st = sb.length();
        sb.append("#" + (i + 1) + " " + label(s) + "  " + (int) s.mass);
        sb.setSpan(new ForegroundColorSpan(s == P ? 0xFFFFFFFF : s.bestC), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (s == P) sb.setSpan(new StyleSpan(Typeface.BOLD), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void tickHud(float dt) {
        if ((lbT -= dt) <= 0) { lbT = .4f; hud(); }
        if ((uiT -= dt) <= 0) {
            uiT = .1f;
            final int len = (int) Math.floor(P.mass), kl = P.kills;
            final float pct = clamp(boostFuel / boostMax, 0f, 1f);
            final boolean bon = P.boost;
            final String bt = bon ? ("BOOST " + String.format("%.1f", boostFuel) + "s")
                    : (pct < .05f ? "EMPTY - eat +" + (int) boostRefillAt : "BOOST " + String.format("%.1f", boostFuel) + "/" + (int) boostMax);
            final String nm = P.name, rk = rankTxt + "  Boops " + kl;
            final CharSequence lb = lbText;
            ui.post(new Runnable() {
                @Override public void run() {
                    tvPn.setText(nm); tvLen.setText("" + len); tvRank.setText(rk); tvLb.setText(lb); tvBt.setText(bt);
                    ViewGroup.LayoutParams lp = bi.getLayoutParams();
                    lp.width = (int) (bbar.getWidth() * pct);
                    bi.setLayoutParams(lp);
                    setBg(bi, bon ? rr(0xFFffe27a, 9, 0, 0) : (pct < .2f ? rr(0xFFff4040, 9, 0, 0) : rr(0xFFff7a3c, 9, 0, 0)));
                    boostBtn.setAlpha(pct > .05f ? 1f : .35f);
                    btnRefill.setVisibility(pct < .05f && state == ST_PLAY ? View.VISIBLE : View.GONE);
                }
            });
        }
    }

    // =====================================================================
    // RENDER
    // =====================================================================
    private ColorFilter cf(int color) {
        int k = color | 0xFF000000;
        ColorFilter f = cfCache.get(k);
        if (f == null) { f = new PorterDuffColorFilter(k, PorterDuff.Mode.SRC_IN); cfCache.put(k, f); }
        return f;
    }

    private void glow(Canvas c, float cx, float cy, float size, int color, int alpha) {
        pGlow.setColorFilter(cf(color));
        pGlow.setAlpha(alpha);
        rc.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
        c.drawBitmap(GLOW, null, rc, pGlow);
    }

    private void sprite(Canvas c, Bitmap b, float cx, float cy, float r, int alpha) {
        pSpr.setAlpha(alpha);
        rc.set(cx - r, cy - r, cx + r, cy + r);
        c.drawBitmap(b, null, rc, pSpr);
    }

    // ---- FISH-STYLE FEATHER FINS + SIDE STUDS (pure canvas, no bitmaps, no per-frame allocation) ----
    /** one feather: base (bx,by), direction ang (radians), length L, half width W */
    private void finFeather(Canvas c, float bx, float by, float ang, float L, float W, int fill, int edge) {
        float ca = cosf(ang), sa = sinf(ang);
        float tx = bx + ca * L, ty = by + sa * L;
        float mx = bx + ca * L * .45f, my = by + sa * L * .45f;
        float px = -sa * W, py = ca * W;
        finPath.reset();
        finPath.moveTo(bx, by);
        finPath.quadTo(mx + px * 1.1f, my + py * 1.1f, tx, ty);
        finPath.quadTo(mx - px * .8f, my - py * .8f, bx, by);
        finPath.close();
        pFill.setColor(fill);
        c.drawPath(finPath, pFill);
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(Math.max(1f, W * .16f));
        pStroke.setColor(edge);
        c.drawPath(finPath, pStroke);
        pStroke.setStrokeWidth(Math.max(1f, W * .1f));   // centre rib
        pStroke.setColor(0x88FFFFFF);
        c.drawLine(bx, by, tx - ca * L * .1f, ty - sa * L * .1f, pStroke);
    }

    /** feather fins on both flanks (swept back, flapping with the swim wave) + a feathered fish tail. Drawn behind the body. */
    private void drawFins(Canvas c, Snake s, int n, float t, float ppu, float hw, float hh, int lodQ, int si) {
        if (lodQ >= 2 || n < 6) return;
        int step = n > 150 ? 6 : 4;
        int last = Math.max(5, (int) (n * .62f));
        for (int i = last - (last % step); i >= 3; i -= step) {
            if (i >= n - 1) continue;
            float k = s.rp[i * 3 + 2];
            int ia = i - 1, ib = i + 1;
            if (k <= 0 || s.rp[ia * 3 + 2] <= 0 || s.rp[ib * 3 + 2] <= 0) continue;
            float sx = (s.rp[i * 3] - camX) * ppu + hw, sy = (s.rp[i * 3 + 1] - camY) * ppu + hh;
            float sr = k * 1.22f * ppu;
            if (sr < 3f) continue;
            float reach = sr * 3.4f;
            if (sx < -reach || sx > W + reach || sy < -reach || sy > H + reach) continue;
            float h = atan2f(s.rp[ia * 3 + 1] - s.rp[ib * 3 + 1], s.rp[ia * 3] - s.rp[ib * 3]);
            float tt = (float) i / n;
            int bcol = s.bands[i] | 0xFF000000;
            int fill = (lerpColor(bcol, 0xFFFFFFFF, .22f) & 0xFFFFFF) | 0xE6000000;
            int edge = (lerpColor(bcol, 0xFF000000, .55f) & 0xFFFFFF) | 0xDC000000;
            float L = sr * (2.8f - 1.3f * tt), Wd = sr * .55f;
            for (int zi = 0; zi < 2; zi++) {
                float z = zi == 0 ? -1f : 1f;
                float fl = .22f * sinf(t * 6f - i * .55f + si);
                float bx = sx + cosf(h + z * 1.5708f) * sr * .7f, by = sy + sinf(h + z * 1.5708f) * sr * .7f;
                finFeather(c, bx, by, h + z * (2.55f + fl), L * .72f, Wd * .8f, fill, edge);   // short rear feather
                finFeather(c, bx, by, h + z * (2.1f + fl), L, Wd, fill, edge);                 // long main feather
            }
        }
        // feathered fish tail
        int e = n - 1;
        float ke = s.rp[e * 3 + 2];
        if (ke > 0 && s.rp[(e - 1) * 3 + 2] > 0) {
            float ex = (s.rp[e * 3] - camX) * ppu + hw, ey = (s.rp[e * 3 + 1] - camY) * ppu + hh;
            float R = s.r * 1.22f * ppu;
            float reach = R * 4f;
            if (R >= 3f && ex > -reach && ex < W + reach && ey > -reach && ey < H + reach) {
                float h = atan2f(s.rp[(e - 1) * 3 + 1] - s.rp[e * 3 + 1], s.rp[(e - 1) * 3] - s.rp[e * 3]) + 3.1416f;
                int bcol = s.bands[e] | 0xFF000000;
                int fill = (lerpColor(bcol, 0xFFFFFFFF, .22f) & 0xFFFFFF) | 0xE6000000;
                int edge = (lerpColor(bcol, 0xFF000000, .55f) & 0xFFFFFF) | 0xDC000000;
                float wag = .3f * sinf(t * 7f + si);
                finFeather(c, ex, ey, h - .75f + wag, R * 2.5f, R * .6f, fill, edge);
                finFeather(c, ex, ey, h + .75f + wag, R * 2.5f, R * .6f, fill, edge);
                finFeather(c, ex, ey, h - .32f + wag, R * 3.3f, R * .7f, fill, edge);
                finFeather(c, ex, ey, h + .32f + wag, R * 3.3f, R * .7f, fill, edge);
                finFeather(c, ex, ey, h + wag, R * 3.8f, R * .75f, fill, edge);
            }
        }
    }

    /** NEW side decoration: glowing pearl studs (gold / white) running along both flanks. Drawn on top of the body. */
    private void drawStuds(Canvas c, Snake s, int n, float t, float ppu, float hw, float hh, int lodQ, int hstep, int si) {
        if (lodQ >= 2 || n < 4) return;
        int step = hstep >= 3 ? 9 : (hstep == 2 ? 6 : 3);
        for (int i = n - 2; i >= 2; i--) {
            if (i % step != 0) continue;
            float k = s.rp[i * 3 + 2];
            int ia = i - 1, ib = i + 1;
            if (k <= 0 || s.rp[ia * 3 + 2] <= 0 || s.rp[ib * 3 + 2] <= 0) continue;
            float sx = (s.rp[i * 3] - camX) * ppu + hw, sy = (s.rp[i * 3 + 1] - camY) * ppu + hh;
            float sr = k * 1.22f * ppu;
            if (sr < 4f || sx < -sr * 2 || sx > W + sr * 2 || sy < -sr * 2 || sy > H + sr * 2) continue;
            float h = atan2f(s.rp[ia * 3 + 1] - s.rp[ib * 3 + 1], s.rp[ia * 3] - s.rp[ib * 3]);
            float tt = (float) i / n;
            float sd = sr * .2f * (1f - .4f * tt);
            float pul = .6f + .4f * sinf(t * 8f - i * .5f + si);
            int col = ((i / step) & 1) == 0 ? 0xFFFFE27A : 0xFFFFFFFF;
            for (int zi = 0; zi < 2; zi++) {
                float z = zi == 0 ? -1f : 1f;
                float px = sx + cosf(h + z * 1.5708f) * sr * .66f, py = sy + sinf(h + z * 1.5708f) * sr * .66f;
                if (lodQ < 1 && hstep == 1) glow(c, px, py, sd * 5.5f, col, (int) (140 * pul));
                pFill.setColor(0xB0000000);
                c.drawCircle(px, py, sd * 1.35f, pFill);
                pFill.setColor(col);
                c.drawCircle(px, py, sd, pFill);
                pFill.setColor(0xFFFFFFFF);
                c.drawCircle(px - sd * .3f, py - sd * .3f, sd * .38f, pFill);
            }
        }
    }

    private void render(Canvas c, float t) {
        if (state == ST_MENU) { camX = cosf(t * .08f) * 8f; camY = sinf(t * .08f) * 8f; fx = 0; fz = 0; }
        else if (P.alive) {
            float k = 1f - expf(-dtG * (20f + P.v * .4f));
            float la = P.v * .06f;
            camX += (P.x + cosf(P.a) * la - camX) * k; camY += (P.y + sinf(P.a) * la - camY) * k; fx = camX; fz = camY;
        }
        float zt = ppuT;
        if (state == ST_PLAY && P.alive) zt *= (1f - clamp((P.mass - 15f) / 1200f, 0f, .22f)) * (1f - clamp((P.mass - 1215f) / 5000f, 0f, .18f)) * (P.boost ? .94f : 1f);
        HAT_LOD = perfQ;
        ppu += (zt - ppu) * (1f - expf(-dtG * 7f));
        shake *= expf(-dtG * 7f);
        float shx = 0f, shy = 0f;
        if (shake > .02f) { shx = (rand() - .5f) * 2f * shake * dp(7); shy = (rand() - .5f) * 2f * shake * dp(7); }
        c.save();
        c.translate(shx, shy);
        final float vw = W / (2f * ppu) + 6f, vh = H / (2f * ppu) + 6f;
        final float hw = W / 2f, hh = H / 2f;

        // ---- background ----
        Bg bg = BGS[bgIdx];
        if (bg.tile == null) bg.tile = makeTile(bgIdx);
        if (bgShader == null || bgShaderFor != bgIdx) {
            bgShader = new BitmapShader(bg.tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            bgShaderFor = bgIdx;
            pBg.setShader(bgShader);
            pBg.setFilterBitmap(true);
        }
        bgM.setScale(bg.w * ppu * bgZoom / bg.tile.getWidth(), bg.h * ppu * bgZoom / bg.tile.getHeight());
        bgM.postTranslate(hw - camX * ppu, hh - camY * ppu);
        bgShader.setLocalMatrix(bgM);
        c.drawColor(bg.color);
        c.drawRect(-80, -80, W + 80, H + 80, pBg);

        // ---- particles ----
        for (int i = 0; i < PN; i++) {
            if (pl[i] <= 0) continue;
            float k = Math.max(0f, pl[i] / pm[i]), sz = ps[i] * (.4f + k * .7f) * ppu;
            if (sz < .4f) continue;
            float sx = (px[i] - camX) * ppu + hw, sy = (py[i] - camY) * ppu + hh;
            if (sx < -20 || sx > W + 20 || sy < -20 || sy > H + 20) continue;
            pFill.setColor(pc[i] | 0xFF000000);
            pFill.setAlpha((int) (Math.min(1f, k * 1.6f) * 255));
            c.drawCircle(sx, sy, sz, pFill);
        }

        // ---- food ----
        for (int i = 0; i < food.size(); i++) {
            Food f = food.get(i);
            if (f.b > gt) continue;
            if (Math.abs(f.x - camX) > vw + 3 || Math.abs(f.y - camY) > vh + 3) continue;
            float age = f.b != 0 ? gt - f.b : 9f, pop = 1f;
            if (age < .3f) {
                if (age < 0) pop = 0;
                else { float x = age / .3f - 1f; pop = 1f + 2.7f * x * x * x + 1.7f * x * x; }
            }
            float k0 = f.k != 0 ? f.k : (.34f + Math.min(f.v, 8f) * .1f);
            float k = k0 * (1f + sinf(t * 2.6f + f.p) * .04f) * pop * f.sh;
            float sx = (f.x - camX) * ppu + hw, sy = (f.y - camY) * ppu + hh, sr = k * 1.35f * ppu;
            if (sr < .5f) continue;
            float fp = .85f + .15f * sinf(t * 4.5f + f.p);
            glow(c, sx, sy, sr * 4.8f * fp, f.c, 190);
            glow(c, sx, sy, sr * 2.2f * fp, 0xFFFFFFFF, 150);
            pFill.setColor(0x29000000);
            rc.set(sx - sr * 1.1f, sy + sr * .45f - sr * .38f, sx + sr * .9f, sy + sr * .45f + sr * .38f);
            c.drawOval(rc, pFill);
            pFill.setColor(0x4D080812);
            c.drawCircle(sx, sy, sr + 1.3f, pFill);
            pFill.setColor(f.c | 0xFF000000);
            c.drawCircle(sx, sy, sr, pFill);
            sprite(c, GLOSS_F, sx, sy, sr, 255);
            pStroke.setStyle(Paint.Style.STROKE);
            pStroke.setStrokeWidth(2f);
            pStroke.setColor(f.c | 0xFF000000);
            pStroke.setAlpha((int) ((.25f + .2f * sinf(t * 5f + f.p)) * 255));
            c.drawCircle(sx, sy, sr * (1.35f + .15f * sinf(t * 4f + f.p)), pStroke);
        }

        // ---- snakes ----
        for (int si = 0; si < snakes.size(); si++) {
            Snake s = snakes.get(si);
            if (!s.alive) continue;
            int n = Math.min(s.n, MAXSEG);
            float r = s.r, gap = gapOf(s), kw = 6.2832f / (r * 9.5f);
            s.bw += ((s.boost ? 1f : 0f) - s.bw) * Math.min(1f, dtG * 10f);
            float wph = s.dist * kw * .85f + s.p0, wamp = r * .36f * (1f - .45f * s.bw);
            for (int i = 0; i < n; i++) {
                float qx = s.sx[i], qy = s.sy[i];
                if (Math.abs(qx - camX) > vw + r * 3 || Math.abs(qy - camY) > vh + r * 3) { s.rp[i * 3 + 2] = 0; continue; }
                int ia = i > 0 ? i - 1 : 0, ib = i < n - 1 ? i + 1 : n - 1;
                float dx = s.sx[ia] - s.sx[ib], dy = s.sy[ia] - s.sy[ib];
                float l = hypotf(dx, dy);
                if (l == 0) l = 1;
                dx /= l; dy /= l;
                boolean mid = i > 0 && i < n - 1;
                float cx = mid ? qx * .5f + (s.sx[ia] + s.sx[ib]) * .25f : qx;
                float cy = mid ? qy * .5f + (s.sy[ia] + s.sy[ib]) * .25f : qy;
                float u = i * gap, tt = (float) i / n, ramp = Math.min(1f, u / (r * 5.5f));
                float w = wamp * ramp * (1f - .5f * tt) * sinf(wph - u * kw);
                s.rp[i * 3] = cx - dy * w;
                s.rp[i * 3 + 1] = cy + dx * w;
                s.rp[i * 3 + 2] = r * (1f - .6f * tt * tt) * (1f + .025f * sinf(t * 8f - i * .4f));
            }
            boolean isMe = s == P;
            float np = isMe ? neonPower : .85f;
            // level of detail: long snakes draw glow every 2nd/3rd segment (circles still overlap), slow frames thin it out more
            int lodQ = perfQ;
            int hstep = n > 150 ? 3 : (n > 70 ? 2 : 1);
            if (lodQ >= 1) hstep = Math.max(hstep, 2);
            if (lodQ >= 2) hstep = Math.max(hstep, 4);
            boolean glossAll = lodQ < 2;
            drawFins(c, s, n, t, ppu, hw, hh, lodQ, si);   // feather fins + tail (behind body)
            for (int pass = 0; pass < 2; pass++) {
                for (int i = n - 1; i >= 0; i--) {
                    float k = s.rp[i * 3 + 2];
                    if (k <= 0) continue;
                    float sx = (s.rp[i * 3] - camX) * ppu + hw, sy = (s.rp[i * 3 + 1] - camY) * ppu + hh;
                    float sr = k * 1.22f * (i > 0 ? 1f : 1.08f) * ppu;
                    if (sr < .5f) continue;
                    float pulse = .65f + .35f * sinf(t * 9f + i * .35f + s.p0);
                    int bcol = (i > 0 ? s.bands[i] : s.hc) | 0xFF000000;
                    int col = liveColor(bcol, i, t, si * 1.7f);
                    if (pass == 0) {
                        if (i == 0 || (i % hstep == 0 && (isMe || (i & 1) == 0))) {
                            float halo = sr * (s.boost ? 3.8f : 2.8f) * np * pulse * (1f + .22f * (hstep - 1));
                            if (halo > 2) glow(c, sx, sy, halo, bcol, 200);
                        }
                        if (hstep < 3 || (i & 1) == 0) {
                            pFill.setColor(0x2E000000);
                            rc.set(sx - sr * 1.1f, sy + sr * .48f - sr * .42f, sx + sr * .9f, sy + sr * .48f + sr * .42f);
                            c.drawOval(rc, pFill);
                        }
                        pFill.setColor(0xFF000000);
                        c.drawCircle(sx, sy, sr + 1.4f + sr * .1f, pFill);
                    } else {
                        pFill.setColor(col);
                        c.drawCircle(sx, sy, sr, pFill);
                        if (glossAll || (i & 1) == 0) sprite(c, GLOSS_S, sx, sy, sr, isMe ? (int) (200 + 55 * pulse * np) : 255);
                        if (lodQ < 2 && sr > 4f && sparkle(i, t, si)) {
                            pFill.setColor(0xD0FFFFFF);
                            c.drawCircle(sx - sr * .28f, sy - sr * .32f, sr * .2f * (.6f + .4f * sinf(t * 12f + i)), pFill);
                        }
                        if (i % hstep == 0 && (isMe || (i & 1) == 0)) {
                            float coreA = (.12f + .1f * sinf(t * 9f + i * .3f) + (s.boost ? .22f : 0f)) * np;
                            if (coreA > .04f) glow(c, sx, sy, sr * 1.3f, 0xFFFFFFFF, (int) (Math.min(.65f, coreA) * 255));
                        }
                    }
                }
            }
            drawStuds(c, s, n, t, ppu, hw, hh, lodQ, hstep, si);   // side pearl studs (on top of body)
            // eyes + hat
            if (Math.abs(s.x - camX) < vw && Math.abs(s.y - camY) < vh) {
                float ca = cosf(s.a), sa = sinf(s.a);
                float sh = clamp(angDiff(s.a, s.ta), -1f, 1f) * .16f * r;
                for (int zi = 0; zi < 2; zi++) {
                    float z = zi == 0 ? -1f : 1f;
                    float ex = s.x + ca * r * .72f - sa * z * r * .46f, ez = s.y + sa * r * .72f + ca * z * r * .46f;
                    float esx = (ex - camX) * ppu + hw, esy = (ez - camY) * ppu + hh, er = r * .72f * ppu;
                    pFill.setColor(0xFFFFFFFF);
                    if (((t + si * 1.3f) % 3.7f) < .12f) { rc.set(esx - er, esy - er * .2f, esx + er, esy + er * .2f); c.drawOval(rc, pFill); continue; }
                    c.drawCircle(esx, esy, er, pFill);
                    float pxx = ex + ca * r * .34f - sa * sh, pzz = ez + sa * r * .34f + ca * sh;
                    float psx = (pxx - camX) * ppu + hw, psy = (pzz - camY) * ppu + hh;
                    pFill.setColor(0xFF07070d);
                    c.drawCircle(psx, psy, er * .55f, pFill);
                    pFill.setColor(0xFFFFFFFF);
                    c.drawCircle(psx + er * .22f, psy - er * .18f, er * .22f, pFill);
                }
                Hat hs = HATS[s.hat];
                if (hs.kind >= 1) {
                    float S = r * ppu;
                    float hx = s.x - ca * r * 1.0f, hz = s.y - sa * r * 1.0f;
                    drawHatKind(c, pCrown, crownPath, hs.kind, (hx - camX) * ppu + hw, (hz - camY) * ppu + hh - .45f * S, S * .8f);
                } else if (hs.p.size() > 0) {
                    float S = r * ppu;
                    for (int q = 0; q < hs.p.size(); q++) {
                        HatPart hq = hs.p.get(q);
                        float qx = hq.x + (hs.f ? 0f : -1.25f);
                        float hx = s.x + (ca * qx - sa * hq.y) * r, hz = s.y + (sa * qx + ca * hq.y) * r;
                        float hx2 = (hx - camX) * ppu + hw, hy2 = (hz - camY) * ppu + hh - hq.h * S * .28f;
                        float hr = hq.s * S * .85f;
                        if (hr < 1f) continue;
                        drawHatPart(c, pSpr, hq, hx2, hy2, hr);
                    }
                    pSpr.setAlpha(255);
                }
            }
        }

        // ---- name labels ----
        if (state != ST_MENU) {
            pText.setTextSize(dp(10));
            for (int si = 0; si < snakes.size(); si++) {
                Snake s = snakes.get(si);
                if (!s.alive) continue;
                float sx = (s.x - camX) * ppu + hw, sy = (s.y - camY) * ppu + hh;
                if (sx < -80 || sx > W + 80 || sy < -40 || sy > H + 40) continue;
                String txt = (s == leader ? "\uD83D\uDC51 " : "") + label(s);
                float ty = sy + s.r * ppu * 1.7f + dp(10);
                pText.setStyle(Paint.Style.STROKE);
                pText.setStrokeWidth(dp(2.5f));
                pText.setColor(0xCC000000);
                c.drawText(txt, sx, ty, pText);
                pText.setStyle(Paint.Style.FILL);
                pText.setColor(s.bestC | 0xFF000000);
                c.drawText(txt, sx, ty, pText);
            }
        }

        // ---- shockwave rings ----
        for (int i = 0; i < 8; i++) {
            if (wt[i] <= 0) continue;
            float u = 1f - wt[i] / .7f;
            float swx = (wx[i] - camX) * ppu + hw, swy = (wy[i] - camY) * ppu + hh;
            pStroke.setStyle(Paint.Style.STROKE);
            pStroke.setStrokeWidth(dp(3.5f) * (1f - u) + 1f);
            pStroke.setColor(wc[i] | 0xFF000000);
            pStroke.setAlpha((int) (200 * (1f - u)));
            c.drawCircle(swx, swy, (1.5f + u * 14f) * ppu, pStroke);
        }
        c.restore();

        // ---- boost vignette ----
        if (state == ST_PLAY && P.alive && P.boost) {
            if (fxShader == null || fxColor != P.bestC || fxW != W || fxH != H) {
                int rgb = P.bestC & 0xFFFFFF;
                fxShader = new RadialGradient(hw, hh, hypotf(W, H) / 2f, new int[]{rgb, rgb, 0xB0000000 | rgb},
                        new float[]{0f, .4f, 1f}, Shader.TileMode.CLAMP);
                fxColor = P.bestC; fxW = W; fxH = H;
                pFx.setShader(fxShader);
            }
            pFx.setAlpha(150);
            c.drawRect(0, 0, W, H, pFx);
        }

        // ---- boost speed lines ----
        if (state == ST_PLAY && P.alive && P.bw > .05f) {
            float md = Math.min(W, H), al = P.bw;
            pStroke.setStyle(Paint.Style.STROKE); pStroke.setStrokeCap(Paint.Cap.ROUND);
            pStroke.setStrokeWidth(dp(2f)); pStroke.setColor(0xFFFFFFFF);
            for (int k = 0; k < 16; k++) {
                float a = k * .3927f + .11f * (k % 3);
                float ph = (t * 2.4f + k * .173f) % 1f;
                float r0 = md * (.30f + ph * .45f), r1 = r0 + md * .10f * (.4f + ph);
                pStroke.setAlpha((int) (al * 110 * (1f - ph)));
                c.drawLine(hw + cosf(a) * r0, hh + sinf(a) * r0, hw + cosf(a) * r1, hh + sinf(a) * r1, pStroke);
            }
            pStroke.setStrokeCap(Paint.Cap.BUTT);
        }

        // ---- steering arrow ----
        if (state == ST_PLAY && P.alive && !btnOn) {
            float ang = P.a;
            float dx = joyX - joyOx, dy = joyY - joyOy;
            if (joyId != -1 && dx * dx + dy * dy > sq(dp(3))) ang = atan2f(dy, dx);
            aVis += angDiff(aVis, ang) * (1f - expf(-dtG * 28f));
            float sc = dp(54) / 40f;
            c.save();
            c.translate(joyX, joyY);
            c.rotate((float) Math.toDegrees(aVis));
            c.scale(sc, sc);
            pFill.setColor(P.bestC & 0x00FFFFFF | 0x66000000);
            c.drawCircle(0, 0, 20f, pFill);
            pFill.setColor(P.bestC | 0xFF000000);
            pFill.setAlpha(235);
            c.drawPath(arrow, pFill);
            pStroke.setStyle(Paint.Style.STROKE);
            pStroke.setStrokeWidth(2f);
            pStroke.setStrokeJoin(Paint.Join.ROUND);
            pStroke.setColor(0xFFFFFFFF);
            c.drawPath(arrow, pStroke);
            c.restore();
        }
    }

    // =====================================================================
    // BACKGROUND TILES
    // =====================================================================
    private Bitmap makeTile(int i) {
        Bg b = BGS[i];
        int ts = b.cw >= 256 ? 1 : 2;
        Bitmap bmp = Bitmap.createBitmap(b.cw * ts, b.ch * ts, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.scale(ts, ts);
        Rng R = new Rng(1234 + i * 7);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        switch (i) {
            case 0: hexTile(c, p, 0xFF6376e0, 0xFF7f95f3, 0xFF6d83ea, 0xFF5a6dd8); break;
            case 1: hexTile(c, p, 0xFF0d0e13, 0xFF2e313d, 0xFF1d1f27, 0xFF20222b); break;
            case 2: hexTile(c, p, 0xFFdfe3ee, 0xFFffffff, 0xFFeef1f8, 0xFFcfd5e3); break;
            case 3: {
                p.setColor(0xFF070813); c.drawRect(0, 0, 128, 128, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(9); p.setColor(0x1F3cc8ff); gridLines(c, p, 0, 128);
                p.setStrokeWidth(3); p.setColor(0xD950e1ff); gridLines(c, p, 0, 128);
                p.setStrokeWidth(2); p.setColor(0x47ff46c8); gridLines(c, p, 64, 64);
                p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFFFFF);
                c.drawCircle(0, 0, 4, p); c.drawCircle(128, 0, 4, p); c.drawCircle(0, 128, 4, p); c.drawCircle(128, 128, 4, p);
                break;
            }
            case 4: {
                int S = 256;
                p.setColor(0xFF3f9d41); c.drawRect(0, 0, S, S, p);
                p.setColor(0xFF47a94a); c.drawRect(0, 0, 128, 128, p); c.drawRect(128, 128, 256, 256, p);
                float[][] it = new float[160][5];
                for (int k = 0; k < 160; k++) { it[k][0] = R.next() * S; it[k][1] = R.next() * S; it[k][2] = (R.next() - .5f) * 8; it[k][3] = 5 + R.next() * 7; it[k][4] = R.next() < .5f ? 1 : 0; }
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setStrokeCap(Paint.Cap.ROUND);
                for (int ox = -1; ox <= 1; ox++) for (int oy = -1; oy <= 1; oy++) {
                    c.save(); c.translate(ox * S, oy * S);
                    for (int k = 0; k < 160; k++) {
                        p.setColor(it[k][4] == 1 ? 0x66186422 : 0x66bef082);
                        c.drawLine(it[k][0], it[k][1], it[k][0] + it[k][2], it[k][1] - it[k][3], p);
                    }
                    c.restore();
                }
                break;
            }
            case 5: {
                int S = 256;
                p.setColor(0xFF0b5394); c.drawRect(0, 0, S, S, p);
                float[][] bl = new float[7][4], rp = new float[9][3];
                for (int k = 0; k < 7; k++) { bl[k][0] = R.next() * S; bl[k][1] = R.next() * S; bl[k][2] = 40 + R.next() * 50; bl[k][3] = R.next() < .5f ? 1 : 0; }
                for (int k = 0; k < 9; k++) { rp[k][0] = R.next() * S; rp[k][1] = R.next() * S; rp[k][2] = 14 + R.next() * 26; }
                for (int ox = -1; ox <= 1; ox++) for (int oy = -1; oy <= 1; oy++) {
                    c.save(); c.translate(ox * S, oy * S);
                    p.setStyle(Paint.Style.FILL);
                    for (int k = 0; k < 7; k++) {
                        int cc = bl[k][3] == 1 ? 0x475abeff : 0x4D001e5a;
                        p.setShader(new RadialGradient(bl[k][0], bl[k][1], bl[k][2], cc, cc & 0xFFFFFF, Shader.TileMode.CLAMP));
                        c.drawCircle(bl[k][0], bl[k][1], bl[k][2], p);
                    }
                    p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0x38FFFFFF);
                    for (int k = 0; k < 9; k++) {
                        rc.set(rp[k][0] - rp[k][2], rp[k][1] - rp[k][2] * .45f, rp[k][0] + rp[k][2], rp[k][1] + rp[k][2] * .45f);
                        c.drawOval(rc, p);
                    }
                    c.restore();
                }
                break;
            }
            case 6: {
                int S = 256;
                p.setColor(0xFFe8cd92); c.drawRect(0, 0, S, S, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(0x47be8c46);
                for (int k = 0; k < 8; k++) {
                    float y0 = k * 32 + 16;
                    Path pa = new Path();
                    for (int x = 0; x <= S; x += 4) {
                        float y = y0 + sinf(x / (float) S * 6.2832f * 2f + k * 1.3f) * 5f;
                        if (x == 0) pa.moveTo(x, y); else pa.lineTo(x, y);
                    }
                    c.drawPath(pa, p);
                }
                float[][] it = new float[90][4];
                for (int k = 0; k < 90; k++) { it[k][0] = R.next() * S; it[k][1] = R.next() * S; it[k][2] = .8f + R.next() * 1.4f; it[k][3] = R.next() < .5f ? 1 : 0; }
                p.setStyle(Paint.Style.FILL);
                for (int ox = -1; ox <= 1; ox++) for (int oy = -1; oy <= 1; oy++) {
                    c.save(); c.translate(ox * S, oy * S);
                    for (int k = 0; k < 90; k++) {
                        p.setColor(it[k][3] == 1 ? 0x59966432 : 0x80fff5d7);
                        c.drawCircle(it[k][0], it[k][1], it[k][2], p);
                    }
                    c.restore();
                }
                break;
            }
            case 7: {
                int S = 256;
                p.setColor(0xFFff9ec9); c.drawRect(0, 0, S, S, p);
                p.setColor(0xFFffb8da);
                for (int a = 0; a < 4; a++) for (int d = 0; d < 4; d++) if ((a + d) % 2 == 0) c.drawRect(a * 64, d * 64, a * 64 + 64, d * 64 + 64, p);
                int[] cs = {0xFFffffff, 0xFFffe14d, 0xFF7be0ff, 0xFFb08cff, 0xFF7dffb0};
                float[][] it = new float[26][4];
                for (int k = 0; k < 26; k++) { it[k][0] = R.next() * S; it[k][1] = R.next() * S; it[k][2] = R.next() * 3.14f; it[k][3] = cs[(int) (R.next() * 5) % 5]; }
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5); p.setStrokeCap(Paint.Cap.ROUND);
                for (int ox = -1; ox <= 1; ox++) for (int oy = -1; oy <= 1; oy++) {
                    c.save(); c.translate(ox * S, oy * S);
                    for (int k = 0; k < 26; k++) {
                        p.setColor(((int) it[k][3] & 0xFFFFFF) | 0xD9000000);
                        c.drawLine(it[k][0] - cosf(it[k][2]) * 7, it[k][1] - sinf(it[k][2]) * 7, it[k][0] + cosf(it[k][2]) * 7, it[k][1] + sinf(it[k][2]) * 7, p);
                    }
                    c.restore();
                }
                break;
            }
            case 8: {
                int S = 512;
                p.setColor(0xFF04050e); c.drawRect(0, 0, S, S, p);
                float[][] nb = new float[6][4], st = new float[260][3], gl2 = new float[16][3];
                for (int k = 0; k < 6; k++) { nb[k][0] = R.next() * S; nb[k][1] = R.next() * S; nb[k][2] = 110 + R.next() * 110; nb[k][3] = R.next() < .5f ? 1 : 0; }
                for (int k = 0; k < 260; k++) { st[k][0] = R.next() * S; st[k][1] = R.next() * S; st[k][2] = .5f + R.next() * 1.1f; }
                for (int k = 0; k < 16; k++) { gl2[k][0] = R.next() * S; gl2[k][1] = R.next() * S; gl2[k][2] = 5 + R.next() * 5; }
                for (int ox = -1; ox <= 1; ox++) for (int oy = -1; oy <= 1; oy++) {
                    c.save(); c.translate(ox * S, oy * S);
                    p.setStyle(Paint.Style.FILL);
                    for (int k = 0; k < 6; k++) {
                        int rgb = nb[k][3] == 1 ? 0x6e3cc8 : 0x2878ff;
                        p.setShader(new RadialGradient(nb[k][0], nb[k][1], nb[k][2], 0x38000000 | rgb, rgb, Shader.TileMode.CLAMP));
                        c.drawCircle(nb[k][0], nb[k][1], nb[k][2], p);
                    }
                    p.setShader(null); p.setColor(0xB0FFFFFF);
                    for (int k = 0; k < 260; k++) c.drawCircle(st[k][0], st[k][1], st[k][2], p);
                    for (int k = 0; k < 16; k++) {
                        p.setShader(new RadialGradient(gl2[k][0], gl2[k][1], gl2[k][2], 0xE6FFFFFF, 0x00a0beff, Shader.TileMode.CLAMP));
                        c.drawCircle(gl2[k][0], gl2[k][1], gl2[k][2], p);
                    }
                    p.setShader(null);
                    c.restore();
                }
                break;
            }
            case 9:
                p.setColor(0xFFFFFFFF); c.drawRect(0, 0, 128, 128, p);
                p.setColor(0x12000000); c.drawCircle(64, 64, 2.5f, p);
                break;
            default:
                p.setColor(0xFF000000); c.drawRect(0, 0, 128, 128, p);
                p.setColor(0x14FFFFFF); c.drawCircle(64, 64, 2.5f, p);
                break;
        }
        return bmp;
    }

    private void gridLines(Canvas c, Paint p, int a, int b) {
        if (a == b) { c.drawLine(a, 0, a, 128, p); c.drawLine(0, a, 128, a, p); return; }
        c.drawLine(a, 0, a, 128, p); c.drawLine(b, 0, b, 128, p);
        c.drawLine(0, a, 128, a, p); c.drawLine(0, b, 128, b, p);
    }

    private void hexTile(Canvas c, Paint p, int base, int f0, int f1, int st) {
        float r = 128f / sqrtf(3f);
        p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(base);
        c.drawRect(0, 0, 128, 256, p);
        c.save();
        c.scale(1f, 256f / (3f * r));
        float[][] cs = {{0, 0}, {128, 0}, {0, 3 * r}, {128, 3 * r}, {64, 1.5f * r}};
        Path path = new Path();
        for (int q = 0; q < cs.length; q++) {
            path.reset();
            for (int k = 0; k < 6; k++) {
                float a = 3.14159265f / 6f + k * 3.14159265f / 3f;
                float x = cs[q][0] + r * .9f * cosf(a), y = cs[q][1] + r * .9f * sinf(a);
                if (k == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
            p.setShader(new RadialGradient(cs[q][0], cs[q][1], r, new int[]{f0, f0, f1}, new float[]{0f, .2f, 1f}, Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);
            c.drawPath(path, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(7); p.setStrokeJoin(Paint.Join.ROUND); p.setColor(st);
            c.drawPath(path, p);
        }
        c.restore();
        p.setStyle(Paint.Style.FILL); p.setShader(null);
    }

    // =====================================================================
    // GAME FLOW (called from UI thread)
    // =====================================================================
    private void startGame() { startGame(15f); }

    private void startGame(float startMass) {
        reviveUsed = false; scoreDoubled = false; roundsPlayed++;
        synchronized (lock) {
            String nm = etName.getText().toString().trim();
            if (nm.length() == 0) nm = "You";
            prefs.edit().putString("name", nm).apply();
            P.name = nm;
            setSkin(P, curSkin);
            P.hat = curHat;
            fx = 0; fz = 0;
            spawn(P, 0, 0, startMass, Float.NaN);
            camX = 0; camY = 0;
            newWorld();
            boostFuel = boostMax; boostMassMark = startMass; boostRefillAt = 100f; neonPower = 1f; playerRank = 1;
            for (int a = 0; a < snakes.size(); a++) {
                Snake o = snakes.get(a);
                if (o != P && o.alive && hypotf(o.x, o.y) < 16f) {
                    float[] q = ringPos(30f, LIVE - 6f);
                    float dx = q[0] - o.x, dy = q[1] - o.y;
                    for (int i = 0; i < o.n; i++) { o.sx[i] += dx; o.sy[i] += dy; }
                    o.x = q[0]; o.y = q[1];
                }
            }
            clearSave();
            enterPlayLocked();
        }
        playSfx(sStartId, .8f, 1f);
        showPlayUi();
    }

    private void enterPlayLocked() {
        state = ST_PLAY;
        leader = null; lbT = 0; uiT = 0; boostIn = false; btnOn = false;
        aVis = P.a; joyId = -1; btnId = -1;
        joyX = W / 2f + cosf(P.a) * 100f; joyY = H / 2f + sinf(P.a) * 100f; joyOx = joyX; joyOy = joyY;
    }

    private void showPlayUi() {
        menuV.setVisibility(View.GONE);
        overV.setVisibility(View.GONE);
        hideScreens();
        hudV.setAlpha(0f);
        hudV.setVisibility(View.VISIBLE);
        hudV.animate().alpha(1f).setDuration(300).start();
    }

    private void pauseGame() {
        boolean did = false;
        synchronized (lock) {
            if (state == ST_PLAY) {
                state = ST_PAUSED;
                boostIn = false; btnOn = false;
                saveGame();
                did = true;
            }
        }
        if (did) {
            hudV.setVisibility(View.GONE);
            if (pauseV != null) pauseV.setVisibility(View.VISIBLE);   // pause screen: only a Resume button
            updateBurger();
        }
    }

    private void resumeGame() {
        boolean ok = false;
        synchronized (lock) {
            if (state == ST_PAUSED) { enterPlayLocked(); ok = true; }
            else if (loadGame()) { enterPlayLocked(); ok = true; }
        }
        if (ok) { playSfx(sStartId, .8f, 1f); showPlayUi(); }
        else { clearSave(); refreshMenu(); }
    }

    private void toMenu() {
        synchronized (lock) {
            state = ST_MENU;
            clearWorld();
            P.alive = false;
        }
        overV.setVisibility(View.GONE);
        hudV.setVisibility(View.GONE);
        if (pauseV != null) pauseV.setVisibility(View.GONE);
        goScreen(SC_SKIN);
    }

    private boolean canResume() { return state == ST_PAUSED || (state == ST_MENU && prefs.contains("save")); }

    private void clearSave() { prefs.edit().remove("save").apply(); }

    // ---- save / load ----
    private JSONObject snJson(Snake s) throws Exception {
        JSONObject o = new JSONObject();
        o.put("x", r2(s.x)); o.put("y", r2(s.y)); o.put("a", r2(s.a)); o.put("m", r2(s.mass));
        o.put("k", s.kills); o.put("n", s.name); o.put("p", s.skin.p); o.put("w", s.skin.w); o.put("h", s.hat);
        JSONArray cl = new JSONArray();
        for (int i = 0; i < s.skin.c.length; i++) cl.put(s.skin.c[i]);
        o.put("c", cl);
        JSONArray g = new JSONArray();
        for (int i = 0; i < s.n; i++) { g.put(r2(s.sx[i])); g.put(r2(s.sy[i])); }
        o.put("g", g);
        return o;
    }
    private static double r2(float v) { return Math.round(v * 100f) / 100.0; }

    private void saveGame() {
        if (!P.alive) return;
        try {
            JSONObject d = new JSONObject();
            d.put("P", snJson(P));
            JSONArray B = new JSONArray();
            for (int i = 0; i < snakes.size(); i++) {
                Snake s = snakes.get(i);
                if (s.bot && s.alive) { JSONObject o = snJson(s); o.put("i", s.idx); B.put(o); }
            }
            d.put("B", B);
            JSONArray F = new JSONArray();
            for (int i = 0; i < food.size(); i++) {
                Food f = food.get(i);
                JSONArray a = new JSONArray();
                a.put(Math.round(f.x * 10f) / 10.0); a.put(Math.round(f.y * 10f) / 10.0); a.put(r2(f.v)); a.put(f.c);
                F.put(a);
            }
            d.put("F", F);
            d.put("n", botN);
            prefs.edit().putString("save", d.toString()).apply();
        } catch (Exception e) { }
    }

    private void putSnake(Snake s, JSONObject o) throws Exception {
        JSONArray cl = o.getJSONArray("c");
        int[] cols = new int[cl.length()];
        for (int i = 0; i < cols.length; i++) cols[i] = cl.getInt(i);
        setSkin(s, new Skin(o.getInt("p"), o.getInt("w"), cols));
        spawn(s, (float) o.getDouble("x"), (float) o.getDouble("y"), (float) o.getDouble("m"), (float) o.getDouble("a"));
        s.kills = o.getInt("k"); s.hat = o.getInt("h");
        JSONArray g = o.getJSONArray("g");
        int n = Math.min(MAXSEG, g.length() / 2);
        for (int i = 0; i < n; i++) { s.sx[i] = (float) g.getDouble(i * 2); s.sy[i] = (float) g.getDouble(i * 2 + 1); }
        if (n > 0) s.n = n;
    }

    private boolean loadGame() {
        String js = prefs.getString("save", null);
        if (js == null) return false;
        try {
            JSONObject d = new JSONObject(js);
            JSONObject po = d.getJSONObject("P");
            botN = Math.max(0, Math.min(BOTS, d.getInt("n")));
            for (int i = 0; i < snakes.size(); i++) if (snakes.get(i).bot) snakes.get(i).alive = false;
            P.name = po.getString("n");
            putSnake(P, po);
            if (hatLocked(P.hat)) P.hat = 0;   // premium hats only stay on after a rewarded ad in this session
            JSONArray B = d.getJSONArray("B");
            for (int i = 0; i < B.length(); i++) {
                JSONObject o = B.getJSONObject(i);
                int idx = o.getInt("i");
                for (int a = 0; a < snakes.size(); a++) {
                    Snake b = snakes.get(a);
                    if (b.bot && b.idx == idx) putSnake(b, o);
                }
            }
            food.clear();
            JSONArray F = d.getJSONArray("F");
            for (int i = 0; i < F.length(); i++) {
                JSONArray f = F.getJSONArray(i);
                addFood((float) f.getDouble(0), (float) f.getDouble(1), (float) f.getDouble(2), f.getInt(3), 0, 0, 0, 0, 0);
            }
            camX = P.x; camY = P.y; fx = camX; fz = camY;
            final int bn = botN;
            ui.post(new Runnable() {
                @Override public void run() {
                    ((SeekBar) findViewById(R.id.seek_bots)).setProgress(bn);
                    tvBn.setText("BOTS PLAYING: " + bn);
                    if (sbBotsNew != null) { sbBotsNew.setProgress(bn); tvBotsNew.setText("OPPONENTS (BOTS): " + bn); }
                }
            });
            return true;
        } catch (Exception e) { return false; }
    }

    // =====================================================================
    // UI HELPERS
    // =====================================================================
    private GradientDrawable rr(int fill, float rdp, int stroke, float sdp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(rdp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(sdp)), stroke);
        return g;
    }

    private GradientDrawable grad(int c1, int c2, float rdp, int stroke) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{c1, c2});
        g.setCornerRadius(dp(rdp));
        if (stroke != 0) g.setStroke(dp(1.5f), stroke);
        return g;
    }

    /** 3D game button: light top -> colour -> darker, with a solid bottom edge */
    private Drawable fancy(int c1, int c2, float rdp) {
        int dark = Color.rgb((int) (Color.red(c2) * .5f), (int) (Color.green(c2) * .5f), (int) (Color.blue(c2) * .5f));
        GradientDrawable base = rr(dark, rdp, 0, 0);
        GradientDrawable face = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{lerpColor(c1, 0xFFFFFFFF, .28f), c1, c2});
        face.setCornerRadius(dp(rdp));
        face.setStroke(dp(1.5f), 0x66FFFFFF);
        android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(new Drawable[]{base, face});
        ld.setLayerInset(1, 0, 0, 0, dp(4));
        return ld;
    }

    /** big, easy-to-touch menu button */
    private void menuBtn(TextView t, int c1, int c2, float rdp, int minDp) {
        setBg(t, fancy(c1, c2, rdp));
        t.setMinimumHeight(dp(minDp));
        t.setGravity(Gravity.CENTER);
        t.setTextColor(0xFFFFFFFF);
        t.setShadowLayer(dp(2), 0, dp(1), 0x88000000);
    }

    private GradientDrawable oval(int fill, int stroke, float sdp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        if (stroke != 0) g.setStroke(dp(sdp), stroke);
        return g;
    }

    @SuppressWarnings("deprecation")
    private void setBg(View v, Drawable d) {
        if (Build.VERSION.SDK_INT >= 16) v.setBackground(d); else v.setBackgroundDrawable(d);
    }

    private void showToast(String msg) {
        tvToast.setText(msg);
        tvToast.setAlpha(1f);
        tvToast.animate().cancel();
        tvToast.animate().alpha(0f).setStartDelay(1500).setDuration(300).start();
    }

    private TextView chip(String text, boolean on) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(9), dp(10), dp(9));
        t.setMinimumHeight(dp(46));
        styleChip(t, on);
        return t;
    }

    private void styleChip(TextView t, boolean on) {
        t.setTextColor(on ? 0xFF14163a : 0xFFFFFFFF);
        setBg(t, rr(on ? 0xFFFFFFFF : 0x14FFFFFF, 14, 0x24FFFFFF, 1));
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    private LinearLayout.LayoutParams cellLp(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }

    // =====================================================================
    // VIEW INIT
    // =====================================================================
    private void initViews() {
        surface = (SurfaceView) findViewById(R.id.game_surface);
        hudV = findViewById(R.id.hud);
        menuV = findViewById(R.id.menu);
        overV = findViewById(R.id.over);
        tvPn = (TextView) findViewById(R.id.tv_pn);
        tvLen = (TextView) findViewById(R.id.tv_len);
        tvRank = (TextView) findViewById(R.id.tv_rank);
        tvLb = (TextView) findViewById(R.id.tv_lb);
        tvBt = (TextView) findViewById(R.id.bt);
        tvToast = (TextView) findViewById(R.id.tv_toast);
        btnPause = (TextView) findViewById(R.id.btn_pause);
        btnSnd = (TextView) findViewById(R.id.btn_snd);
        btnBg = (TextView) findViewById(R.id.btn_bg);
        boostBtn = (TextView) findViewById(R.id.boost_btn);
        bbar = (FrameLayout) findViewById(R.id.bbar);
        bi = findViewById(R.id.bi);
        btnResume = (TextView) findViewById(R.id.btn_resume);
        btnNext = (TextView) findViewById(R.id.btn_next);
        btnPlay = (TextView) findViewById(R.id.btn_play);
        btnAgain = (TextView) findViewById(R.id.btn_again);
        btnToMenu = (TextView) findViewById(R.id.btn_tomenu);
        tvWhy = (TextView) findViewById(R.id.tv_why);
        tvRl = (TextView) findViewById(R.id.tv_rl);
        tvRkl = (TextView) findViewById(R.id.tv_rkl);
        tvRb = (TextView) findViewById(R.id.tv_rb);
        tvBn = (TextView) findViewById(R.id.tv_bn);
        tvZn = (TextView) findViewById(R.id.tv_zn);
        etName = (EditText) findViewById(R.id.et_name);

        // ---- styling (no drawable resources) ----
        setBg(findViewById(R.id.menu_card), grad(0xEB242962, 0xF00b0d26, 24, 0x38FFFFFF));
        setBg(findViewById(R.id.over_card), grad(0xEB242962, 0xF00b0d26, 24, 0x38FFFFFF));
        setBg(findViewById(R.id.st1), rr(0x1FFFFFFF, 14, 0x1FFFFFFF, 1));
        setBg(findViewById(R.id.st2), rr(0x1FFFFFFF, 14, 0x1FFFFFFF, 1));
        setBg(findViewById(R.id.st3), rr(0x1FFFFFFF, 14, 0x1FFFFFFF, 1));
        setBg(etName, rr(0x1FFFFFFF, 26, 0x40FFFFFF, 1));
        setBg(findViewById(R.id.score_panel), grad(0xFF4d5598, 0xFF181b42, 14, 0xFF0b0d26));
        setBg(tvLb, rr(0x80121530, 10, 0x24FFFFFF, 1));
        setBg(btnPause, oval(0xFF2273e8, 0xE6FFFFFF, 3));
        menuBtn(btnSnd, 0xFF5560c0, 0xFF2a3080, 18, 52);
        menuBtn(btnBg, 0xFF5560c0, 0xFF2a3080, 18, 52);
        setBg(boostBtn, oval(0x80ff6a3c, 0xFFffb04a, 3));
        setBg(bbar, rr(0xFF0c0e26, 11, 0xFF0b0d26, 2));
        setBg(bi, rr(0xFFff7a3c, 9, 0, 0));
        setBg(tvToast, rr(0x99000000, 30, 0, 0));
        menuBtn(btnNext, 0xFF9a8bff, 0xFF4a36c9, 28, 58);
        menuBtn(btnPlay, 0xFF47c8ff, 0xFF0b56c9, 28, 62);
        menuBtn(btnResume, 0xFF6beb8a, 0xFF1b9a45, 28, 58);
        menuBtn(btnAgain, 0xFF47c8ff, 0xFF0b56c9, 28, 58);
        menuBtn(btnToMenu, 0xFF9a8bff, 0xFF4a36c9, 28, 58);
        tvToast.setVisibility(View.VISIBLE);

        etName.setText(prefs.getString("name", ""));
        muteLabel();

        // ---- surface touch (steer + 2nd finger boost) ----
        surface.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked(), ai = e.getActionIndex();
                int id = e.getPointerId(ai);
                if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
                    if (joyId == -1) { joyId = id; joyX = joyOx = e.getX(ai); joyY = joyOy = e.getY(ai); }
                    else if (state == ST_PLAY) boostIn = true;
                } else if (a == MotionEvent.ACTION_MOVE) {
                    for (int i = 0; i < e.getPointerCount(); i++)
                        if (e.getPointerId(i) == joyId) moveJoy(e.getX(i), e.getY(i));
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_POINTER_UP || a == MotionEvent.ACTION_CANCEL) {
                    if (id == joyId || a == MotionEvent.ACTION_CANCEL) { if (joyId != btnId) joyId = -1; }
                    if (e.getPointerCount() - 1 < 2 && !btnOn) boostIn = false;
                }
                return true;
            }
        });

        // ---- boost button = thumb stick ----
        boostBtn.setOnTouchListener(new View.OnTouchListener() {
            float bx, by;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    btnOn = true; boostIn = true;
                    btnId = e.getPointerId(0);
                    surface.getLocationOnScreen(sloc);
                    bx = e.getRawX(); by = e.getRawY();
                    if (joyId == -1) { joyId = btnId; joyX = joyOx = bx - sloc[0]; joyY = joyOy = by - sloc[1]; }
                    setBg(v, oval(0xCCff7a3c, 0xFFffe27a, 3));
                } else if (a == MotionEvent.ACTION_MOVE) {
                    float rx = e.getRawX(), ry = e.getRawY();
                    if (joyId == btnId) moveJoy(rx - sloc[0], ry - sloc[1]);
                    float dx = rx - bx, dy = ry - by, d = hypotf(dx, dy), m = dp(30);
                    if (d > m) { dx *= m / d; dy *= m / d; }
                    v.setTranslationX(dx); v.setTranslationY(dy);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    btnOn = false; boostIn = false;
                    if (joyId == btnId) joyId = -1;
                    btnId = -1;
                    v.setTranslationX(0); v.setTranslationY(0);
                    setBg(v, oval(0x80ff6a3c, 0xFFffb04a, 3));
                }
                return true;
            }
        });

        btnPause.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { pauseGame(); } });
        btnSnd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { muted = !muted; prefs.edit().putBoolean("muted", muted).apply(); muteLabel(); }
        });
        btnBg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyBG(bgIdx + 1); }
        });
        btnAgain.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { interstitialThen(new Runnable() { @Override public void run() { startGame(); } }); } });
        btnToMenu.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { interstitialThen(new Runnable() { @Override public void run() { toMenu(); } }); } });
        btnPlay.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startGame(); } });
        btnResume.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { resumeGame(); } });
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenuStep(menuStep + 1); playSfx(sEat, .5f, .7f); }
        });
    }

    // =====================================================================
    // EXTRA UI: exit, in-game zoom, press effects, styling
    // =====================================================================
    private TextView bigBtn(String text, int c1, int c2, float sp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        menuBtn(t, c1, c2, 28, 58);
        if (Build.VERSION.SDK_INT >= 21) t.setElevation(dp(5));
        pressFx(t);
        return t;
    }

    private TextView roundBtn(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(24);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        setBg(t, oval(0xAA2a3070, 0xCCFFFFFF, 2));
        if (Build.VERSION.SDK_INT >= 21) t.setElevation(dp(4));
        pressFx(t);
        return t;
    }

    private void pressFx(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View x, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) x.animate().scaleX(.94f).scaleY(.94f).alpha(.85f).setDuration(70).start();
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) x.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
                return false;
            }
        });
    }

    private void buildExtraUi() {
        // exit button in menu / pause screen
        LinearLayout box = (LinearLayout) findViewById(R.id.linear11);
        btnExit = bigBtn("Exit game", 0xFFff6a6a, 0xFFc01f4a, 18);
        LinearLayout.LayoutParams lpx = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpx.topMargin = dp(8);
        box.addView(btnExit, lpx);
        btnExit.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { confirmExit(); } });

        // skins & cosmetics picker button
        TextView btnSkins = bigBtn("Skins & Cosmetics", 0xFF6beb8a, 0xFF1b9a45, 18);
        box.addView(btnSkins, Math.max(0, box.indexOfChild(btnExit)), adLp(false));
        btnSkins.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openPicker(); playSfx(sEat, .5f, .7f); } });
        preview.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openPicker(); } });

        // exit button on game-over card
        ViewGroup oc = (ViewGroup) findViewById(R.id.over_card);
        btnExitOver = bigBtn("Exit game", 0xFFff6a6a, 0xFFc01f4a, 18);
        LinearLayout.LayoutParams lpo = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpo.topMargin = dp(10);
        oc.addView(btnExitOver, lpo);
        btnExitOver.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { confirmExit(); } });

        // press feedback + depth on existing buttons
        View[] bs = { btnNext, btnPlay, btnResume, btnAgain, btnToMenu, btnPause, btnSnd, btnBg };
        for (int i = 0; i < bs.length; i++) {
            pressFx(bs[i]);
            if (Build.VERSION.SDK_INT >= 21) bs[i].setElevation(dp(4));
        }
        buildMusicBtn();
        TextView title = (TextView) findViewById(R.id.tv_title);
        title.setShadowLayer(dp(14), 0, 0, 0xFF8a7bff);
        if (Build.VERSION.SDK_INT >= 21) title.setLetterSpacing(.06f);
        buildAdUi();
        buildPrevBtn();
        buildSideBar();
        buildFlow();
    }

    // =====================================================================
    // SIDEBAR: Privacy Policy / Rate App / Share App
    // =====================================================================
    private FrameLayout sideRoot;
    private View sideScrim, sidePanel;
    private TextView btnBurger;
    private boolean sideOpen;

    private void buildSideBar() {
        // hamburger button (top-left, only on menu / pause screens)
        btnBurger = new TextView(this);
        btnBurger.setText("\u2630");
        btnBurger.setTextSize(22);
        btnBurger.setTypeface(Typeface.DEFAULT_BOLD);
        btnBurger.setTextColor(0xFFFFFFFF);
        btnBurger.setGravity(Gravity.CENTER);
        setBg(btnBurger, oval(0xAA2a3070, 0xCCFFFFFF, 2));
        if (Build.VERSION.SDK_INT >= 21) btnBurger.setElevation(dp(40));
        pressFx(btnBurger);
        FrameLayout.LayoutParams lb = new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.TOP | Gravity.LEFT);
        lb.leftMargin = dp(10); lb.topMargin = dp(10);
        addContentView(btnBurger, lb);
        btnBurger.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openSide(); } });

        // drawer: dim scrim + sliding panel
        sideRoot = new FrameLayout(this);
        sideRoot.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= 21) sideRoot.setElevation(dp(50));

        sideScrim = new View(this);
        sideScrim.setBackgroundColor(0x33000000);   // light dim so a changed background stays visible
        sideScrim.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { closeSide(); } });
        sideRoot.addView(sideScrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setClickable(true);
        setBg(sv, grad(0xFF2c326e, 0xFF0b0d26, 0, 0));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(6), dp(56), dp(6), dp(16));
        sv.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sidePanel = sv;

        TextView title = new TextView(this);
        title.setText("Menu");
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFFFFFFF);
        title.setShadowLayer(dp(12), 0, 0, 0xFF8a7bff);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        panel.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView bPrivacy = bigBtn("\uD83D\uDD12\nPrivacy", 0xFF5560c0, 0xFF2a3080, 10);
        TextView bRate = bigBtn("\u2B50\nRate app", 0xFFffd86b, 0xFFe08a00, 10);
        TextView bShare = bigBtn("\u27A6\nShare app", 0xFF6beb8a, 0xFF1b9a45, 10);
        TextView bClose = bigBtn("\u2715\nClose", 0xFF8a7bff, 0xFF4a36c9, 10);
        // category 1: settings (Sound / Music / Background moved here from the main menu)
        panel.addView(sideLabel("SETTINGS"));
        moveToSide(panel, btnSnd);
        moveToSide(panel, btnMusic);
        moveToSide(panel, btnBg);
        // category 2: more
        panel.addView(sideLabel("MORE"));
        panel.addView(bPrivacy, adLp(false));
        panel.addView(bRate, adLp(false));
        panel.addView(bShare, adLp(false));
        moveToSide(panel, btnPrivacy);   // ads privacy options (EEA only, hidden elsewhere)
        moveToSide(panel, btnExit);
        LinearLayout.LayoutParams lcl = adLp(false);
        lcl.topMargin = dp(22);
        panel.addView(bClose, lcl);
        btnExit.setText("\uD83D\uDEAA\nExit");
        btnPrivacy.setText("\uD83D\uDEE1\nAd privacy");
        TextView[] narrow = {btnSnd, btnMusic, btnBg, bPrivacy, bRate, bShare, btnPrivacy, btnExit, bClose};
        for (int i = 0; i < narrow.length; i++) {   // compact icon-style buttons for the slim sidebar
            narrow[i].setTextSize(10);
            narrow[i].setPadding(dp(2), dp(8), dp(2), dp(8));
            narrow[i].setMinimumHeight(dp(60));
            narrow[i].setSingleLine(false);
        }

        bPrivacy.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { closeSide(); openUrl(PRIVACY_URL); } });
        bRate.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { closeSide(); rateApp(); } });
        bShare.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { closeSide(); shareApp(); } });
        bClose.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { closeSide(); } });

        int pw = Math.max(dp(80), (int) (getResources().getDisplayMetrics().widthPixels * 0.2f));   // slim sidebar: ~20% of the screen
        sideRoot.addView(sv, new FrameLayout.LayoutParams(pw, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));
        addContentView(sideRoot, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateBurger();
    }

    // =====================================================================
    // FLOW: simple step-by-step screens (replaces the old Next / Previous menu)
    //   Step 1  Pick snake  (+ Cosmetics hats screen, + Build your own snake)
    //   Step 2  Background + camera / zoom
    //   Step 3  Opponents (bots) + name + PLAY
    //   Pause   only a big Resume button
    // =====================================================================
    static final int SC_NONE = -1, SC_SKIN = 0, SC_HATS = 1, SC_BG = 2, SC_GO = 3, SC_BUILD = 4;
    private int screen = SC_SKIN;
    private FrameLayout flowRoot;
    private final LinearLayout[] scr = new LinearLayout[5];
    private View pauseV;
    private HatList hatList;
    private PlayPreview playPrev;
    private PreviewView finalPreview, builderPrev;
    private final View[] bgDots2 = new View[BGS.length];
    private SeekBar sbBotsNew, sbC1, sbC2;
    private TextView tvBotsNew, tvCamNew, tvBgzNew;
    private View chipC1, chipC2;
    private final TextView[] patChips2 = new TextView[4];

    private void buildFlow() {
        menuV.setVisibility(View.GONE);   // the old wizard menu is no longer used
        flowRoot = new FrameLayout(this);
        flowRoot.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= 21) flowRoot.setElevation(dp(30));
        addContentView(flowRoot, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        buildHatScreen();
        buildBgScreen();
        buildGoScreen();
        buildBuildScreen();
        buildPauseView();
        goScreen(SC_SKIN);
    }

    private void goScreen(int s) {
        screen = s;
        if (s == SC_SKIN) openPicker();
        else if (picker != null) picker.setVisibility(View.GONE);
        if (flowRoot != null) {
            flowRoot.setVisibility(s > SC_SKIN ? View.VISIBLE : View.GONE);
            for (int i = 1; i < scr.length; i++) if (scr[i] != null) scr[i].setVisibility(i == s ? View.VISIBLE : View.GONE);
        }
        if (s == SC_GO) {
            if (finalPreview != null) finalPreview.setSkin(curSkin);
            if (sbBotsNew != null) { sbBotsNew.setProgress(botN); tvBotsNew.setText("OPPONENTS (BOTS): " + botN); }
        }
        updateBurger();
    }

    private void hideScreens() {
        goScreen(SC_NONE);
        if (pauseV != null) pauseV.setVisibility(View.GONE);
    }

    private TextView hintTv(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(0xFFd8dcff);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(8), 0, dp(8));
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    /** one full-screen page: header (step + title), scrolling body, footer buttons. Returns the body to fill. */
    private LinearLayout makeScr(int id, String step, String title, String backTxt, String nextTxt, int c1, int c2,
                                 View.OnClickListener onBack, View.OnClickListener onNext) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClickable(true);
        setBg(root, grad(0xFF2c326e, 0xFF0b0d26, 0, 0));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER);
        head.setPadding(dp(66), dp(16), dp(66), dp(8));
        TextView st = new TextView(this);
        st.setText(step);
        st.setTextSize(11);
        st.setTypeface(Typeface.DEFAULT_BOLD);
        st.setTextColor(0xFF9bb0ff);
        st.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= 21) st.setLetterSpacing(.12f);
        TextView ti = new TextView(this);
        ti.setText(title);
        ti.setTextSize(22);
        ti.setTypeface(Typeface.DEFAULT_BOLD);
        ti.setTextColor(0xFFFFFFFF);
        ti.setGravity(Gravity.CENTER);
        ti.setShadowLayer(dp(10), 0, 0, 0xFF8a7bff);
        head.addView(st, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        head.addView(ti, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sv = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(4), dp(16), dp(16));
        sv.addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setPadding(dp(12), dp(8), dp(12), dp(12));
        setBg(foot, rr(0xDD0b0d26, 0, 0, 0));
        if (backTxt != null) {
            TextView b = bigBtn(backTxt, 0xFF5a64b8, 0xFF2a2f78, 17);
            LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lb.rightMargin = dp(8);
            foot.addView(b, lb);
            b.setOnClickListener(onBack);
        }
        TextView n = bigBtn(nextTxt, c1, c2, 21);
        foot.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, backTxt != null ? 2f : 1f));
        n.setOnClickListener(onNext);
        root.addView(foot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scr[id] = root;
        root.setVisibility(View.GONE);
        flowRoot.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return body;
    }

    // ---- Cosmetics: every hat shown on the chosen snake, live ----
    private void buildHatScreen() {
        LinearLayout b = makeScr(SC_HATS, "COSMETICS", "Choose a Hat", null, "✔  Done", 0xFF6beb8a, 0xFF1b9a45, null,
                new View.OnClickListener() { @Override public void onClick(View v) { playSfx(sEat, .5f, .8f); goScreen(SC_SKIN); } });
        b.addView(hintTv("Tap a hat to put it on your snake"));
        hatList = new HatList();
        b.addView(hatList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void chooseHat(int idx) {
        if (hatLocked(idx)) { askUnlock(true, idx); return; }
        selectHatIdx(idx);
        if (hatList != null) hatList.invalidate();
    }

    // ---- Step 2: background, camera distance, background zoom ----
    private void buildBgScreen() {
        LinearLayout b = makeScr(SC_BG, "STEP 2 OF 3", "Choose Background", "◀  Back", "Next  ▶", 0xFF47c8ff, 0xFF0b56c9,
                new View.OnClickListener() { @Override public void onClick(View v) { goScreen(SC_SKIN); } },
                new View.OnClickListener() { @Override public void onClick(View v) { playSfx(sEat, .5f, .8f); goScreen(SC_GO); } });
        playPrev = new PlayPreview();
        bgPrevs.add(playPrev);
        b.addView(playPrev, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        b.addView(hintTv("This is how your snake will look in the game"));

        b.addView(sideLabel("TAP A BACKGROUND"));
        LinearLayout rw = null;
        for (int i = 0; i < BGS.length; i++) {
            if (i % 4 == 0) { rw = row(); b.addView(rw); }
            final int idx = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(2), dp(6), dp(2), dp(6));
            View dot = new View(this);
            cell.addView(dot, new LinearLayout.LayoutParams(dp(48), dp(48)));
            TextView nm = new TextView(this);
            nm.setText(BGS[i].n);
            nm.setTextSize(11);
            nm.setTextColor(0xFFdfe4ff);
            nm.setSingleLine(true);
            nm.setGravity(Gravity.CENTER);
            nm.setPadding(0, dp(3), 0, 0);
            cell.addView(nm, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { applyBG(idx); playSfx(sEat, .4f, .7f); } });
            rw.addView(cell, cellLp(1f));
            bgDots2[i] = dot;
        }

        // camera distance
        tvCamNew = sideLabel("CAMERA: " + Math.round(camDist) + "   (left = closer, right = farther)");
        b.addView(tvCamNew);
        SeekBar sbCam = new SeekBar(this);
        sbCam.setMax(38);
        sbCam.setProgress(Math.round(camDist) - 12);
        sbCam.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int pr, boolean user) {
                synchronized (lock) { camDist = 12 + pr; ppuT = Math.min(W, H) / camDist; }
                tvCamNew.setText("CAMERA: " + (12 + pr) + "   (left = closer, right = farther)");
                prefs.edit().putInt("zoom", 12 + pr).apply();
                for (int k = 0; k < bgPrevs.size(); k++) bgPrevs.get(k).invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        b.addView(sbCam, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // background zoom only
        tvBgzNew = sideLabel("BACKGROUND SIZE: " + Math.round(bgZoom * 100f) + "%");
        b.addView(tvBgzNew);
        SeekBar sbBgz = new SeekBar(this);
        sbBgz.setMax(150);
        sbBgz.setProgress(Math.round(bgZoom * 100f) - 50);
        sbBgz.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int pr, boolean user) {
                bgZoom = (50 + pr) / 100f;
                tvBgzNew.setText("BACKGROUND SIZE: " + (50 + pr) + "%");
                prefs.edit().putInt("bgz", 50 + pr).apply();
                for (int k = 0; k < bgPrevs.size(); k++) bgPrevs.get(k).invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        b.addView(sbBgz, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        applyBG(bgIdx);
    }

    // ---- Step 3: name, bots, play ----
    private void buildGoScreen() {
        LinearLayout b = makeScr(SC_GO, "STEP 3 OF 3", "Ready to Play", "◀  Back", "▶  PLAY", 0xFF5be07a, 0xFF1b9a45,
                new View.OnClickListener() { @Override public void onClick(View v) { goScreen(SC_BG); } },
                new View.OnClickListener() { @Override public void onClick(View v) { startGame(); } });
        finalPreview = new PreviewView(this);
        finalPreview.setSkin(curSkin);
        b.addView(finalPreview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        b.addView(sideLabel("YOUR NAME"));
        etName = new EditText(this);
        etName.setHint("Your name");
        etName.setHintTextColor(0x88FFFFFF);
        etName.setTextColor(0xFFFFFFFF);
        etName.setTextSize(18);
        etName.setSingleLine(true);
        etName.setGravity(Gravity.CENTER);
        etName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});
        etName.setPadding(dp(16), dp(12), dp(16), dp(12));
        setBg(etName, rr(0x1FFFFFFF, 26, 0x40FFFFFF, 1));
        etName.setText(prefs.getString("name", ""));
        b.addView(etName, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvBotsNew = sideLabel("OPPONENTS (BOTS): " + botN);
        b.addView(tvBotsNew);
        sbBotsNew = new SeekBar(this);
        sbBotsNew.setMax(BOTS);
        sbBotsNew.setProgress(botN);
        sbBotsNew.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int pr, boolean user) {
                synchronized (lock) { botN = pr; applyBots(); }
                tvBotsNew.setText("OPPONENTS (BOTS): " + pr);
                prefs.edit().putInt("bots", pr).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        b.addView(sbBotsNew, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView gi = bigBtn("Start Giant (" + (int) GIANT_MASS + " mass) - Watch Ad", 0xFFffb04a, 0xFFe0561b, 16);
        LinearLayout.LayoutParams lg = adLp(false);
        lg.topMargin = dp(20);
        b.addView(gi, lg);
        gi.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showRewarded("giant", new Runnable() { @Override public void run() { startGame(GIANT_MASS); } });
            }
        });
    }

    // ---- Build your own snake ----
    private LinearLayout colorRow(View chip, SeekBar sb) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lc = new LinearLayout.LayoutParams(dp(40), dp(40));
        lc.rightMargin = dp(10);
        r.addView(chip, lc);
        r.addView(sb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private void applyBuilder() {
        custom.c = new int[]{hsl(sbC1.getProgress() / 360f, 1f, .6f), hsl(sbC2.getProgress() / 360f, 1f, .6f)};
        setBg(chipC1, rr(custom.c[0], 8, 0xFFFFFFFF, 2));
        setBg(chipC2, rr(custom.c[1], 8, 0xFFFFFFFF, 2));
        choose(custom, 2, 0);
    }

    private void buildBuildScreen() {
        LinearLayout b = makeScr(SC_BUILD, "BUILD YOUR OWN", "Make Your Snake", null, "✔  Done", 0xFF6beb8a, 0xFF1b9a45, null,
                new View.OnClickListener() { @Override public void onClick(View v) { choose(custom, 2, 0); playSfx(sEat, .5f, .8f); goScreen(SC_SKIN); } });
        builderPrev = new PreviewView(this);
        builderPrev.setSkin(custom);
        b.addView(builderPrev, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        SeekBar.OnSeekBarChangeListener cl = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int pr, boolean user) { if (user) applyBuilder(); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        };
        chipC1 = new View(this); chipC2 = new View(this);
        sbC1 = new SeekBar(this); sbC1.setMax(360); sbC1.setProgress(326); sbC1.setOnSeekBarChangeListener(cl);
        sbC2 = new SeekBar(this); sbC2.setMax(360); sbC2.setProgress(190); sbC2.setOnSeekBarChangeListener(cl);
        b.addView(sideLabel("FIRST COLOR"));
        b.addView(colorRow(chipC1, sbC1));
        b.addView(sideLabel("SECOND COLOR"));
        b.addView(colorRow(chipC2, sbC2));
        setBg(chipC1, rr(custom.c[0], 8, 0xFFFFFFFF, 2));
        setBg(chipC2, rr(custom.c[1], 8, 0xFFFFFFFF, 2));

        b.addView(sideLabel("PATTERN"));
        LinearLayout pr = row();
        String[] pn = {"Solid", "Stripes", "Fade", "Dots"};
        final int[] pv = {P_SOLID, P_STRIPE, P_FADE, P_DOTS};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView ch = chip(pn[i], pv[i] == custom.p);
            ch.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    custom.p = pv[idx];
                    for (int k = 0; k < 4; k++) styleChip(patChips2[k], k == idx);
                    applyBuilder();
                }
            });
            pr.addView(ch, cellLp(1f));
            patChips2[i] = ch;
        }
        b.addView(pr);
    }

    // ---- Pause: only a Resume button ----
    private void buildPauseView() {
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(0xDD0b0d26);
        f.setClickable(true);
        if (Build.VERSION.SDK_INT >= 21) f.setElevation(dp(25));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = new TextView(this);
        t.setText("Paused");
        t.setTextSize(34);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFFFFFFFF);
        t.setShadowLayer(dp(12), 0, 0, 0xFF8a7bff);
        t.setGravity(Gravity.CENTER);
        box.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView sub = new TextView(this);
        sub.setText("Your game is saved");
        sub.setTextSize(15);
        sub.setTextColor(0xFFd8dcff);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(28));
        box.addView(sub, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView r = bigBtn("▶  Resume", 0xFF5be07a, 0xFF1b9a45, 26);
        box.addView(r, new LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT));
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { resumeGame(); } });
        f.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        f.setVisibility(View.GONE);
        addContentView(f, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pauseV = f;
    }

    // ---- Cosmetics list: the chosen snake wearing every hat (live) ----
    private class HatList extends View {
        final SkinArt art = new SkinArt();
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final float rowH = dp(86);
        final int slop = dp(8);
        float dx0, dy0;

        HatList() { super(MainActivity.this); setClickable(true); }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            setMeasuredDimension(View.MeasureSpec.getSize(wSpec), (int) (rowH * HATS.length));
        }

        @Override protected void onDraw(Canvas c) {
            float t = (System.nanoTime() - startNs) / 1e9f;
            int w = getWidth();
            android.graphics.Rect cb = c.getClipBounds();
            for (int i = 0; i < HATS.length; i++) {
                float top = i * rowH;
                if (top + rowH < cb.top || top > cb.bottom) continue;
                boolean on = i == curHat, lk = hatLocked(i);
                r.set(dp(2), top + dp(4), w - dp(2), top + rowH - dp(4));
                p.setStyle(Paint.Style.FILL); p.setColor(on ? 0x33FFFFFF : 0x12FFFFFF);
                c.drawRoundRect(r, dp(16), dp(16), p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(on ? dp(3) : dp(1)); p.setColor(on ? 0xFF9bff5a : 0x24FFFFFF);
                c.drawRoundRect(r, dp(16), dp(16), p);
                pt.setStyle(Paint.Style.FILL); pt.setTextAlign(Paint.Align.LEFT); pt.setTypeface(Typeface.DEFAULT_BOLD);
                pt.setTextSize(dp(16)); pt.setColor(0xFFFFFFFF);
                c.drawText(i == 0 ? "No hat" : HATS[i].n, dp(16), top + rowH / 2f - dp(2), pt);
                pt.setTextSize(dp(12)); pt.setColor(on ? 0xFF9bff5a : 0xFFb8c0ff);
                c.drawText(on ? "✔ Selected" : (lk ? "🔒 Watch ad to unlock" : "Tap to wear"), dp(16), top + rowH / 2f + dp(16), pt);
                art.snake(c, curSkin, w * .5f, w - dp(34), top + rowH / 2f + dp(4), dp(13), dp(6), 5f, t, i * .9f, dp(1.4f), false, HATS[i]);
            }
            if (getVisibility() == View.VISIBLE) postInvalidateOnAnimation();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: dx0 = e.getX(); dy0 = e.getY(); return true;
                case MotionEvent.ACTION_UP:
                    if (Math.abs(e.getX() - dx0) < slop && Math.abs(e.getY() - dy0) < slop) {
                        int i = (int) (e.getY() / rowH);
                        if (i >= 0 && i < HATS.length) chooseHat(i);
                    }
                    return true;
            }
            return true;
        }
    }

    // ---- live preview of snake on the chosen background (same zoom maths as the game) ----
    private class PlayPreview extends View {
        final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Paint pb = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Matrix m = new Matrix();
        final Path clip = new Path();
        final RectF r = new RectF();
        final SkinArt art = new SkinArt();
        BitmapShader sh;
        int shFor = -1;

        PlayPreview() { super(MainActivity.this); }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            setMeasuredDimension(View.MeasureSpec.getSize(wSpec), dp(210));
        }

        @Override protected void onDraw(Canvas c) {
            float t = (System.nanoTime() - startNs) / 1e9f;
            float w = getWidth(), h = getHeight(), rad = dp(16);
            Bg bg = BGS[bgIdx];
            r.set(0, 0, w, h);
            clip.reset();
            clip.addRoundRect(r, rad, rad, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            c.drawColor(bg.color);
            Bitmap tl = bg.tile;
            if (tl != null) {
                if (sh == null || shFor != bgIdx) {
                    sh = new BitmapShader(tl, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                    shFor = bgIdx;
                    p.setShader(sh);
                }
                float u = w / camDist * bgZoom;
                m.setScale(bg.w * u / tl.getWidth(), bg.h * u / tl.getHeight());
                m.postTranslate(w / 2f, h / 2f);
                sh.setLocalMatrix(m);
                c.drawRect(0, 0, w, h, p);
            }
            // snake drawn at in-game size for this camera distance
            float R = Math.max(dp(4), .75f * w / camDist);
            art.snake(c, curSkin, w * .12f, w * .88f, h / 2f, R, R * .8f, 5.4f, t, 0f, 1.5f, false, HATS[curHat]);
            c.restore();
            pb.setStyle(Paint.Style.STROKE); pb.setStrokeWidth(2); pb.setColor(0x66FFFFFF);
            r.set(1, 1, w - 1, h - 1);
            c.drawRoundRect(r, rad, rad, pb);
            postInvalidateOnAnimation();
        }
    }

    private TextView sideLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFF9bb0ff);
        t.setPadding(dp(4), dp(18), 0, dp(2));
        if (Build.VERSION.SDK_INT >= 21) t.setLetterSpacing(.12f);
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    /** takes an existing menu button out of the main menu and puts it in the sidebar */
    private void moveToSide(LinearLayout panel, View v) {
        if (v == null) return;
        ViewGroup par = (ViewGroup) v.getParent();
        if (par != null) par.removeView(v);
        panel.addView(v, adLp(false));
    }

    private void openSide() {
        if (sideRoot == null || sideOpen) return;
        sideOpen = true;
        sideRoot.setVisibility(View.VISIBLE);
        sideScrim.setAlpha(0f);
        sideScrim.animate().alpha(1f).setDuration(200).start();
        sidePanel.setTranslationX(-sidePanel.getLayoutParams().width);
        sidePanel.animate().translationX(0f).setDuration(220).start();
        playSfx(sEat, .5f, .7f);
    }

    private void closeSide() {
        if (sideRoot == null || !sideOpen) return;
        sideOpen = false;
        sideScrim.animate().alpha(0f).setDuration(180).start();
        sidePanel.animate().translationX(-sidePanel.getLayoutParams().width).setDuration(200)
                .withEndAction(new Runnable() {
                    @Override public void run() { if (!sideOpen) sideRoot.setVisibility(View.GONE); }
                }).start();
    }

    /** hamburger only shows on the menu / pause screens (not in game, not while the skin picker is open) */
    private void updateBurger() {
        if (btnBurger == null) return;
        boolean show = state == ST_MENU;   // only on the setup screens (not in game, not on pause)
        btnBurger.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) closeSide();
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { adToast("Could not open the link"); }
    }

    private void rateApp() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + PLAY_PACKAGE))); }
        catch (ActivityNotFoundException e) { openUrl(PLAY_URL); }
        catch (Exception e) { openUrl(PLAY_URL); }
    }

    private void shareApp() {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, "Slither Arena");
            i.putExtra(Intent.EXTRA_TEXT, "Try Slither Arena - a fun snake battle game!\n" + PLAY_URL);
            startActivity(Intent.createChooser(i, "Share app via"));
        } catch (Exception e) { adToast("Could not share"); }
    }

    private void zoomBy(float d) {
        synchronized (lock) {
            camDist = clamp(camDist + d, 12f, 50f);
            ppuT = Math.min(W, H) / camDist;
        }
        int z = Math.round(camDist);
        prefs.edit().putInt("zoom", z).apply();
        if (sbZoomRef != null) sbZoomRef.setProgress(z - 12);
        showToast("Zoom " + (z <= 20 ? "in" : (z >= 38 ? "far out" : "")) + " (" + z + ")");
    }

    private void confirmExit() {
        final AlertDialog dlg = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).create();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(20));
        setBg(card, grad(0xFF4d5598, 0xFF181b42, 22, 0xFF8a7bff));
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(10));

        TextView t = new TextView(this);
        t.setText("Exit Slither Arena?");
        t.setTextSize(22);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        t.setShadowLayer(dp(12), 0, 0, 0xFF8a7bff);
        card.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView m = new TextView(this);
        m.setText("Your current game is saved so you can resume it later.");
        m.setTextSize(15);
        m.setTextColor(0xFFd8dcff);
        m.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpm = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpm.topMargin = dp(10);
        card.addView(m, lpm);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lpr = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpr.topMargin = dp(20);
        card.addView(row, lpr);

        TextView stay = bigBtn("Stay", 0xFF5be07a, 0xFF1b9a45, 17);
        TextView exit = bigBtn("Exit", 0xFFff6a6a, 0xFFc01f4a, 17);
        LinearLayout.LayoutParams lps = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lps.rightMargin = dp(6);
        LinearLayout.LayoutParams lpe = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpe.leftMargin = dp(6);
        row.addView(stay, lps);
        row.addView(exit, lpe);

        stay.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dlg.dismiss(); } });
        exit.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dlg.dismiss(); doExit(); } });

        dlg.setView(card);
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            dlg.getWindow().setLayout(Math.min(dp(320), (int) (getResources().getDisplayMetrics().widthPixels * 0.9f)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void doExit() {
        if (state == ST_PLAY) pauseGame();
        // finish and also remove the app from the Recents list
        if (Build.VERSION.SDK_INT >= 21) finishAndRemoveTask();
        else finish();
    }

    // =====================================================================
    // ADS  -  all ad logic lives here
    //
    //  AdMob is live (ADS_TEST_MODE = false). Needs the gradle dependency
    //  com.google.android.gms:play-services-ads:23.6.0 (min SDK 21; it already
    //  includes the UMP consent SDK, no extra dependency). Replace the 2 TEST unit ids
    //  at the top of the file with your own before release.
    //
    //  Rewarded placements : revive, giant, unlock_hat, unlock_flag, double_score, refill
    //  Interstitial        : every INTERSTITIAL_EVERY-th tap on Play Again / Menu
    //
    // =====================================================================
    private volatile boolean adBusy, reviveUsed, scoreDoubled;
    private volatile int finalScore, deathKills;
    private volatile float deathMass;
    private long adStartMs;
    private boolean adEarned;
    private int adCounter;
    private Runnable adOnReward, interNext;
    private RewardedAd rewardedAd;
    private InterstitialAd interstitialAd;

    // ---- consent (Google UMP) - ads are only requested after consent is handled ----
    private ConsentInformation consentInfo;
    private boolean adsStarted;

    // ---- preload manager: both ads are always kept loaded, retried with back-off, refreshed before they expire ----
    static final long AD_STALE_MS = 50L * 60L * 1000L;   // AdMob ads expire after about 1 hour -> swap earlier
    private boolean rewardedLoading, interLoading, interDue;
    private long rewardedAt, interAt, lastFullAdMs, rewardedLoadT, interLoadT;
    private int rewardedFails, interFails, roundsPlayed;
    private final Handler adH = new Handler(Looper.getMainLooper());
    private final Runnable adKeepWarm = new Runnable() {
        @Override public void run() { ensureAds(); adH.postDelayed(this, 30000L); }
    };

    private boolean rewardedReady() { return rewardedAd != null && System.currentTimeMillis() - rewardedAt < AD_STALE_MS; }
    private boolean interReady() { return interstitialAd != null && System.currentTimeMillis() - interAt < AD_STALE_MS; }
    private long adBackoff(int fails) { return Math.min(60000L, 3000L << Math.min(5, Math.max(0, fails - 1))); }

    /** called on start, on resume and every 30 s: makes sure a fresh rewarded + interstitial ad is waiting */
    private void ensureAds() {
        if (ADS_TEST_MODE || !adsStarted) return;
        long now = System.currentTimeMillis();
        if (rewardedLoading && now - rewardedLoadT > 60000L) rewardedLoading = false;   // a load that never answered must not block new ones
        if (interLoading && now - interLoadT > 60000L) interLoading = false;
        if (!rewardedReady()) { rewardedAd = null; loadRewarded(); }
        if (!interReady()) { interstitialAd = null; loadInterstitial(); }
    }

    /** true when a rewarded ad can be shown right now; otherwise tells the player and starts loading */
    private boolean adReadyOrWarn() {
        if (ADS_TEST_MODE || rewardedReady()) return true;
        adToast("Ad is still loading - try again in a few seconds");
        ensureAds();
        return false;
    }

    private void initAds() {
        if (ADS_TEST_MODE) return;
        consentInfo = UserMessagingPlatform.getConsentInformation(this);
        ConsentRequestParameters.Builder pb = new ConsentRequestParameters.Builder();
        if (UMP_TEST_DEVICE_HASH.length() > 0) {
            pb.setConsentDebugSettings(new ConsentDebugSettings.Builder(this)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .addTestDeviceHashedId(UMP_TEST_DEVICE_HASH).build());
        }
        consentInfo.requestConsentInfoUpdate(this, pb.build(),
                new ConsentInformation.OnConsentInfoUpdateSuccessListener() {
                    @Override public void onConsentInfoUpdateSuccess() {
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(MainActivity.this,
                                new ConsentForm.OnConsentFormDismissedListener() {
                                    @Override public void onConsentFormDismissed(FormError err) { consentReady(); }
                                });
                    }
                },
                new ConsentInformation.OnConsentInfoUpdateFailureListener() {
                    @Override public void onConsentInfoUpdateFailure(FormError err) { consentReady(); }
                });
        consentReady();   // returning users who already gave consent can start ads right away
    }

    private void consentReady() {
        if (btnPrivacy != null) {
            boolean req = consentInfo != null && consentInfo.getPrivacyOptionsRequirementStatus()
                    == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
            btnPrivacy.setVisibility(req ? View.VISIBLE : View.GONE);
        }
        if (adsStarted || consentInfo == null || !consentInfo.canRequestAds()) return;
        adsStarted = true;
        try { MobileAds.initialize(this); } catch (Throwable t) { adSetupError(t); }
        loadRewarded();
        loadInterstitial();
    }

    // ---- rewarded: call showRewarded(placement, whatToGiveOnReward) ----
    private void showRewarded(String placement, Runnable onReward) {
        long now = System.currentTimeMillis();
        if (adBusy && now - adStartMs < 90000L) return;
        if (!adReadyOrWarn()) return;
        adBusy = true; adStartMs = now; adEarned = false; adOnReward = onReward;
        if (ADS_TEST_MODE) {
            adToast("TEST MODE: pretending to watch a rewarded ad...");
            ui.postDelayed(new Runnable() { @Override public void run() { adDone(true); } }, 800);
            return;
        }
        admobShowRewarded(placement);
    }

    /** Must be called when the rewarded ad is closed or failed. rewarded = true -> reward is given. */
    private void adDone(final boolean rewarded) {
        ui.post(new Runnable() {
            @Override public void run() {
                adBusy = false;
                joyId = -1; btnId = -1; boostIn = false; btnOn = false;
                Runnable r = adOnReward; adOnReward = null;
                if (rewarded && r != null) r.run();
                else if (!rewarded) adToast("Ad not available right now - no reward");
            }
        });
    }

    private void admobShowRewarded(String placement) {
        if (!rewardedReady()) { adDone(false); ensureAds(); return; }
        rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { rewardedAd = null; lastFullAdMs = System.currentTimeMillis(); loadRewarded(); adDone(adEarned); }
            @Override public void onAdFailedToShowFullScreenContent(AdError e) { rewardedAd = null; loadRewarded(); adDone(false); }
        });
        try {
            rewardedAd.show(this, new OnUserEarnedRewardListener() {
                @Override public void onUserEarnedReward(RewardItem item) { adEarned = true; }
            });
        } catch (Throwable t) { rewardedAd = null; adSetupError(t); adDone(false); }
    }

    private void loadRewarded() {
        if (!adsStarted || rewardedLoading || rewardedAd != null) return;
        rewardedLoading = true; rewardedLoadT = System.currentTimeMillis();
        try {
            RewardedAd.load(this, AD_REWARDED_ID, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
                @Override public void onAdLoaded(RewardedAd ad) {
                    rewardedAd = ad; rewardedAt = System.currentTimeMillis(); rewardedLoading = false; rewardedFails = 0;
                }
                @Override public void onAdFailedToLoad(LoadAdError e) {
                    rewardedAd = null; rewardedLoading = false; rewardedFails++;
                    adH.postDelayed(new Runnable() { @Override public void run() { loadRewarded(); } }, adBackoff(rewardedFails));
                }
            });
        } catch (Throwable t) { rewardedLoading = false; rewardedAd = null; adSetupError(t); }
    }

    // ---- interstitial: shown on every INTERSTITIAL_EVERY-th call, then runs "next" ----
    // Only at natural breaks (after a round, never during play), at most every INTERSTITIAL_EVERY-th break, never right after
    // another full-screen ad (INTERSTITIAL_MIN_GAP_MS), never before the 2nd round, and only when an ad is already loaded.
    private void interstitialThen(final Runnable next) {
        adCounter++;
        if (INTERSTITIAL_EVERY > 0 && adCounter >= INTERSTITIAL_EVERY) interDue = true;
        boolean calm = System.currentTimeMillis() - lastFullAdMs > INTERSTITIAL_MIN_GAP_MS;
        if (interDue && calm && roundsPlayed >= 2 && (ADS_TEST_MODE || interReady())) {
            interDue = false; adCounter = 0;
            showInterstitial(next);
        } else next.run();
    }

    private void showInterstitial(Runnable next) {
        if (ADS_TEST_MODE) { adToast("TEST MODE: interstitial ad would show here"); next.run(); return; }
        interNext = next;
        admobShowInterstitial();
    }

    /** Must be called when the interstitial is closed or failed - it continues the game. */
    private void interstitialDone() {
        ui.post(new Runnable() {
            @Override public void run() {
                Runnable n = interNext; interNext = null;
                if (n != null) n.run();
            }
        });
    }

    private void admobShowInterstitial() {
        if (!interReady()) { interstitialDone(); ensureAds(); return; }
        interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { interstitialAd = null; lastFullAdMs = System.currentTimeMillis(); loadInterstitial(); interstitialDone(); }
            @Override public void onAdFailedToShowFullScreenContent(AdError e) { interstitialAd = null; loadInterstitial(); interstitialDone(); }
        });
        try { interstitialAd.show(this); }
        catch (Throwable t) { interstitialAd = null; adSetupError(t); interstitialDone(); }
    }

    private void loadInterstitial() {
        if (!adsStarted || interLoading || interstitialAd != null) return;
        interLoading = true; interLoadT = System.currentTimeMillis();
        try {
            InterstitialAd.load(this, AD_INTERSTITIAL_ID, new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
                @Override public void onAdLoaded(InterstitialAd ad) {
                    interstitialAd = ad; interAt = System.currentTimeMillis(); interLoading = false; interFails = 0;
                }
                @Override public void onAdFailedToLoad(LoadAdError e) {
                    interstitialAd = null; interLoading = false; interFails++;
                    adH.postDelayed(new Runnable() { @Override public void run() { loadInterstitial(); } }, adBackoff(interFails));
                }
            });
        } catch (Throwable t) { interLoading = false; interstitialAd = null; adSetupError(t); }
    }

    private boolean adErrShown;

    /** Ads must never crash the game: show the real reason once (usually a missing manifest entry). */
    private void adSetupError(Throwable t) {
        if (adErrShown) return;
        adErrShown = true;
        String m = String.valueOf(t.getMessage());
        adToast("Ad setup error: " + (m.length() > 160 ? m.substring(0, 160) : m));
    }

    private void adToast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    // ---- ad buttons ----
    private LinearLayout.LayoutParams adLp(boolean w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w ? 0 : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, w ? 1f : 0f);
        lp.topMargin = dp(8);
        return lp;
    }

    private void buildAdUi() {
        // --- game over card: [Revive (Ad)] [2x Score (Ad)] above Play Again ---
        btnRevive = bigBtn("Revive (Ad)", 0xFF5be07a, 0xFF1b9a45, 15);
        btnDouble = bigBtn("2x Score (Ad)", 0xFFffd86b, 0xFFe08a00, 15);
        btnRevive.setVisibility(View.GONE);
        btnDouble.setVisibility(View.GONE);
        LinearLayout adRow = new LinearLayout(this);
        adRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lr = adLp(false);
        LinearLayout.LayoutParams l1 = adLp(true); l1.topMargin = 0; l1.rightMargin = dp(4);
        LinearLayout.LayoutParams l2 = adLp(true); l2.topMargin = 0; l2.leftMargin = dp(4);
        adRow.addView(btnRevive, l1);
        adRow.addView(btnDouble, l2);
        ViewGroup host = (ViewGroup) findViewById(R.id.over_card);
        int at = -1;
        ViewGroup bp = (ViewGroup) btnAgain.getParent();
        if (bp instanceof LinearLayout && ((LinearLayout) bp).getOrientation() == LinearLayout.VERTICAL) { host = bp; at = bp.indexOfChild(btnAgain); }
        host.addView(adRow, at, lr);
        btnRevive.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showRewarded("revive", new Runnable() { @Override public void run() { revivePlayer(); } });
            }
        });
        btnDouble.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showRewarded("double_score", new Runnable() { @Override public void run() { doubleScore(); } });
            }
        });

        // --- menu: Start Giant button under Play ---
        btnGiant = bigBtn("Start Giant (" + (int) GIANT_MASS + " Mass) - Ad", 0xFFffb04a, 0xFFe0561b, 17);
        ViewGroup pp = (ViewGroup) btnPlay.getParent();
        if (pp instanceof LinearLayout && ((LinearLayout) pp).getOrientation() == LinearLayout.VERTICAL) {
            pp.addView(btnGiant, pp.indexOfChild(btnPlay) + 1, adLp(false));
        } else {
            LinearLayout box = (LinearLayout) findViewById(R.id.linear11);
            box.addView(btnGiant, Math.max(0, box.indexOfChild(btnExit)), adLp(false));
        }
        btnGiant.setVisibility(btnPlay.getVisibility());
        btnGiant.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showRewarded("giant", new Runnable() { @Override public void run() { startGame(GIANT_MASS); } });
            }
        });

        // --- Privacy settings button (EEA/UK only, required by Google) ---
        btnPrivacy = bigBtn("Privacy settings (ads)", 0xFF3c448c, 0xFF16193e, 14);
        LinearLayout box3 = (LinearLayout) findViewById(R.id.linear11);
        box3.addView(btnPrivacy, Math.max(0, box3.indexOfChild(btnExit)), adLp(false));
        btnPrivacy.setVisibility(View.GONE);
        btnPrivacy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                UserMessagingPlatform.showPrivacyOptionsForm(MainActivity.this, new ConsentForm.OnConsentFormDismissedListener() {
                    @Override public void onConsentFormDismissed(FormError err) { consentReady(); }
                });
            }
        });

        // --- pause screen: Back to Menu button (above Exit) ---
        btnBackMenu = bigBtn("Back to Menu", 0xFF8a7bff, 0xFF4a36c9, 18);
        LinearLayout box2 = (LinearLayout) findViewById(R.id.linear11);
        box2.addView(btnBackMenu, Math.max(0, box2.indexOfChild(btnExit)), adLp(false));
        btnBackMenu.setVisibility(state == ST_PAUSED ? View.VISIBLE : View.GONE);
        btnBackMenu.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                interstitialThen(new Runnable() { @Override public void run() { toMenu(); } });
            }
        });

        // --- HUD: Refill Boost button (only visible when boost is empty) ---
        btnRefill = bigBtn("Refill Boost (Ad)", 0xFFffb04a, 0xFFe0561b, 13);
        btnRefill.setPadding(dp(14), dp(8), dp(14), dp(8));
        btnRefill.setVisibility(View.GONE);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM);
        rl.rightMargin = dp(16); rl.bottomMargin = dp(182);
        ((FrameLayout) hudV).addView(btnRefill, rl);
        btnRefill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (state != ST_PLAY) return;
                if (!adReadyOrWarn()) return;
                pauseGame();   // keeps the game safe while the prompt / ad is on screen
                adPrompt("Refill boost?", "Watch a short ad to refill your boost.",
                        new Runnable() {
                            @Override public void run() {
                                showRewarded("refill", new Runnable() {
                                    @Override public void run() {
                                        synchronized (lock) { boostFuel = boostMax; }
                                        resumeGame();
                                    }
                                });
                            }
                        },
                        new Runnable() { @Override public void run() { resumeGame(); } });
            }
        });
    }

    // ---- reward: revive on death (once per match, mass + kills kept) ----
    private void revivePlayer() {
        boolean ok = false;
        synchronized (lock) {
            if (state == ST_OVER && !reviveUsed) {
                float[] sp = freeSpot(0f, 22f);
                spawn(P, sp[0], sp[1], Math.max(15f, deathMass), Float.NaN);
                P.kills = deathKills;
                boostFuel = boostMax;
                shake = 0f;
                enterPlayLocked();
                reviveUsed = true;
                ok = true;
            }
        }
        if (ok) { playSfx(sStartId, .8f, 1f); showPlayUi(); }
    }

    // ---- reward: 2x final score on game over card ----
    private void doubleScore() {
        if (state != ST_OVER || scoreDoubled) return;
        scoreDoubled = true;
        finalScore *= 2;
        synchronized (lock) {
            if (finalScore > best) { best = finalScore; prefs.edit().putInt("best", best).apply(); }
        }
        tvRl.setText("" + finalScore);
        tvRb.setText("" + best);
        btnDouble.setVisibility(View.GONE);
        btnRevive.setVisibility(View.GONE);
    }

    // ---- reward: unlock premium hats / flags ----
    private static final String LOCK_ICON = "\uD83D\uDD12 ";

    // unlocks last only for the current app session (not saved) - locked again after the app is closed and reopened
    private final boolean[] hatUnl = new boolean[HATS.length];
    private final boolean[] flagUnl = new boolean[FLAGS.length];

    private boolean hatLocked(int i) {
        String n = HATS[i].n;
        for (int k = 0; k < PREMIUM_HATS.length; k++)
            if (PREMIUM_HATS[k].equals(n)) return !hatUnl[i];
        return false;
    }

    private boolean flagLocked(int i) { return LOCK_FLAGS && !flagUnl[i]; }
    private String hatLabel(int i) { return hatLocked(i) ? LOCK_ICON + HATS[i].n : HATS[i].n; }
    private String flagLabel(int i) { return flagLocked(i) ? LOCK_ICON + FLAGS[i].n : FLAGS[i].n; }

    private void askUnlock(final boolean hat, final int idx) {
        if (!adReadyOrWarn()) return;   // the ad is preloaded; if it is somehow not ready yet, say so instead of a dead button
        final String nm = hat ? HATS[idx].n : FLAGS[idx].n;
        adPrompt("Unlock " + nm + "?", "Watch a short ad to unlock the " + nm + (hat ? " hat" : " flag skin") + " for this session.",
                new Runnable() {
                    @Override public void run() {
                        showRewarded(hat ? "unlock_hat" : "unlock_flag", new Runnable() {
                            @Override public void run() { unlockItem(hat, idx); }
                        });
                    }
                });
    }

    private void unlockItem(boolean hat, int idx) {
        if (hat) {
            hatUnl[idx] = true;
            prefs.edit().putInt("hat", idx).apply();
            hatChips[idx].setText(hatLabel(idx));
            curHat = idx;
            for (int k = 0; k < hatChips.length; k++) styleChip(hatChips[k], k == idx);
            preview.invalidate();
            if (hatList != null) hatList.invalidate();
        } else {
            flagUnl[idx] = true;
            flagTxt[idx].setText(flagLabel(idx));
            choose(flagSkin(idx), 1, idx);
        }
        playSfx(sEat, .5f, 1f);
    }

    private void adPrompt(String title, String msg, final Runnable onWatch) { adPrompt(title, msg, onWatch, null); }

    private void adPrompt(String title, String msg, final Runnable onWatch, final Runnable onCancel) {
        final AlertDialog dlg = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).create();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(20));
        setBg(card, grad(0xFF4d5598, 0xFF181b42, 22, 0xFF8a7bff));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(21);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        card.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView m = new TextView(this);
        m.setText(msg);
        m.setTextSize(15);
        m.setTextColor(0xFFd8dcff);
        m.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpm = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpm.topMargin = dp(10);
        card.addView(m, lpm);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lpr = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpr.topMargin = dp(20);
        card.addView(row, lpr);

        TextView watch = bigBtn("Watch Ad", 0xFF5be07a, 0xFF1b9a45, 16);
        TextView cancel = bigBtn("Cancel", 0xFF8a7bff, 0xFF4a36c9, 16);
        LinearLayout.LayoutParams lw = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lw.rightMargin = dp(6);
        LinearLayout.LayoutParams lc = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lc.leftMargin = dp(6);
        row.addView(watch, lw);
        row.addView(cancel, lc);
        watch.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dlg.dismiss(); onWatch.run(); } });
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { dlg.dismiss(); if (onCancel != null) onCancel.run(); } });
        dlg.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override public void onCancel(DialogInterface d) { if (onCancel != null) onCancel.run(); }
        });

        dlg.setView(card);
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            dlg.getWindow().setLayout(Math.min(dp(320), (int) (getResources().getDisplayMetrics().widthPixels * 0.9f)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    // =====================================================================
    // BACKGROUND MUSIC (assets/bg.mp3, looped, on/off saved)
    // =====================================================================
    private void initMusic() {
        try {
            bgm = new MediaPlayer();
            try {
                android.content.res.AssetFileDescriptor afd = getAssets().openFd("bg.mp3");
                bgm.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            } catch (Exception e1) {
                // compressed asset: copy to cache and play from there
                File f = new File(getCacheDir(), "bg.mp3");
                java.io.InputStream in = getAssets().open("bg.mp3");
                FileOutputStream fo = new FileOutputStream(f);
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
                fo.close();
                in.close();
                bgm.reset();
                bgm.setDataSource(f.getAbsolutePath());
            }
            bgm.setAudioStreamType(AudioManager.STREAM_MUSIC);
            bgm.setLooping(true);
            bgm.setVolume(.45f, .45f);
            bgm.prepare();
        } catch (Exception e) {
            releaseMusic();
        }
    }

    private void startMusic() {
        try { if (bgm != null && musicOn && !bgm.isPlaying()) bgm.start(); } catch (Exception e) { }
    }

    private void pauseMusic() {
        try { if (bgm != null && bgm.isPlaying()) bgm.pause(); } catch (Exception e) { }
    }

    private void releaseMusic() {
        try { if (bgm != null) { bgm.release(); } } catch (Exception e) { }
        bgm = null;
    }

    private void musicLabel() { if (btnMusic != null) btnMusic.setText(musicOn ? "\u266B\nMusic on" : "\u266B\nMusic off"); }

    private void buildMusicBtn() {
        btnMusic = new TextView(this);
        btnMusic.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, btnSnd.getTextSize());
        btnMusic.setTypeface(btnSnd.getTypeface());
        btnMusic.setTextColor(btnSnd.getTextColors());
        btnMusic.setGravity(btnSnd.getGravity());
        btnMusic.setPadding(btnSnd.getPaddingLeft(), btnSnd.getPaddingTop(), btnSnd.getPaddingRight(), btnSnd.getPaddingBottom());
        menuBtn(btnMusic, 0xFF5560c0, 0xFF2a3080, 18, 52);
        pressFx(btnMusic);
        if (Build.VERSION.SDK_INT >= 21) btnMusic.setElevation(dp(4));
        ViewGroup par = (ViewGroup) btnSnd.getParent();
        if (par instanceof LinearLayout && btnSnd.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            par.addView(btnMusic, par.indexOfChild(btnSnd) + 1, new LinearLayout.LayoutParams((LinearLayout.LayoutParams) btnSnd.getLayoutParams()));
        } else {
            LinearLayout box = (LinearLayout) findViewById(R.id.linear11);
            box.addView(btnMusic, Math.max(0, box.indexOfChild(btnExit)), adLp(false));
        }
        musicLabel();
        btnMusic.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                musicOn = !musicOn;
                prefs.edit().putBoolean("music", musicOn).apply();
                musicLabel();
                if (musicOn) startMusic(); else pauseMusic();
            }
        });
    }

    private void muteLabel() { btnSnd.setText(muted ? "\uD83D\uDD07\nSound off" : "\uD83D\uDD0A\nSound on"); }

    private void applyBG(int i) {
        bgIdx = (i + BGS.length) % BGS.length;
        prefs.edit().putInt("bg", bgIdx).apply();
        btnBg.setText("\uD83D\uDDBC\nBackground");
        for (int k = 0; k < bgDots.length; k++)
            if (bgDots[k] != null) setBg(bgDots[k], oval(BGS[k].color, k == bgIdx ? 0xFFFFFFFF : 0x66FFFFFF, k == bgIdx ? 3.5f : 1.5f));
        for (int k = 0; k < bgDots2.length; k++)
            if (bgDots2[k] != null) setBg(bgDots2[k], oval(BGS[k].color, k == bgIdx ? 0xFF9bff5a : 0x66FFFFFF, k == bgIdx ? 4f : 1.5f));
        for (int k = 0; k < bgPrevs.size(); k++) bgPrevs.get(k).invalidate();
    }

    // =====================================================================
    // MENU
    // =====================================================================
    private void initMenu() {
        // title
        String title = "Slither Arena";
        int[] tc = {0xFFffd86b, 0xFFd9f56b, 0xFF8cf26b, 0xFF5be07a, 0xFF4fe0a8, 0xFF6be0ff, 0xFFb08cff, 0xFFFFFFFF,
                0xFFff6bd6, 0xFFff6bd6, 0xFFff6bd6, 0xFFff6bd6, 0xFFff6bd6};
        SpannableStringBuilder sb = new SpannableStringBuilder(title);
        for (int i = 0; i < title.length(); i++)
            sb.setSpan(new ForegroundColorSpan(tc[i]), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ((TextView) findViewById(R.id.tv_title)).setText(sb);

        preview = new PreviewView(this);
        ((FrameLayout) findViewById(R.id.preview_holder)).addView(preview,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pages[0] = findViewById(R.id.t1); pages[1] = findViewById(R.id.t2);
        pages[2] = findViewById(R.id.t3); pages[3] = findViewById(R.id.t4);

        // classic skins: rows of 4
        LinearLayout skinsBox = (LinearLayout) findViewById(R.id.skins_box);
        LinearLayout rw = null;
        for (int i = 0; i < SKINS.length; i++) {
            if (i % 4 == 0) { rw = row(); skinsBox.addView(rw); }
            final int idx = i;
            SwatchView sv = new SwatchView(this, SKINS[i]);
            sv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { choose(SKINS[idx], 0, idx); playSfx(sEat, .4f, .7f); }
            });
            rw.addView(sv, new LinearLayout.LayoutParams(0, dp(42), 1f));
            ((LinearLayout.LayoutParams) sv.getLayoutParams()).setMargins(dp(3), dp(3), dp(3), dp(3));
            skinSw[i] = sv;
        }
        // flags: rows of 3
        LinearLayout flagsBox = (LinearLayout) findViewById(R.id.flags_box);
        for (int i = 0; i < FLAGS.length; i++) {
            if (i % 3 == 0) { rw = row(); flagsBox.addView(rw); }
            final int idx = i;
            LinearLayout fb = new LinearLayout(this);
            fb.setOrientation(LinearLayout.VERTICAL);
            fb.setGravity(Gravity.CENTER_HORIZONTAL);
            fb.setPadding(dp(6), dp(8), dp(6), dp(8));
            SwatchView sv = new SwatchView(this, flagSkin(i));
            sv.flat = true;
            fb.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
            TextView tn = new TextView(this);
            tn.setText(flagLabel(i)); flagTxt[i] = tn; tn.setTextSize(11); tn.setTypeface(Typeface.DEFAULT_BOLD);
            tn.setTextColor(0xFFdfe4ff); tn.setSingleLine(true); tn.setGravity(Gravity.CENTER);
            tn.setPadding(0, dp(3), 0, 0);
            fb.addView(tn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            fb.setClickable(true);
            fb.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (flagLocked(idx)) { askUnlock(false, idx); return; }
                    choose(flagSkin(idx), 1, idx); playSfx(sEat, .4f, .7f);
                }
            });
            rw.addView(fb, cellLp(1f));
            flagBtn[i] = fb;
        }
        // custom
        custBox = (LinearLayout) findViewById(R.id.cust);
        c1Chip = findViewById(R.id.c1_chip);
        c2Chip = findViewById(R.id.c2_chip);
        SeekBar s1 = (SeekBar) findViewById(R.id.c1_seek), s2 = (SeekBar) findViewById(R.id.c2_seek);
        s1.setProgress(326); s2.setProgress(190);
        SeekBar.OnSeekBarChangeListener cl = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb2, int pr, boolean user) { if (user) applyCustom(); }
            @Override public void onStartTrackingTouch(SeekBar sb2) { }
            @Override public void onStopTrackingTouch(SeekBar sb2) { }
        };
        s1.setOnSeekBarChangeListener(cl); s2.setOnSeekBarChangeListener(cl);
        custom.c = new int[]{hsl(326f / 360f, 1f, .6f), hsl(190f / 360f, 1f, .6f)};
        setBg(c1Chip, rr(custom.c[0], 6, 0xFFFFFFFF, 2));
        setBg(c2Chip, rr(custom.c[1], 6, 0xFFFFFFFF, 2));
        LinearLayout pts = (LinearLayout) findViewById(R.id.pts);
        String[] pn = {"Solid", "Stripes", "Fade", "Dots"};
        final int[] pv = {P_SOLID, P_STRIPE, P_FADE, P_DOTS};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView ch = chip(pn[i], pv[i] == custom.p);
            ch.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    custom.p = pv[idx];
                    for (int k = 0; k < 4; k++) styleChip(patChips[k], k == idx);
                    applyCustom();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            pts.addView(ch, lp);
            patChips[i] = ch;
        }
        // hats: rows of 3
        LinearLayout hatsBox = (LinearLayout) findViewById(R.id.hats_box);
        for (int i = 0; i < HATS.length; i++) {
            if (i % 3 == 0) { rw = row(); hatsBox.addView(rw); }
            final int idx = i;
            TextView ch = chip(hatLabel(i), i == curHat);
            HatThumb ht = new HatThumb(this, i);
            ht.setBounds(0, 0, dp(52), dp(52));
            ch.setCompoundDrawables(null, ht, null, null);
            ch.setCompoundDrawablePadding(dp(2));
            ch.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (hatLocked(idx)) { askUnlock(true, idx); return; }
                    curHat = idx;
                    prefs.edit().putInt("hat", idx).apply();
                    for (int k = 0; k < hatChips.length; k++) styleChip(hatChips[k], k == idx);
                    preview.invalidate();
                    playSfx(sEat, .4f, .7f);
                }
            });
            rw.addView(ch, cellLp(1f));
            hatChips[i] = ch;
        }
        // backgrounds: rows of 6
        LinearLayout bgsBox = (LinearLayout) findViewById(R.id.bgs_box);
        for (int i = 0; i < BGS.length; i++) {
            if (i % 6 == 0) { rw = row(); bgsBox.addView(rw); }
            final int idx = i;
            View d = new View(this);
            d.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { applyBG(idx); playSfx(sEat, .4f, .7f); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            lp.setMargins(dp(3), dp(4), dp(6), dp(4));
            rw.addView(d, lp);
            bgDots[i] = d;
        }
        applyBG(bgIdx);
        // sliders
        SeekBar sbBots = (SeekBar) findViewById(R.id.seek_bots);
        sbBots.setProgress(botN);
        tvBn.setText("BOTS PLAYING: " + botN);
        sbBots.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int pr, boolean user) {
                synchronized (lock) { botN = pr; applyBots(); }
                tvBn.setText("BOTS PLAYING: " + pr);
                prefs.edit().putInt("bots", pr).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        SeekBar sbZoom = (SeekBar) findViewById(R.id.seek_zoom);
        sbZoomRef = sbZoom;
        sbZoom.setMax(38);
        sbZoom.setProgress((int) camDist - 12);
        tvZn.setText("CAMERA DISTANCE: " + (int) camDist);
        sbZoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int pr, boolean user) {
                synchronized (lock) { camDist = 12 + pr; ppuT = Math.min(W, H) / camDist; }
                tvZn.setText("CAMERA DISTANCE: " + (12 + pr));
                prefs.edit().putInt("zoom", 12 + pr).apply();
                for (int k = 0; k < bgPrevs.size(); k++) bgPrevs.get(k).invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        // live background previews (backgrounds list + next to the zoom slider)
        BgPreview bp1 = new BgPreview(this);
        LinearLayout.LayoutParams bl1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl1.bottomMargin = dp(6);
        bgsBox.addView(bp1, 0, bl1);
        bgPrevs.add(bp1);
        ViewGroup zp = (ViewGroup) sbZoom.getParent();
        // ---- background-zoom-only slider (does not touch camera zoom) ----
        final TextView tvBz = new TextView(this);
        tvBz.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, tvZn.getTextSize());
        tvBz.setTypeface(tvZn.getTypeface());
        tvBz.setTextColor(tvZn.getTextColors());
        tvBz.setPadding(tvZn.getPaddingLeft(), dp(12), tvZn.getPaddingRight(), tvZn.getPaddingBottom());
        tvBz.setText("BACKGROUND ZOOM ONLY: " + Math.round(bgZoom * 100f) + "%");
        SeekBar sbBz = new SeekBar(this);
        sbBz.setMax(150);
        sbBz.setProgress(Math.round(bgZoom * 100f) - 50);
        sbBz.setPadding(sbZoom.getPaddingLeft(), sbZoom.getPaddingTop(), sbZoom.getPaddingRight(), sbZoom.getPaddingBottom());
        sbBz.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int pr, boolean user) {
                bgZoom = (50 + pr) / 100f;
                tvBz.setText("BACKGROUND ZOOM ONLY: " + (50 + pr) + "%");
                prefs.edit().putInt("bgz", 50 + pr).apply();
                for (int k = 0; k < bgPrevs.size(); k++) bgPrevs.get(k).invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        LinearLayout.LayoutParams lbz = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        LinearLayout.LayoutParams lsz = sbZoom.getLayoutParams() instanceof LinearLayout.LayoutParams
                ? new LinearLayout.LayoutParams((LinearLayout.LayoutParams) sbZoom.getLayoutParams())
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (zp instanceof LinearLayout) {
            int at = zp.indexOfChild(sbZoom) + 1;
            zp.addView(tvBz, at, lbz);
            zp.addView(sbBz, at + 1, lsz);
        } else {
            bgsBox.addView(tvBz, lbz);
            bgsBox.addView(sbBz, lsz);
        }
        if (zp instanceof LinearLayout && zp != bgsBox) {
            BgPreview bp2 = new BgPreview(this);
            LinearLayout.LayoutParams bl2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl2.topMargin = dp(8); bl2.bottomMargin = dp(4);
            zp.addView(bp2, zp.indexOfChild(sbBz) + 1, bl2);
            bgPrevs.add(bp2);
        }
        choose(SKINS[0], 0, 0);
        showMenuStep(0);
        refreshMenu();
    }

    private void applyCustom() {
        SeekBar s1 = (SeekBar) findViewById(R.id.c1_seek), s2 = (SeekBar) findViewById(R.id.c2_seek);
        custom.c = new int[]{hsl(s1.getProgress() / 360f, 1f, .6f), hsl(s2.getProgress() / 360f, 1f, .6f)};
        setBg(c1Chip, rr(custom.c[0], 6, 0xFFFFFFFF, 2));
        setBg(c2Chip, rr(custom.c[1], 6, 0xFFFFFFFF, 2));
        choose(custom, 2, 0);
    }

    private void choose(Skin sk, int kind, int index) {
        curSkin = sk;
        skinKind = kind; skinIdx = index;
        for (int i = 0; i < skinSw.length; i++) { skinSw[i].sel = (kind == 0 && i == index); skinSw[i].invalidate(); }
        for (int i = 0; i < flagBtn.length; i++)
            setBg(flagBtn[i], rr((kind == 1 && i == index) ? 0x33FFFFFF : 0x12FFFFFF, 12, (kind == 1 && i == index) ? 0xFFFFFFFF : 0, 2));
        setBg(custBox, rr(kind == 2 ? 0x14FFFFFF : 0x00000000, 14, kind == 2 ? 0xFFFFFFFF : 0x24FFFFFF, 1.5f));
        preview.setSkin(sk);
        for (int i = 0; i < hatChips.length; i++) if (hatChips[i] != null) hatChips[i].invalidate();
        if (finalPreview != null) finalPreview.setSkin(sk);
    }

    private void showMenuStep(int step) {
        menuStep = Math.max(0, Math.min(3, step));
        for (int i = 0; i < 4; i++) pages[i].setVisibility(i == menuStep ? View.VISIBLE : View.GONE);
        ((View) findViewById(R.id.menu_scroll)).scrollTo(0, 0);
        boolean last = menuStep == 3;
        btnNext.setVisibility(last ? View.GONE : View.VISIBLE);
        btnPlay.setVisibility(last ? View.VISIBLE : View.GONE);
        if (btnPrev != null) btnPrev.setVisibility(menuStep > 0 ? View.VISIBLE : View.GONE);
        if (btnGiant != null) btnGiant.setVisibility(last ? View.VISIBLE : View.GONE);
        refreshMenu();
    }

    // ---- menu: Previous button (goes back one step) ----
    private void buildPrevBtn() {
        btnPrev = bigBtn("Previous", 0xFF5a64b8, 0xFF2a2f78, 17);
        ViewGroup par = (ViewGroup) btnPlay.getParent();
        View anchor = (btnGiant != null && btnGiant.getParent() == par) ? btnGiant : btnPlay;
        if (par instanceof LinearLayout && ((LinearLayout) par).getOrientation() == LinearLayout.VERTICAL) {
            par.addView(btnPrev, par.indexOfChild(anchor) + 1, adLp(false));
        } else {
            LinearLayout box = (LinearLayout) findViewById(R.id.linear11);
            box.addView(btnPrev, Math.max(0, box.indexOfChild(btnExit)), adLp(false));
        }
        btnPrev.setVisibility(menuStep > 0 ? View.VISIBLE : View.GONE);
        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenuStep(menuStep - 1); playSfx(sEat, .5f, .7f); }
        });
    }

    private void refreshMenu() {
        boolean c = canResume();
        btnResume.setVisibility(c ? View.VISIBLE : View.GONE);
        btnPlay.setText(c ? "New game" : "Play");
        if (btnBackMenu != null) btnBackMenu.setVisibility(state == ST_PAUSED ? View.VISIBLE : View.GONE);
        updateBurger();
    }

    // =====================================================================
    // SKIN PICKER  (full-screen scrolling list of animated snakes, selected one is bigger)
    // =====================================================================
    private SkinPicker picker;
    private int skinKind = 0, skinIdx = 0, hatCursor = 0;

    private void openPicker() {
        if (picker == null) {
            picker = new SkinPicker();
            addContentView(picker, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (Build.VERSION.SDK_INT >= 21) picker.setElevation(dp(30));
        }
        picker.reset();
        picker.setVisibility(View.VISIBLE);
        picker.invalidate();
        updateBurger();
    }

    private void closePicker() {
        if (picker != null) picker.setVisibility(View.GONE);
        updateBurger();
    }

    private void selectHatIdx(int idx) {
        curHat = idx;
        hatCursor = idx;
        prefs.edit().putInt("hat", idx).apply();
        for (int k = 0; k < hatChips.length; k++) styleChip(hatChips[k], k == idx);
        preview.invalidate();
        playSfx(sEat, .4f, .7f);
    }

    private void cycleHat() {
        int base = hatLocked(hatCursor) ? hatCursor : curHat;
        int next = (base + 1) % HATS.length;
        hatCursor = next;
        if (hatLocked(next)) { askUnlock(true, next); return; }
        selectHatIdx(next);
    }

    private class SkinPicker extends View {
        final int total = SKINS.length + FLAGS.length + 1;
        final SkinArt art = new SkinArt();
        final Skin[] sks = new Skin[total];
        final float[] grow = new float[total];
        static final int N = 44;
        final float[] xs = new float[N], ys = new float[N];
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ps = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        final Paint pbg = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Paint pth = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Matrix m = new Matrix(), m2 = new Matrix();
        final Path hp = new Path(), clipP = new Path();
        final RectF tmp = new RectF(), rBg = new RectF(), rOk = new RectF(), hBg = new RectF(), hCos = new RectF(), hBuild = new RectF(), hOk = new RectF(), hNext = new RectF(), hResume = new RectF();
        BitmapShader sh, sh2;
        int shFor = -1, sh2For = -1;
        float scroll, target, vel, downX, downY, startScroll, lastY;
        long lastT, lastDraw;
        boolean drag;
        final int slop;
        final float hN, hS, padTop, padBottom;

        SkinPicker() {
            super(MainActivity.this);
            for (int i = 0; i < SKINS.length; i++) sks[i] = SKINS[i];
            for (int i = 0; i < FLAGS.length; i++) sks[SKINS.length + i] = flagSkin(i);
            sks[total - 1] = custom;
            slop = dp(8);
            hN = dp(86); hS = dp(132); padTop = dp(120); padBottom = dp(116);
            setClickable(true);
        }

        @Override protected void onSizeChanged(int nw, int nh, int ow, int oh) { super.onSizeChanged(nw, nh, ow, oh); reset(); }

        void txt(Canvas c, String s, float x, float y, float sz, int col) {
            pt.setTextAlign(Paint.Align.CENTER); pt.setTypeface(Typeface.DEFAULT_BOLD);
            pt.setStyle(Paint.Style.FILL); pt.setTextSize(sz); pt.setColor(col);
            c.drawText(s, x, y, pt);
        }

        int sel() { return skinKind == 0 ? skinIdx : (skinKind == 1 ? SKINS.length + skinIdx : total - 1); }
        float hOf(int i) { return hN + (hS - hN) * grow[i]; }
        float contentH() { float s = padTop + padBottom; for (int i = 0; i < total; i++) s += hOf(i); return s; }
        float maxScroll() { return Math.max(0f, contentH() - (getHeight() > 0 ? getHeight() : H)); }
        void centerOn(int i, boolean snap) {
            int vh = getHeight() > 0 ? getHeight() : H;
            float fin = padTop + (total - 1) * hN + hS + padBottom;
            float tg = clamp(padTop + i * hN + hS / 2f - vh / 2f, 0f, Math.max(0f, fin - vh));
            target = tg;
            if (snap) scroll = tg;
            vel = 0;
        }
        void reset() {
            int s = sel();
            for (int i = 0; i < total; i++) grow[i] = i == s ? 1f : 0f;
            centerOn(s, true);
            lastDraw = System.nanoTime();
        }

        void label(Canvas c, String s, float x, float y, float sz) {
            pt.setTextAlign(Paint.Align.CENTER);
            pt.setTextSize(sz);
            pt.setTypeface(Typeface.DEFAULT_BOLD);
            pt.setStyle(Paint.Style.STROKE); pt.setStrokeWidth(dp(3)); pt.setStrokeJoin(Paint.Join.ROUND);
            pt.setColor(0xFF1b3d12);
            c.drawText(s, x, y, pt);
            pt.setStyle(Paint.Style.FILL); pt.setColor(0xFF9bff5a);
            c.drawText(s, x, y, pt);
        }

        @Override
        protected void onDraw(Canvas c) {
            long now = System.nanoTime();
            float dt = Math.min(.05f, (now - lastDraw) / 1e9f);
            lastDraw = now;
            float t = (now - startNs) / 1e9f;
            int w = getWidth(), h = getHeight();
            int sel = sel();
            for (int i = 0; i < total; i++) grow[i] += ((i == sel ? 1f : 0f) - grow[i]) * (1f - expf(-dt * 10f));
            float ms = maxScroll();
            if (!drag) {
                if (Math.abs(vel) > 25f) { scroll += vel * dt; vel *= expf(-dt * 3.5f); target = scroll; }
                else { vel = 0; scroll += (target - scroll) * (1f - expf(-dt * 9f)); }
            }
            scroll = clamp(scroll, 0f, ms);

            // ---- backdrop = chosen background ----
            Bg bg = BGS[bgIdx];
            c.drawColor(bg.color);
            Bitmap tl = bg.tile;
            if (tl != null) {
                if (sh == null || shFor != bgIdx) {
                    sh = new BitmapShader(tl, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                    shFor = bgIdx; pbg.setShader(sh);
                }
                float u = w / camDist * bgZoom;
                m.setScale(bg.w * u / tl.getWidth(), bg.h * u / tl.getHeight());
                m.postTranslate(w / 2f, h / 2f - scroll * .3f);
                sh.setLocalMatrix(m);
                c.drawRect(0, 0, w, h, pbg);
            }

            // ---- snake list ----
            float y = padTop - scroll;
            Hat hatSel = HATS[curHat];
            for (int i = 0; i < total; i++) {
                float hi = hOf(i), g = grow[i];
                if (y + hi > -dp(30) && y < h + dp(30)) {
                    float R = dp(9.5f) + dp(11.5f) * g;
                    float x1 = w * (.72f + .21f * g) - R, x0 = w * (.29f - .225f * g) + R;
                    float amp = dp(9) + dp(8) * g;
                    boolean locked = i >= SKINS.length && i < SKINS.length + FLAGS.length && flagLocked(i - SKINS.length);
                    art.snake(c, sks[i], x0, x1, y + hi / 2f, R, amp, 5.4f, t, i * .9f, dp(1.4f), false, g > .5f ? hatSel : null);   // original colours, even when locked
                    if (i >= SKINS.length && i < SKINS.length + FLAGS.length)
                        label(c, FLAGS[i - SKINS.length].n, (x0 + x1) / 2f, y + hi - dp(8), dp(13));   // country name
                    if (locked) {
                        pt.setTextAlign(Paint.Align.CENTER); pt.setStyle(Paint.Style.FILL); pt.setTextSize(dp(18));
                        pt.setColor(0xFFFFFFFF);
                        c.drawText("\uD83D\uDD12", x0 - dp(22), y + hi / 2f + dp(6), pt);
                    }
                }
                y += hi;
            }

            // ---- fixed overlays: top bar + bottom bar ----
            float topH = dp(112), botH = dp(104);
            p.setStyle(Paint.Style.FILL); p.setColor(0xE60b0d26);
            c.drawRect(0, 0, w, topH, p);
            c.drawRect(0, h - botH, w, h, p);
            p.setColor(0x33FFFFFF);
            c.drawRect(0, topH - dp(1), w, topH, p);
            c.drawRect(0, h - botH, w, h - botH + dp(1), p);

            // title (between the menu button and the Cosmetics button)
            float tcx = (dp(64) + w - dp(116)) / 2f;
            txt(c, "STEP 1 OF 3", tcx, dp(44), dp(11), 0xFF9bb0ff);
            txt(c, "Pick Your Snake", tcx, dp(72), dp(20), 0xFFFFFFFF);
            txt(c, "Tap a snake to choose it", tcx, dp(92), dp(11), 0xFFd8dcff);

            // Cosmetics button (top-right)
            hCos.set(w - dp(106), dp(14), w - dp(10), dp(100));
            p.setStyle(Paint.Style.FILL); p.setColor(0xFF1f3aa8);
            c.drawRoundRect(hCos, dp(14), dp(14), p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2.5f)); p.setColor(0xFF8a5bff);
            c.drawRoundRect(hCos, dp(14), dp(14), p);
            art.head(c, hCos.centerX(), hCos.top + dp(38), dp(17), -.35f, skinColor(curSkin, 0) | 0xFF000000, dp(1.4f), curHat == 0 ? null : hatSel, t, 0f);
            txt(c, "Cosmetics", hCos.centerX(), hCos.bottom - dp(12), dp(13), 0xFFFFFFFF);

            // Build your own snake (bottom-left)
            float by0 = h - botH;
            hBuild.set(dp(12), by0 + dp(14), dp(122), h - dp(14));
            p.setStyle(Paint.Style.FILL); p.setColor(0xFF1f2a6e);
            c.drawRoundRect(hBuild, dp(14), dp(14), p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setColor(0x558a5bff);
            c.drawRoundRect(hBuild, dp(14), dp(14), p);
            art.head(c, hBuild.centerX(), hBuild.top + dp(28), dp(15), -.35f, skinColor(custom, 0) | 0xFF000000, dp(1.4f), null, t, 1.5f);
            txt(c, "Build Your", hBuild.centerX(), hBuild.bottom - dp(24), dp(12), 0xFFFFFFFF);
            txt(c, "Own Snake", hBuild.centerX(), hBuild.bottom - dp(10), dp(12), 0xFFFFFFFF);

            // NEXT button (bottom-right, big)
            hNext.set(dp(134), by0 + dp(18), w - dp(12), h - dp(18));
            p.setStyle(Paint.Style.FILL); p.setColor(0xFF117a33);
            c.drawRoundRect(hNext, dp(22), dp(22), p);
            tmp.set(hNext.left, hNext.top, hNext.right, hNext.bottom - dp(4));
            p.setColor(0xFF3fcf63);
            c.drawRoundRect(tmp, dp(22), dp(22), p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.5f)); p.setColor(0x88FFFFFF);
            c.drawRoundRect(tmp, dp(22), dp(22), p);
            txt(c, "NEXT  ▶", tmp.centerX(), tmp.centerY() + dp(9), dp(26), 0xFFFFFFFF);

            // resume a saved game (only if there is one)
            if (state == ST_MENU && prefs.contains("save")) {
                hResume.set(w / 2f - dp(120), topH + dp(8), w / 2f + dp(120), topH + dp(48));
                p.setStyle(Paint.Style.FILL); p.setColor(0xFF1b9a45);
                c.drawRoundRect(hResume, dp(20), dp(20), p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setColor(0xFFFFFFFF);
                c.drawRoundRect(hResume, dp(20), dp(20), p);
                txt(c, "▶  Resume saved game", hResume.centerX(), hResume.centerY() + dp(6), dp(15), 0xFFFFFFFF);
            } else hResume.setEmpty();

            if (getVisibility() == View.VISIBLE) {
                if (Build.VERSION.SDK_INT >= 16) postInvalidateOnAnimation(); else postInvalidateDelayed(16);
            }
        }

        void tap(float x, float y) {
            if (hNext.contains(x, y)) { playSfx(sEat, .5f, .8f); goScreen(SC_BG); return; }
            if (hCos.contains(x, y)) { playSfx(sEat, .5f, .8f); goScreen(SC_HATS); return; }
            if (hBuild.contains(x, y)) { choose(custom, 2, 0); playSfx(sEat, .5f, .8f); goScreen(SC_BUILD); return; }
            if (hResume.contains(x, y)) { resumeGame(); return; }
            if (y < dp(112) || y > getHeight() - dp(104)) return;
            float yy = padTop - scroll;
            for (int i = 0; i < total; i++) {
                float hi = hOf(i);
                if (y >= yy && y < yy + hi) {
                    if (i < SKINS.length) choose(SKINS[i], 0, i);
                    else if (i < SKINS.length + FLAGS.length) {
                        int fi = i - SKINS.length;
                        if (flagLocked(fi)) { askUnlock(false, fi); return; }
                        choose(flagSkin(fi), 1, fi);
                    } else choose(custom, 2, 0);
                    playSfx(sEat, .4f, .7f);
                    centerOn(i, false);
                    return;
                }
                yy += hi;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX(); downY = e.getY(); startScroll = scroll; drag = false; vel = 0;
                    lastY = downY; lastT = e.getEventTime();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dy = e.getY() - downY;
                    if (!drag && Math.abs(dy) > slop) drag = true;
                    if (drag) {
                        scroll = clamp(startScroll - dy, 0f, maxScroll());
                        target = scroll;
                        long tt = e.getEventTime();
                        float dtt = (tt - lastT) / 1000f;
                        if (dtt > 0f) vel = vel * .5f + (-(e.getY() - lastY) / dtt) * .5f;
                        lastY = e.getY(); lastT = tt;
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (!drag) tap(e.getX(), e.getY());
                    else if (e.getEventTime() - lastT > 90) vel = 0;
                    drag = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    drag = false; vel = 0;
                    return true;
            }
            return true;
        }
    }

    // =====================================================================
    // CUSTOM VIEWS (menu only)
    // =====================================================================
    private static class SwatchView extends View {
        Skin sk;
        boolean sel, flat;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path clip = new Path();
        final RectF r = new RectF();

        SwatchView(android.content.Context ctx, Skin s) { super(ctx); sk = s; setClickable(true); }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = h / 2f;
            r.set(0, 0, w, h);
            clip.reset();
            clip.addRoundRect(r, rad, rad, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            p.setStyle(Paint.Style.FILL);
            if (sk.p == P_FADE) {
                p.setShader(new LinearGradient(0, 0, w, 0, new int[]{sk.c[0], sk.c[1 % sk.c.length], sk.c[0]}, null, Shader.TileMode.CLAMP));
                c.drawRect(r, p);
                p.setShader(null);
            } else {
                int n = 12;
                for (int k = 0; k < n; k++) {
                    int col;
                    if (flat) col = sk.c[Math.min(sk.c.length - 1, k * sk.c.length / n)];
                    else col = skinColor(sk, (int) (k * 2.5f));
                    p.setColor(col | 0xFF000000);
                    c.drawRect(w * k / n, 0, w * (k + 1) / n + 1, h, p);
                }
            }
            p.setColor(0x33000000);
            c.drawRect(0, h * .72f, w, h, p);
            p.setColor(0x44FFFFFF);
            c.drawRect(0, 0, w, h * .22f, p);
            c.restore();
            if (sel) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5); p.setColor(0xFFFFFFFF);
                r.set(2.5f, 2.5f, w - 2.5f, h - 2.5f);
                c.drawRoundRect(r, rad, rad, p);
            }
        }
    }

    private static class PreviewView extends View {
        Skin sk = SKINS[0];
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ps = new Paint(Paint.FILTER_BITMAP_FLAG);
        final RectF r = new RectF();
        final Path cp = new Path();
        final SkinArt art = new SkinArt();
        final MainActivity act;

        PreviewView(MainActivity a) { super(a); act = a; }
        void setSkin(Skin s) { sk = s; invalidate(); }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = View.MeasureSpec.getSize(wSpec);
            setMeasuredDimension(w, w / 3);
        }

        @Override
        protected void onDraw(Canvas c) {
            float t = (System.nanoTime() - act.startNs) / 1e9f;
            float sc = getWidth() / 600f;
            c.scale(sc, sc);
            art.snake(c, sk, 95f, 520f, 108f + sinf(t * 1.7f) * 4f, 32f, 22f, 8.5f, t, 0f, 3f, false, HATS[act.curHat]);
            if (Build.VERSION.SDK_INT >= 16) postInvalidateOnAnimation(); else postInvalidateDelayed(16);
        }
    }

    // =====================================================================
    // SOUND (tiny synthesized WAVs played through SoundPool)
    // =====================================================================
    private static final int SR = 22050;

    private void initSound() {
        try {
            sp = new SoundPool(8, AudioManager.STREAM_MUSIC, 0);
            float[] b = new float[(int) (SR * .12f)];
            tone(b, 0f, 700f, 2200f, .07f, .5f, 0);
            tone(b, .015f, 1050f, 1050f, .05f, .25f, 1);
            sEat = loadWav("eat", b);
            b = new float[(int) (SR * .65f)];
            float[] nt = {392f, 523f, 659f, 784f, 988f};
            for (int i = 0; i < nt.length; i++) tone(b, i * .06f, nt[i], nt[i], .28f, .3f, 1);
            tone(b, .32f, 1175f, 1175f, .2f, .2f, 0);
            sStartId = loadWav("start", b);
            b = new float[(int) (SR * .85f)];
            tone(b, 0f, 420f, 180f, .15f, .35f, 2);
            tone(b, .08f, 280f, 40f, .7f, .4f, 2);
            noise(b, 0f, .65f, .5f);
            sDie = loadWav("die", b);
            b = new float[(int) (SR * .4f)];
            tone(b, 0f, 740f, 1100f, .12f, .22f, 3);
            tone(b, .08f, 1100f, 1100f, .2f, .22f, 1);
            tone(b, .14f, 1480f, 1480f, .15f, .2f, 0);
            sKill = loadWav("kill", b);
            b = new float[(int) (SR * .9f)];
            ghost(b);
            sGhost = loadWav("ghost", b);
        } catch (Exception e) { sp = null; }
    }

    private static void tone(float[] buf, float t0, float f0, float f1, float dur, float vol, int type) {
        int s0 = (int) (t0 * SR), n = (int) (dur * SR);
        double ph = 0;
        for (int i = 0; i < n && s0 + i < buf.length; i++) {
            float u = (float) i / n;
            float f = f0 * (float) Math.pow(f1 / f0, u);
            ph += 2 * Math.PI * f / SR;
            float env = (float) Math.pow(.0001, u) * Math.min(1f, i / (.008f * SR));
            double x;
            double fr2 = (ph / (2 * Math.PI)) % 1.0;
            if (type == 0) x = Math.sin(ph);
            else if (type == 1) x = 4 * Math.abs(fr2 - .5) - 1;
            else if (type == 2) x = 2 * fr2 - 1;
            else x = fr2 < .5 ? 1 : -1;
            buf[s0 + i] += (float) x * env * vol;
        }
    }

    /** eerie ghost "wooOOoo": wavering falling moan + airy breath */
    private static void ghost(float[] buf) {
        double ph = 0, ph2 = 0;
        float y = 0;
        for (int i = 0; i < buf.length; i++) {
            float t = (float) i / SR, u = (float) i / buf.length;
            float f = (620f - 300f * u) + 45f * (float) Math.sin(2 * Math.PI * 6.5 * t);
            ph += 2 * Math.PI * f / SR;
            ph2 += 2 * Math.PI * (f * 1.5f) / SR;
            float env = (float) Math.sin(Math.PI * Math.pow(u, .6)) * (1f - .3f * u);
            float trem = .75f + .25f * (float) Math.sin(2 * Math.PI * 9 * t);
            y += ((RND.nextFloat() * 2f - 1f) - y) * .12f;
            buf[i] += (float) (Math.sin(ph) * .34 + Math.sin(ph2) * .1) * env * trem + y * env * .22f;
        }
    }

    private static void noise(float[] buf, float t0, float dur, float vol) {
        int s0 = (int) (t0 * SR), n = (int) (dur * SR);
        float y = 0;
        for (int i = 0; i < n && s0 + i < buf.length; i++) {
            float u = (float) i / n;
            float a = .5f - .46f * u;
            y += ((RND.nextFloat() * 2f - 1f) - y) * a;
            buf[s0 + i] += y * (float) Math.pow(.0001, u) * vol * 2f;
        }
    }

    private int loadWav(String name, float[] pcm) throws IOException {
        int n = pcm.length, dl = n * 2;
        byte[] o = new byte[44 + dl];
        putStr(o, 0, "RIFF"); putInt(o, 4, 36 + dl); putStr(o, 8, "WAVE"); putStr(o, 12, "fmt ");
        putInt(o, 16, 16); putShort(o, 20, 1); putShort(o, 22, 1); putInt(o, 24, SR); putInt(o, 28, SR * 2);
        putShort(o, 32, 2); putShort(o, 34, 16); putStr(o, 36, "data"); putInt(o, 40, dl);
        for (int i = 0; i < n; i++) {
            short v = (short) (clamp(pcm[i], -1f, 1f) * 32767f);
            o[44 + 2 * i] = (byte) (v & 0xff);
            o[45 + 2 * i] = (byte) ((v >> 8) & 0xff);
        }
        File f = new File(getCacheDir(), name + ".wav");
        FileOutputStream fo = new FileOutputStream(f);
        fo.write(o);
        fo.close();
        return sp.load(f.getAbsolutePath(), 1);
    }
    private static void putStr(byte[] b, int off, String s) { for (int i = 0; i < s.length(); i++) b[off + i] = (byte) s.charAt(i); }
    private static void putInt(byte[] b, int off, int v) { b[off] = (byte) v; b[off + 1] = (byte) (v >> 8); b[off + 2] = (byte) (v >> 16); b[off + 3] = (byte) (v >> 24); }
    private static void putShort(byte[] b, int off, int v) { b[off] = (byte) v; b[off + 1] = (byte) (v >> 8); }

    private void playSfx(int id, float vol, float rate) {
        if (sp == null || muted || id == 0) return;
        try { sp.play(id, vol, vol, 1, 0, rate); } catch (Exception e) { }
    }
}

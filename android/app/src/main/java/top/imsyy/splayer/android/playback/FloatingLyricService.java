package top.imsyy.splayer.android.playback;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class FloatingLyricService extends Service {
  private static final String TAG = "FloatingLyric";
  private static final String PREFS = "floating_lyric_prefs";
  private static final int DRAG_SLOP = 10;

  private WindowManager wm;
  private LyricView view;
  private WindowManager.LayoutParams lp;
  /** 锁定时的独立解锁按钮小窗口 */
  private View unlockBtnView;
  private WindowManager.LayoutParams unlockLp;
  private SharedPreferences prefs;
  private final Handler handler = new Handler(Looper.getMainLooper());

  /* ---------- 歌词数据 ---------- */
  volatile List<Line> lrcLines = new ArrayList<>();
  volatile List<Line> yrcLines = new ArrayList<>();
  volatile String songName = "", artistName = "";

  /* ---------- 时间插值 ---------- */
  volatile long baseMs = 0;
  volatile long anchorNano = System.nanoTime();
  volatile boolean playing = false;

  /* ---------- 配置 ---------- */
  int colorPlayed = 0xFFFE7971, colorUnplayed = 0xFFCCCCCC, colorShadow = 0x80000000;
  float fontSizeSp = 16f;
  boolean wordMode = true, locked = false, showCtrls = false;

  /* ---------- 拖拽 ---------- */
  private float tX0, tY0;
  private int wX0, wY0;
  private boolean dragging;

  /* ---------- 控制栏按钮命中区 ---------- */
  private final RectF rLock = new RectF(), rPrev = new RectF(), rPlay = new RectF(),
      rNext = new RectF(), rClose = new RectF(), rUnlock = new RectF();

  // ==================== 生命周期 ====================

  @Override public void onCreate() {
    super.onCreate();
    prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
    locked = prefs.getBoolean("locked", false);
    wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    buildView();
    // 如果上次是锁定状态，恢复穿透 + 显示解锁按钮
    if (locked) {
      lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
      try { wm.updateViewLayout(view, lp); } catch (Exception ignored) {}
      showUnlockBtn();
    }
    PlaybackManager.getInstance(this).attachFloatingLyricService(this);
  }

  @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }

  @Override public void onDestroy() {
    PlaybackManager.getInstance(this).detachFloatingLyricService(this);
    removeUnlockBtn();
    if (view != null) { try { wm.removeView(view); } catch (Exception ignored) {} view = null; }
    super.onDestroy();
  }

  @Nullable @Override public IBinder onBind(Intent i) { return null; }

  // ==================== View 构建 ====================

  private void buildView() {
    view = new LyricView(this);
    DisplayMetrics dm = getResources().getDisplayMetrics();
    int w = (int)(dm.widthPixels * 0.92f), h = (int)(72 * dm.density);
    int x = prefs.getInt("x", (dm.widthPixels - w) / 2);
    int y = prefs.getInt("y", (int)(dm.heightPixels * 0.72f));

    lp = new WindowManager.LayoutParams(w, h,
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT);
    lp.gravity = Gravity.TOP | Gravity.START;
    lp.x = x; lp.y = y;
    try { wm.addView(view, lp); } catch (Exception e) { Log.e(TAG, "addView", e); }
  }

  // ==================== 对外 API ====================

  public void pushLyrics(String lrcJson, String yrcJson) {
    lrcLines = parseLines(lrcJson);
    yrcLines = parseLines(yrcJson);
    postRedraw();
  }

  public void pushProgress(long ms, boolean isPlaying) {
    baseMs = ms;
    anchorNano = System.nanoTime();
    playing = isPlaying;
    postRedraw();
  }

  public void pushSongInfo(String name, String artist) {
    songName = name != null ? name : "";
    artistName = artist != null ? artist : "";
    postRedraw();
  }

  public void setLocked(boolean v) {
    locked = v;
    prefs.edit().putBoolean("locked", v).apply();
    showCtrls = false;
    if (v) {
      // 锁定：主窗口穿透 + 显示解锁小按钮
      lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
      try { wm.updateViewLayout(view, lp); } catch (Exception ignored) {}
      showUnlockBtn();
    } else {
      // 解锁：主窗口恢复触摸 + 移除解锁按钮
      lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
      try { wm.updateViewLayout(view, lp); } catch (Exception ignored) {}
      removeUnlockBtn();
    }
    postRedraw();
  }

  /** 显示独立的解锁按钮小窗口 */
  private void showUnlockBtn() {
    if (unlockBtnView != null) return;
    DisplayMetrics dm = getResources().getDisplayMetrics();
    float d = dm.density;
    int btnSize = (int)(32 * d);

    unlockBtnView = new View(this) {
      final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
      final Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG);
      @Override protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        bp.setColor(0x80000000);
        c.drawRoundRect(0, 0, getWidth(), getHeight(), 8*d, 8*d, bp);
        ip.setColor(0xFFFFFFFF);
        ip.setTextAlign(Paint.Align.CENTER);
        ip.setTextSize(16*d);
        Paint.FontMetrics fm = ip.getFontMetrics();
        c.drawText("🔓", getWidth()/2f, getHeight()/2f-(fm.ascent+fm.descent)/2f, ip);
      }
    };
    unlockBtnView.setOnClickListener(v -> setLocked(false));

    int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        : WindowManager.LayoutParams.TYPE_PHONE;

    unlockLp = new WindowManager.LayoutParams(btnSize, btnSize, overlayType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT);
    unlockLp.gravity = Gravity.TOP | Gravity.START;
    // 放在主窗口右上角
    unlockLp.x = lp.x + lp.width - btnSize - (int)(4*d);
    unlockLp.y = lp.y + (int)(4*d);

    try { wm.addView(unlockBtnView, unlockLp); } catch (Exception e) { Log.e(TAG, "addUnlock", e); }
  }

  /** 移除解锁按钮窗口 */
  private void removeUnlockBtn() {
    if (unlockBtnView != null) {
      try { wm.removeView(unlockBtnView); } catch (Exception ignored) {}
      unlockBtnView = null;
    }
  }

  private void postRedraw() {
    if (view != null) view.postInvalidateOnAnimation();
  }

  // ==================== 时间计算 ====================

  long seekMs() {
    if (!playing) return baseMs + 300;
    long elapsed = (System.nanoTime() - anchorNano) / 1_000_000L;
    return baseMs + elapsed + 300;
  }

  List<Line> activeLines() {
    return (wordMode && !yrcLines.isEmpty()) ? yrcLines : lrcLines;
  }

  int findIndex(List<Line> ly, long ms) {
    int r = -1;
    for (int i = 0; i < ly.size(); i++) {
      if (ms >= ly.get(i).start) r = i; else break;
    }
    return r;
  }

  // ==================== 按钮处理 ====================

  private void onBtnTap(float x, float y) {
    if (rUnlock.contains(x, y)) { setLocked(false); return; }
    if (rClose.contains(x, y)) {
      PlaybackManager pm = PlaybackManager.getInstance(this);
      pm.hideFloatingLyric();
      pm.emitDesktopLyricClosed();
      return;
    }
    if (rLock.contains(x, y)) { setLocked(true); return; }
    if (rPrev.contains(x, y)) {
      PlaybackManager.getInstance(this).handleNotificationAction(PlaybackConstants.ACTION_PREVIOUS);
      return;
    }
    if (rPlay.contains(x, y)) {
      PlaybackManager.getInstance(this).handleNotificationAction(PlaybackConstants.ACTION_TOGGLE_PLAYBACK);
      return;
    }
    if (rNext.contains(x, y)) {
      PlaybackManager.getInstance(this).handleNotificationAction(PlaybackConstants.ACTION_NEXT);
    }
  }

  // ==================== 解析 ====================

  private static List<Line> parseLines(String json) {
    List<Line> r = new ArrayList<>();
    if (json == null || json.isEmpty()) return r;
    try {
      JSONArray a = new JSONArray(json);
      for (int i = 0; i < a.length(); i++) {
        JSONObject o = a.getJSONObject(i);
        Line l = new Line();
        l.start = o.optLong("startTime", 0);
        l.end = o.optLong("endTime", 0);
        l.tran = o.optString("translatedLyric", "");
        JSONArray wa = o.optJSONArray("words");
        if (wa != null) for (int j = 0; j < wa.length(); j++) {
          JSONObject wo = wa.getJSONObject(j);
          l.words.add(new Word(wo.optString("word", ""), wo.optLong("startTime", 0), wo.optLong("endTime", 0)));
        }
        r.add(l);
      }
    } catch (Exception e) { Log.w(TAG, "parse", e); }
    return r;
  }

  // ==================== 自定义 View ====================

  private class LyricView extends View {
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG);

    LyricView(Context c) { super(c); }

    @Override public boolean onTouchEvent(MotionEvent e) {
      // 锁定时主窗口 FLAG_NOT_TOUCHABLE，不会进入这里
      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN:
          tX0 = e.getRawX(); tY0 = e.getRawY();
          wX0 = lp.x; wY0 = lp.y; dragging = false;
          return true;
        case MotionEvent.ACTION_MOVE:
          float dx = e.getRawX() - tX0, dy = e.getRawY() - tY0;
          if (!dragging && (Math.abs(dx) > DRAG_SLOP || Math.abs(dy) > DRAG_SLOP)) dragging = true;
          if (dragging) {
            lp.x = wX0 + (int) dx; lp.y = wY0 + (int) dy;
            try { wm.updateViewLayout(view, lp); } catch (Exception ignored) {}
          }
          return true;
        case MotionEvent.ACTION_UP:
          if (dragging) {
            prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply();
          } else {
            if (showCtrls) onBtnTap(e.getX(), e.getY());
            showCtrls = !showCtrls;
            invalidate();
            handler.removeCallbacksAndMessages(null);
            if (showCtrls) handler.postDelayed(() -> { showCtrls = false; postRedraw(); }, 5000);
          }
          return true;
      }
      return super.onTouchEvent(e);
    }

    @Override protected void onDraw(Canvas c) {
      int w = getWidth(), h = getHeight();
      float d = getResources().getDisplayMetrics().density;
      float sd = getResources().getDisplayMetrics().scaledDensity;

      // 背景
      bp.setColor(showCtrls ? 0xDD1E1E2E : 0x661E1E2E);
      c.drawRoundRect(0, 0, w, h, 14*d, 14*d, bp);

      if (locked) {
        paintLyrics(c, w, h, d, sd);
      } else if (showCtrls) {
        paintControls(c, w, h, d);
      } else {
        paintLyrics(c, w, h, d, sd);
      }

      // 关键：播放中自驱动连续重绘
      if (playing && !showCtrls) {
        postInvalidateOnAnimation();
      }
    }

    // ---------- 歌词绘制 ----------

    private void paintLyrics(Canvas c, int w, int h, float d, float sd) {
      float tsz = fontSizeSp * sd;
      tp.setTextSize(tsz);
      tp.setTypeface(Typeface.DEFAULT);
      tp.setShadowLayer(2*d, 0, 0, colorShadow);
      tp.setShader(null);

      float pad = 14*d, maxW = w - pad*2;
      List<Line> ly = activeLines();
      long sk = seekMs();
      int idx = ly.isEmpty() ? -1 : findIndex(ly, sk);

      if (ly.isEmpty() || idx < 0 || idx >= ly.size()) {
        String txt = songName.isEmpty() ? "SPlayer" : songName + " - " + artistName;
        tp.setColor(0xBBFFFFFF);
        centeredText(c, txt, w, h * 0.5f, tp, maxW);
        return;
      }

      Line line = ly.get(idx);
      boolean hasTran = line.tran != null && !line.tran.isEmpty();
      boolean hasNext = idx + 1 < ly.size();
      boolean twoLine = hasTran || hasNext;
      float mainY = twoLine ? h * 0.34f : h * 0.5f;

      // 主歌词
      if (wordMode && !yrcLines.isEmpty() && line.words.size() > 1) {
        paintWordLyric(c, line, sk, w, mainY, tsz, pad, d);
      } else {
        tp.setShader(null);
        tp.setColor(colorPlayed);
        tp.setTextSize(tsz);
        centeredText(c, lineText(line), w, mainY, tp, maxW);
      }

      // 第二行
      if (twoLine) {
        String sub = hasTran ? line.tran : lineText(ly.get(idx + 1));
        tp.setShader(null);
        tp.setColor(colorUnplayed);
        tp.setTextSize(tsz * 0.7f);
        centeredText(c, sub, w, h * 0.72f, tp, maxW);
      }
    }

    private void paintWordLyric(Canvas c, Line line, long sk,
        int w, float cy, float tsz, float pad, float d) {
      tp.setTextSize(tsz);
      tp.setShader(null);
      // 逐字渲染时关闭 shadow，避免 gradient 模式下出现白色描边
      tp.setShadowLayer(0, 0, 0, 0);
      int n = line.words.size();
      float[] ww = new float[n];
      float total = 0;
      for (int i = 0; i < n; i++) {
        ww[i] = tp.measureText(line.words.get(i).text);
        total += ww[i];
      }
      float x = Math.max(pad, (w - total) / 2f);
      Paint.FontMetrics fm = tp.getFontMetrics();
      float bl = cy - (fm.ascent + fm.descent) / 2f;

      for (int i = 0; i < n; i++) {
        Word word = line.words.get(i);
        float prog = wordProg(word, sk);
        tp.setShader(null);
        if (prog <= 0f) {
          tp.setColor(colorUnplayed);
        } else if (prog >= 1f) {
          tp.setColor(colorPlayed);
        } else {
          float sx = x + ww[i] * prog;
          tp.setShader(new LinearGradient(sx - d*0.5f, 0, sx + d*0.5f, 0,
              colorPlayed, colorUnplayed, Shader.TileMode.CLAMP));
          tp.setColor(Color.WHITE);
        }
        c.drawText(word.text, x, bl, tp);
        x += ww[i];
      }
      tp.setShader(null);
      // 恢复 shadow
      tp.setShadowLayer(2*d, 0, 0, colorShadow);
    }

    private float wordProg(Word w, long sk) {
      if (sk >= w.end) return 1f;
      if (sk <= w.start) return 0f;
      return (float)(sk - w.start) / Math.max(w.end - w.start, 1f);
    }

    // ---------- 控制栏 ----------

    private void paintControls(Canvas c, int w, int h, float d) {
      float sz = 34*d, gap = 20*d;
      float tw = sz*5 + gap*4, sx = (w - tw) / 2f, cy = h / 2f;
      ip.setTextAlign(Paint.Align.CENTER);
      ip.setTextSize(18*d);
      ip.setColor(0xFFFFFFFF);

      drawBtn(c, rLock, sx, cy, sz, d, "🔒");
      drawBtn(c, rPrev, sx + sz + gap, cy, sz, d, "⏮");
      drawBtn(c, rPlay, sx + (sz+gap)*2, cy, sz, d, playing ? "⏸" : "▶");
      drawBtn(c, rNext, sx + (sz+gap)*3, cy, sz, d, "⏭");
      drawBtn(c, rClose, sx + (sz+gap)*4, cy, sz, d, "✕");
    }

    private void drawBtn(Canvas c, RectF r, float l, float cy, float sz, float d, String icon) {
      r.set(l, cy-sz/2, l+sz, cy+sz/2);
      bp.setColor(0x40FFFFFF);
      c.drawRoundRect(r, 8*d, 8*d, bp);
      Paint.FontMetrics fm = ip.getFontMetrics();
      c.drawText(icon, r.centerX(), r.centerY() - (fm.ascent+fm.descent)/2f, ip);
    }

    // ---------- 工具 ----------

    private void centeredText(Canvas c, String t, int w, float cy, Paint p, float mw) {
      p.setShader(null);
      float tw = p.measureText(t);
      String dt = t;
      if (tw > mw && mw > 0) {
        int n = p.breakText(t, true, mw - p.measureText("…"), null);
        if (n > 0 && n < t.length()) { dt = t.substring(0, n) + "…"; tw = p.measureText(dt); }
      }
      Paint.FontMetrics fm = p.getFontMetrics();
      c.drawText(dt, Math.max(0, (w-tw)/2f), cy-(fm.ascent+fm.descent)/2f, p);
    }
  }

  // ==================== 数据结构 ====================

  static String lineText(Line l) {
    if (l == null || l.words.isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    for (Word w : l.words) sb.append(w.text);
    return sb.toString();
  }

  static class Line {
    long start, end;
    String tran = "";
    List<Word> words = new ArrayList<>();
  }

  static class Word {
    final String text;
    final long start, end;
    Word(String t, long s, long e) { text = t; start = s; end = e; }
  }
}

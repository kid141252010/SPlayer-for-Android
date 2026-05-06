package top.imsyy.splayer.android;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;
import top.imsyy.splayer.android.download.AndroidDownloadPlugin;
import top.imsyy.splayer.android.lyric.AndroidLocalLyricPlugin;
import top.imsyy.splayer.android.playback.AndroidNativePlaybackPlugin;

public class MainActivity extends BridgeActivity {
  private static final String PREF_SHOW_STATUS_BAR = "androidShowStatusBar";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    registerPlugin(AndroidNativePlaybackPlugin.class);
    registerPlugin(AndroidLocalLyricPlugin.class);
    registerPlugin(AndroidDownloadPlugin.class);
    super.onCreate(savedInstanceState);
    applyImmersiveMode();
  }

  @Override
  public void onResume() {
    super.onResume();
    applyImmersiveMode();
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      applyImmersiveMode();
    }
  }

  private boolean shouldShowStatusBar() {
    try {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      return prefs.getBoolean(PREF_SHOW_STATUS_BAR, false);
    } catch (Exception e) {
      return false;
    }
  }

  public void applyImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

    View decorView = getWindow().getDecorView();
    WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), decorView);
    if (controller != null) {
      boolean showStatusBar = shouldShowStatusBar();
      if (showStatusBar) {
        controller.setSystemBarsBehavior(
            WindowInsetsControllerCompat.BEHAVIOR_DEFAULT);
        controller.show(WindowInsetsCompat.Type.statusBars());
      } else {
        controller.setSystemBarsBehavior(
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.statusBars());
      }
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
          | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
      if (!shouldShowStatusBar()) {
        flags |= View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
      }
      decorView.setSystemUiVisibility(flags);
    }
  }
}

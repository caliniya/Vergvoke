package caliniya.vergvoke;

import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import static arc.Core.*;

import android.os.Bundle;
import arc.*;
import arc.ApplicationListener;
import arc.backend.android.AndroidApplication;
import arc.backend.android.AndroidApplicationConfiguration;
import arc.files.Fi;
import arc.graphics.Color;
import arc.util.Log;
import arc.util.Log.*;
import caliniya.vergvoke.core.UI;
import cat.ereza.customactivityoncrash.config.CaocConfig;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AndroidLauncher extends AndroidApplication {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    CaocConfig.Builder.create().enabled(true).errorActivity(ErrorActivity.class).apply();

    Window win = getWindow();
    win.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.BLACK));
    requestFullscreen(win);

    initialize(
        new Vergvoke(),
        new AndroidApplicationConfiguration() {
          {
            useImmersiveMode = true;
            hideStatusBar = true;
          }
        });

    // --- 安全区检测 ---
    View rootView = getWindow().getDecorView();
    rootView.setOnApplyWindowInsetsListener((v, insets) -> {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        DisplayCutout cutout = insets.getDisplayCutout();
        if (cutout != null) {
          UI.safeAreaSize = max(
              max(cutout.getSafeInsetTop(), cutout.getSafeInsetBottom()),
              max(cutout.getSafeInsetLeft(), cutout.getSafeInsetRight()));
        }
      }
      return insets;
    });
    rootView.requestApplyInsets();
    // --- 安全区检测结束 ---
    Fi data = Core.files.absolute(this.getExternalFilesDir(null).getAbsolutePath());
    Core.settings.setDataDirectory(data);
    try {
      Writer writer = settings.getDataDirectory().child("log.txt").writer(false);
      LogHandler originalLogger = Log.logger;
      // 要过滤的标签列表(它们太多了而且一般没有用)
      String[] filteredTags = {"AndroidGraphics", "GL30"};
      
      // 定义时间格式
      SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
      
      Log.level = Log.LogLevel.info;
      
      Log.logger =
          (level, text) -> {
            
            if (Log.level != Log.LogLevel.debug) {
              // 直接进行过滤检查
              for (String tag : filteredTags) {
                if (text.matches("\\[" + tag + "\\].*")) {
                  return;
                }
              }
            }
            
            originalLogger.log(level, text);
            try {
              // 获取当前时间
              String timestamp = dateFormat.format(new Date());
              
              writer.write(
                  "["
                      + timestamp
                      + "] ["
                      + Character.toUpperCase(level.name().charAt(0))
                      + "] "
                      + Log.removeColors(text)
                      + "\n");
              writer.flush();
            } catch (Exception e) {
              e.printStackTrace();
            }
          };
    } catch (Exception e) {
      // 只能这么做了
      Log.err(e);
    }
    //Log.level = Log.LogLevel.info;
    Log.info("Start-Android");
    Log.info("Log Level : " + Log.level);
  }

  /**
   * 全屏处理
   */
  private void requestFullscreen(Window win) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      WindowInsetsController controller = win.getDecorView().getWindowInsetsController();
      if (controller != null) {
        controller.hide(WindowInsets.Type.statusBars());
        controller.setSystemBarsBehavior(
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      }
    } else {
      legacyFullscreen(win);
    }
  }

  @SuppressWarnings("deprecation")
  private void legacyFullscreen(Window win) {
    win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
  }

  @Override
  public void addListener(ApplicationListener appl) {
    synchronized (this.getListeners()) {
      this.getListeners().add(appl);
    }
  }

  private static int max(int a, int b) {
    return a > b ? a : b;
  }
}
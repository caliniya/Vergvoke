package caliniya.vergvoke.io;

import java.io.File;

import arc.Core;
import arc.files.Fi;
import arc.util.Log;
import arc.util.OS;

/**
 * 数据目录解析（桌面端）：
 *
 * <ol>
 * <li>系统属性 {@code -Dvergvoke.datadir=<path>} 显式指定（开发 / CI / 玩家自定义）；
 * <li>便携：{@code <程序目录>/data} —— 程序目录取 jpackage 生成的 exe 所在目录
 * （用 {@code jpackage.app-path}），没有就用 jar / classes 目录；目录不可写时回退；
 * <li>回退：{@code %APPDATA%/Vergvoke}（Arc 默认，装到 Program Files 这种只读位置时用）。
 * </ol>
 *
 * 移动端不走这里（数据目录在 AndroidLauncher 里指到外置存储）。
 */
public class DataPaths {

    /** 显式指定数据目录的系统属性名。 */
    public static final String OVERRIDE_PROPERTY = "vergvoke.datadir";

    /** 解析并把数据目录设给 Arc settings（之后所有 {@code settings.getDataDirectory()} 都走这里）。 */
    public static Fi apply() {
        Fi dir = resolve();
        dir.mkdirs();
        Core.settings.setDataDirectory(dir);
        Log.info("Data dir: @", dir.absolutePath());
        return dir;
    }

    /** @return 最终采用的数据目录（不写 settings，方便测试）。 */
    public static Fi resolve() {
        String override = System.getProperty(OVERRIDE_PROPERTY);
        if (override != null && !override.isEmpty()) {
            return Fi.get(override);
        }

        Fi appDir = appDirectory();
        if (appDir != null) {
            Fi data = appDir.child("data");
            if (canWrite(data)) {
                return data;
            }
            Log.warn("App dir not writable, falling back to user data dir: @", appDir.absolutePath());
        }

        return Fi.get(OS.getAppDataDirectoryString(Core.settings.getAppName()));
    }

    /**
     * 程序所在目录：
     * jpackage 打出来的 exe 用 {@code jpackage.app-path} 的父目录；
     * 否则用 jar（或开发时的 classes 目录）的位置。
     */
    private static Fi appDirectory() {
        try {
            String appPath = System.getProperty("jpackage.app-path");
            if (appPath != null && !appPath.isEmpty()) {
                return Fi.get(appPath).parent();
            }

            File location = new File(
                    DataPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Fi fi = Fi.get(location.getAbsolutePath());
            return fi.isDirectory() ? fi : fi.parent();
        } catch (Exception e) {
            Log.warn("Cannot locate app directory: @", e.toString());
            return null;
        }
    }

    /** 目录能建、而且真能写进去才算可写。 */
    private static boolean canWrite(Fi dir) {
        try {
            dir.mkdirs();
            Fi probe = dir.child(".write-test");
            probe.writeString("ok", false);
            probe.delete();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

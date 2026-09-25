package caliniya.vergvoke.io;

import arc.Core;
import arc.files.Fi;
import arc.func.Cons;
import arc.struct.*;
import arc.util.*;
import arc.util.io.Reads;
import caliniya.vergvoke.map.Map;
import java.io.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class GameIO {

    public static final String MAGIC = "AEVS";
    /** v2：单位序列化加入坐标（MoveComp.x/y）与阵营（StateComp），旧存档不兼容 */
    public static final int SAVE_VERSION = 2;

    private static final BlockingQueue<Runnable> ioQueue = new LinkedBlockingQueue<>();
    private static volatile Thread ioThread;

    /** 确保后台 IO 线程已启动（线程安全，懒加载）。 */
    private static void ensureIoThread() {
        if (ioThread != null)
            return;
        synchronized (GameIO.class) {
            if (ioThread != null)
                return;
            Thread t = new Thread(
                    () -> {
                        while (true) {
                            Runnable task;
                            try {
                                task = ioQueue.take(); // 队列空时阻塞等待
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break; // 被中断则退出线程
                            }
                            try {
                                task.run();
                            } catch (Throwable err) {
                                Log.err("IO task failed", err);
                            }
                        }
                    },
                    "Vergvoke-IO");
            t.setDaemon(true);
            t.start();
            ioThread = t;
        }
    }

    /** 把一个任务丢进后台 IO 线程执行。 */
    public static void submitIo(Runnable task) {
        ensureIoThread();
        ioQueue.add(task);
    }

    // ==================== 元数据读取 ====================

    /** 快速读取存档头，返回 Map 元数据对象（不加载游戏数据） */
    public static Map readMeta(Fi file) {
        try (DataInputStream stream = new DataInputStream(file.read())) {
            Reads r = new Reads(stream);
            String magic = new String(r.b(4));
            if (!magic.equals(MAGIC))
                return null;
            int ver = r.i();
            int w = r.i();
            int h = r.i();
            StringMap tags = new StringMap();
            int tagCount = r.s();
            for (int i = 0; i < tagCount; i++)
                tags.put(r.str(), r.str());
            return new Map(file, w, h, tags, true);
        } catch (IOException e) {
            return null;
        }
    }

    public static void save(Fi file) {
        save(file, null);
    }

    /**
     * 将DataIO的数据写入磁盘
     *
     * <p>
     * 指定文件路径
     */
    public static void save(Fi file, Cons<byte[]> con) {
        if (!DataIO.copyed)
            return; // 如果数据尚未复制完，那就不可以执行
        submitIo(
                () -> {
                    try {
                        file.writeBytes(DataIO.data);
                        Log.info("Save success - @", file.absolutePath());
                    } catch (Throwable e) {
                        Log.err("Save field -", e);
                    }
                    if (con != null) {
                        Core.app.post(() -> con.get(DataIO.data));
                    }
                });
    }

    public static void load(Fi file) {
        load(file, null);
    }

    // 从指定文件将数据加载到内存
    public static void load(Fi file, Cons<byte[]> onData) {
        submitIo(
                () -> {
                    try {
                        DataIO.data = file.readBytes(); // 后台读盘
                        DataIO.loaded = true;
                    } catch (Throwable e) {
                        Log.err("Load read failed", e);
                        DataIO.data = null;
                    }
                    if (onData != null) {
                        Core.app.post(() -> onData.get(DataIO.data));
                    }
                });
    }
}

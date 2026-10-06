package caliniya.vergvoke.io;

import arc.*;
import arc.files.*;
import arc.util.*;
import arc.util.io.*;
import caliniya.vergvoke.game.data.*;

import java.io.*;

/** 进度文件的读写（数据目录/progress.aevp）。 */
public class ProgressIO {

	public static final String MAGIC = "AEVP";
	public static final int SAVE_VERSION = 5;

	private ProgressIO() {
	}

	public static Fi file() {
		return Core.settings.getDataDirectory().child("progress.aevp");
	}

	/** 保存进度。 */
	public static void save(ProgressData data) {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 10);
			 DataOutputStream stream = new DataOutputStream(bos)) {
			Writes w = new Writes(stream);
			w.b(MAGIC.getBytes());
			w.i(SAVE_VERSION);
			data.write(w);
			stream.flush();
			file().writeBytes(bos.toByteArray());
		} catch (Throwable e) {
			Log.err("Progress save failed", e);
		}
	}

	/** 加载进度；文件不存在/损坏/版本过高时返回空进度。 */
	public static ProgressData load() {
		Fi f = file();
		if (!f.exists()) return new ProgressData();
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(f.readBytes()))) {
			Reads r = new Reads(in);
			String magic = new String(r.b(4));
			if (!magic.equals(MAGIC)) {
				Log.warn("Invalid progress file");
				return new ProgressData();
			}
			int ver = r.i();
			if (ver > SAVE_VERSION) {
				Log.warn("Unsupported progress version: @", ver);
				return new ProgressData();
			}
			ProgressData data = new ProgressData();
			data.read(r);
			return data;
		} catch (Throwable e) {
			Log.err("Progress load failed", e);
			return new ProgressData();
		}
	}
}

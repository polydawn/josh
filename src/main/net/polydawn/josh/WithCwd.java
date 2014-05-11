package net.polydawn.josh;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class WithCwd implements AutoCloseable {
	public WithCwd(String relPath) {
		this(new File(relPath));
	}

	public WithCwd(File relPath) {
		popDir = new File(System.getProperties().getProperty("user.dir"));
		pushedDir = relPath.isAbsolute() ? relPath : new File(popDir, relPath.toString());
		pushedDir.mkdirs();
		cd(pushedDir);
	}


	/**
	 * Creates a random temporary directory under {@link #tmp}.
	 */
	public static WithCwd temp() {
		try {
			return new WithCwd(createUniqueTempFolder().getCanonicalFile());
		} catch (IOException e) {
			throw new Error("cwd?", e);
		}
	}

	static File createUniqueTempFolder() {
		while (true) {
			File f = new File(tmp, UUID.randomUUID().toString());
			if (f.mkdirs()) {
				tmpdirs.add(f);
				return f;
			}
		}
	}


	static final File tmp = new File(System.getProperty("java.io.tmpdir"));
	private static final List<File> tmpdirs = new ArrayList<File>();
	private static final boolean keep = !System.getProperty("keep-tmpdir", "false").equals("false");
	static {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.gc();
				Iterator<File> itr = tmpdirs.iterator();
				while (itr.hasNext()) {
					delete(itr.next());
					itr.remove();
				}
			}
		});
	}

	final File pushedDir;
	final File popDir;

	private void cd(File dir) {
		System.getProperties().setProperty("user.dir", dir.toString());
	}

	public void close() {
		cd(popDir);
	}

	public void clear() {
		close();
		delete(pushedDir);
	}

	private static void delete(File f) {
		if (!keep) deleter(f);
	}

	private static boolean deleter(File f) {
		File[] fs = f.listFiles();
		if (fs != null)
			for (File g : fs)
				deleter(g);
		return f.delete();
	}
}

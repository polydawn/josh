package net.polydawn.josh;

import java.io.*;
import java.util.*;

public class WithCwd implements AutoCloseable {
	public WithCwd(String relPath) {
		this(new File(relPath), false);
	}

	public WithCwd(File relPath) {
		this(relPath, false);
	}

	public WithCwd(String relPath, boolean deleteOnClose) {
		this(new File(relPath), deleteOnClose);
	}

	public WithCwd(File relPath, boolean deleteOnClose) {
		this.deleteOnClose = deleteOnClose;
		popDir = new File(System.getProperties().getProperty("user.dir"));
		pushedDir = relPath.isAbsolute() ? relPath : new File(popDir, relPath.toString());
		pushedDir.mkdirs();
		cd(pushedDir);
	}


	/**
	 * Creates a random temporary directory under {@link #tmp}, by the same mechanisms
	 * as {@link #temp(File)}.
	 */
	public static WithCwd temp() {
		return temp(tmp);
	}

	public static WithCwd temp(File basedir) {
		return temp(basedir, "", false);
	}

	public static WithCwd temp(boolean deleteOnClose) {
		return temp(tmp, deleteOnClose);
	}

	public static WithCwd temp(File basedir, boolean deleteOnClose) {
		return temp(basedir, "", deleteOnClose);
	}

	public static WithCwd temp(File basedir, String prefix) {
		return temp(basedir, prefix, false);
	}

	/**
	 * Creates a random temporary directory under {@code basedir}.
	 * <p>
	 * A best-effort attempt will be made to unlink all of temporary paths when the
	 * program exits.
	 */
	public static WithCwd temp(File basedir, String prefix, boolean deleteOnClose) {
		try {
			while (true) {
				File f = new File(basedir, prefix+UUID.randomUUID().toString()).getCanonicalFile();
				if (f.mkdirs()) {
					tmpdirs.add(f);
					return new WithCwd(f, deleteOnClose);
				}
			}
		} catch (IOException e) {
			throw new Error("cwd?", e);
		}
	}


	public static final File tmp = new File(System.getProperty("java.io.tmpdir"));
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
	final boolean deleteOnClose;

	private void cd(File dir) {
		System.getProperties().setProperty("user.dir", dir.toString());
	}

	public void close() {
		cd(popDir);
		if (deleteOnClose) delete(pushedDir);
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

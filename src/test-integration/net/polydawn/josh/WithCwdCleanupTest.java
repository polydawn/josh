package net.polydawn.josh;

import java.io.*;

public class WithCwdCleanupTest {
	public static void main(String... args) throws IOException {
		try (WithCwd cwd = new WithCwd("pants", true)) {
			new File("left").getCanonicalFile().mkdir();
			new File("right").getCanonicalFile().mkdir();
		}
		// should be no pants
	}
}

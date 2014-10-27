package net.polydawn.josh;

import java.io.*;
import java.util.concurrent.*;

public class BenchmarkDemo {
	public static void main(String... args) {
		try {
			really();
		} catch (Throwable e) {
			e.printStackTrace();
			System.exit(3);
		}
	}

	public static void really() throws InterruptedException, ExecutionException, IOException {
		System.out.printf("starting benchmark...\n");

		// Note we use 'touch' instead of something like 'echo'.
		// This is because 'echo' is a builtin in bash,
		// and thus has radically different performance implications (namely, it doesn't fork/exec at all).

		long start, end;
		float seconds;

		start = System.currentTimeMillis();
		for (int n = 0; n < 100; n++) {
			new Josh("touch").args(".").opts(Opts.DirectIO).start().get();
		}
		end = System.currentTimeMillis();

		seconds = (end-start)/1000f;
		System.out.printf("time looped touching in josh: %.3fsec (%.3fsec each)\n", seconds, seconds/100f);

		start = System.currentTimeMillis();
		new Josh("bash").args("-c", "for n in {1..100}; do touch . ; done").opts(Opts.DirectIO).start().get();
		end = System.currentTimeMillis();

		seconds = (end-start)/1000f;
		System.out.printf("time looped touching in bash: %.3fsec (%.3fsec each)\n", seconds, seconds/100f);
	}
}

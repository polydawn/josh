package net.polydawn.josh;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

public class Opts {
	public Opts() {}

	public static final Opts DefaultIO = new Opts() {{
		in_pass();
		out_pass();
		err_pass();
	}};

	public static final Opts NullIO = new Opts() {{
		in_null();
		out_null();
		err_null();
	}};

	public Opts(Opts cpy) {
		this.in = cpy.in;
		this.out = cpy.out;
		this.err = cpy.err;
	}

	public InputStream in() {
		return in;
	}

	public OutputStream out() {
		return out;
	}

	public OutputStream err() {
		return err;
	}

	// any of these fields may be null for "leave it"

	/**
	 * Can be a:
	 *   - string, in which case it will be copied in literally
	 *   - []byte, again, taken literally
	 *   - io.Reader, which will be streamed in
	 *   - bytes.Buffer, all that sort of thing, taken literally
	 *   - <-chan string, in which case that will be streamed in
	 *   - <-chan byte[], in which case that will be streamed in
	 *   - another Command, in which case that will be started with this one and its output piped into this one
	 */
	InputStream in;

	/**
	 * Can be a:
	 *   - bytes.Buffer, which will be written to literally
	 *   - io.Writer, which will be written to streamingly, flushed to whenever the command flushes
	 *   - chan<- string, which will be written to streamingly, flushed to whenever a line break occurs in the output
	 *   - chan<- byte[], which will be written to streamingly, flushed to whenever the command flushes
	 *
	 * (There's nothing that's quite the equivalent of how you can give In a string, sadly; since
	 * strings are immutable in golang, you can't set Out=&str and get anywhere.)
	 */
	OutputStream out;

	OutputStream err;

	public Opts in(String newInput) {
		this.in = new ByteArrayInputStream(newInput.getBytes(Charset.forName("UTF-8")));
		return this;
	}

	public Opts in(Queue<String> newInput) {
		this.in = new InputStringer(newInput);
		return this;
	}

	public Opts in(byte[] newInput) {
		this.in = new ByteArrayInputStream(newInput);
		return this;
	}

	public Opts in(InputStream newInput) {
		this.in = newInput;
		return this;
	}

	public Opts in_null() {
		this.in = new ClosedInputStream();
		return this;
	}

	public Opts in_pass() {
		this.in = System.in;
		return this;
	}



	static class InputStringer extends InputStream {
		public InputStringer(Queue<String> source) {
			// maybe Streams from java8 are actually what would best fulfill my dreams here
			this.source = source;
			this.buffer = ByteBuffer.wrap(new byte[0]);
		}

		private final Queue<String> source;
		private ByteBuffer buffer;

		private boolean pump() {
			if (!buffer.hasRemaining()) {
				if (source.isEmpty()) return false;
				buffer = ByteBuffer.wrap(source.poll().getBytes(Charset.forName("UTF-8")));
			}
			return true;
		}

		public int available() throws IOException {
			return buffer.remaining();
		}

		public int read() throws IOException {
			if (!pump()) return -1;
			return buffer.get();
		}

		public int read(byte[] b) throws IOException {
			return read(b, 0, b.length);
		}

		public int read(byte[] b, int off, int len) throws IOException {
			if (!pump()) return -1;
			int readSize = Math.min(len, available());
			buffer.get(b, off, readSize);
			return readSize;
		}

		public void close() throws IOException {
			super.close();
		}
	}



	static class ClosedInputStream extends InputStream {
		public int available() {
			return 0;
		}

		public int read() {
			return -1;
		}

		public int read(byte[] b) {
			return -1;
		}

		public int read(byte[] b, int off, int len) {
			return -1;
		}

		public void close() {}
	}



	// it's really embarassing how golang had all these awesome options for getting data back out, and in java it's kinda fuck you.

	public Opts out(OutputStream newOutput) {
		this.out = newOutput;
		return this;
	}

	/**
	 * Provide a collection to drain output of the command into.
	 * <p>
	 * If you're going to wait for the process to finish, any old collection is fine
	 * here. If you want to read out of this before waiting for the process to finish,
	 * you're gonna want to use a thread-safe queue; ConcurrentLinkedQueue is a
	 * reasonable option.
	 */
	public Opts out(Collection<String> newOutput) {
		this.out = new OutputStringer(newOutput);
		return this;
	}

	public Opts out(OutputStringer newOutput) {
		this.out = newOutput;
		return this;
	}

	//public Opts outBytes(Collection<byte[]> newOutput) {
	//	this.out = new OutputChunker(newOutput);
	//	return this;
	//}
	//
	//public Opts outBytes(OutputChunker newOutput) {
	//	this.out = newOutput;
	//	return this;
	//}

	public Opts out_null() {
		this.out = new NullOutputStream();
		return this;
	}

	public Opts out_closed() {
		this.out = new ClosedOutputStream();
		return this;
	}

	public Opts out_pass() {
		this.out = System.out;
		return this;
	}

	// and now all the same for stderr

	public Opts err(OutputStream newErrput) {
		this.err = newErrput;
		return this;
	}

	/**
	 * Provide a collection to drain output of the command into.
	 * <p>
	 * If you're going to wait for the process to finish, any old collection is fine
	 * here. If you want to read out of this before waiting for the process to finish,
	 * you're gonna want to use a thread-safe queue; ConcurrentLinkedQueue is a
	 * reasonable option.
	 */
	public Opts err(Collection<String> newErrput) {
		this.err = new OutputStringer(newErrput);
		return this;
	}

	public Opts err(OutputStringer newErrput) {
		this.err = newErrput;
		return this;
	}

	//public Opts errBytes(Collection<byte[]> newErrput) {
	//	this.out = new OutputChunker(newErrput);
	//	return this;
	//}
	//
	//public Opts errBytes(OutputChunker newErrput) {
	//	this.out = newErrput;
	//	return this;
	//}

	public Opts err_null() {
		this.err = new NullOutputStream();
		return this;
	}

	public Opts err_closed() {
		this.err = new ClosedOutputStream();
		return this;
	}

	public Opts err_pass() {
		this.err = System.err;
		return this;
	}



	public static class OutputStringer extends OutputStream {
		public OutputStringer(Collection<String> sink) {
			this(sink, false);
		}

		public OutputStringer(Collection<String> sink, boolean breakOnFlush) {
			this(sink, breakOnFlush, (byte) '\n');
		}

		public OutputStringer(Collection<String> sink, boolean breakOnFlush, byte breakByte) {
			this.sink = sink;
			this.breakOnFlush = breakOnFlush;
			this.breakByte = breakByte;
			this.buffer = new ByteArrayOutputStream();
		}

		private final Collection<String> sink;

		/**
		 * If true, outputs a string every time a flush() is invoked; if false,
		 * ignores flush() and outputs a string every time a {@link #breakByte} is
		 * encountered.
		 *
		 * Regardless {@link #breakByte} characters will not be stripped, because
		 * we're not insanely hostile to the concept of output being comparable to
		 * input (I'm looking at you, {@link BufferedReader#readLine()}).
		 */
		private final boolean breakOnFlush;

		private final byte breakByte;

		private final ByteArrayOutputStream buffer;

		public void write(int b) throws IOException {
			buffer.write(b);
			if (!breakOnFlush && b == breakByte)
				push();
		}

		// zero guarantees about the efficiency of all this.  java's stdlib for handling rows of bytes is one of
		// the most monstrous survivors of the 90s and trying to fix it is like trying to cut a new sluice through
		// the three gorges dam with tacky grill ignitor from the dollar store.
		public void write(byte b[], int off, int len) throws IOException {
			if (b == null)
				throw new NullPointerException();
			if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0))
				throw new IndexOutOfBoundsException();
			if (len == 0)
				return;
			if (breakOnFlush) {
				buffer.write(b, off, len);
			} else {
				for (int i = 0; i < len; i++) {
					byte a = b[off + i];
					buffer.write(a);
					if (a == breakByte)
						push();
				}
			}
		}

		private void push() {
			try {
				sink.add(buffer.toString("UTF-8"));
			} catch (UnsupportedEncodingException e) {
				throw new Error(e);
			}
			buffer.reset();
			// if BAOS was a less completely shit abstraction i might ask it to shrink in case it's a gig, but, welp.
			// someday some saint should make a halfway decent byte rope implementation.
		}

		public void flush() throws IOException {
			if (breakOnFlush)
				push();
		}

		public void close() throws IOException {
			if (buffer.size() > 0)
				push();
		}
	}



	static class ClosedOutputStream extends OutputStream {
		// be nice if there was a predicate for, oh, i don't know, isClosed().
		// but I guess the command runner will have to catch these and silently close the process's stream.
		// ohwait, no, that's a race and stupid.  sigh, fuckit, we'll just instanceof on this.

		public void write(int b) throws IOException {
			throw new IOException("closed");
		}

		public void write(byte[] b) throws IOException {
			throw new IOException("closed");
		}

		public void write(byte[] b, int off, int len) throws IOException {
			throw new IOException("closed");
		}

		public void flush() throws IOException {}

		public void close() throws IOException {}
	}



	static class NullOutputStream extends OutputStream {
		public void write(int b) throws IOException {}

		public void write(byte[] b) throws IOException {}

		public void write(byte[] b, int off, int len) throws IOException {}

		public void flush() throws IOException {}

		public void close() throws IOException {}
	}
}

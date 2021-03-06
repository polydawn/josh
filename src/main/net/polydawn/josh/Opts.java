/*
 * Copyright 2014 Eric Myhre <http://exultant.us>
 *
 * This file is part of josh <https://github.com/polydawn/josh/>.
 *
 * josh is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package net.polydawn.josh;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

public class Opts {
	public Opts() {}

	/**
	 * Routes input, output, and err channels through {@link System#in},
	 * {@link System#out}, and {@link System#err}.
	 */
	// FIXME: no, really: the reason this is fucked in the first place is that this lets the subprocess close the output streams.
	// FIXME: which, somehow, miraculously, (and I can't even figure out how to write tests for this) causes proc.waitFor to HANG if run in eclipse's console.
	public static final Opts DefaultIO = new Opts() {{
		in_pass();
		out_pass();
		err_pass();
	}};

	/**
	 * Applies "direct" input, output, and err channels. If you wish to use TTYs in
	 * your application, you will probably want this.
	 */
	public static final Opts DirectIO = new Opts() {{
		in_direct();
		out_direct();
		err_direct();
	}};

	/**
	 * Routes input, output, and err channels to /dev/null.
	 */
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
		this.in = new UnclosableInputStream(System.in);
		return this;
	}

	/**
	 * Force use of ProcessBuilder.Redirect.INHERIT instead of shuttling bytes through
	 * an InputStream. This allows use of a TTY, but will not observe any changes to
	 * the JVM's value of {@link System#in}.
	 */
	public Opts in_direct() {
		this.in = new MagicInputStream();
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



	/**
	 * Used to proxy System.in but silently ignore close operations (closing System.in
	 * can cause Strange Things To Happen).
	 */
	static class UnclosableInputStream extends InputStream {
		public UnclosableInputStream(InputStream delegate) {
			this.delegate = delegate;
		}

		private final InputStream delegate;

		public void close() {}

		// delegate methods:

		public int read() throws IOException {
			return this.delegate.read();
		}

		public int hashCode() {
			return this.delegate.hashCode();
		}

		public int read(byte[] b) throws IOException {
			return this.delegate.read(b);
		}

		public boolean equals(Object obj) {
			return this.delegate.equals(obj);
		}

		public int read(byte[] b, int off, int len) throws IOException {
			return this.delegate.read(b, off, len);
		}

		public long skip(long n) throws IOException {
			return this.delegate.skip(n);
		}

		public String toString() {
			return this.delegate.toString();
		}

		public int available() throws IOException {
			return this.delegate.available();
		}

		public synchronized void mark(int readlimit) {
			this.delegate.mark(readlimit);
		}

		public synchronized void reset() throws IOException {
			this.delegate.reset();
		}

		public boolean markSupported() {
			return this.delegate.markSupported();
		}
	}



	static class MagicInputStream extends InputStream {
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
		this.out = new UnclosableOutputStream(System.out);
		return this;
	}

	/**
	 * Force use of ProcessBuilder.Redirect.INHERIT instead of shuttling bytes through
	 * an OutputStream. This allows use of a TTY, but will not observe any changes to
	 * the JVM's value of {@link System#out}.
	 */
	public Opts out_direct() {
		this.out = new MagicOutputStream();
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
		this.err = new UnclosableOutputStream(System.err);
		return this;
	}

	/**
	 * Force use of ProcessBuilder.Redirect.INHERIT instead of shuttling bytes through
	 * an OutputStream. This allows use of a TTY, but will not observe any changes to
	 * the JVM's value of {@link System#err}.
	 */
	public Opts err_direct() {
		this.err = new MagicOutputStream();
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



	/**
	 * Used to proxy System.out/err but silently ignore close operations (closing
	 * System.out/err can cause Strange Things To Happen).
	 */
	static class UnclosableOutputStream extends OutputStream {
		public UnclosableOutputStream(OutputStream delegate) {
			this.delegate = delegate;
		}

		private final OutputStream delegate;

		public void close() {}

		// delegate methods:

		public void write(int b) throws IOException {
			this.delegate.write(b);
		}

		public int hashCode() {
			return this.delegate.hashCode();
		}

		public void write(byte[] b) throws IOException {
			this.delegate.write(b);
		}

		public void write(byte[] b, int off, int len) throws IOException {
			this.delegate.write(b, off, len);
		}

		public boolean equals(Object obj) {
			return this.delegate.equals(obj);
		}

		public void flush() throws IOException {
			this.delegate.flush();
		}

		public String toString() {
			return this.delegate.toString();
		}
	}



	static class MagicOutputStream extends OutputStream {
		public void write(int b) throws IOException {}

		public void write(byte[] b) throws IOException {}

		public void write(byte[] b, int off, int len) throws IOException {}

		public void flush() throws IOException {}

		public void close() throws IOException {}
	}
}

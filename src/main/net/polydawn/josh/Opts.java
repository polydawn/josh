package net.polydawn.josh;

import java.io.*;
import java.nio.charset.*;
import java.util.*;

public class Opts {
	// any of these fields may be null for "leave it"

	public File cwd;

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
	public InputStream in;

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
	public OutputStream out;

	public OutputStream err;

	/**
	 * Exit status codes that are to be considered "successful".  If not provided, [0] is the default.
	 * (If this slice is provided, zero will -not- be considered a success code unless explicitly included.)
	 */
	public List<Byte> okExit;

	public Opts in(String newInput) {
		this.in = new ByteArrayInputStream(newInput.getBytes(Charset.forName("UTF-8")));
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
		this.in = new ByteArrayInputStream(new byte[0]);
		return this;
	}

	public Opts in_pass() {
		this.in = System.in;
		return this;
	}

	// it's really embarassing how golang had all these awesome options for getting data back out, and in java it's kinda fuck you.
}

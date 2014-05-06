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
import java.util.*;
import java.util.concurrent.*;

/**
 * A command template.  The n'th reimplementation of pbs/gosh/sigh.
 *
 * No, ProcessBuilder does not count.  You know why.
 *
 * @author Eric Myhre <tt>hash@exultant.us</tt>
 *
 */
public class Josh {
	public Josh(String cmd) {
		this.cmd = cmd;
		this.args = Collections.emptyList();
		this.env = Collections.unmodifiableMap(System.getenv());
		this.cwd = null;
		this.opts = Opts.DefaultIO;
		this.okExit = Collections.unmodifiableList(Arrays.asList(new Integer[] {0}));
	}

	private Josh(Josh cpy) {
		this.cmd = cpy.cmd;
		this.args = cpy.args;
		this.env = cpy.env;
		this.cwd = cpy.cwd;
		this.opts = cpy.opts;
		this.okExit = cpy.okExit;
	}

	// treat all fields as final, i just can't be arsed to make enough copy constructors to have them actually be so.

	private String cmd;
	private List<String> args;
	private Map<String,String> env;
	private File cwd; // null will cause inherit
	private Opts opts;

	/**
	 * Exit status codes that are to be considered "successful".  If not provided, [0] is the default.
	 * (If this slice is provided, zero will -not- be considered a success code unless explicitly included.)
	 */
	private List<Integer> okExit;

	public Josh args(String... moreArgs) {
		Josh next = new Josh(this);
		List<String> argsNext = new ArrayList<String>(this.args.size()+moreArgs.length);
		argsNext.addAll(this.args);
		for (String moreArg : moreArgs)
			argsNext.add(moreArg);
		next.args = Collections.unmodifiableList(argsNext);
		return next;
	}

	public Josh env(String key, String value) {
		Josh next = new Josh(this);
		Map<String,String> envNext = new HashMap<String,String>();
		envNext.putAll(this.env);
		if (value != null)
			envNext.put(key, value);
		else
			envNext.remove(key);
		next.env = Collections.unmodifiableMap(envNext);
		return next;
	}

	public Josh env(Map<String,String> moreEnv) {
		Josh next = new Josh(this);
		Map<String,String> envNext = new HashMap<String,String>();
		envNext.putAll(this.env);
		for (Map.Entry<String,String> pair : moreEnv.entrySet())
			if (pair.getValue() != null)
				envNext.put(pair.getKey(), pair.getValue());
			else
				envNext.remove(pair.getKey());
		next.env = Collections.unmodifiableMap(envNext);
		return next;
	}

	public Josh filterEnv(Collection<String> allowedKeys) {
		Josh next = new Josh(this);
		Map<String,String> envNext = new HashMap<String,String>();
		for (Map.Entry<String,String> pair : this.env.entrySet())
			if (allowedKeys.contains(pair.getKey()))
				envNext.put(pair.getKey(), pair.getValue());
		next.env = Collections.unmodifiableMap(envNext);
		return next;
	}

	public Josh clearEnv() {
		Josh next = new Josh(this);
		next.env = Collections.emptyMap();
		return next;
	}

	public Josh cwd(File newCwd) {
		Josh next = new Josh(this);
		next.cwd = newCwd;
		return next;
	}

	public Josh opts(Opts newOpts) {
		Josh next = new Josh(this);
		next.opts = new Opts(this.opts);
		if (newOpts.in != null) next.opts.in = newOpts.in;
		if (newOpts.out != null) next.opts.out = newOpts.out;
		if (newOpts.err != null) next.opts.err = newOpts.err;
		return next;
	}

	public Josh okExit(int... newOkExit) {
		Integer[] z = new Integer[newOkExit.length];
		for (int y = 0; y < newOkExit.length; y++)
			z[y] = newOkExit[y];
		return okExit(z);
	}

	public Josh okExit(Integer... newOkExit) {
		Josh next = new Josh(this);
		next.okExit = Collections.unmodifiableList(Arrays.asList(newOkExit));
		return next;
	}

	public Future<Integer> start() throws IOException {
		String[] cmdarray = new String[args.size()+1];
		cmdarray[0] = cmd;
		for (int i = 1; i < cmdarray.length; i++)
			cmdarray[i] = args.get(i-1);

		ProcessBuilder bother = new ProcessBuilder().command(cmdarray);
		bother.environment().clear(); // fuck whoever made this interface
		bother.environment().putAll(env);
		bother.directory(cwd);
		bother.redirectInput(opts.in == System.in ? ProcessBuilder.Redirect.INHERIT : ProcessBuilder.Redirect.PIPE);
		bother.redirectOutput(opts.out == System.out ? ProcessBuilder.Redirect.INHERIT : ProcessBuilder.Redirect.PIPE);
		bother.redirectError(opts.err == System.err ? ProcessBuilder.Redirect.INHERIT : ProcessBuilder.Redirect.PIPE);
		final Process proc = bother.start();
		final Thread incopier  = (opts.in  != System.in ) ? iocopy(opts.in, proc.getOutputStream()) : null;
		final Thread outcopier = (opts.out != System.out) ? iocopy(proc.getInputStream(), opts.out) : null;
		final Thread errcopier = (opts.err != System.err) ? iocopy(proc.getErrorStream(), opts.err) : null;

		FutureTask<Integer> answer = new FutureTask<Integer>(new Callable<Integer>() {
			public Integer call() throws Exception {
				int exitCode = proc.waitFor();
				if (incopier  != null) incopier.join();
				if (outcopier != null) outcopier.join();
				if (errcopier != null) errcopier.join();
				if (!okExit.contains(exitCode))
					throw new ExecutionException("executing \""+cmd+"\" returned code "+exitCode, null);
				return exitCode;
			}
		});
		new Thread(answer).start();
		return answer;
	}

	private static Thread iocopy(final InputStream in, final OutputStream out) {
		if (out instanceof Opts.ClosedOutputStream) {
			try {
				in.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			return null;
		}
		if (in instanceof Opts.ClosedInputStream) {
			try {
				out.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			return null;
		}
		Thread t = new Thread() { public void run() {
			try {
				byte[] buf = new byte[1024*8]; int k;
				while ((k = in.read(buf)) != -1) {
					out.write(buf, 0, k);
				}
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
				try {
					in.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				try {
					out.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}};
		t.start();
		return t;
	}
}

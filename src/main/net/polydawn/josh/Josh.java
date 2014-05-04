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
		this.env = Collections.emptyMap(); // may want to use null to indicate passthru.  but no, just load it, otherwise mutation is pants.
		this.opts = new Opts();
	}

	private Josh(Josh cpy) {
		this.cmd = cpy.cmd;
		this.args = cpy.args;
		this.env = cpy.env;
		this.opts = cpy.opts;
	}

	// treat all fields as final, i just can't be arsed to make enough copy constructors to have them actually be so.

	private String cmd;
	private List<String> args;
	private Map<String,String> env;
	private Opts opts;

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

	public Josh clearEnv() {
		Josh next = new Josh(this);
		next.env = Collections.emptyMap();
		return next;
	}

	public Future<Integer> start() throws IOException {
		String[] cmdarray = new String[args.size()+1];
		cmdarray[0] = cmd;
		for (int i = 1; i < cmdarray.length; i++)
			cmdarray[i] = args.get(i-1);
		String[] envp = new String[env.size()];
		int i = 0;
		for (Map.Entry<String,String> pair : env.entrySet())
			envp[i++] = pair.getKey()+"="+pair.getValue();
		final Process proc = Runtime.getRuntime().exec(cmdarray, envp, opts.cwd);
		// going through a ProcessBuilder will enable some cheats that will probably make output to a file faster (fewer memcpy).
		// but it doesn't *actually* let you connect the real system (glhf getting a tty through).
		// and it won't help a bit for in-process.  you still have to launch a bunch of shitty threads to do shitty blocking io.
		// so fuck that.
		if (opts.in != null) iocopy(opts.in, proc.getOutputStream());
		if (opts.out != null) iocopy(proc.getInputStream(), opts.out);
		if (opts.err != null) iocopy(proc.getErrorStream(), opts.err);
		FutureTask<Integer> answer = new FutureTask<Integer>(new Callable<Integer>() {
			public Integer call() throws Exception {
				return proc.waitFor();
			}
		});
		new Thread(answer).start();
		return answer;
	}

	private static void iocopy(final InputStream in, final OutputStream out) {
		if (out instanceof Opts.ClosedOutputStream) {
			try {
				in.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			return;
		}
		new Thread() { public void run() {
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
			}
		}}.start();
	}
}

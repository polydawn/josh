josh
====

A simple-minded API for exec'ing in java.

Avoid all the futz with connecting your inputstreams to your outputstreams.
Get error handling by default instead of return codes (you forgot to check them, admit it).
Skip over miles of boilerplate.
Generally be a happier person.

Example:

```java
	List<String> countedObjects = new ArrayList<>();
	new Josh("git")
		.args("count-objects", "-v")
		.cwd(new File("thingy/.git/"))
		.env("ENV_VAR",	"so important")
		.opts(new Opts().out(countedObjects))
		.opts(new Opts().in_null())
		.okExit(0, 12, 47)
		.start()
		.get();
	System.out.println(
		"the first line of output from the subcommand was: "+
		countedObjects.get(0)
	);
```

The example shows:

1.  executing git
2.  giving it some arguments
3.  setting a working directory
4.  adding an environment variable
5.  collecting stdout by lines into a list of strings
6.  setting stdin to /dev/null
7.  (since we didn't override it, stderr is redirected to the same stderr as the main process)
8.  whitelisting acceptable exit codes
9.  launching it
10. waiting for it to complete

Waiting for the process returns an Integer containing the exit code from subprocess.
By default, if the exit code is non-zero, an exception is thrown.
If you need to handle other exit codes, you can provide a list of okay codes.

After every call, a new Josh object is returned.
(Each Josh object is immutable.)
So, a series of similar commands can be templated like this:

```java
	Josh ll = new Josh("ls").args("-la");
	ll.args("dir1").start().get();
	ll.args("dir2").start().get();
```

Any kind of collection can be used for input and output of strings and bytes.
Simple applications set up their input and output, call the command, wait for return, and go about their business -- like the example did with a `List<String>`.
Fancier applications that want to pipeline parallel processing can drop in a `ConcurrentLinkedQueue` and parallel the day away.

Depends on a java runtime version >= 1.7.  No other external dependencies.

Inspired by https://github.com/amoffat/sh/ and https://github.com/polydawn/pogo/tree/master/gosh/ .

No warranty implied.  Do not place in closed boxes with cats.  May eat your homework.  Is not web scale.  Artisanally crafted with venom and scorn.  Coroutine free.



# Fix for HotSpot deep stack problem

Java HotSpot C2 compiler has a problem with deeply recursive functions. 
For a discussion of the problem see https://codeforces.com/blog/entry/67341

> This discussion mention a workaround using `-XX:TieredStopAtLevel=1` option to use only C1 compiler. 
> However, this option significantly slows down computational code, because C1 compiler is much
> worse at optimizing code than C2 compiler.

This project introduces a workaround that does works with HotSpot C2 compiler.

**Step 1.** Compiled classes need to be preprocessed with this `HotFix` tool.

```bash
java -jar HotFix-1.0.jar <classes-directory> <main-class-name> [-q]
```

> The `-q` option is to make it quiet, without writing anything to the console.

**Step 2.** Run the resulting code with `-XX:-UseInterpreter` option.

```bash
java -XX:-UseInterpreter -cp <classes-directory> <main-class-name>

```
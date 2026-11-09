package exp01;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Measurement infrastructure for Assignment 1 (The Bubble Algorithm Library).
 *
 * <p>Design: full-factorial (algorithm x input type x array size), every condition measured
 * {@code reps} times, trials executed in a randomized order after a JIT warm-up. The dependent
 * variable is the wall-clock time of one {@code sort()} call in nanoseconds (ratio scale),
 * measured with {@link System#nanoTime()}.</p>
 *
 * <pre>
 *   java -Xms1g -Xmx1g -cp out exp01.Benchmark --sizes 100,1000,5000,10000 --reps 30 --warmup 10 --seed 42 --out data/results.csv
 * </pre>
 */
public final class Benchmark {

    /** Levels of the "algorithm" independent variable. */
    private static final Map<String, Supplier<Sorter<Integer>>> ALGORITHMS = new LinkedHashMap<>();

    static {
        ALGORITHMS.put("BubbleSortPassPerItem", BubbleSortPassPerItem::new);
        ALGORITHMS.put("BubbleSortUntilNoChange", BubbleSortUntilNoChange::new);
        ALGORITHMS.put("BubbleSortWhileNeeded", BubbleSortWhileNeeded::new);
    }

    /** Prevents the JIT from treating the sorts as dead code. */
    private static long blackhole;

    private Benchmark() {
    }

    /** One planned measurement: a condition plus a repetition index. */
    private static final class Trial {
        final String algorithm;
        final InputGenerator.InputType type;
        final int size;
        final int rep;

        Trial(final String algorithm, final InputGenerator.InputType type, final int size, final int rep) {
            this.algorithm = algorithm;
            this.type = type;
            this.size = size;
            this.rep = rep;
        }
    }

    public static void main(final String[] args) throws IOException {
        // ---- 1. Parse parameters (all of them are reported in the report) ------------------
        int[] sizes = {100, 1_000, 5_000, 10_000};
        int reps = 30;
        int warmup = 10;
        long seed = 42L;
        String out = "data/results.csv";
        for (int i = 0; i < args.length - 1; i += 2) {
            switch (args[i]) {
                case "--sizes": sizes = Arrays.stream(args[i + 1].split(",")).mapToInt(Integer::parseInt).toArray(); break;
                case "--reps": reps = Integer.parseInt(args[i + 1]); break;
                case "--warmup": warmup = Integer.parseInt(args[i + 1]); break;
                case "--seed": seed = Long.parseLong(args[i + 1]); break;
                case "--out": out = args[i + 1]; break;
                default: throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }

        // ---- 2. Warm-up: let the JIT compile every algorithm before we measure ---------------
        System.out.println("Warm-up (" + warmup + " rounds, not recorded)...");
        for (int w = 0; w < warmup; w++) {
            for (final Supplier<Sorter<Integer>> s : ALGORITHMS.values()) {
                for (final InputGenerator.InputType t : InputGenerator.InputType.values()) {
                    final Integer[] a = InputGenerator.generate(t, 2_000, seed + 999, w);
                    s.get().sort(a);
                    blackhole += a[0];
                }
            }
        }

        // ---- 3. Plan all trials and shuffle them (randomized run order) ----------------------
        // Randomizing the order spreads "time threats" (thermal throttling, background jobs...)
        // evenly over all conditions instead of hitting one algorithm only.
        final List<Trial> trials = new ArrayList<>();
        for (final String alg : ALGORITHMS.keySet()) {
            for (final InputGenerator.InputType t : InputGenerator.InputType.values()) {
                for (final int n : sizes) {
                    for (int r = 0; r < reps; r++) {
                        trials.add(new Trial(alg, t, n, r));
                    }
                }
            }
        }
        Collections.shuffle(trials, new Random(seed));

        // ---- 4. Run the trials and write one CSV row per measurement -------------------------
        final Path outPath = Paths.get(out);
        if (outPath.getParent() != null) {
            Files.createDirectories(outPath.getParent());
        }
        try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(outPath))) {
            csv.println("run_order,algorithm,input_type,size,repetition,time_ns");
            int order = 0;
            for (final Trial tr : trials) {
                // Preparation is OUTSIDE the timed region: input creation, object creation, GC.
                final Integer[] data = InputGenerator.generate(tr.type, tr.size, seed, tr.rep);
                final Sorter<Integer> sorter = ALGORITHMS.get(tr.algorithm).get();
                System.gc(); // reduce the chance of a GC pause inside the timed region

                final long start = System.nanoTime();
                sorter.sort(data);
                final long end = System.nanoTime();

                // Correctness check (also outside timing): a wrong sort would invalidate the data.
                for (int i = 1; i < data.length; i++) {
                    if (data[i - 1] > data[i]) {
                        throw new IllegalStateException(tr.algorithm + " did not sort the array");
                    }
                }
                blackhole += data[data.length / 2];

                csv.printf("%d,%s,%s,%d,%d,%d%n", order++, tr.algorithm, tr.type, tr.size, tr.rep, end - start);
                if (order % 200 == 0) {
                    System.out.printf("  %d / %d trials done%n", order, trials.size());
                }
            }
        }

        // ---- 5. Record the environment (apparatus section of the report) ---------------------
        final Path envPath = outPath.resolveSibling("environment.txt");
        try (PrintWriter env = new PrintWriter(Files.newBufferedWriter(envPath))) {
            env.println("timestamp: " + Instant.now());
            env.println("java.version: " + System.getProperty("java.version"));
            env.println("java.vm.name: " + System.getProperty("java.vm.name"));
            env.println("os: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                    + " " + System.getProperty("os.arch"));
            env.println("available processors: " + Runtime.getRuntime().availableProcessors());
            env.println("max heap (MB): " + Runtime.getRuntime().maxMemory() / (1024 * 1024));
            env.println("jvm arguments: " + ManagementFactory.getRuntimeMXBean().getInputArguments());
            env.println("sizes: " + Arrays.toString(sizes) + ", reps: " + reps + ", warmup: " + warmup
                    + ", seed: " + seed);
        }
        System.out.println("Done. Results in " + outPath + " (blackhole " + blackhole + ")");
    }
}
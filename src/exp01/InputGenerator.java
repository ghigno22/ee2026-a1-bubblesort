package exp01;

import java.util.Random;

/**
 * Creates the arrays that the sorting algorithms are asked to sort.
 *
 * <p>The input type is an independent variable of the experiment. Every array is generated
 * deterministically from (seed, type, size, repetition), so for a given repetition all three
 * algorithms sort exactly the same data. This keeps the input a controlled variable when we
 * compare algorithms.</p>
 */
public final class InputGenerator {

    /** The levels of the "input type" independent variable. */
    public enum InputType {
        /** Already in ascending order (best case for algorithms that can stop early). */
        SORTED,
        /** Descending order (worst case: every comparison causes a swap). */
        REVERSED,
        /** Uniformly random integers (the "typical" case). */
        RANDOM,
        /** Ascending order with 1% of the positions randomly swapped. */
        NEARLY_SORTED
    }

    private InputGenerator() {
    }

    /**
     * Builds a new array of the given type and size.
     *
     * @param type       input type (level of the independent variable)
     * @param size       number of elements (level of the independent variable)
     * @param seed       global experiment seed (reported in the report for reproducibility)
     * @param repetition repetition index; different repetitions get different random data
     * @return a fresh array that can be sorted in place
     */
    public static Integer[] generate(final InputType type, final int size, final long seed, final int repetition) {
        // One RNG per (type, size, repetition): reproducible and identical for every algorithm.
        final Random rnd = new Random(seed * 1_000_003L + type.ordinal() * 10_007L + size * 31L + repetition);
        final Integer[] a = new Integer[size];
        switch (type) {
            case SORTED:
                for (int i = 0; i < size; i++) {
                    a[i] = i;
                }
                break;
            case REVERSED:
                for (int i = 0; i < size; i++) {
                    a[i] = size - i;
                }
                break;
            case RANDOM:
                for (int i = 0; i < size; i++) {
                    a[i] = rnd.nextInt();
                }
                break;
            case NEARLY_SORTED:
                for (int i = 0; i < size; i++) {
                    a[i] = i;
                }
                final int swaps = Math.max(1, size / 100);
                for (int s = 0; s < swaps; s++) {
                    final int i = rnd.nextInt(size);
                    final int j = rnd.nextInt(size);
                    final Integer tmp = a[i];
                    a[i] = a[j];
                    a[j] = tmp;
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown input type " + type);
        }
        return a;
    }
}
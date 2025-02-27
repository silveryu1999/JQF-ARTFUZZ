import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.InRange;
import com.pholser.junit.quickcheck.generator.Size;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

/**
 * @author Rohan Padhye
 */
public class CacheRequestsGenerator extends Generator<int[]> {

    private Size size;
    private InRange range;

    public CacheRequestsGenerator() {
        super(int[].class);
    }

    public void configure(Size size) {
        this.size = size;
    }

    public void configure(InRange range) {
        this.range = range;
    }

    @Override
    public int[] generate(SourceOfRandomness r, GenerationStatus generationStatus) {
        int minSize = size != null ? size.min() : 0;
        int maxSize = size != null ? size.max() : Integer.MAX_VALUE;
        int size = r.nextInt(minSize, maxSize);
        int[] requests = new int[size];

        int minRange = range != null ? range.minInt() : 0;
        int maxRange = range != null ? range.maxInt() : 1024;

        for (int i = 0; i < size; i++) {
            int request;
            // Maybe re-use
            if (i > 0 && r.nextBoolean()) {
                int idx = r.nextInt(0, i);
                request = requests[idx];
            } else {
                request = r.nextInt(minRange, maxRange);
            }
            requests[i] = request;
        }

        return requests;
    }
}
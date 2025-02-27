import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.Size;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

/**
 * @author Rohan Padhye
 */
public abstract class AbstractKaitaiGenerator extends Generator<InputStream> {

    public AbstractKaitaiGenerator() {
        super(InputStream.class);
    }

    private int capacity = Integer.MAX_VALUE;
    protected ByteBuffer buf;

    @SuppressWarnings("unused") // invoked by junit-quickcheck for @Size annotation
    public void configure(Size size) {
        this.capacity = size.max();
    }

    @Override
    public InputStream generate(SourceOfRandomness random, GenerationStatus status) {
        buf = ByteBuffer.allocate(this.capacity);
        try {
            // Populate byte buffer
            populate(random);

        } catch (BufferOverflowException e) {
            // throw new AssumptionViolatedException("Generated input is too large", e);
        }

        // Return the bytes as an inputstream
        int len = buf.position();
        buf.rewind();
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new ByteArrayInputStream(bytes);
    }


    abstract protected void populate(SourceOfRandomness random) throws BufferOverflowException;
}
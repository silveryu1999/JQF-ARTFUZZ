import com.pholser.junit.quickcheck.generator.java.lang.AbstractStringGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

/**
 * @author Rohan Padhye
 */
public class AlphaStringGenerator extends AbstractStringGenerator {

    @Override
    protected int nextCodePoint(SourceOfRandomness sourceOfRandomness) {
        return sourceOfRandomness.nextByte((byte) 'a', (byte) 'z');
    }

    @Override
    protected boolean codePointInRange(int i) {
        return i >= 'a' && i <= 'z';
    }
}
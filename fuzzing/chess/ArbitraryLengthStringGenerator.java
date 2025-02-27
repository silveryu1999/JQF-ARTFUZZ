import java.io.IOException;
import java.io.InputStream;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.Size;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import edu.berkeley.cs.jqf.fuzz.junit.quickcheck.InputStreamGenerator;

/**
 * @author Rohan Padhye
 */
public class ArbitraryLengthStringGenerator extends Generator<String> {

    private int maxSize = 4096;

    public ArbitraryLengthStringGenerator() {
        super(String.class);
    }

    public void configure(Size size) {
        maxSize = size.max();
    }


    @Override
    public String generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus) {
        InputStream in = new InputStreamGenerator().generate(sourceOfRandomness, generationStatus);
        byte[] bytes = new byte[maxSize];
        try {
            int len = in.read(bytes);
            if (len <= 0) {
                return "";
            } else {
                return new String(bytes, 0, len);
            }
        } catch (IOException e) {
            throw new RuntimeException("Should not get I/O exception when using generated InputStream", e);
        }

    }
}
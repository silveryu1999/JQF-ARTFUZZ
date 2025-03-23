import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

public class DictionaryBackedStringGenerator extends Generator<String> {

    private final List<String> dictionary;
    private Generator<String> fallback;

    public DictionaryBackedStringGenerator(String source, Generator<String> fallback) throws IOException {
        super(String.class);
        this.dictionary = new ArrayList<>();
        this.fallback = fallback;

        // Read dictionary words
        try (InputStream in = this.getClass().getClassLoader().getResourceAsStream(source)) {
            if (in == null) {
                throw new FileNotFoundException("Dictionary file not found: " + source);
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String item;
            while ((item = br.readLine()) != null) {
                dictionary.add(item);
            }
        }
    }

    @Override
    public String generate(SourceOfRandomness random, GenerationStatus status) {
        if (true) {
            int choice = random.nextInt(dictionary.size());
            return dictionary.get(choice);
        } else {
            if (fallback == null) {
                fallback = gen().type(String.class);
            }
            return fallback.generate(random, status);
        }
    }

}
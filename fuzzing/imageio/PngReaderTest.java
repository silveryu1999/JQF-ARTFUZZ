import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.generator.Size;
//import edu.berkeley.cs.jqf.examples.kaitai.PngKaitaiGenerator;
import edu.berkeley.cs.jqf.fuzz.Fuzz;
import edu.berkeley.cs.jqf.fuzz.JQF;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@RunWith(JQF.class)
public class PngReaderTest {

    @BeforeClass
    public static void disableCaching() {
        // Disable disk-caching as it slows down fuzzing
        // and makes image reads non-idempotent
        ImageIO.setUseCache(false);
    }

    private ImageReader reader;

    @Before
    public void setUp() {
        this.reader = ImageIO.getImageReadersByFormatName("png").next();
    }

    @After
    public void tearDown() {
        this.reader.dispose();
    }

    @Fuzz
    public void read(ImageInputStream input) throws IOException {
        // Decode image from input stream
        reader.setInput(input);
        // Bound dimensions
        Assume.assumeTrue(reader.getHeight(0) < 1024);
        Assume.assumeTrue(reader.getWidth(0) < 1024);
        // Parse PNG
        reader.read(0);
    }

    @Fuzz
    public void getWidth(ImageInputStream input) throws IOException {
        // Decode image from input stream
        reader.setInput(input);
        int width = reader.getWidth(0);
        System.out.println(width);
    }

    @Fuzz
    public void getHeight(ImageInputStream input) throws IOException {
        // Decode image from input stream
        reader.setInput(input);
        int height = reader.getHeight(0);
        System.out.println(height);
    }

    @Fuzz
    public void debugKaitai(@From(PngKaitaiGenerator.class) @Size(max = 256) InputStream input)  {
        try (FileOutputStream out = new FileOutputStream("kaitai.png")) {
            int val;
            while ((val = input.read()) != -1) {
                out.write(val);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Fuzz
    public void fuzzValidMetadata(@From(PngKaitaiGenerator.class) @Size(max = 256) InputStream input)  {
        // Decode image from input stream
        try {
            reader.setInput(ImageIO.createImageInputStream(input));
            reader.getImageMetadata(0);
        } catch (IOException e) {
            Assume.assumeNoException(e);
        }

    }

    @Fuzz
    public void fuzzValidImage(@From(PngKaitaiGenerator.class) @Size(max = 2048) InputStream input)  {
        // Decode image from input stream
        try {
            reader.setInput(ImageIO.createImageInputStream(input));
            reader.getImageMetadata(0);
            Assume.assumeTrue(reader.getHeight(0) < 1024);
            Assume.assumeTrue(reader.getWidth(0)  < 1024);
        } catch (IOException e) {
            Assume.assumeNoException(e);
        }

    }

    @Fuzz
    public void readUsingKaitai(@From(PngKaitaiGenerator.class) @Size(max = 1024) InputStream input) throws IOException {
        // Decode image from input stream
        reader.setInput(ImageIO.createImageInputStream(input));
        // Bound dimensions
        Assume.assumeTrue(reader.getHeight(0) < 1024);
        Assume.assumeTrue(reader.getWidth(0) < 1024);
        // Parse PNG
        reader.read(0);
    }

}
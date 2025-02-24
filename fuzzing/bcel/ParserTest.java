import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.pholser.junit.quickcheck.From;
import edu.berkeley.cs.jqf.fuzz.Fuzz;
import edu.berkeley.cs.jqf.fuzz.JQF;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.ClassFormatException;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.verifier.StatelessVerifierFactory;
import org.apache.bcel.verifier.VerificationResult;
import org.apache.bcel.verifier.Verifier;
import org.junit.Assume;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeThat;

@RunWith(JQF.class)
public class ParserTest {

    @Fuzz
    public void testWithInputStream(InputStream inputStream) throws IOException {
        JavaClass clazz;
        try {
            clazz = new ClassParser(inputStream, "Hello.class").parse();
        } catch (ClassFormatException e) {
            // ClassFormatException thrown by the parser is just invalid input
            Assume.assumeNoException(e);
            return;
        }

        // Any non-IOException thrown here should be marked a failure
        // (including ClassFormatException)
        verifyJavaClass(clazz);
    }

    @Fuzz
    public void testWithGenerator(@From(JavaClassGenerator.class) JavaClass javaClass) throws IOException {

        try {
            // Dump the javaclass to a byte stream and get an input pipe
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javaClass.dump(out);

            ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
            testWithInputStream(in);
        } catch (ClassFormatException e) {
            throw e;
        }
    }


    @Fuzz
    public void verifyJavaClass(@From(JavaClassGenerator.class) JavaClass javaClass) throws IOException {
        try {
            Repository.addClass(javaClass);
            Verifier verifier = StatelessVerifierFactory.getVerifier(javaClass.getClassName());
            VerificationResult result;
            result = verifier.doPass1();
            assumeThat(result.getMessage(), result.getStatus(), is(VerificationResult.VERIFIED_OK));
            result = verifier.doPass2();
            assumeThat(result.getMessage(), result.getStatus(), is(VerificationResult.VERIFIED_OK));
            for (int i = 0; i < javaClass.getMethods().length; i++) {
                result = verifier.doPass3a(i);
                assumeThat(result.getMessage(), result.getStatus(), is(VerificationResult.VERIFIED_OK));
            }
        } finally {
            Repository.clearCache();
        }
    }

}
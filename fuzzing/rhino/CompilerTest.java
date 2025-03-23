import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.pholser.junit.quickcheck.From;
//import edu.berkeley.cs.jqf.examples.common.AsciiStringGenerator;
//import edu.berkeley.cs.jqf.examples.js.JavaScriptCodeGenerator;
import edu.berkeley.cs.jqf.fuzz.Fuzz;
import edu.berkeley.cs.jqf.fuzz.JQF;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Script;

@RunWith(JQF.class)
public class CompilerTest {

    private Context context;

    @Before
    public void initContext() {
        context = Context.enter();
    }

    @After
    public void exitContext() {
        context.exit();
    }

    @Fuzz
    public void testWithString(@From(AsciiStringGenerator.class) String input) {
        try {
            Script script = context.compileString(input, "input", 0, null);
        } catch (EvaluatorException e) {
            Assume.assumeNoException(e);
        }

    }

    @Fuzz
    public void debugWithString(@From(AsciiStringGenerator.class) String code) {
        System.out.println("\nInput:  " + code);
        testWithString(code);
        System.out.println("Success!");
    }

    @Test
    public void smallTest() {
        testWithString("x = 3 + 4");
        testWithString("x <<= undefined");
    }

    @Fuzz
    public void testWithInputStream(InputStream in) throws IOException {
        try {
            Script script = context.compileReader(new InputStreamReader(in), "input", 0, null);
        } catch (EvaluatorException e) {
            Assume.assumeNoException(e);
        }
    }

    @Fuzz
    public void debugWithInputStream(InputStream in) throws IOException {
        String input = IOUtils.toString(in, StandardCharsets.UTF_8);
        debugWithString(input);
    }

    @Fuzz
    public void testWithGenerator(@From(JavaScriptCodeGenerator.class) String code) {
        testWithString(code);
    }

    @Fuzz
    public void debugWithGenerator(@From(JavaScriptCodeGenerator.class) String code) {
        debugWithString(code);
    }



}
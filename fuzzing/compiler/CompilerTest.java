import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.LogManager;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;
import com.pholser.junit.quickcheck.From;
//import edu.berkeley.cs.jqf.examples.common.AsciiStringGenerator;
//import edu.berkeley.cs.jqf.examples.js.JavaScriptCodeGenerator;
import edu.berkeley.cs.jqf.fuzz.Fuzz;
import edu.berkeley.cs.jqf.fuzz.JQF;
import org.apache.commons.io.IOUtils;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JQF.class)
public class CompilerTest {

    static {
        // Disable all logging by Closure passes
        LogManager.getLogManager().reset();
    }

    private Compiler compiler = new Compiler(new PrintStream(new ByteArrayOutputStream(), false));
    private CompilerOptions options = new CompilerOptions();
    private SourceFile externs = SourceFile.fromCode("externs", "");

    @Before
    public void initCompiler() {
        // Don't use threads
        compiler.disableThreads();
        // Don't print things
        options.setPrintConfig(false);
        // Enable all safe optimizations
        CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    }

    private void doCompile(SourceFile input) {
        Result result = compiler.compile(externs, input, options);
        Assume.assumeTrue(result.success);
    }

    @Fuzz
    public void testWithString(@From(AsciiStringGenerator.class) String code) {
        SourceFile input = SourceFile.fromCode("input", code);
        doCompile(input);
    }

    @Fuzz
    public void debugWithString(@From(AsciiStringGenerator.class) String code) {
        System.out.println("\nInput:  " + code);
        testWithString(code);
        System.out.println("Output: " + compiler.toSource());
    }

    @Test
    public void smallTest() {
        debugWithString("x <<= Infinity");
    }

    @Fuzz
    public void testWithInputStream(InputStream in) throws IOException {
        SourceFile input = SourceFile.fromInputStream("input", in, StandardCharsets.UTF_8);
        doCompile(input);
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
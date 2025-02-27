import chess.Situation;
import chess.format.Forsyth;
import com.pholser.junit.quickcheck.From;
//import edu.berkeley.cs.jqf.examples.common.ArbitraryLengthStringGenerator;
import edu.berkeley.cs.jqf.fuzz.Fuzz;
import edu.berkeley.cs.jqf.fuzz.JQF;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import scala.Option;

import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * @author Rohan Padhye
 */
@RunWith(JQF.class)
public class FENTest {

    public static final String INITIAL_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @Test
    public void test() {
        testWithString(INITIAL_FEN);
    }

    @Test
    public void debug() {
        debugWithString(INITIAL_FEN);
        debugWithString("Q");
    }

    private Situation parseFEN(String fen) {
        Option<Situation> situation = Forsyth.$less$less(fen);
        Assume.assumeTrue(situation.isDefined());
        return situation.get();
    }

    @Fuzz
    public void testWithString(@From(ArbitraryLengthStringGenerator.class) String fen) {
        Situation situation = parseFEN(fen);
        assumeTrue(situation.playable(true));
        assertThat(situation.moves().size(), greaterThan(0));
    }

    @Fuzz
    public void debugWithString(@From(ArbitraryLengthStringGenerator.class) String fen) {
        Situation situation = parseFEN(fen);
        System.out.println(situation.moves().values());
    }

    @Fuzz
    public void testWithGenerator(@From(FENGenerator.class) String fen) {
        testWithString(fen);
    }

    @Fuzz
    public void debugWithGenerator(@From(FENGenerator.class) String fen) {
        System.out.println(fen);//debugWithString(fen);
    }

    @Fuzz
    public void testWithInputStream(@From(ArbitraryLengthStringGenerator.class) String fen) {
        testWithString(fen);
    }
}
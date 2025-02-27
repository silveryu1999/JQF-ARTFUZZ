import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

/**
 * @author Rohan Padhye
 */
public class FENGenerator extends Generator<String> {

    public FENGenerator() {
        super(String.class);
    }

    @Override
    public String generate(SourceOfRandomness r, GenerationStatus g) {
        return String.join(" ", generateBoard(r), generateColor(r), generateCastles(r),
                generateEnPassant(r), generateHalfMoveClock(r), generateFullMoveClock(r));
    }

    private char[] pieces = { 'K', 'Q', 'R', 'B', 'N', 'P', 'k', 'q', 'r', 'b', 'n', 'p'};

    private String generateBoard(SourceOfRandomness r) {
        String[] rows = new String[8];
        for (int i = 0; i < 8; i++) {
            String row = "";
            for (int j = 0; j < 8; j++) {
                if (r.nextBoolean()) {
                    // empty square
                    int skip = r.nextInt(0, 8-j);
                    j += skip;
                    row += String.valueOf(skip+1); // Upper bound is exclusive
                } else {
                    // piece
                    row += pieces[r.nextInt(pieces.length)];
                }
            }
            rows[i] = row;
        }
        return String.join("/", rows);
    }

    private String generateColor(SourceOfRandomness r) {
        return r.nextBoolean() ? "w" : "b";
    }

    private String generateCastles(SourceOfRandomness r) {
        if (r.nextBoolean()) {
            return "-";
        }
        String castle = "";
        if (r.nextBoolean()) {
            castle += "K";
        }
        if (r.nextBoolean()) {
            castle += "Q";
        }
        if (r.nextBoolean()) {
            castle += "k";
        }
        if (r.nextBoolean()) {
            castle += "q";
        }
        if (castle.isEmpty()) {
            castle = "-";
        }
        return castle;
    }

    private String generateEnPassant(SourceOfRandomness r) {
        if (r.nextBoolean()) {
            return "-";
        }
        char x = r.nextChar('a', 'i'); // Upper-bound is exclusive
        int y =  r.nextInt(1, 9);      // Upper-bound is exclusive
        return String.valueOf(x) + String.valueOf(y);
    }

    private String generateHalfMoveClock(SourceOfRandomness r) {
        return Integer.toString(r.nextInt(0, 50));
    }

    private String generateFullMoveClock(SourceOfRandomness r) {
        return Integer.toString(r.nextInt(1, 100));
    }
}
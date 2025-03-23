package org.apache.bcel.verifier;

/**
 * A producer of Verifier instances that does not cause memory leaks.
 *
 * @author Rohan Padhye
 */
public class StatelessVerifierFactory {

    // Expose a public method for a package-private constructor
    public static Verifier getVerifier(String fqn) {
        return new Verifier(fqn);
    }
}
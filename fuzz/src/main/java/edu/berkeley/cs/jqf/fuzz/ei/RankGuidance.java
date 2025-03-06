/*
 * Copyright (c) 2017-2018 The Regents of the University of California
 * Copyright (c) 2020-2021 Rohan Padhye
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.berkeley.cs.jqf.fuzz.ei;

import edu.berkeley.cs.jqf.fuzz.guidance.Guidance;
import edu.berkeley.cs.jqf.fuzz.guidance.GuidanceException;
import edu.berkeley.cs.jqf.fuzz.guidance.Result;
import edu.berkeley.cs.jqf.fuzz.guidance.TimeoutException;
import edu.berkeley.cs.jqf.fuzz.util.*;
import edu.berkeley.cs.jqf.instrument.tracing.FastCoverageSnoop;
import edu.berkeley.cs.jqf.instrument.tracing.events.TraceEvent;
import janala.instrument.FastCoverageListener;
import org.eclipse.collections.api.iterator.IntIterator;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.lang.Math.*;

/**
 * A guidance that performs coverage-guided fuzzing using two coverage maps,
 * one for all inputs and one for valid inputs only.
 *
 * @author Rohan Padhye
 */
public class RankGuidance implements Guidance {

    /** Probability that a standard mutation sets the byte to just zero instead of a random value. */
    protected final double MUTATION_ZERO_PROBABILITY = 0.1;

    /** A pseudo-random number generator for generating fresh values. */
    protected Random random;

    /** The name of the test for display purposes. */
    protected final String testName;

    // ------------ ALGORITHM BOOKKEEPING ------------

    /** The max amount of time to run for, in milli-seconds */
    protected final long maxDurationMillis;

    /** The max number of trials to run */
    protected final long maxTrials;

    /** The number of trials completed. */
    protected long numTrials = 0;

    /** The number of failures. */
    protected long totalFailures = 0;

    /** The number of valid inputs. */
    protected long numValid = 0;

    /** The directory where fuzzing results are produced. */
    protected final File outputDirectory;

    /** The directory where interesting inputs are saved. */
    protected File savedCorpusDirectory;

    /** The directory where saved inputs are saved. */
    protected File savedFailuresDirectory;

    /** The directory where all generated inputs are logged in sub-directories (if enabled). */
    protected File allInputsDirectory;

    /** Set of saved inputs to fuzz. */
    protected ArrayList<Input> savedInputs = new ArrayList<>();

    /** Queue of seeds to fuzz. */
    protected Deque<Input> seedInputs = new ArrayDeque<>();

    /** Current input that's running -- valid after getInput() and before handleResult(). */
    protected Input<?> currentInput;

    /** Index of currentInput in the savedInputs -- valid after seeds are processed (OK if this is inaccurate). */
    protected int currentParentInputIdx = 0;

    /** Number of mutated inputs generated from currentInput. */
    protected int numChildrenGeneratedForCurrentParentInput = 0;

    /** Number of cycles completed (i.e. how many times we've reset currentParentInputIdx to 0. */
    protected int cyclesCompleted = 0;

    /** Number of favored inputs in the last cycle. */
    protected int numFavoredLastCycle = 0;

    /** Blind fuzzing -- if true then the queue is always empty. */
    protected boolean blind;

    /** Validity fuzzing -- if true then save valid inputs that increase valid coverage */
    protected boolean validityFuzzing;

    /** Number of saved inputs.
     *
     * This is usually the same as savedInputs.size(),
     * but we do not really save inputs in TOTALLY_RANDOM mode.
     */
    protected int numSavedInputs = 0;

    /** Coverage statistics for a single run. */
    protected ICoverage runCoverage = CoverageFactory.newInstance();

    /** Cumulative coverage statistics. */
    protected ICoverage totalCoverage = CoverageFactory.newInstance();

    /** Cumulative coverage for valid inputs. */
    protected ICoverage validCoverage = CoverageFactory.newInstance();

    /** The maximum number of keys covered by any single input found so far. */
    protected int maxCoverage = 0;

    /** A mapping of coverage keys to inputs that are responsible for them. */
    protected Map<Object, Input> responsibleInputs = new HashMap<>(totalCoverage.size());

    /** The set of unique failures found so far. */
    protected Set<String> uniqueFailures = new HashSet<>();

    /** save crash to specific location (should be used with EXIT_ON_CRASH) **/
    protected final String EXACT_CRASH_PATH = System.getProperty("jqf.ei.EXACT_CRASH_PATH");

    // ---------- LOGGING / STATS OUTPUT ------------

    /** Whether to print log statements to stderr (debug option; manually edit). */
    protected final boolean verbose = true;

    /** A system console, which is non-null only if STDOUT is a console. */
    protected final Console console = System.console();

    /** Time since this guidance instance was created. */
    protected final Date startTime = new Date();

    /** Time at last stats refresh. */
    protected Date lastRefreshTime = startTime;

    /** Total execs at last stats refresh. */
    protected long lastNumTrials = 0;

    /** Minimum amount of time (in millis) between two stats refreshes. */
    protected final long STATS_REFRESH_TIME_PERIOD = 300;

    /** The file where log data is written. */
    protected File logFile;

    /** The file where saved plot data is written. */
    protected File statsFile;

    /** The file where saved plot data (cases indexed and only cov++) is written. */
    protected File statsCovCasesFile;

    /** The currently executing input (for debugging purposes). */
    protected File currentInputFile;

    /** The file contianing the coverage information */
    protected File coverageFile;

    /** Use libFuzzer like output instead of AFL like stats screen (https://llvm.org/docs/LibFuzzer.html#output) **/
    protected final boolean LIBFUZZER_COMPAT_OUTPUT = Boolean.getBoolean("jqf.ei.LIBFUZZER_COMPAT_OUTPUT");

    /** Whether to hide fuzzing statistics **/
    protected final boolean QUIET_MODE = Boolean.getBoolean("jqf.ei.QUIET_MODE");

    /** Whether to store all generated inputs to disk (can get slowww!) */
    protected final boolean LOG_ALL_INPUTS = Boolean.getBoolean("jqf.ei.LOG_ALL_INPUTS");

    // ------------- TIMEOUT HANDLING ------------

    /** Timeout for an individual run. */
    protected long singleRunTimeoutMillis;

    /** Date when last run was started. */
    protected Date runStart;

    /** Number of conditional jumps since last run was started. */
    protected long branchCount;

    /** Whether to stop/exit once a crash is found. **/
    protected final boolean EXIT_ON_CRASH = Boolean.getBoolean("jqf.ei.EXIT_ON_CRASH");

    // ------------- THREAD HANDLING ------------

    /** The first thread in the application, which usually runs the test method. */
    protected Thread firstThread;

    /** Whether the application has more than one thread running coverage-instrumented code */
    protected boolean multiThreaded = false;

    // ------------- FUZZING HEURISTICS ------------

    /** Whether to save only valid inputs **/
    protected final boolean SAVE_ONLY_VALID = Boolean.getBoolean("jqf.ei.SAVE_ONLY_VALID");

    /** Max input size to generate. */
    protected final int MAX_INPUT_SIZE = Integer.getInteger("jqf.ei.MAX_INPUT_SIZE", 10240);

    /** Whether to generate EOFs when we run out of bytes in the input, instead of randomly generating new bytes. **/
    protected final boolean GENERATE_EOF_WHEN_OUT = Boolean.getBoolean("jqf.ei.GENERATE_EOF_WHEN_OUT");

    /** Baseline number of mutated children to produce from a given parent input. */
    protected final int NUM_CHILDREN_BASELINE = 50;

    /** Multiplication factor for number of children to produce for favored inputs. */
    protected final int NUM_CHILDREN_MULTIPLIER_FAVORED = 20;

    /** Mean number of mutations to perform in each round. */
    protected final double MEAN_MUTATION_COUNT = 8.0;

    /** Mean number of contiguous bytes to mutate in each mutation. */
    protected final double MEAN_MUTATION_SIZE = 4.0; // Bytes

    /** Whether to save inputs that only add new coverage bits (but no new responsibilities). */
    protected final boolean DISABLE_SAVE_NEW_COUNTS = Boolean.getBoolean("jqf.ei.DISABLE_SAVE_NEW_COUNTS");

    /** Whether to steal responsibility from old inputs (this increases computation cost). */
    protected final boolean STEAL_RESPONSIBILITY = Boolean.getBoolean("jqf.ei.STEAL_RESPONSIBILITY");

    /** EOF count. */
    protected long EOFcount = 0;

    /** Set of new seeds (produced by current parent input). */
    protected ArrayList<Input> newSeedsFromCurrentParent = new ArrayList<>();

    /** Set of new valid seeds (produced by current parent input). */
    protected ArrayList<Input> newValidSeedsFromCurrentParent = new ArrayList<>();

    /** Set of new invalid seeds (produced by current parent input). */
    protected ArrayList<Input> newInvalidSeedsFromCurrentParent = new ArrayList<>();

    /** Set of saved valid seeds **/
    protected ArrayList<Input> savedValidInputs = new ArrayList<>();

    /** Set of saved invalid seeds **/
    protected ArrayList<Input> savedInvalidInputs = new ArrayList<>();

    /** Number of new unique failures. **/
    protected int newUniqueFailures = 0;

    /** Seconds for updating seeds distances **/
    protected double lastUpdatingTime = 0.0;

    /** Seconds for sorting seeds **/
    protected double lastSortingTime = 0.0;

    /** Avg seconds for calculating Euclidean seeds distances **/
    protected double avgCalEuclideanTime = 0.0;

    /** Last counts for calculating Euclidean seeds distances **/
    protected int EuclideanCount = 0;

    /** Whether to use custom mutation. **/
    protected final boolean CUSTOM_MUTATION = Boolean.getBoolean("jqf.ei.CUSTOM_MUTATION");

    /** Whether to use custom mutation on time and size. **/
    protected final boolean CUSTOM_MUTATION_TIME_AND_SIZE = Boolean.getBoolean("jqf.ei.CUSTOM_MUTATION_TIME_AND_SIZE");

    /** Whether to use custom energy assignment **/
    protected final boolean CUSTOM_ENERGY = Boolean.getBoolean("jqf.ei.CUSTOM_ENERGY");

    /** Whether to select the valid seeds first **/
    protected final boolean VALID_SEED_FIRST = Boolean.getBoolean("jqf.ei.VALID_SEED_FIRST");

    /** Whether to use crossover **/
    protected final boolean USE_CROSSOVER = Boolean.getBoolean("jqf.ei.USE_CROSSOVER");

    /** Whether to save input files **/
    protected final boolean SAVE_INPUT_FILES = Boolean.getBoolean("jqf.ei.SAVE_INPUT_FILES");

    /** Whether to use weight distance **/
    protected final boolean USE_WEIGHT_DISTANCE = Boolean.getBoolean("jqf.ei.USE_WEIGHT_DISTANCE");

    /** Whether to use Hamming distance **/
    protected final boolean USE_HAMMING_DISTANCE = Boolean.getBoolean("jqf.ei.USE_HAMMING_DISTANCE");

    /** Probability of crossover **/
    /** from 0 to 100 ()**/
    protected final int CROSSOVER_PROBABILITY = Integer.getInteger("jqf.ei.CROSSOVER_PROBABILITY", 25);

    /** Probability of crossover **/
    protected final double crossoverRate = (CROSSOVER_PROBABILITY * 1.0 / 100.0);

    /** Current mean distance to invalid seeds **/
    protected double meanInvalidDis = 0.0;

    /** Current mean distance to valid seeds **/
    protected double meanValidDis = 0.0;

    /** Overhead of seed updating **/
    protected long seedUpdatingMilliseconds = 0;

    /** Overhead of seed sorting **/
    protected long seedSortingMilliseconds = 0;

    /** Probability that a standard mutation sets the byte to just one instead of zero. */
    protected final double MUTATION_ONE_PROBABILITY = 0.05;

    /** Comparator of input **/
    protected Comparator<Input> inputComparator = new Comparator<Input>() {
        @Override
        public int compare(Input o1, Input o2) {
            if (o1.isValid() && o2.isValid()) {
                return Double.compare(o2.minToValidSeedsAtCov, o1.minToValidSeedsAtCov);
            } else if (!o1.isValid() && !o2.isValid()) {
                return Double.compare(o2.minToInvalidSeedsAtCov, o1.minToInvalidSeedsAtCov);
            } else if (o1.isValid() && !o2.isValid()) {
                return -1;
            } else if (!o1.isValid() && o2.isValid()) {
                return 1;
            }
            return 0;
        }
    };

    /** Set of hashes of all valid paths executed so far. */
    protected IntHashSet uniqueValidPaths = new IntHashSet();

    /** Set of hashes of all invalid paths executed so far. */
    protected IntHashSet uniqueInvalidPaths = new IntHashSet();

    /** Cumulative unique traces count for valid inputs. */
    protected ICoverage validUniqueTracesCount = CoverageFactory.newInstance();

    /** Cumulative unique traces count for invalid inputs. */
    protected ICoverage invalidUniqueTracesCount = CoverageFactory.newInstance();

    /** Epsilon for weight. */
    protected final double EPSILON = 0.01;

    /**
     * Creates a new Zest guidance instance with optional duration,
     * optional trial limit, and possibly deterministic PRNG.
     *
     * @param testName the name of test to display on the status screen
     * @param duration the amount of time to run fuzzing for, where
     *                 {@code null} indicates unlimited time.
     * @param trials   the number of trials for which to run fuzzing, where
     *                 {@code null} indicates unlimited trials.
     * @param outputDirectory the directory where fuzzing results will be written
     * @param sourceOfRandomness      a pseudo-random number generator
     * @throws IOException if the output directory could not be prepared
     */
    public RankGuidance(String testName, Duration duration, Long trials, File outputDirectory, Random sourceOfRandomness) throws IOException {
        this.random = sourceOfRandomness;
        this.testName = testName;

        /** Valid time durations are non-empty strings in the format [Nh][Nm][Ns], such as "60s" or "2h30m". **/
        String time = System.getProperty("jqf.ei.TIME_LIMIT");
        Duration targetDuration = null;
        if (time != null && !time.isEmpty()) {
            try {
                targetDuration = Duration.parse("PT"+time);
            } catch (DateTimeParseException e) {
                targetDuration = null;
            }
        }
        this.maxDurationMillis = duration != null ? duration.toMillis() : (targetDuration != null ? targetDuration.toMillis() : Long.MAX_VALUE);

        Long trialsLimit = Long.getLong("jqf.ei.TRIAL_LIMIT", 0L);
        this.maxTrials = trials != null ? trials : (trialsLimit != 0L ? trialsLimit : Long.MAX_VALUE);
        this.outputDirectory = outputDirectory;
        this.blind = Boolean.getBoolean("jqf.ei.TOTALLY_RANDOM");
        this.validityFuzzing = !Boolean.getBoolean("jqf.ei.DISABLE_VALIDITY_FUZZING");
        prepareOutputDirectory();

        if(this.runCoverage instanceof FastCoverageListener){
            FastCoverageSnoop.setFastCoverageListener((FastCoverageListener) this.runCoverage);
        }

        // Try to parse the single-run timeout
        String timeout = System.getProperty("jqf.ei.TIMEOUT");
        if (timeout != null && !timeout.isEmpty()) {
            try {
                // Interpret the timeout as milliseconds (just like `afl-fuzz -t`)
                this.singleRunTimeoutMillis = Long.parseLong(timeout);
            } catch (NumberFormatException e1) {
                throw new IllegalArgumentException("Invalid timeout duration: " + timeout);
            }
        }
    }

    /**
     * Creates a new Zest guidance instance with seed input files and optional
     * duration, optional trial limit, an possibly deterministic PRNG.
     *
     * @param testName the name of test to display on the status screen
     * @param duration the amount of time to run fuzzing for, where
     *                 {@code null} indicates unlimited time.
     * @param trials   the number of trials for which to run fuzzing, where
     *                 {@code null} indicates unlimited trials.
     * @param outputDirectory the directory where fuzzing results will be written
     * @param seedInputFiles one or more input files to be used as initial inputs
     * @param sourceOfRandomness      a pseudo-random number generator
     * @throws IOException if the output directory could not be prepared
     */
    public RankGuidance(String testName, Duration duration, Long trials, File outputDirectory, File[] seedInputFiles, Random sourceOfRandomness) throws IOException {
        this(testName, duration, trials, outputDirectory, sourceOfRandomness);
        if (seedInputFiles != null) {
            for (File seedInputFile : seedInputFiles) {
                seedInputs.add(new SeedInput(seedInputFile));
            }
        }
    }

    /**
     * Creates a new Zest guidance instance with seed input directory and optional
     * duration, optional trial limit, an possibly deterministic PRNG.
     *
     * @param testName the name of test to display on the status screen
     * @param duration the amount of time to run fuzzing for, where
     *                 {@code null} indicates unlimited time.
     * @param trials   the number of trials for which to run fuzzing, where
     *                 {@code null} indicates unlimited trials.
     * @param outputDirectory the directory where fuzzing results will be written
     * @param seedInputDir the directory containing one or more input files to be used as initial inputs
     * @param sourceOfRandomness      a pseudo-random number generator
     * @throws IOException if the output directory could not be prepared
     */
    public RankGuidance(String testName, Duration duration, Long trials, File outputDirectory, File seedInputDir, Random sourceOfRandomness) throws IOException {
        this(testName, duration, trials, outputDirectory, IOUtils.resolveInputFileOrDirectory(seedInputDir), sourceOfRandomness);
    }

    /**
     * Creates a new Zest guidance instance with seed inputs and
     * optional duration.
     *
     * @param testName the name of test to display on the status screen
     * @param duration the amount of time to run fuzzing for, where
     *                 {@code null} indicates unlimited time.
     * @param outputDirectory the directory where fuzzing results will be written
     * @param seedInputDir the directory containing one or more input files to be used as initial inputs
     * @throws IOException if the output directory could not be prepared
     */
    public RankGuidance(String testName, Duration duration, File outputDirectory, File seedInputDir) throws IOException {
        this(testName, duration, null, outputDirectory, seedInputDir, new Random());
    }

    /**
     * Creates a new Zest guidance instance with seed inputs and
     * optional duration.
     *
     * @param testName the name of test to display on the status screen
     * @param duration the amount of time to run fuzzing for, where
     *                 {@code null} indicates unlimited time.
     * @param outputDirectory the directory where fuzzing results will be written
     * @throws IOException if the output directory could not be prepared
     */
    public RankGuidance(String testName, Duration duration, File outputDirectory) throws IOException {
        this(testName, duration, null, outputDirectory, new Random());
    }

    /**
     * Creates a new Zest guidance instance with seed inputs and
     * optional duration.
     *
     * @param testName the name of test to display on the status screen
     * @param duration the amount of time to run fuzzing for, where
     *                 {@code null} indicates unlimited time.
     * @param outputDirectory the directory where fuzzing results will be written
     * @throws IOException if the output directory could not be prepared
     */
    public RankGuidance(String testName, Duration duration, File outputDirectory, File[] seedFiles) throws IOException {
        this(testName, duration, null, outputDirectory, seedFiles, new Random());
    }

    private void prepareOutputDirectory() throws IOException {
        // Create the output directory if it does not exist
        IOUtils.createDirectory(outputDirectory);

        // Name files and directories after AFL
        this.savedCorpusDirectory = IOUtils.createDirectory(outputDirectory, "corpus");
        this.savedFailuresDirectory = IOUtils.createDirectory(outputDirectory, "failures");
        if (LOG_ALL_INPUTS) {
            this.allInputsDirectory = IOUtils.createDirectory(outputDirectory, "all");
            IOUtils.createDirectory(allInputsDirectory, "success");
            IOUtils.createDirectory(allInputsDirectory, "invalid");
            IOUtils.createDirectory(allInputsDirectory, "failure");
        }
        this.statsFile = new File(outputDirectory, "plot_data");
        this.statsCovCasesFile = new File(outputDirectory, "plot_data_cases_cov_only");
        this.logFile = new File(outputDirectory, "fuzz.log");
        this.currentInputFile = new File(outputDirectory, ".cur_input");
        this.coverageFile = new File(outputDirectory, "coverage_hash");

        // Delete everything that we may have created in a previous run.
        // Trying to stay away from recursive delete of parent output directory in case there was a
        // typo and that was not a directory we wanted to nuke.
        // We also do not check if the deletes are actually successful.
        statsFile.delete();
        statsCovCasesFile.delete();
        logFile.delete();
        coverageFile.delete();
        for (File file : savedCorpusDirectory.listFiles()) {
            file.delete();
        }
        for (File file : savedFailuresDirectory.listFiles()) {
            file.delete();
        }

        appendLineToFile(statsFile, getStatNames());

        appendLineToFile(statsCovCasesFile, getStatCasesNames());
        String plotDataCaseCovOnly = String.format("%d, %d, %d, %d, %d, %d, %.2f%%, %.2f%%, %d, %d, %d, %d, %d, %f, %.2f%%, %f, %.2f%%, %f, %.2f%%, %d, %d, %d",
                TimeUnit.MILLISECONDS.toSeconds(new Date().getTime()), numTrials, uniqueFailures.size(), totalFailures,
                numValid, numTrials-numValid, 0.0, 0.0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0);
        appendLineToFile(statsCovCasesFile, plotDataCaseCovOnly);
    }

    protected String getStatNames() {
        return "# unix_time, cycles_done, cur_path, paths_total, " +
            "map_size, unique_crashes, all_crashes, execs_per_sec, valid_inputs, invalid_inputs, valid_cov, all_covered_probes, valid_covered_probes, eof_counts, unique_valid_traces, unique_invalid_traces";
    }

    protected String getStatCasesNames() {
        return "# unix_time, input_id, total_unique_failures, total_failures, total_valid, total_invalid, total_cov, total_valid_cov, total_branch, total_valid_branch, cycles, total_seeds, valid_seeds, overhead_total_seconds, overhead_total_percentage, update_total_seconds, update_total_percentage, sort_total_seconds, sort_total_percentage, eof_counts, unique_valid_traces, unique_invalid_traces";
    }

    /* Writes a line of text to a given log file. */
    protected void appendLineToFile(File file, String line) throws GuidanceException {
        try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
            out.println(line);
        } catch (IOException e) {
            throw new GuidanceException(e);
        }

    }

    @Override
    public String observeGuidance() {
        if (blind) {
            return "Random";
        }
        return "Zest";
    }

    /* Writes a line of text to the log file. */
    protected void infoLog(String str, Object... args) {
        if (verbose) {
            String line = String.format(str, args);
            if (logFile != null) {
                appendLineToFile(logFile, line);

            } else {
                System.err.println(line);
            }
        }
    }

    protected String millisToDuration(long millis) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis % TimeUnit.MINUTES.toMillis(1));
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis % TimeUnit.HOURS.toMillis(1));
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        String result = "";
        if (hours > 0) {
            result = hours + "h ";
        }
        if (hours > 0 || minutes > 0) {
            result += minutes + "m ";
        }
        result += seconds + "s";
        return result;
    }

    // Call only if console exists
    protected void displayStats(boolean force) {
        Date now = new Date();
        long intervalMilliseconds = now.getTime() - lastRefreshTime.getTime();
        intervalMilliseconds = Math.max(1, intervalMilliseconds);
        if (intervalMilliseconds < STATS_REFRESH_TIME_PERIOD && !force) {
            return;
        }
        long interlvalTrials = numTrials - lastNumTrials;
        long intervalExecsPerSec = interlvalTrials * 1000L;
        double intervalExecsPerSecDouble = interlvalTrials * 1000.0;
        if(intervalMilliseconds != 0) {
            intervalExecsPerSec = interlvalTrials * 1000L / intervalMilliseconds;
            intervalExecsPerSecDouble = interlvalTrials * 1000.0 / intervalMilliseconds;
        }
        lastRefreshTime = now;
        lastNumTrials = numTrials;
        long elapsedMilliseconds = now.getTime() - startTime.getTime();
        elapsedMilliseconds = Math.max(1, elapsedMilliseconds);
        long execsPerSec = numTrials * 1000L / elapsedMilliseconds;

        String currentParentInputDesc;
//        if (seedInputs.size() > 0 || savedInputs.isEmpty()) {
//            currentParentInputDesc = "<seed>";
//        } else {
//            Input currentParentInput = savedInputs.get(currentParentInputIdx);
//            currentParentInputDesc = currentParentInputIdx + " ";
//            currentParentInputDesc += currentParentInput.isFavored() ? "(favored)" : "(not favored)";
//            currentParentInputDesc += " {" + numChildrenGeneratedForCurrentParentInput +
//                    "/" + getTargetChildrenForParent(currentParentInput) + " mutations}";
//        }

        if (seedInputs.size() > 0 || savedInputs.isEmpty()) {
            currentParentInputDesc = "<seed>";
        } else {
            Input currentParentInput = savedInputs.get(currentParentInputIdx);
            currentParentInputDesc = currentParentInputIdx + " ";
            currentParentInputDesc += currentParentInput.isFavored() ? "(favored)" : "(not favored)";
            currentParentInputDesc += currentParentInput.isValid() ? "(valid)" : "(invalid)";
            currentParentInputDesc += " {" + numChildrenGeneratedForCurrentParentInput + "/" + (CUSTOM_ENERGY ? getTargetChildrenForParentNew(currentParentInput) : getTargetChildrenForParent(currentParentInput)) + " mutations}";
        }

        int nonZeroCount = totalCoverage.getNonZeroCount();
        double nonZeroFraction = nonZeroCount * 100.0 / totalCoverage.size();
        int nonZeroValidCount = validCoverage.getNonZeroCount();
        double nonZeroValidFraction = nonZeroValidCount * 100.0 / validCoverage.size();

        if (console != null) {
            if (LIBFUZZER_COMPAT_OUTPUT) {
                console.printf("#%,d\tNEW\tcov: %,d exec/s: %,d L: %,d\n", numTrials, nonZeroValidCount, intervalExecsPerSec, currentInput.size());
            } else if (!QUIET_MODE) {
                console.printf("\033[2J");
                console.printf("\033[H");
                console.printf(this.getTitle() + "\n");
                if (this.testName != null) {
                    console.printf("Test name:            %s\n", this.testName);
                }

                String instrumentationType = "Janala";
                if (this.runCoverage instanceof FastNonCollidingCoverage) {
                    instrumentationType = "Fast";
                }
                console.printf("Instrumentation:      %s\n", instrumentationType);
                console.printf("Results directory:    %s\n", this.outputDirectory.getAbsolutePath());
                console.printf("Elapsed time:         %s (%s)\n", millisToDuration(elapsedMilliseconds),
                        maxDurationMillis == Long.MAX_VALUE ? "no time limit" : ("max " + millisToDuration(maxDurationMillis)));
                console.printf("Number of executions: %,d (%s)\n", numTrials,
                               maxTrials == Long.MAX_VALUE ? "no trial limit" : ("max " + maxTrials));
                console.printf("Valid inputs:         %,d (%.2f%%)\n", numValid, numValid * 100.0 / numTrials);
                console.printf("Cycles completed:     %d\n", cyclesCompleted);
                console.printf("Unique failures:      %,d\n", uniqueFailures.size());
                console.printf("Queue size:           %,d (%,d favored last cycle)\n", savedInputs.size(), numFavoredLastCycle);
                console.printf("All Seeds (Old / New):     (%,d / %,d)\n", savedInputs.size(), newSeedsFromCurrentParent.size());
                console.printf("Valid Seeds (Old / New):   (%,d / %,d)\n", savedValidInputs.size(), newValidSeedsFromCurrentParent.size());
                console.printf("Invalid Seeds (Old / New): (%,d / %,d)\n", savedInvalidInputs.size(), newInvalidSeedsFromCurrentParent.size());
                console.printf("Unique Failures (New):     (%,d)\n", newUniqueFailures);
                console.printf("Mean Valid/Invalid Dis:    (%f / %f)\n", meanValidDis, meanInvalidDis);
                console.printf("Current parent input: %s\n", currentParentInputDesc);
                console.printf("Current parent Distance:   %f\n", savedInputs.get(currentParentInputIdx).isValid() ? savedInputs.get(currentParentInputIdx).minToValidSeedsAtCov : savedInputs.get(currentParentInputIdx).minToInvalidSeedsAtCov);
                console.printf("Execution speed:      %,d/sec now | %,d/sec overall\n", intervalExecsPerSec, execsPerSec);
                console.printf("Total coverage:       %,d branches (%.2f%% of map)\n", nonZeroCount, nonZeroFraction);
                console.printf("Valid coverage:       %,d branches (%.2f%% of map)\n", nonZeroValidCount, nonZeroValidFraction);
                console.printf("EOF counts:           %,d\n", EOFcount);
                console.printf("Last Update/Sort Time:     (%f / %f)\n", lastUpdatingTime, lastSortingTime);
                console.printf("Last Avg Euclidean Time:   %f\n", avgCalEuclideanTime);
                console.printf("Last Euclidean Counts:     %d\n", EuclideanCount);
                console.printf("Unique Valid Traces:       %d\n", uniqueValidPaths.size());
                console.printf("Unique Invalid Traces:     %d\n", uniqueInvalidPaths.size());
                console.printf("Overhead Updating / Sorting:       (%.2f%% / %.2f%%)\n", seedUpdatingMilliseconds * 100.0 / elapsedMilliseconds, seedSortingMilliseconds * 100.0 / elapsedMilliseconds);
                console.printf("JVM Total Memory:     %,f\n", (Runtime.getRuntime().totalMemory()) / (1024.0 * 1024));
                console.printf("JVM Max Memory:       %,f\n", (Runtime.getRuntime().maxMemory()) / (1024.0 * 1024));
                console.printf("JVM Free Memory:      %,f\n", (Runtime.getRuntime().freeMemory()) / (1024.0 * 1024));
            }
        }

//        String plotData = String.format("%d, %d, %d, %d, %d, %d, %.2f%%, %d, %d, %d, %.2f, %d, %d, %.2f%%, %d, %d",
//                TimeUnit.MILLISECONDS.toSeconds(now.getTime()), cyclesCompleted, currentParentInputIdx,
//                numSavedInputs, 0, 0, nonZeroFraction, uniqueFailures.size(), 0, 0, intervalExecsPerSecDouble,
//                numValid, numTrials-numValid, nonZeroValidFraction, nonZeroCount, nonZeroValidCount);
//        appendLineToFile(statsFile, plotData);

        String plotData = String.format("%d, %d, %d, %d, %.2f%%, %d, %d, %.2f, %d, %d, %.2f%%, %d, %d, %d, %d, %d",
                TimeUnit.MILLISECONDS.toSeconds(now.getTime()), cyclesCompleted, currentParentInputIdx,
                numSavedInputs, nonZeroFraction, uniqueFailures.size(), totalFailures, intervalExecsPerSecDouble,
                numValid, numTrials-numValid, nonZeroValidFraction, nonZeroCount, nonZeroValidCount, EOFcount, uniqueValidPaths.size(), uniqueInvalidPaths.size());
        appendLineToFile(statsFile, plotData);
    }

    /** Updates the data in the coverage file */
    protected void updateCoverageFile() {
        try {
            PrintWriter pw = new PrintWriter(coverageFile);
            pw.println(getTotalCoverage().toString());
            pw.println("Hash code: " + getTotalCoverage().hashCode());
            pw.close();
        } catch (FileNotFoundException ignore) {
            throw new GuidanceException(ignore);
        }
    }

    /* Returns the banner to be displayed on the status screen */
    protected String getTitle() {
        if (blind) {
            return  "Generator-based random fuzzing (no guidance)\n" +
                    "--------------------------------------------\n";
        } else {
            return  "Semantic Fuzzing with Zest\n" +
                    "--------------------------\n";
        }
    }

    public void setBlind(boolean blind) {
        this.blind = blind;
    }

    protected int getTargetChildrenForParent(Input parentInput) {
        // Baseline is a constant
        int target = NUM_CHILDREN_BASELINE;

        // We like inputs that cover many things, so scale with fraction of max
        if (maxCoverage > 0) {
            target = (NUM_CHILDREN_BASELINE * parentInput.nonZeroCoverage) / maxCoverage;
        }

        // We absolutely love favored inputs, so fuzz them more
        if (parentInput.isFavored()) {
            target = target * NUM_CHILDREN_MULTIPLIER_FAVORED;
        }

        return target;
    }

    protected int getTargetChildrenForParentNew(Input parentInput) {
        // Baseline is a constant
        int target = parentInput.isValid() ? NUM_CHILDREN_BASELINE : NUM_CHILDREN_BASELINE / 2;

        // We like inputs that cover many things, so scale with fraction of max
        if (maxCoverage > 0) {
            target = (NUM_CHILDREN_BASELINE * parentInput.nonZeroCoverage) / maxCoverage;
        }

        // We absolutely love favored inputs, so fuzz them more
        if (parentInput.isFavored()) {
            target = target * NUM_CHILDREN_MULTIPLIER_FAVORED;
        }

        // adjust the target number according to the distance
        if (parentInput.isValid()) {
            if (meanValidDis != 0.0 && parentInput.minToValidSeedsAtCov != Double.MAX_VALUE) {
                target = (int) ((double) target * Math.max(Math.min((parentInput.minToValidSeedsAtCov / meanValidDis), 2.0), 0.5));
            }
        } else {
            if (meanInvalidDis != 0.0 && parentInput.minToInvalidSeedsAtCov != Double.MAX_VALUE) {
                target = (int) ((double) target * Math.max(Math.min((parentInput.minToInvalidSeedsAtCov / meanInvalidDis), 2.0), 0.5));
            }
        }

        return target;
    }

    /** Handles the end of fuzzing cycle (i.e., having gone through the entire queue) */
    protected void completeCycle() {
        // Increment cycle count
        cyclesCompleted++;
        infoLog("\n# Cycle " + cyclesCompleted + " completed.");

        // Go over all inputs and do a sanity check (plus log)
        infoLog("Here is a list of favored inputs:");
        int sumResponsibilities = 0;
        numFavoredLastCycle = 0;
        for (Input input : savedInputs) {
            if (input.isFavored()) {
                int responsibleFor = input.responsibilities.size();
                infoLog("Input %d is responsible for %d branches", input.id, responsibleFor);
                sumResponsibilities += responsibleFor;
                numFavoredLastCycle++;
            }
        }
        int totalCoverageCount = totalCoverage.getNonZeroCount();
        infoLog("Total %d branches covered", totalCoverageCount);
        if (sumResponsibilities != totalCoverageCount) {
            if (multiThreaded) {
                infoLog("Warning: other threads are adding coverage between test executions");
            } else {
                throw new AssertionError("Responsibilty mismatch");
            }
        }

        // Break log after cycle
        infoLog("\n\n\n");
    }

    /**
     * Spawns a new input from thin air (i.e., actually random)
     *
     * @return a fresh input
     */
    protected Input<?> createFreshInput() {
        return new LinearInput();
    }

    /**
     * Returns an InputStream that delivers parameters to the generators.
     *
     * Note: The variable `currentInput` has been set to point to the input
     * to mutate.
     *
     * @return an InputStream that delivers parameters to the generators
     */
    protected InputStream createParameterStream() {
        // Return an input stream that reads bytes from a linear array
        return new InputStream() {
            int bytesRead = 0;

            @Override
            public int read() throws IOException {
                assert currentInput instanceof LinearInput : "ZestGuidance should only mutate LinearInput(s)";

                // For linear inputs, get with key = bytesRead (which is then incremented)
                LinearInput linearInput = (LinearInput) currentInput;
                // Attempt to get a value from the list, or else generate a random value
                int ret = linearInput.getOrGenerateFresh(bytesRead++, random);
                // infoLog("read(%d) = %d", bytesRead, ret);
                return ret;
            }
        };
    }

    public double calHammingDistance(Input a, Input b) {
        double distance = 0.0;
        Counter c1 = a.coverage.getCounter();
        Counter c2 = b.coverage.getCounter();
        IntArrayList nonZeroKeys1 = (IntArrayList) c1.getNonZeroIndices();
        IntArrayList nonZeroKeys2 = (IntArrayList) c2.getNonZeroIndices();
        nonZeroKeys1.sortThis();
        nonZeroKeys2.sortThis();

        if (USE_WEIGHT_DISTANCE) {
            IntHashSet uniquePaths = a.isValid() ? uniqueValidPaths : uniqueInvalidPaths;
            Counter counter = a.isValid() ? validUniqueTracesCount.getCounter() : invalidUniqueTracesCount.getCounter();
            int i = 0;
            int j = 0;
            while (i < nonZeroKeys1.size() && j < nonZeroKeys2.size()) {
                int idx1 = nonZeroKeys1.get(i);
                int idx2 = nonZeroKeys2.get(j);
                if (idx1 == idx2) {
                    i++;
                    j++;
                } else if (idx1 < idx2) {
                    distance += Math.log(1.0 + ((double) uniquePaths.size() / (double) counter.getAtIndex(idx1)));
                    i++;
                } else {
                    distance += Math.log(1.0 + ((double) uniquePaths.size() / (double) counter.getAtIndex(idx2)));
                    j++;
                }
            }

            while (i < nonZeroKeys1.size()) {
                int idx1 = nonZeroKeys1.get(i);
                distance += Math.log(1.0 + ((double) uniquePaths.size() / (double) counter.getAtIndex(idx1)));
                i++;
            }

            while (j < nonZeroKeys2.size()) {
                int idx2 = nonZeroKeys2.get(j);
                distance += Math.log(1.0 + ((double) uniquePaths.size() / (double) counter.getAtIndex(idx2)));
                j++;
            }
        } else {
            int i = 0;
            int j = 0;
            while (i < nonZeroKeys1.size() && j < nonZeroKeys2.size()) {
                int idx1 = nonZeroKeys1.get(i);
                int idx2 = nonZeroKeys2.get(j);
                if (idx1 == idx2) {
                    i++;
                    j++;
                } else if (idx1 < idx2) {
                    distance += 1;
                    i++;
                } else {
                    distance += 1;
                    j++;
                }
            }

            while (i < nonZeroKeys1.size()) {
                distance += 1;
                i++;
            }

            while (j < nonZeroKeys2.size()) {
                distance += 1;
                j++;
            }
        }

        return distance;
    }

    public double calEuclideanDistance(Input a, Input b) {
        double distance = 0.0;

        Counter c1 = a.coverage.getCounter();
        Counter c2 = b.coverage.getCounter();
        IntArrayList nonZeroKeys1 = (IntArrayList) c1.getNonZeroIndices();
        IntArrayList nonZeroKeys2 = (IntArrayList) c2.getNonZeroIndices();
        nonZeroKeys1.sortThis();
        nonZeroKeys2.sortThis();

        if (USE_WEIGHT_DISTANCE) {
            IntHashSet uniquePaths = a.isValid() ? uniqueValidPaths : uniqueInvalidPaths;
            Counter counter = a.isValid() ? validUniqueTracesCount.getCounter() : invalidUniqueTracesCount.getCounter();
            int i = 0;
            int j = 0;
            while (i < nonZeroKeys1.size() && j < nonZeroKeys2.size()) {
                int idx1 = nonZeroKeys1.get(i);
                int idx2 = nonZeroKeys2.get(j);
                if (idx1 == idx2) {
                    distance += Math.log(1.0 + ((double) uniquePaths.size() / (double) counter.getAtIndex(idx1) + EPSILON)) * ((c1.getAtIndex(idx1) - c2.getAtIndex(idx2)) * (c1.getAtIndex(idx1) - c2.getAtIndex(idx2)));
                    i++;
                    j++;
                } else if (idx1 < idx2) {
                    distance += Math.log(1.0 + ((double) uniquePaths.size() / (double) counter.getAtIndex(idx1) + EPSILON)) * (c1.getAtIndex(idx1) * c1.getAtIndex(idx1));
                    i++;
                } else {
                    distance += Math.log(1.0 + ((double) uniquePaths.size() / (double) counter.getAtIndex(idx2) + EPSILON)) * (c2.getAtIndex(idx2) * c2.getAtIndex(idx2));
                    j++;
                }
            }

            while (i < nonZeroKeys1.size()) {
                int idx1 = nonZeroKeys1.get(i);
                distance += Math.log(1.0 + ((double) uniquePaths.size() / (double) counter.getAtIndex(idx1) + EPSILON)) * (c1.getAtIndex(idx1) * c1.getAtIndex(idx1));
                i++;
            }

            while (j < nonZeroKeys2.size()) {
                int idx2 = nonZeroKeys2.get(j);
                distance += Math.log(1.0 + ((double) uniquePaths.size() / (double) counter.getAtIndex(idx2) + EPSILON)) * (c2.getAtIndex(idx2) * c2.getAtIndex(idx2));
                j++;
            }
        } else {
            int i = 0;
            int j = 0;
            while (i < nonZeroKeys1.size() && j < nonZeroKeys2.size()) {
                int idx1 = nonZeroKeys1.get(i);
                int idx2 = nonZeroKeys2.get(j);
                if (idx1 == idx2) {
                    distance += (c1.getAtIndex(idx1) - c2.getAtIndex(idx2)) * (c1.getAtIndex(idx1) - c2.getAtIndex(idx2));
                    i++;
                    j++;
                } else if (idx1 < idx2) {
                    distance += c1.getAtIndex(idx1) * c1.getAtIndex(idx1);
                    i++;
                } else {
                    distance += c2.getAtIndex(idx2) * c2.getAtIndex(idx2);
                    j++;
                }
            }

            while (i < nonZeroKeys1.size()) {
                int idx1 = nonZeroKeys1.get(i);
                distance += c1.getAtIndex(idx1) * c1.getAtIndex(idx1);
                i++;
            }

            while (j < nonZeroKeys2.size()) {
                int idx2 = nonZeroKeys2.get(j);
                distance += c2.getAtIndex(idx2) * c2.getAtIndex(idx2);
                j++;
            }
        }

        return Math.sqrt(distance);
    }

    public void updateTracesCount(IntList nonZeroBranches, Boolean isValid) {
        NonZeroCachingCounter counter = (NonZeroCachingCounter) (isValid ? validUniqueTracesCount.getCounter() : invalidUniqueTracesCount.getCounter());
        IntIterator iter = nonZeroBranches.intIterator();
        while(iter.hasNext()){
            int idx = iter.next();
            counter.incrementAtIndex(idx, 1);
        }
    }

    @Override
    public void EOFcount() {
        EOFcount++;
    }

    public void rankingSeedsConditionally() {
        // ranking mode
        // 0: full ranking on saved seeds (when reaching the end of saved seeds, a start of new cycle)
        // 1: additional ranking on new seeds, also updating the distances
        // 2: no ranking
        int rankingMode = 2;
        if (currentParentInputIdx == savedInputs.size() - 1) {
            if (newSeedsFromCurrentParent.isEmpty()) {
                rankingMode = 0;
            } else {
                rankingMode = 1;
            }
        } else if (VALID_SEED_FIRST && !savedInputs.get(currentParentInputIdx + 1).isValid() && !newValidSeedsFromCurrentParent.isEmpty()) {
            rankingMode = 1;
        }

        if (rankingMode == 0) {
            long currentTime = System.currentTimeMillis();

            // perform a full ranking
            savedInputs.sort(inputComparator);

            // calculate the mean dis
            meanValidDis = 0.0;
            meanInvalidDis = 0.0;
            int toValidDisCount = 0;
            int toInvalidDisCount = 0;
            for (Input currentSeed : savedInputs) {
                if (currentSeed.isValid()) {
                    if (currentSeed.minToValidSeedsAtCov != Double.MAX_VALUE) {
                        meanValidDis = meanValidDis * ((double) toValidDisCount / (double) (toValidDisCount + 1)) + currentSeed.minToValidSeedsAtCov / ((double) (toValidDisCount + 1));
                    }
                } else {
                    if (currentSeed.minToInvalidSeedsAtCov != Double.MAX_VALUE) {
                        meanInvalidDis = meanInvalidDis * ((double) toInvalidDisCount / (double) (toInvalidDisCount + 1)) + currentSeed.minToInvalidSeedsAtCov / ((double) (toInvalidDisCount + 1));
                    }
                }
            }

            long elapsedTime = System.currentTimeMillis() - currentTime;
            seedSortingMilliseconds += elapsedTime;
            lastSortingTime = (elapsedTime * 1.0) / 1000.0;
        } else if (rankingMode == 1) {
            long currentTime = System.currentTimeMillis();
            // first update the distances, then append the new seeds, finally sort from the new ones

            EuclideanCount = 0;

            for (int i=0; i<newSeedsFromCurrentParent.size(); i++) {
                Input newSeed = newSeedsFromCurrentParent.get(i);

                if (newSeed.isValid()) {
                    // update with the old valid seeds
                    for (Input oldSeed : savedInputs) {
                        if (oldSeed.isValid()) {
                            long calEuclideanStartTime = System.currentTimeMillis();
                            double dis = USE_HAMMING_DISTANCE ? calHammingDistance(oldSeed, newSeed) : calEuclideanDistance(oldSeed, newSeed);
                            avgCalEuclideanTime = avgCalEuclideanTime * ((double) EuclideanCount / (double) (EuclideanCount + 1)) + (System.currentTimeMillis() - calEuclideanStartTime * 1.0) / 1000.0 / ((double) (EuclideanCount + 1));
                            EuclideanCount++;
                            oldSeed.minToValidSeedsAtCov = Math.min(oldSeed.minToValidSeedsAtCov, dis);
                            newSeed.minToValidSeedsAtCov = Math.min(newSeed.minToValidSeedsAtCov, dis);
                        }
                    }

                    // update with the new valid seeds
                    for (int j=i+1; j<newSeedsFromCurrentParent.size(); j++) {
                        Input anotherNewSeed = newSeedsFromCurrentParent.get(j);
                        if (anotherNewSeed.isValid()) {
                            long calEuclideanStartTime = System.currentTimeMillis();
                            double dis = USE_HAMMING_DISTANCE ? calHammingDistance(anotherNewSeed, newSeed) : calEuclideanDistance(anotherNewSeed, newSeed);
                            avgCalEuclideanTime = avgCalEuclideanTime * ((double) EuclideanCount / (double) (EuclideanCount + 1)) + (System.currentTimeMillis() - calEuclideanStartTime * 1.0) / 1000.0 / ((double) (EuclideanCount + 1));
                            EuclideanCount++;
                            anotherNewSeed.minToValidSeedsAtCov = Math.min(anotherNewSeed.minToValidSeedsAtCov, dis);
                            newSeed.minToValidSeedsAtCov = Math.min(newSeed.minToValidSeedsAtCov, dis);
                        }
                    }
                } else {
                    // update with the old invalid seeds
                    for (Input oldSeed : savedInputs) {
                        if (!oldSeed.isValid()) {
                            long calEuclideanStartTime = System.currentTimeMillis();
                            double dis = USE_HAMMING_DISTANCE ? calHammingDistance(oldSeed, newSeed) : calEuclideanDistance(oldSeed, newSeed);
                            avgCalEuclideanTime = avgCalEuclideanTime * ((double) EuclideanCount / (double) (EuclideanCount + 1)) + (System.currentTimeMillis() - calEuclideanStartTime * 1.0) / 1000.0 / ((double) (EuclideanCount + 1));
                            EuclideanCount++;
                            oldSeed.minToInvalidSeedsAtCov = Math.min(oldSeed.minToInvalidSeedsAtCov, dis);
                            newSeed.minToInvalidSeedsAtCov = Math.min(newSeed.minToInvalidSeedsAtCov, dis);
                        }
                    }

                    // update with the new invalid seeds
                    for (int j=i+1; j<newSeedsFromCurrentParent.size(); j++) {
                        Input anotherNewSeed = newSeedsFromCurrentParent.get(j);
                        if (!anotherNewSeed.isValid()) {
                            long calEuclideanStartTime = System.currentTimeMillis();
                            double dis = USE_HAMMING_DISTANCE ? calHammingDistance(anotherNewSeed, newSeed) : calEuclideanDistance(anotherNewSeed, newSeed);
                            avgCalEuclideanTime = avgCalEuclideanTime * ((double) EuclideanCount / (double) (EuclideanCount + 1)) + (System.currentTimeMillis() - calEuclideanStartTime * 1.0) / 1000.0 / ((double) (EuclideanCount + 1));
                            EuclideanCount++;
                            anotherNewSeed.minToInvalidSeedsAtCov = Math.min(anotherNewSeed.minToInvalidSeedsAtCov, dis);
                            newSeed.minToInvalidSeedsAtCov = Math.min(newSeed.minToInvalidSeedsAtCov, dis);
                        }
                    }
                }
            }

            savedInputs.addAll(newSeedsFromCurrentParent);
            newSeedsFromCurrentParent.clear();
            savedValidInputs.addAll(newValidSeedsFromCurrentParent);
            newValidSeedsFromCurrentParent.clear();
            savedInvalidInputs.addAll(newInvalidSeedsFromCurrentParent);
            newInvalidSeedsFromCurrentParent.clear();
            newUniqueFailures = 0;

            long elapsedTime = System.currentTimeMillis() - currentTime;
            seedUpdatingMilliseconds += elapsedTime;
            lastUpdatingTime = (elapsedTime * 1.0) / 1000.0;

            // sort
            savedInputs.subList(currentParentInputIdx + 1, savedInputs.size()).sort(inputComparator);

            meanValidDis = 0.0;
            meanInvalidDis = 0.0;
            int toValidDisCount = 0;
            int toInvalidDisCount = 0;
            for (int i=currentParentInputIdx+1; i<savedInputs.size(); i++) {
                Input currentSeed = savedInputs.get(i);
                if (currentSeed.isValid()) {
                    if (currentSeed.minToValidSeedsAtCov != Double.MAX_VALUE) {
                        meanValidDis = meanValidDis * ((double) toValidDisCount / (double) (toValidDisCount + 1)) + currentSeed.minToValidSeedsAtCov / ((double) (toValidDisCount + 1));
                        toValidDisCount++;
                    }
                } else {
                    if (currentSeed.minToInvalidSeedsAtCov != Double.MAX_VALUE) {
                        meanInvalidDis = meanInvalidDis * ((double) toInvalidDisCount / (double) (toInvalidDisCount + 1)) + currentSeed.minToInvalidSeedsAtCov / ((double) (toInvalidDisCount + 1));
                        toInvalidDisCount++;
                    }
                }
            }

            long elapsedTime2 = System.currentTimeMillis() - currentTime;
            seedSortingMilliseconds += elapsedTime2 - elapsedTime;
            lastSortingTime = ((elapsedTime2 - elapsedTime) * 1.0) / 1000.0;
        }
    }

    @Override
    public InputStream getInput() throws GuidanceException {
        conditionallySynchronize(multiThreaded, () -> {
            // Clear coverage stats for this run
            runCoverage.clear();

            // Choose an input to execute based on state of queues
            if (!seedInputs.isEmpty()) {
                // First, if we have some specific seeds, use those
                currentInput = seedInputs.removeFirst();

                // Hopefully, the seeds will lead to new coverage and be added to saved inputs

            } else if (savedInputs.isEmpty()) {
                // If no seeds given try to start with something random
                if (!blind && numTrials > 100_000) {
                    throw new GuidanceException("Too many trials without coverage; " +
                            "likely all assumption violations");
                }

                // Make fresh input using either list or maps
                // infoLog("Spawning new input from thin air");
                currentInput = createFreshInput();
            } else {
                // The number of children to produce is determined by how much of the coverage
                // pool this parent input hits
                Input currentParentInput = savedInputs.get(currentParentInputIdx);
                int targetNumChildren;
                if (CUSTOM_ENERGY) {
                    targetNumChildren = getTargetChildrenForParentNew(currentParentInput);
                } else {
                    targetNumChildren = getTargetChildrenForParent(currentParentInput);
                }
                if (numChildrenGeneratedForCurrentParentInput >= targetNumChildren) {
                    // Ranking the pending seeds (including the old and new ones).
                    rankingSeedsConditionally();

                    // Select the next saved input to fuzz
                    currentParentInputIdx = (currentParentInputIdx + 1) % savedInputs.size();

                    // Count cycles
                    if (currentParentInputIdx == 0) {
                        completeCycle();
                    }

                    numChildrenGeneratedForCurrentParentInput = 0;
                }
                Input parent = savedInputs.get(currentParentInputIdx);

                // Fuzz it to get a new input
                // infoLog("Mutating input: %s", parent.desc);
                currentInput = parent.fuzz(random);

                numChildrenGeneratedForCurrentParentInput++;

                // Write it to disk for debugging
                try {
                    writeCurrentInputToFile(currentInputFile);
                } catch (IOException ignore) {
                }

                // Start time-counting for timeout handling
                this.runStart = new Date();
                this.branchCount = 0;
            }
        });

        return createParameterStream();
    }

//    @Override
//    public InputStream getInput() throws GuidanceException {
//        conditionallySynchronize(multiThreaded, () -> {
//            // Clear coverage stats for this run
//            runCoverage.clear();
//
//            // Choose an input to execute based on state of queues
//            if (!seedInputs.isEmpty()) {
//                // First, if we have some specific seeds, use those
//                currentInput = seedInputs.removeFirst();
//
//                // Hopefully, the seeds will lead to new coverage and be added to saved inputs
//
//            } else if (savedInputs.isEmpty()) {
//                // If no seeds given try to start with something random
//                if (!blind && numTrials > 100_000) {
//                    throw new GuidanceException("Too many trials without coverage; " +
//                            "likely all assumption violations");
//                }
//
//                // Make fresh input using either list or maps
//                // infoLog("Spawning new input from thin air");
//                currentInput = createFreshInput();
//            } else {
//                // The number of children to produce is determined by how much of the coverage
//                // pool this parent input hits
//                Input currentParentInput = savedInputs.get(currentParentInputIdx);
//                int targetNumChildren = getTargetChildrenForParent(currentParentInput);
//                if (numChildrenGeneratedForCurrentParentInput >= targetNumChildren) {
//                    // Select the next saved input to fuzz
//                    currentParentInputIdx = (currentParentInputIdx + 1) % savedInputs.size();
//
//                    // Count cycles
//                    if (currentParentInputIdx == 0) {
//                        completeCycle();
//                    }
//
//                    numChildrenGeneratedForCurrentParentInput = 0;
//                }
//                Input parent = savedInputs.get(currentParentInputIdx);
//
//                // Fuzz it to get a new input
//                // infoLog("Mutating input: %s", parent.desc);
//                currentInput = parent.fuzz(random);
//                numChildrenGeneratedForCurrentParentInput++;
//
//                // Write it to disk for debugging
//                try {
//                    writeCurrentInputToFile(currentInputFile);
//                } catch (IOException ignore) {
//                }
//
//                // Start time-counting for timeout handling
//                this.runStart = new Date();
//                this.branchCount = 0;
//            }
//        });
//
//        return createParameterStream();
//    }

    @Override
    public boolean hasInput() {
        Date now = new Date();
        long elapsedMilliseconds = now.getTime() - startTime.getTime();
        if (EXIT_ON_CRASH && uniqueFailures.size() >= 1) {
            // exit
            return false;
        }
        if(elapsedMilliseconds < maxDurationMillis
            && numTrials < maxTrials) {
            return true;
        } else {
            displayStats(true);
            return false;
        }
    }

    @Override
    public void handleResult(Result result, Throwable error) throws GuidanceException {
        conditionallySynchronize(multiThreaded, () -> {
            // Stop timeout handling
            this.runStart = null;

            // Increment run count
            this.numTrials++;

            boolean valid = result == Result.SUCCESS;

            if (valid) {
                // Increment valid counter
                numValid++;
            }

            boolean save_cov_only = false;

            if (result == Result.SUCCESS || (result == Result.INVALID && !SAVE_ONLY_VALID)) {

                // Compute a list of keys for which this input can assume responsibility.
                // Newly covered branches are always included.
                // Existing branches *may* be included, depending on the heuristics used.
                // A valid input will steal responsibility from invalid inputs
                IntHashSet responsibilities = computeResponsibilities(valid);

                // Determine if this input should be saved
                List<String> savingCriteriaSatisfied = checkSavingCriteriaSatisfied(result);
                boolean toSave = savingCriteriaSatisfied.size() > 0;

                if (toSave) {
                    String why = String.join(" ", savingCriteriaSatisfied);

                    for (String reason : savingCriteriaSatisfied) {
                        if (reason.equals("+cov")) {
                            save_cov_only = true;
                        }
                    }

                    // Trim input (remove unused keys)
                    currentInput.gc();

                    // It must still be non-empty
                    assert (currentInput.size() > 0) : String.format("Empty input: %s", currentInput.desc);

                    // libFuzzerCompat stats are only displayed when they hit new coverage
                    if (LIBFUZZER_COMPAT_OUTPUT) {
                        displayStats(false);
                    }

                    infoLog("Saving new input (at run %d): " +
                                    "input #%d " +
                                    "of size %d; " +
                                    "reason = %s",
                            numTrials,
                            savedInputs.size(),
                            currentInput.size(),
                            why);

                    // Save input to queue and to disk
                    final String reason = why;
                    GuidanceException.wrap(() -> saveCurrentInput(responsibilities, reason, valid));

                    // Update coverage information
                    updateCoverageFile();
                }
            } else if (result == Result.FAILURE || result == Result.TIMEOUT) {
                totalFailures++;

                save_cov_only = true;

                String msg = error.getMessage();

                // Get the root cause of the failure
                Throwable rootCause = error;
                while (rootCause.getCause() != null) {
                    rootCause = rootCause.getCause();
                }

                // Attempt to add this to the set of unique failures
                if (uniqueFailures.add(failureDigest(rootCause.getStackTrace()))) {

                    // Trim input (remove unused keys)
                    currentInput.gc();

                    // It must still be non-empty
                    assert (currentInput.size() > 0) : String.format("Empty input: %s", currentInput.desc);

                    // Save crash to disk
                    int crashIdx = uniqueFailures.size() - 1;
                    String saveFileName = String.format("id_%06d", crashIdx);
                    if (SAVE_INPUT_FILES) {
                        File saveFile = new File(savedFailuresDirectory, saveFileName);
                        GuidanceException.wrap(() -> writeCurrentInputToFile(saveFile));
                        infoLog("%s", "Found crash: " + error.getClass() + " - " + (msg != null ? msg : ""));
                        String how = currentInput.desc;
                        String why = result == Result.FAILURE ? "+crash" : "+hang";
                        infoLog("Saved - %s %s %s", saveFile.getPath(), how, why);
                    } else {
                        infoLog("%s", "Found crash: " + error.getClass() + " - " + (msg != null ? msg : ""));
                        String how = currentInput.desc;
                        String why = result == Result.FAILURE ? "+crash" : "+hang";
                        infoLog("Saved - %s %s %s", saveFileName, how, why);
                    }

                    if (EXACT_CRASH_PATH != null && !EXACT_CRASH_PATH.equals("")) {
                        File exactCrashFile = new File(EXACT_CRASH_PATH);
                        GuidanceException.wrap(() -> writeCurrentInputToFile(exactCrashFile));
                    }

                    // libFuzzerCompat stats are only displayed when they hit new coverage or crashes
                    if (LIBFUZZER_COMPAT_OUTPUT) {
                        displayStats(false);
                    }

                    currentInput.coverage = runCoverage.copy();

                    // add to unique failures
                    newUniqueFailures++;
//                    newUniqueFailedInputs.add(currentInput);
                }
            }

            // displaying stats on every interval is only enabled for AFL-like stats screen
            if (!LIBFUZZER_COMPAT_OUTPUT) {
                displayStats(false);
            }

            // Save input unconditionally if such a setting is enabled
            if (LOG_ALL_INPUTS && (SAVE_ONLY_VALID ? valid : true)) {
                File logDirectory = new File(allInputsDirectory, result.toString().toLowerCase());
                String saveFileName = String.format("id_%09d", numTrials);
                File saveFile = new File(logDirectory, saveFileName);
                GuidanceException.wrap(() -> writeCurrentInputToFile(saveFile));
            }

            if (save_cov_only) {
                String plotDataCaseCovOnly = String.format("%d, %d, %d, %d, %d, %d, %.2f%%, %.2f%%, %d, %d, %d, %d, %d, %f, %.2f%%, %f, %.2f%%, %f, %.2f%%, %d, %d, %d",
                        TimeUnit.MILLISECONDS.toSeconds(new Date().getTime()), numTrials, uniqueFailures.size(), totalFailures,
                        numValid, numTrials-numValid,
                        totalCoverage.getNonZeroCount() * 100.0 / totalCoverage.size(),
                        validCoverage.getNonZeroCount() * 100.0 / validCoverage.size(),
                        totalCoverage.getNonZeroCount(), validCoverage.getNonZeroCount(), cyclesCompleted, savedInputs.size() + newSeedsFromCurrentParent.size(), savedValidInputs.size() + newValidSeedsFromCurrentParent.size(),
                        (seedUpdatingMilliseconds + seedSortingMilliseconds) * 1.0 / 1000.0, (seedUpdatingMilliseconds + seedSortingMilliseconds) * 100.0 / Math.max(1, new Date().getTime() - startTime.getTime()),
                        seedUpdatingMilliseconds * 1.0 / 1000.0, seedUpdatingMilliseconds  * 100.0 / Math.max(1, new Date().getTime() - startTime.getTime()),
                        seedSortingMilliseconds * 1.0 / 1000.0, seedSortingMilliseconds  * 100.0 / Math.max(1, new Date().getTime() - startTime.getTime()),
                        EOFcount, uniqueValidPaths.size(), uniqueInvalidPaths.size());
                appendLineToFile(statsCovCasesFile, plotDataCaseCovOnly);
            }
        });
    }

    // Return a list of saving criteria that have been satisfied for a non-failure input
    protected List<String> checkSavingCriteriaSatisfied(Result result) {
        // Coverage before
        int nonZeroBefore = totalCoverage.getNonZeroCount();
        int validNonZeroBefore = validCoverage.getNonZeroCount();

        // update the unique traces and species counts
        if (USE_WEIGHT_DISTANCE) {
            if (result == Result.SUCCESS && uniqueValidPaths.add(runCoverage.hashCode())) {
                IntList nonZeroValidBranches = runCoverage.getCovered();
                updateTracesCount(nonZeroValidBranches, true);
            } else if (result == Result.INVALID && uniqueInvalidPaths.add(runCoverage.hashCode())) {
                IntList nonZeroInvalidBranches = runCoverage.getCovered();
                updateTracesCount(nonZeroInvalidBranches, false);
            }
        }

        // Update total coverage
        boolean coverageBitsUpdated = totalCoverage.updateBits(runCoverage);
        if (result == Result.SUCCESS) {
            validCoverage.updateBits(runCoverage);
        }

        // Coverage after
        int nonZeroAfter = totalCoverage.getNonZeroCount();
        if (nonZeroAfter > maxCoverage) {
            maxCoverage = nonZeroAfter;
        }
        int validNonZeroAfter = validCoverage.getNonZeroCount();

        // Possibly save input
        List<String> reasonsToSave = new ArrayList<>();


        if (!DISABLE_SAVE_NEW_COUNTS && coverageBitsUpdated) {
            reasonsToSave.add("+count");
        }

        // Save if new total coverage found
        if (nonZeroAfter > nonZeroBefore) {
            reasonsToSave.add("+cov");
        }

        // Save if new valid coverage is found
        if (this.validityFuzzing && validNonZeroAfter > validNonZeroBefore) {
            reasonsToSave.add("+valid");
        }

        return reasonsToSave;
    }


    // Compute a set of branches for which the current input may assume responsibility
    protected IntHashSet computeResponsibilities(boolean valid) {
        IntHashSet result = new IntHashSet();

        // This input is responsible for all new coverage
        IntList newCoverage = runCoverage.computeNewCoverage(totalCoverage);
        if (newCoverage.size() > 0) {
            result.addAll(newCoverage);
        }

        // If valid, this input is responsible for all new valid coverage
        if (valid) {
            IntList newValidCoverage = runCoverage.computeNewCoverage(validCoverage);
            if (newValidCoverage.size() > 0) {
                result.addAll(newValidCoverage);
            }
        }

        // Perhaps it can also steal responsibility from other inputs
        if (STEAL_RESPONSIBILITY) {
            int currentNonZeroCoverage = runCoverage.getNonZeroCount();
            int currentInputSize = currentInput.size();
            IntHashSet covered = new IntHashSet();
            covered.addAll(runCoverage.getCovered());

            // Search for a candidate to steal responsibility from
            candidate_search:
            for (Input candidate : savedInputs) {
                IntHashSet responsibilities = candidate.responsibilities;

                // Candidates with no responsibility are not interesting
                if (responsibilities.isEmpty()) {
                    continue candidate_search;
                }

                // To avoid thrashing, only consider candidates with either
                // (1) strictly smaller total coverage or
                // (2) same total coverage but strictly larger size
                if (candidate.nonZeroCoverage < currentNonZeroCoverage ||
                        (candidate.nonZeroCoverage == currentNonZeroCoverage &&
                                currentInputSize < candidate.size())) {

                    // Check if we can steal all responsibilities from candidate
                    IntIterator iter = responsibilities.intIterator();
                    while(iter.hasNext()){
                        int b = iter.next();
                        if (covered.contains(b) == false) {
                            // Cannot steal if this input does not cover something
                            // that the candidate is responsible for
                            continue candidate_search;
                        }
                    }
                    // If all of candidate's responsibilities are covered by the
                    // current input, then it can completely subsume the candidate
                    result.addAll(responsibilities);
                }

            }
        }

        return result;
    }

    protected void writeCurrentInputToFile(File saveFile) throws IOException {
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(saveFile))) {
            for (Integer b : currentInput) {
                assert (b >= 0 && b < 256);
                out.write(b);
            }
        }

    }

    /* Saves an interesting input to the queue. */
    protected void saveCurrentInput(IntHashSet responsibilities, String why, boolean valid) throws IOException {

        // First, save to disk (note: we issue IDs to everyone, but only write to disk  if valid)
        int newInputIdx = numSavedInputs++;
        String saveFileName = String.format("id_%06d", newInputIdx);
        String how = currentInput.desc;
        if (SAVE_INPUT_FILES) {
            File saveFile = new File(savedCorpusDirectory, saveFileName);
            writeCurrentInputToFile(saveFile);
            infoLog("Saved - %s %s %s", saveFile.getPath(), how, why);
            currentInput.saveFile = saveFile;
        } else {
            infoLog("Saved - %s %s %s", saveFileName, how, why);
        }

        // If not using guidance, do nothing else
        if (blind) {
            return;
        }

        // Second, save to queue (change: save to pending queue)
        if (savedInputs.isEmpty()) {
            savedInputs.add(currentInput);
            if (valid) {
                savedValidInputs.add(currentInput);
            } else {
                savedInvalidInputs.add(currentInput);
            }
        } else {
            newSeedsFromCurrentParent.add(currentInput);
            if (valid) {
                newValidSeedsFromCurrentParent.add(currentInput);
            } else {
                newInvalidSeedsFromCurrentParent.add(currentInput);
            }
        }

        // Third, store basic book-keeping data
        currentInput.id = newInputIdx;
        currentInput.coverage = runCoverage.copy();
        currentInput.nonZeroCoverage = runCoverage.getNonZeroCount();
        currentInput.offspring = 0;
        savedInputs.get(currentParentInputIdx).offspring += 1;

        if (valid) {
            currentInput.setValid();
        }

        // Fourth, assume responsibility for branches
        currentInput.responsibilities = responsibilities;
        if (responsibilities.size() > 0) {
          currentInput.setFavored();
        }
        IntIterator iter = responsibilities.intIterator();
        while(iter.hasNext()){
            int b = iter.next();
            // If there is an old input that is responsible,
            // subsume it
            Input oldResponsible = responsibleInputs.get(b);
            if (oldResponsible != null) {
                oldResponsible.responsibilities.remove(b);
                // infoLog("-- Stealing responsibility for %s from input %d", b, oldResponsible.id);
            } else {
                // infoLog("-- Assuming new responsibility for %s", b);
            }
            // We are now responsible
            responsibleInputs.put(b, currentInput);
        }

    }

    @Override
    public Consumer<TraceEvent> generateCallBack(Thread thread) {
        if (firstThread == null) {
            firstThread = thread;
        } else if (firstThread != thread) {
            multiThreaded = true;
        }
        return this::handleEvent;
    }

    /**
     * Handles a trace event generated during test execution.
     *
     * Not used by FastNonCollidingCoverage, which does not allocate an
     * instance of TraceEvent at each branch probe execution.
     *
     * @param e the trace event to be handled
     */
    protected void handleEvent(TraceEvent e) {
        conditionallySynchronize(multiThreaded, () -> {
            // Collect totalCoverage
            ((Coverage) runCoverage).handleEvent(e);
            // Check for possible timeouts every so often
            if (this.singleRunTimeoutMillis > 0 &&
                    this.runStart != null && (++this.branchCount) % 10_000 == 0) {
                long elapsed = new Date().getTime() - runStart.getTime();
                if (elapsed > this.singleRunTimeoutMillis) {
                    throw new TimeoutException(elapsed, this.singleRunTimeoutMillis);
                }
            }
        });
    }

    /**
     * Returns a reference to the coverage statistics.
     * @return a reference to the coverage statistics
     */
    public ICoverage getTotalCoverage() {
        return totalCoverage;
    }

    /**
     * Conditionally run a method using synchronization.
     *
     * This is used to handle multi-threaded fuzzing.
     */
    protected void conditionallySynchronize(boolean cond, Runnable task) {
        if (cond) {
            synchronized (this) {
                task.run();
            }
        } else {
            task.run();
        }
    }

    private static MessageDigest sha1;

    private static String failureDigest(StackTraceElement[] stackTrace) {
        if (sha1 == null) {
            try {
                sha1 = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                throw new GuidanceException(e);
            }
        }
        byte[] bytes = sha1.digest(Arrays.deepToString(stackTrace).getBytes());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16)
                    .substring(1));
        }
        return sb.toString();
    }

    /**
     * A candidate or saved test input that maps objects of type K to bytes.
     */
    public static abstract class Input<K> implements Iterable<Integer> {

        /** Minimum distance to existing seeds, at coverage. **/
//        int minToSeeds = Integer.MAX_VALUE;

        /** Minimum distance to existing unique failures. **/
//        int minToUniqueFailures = Integer.MAX_VALUE;

        /** Minimum distance to existing valid seeds, at coverage. **/
//        int minToValidSeeds = Integer.MAX_VALUE;

        /** Minimum distance to existing valid seeds, at input value. **/
//        int minToValidSeedsAtInput = Integer.MAX_VALUE;

        /** Minimum distance to existing valid seeds, at coverage, euclidean. **/
        double minToInvalidSeedsAtCov = Double.MAX_VALUE;

        /** Minimum distance to existing valid seeds, at coverage, euclidean. **/
        double minToValidSeedsAtCov = Double.MAX_VALUE;

        /** Whether this input is valid. **/
        boolean valid;

        /**
         * The file where this input is saved.
         *
         * <p>This field is null for inputs that are not saved.</p>
         */
        File saveFile = null;

        /**
         * An ID for a saved input.
         *
         * <p>This field is -1 for inputs that are not saved.</p>
         */
        int id;

        /**
         * Whether this input is favored.
         */
        boolean favored;

        /**
         * The description for this input.
         *
         * <p>This field is modified by the construction and mutation
         * operations.</p>
         */
        String desc;

        /**
         * The run coverage for this input, if the input is saved.
         *
         * <p>This field is null for inputs that are not saved.</p>
         */
        ICoverage coverage = null;

        /**
         * The number of non-zero elements in `coverage`.
         *
         * <p>This field is -1 for inputs that are not saved.</p>
         *
         * <p></p>When this field is non-negative, the information is
         * redundant (can be computed using {@link Coverage#getNonZeroCount()}),
         * but we store it here for performance reasons.</p>
         */
        int nonZeroCoverage = -1;

        /**
         * The number of mutant children spawned from this input that
         * were saved.
         *
         * <p>This field is -1 for inputs that are not saved.</p>
         */
        int offspring = -1;

        /**
         * The set of coverage keys for which this input is
         * responsible.
         *
         * <p>This field is null for inputs that are not saved.</p>
         *
         * <p>Each coverage key appears in the responsibility set
         * of exactly one saved input, and all covered keys appear
         * in at least some responsibility set. Hence, this list
         * needs to be kept in-sync with {@link #responsibleInputs}.</p>
         */
        IntHashSet responsibilities = null;

        /**
         * Create an empty input.
         */
        public Input() {
            desc = "random";
        }

        /**
         * Create a copy of an existing input.
         *
         * @param toClone the input map to clone
         */
        public Input(Input toClone) {
            desc = String.format("src:%06d", toClone.id);
        }

        public abstract int getOrGenerateFresh(K key, Random random);
        public abstract int size();
        public abstract Input fuzz(Random random);
        public abstract void gc();

        /**
         * Sets this input to be favored for fuzzing.
         */
        public void setFavored() {
            favored = true;
        }


        /**
         * Returns whether this input should be favored for fuzzing.
         *
         * <p>An input is favored if it is responsible for covering
         * at least one branch.</p>
         *
         * @return whether or not this input is favored
         */
        public boolean isFavored() {
            return favored;
        }

        public void setValid() { valid = true; }

        public boolean isValid() { return valid; }

        /**
         * Sample from a geometric distribution with given mean.
         *
         * Utility method used in implementing mutation operations.
         *
         * @param random a pseudo-random number generator
         * @param mean the mean of the distribution
         * @return a randomly sampled value
         */
        public static int sampleGeometric(Random random, double mean) {
            double p = 1 / mean;
            double uniform = random.nextDouble();
            return (int) ceil(log(1 - uniform) / log(1 - p));
        }
    }

    public class LinearInput extends Input<Integer> {

        /** A list of byte values (0-255) ordered by their index. */
        protected ArrayList<Integer> values;

        /** The number of bytes requested so far */
        protected int requested = 0;

        public LinearInput() {
            super();
            this.values = new ArrayList<>();
        }

        public LinearInput(LinearInput other) {
            super(other);
            this.values = new ArrayList<>(other.values);
        }


        @Override
        public int getOrGenerateFresh(Integer key, Random random) {
            // Otherwise, make sure we are requesting just beyond the end-of-list
            // assert (key == values.size());
            if (key != requested) {
                throw new IllegalStateException(String.format("Bytes from linear input out of order. " +
                        "Size = %d, Key = %d", values.size(), key));
            }

            // Don't generate over the limit
            if (requested >= MAX_INPUT_SIZE) {
                return -1;
            }

            // If it exists in the list, return it
            if (key < values.size()) {
                requested++;
                // infoLog("Returning old byte at key=%d, total requested=%d", key, requested);
                return values.get(key);
            }

            // Handle end of stream
            if (GENERATE_EOF_WHEN_OUT) {
                return -1;
            } else {
                // Just generate a random input
                int val = random.nextInt(256);
                values.add(val);
                requested++;
                // infoLog("Generating fresh byte at key=%d, total requested=%d", key, requested);
                return val;
            }
        }

        @Override
        public int size() {
            return values.size();
        }

        /**
         * Truncates the input list to remove values that were never actually requested.
         *
         * <p>Although this operation mutates the underlying object, the effect should
         * not be externally visible (at least as long as the test executions are
         * deterministic).</p>
         */
        @Override
        public void gc() {
            // Remove elements beyond "requested"
            values = new ArrayList<>(values.subList(0, requested));
            values.trimToSize();

            // Inputs should not be empty, otherwise mutations don't work
            if (values.isEmpty()) {
                throw new IllegalArgumentException("Input is either empty or nothing was requested from the input generator.");
            }
        }

        @Override
        public Input fuzz(Random random) {
            // Clone this input to create initial version of new child
            LinearInput newInput = new LinearInput(this);

            if (!CUSTOM_MUTATION) {
                // Stack a bunch of mutations
                int numMutations = sampleGeometric(random, MEAN_MUTATION_COUNT);
                newInput.desc += ",havoc:"+numMutations;

                boolean setToZero = random.nextDouble() < MUTATION_ZERO_PROBABILITY; // one out of 10 times

                for (int mutation = 1; mutation <= numMutations; mutation++) {

                    // Select a random offset and size
                    int offset = random.nextInt(newInput.values.size());
                    int mutationSize = sampleGeometric(random, MEAN_MUTATION_SIZE);

                    // desc += String.format(":%d@%d", mutationSize, idx);

                    // Mutate a contiguous set of bytes from offset
                    for (int i = offset; i < offset + mutationSize; i++) {
                        // Don't go past end of list
                        if (i >= newInput.values.size()) {
                            break;
                        }

                        // Otherwise, apply a random mutation
                        int mutatedValue = setToZero ? 0 : random.nextInt(256);
                        newInput.values.set(i, mutatedValue);
                    }
                }
            } else {
                // Stack a bunch of mutations
                int numMutations;
                if (CUSTOM_MUTATION_TIME_AND_SIZE) {
                    double meanTarget;
                    if (this.isValid()) {
                        meanTarget = meanValidDis == 0.0 ? MEAN_MUTATION_COUNT : (MEAN_MUTATION_COUNT * meanValidDis / this.minToValidSeedsAtCov);
                        meanTarget = Math.max(Math.min(meanTarget, 16.0), 4.0);
                    } else {
                        meanTarget = meanInvalidDis == 0.0 ? MEAN_MUTATION_COUNT : (MEAN_MUTATION_COUNT * meanInvalidDis / this.minToInvalidSeedsAtCov);
                        meanTarget = Math.max(Math.min(meanTarget, 16.0), 4.0);
                    }
                    numMutations = sampleGeometric(random, meanTarget);
                } else {
                    numMutations = sampleGeometric(random, MEAN_MUTATION_COUNT);
                }

//                int numMutations = sampleGeometric(random, MEAN_MUTATION_COUNT);
                newInput.desc += ",havoc:"+numMutations;

                // for valid input, start with a crossover with another valid seed in a certain probability
                if (USE_CROSSOVER) {
                    if (currentParentInputIdx > 1) {
                        boolean isCrossovered = random.nextDouble() < crossoverRate;
                        if (isCrossovered) {
                            int parentSeed1Index = random.nextInt(currentParentInputIdx);
                            int parentSeed2Index = -1;
                            while (parentSeed2Index == -1 || parentSeed2Index == parentSeed1Index) {
                                parentSeed2Index = random.nextInt(currentParentInputIdx);
                            }

                            LinearInput parentSeed1 = (LinearInput) savedInputs.get(parentSeed1Index);
                            LinearInput parentSeed2 = (LinearInput) savedInputs.get(parentSeed2Index);

                            double std1 = (double) parentSeed1.values.size() / 6.0;
                            double mean1 = (double) parentSeed1.values.size() / 2.0;
                            double std2 = (double) parentSeed2.values.size() / 6.0;
                            double mean2 = (double) parentSeed2.values.size() / 2.0;
                            int mid1 = (int) Math.round(std1 * random.nextGaussian() + mean1);
                            int mid2 = (int) Math.round(std2 * random.nextGaussian() + mean2);
                            mid1 = mid1 < 0 ? 0 : Math.min(mid1, (parentSeed1.values.size() - 1));
                            mid2 = mid2 < 0 ? 0 : Math.min(mid2, (parentSeed2.values.size() - 1));

                            newInput.values.clear();
                            for (int i = 0; i < mid1; i++) {
                                newInput.values.add(parentSeed1.values.get(i));
                            }
                            for (int i = mid2; i < parentSeed2.values.size(); i++) {
                                newInput.values.add(parentSeed2.values.get(i));
                            }
                            numMutations--;
                        }
                    }
                }

                double setValue = random.nextDouble();
                boolean setToZero = setValue < MUTATION_ZERO_PROBABILITY; // half of one out of 10 times
                boolean setToOne = setValue < MUTATION_ONE_PROBABILITY; // half of one out of 10 times
//                boolean setToZero = random.nextDouble() < MUTATION_ZERO_PROBABILITY; // one out of 10 times

                for (int mutation = 1; mutation <= numMutations; mutation++) {

                    // Select a random offset and size
                    int offset = random.nextInt(newInput.values.size());

                    int mutationSize;
                    if (CUSTOM_MUTATION_TIME_AND_SIZE) {
                        double meanTarget;
                        if (this.isValid()) {
                            meanTarget = meanValidDis == 0.0 ? MEAN_MUTATION_SIZE : (MEAN_MUTATION_SIZE * meanValidDis / this.minToValidSeedsAtCov);
                            meanTarget = Math.max(Math.min(meanTarget, 8.0), 2.0);
                        } else {
                            meanTarget = meanInvalidDis == 0.0 ? MEAN_MUTATION_SIZE : (MEAN_MUTATION_SIZE * meanInvalidDis / this.minToInvalidSeedsAtCov);
                            meanTarget = Math.max(Math.min(meanTarget, 8.0), 2.0);
                        }
                        mutationSize = sampleGeometric(random, meanTarget);
                    } else {
                        mutationSize = sampleGeometric(random, MEAN_MUTATION_SIZE);
                    }

//                    int mutationSize = sampleGeometric(random, MEAN_MUTATION_SIZE);

                    // desc += String.format(":%d@%d", mutationSize, idx);

                    // Mutate a contiguous set of bytes from offset
                    for (int i = offset; i < offset + mutationSize; i++) {
                        // Don't go past end of list
                        if (i >= newInput.values.size()) {
                            break;
                        }

                        int mutatedValue;
                        // Otherwise, apply a random mutation
                        if (CUSTOM_MUTATION_TIME_AND_SIZE) {
                            mutatedValue = setToZero ? (setToOne ? 255 : 0) : random.nextInt(256);
                        } else {
                            mutatedValue = setToZero ? 0 : random.nextInt(256);
                        }
                        newInput.values.set(i, mutatedValue);
                    }
                }
            }

            return newInput;
        }

        @Override
        public Iterator<Integer> iterator() {
            return values.iterator();
        }
    }

    public class SeedInput extends LinearInput {
        final File seedFile;
        final InputStream in;

        public SeedInput(File seedFile) throws IOException {
            super();
            this.seedFile = seedFile;
            this.in = new BufferedInputStream(new FileInputStream(seedFile));
            this.desc = "seed";
        }

        @Override
        public int getOrGenerateFresh(Integer key, Random random) {
            int value;
            try {
                value = in.read();
            } catch (IOException e) {
                throw new GuidanceException("Error reading from seed file: " + seedFile.getName(), e);

            }

            // assert (key == values.size())
            if (key != values.size() && value != -1) {
                throw new IllegalStateException(String.format("Bytes from seed out of order. " +
                        "Size = %d, Key = %d", values.size(), key));
            }

            if (value >= 0) {
                requested++;
                values.add(value);
            }

            // If value is -1, then it is returned (as EOF) but not added to the list
            return value;
        }

        @Override
        public void gc() {
            super.gc();
            try {
                in.close();
            } catch (IOException e) {
                throw new GuidanceException("Error closing seed file:" + seedFile.getName(), e);
            }
        }

    }


}

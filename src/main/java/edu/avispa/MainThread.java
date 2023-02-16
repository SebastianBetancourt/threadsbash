package edu.avispa;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.MutuallyExclusiveGroup;

import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;

public class MainThread {

    public static int lowerBound;
    public static int upperBound;
    private static long totalMemory;
    private static GlobalMemory memory;
    public static long usedMemory; // in percentage
    private static String policy;
    public static ArrayList<String> bisimilarList;
    public static File automataFolder;
    public static File equivalenceProgram;
    public static Logger logger;
    public static CountDownLatch remainingComparisons;
    public static int todoComparisons;
    public static ThreadPoolExecutor executor;
    public static boolean transitivityInference;
    public static int tries;
    public static long maximumTimePerComparison; // in ms

    public static AtomicInteger succesfulProcesses;
    public static AtomicInteger errorProcesses;
    public static AtomicInteger startedProcesses;

    public static AtomicInteger timedOutComparisons;
    public static AtomicInteger computedComparisons;
    public static AtomicInteger skippedComparisons;
    public static AtomicInteger inferredComparisons;
    

    public static void main(String[] args) {

        ArgumentParser parser = buildParser();
        Map<String, Object> res = null;
        try {
            res = parser.parseArgs(args).getAttrs();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        memory = new SystemInfo().getHardware().getMemory();
        totalMemory = memory.getTotal();
        int initialPoolsize;
        long modulationPeriod = 0;
        if (res.get("fixedThreads") != null) {
            initialPoolsize = (int) res.get("fixedThreads");
            policy = "FixedThreads";
        } else if (res.get("bounds") != null) {
            initialPoolsize = (int) res.get("initialThreads");
            ArrayList<Integer> bounds = (ArrayList<Integer>) res.get("bounds");
            lowerBound = bounds.get(0);
            upperBound = bounds.get(1);
            modulationPeriod = (long) 1000 * (int) res.get("modulationPeriod");
            policy = "Modulated";
        } else {
            long memoryUsagePerProcess = 1000000L * (int) res.get("memoryUsagePerProcess");
            initialPoolsize = (int) Math.floor(totalMemory / memoryUsagePerProcess);
            policy = "CalculatedFixedThreads";
        }

        final Path WORK_DIR = Paths.get("");

        Path outputFolder = WORK_DIR
                .resolve(((String) res.get("outputFolder")).replaceFirst("^~", System.getProperty("user.home")));
        Path logFolder = WORK_DIR
                .resolve(((String) res.get("logFolder")).replaceFirst("^~", System.getProperty("user.home")));
        equivalenceProgram = WORK_DIR
                .resolve(((String) res.get("equivalenceProgram")).replaceFirst("^~", System.getProperty("user.home")))
                .toFile();
        automataFolder = new File(
                ((String) res.get("automataFolder")).replaceFirst("^~", System.getProperty("user.home")));
        String runDescription = (String) res.get("runDescription");
        transitivityInference = (Boolean) res.get("transitivityInference");
        tries = (int) res.get("tries");
        maximumTimePerComparison = ((int) res.get("maximumTimePerComparison")) * 1000L;
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(initialPoolsize);
        Class.init();

        final String[] pathnames = automataFolder.list();
        assert pathnames != null;
        int n = pathnames.length;

        succesfulProcesses = new AtomicInteger();
        errorProcesses = new AtomicInteger();
        startedProcesses = new AtomicInteger();
        timedOutComparisons = new AtomicInteger();
        computedComparisons = new AtomicInteger();
        skippedComparisons = new AtomicInteger();
        inferredComparisons = new AtomicInteger();
        bisimilarList = new ArrayList<>();

        long start = System.currentTimeMillis();

        todoComparisons = (n * (n - 1)) / 2;

        long mainPid = ProcessHandle.current().pid();

        logger = new Logger(logFolder, outputFolder);
        logger.log("START", automataFolder.getPath(), "total comparisons: " + todoComparisons);
        logger.log("DETAILS", "description:" + runDescription, "mainPid:" + mainPid, "threadPolicy:" + policy,
                "bounds:" + lowerBound + "-" + upperBound, "initialThreads:" + initialPoolsize,
                "maxThreadsAvailable:" + Runtime.getRuntime().availableProcessors(),
                "transitivityInference:" + transitivityInference, "tries:" + tries,
                "maxTimePerComparison:" + maximumTimePerComparison);

        remainingComparisons = new CountDownLatch(todoComparisons);

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                ProcessTask processTask = new ProcessTask(pathnames[i], pathnames[j], 1);
                executor.execute(processTask);
            }
        }

        long timer = System.currentTimeMillis();
        final int MAXIMUM_POOLSIZE = Runtime.getRuntime().availableProcessors();
        while (remainingComparisons.getCount() > 0) {
            usedMemory = (long) (100 * (1.0 - ((double) memory.getAvailable() / totalMemory)));
            if (policy == "Modulated") {
                long now = System.currentTimeMillis();
                if (now - timer > modulationPeriod) {
                    int threadPoolSize = executor.getPoolSize();
                    String modulation;
                    if (usedMemory < lowerBound && threadPoolSize < MAXIMUM_POOLSIZE) {
                        threadPoolSize++;
                        executor.setMaximumPoolSize(threadPoolSize);
                        executor.setCorePoolSize(threadPoolSize);
                        modulation = "up";

                    } else {
                        modulation = "stable";
                    }
                    logger.log("MODULATION", modulation, "now " + threadPoolSize + " threads");
                    timer = System.currentTimeMillis();
                }
            }
        }

        executor.shutdown();
        try {
            remainingComparisons.await();
        } catch (InterruptedException e) {
            logger.log("ERROR",
                    e.getLocalizedMessage());
            e.printStackTrace();
        }

        long totalElapsedTime = System.currentTimeMillis() - start;

        logger.log("DONE",  
                "totalElapsedTime:" + (totalElapsedTime / (1000.0 * 60)) + "m",
                "avgComparison:" + totalElapsedTime / (1000.0 * todoComparisons) + "s");
        logger.log("COMPARISON COUNT","todo",todoComparisons, "computed", computedComparisons, 
        "inferred", inferredComparisons,"skipped",skippedComparisons, "timedOut", timedOutComparisons);
        logger.log("PROCESS COUNT","started",startedProcesses,  "successful", succesfulProcesses, "error", errorProcesses);
        if (transitivityInference) {
            for (Class c : Class.equivalentClasses) {
                logger.log("EQUIVALENCE CLASSES", c.equivalent.toString());
            }
        }

        logger.close();

    }

    public static synchronized void modulateDown() {
        if(policy == "Modulated"){
            int threadPoolSize = executor.getPoolSize();
            String modulation;
            if (usedMemory > upperBound && threadPoolSize > 1) {
                threadPoolSize--;
                executor.setCorePoolSize(threadPoolSize);
                executor.setMaximumPoolSize(threadPoolSize);
                modulation = "down";
            } else {
                modulation = "stable";
            }
            logger.log("MODULATION", modulation, threadPoolSize);
        }
    }

    private static ArgumentParser buildParser() {
        ArgumentParser parser = ArgumentParsers.newFor("java -jar ThreadSpawner.jar").build()
                .description(
                        "Program that spawns Java processes in parallel, by executing a .jar file with certain parameters. The purpose is to parallelize bisimulation equivalence checking between a set of timed automata, in a everyone-vs-everyone round robin fashion.");
        parser.addArgument("-E")
                .dest("equivalenceProgram")
                .help("Path to the Equivalance checking algorithm .jar")
                .required(true);
        parser.addArgument("-F")
                .dest("automataFolder")
                .help("Path to the folder that contains the automata .xml")
                .required(true);
        parser.addArgument("-o")
                .dest("outputFolder")
                .help("Folder path to create an output file")
                .setDefault("~");
        parser.addArgument("-l")
                .dest("logFolder")
                .help("Folder path to create a log file.")
                .setDefault("~");
        parser.addArgument("-d")
                .dest("runDescription")
                .help("Sets the description to be written in the logs of that particular execution.")
                .setDefault("");
        parser.addArgument("--ModulationPeriod")
                .dest("modulationPeriod")
                .help(" How often should the program try to regulate the thread pool size, each SECS seconds.")
                .type(int.class)
                .setDefault(5);
        parser.addArgument("--MemoryUsagePerProcess")
                .dest("memoryUsagePerProcess")
                .help("How many megabytes of memory are the processes estimated to use at their peak.")
                .type(int.class)
                .setDefault(3072);
        parser.addArgument("--initialThreads", "-i")
                .dest("initialThreads")
                .help("Initial size of the pool, for either fixedThread or ModulateThread policies")
                .type(int.class)
                .setDefault(1);
        parser.addArgument("--NotTransitive", "-nt")
                .dest("transitivityInference")
                .help("Tells the program to not infer equivalences from results computed previously in the run, not taking advantage of transitivity. This forces the program to go through all comparisons.")
                .action(Arguments.storeFalse())
                .setDefault(true);
        parser.addArgument("--tries", "-t")
                .dest("tries")
                .help("How many times the program should requeue and retry a comparisson if it fails. Default = 5")
                .type(int.class)
                .setDefault(5);
        parser.addArgument("--maximumTimePerComparison", "-mt")
                .dest("maximumTimePerComparison")
                .help("how much time a comparison may last before being interrupted and not requeued. Expressed in seconds. Default = 200s")
                .type(int.class)
                .setDefault(200);

        MutuallyExclusiveGroup threadPolicies = parser.addMutuallyExclusiveGroup("Thread pool size policy");
        threadPolicies.addArgument("--FixedThreads")
                .dest("fixedThreads")
                .help("Specifies a fixed thread pool size of NUMBER.")
                .type(int.class);
        threadPolicies.addArgument("--ModulateThreads", "-m")
                .nargs(2)
                .dest("bounds")
                .help("Lets the program regulate the size of the thread pool dinamically, trying to keep memory usage percentage between the integers LOWERBOUND and UPPERBOUND.")
                .type(int.class);
        threadPolicies.addArgument("--CalculateFixedThreads")
                .dest("calculateFixedThreads")
                .help("Asks the program to determine the best size of a fixed thread pool based on the available memory of the machine. This is the default behavior.")
                .action(Arguments.storeTrue());

        return parser;
    }
}

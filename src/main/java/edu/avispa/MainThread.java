package edu.avispa;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
//import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

public class MainThread {

    //public static ArrayList<String> bisimilarList;
    public static File automataFolder;
    public static File equivalenceProgram;
    public static Logger logger;
    public static CountDownLatch remainingComparisons;
    public static int todoComparisons;
    public static ThreadPoolExecutor executor;
    public static boolean transitivityInference;

    private static long startTime;

    public static AtomicInteger computedComparisons;
    public static AtomicInteger inferredComparisons;
    public static AtomicInteger failedComparisons;

    public static void main(String[] args) {

        ArgumentParser parser = buildParser();
        Map<String, Object> res = null;
        try {
            res = parser.parseArgs(args).getAttrs();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        final int threads = (int) res.get("threads");
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
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threads);
        Class.init();

        final String[] pathnames = automataFolder.list();
        assert pathnames != null;
        int n = pathnames.length;


        computedComparisons = new AtomicInteger();
        inferredComparisons = new AtomicInteger();
        failedComparisons = new AtomicInteger();
        //bisimilarList = new ArrayList<>();

        startTime = System.currentTimeMillis();

        todoComparisons = (n * (n - 1)) / 2;

        long mainPid = ProcessHandle.current().pid();

        logger = new Logger(logFolder, outputFolder);
        logger.log("START", automataFolder.getPath(), "total comparisons: " + todoComparisons);
        logger.log("DETAILS", "description:" + runDescription, "mainPid:" + mainPid, "threadPolicy:fixedThreads", "threads:" + threads,
                "maxThreadsAvailable:" + Runtime.getRuntime().availableProcessors(),
                "transitivityInference:" + transitivityInference);

        remainingComparisons = new CountDownLatch(todoComparisons);

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                ProcessTask processTask = new ProcessTask(pathnames[i], pathnames[j]);
                executor.execute(processTask);
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

        long totalElapsedTime = System.currentTimeMillis() - startTime;

        logger.log("DONE",  
                "totalElapsedTime:" + String.format("%.3f", (totalElapsedTime / (1000.0 * 60))) + "m",
                "avgComparison:" + totalElapsedTime / (1000.0 * todoComparisons) + "s");
        logger.log("COMPARISON COUNT","todo",todoComparisons, "computed", computedComparisons, 
        "inferred", inferredComparisons, "failed", failedComparisons);
        for (Class c : Class.equivalentClasses) {
            logger.log("EQUIVALENCE CLASSES", c.equivalent.toString());
        }

        logger.close();

    }

    public static String getAvgTimePerComparison(){
        double average = (System.currentTimeMillis() - startTime) / ((todoComparisons - remainingComparisons.getCount() + 1) * 1000.0);
        return String.format("%.3f", average) + "s/comparison";
    }

    public static String getTotalProgress(){
        return (todoComparisons - remainingComparisons.getCount() + 1) + "/"
        + todoComparisons;
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
        parser.addArgument("--NotTransitive", "-nt")
                .dest("transitivityInference")
                .help("Tells the program to not infer equivalences from results computed previously in the run, not taking advantage of transitivity. This forces the program to go through all comparisons.")
                .action(Arguments.storeFalse())
                .setDefault(true);
                parser.addArgument("--threads", "-t")
                .dest("threads")
                .help("how many threads are going to be working in parallel. The default is the maximum available processors allowed by the OS.")
                .type(int.class)
                .setDefault(Runtime.getRuntime().availableProcessors());
        return parser;
    }
}

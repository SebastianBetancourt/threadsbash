package edu.avispa;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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

    public static void main(String[] args) throws InterruptedException {

        ArgumentParser parser = ArgumentParsers.newFor("ThreadSpawner").build()
                .description(
                        "Program that spawns Java processes in parallel, by executing a .jar file with certain parameters. The purpose is to parallelize bisimulation equivalence checking between a set of timed automata, in a everyone-vs-everyone round robin fashion.");
        parser.addArgument("-E")
                .dest("equivalenceProgram")
                .required(true);
        parser.addArgument("-F")
                .dest("automataFolder")
                .required(true);
        parser.addArgument("-o")
                .dest("outputFile")
                .setDefault("~/output.txt");
        parser.addArgument("-l")
                .dest("logFile")
                .setDefault("~/log.txt");
        parser.addArgument("-d")
                .dest("runDescription")
                .setDefault("");
        parser.addArgument("--ModulationPeriod")
                .dest("modulationPeriod")
                .type(int.class)
                .setDefault(5);
        parser.addArgument("--MemoryUsagePerProcess", "-m")
                .dest("memoryUsagePerProcess")
                .type(int.class)
                .setDefault(3072);

        MutuallyExclusiveGroup threadPolicies = parser.addMutuallyExclusiveGroup("Thread pool size policy");
        threadPolicies.addArgument("--FixedThreads")
                .dest("fixedThreads")
                .type(int.class);
        threadPolicies.addArgument("--ModulateThreads")
                .nargs(2)
                .dest("bounds")
                .type(int.class);
        threadPolicies.addArgument("--CalculateFixedThreads")
                .dest("calculateFixedThreads")
                .action(Arguments.storeTrue());

        Map<String, Object> res = null;

        try {
            res = parser.parseArgs(args).getAttrs();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        SystemInfo si = new SystemInfo();
        GlobalMemory memory = si.getHardware().getMemory();

        int initialPoolsize = 0;
        String policy = "";

        if (res.get("fixedThreads") != null) {
            initialPoolsize = (int) res.get("fixedThreads");
            policy = "FixedThreads";
        } else if (res.get("bounds") != null) {
            initialPoolsize = 1;
            policy = "Modulated";
        } else {
            long memoryUsagePerProcess = 1000000L * (int) res.get("memoryUsagePerProcess");
            initialPoolsize = (int) Math.floor(memory.getTotal() / memoryUsagePerProcess);
            policy = "CalculatedFixedThreads";
        }

        final Path WORK_DIR = Paths.get("");

        File outputFile = WORK_DIR
                .resolve(((String) res.get("outputFile")).replaceFirst("^~", System.getProperty("user.home"))).toFile();
        File logFile = WORK_DIR
                .resolve(((String) res.get("logFile")).replaceFirst("^~", System.getProperty("user.home"))).toFile();
        File equivalenceProgram = WORK_DIR
                .resolve(((String) res.get("equivalenceProgram")).replaceFirst("^~", System.getProperty("user.home")))
                .toFile();
        File automataFolder = new File(
                ((String) res.get("automataFolder")).replaceFirst("^~", System.getProperty("user.home")));
        String runDescription = (String) res.get("runDescription");

        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(initialPoolsize);

        final String[] pathnames = automataFolder.list();
        assert pathnames != null;
        int n = pathnames.length;

        AtomicInteger succesfulComparisons = new AtomicInteger();
        ArrayList<String> bisimilarList = new ArrayList<>();

        long start = System.currentTimeMillis();

        int totalComparisons = (n * (n - 1)) / 2;

        long mainPid = ProcessHandle.current().pid();

        try {
            FileWriter fwLog = new FileWriter(logFile.getAbsolutePath(), true);

            fwLog.write(String.join(",", new String[] { "START", String.valueOf(start), automataFolder.getPath(),
                    "total: " + totalComparisons, "main pid " + mainPid, policy, "\n" }));
            fwLog.write(String.join(",", new String[] { "DESCRIPTION", runDescription, "\n" }));
            fwLog.flush();
            fwLog.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        CountDownLatch latch = new CountDownLatch(totalComparisons);

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                ProcessTask processTask = null;
                try {
                    processTask = new ProcessTask(totalComparisons, succesfulComparisons, bisimilarList, automataFolder,
                            pathnames[i], pathnames[j], outputFile, logFile, memory, equivalenceProgram, latch);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                assert processTask != null;
                executor.execute(processTask);

            }
        }

        executor.shutdown();

        if (policy == "Modulated") {
            ArrayList<Integer> bounds = (ArrayList<Integer>) res.get("bounds");
            final long MODULATION_PERIOD = (long) 1000 * (int) res.get("modulationPeriod"); // modulation every
                                                                                            // MODULATION_PERIOD
                                                                                            // milliseconds
            final int MAXIMUM_POOLSIZE = Runtime.getRuntime().availableProcessors();
            final int USED_MEMORY_LOWER_BOUND = bounds.get(0); // in percentage
            final int USED_MEMORY_UPPER_BOUND = bounds.get(1); // in percentage

            long timer = System.currentTimeMillis();

            int threadPoolSize = initialPoolsize;

            while (latch.getCount() > 0) {
                long now = System.currentTimeMillis();
                if (now - timer > MODULATION_PERIOD) {
                    double usedMemory = 100 - (100 * memory.getAvailable() / memory.getTotal());
                    System.out.print(now + ", " + usedMemory + "% used memory");
                    if (usedMemory > USED_MEMORY_UPPER_BOUND && threadPoolSize > 1) {
                        threadPoolSize--;
                        executor.setCorePoolSize(threadPoolSize);
                        executor.setMaximumPoolSize(threadPoolSize);
                        System.out.print(", modulating down to thread pool size " + threadPoolSize);
                    } else if (usedMemory < USED_MEMORY_LOWER_BOUND && threadPoolSize < MAXIMUM_POOLSIZE) {
                        threadPoolSize++;
                        executor.setMaximumPoolSize(threadPoolSize);
                        executor.setCorePoolSize(threadPoolSize);
                        System.out.print(", modulating up to thread pool size " + threadPoolSize);
                    }
                    System.out.println();
                    timer = System.currentTimeMillis();
                }
            }
        }

        latch.await();

        long end = System.currentTimeMillis();

        try {
            FileWriter fwLog = new FileWriter(logFile, true);

            fwLog.write(String.join(",",
                    new String[] { "DONE", String.valueOf(end),
                            succesfulComparisons + "/" + totalComparisons + " successful", String.valueOf(end - start),
                            "\n" }));
            fwLog.flush();
            fwLog.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}

package edu.avispa;

import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

public class ProcessTask implements Runnable {

    private final ArrayList<String> bisimilarList;
    private final File automataFolder;
    private final String automatonA;
    private final String automatonB;
    private final File equivalenceProgram;
    private final AtomicInteger succesfulComparisons;
    private final Logger logger;
    private CountDownLatch remainingComparisons;
    private int totalComparisons;
    private MemoryManager memory;
    private ThreadPoolExecutor executor;

    ProcessTask(int totalComparisons,
            File automataFolder,
            String automatonA,
            String automatonB,
            File equivalenceProgram,
            Logger logger,
            CountDownLatch remainingComparisons,
            AtomicInteger succesfulComparisons,
            ArrayList<String> bisimilarList,
            MemoryManager memory,
            ThreadPoolExecutor executor) {
                this.totalComparisons = totalComparisons;
        this.succesfulComparisons = succesfulComparisons;
        this.bisimilarList = bisimilarList;
        this.automataFolder = automataFolder;
        this.automatonA = automatonA;
        this.automatonB = automatonB;
        this.logger = logger;
        this.equivalenceProgram = equivalenceProgram;
        this.remainingComparisons = remainingComparisons;
        this.memory= memory;
        this.executor = executor;
    }

    public void run() {

        try {
            logger.output(this.automatonA.concat(" ").concat(automatonB));

            String[] cmd = { "java",
                    "-Djava.library.path=PATH/x64_linux-gnu",
                    "-cp",
                    this.equivalenceProgram.getAbsolutePath(),
                    "main.Main",
                    this.automataFolder.getAbsolutePath().concat(File.separator).concat(this.automatonA),
                    this.automataFolder.getAbsolutePath().concat(File.separator).concat(this.automatonB), };

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectError(new File("/dev/null"));
            Process p = pb.start();

            long start = System.currentTimeMillis();

            logger.log("PROCESS START", automatonA, automatonB, memory.getUsedMemory() + "% used memory");

            String progress = (totalComparisons - remainingComparisons.getCount() + 1) + "/" + totalComparisons;

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = null;

            stdInput.readLine(); // Done to skip the initial output lines ImplThesis.jar creates, which are very long
            stdInput.readLine();
            stdInput.readLine();

            while ((line = stdInput.readLine()) != null) {
                    long now = System.currentTimeMillis();
                    long elapsedTime = now - start;
                if (line.startsWith("Result of bisimulation check:")) {
                    String bisimilarity = "";
                    if (line.contains("Result of bisimulation check: true")) {
                        bisimilarity = "BISIM TRUE";
                        this.bisimilarList.add(automatonA.concat("<->").concat(automatonB));
                        logger.output(automatonA, automatonB, elapsedTime, bisimilarList.size(), String.join("  ", this.bisimilarList));
                    } else if (line.startsWith("Result of bisimulation check: false")) {
                        bisimilarity = "BISIM FALSE";
                        logger.output(automatonA, automatonB, elapsedTime);
                    }
                    logger.log("PROCESS SUCCESSFUL", automatonA, automatonB, bisimilarity, progress, elapsedTime);
                    succesfulComparisons.incrementAndGet();
                }

                if (memory.modulatingDown()) {
                    p.destroy();
                    executor.submit(this);
                    stdInput.close();
                    logger.log("MODULATION", automatonA, automatonB, "thread killing itself", memory.getUsedMemory() + "% used memory");
                    return;
                } 
    
            }

            p.waitFor();
            int exitValue = p.exitValue();
            if (exitValue != 0) {
                long end = System.currentTimeMillis();
                logger.log("PROCESS ERROR", end, automatonA, automatonB,
                "EXIT VALUE " + exitValue, progress, memory.getUsedMemory() + "% used memory");
            }
            stdInput.close();
            this.remainingComparisons.countDown();

        } catch (Exception e) {
            logger.log("ERROR", System.currentTimeMillis(), automatonA, automatonB,
            e.getLocalizedMessage() );
            this.remainingComparisons.countDown();
            e.printStackTrace();
        }

    }
}

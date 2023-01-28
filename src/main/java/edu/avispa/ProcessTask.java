package edu.avispa;

import java.io.*;

public class ProcessTask implements Runnable {

    private final String automatonA;
    private final String automatonB;
    private final int thTry;

    ProcessTask(String automatonA, String automatonB, int thTry) {
        this.automatonA = automatonA;
        this.automatonB = automatonB;
        this.thTry = thTry;
    }

    public void run() {
        Logger logger = MainThread.logger;
        final String comparisonId = this.automatonA.concat("-").concat(automatonB).concat("-" + thTry);
        try {

            logger.output(this.automatonA.concat(" ").concat(automatonB));

            String[] cmd = { "java",
                    "-Djava.library.path=PATH/x64_linux-gnu",
                    "-cp",
                    MainThread.equivalenceProgram.getAbsolutePath(),
                    "main.Main",
                    MainThread.automataFolder.getAbsolutePath().concat(File.separator).concat(this.automatonA),
                    MainThread.automataFolder.getAbsolutePath().concat(File.separator).concat(this.automatonB), };

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectError(new File("/dev/null"));
            Process p = pb.start();

            long start = System.currentTimeMillis();

            logger.log("PROCESS START", comparisonId);

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = null;

            while ((line = stdInput.readLine()) != null) {
                long now = System.currentTimeMillis();
                long elapsedTime = now - start;
                if (line.startsWith("Result of bisimulation check:")) {
                    String bisimilarity = "";
                    if (line.contains("Result of bisimulation check: true")) {
                        bisimilarity = "BISIM TRUE";
                        MainThread.bisimilarList.add(automatonA.concat("<->").concat(automatonB));
                        logger.output(automatonA, automatonB, elapsedTime, MainThread.bisimilarList.size(),
                                String.join("  ", MainThread.bisimilarList));
                    } else if (line.startsWith("Result of bisimulation check: false")) {
                        bisimilarity = "BISIM FALSE";
                        logger.output(automatonA, automatonB, elapsedTime);
                    }
                    logger.log("PROCESS SUCCESSFUL", comparisonId, bisimilarity, getTotalProgress(),
                            (elapsedTime / 1000.0) + "s");
                    MainThread.succesfulComparisons.incrementAndGet();
                }
            }

            p.waitFor();
            int exitValue = p.exitValue();
            if (exitValue != 0) {
                MainThread.modulateDown();
                long now = System.currentTimeMillis();
                long elapsedTime = now - start;
                logger.log("PROCESS ERROR", comparisonId, "EXIT VALUE " + exitValue, getTotalProgress(),
                        (elapsedTime / 1000.0) + "s");
                if (thTry < 10) {
                    MainThread.executor.submit(new ProcessTask(automatonA, automatonB, thTry + 1));
                    logger.log("REQUEUEING", comparisonId);
                }

            }
            if (exitValue == 0 || thTry >= 10) {
                MainThread.remainingComparisons.countDown();
            }

            stdInput.close();

        } catch (Exception e) {
            logger.log("ERROR", comparisonId,
                    e.getLocalizedMessage());
            MainThread.remainingComparisons.countDown();
            e.printStackTrace();
        }

    }

    private String getTotalProgress(){
        return (MainThread.totalComparisons - MainThread.remainingComparisons.getCount() + 1) + "/"
        + MainThread.totalComparisons;
    }
}

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
        final String comparisonId = this.automatonA.concat("-").concat(automatonB).concat("-"+thTry);
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

            String progress = (MainThread.totalComparisons - MainThread.remainingComparisons.getCount() + 1) + "/"
                    + MainThread.totalComparisons;

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line = null;

            // stdInput.readLine(); // Done to skip the initial output lines ImplThesis.jar
            // creates, which are very long
            // stdInput.readLine();
            // stdInput.readLine();

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
                    logger.log("PROCESS SUCCESSFUL", comparisonId, bisimilarity, progress,
                            (elapsedTime / 1000.0) + "s");
                    MainThread.succesfulComparisons.incrementAndGet();
                }

                if (false && MainThread.usedMemory >= MainThread.upperBound) {
                    p.destroy();

                    stdInput.close();
                    logger.log("SUICIDE", comparisonId);
                    return;
                }

            }

            p.waitFor();
            int exitValue = p.exitValue();
            if (exitValue != 0) {
                long end = System.currentTimeMillis();
                MainThread.modulate();
                logger.log("PROCESS ERROR", comparisonId, end,
                "EXIT VALUE " + exitValue, progress);
                if(thTry < 10){
                    MainThread.executor.submit(new ProcessTask(automatonA, automatonB, thTry+1));
                    logger.log("REQUEUEING", comparisonId);
                }

            } else if(exitValue == 0 || thTry >= 10){
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
}

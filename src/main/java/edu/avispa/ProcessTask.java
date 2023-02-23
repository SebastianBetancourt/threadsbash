package edu.avispa;

import java.io.*;

public class ProcessTask implements Runnable {

    private final String automatonA;
    private final String automatonB;

    ProcessTask(String automatonA, String automatonB) {
        this.automatonA = automatonA;
        this.automatonB = automatonB;
    }

    public void run() {
        Logger logger = MainThread.logger;
        final String comparisonId = this.automatonA.concat("-").concat(automatonB);
        long start = System.currentTimeMillis();
        String bisimilarity = "";

        if(MainThread.transitivityInference){
            Class classOfA = Class.getClassOf(automatonA);
            boolean alreadyEquivalent = classOfA.equivalent.contains(automatonB);
            boolean alreadyNotEquivalent = classOfA.notEquivalent.contains(automatonB);
            if(alreadyEquivalent || alreadyNotEquivalent){
                if(alreadyEquivalent){
                    bisimilarity = "BISIM TRUE";
                    //MainThread.bisimilarList.add(automatonA.concat("<->").concat(automatonB));
                } else if (alreadyNotEquivalent) {
                    bisimilarity = "BISIM FALSE";
                }
                logger.log("PROCESS INFERRED", comparisonId, bisimilarity, MainThread.getTotalProgress(), MainThread.getAvgTimePerComparison(), (System.currentTimeMillis() - start/ 1000.0) + "s");
                MainThread.remainingComparisons.countDown();
                MainThread.inferredComparisons.incrementAndGet();
                return;
            }
        }
        try {
            String[] cmd = { "java",
                    //"-Xmx3072m",
                    "-Djava.library.path=PATH/x64_linux-gnu",
                    "-XX:ErrorFile=/dev/null",
                    "-cp",
                    MainThread.equivalenceProgram.getAbsolutePath(),
                    "main.Main",
                    MainThread.automataFolder.getAbsolutePath().concat(File.separator).concat(this.automatonA),
                    MainThread.automataFolder.getAbsolutePath().concat(File.separator).concat(this.automatonB), };

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectError(new File("/dev/null"));
            Process p = pb.start();

            

            logger.log("PROCESS START", comparisonId);
            BufferedReader output = new BufferedReader(new InputStreamReader(p.getInputStream()));
            p.waitFor();
            String line = null;

            while ((line = output.readLine()) != null) {
                    Class classOfB =  Class.getClassOf(automatonB);
                    Class classOfA = Class.getClassOf(automatonA);

                    if (line.equals("true")) {
                        bisimilarity = "BISIM TRUE";
                        //MainThread.bisimilarList.add(automatonA.concat("<->").concat(automatonB));
                        classOfA.equivalent.addAll(classOfB.equivalent);
                        classOfA.notEquivalent.addAll(classOfB.notEquivalent);
                        if(!classOfA.equals(classOfB)){
                            Class.equivalentClasses.remove(classOfB);
                        }
                    } else if (line.equals("false")) {
                        bisimilarity = "BISIM FALSE";
                        classOfA.notEquivalent.addAll(classOfB.equivalent);
                        classOfB.notEquivalent.addAll(classOfA.equivalent);
                    }
                    MainThread.computedComparisons.incrementAndGet();
            }

            int exitValue = p.exitValue();
            long elapsedTime = System.currentTimeMillis() - start;
            if (exitValue != 0) {
                logger.log("PROCESS ERROR", comparisonId, "EXIT VALUE " + exitValue, MainThread.getTotalProgress(),
                (elapsedTime / 1000.0) + "s");
                MainThread.failedComparisons.incrementAndGet();
            } else {
                logger.log("PROCESS SUCCESSFUL", comparisonId, bisimilarity, MainThread.getTotalProgress(), MainThread.getAvgTimePerComparison(),
                String.format("%.3f", (elapsedTime / 1000.0)) + "s");
                MainThread.remainingComparisons.countDown();
            }
            output.close();

        } catch (Exception e) {
            logger.log("ERROR", comparisonId,
                    e.getLocalizedMessage());
            MainThread.remainingComparisons.countDown();
            e.printStackTrace();
        }

    }

}

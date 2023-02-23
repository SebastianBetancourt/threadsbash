package edu.avispa;

import java.io.Closeable;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class Logger implements Closeable{
    private FileWriter log;

    Logger(Path logFolder, Path outputFolder){
        String fileId = ZonedDateTime.now(ZoneOffset.of("-5")).format(DateTimeFormatter.ofPattern("ddHHmm"));
        try {
            String logPath = logFolder+"/log_"+fileId+".csv";
            this.log = new FileWriter(logPath, true);
            System.out.println("logging to: "+logPath);
            log.write("timestamp,event,\n");
        } catch (IOException e) {
            e.printStackTrace();
        }        
    }

    public void log(Object... logEntry){
        write(log, logEntry);
    }

    private synchronized void write(FileWriter w, Object[] entry) {
        String timestamp = ZonedDateTime.now(ZoneOffset.of("-5")).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_ZONED_DATE_TIME);

        try {
            w.write(timestamp+",");
            for (Object s : entry) {
                w.write(s.toString()+",");
            }
            w.write("\n");
            w.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close(){
        try {
            log.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

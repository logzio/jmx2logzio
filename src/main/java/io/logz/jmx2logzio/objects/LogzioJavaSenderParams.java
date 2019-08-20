package io.logz.jmx2logzio.objects;

import com.google.common.hash.Hashing;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class LogzioJavaSenderParams {


    private String url = "https://listener.logz.io:8071";
    public static String TYPE = "jmx2LogzioType";
    private String token;
    private int threadPoolSize = 3;
    private boolean debug = true;
    private boolean compressRequests = true;
    private boolean fromDisk = true;

    // In-memory queue parameters
    private int inMemoryQueueCapacityInBytes = 1024 * 1024 * 100;
    private int logsCountLimit = -1;

    // Disk queue parameters
    private File queueDir;
    private int fileSystemFullPercentThreshold = 98;
    private int gcPersistedQueueFilesIntervalSeconds = 30;
    private int diskSpaceCheckInterval = 1000;

    /**
     * Constructor automatically create metrics directory
     */

    public void setQueueDir() {
        String tokenTypeSha = Hashing.sha256()
                .hashString(TYPE + token, StandardCharsets.UTF_8)
                .toString();

        String queuePath = System.getProperty("user.dir");
        queuePath += queuePath.endsWith("/") ? "" : "/";
        queuePath += "metrics" + tokenTypeSha;
        this.queueDir = new File(queuePath);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getType() {
        return TYPE;
    }


    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
        setQueueDir();
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isCompressRequests() {
        return compressRequests;
    }

    public int getInMemoryQueueCapacityInBytes() {
        return inMemoryQueueCapacityInBytes;
    }

    public void setInMemoryQueueCapacityInBytes(int inMemoryQueueCapacityInBytes) {
        this.inMemoryQueueCapacityInBytes = inMemoryQueueCapacityInBytes;
    }

    public int getLogsCountLimit() {
        return logsCountLimit;
    }

    public void setLogsCountLimit(int logsCountLimit) {
        this.logsCountLimit = logsCountLimit;
    }

    public int getDiskSpaceCheckInterval() {
        return diskSpaceCheckInterval;
    }

    public void setDiskSpaceCheckInterval(int diskSpaceCheckInterval) {
        this.diskSpaceCheckInterval = diskSpaceCheckInterval;
    }

    public File getQueueDir() {
        return queueDir;
    }

    public int getFileSystemFullPercentThreshold() {
        return fileSystemFullPercentThreshold;
    }

    public void setFileSystemFullPercentThreshold(int fileSystemFullPercentThreshold) {
        this.fileSystemFullPercentThreshold = fileSystemFullPercentThreshold;
    }

    public int getGcPersistedQueueFilesIntervalSeconds() {
        return gcPersistedQueueFilesIntervalSeconds;
    }

    public void setGcPersistedQueueFilesIntervalSeconds(int gcPersistedQueueFilesIntervalSeconds) {
        this.gcPersistedQueueFilesIntervalSeconds = gcPersistedQueueFilesIntervalSeconds;
    }

    public boolean isFromDisk() {
        return fromDisk;
    }

    public void setFromDisk(boolean fromDisk) {
        this.fromDisk = fromDisk;
    }
}

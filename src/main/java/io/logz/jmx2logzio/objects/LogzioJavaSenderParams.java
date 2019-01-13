package io.logz.jmx2logzio.objects;

import java.io.File;

public class LogzioJavaSenderParams {

    public static String LISTENER_URL = "logzioJavaSender.url";
    public static String LOGZIO_TOKEN = "logzioJavaSender.token";
    public static String FROM_DISK = "logzioJavaSender.from-disk";
    public static String IN_MEMORY_QUEUE_CAPACITY = "logzioJavaSender.in-memory-queue-capacity";
    public static String LOGS_COUNT_LIMIT = "logzioJavaSender.log-count-limit";
    public static String DISK_SPACE_CHECK_INTERVAL = "logzioJavaSender.disk-space-checks-interval";
    public static String QUEUE_DIR = "logzioJavaSender.queue-dir";
    public static String FILE_SYSTEM_SPACE_LIMIT = "logzioJavaSender.file-system-full-percent-threshold";
    public static String CLEAN_SENT_METRICS_INTERVAL = "logzioJavaSender.clean-sent-metrics-interval";


    private String url = "https://listener.logz.io:8071";
    private String type = "jmx2LogzioType";
    private String Token;
    private int threadPoolSize = 3;
    private boolean debug = true;
    private boolean compressRequests = true;
    private boolean fromDisk = true;

    // In-memory queue parameters
    private int inMemoryQueueCapacityInBytes = 1024 * 1024 * 100;
    private int logsCountLimit = -1;
    private int diskSpaceCheckInterval = 1000;

    // Disk queue parameters
    private File queueDir;
    private int fileSystemFullPercentThreshold = 98;
    private int gcPersistedQueueFilesIntervalSeconds = 30;

    public LogzioJavaSenderParams() {
        String queuePath = System.getProperty("user.dir");
        queuePath += queuePath.endsWith("/") ? "" : "/";
        queuePath += "logs";
        this.queueDir = new File(queuePath);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getType() {
        return type;
    }


    public String getToken() {
        return Token;
    }

    public void setToken(String token) {
        Token = token;
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

    public void setQueueDir(File queueDir) {
        this.queueDir = queueDir;
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

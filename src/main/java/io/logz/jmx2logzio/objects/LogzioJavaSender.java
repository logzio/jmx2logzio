package io.logz.jmx2logzio.objects;

public class LogzioJavaSender {

    private String url = "https://listener.logz.io:8071";
    private String type = "jmx2LogzioType";
    private String Token;
    private int threadPoolSize = 3;

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

}

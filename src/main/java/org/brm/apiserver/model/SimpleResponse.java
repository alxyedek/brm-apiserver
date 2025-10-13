package org.brm.apiserver.model;


public class SimpleResponse {
    private String hostString;
    private String pathString;
    private String timeString;
    private Integer randomInteger;
    private String threadID;

    /// constructors
    /// -----------
    public SimpleResponse() {

    }

    public SimpleResponse(String hostString, String pathString, String timeString, Integer randomInteger, String threadID) {
        this.hostString = hostString;
        this.pathString = pathString;
        this.timeString = timeString;
        this.randomInteger = randomInteger;
        this.threadID = threadID;
    }


    /// getters
    /// -------
    public String getHostString() {
        return hostString;
    }

    public String getPathString() {
        return pathString;
    }

    public String getTimeString() {
        return timeString;
    }

    public Integer getRandomInteger() {
        return randomInteger;
    }

    public String getThreadID() {
        return threadID;
    }

    /// setters
    /// -------
    public void setHostString(String hostString) {
        this.hostString = hostString;
    }

    public void setPathString(String pathString) {
        this.pathString = pathString;
    }

    public void setTimeString(String timeString) {
        this.timeString = timeString;
    }

    public void setRandomInteger(Integer randomInteger) {
        this.randomInteger = randomInteger;
    }

    public void setThreadID(String threadID) {
        this.threadID = threadID;
    }

    @Override
    public String toString() {
        return "SimpleResponse [hostString=" + hostString + ", pathString=" + pathString + ", timeString=" + timeString + ", randomInteger=" + randomInteger + ", threadID=" + threadID + "]";
    }
}
package org.montgoemymoon.hermesscheduler.raft;

public class RaftMessage {
    private String type; // "RequestVote", "RequestVoteResponse", "Heartbeat", "HeartbeatResponse"
    private int term;
    private String candidateId;
    private boolean voteGranted;
    private String leaderId;

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public boolean isVoteGranted() {
        return voteGranted;
    }

    public int getTerm() {
        return term;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public void setLeaderId(String leaderId) {
        this.leaderId = leaderId;
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public void setVoteGranted(boolean voteGranted) {
        this.voteGranted = voteGranted;
    }
}

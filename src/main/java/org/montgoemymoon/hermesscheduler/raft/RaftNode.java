package org.montgoemymoon.hermesscheduler.raft;

import org.montgoemymoon.hermesscheduler.task.TaskScheduler;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.rmi.server.LogStream.log;

public class RaftNode {
    private final String nodeId;
    private final List<String> peerNodes; // 集群所有节点地址 "host:port"
    private int currentTerm = 0;
    private String votedFor = null;
    private NodeRole role = NodeRole.FOLLOWER;
    private long lastHeartbeatTime = System.currentTimeMillis();
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private Random random = new Random();
    private RaftServer server;

    // 选举超时（150~300ms），心跳间隔 50ms
    private volatile boolean running = true;
    private ScheduledFuture<?> electionTimeoutFuture;

    public RaftNode(String nodeId, List<String> peerNodes, int port) {
        this.nodeId = nodeId;
        this.peerNodes = new ArrayList<>(peerNodes);
        this.server = new RaftServer(this,port);
        new Thread(server).start();
        startElectionTimeout();
    }

    private void startElectionTimeout() {
        if (electionTimeoutFuture != null) electionTimeoutFuture.cancel(false);
        int timeout = 150 + random.nextInt(150);
        electionTimeoutFuture = executor.schedule(this::onElectionTimeout, timeout, TimeUnit.MILLISECONDS);
    }

    private void onElectionTimeout() {
        if (role == NodeRole.LEADER) return; // 仅当 follower 或 candidate 超时
        becomeCandidate();
        startElection();
        startElectionTimeout();
    }

    private void becomeCandidate() {
        role = NodeRole.CANDIDATE;
        currentTerm++;
        votedFor = nodeId;
        lastHeartbeatTime = System.currentTimeMillis();
        log("Became candidate for term " + currentTerm);
    }

    private void startElection() {
        final int[] votesReceived = {1}; // 自己投票
        for (String peer : peerNodes) {
            RaftMessage request = new RaftMessage();
            request.setType("RequestVote");
            request.setTerm(currentTerm);
            request.setCandidateId(nodeId);
            sendMessage(peer, request, response -> {
                if (response.isVoteGranted()) {
                    votesReceived[0]++;
                    if (votesReceived[0] > peerNodes.size() / 2 && role == NodeRole.CANDIDATE) {
                        becomeLeader();
                    }
                }
            });
        }
    }

    private void becomeLeader() {
        role = NodeRole.LEADER;
        log("Became leader for term " + currentTerm);
        // 取消选举超时，启动心跳
        if (electionTimeoutFuture != null) electionTimeoutFuture.cancel(false);
        startHeartbeat();
        // 通知 UI 和调度器启动
        onBecomeLeader();
    }

    private void startHeartbeat() {
        executor.scheduleAtFixedRate(() -> {
            if (role == NodeRole.LEADER) {
                for (String peer : peerNodes) {
                    RaftMessage heartbeat = new RaftMessage();
                    heartbeat.setType("Heartbeat");
                    heartbeat.setTerm(currentTerm);
                    heartbeat.setLeaderId(nodeId);
                    sendMessage(peer, heartbeat, resp -> {
                        if (resp.getTerm() > currentTerm) {
                            stepDown(resp.getTerm());
                        }
                    });
                }
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
    }

    private void stepDown(int higherTerm) {
        if (higherTerm > currentTerm) {
            currentTerm = higherTerm;
            role = NodeRole.FOLLOWER;
            votedFor = null;
            startElectionTimeout();
            log("Stepped down to follower, new term=" + currentTerm);
        }
    }

    // 处理收到的 Raft 消息
    public RaftMessage processMessage(RaftMessage msg, String remoteAddr) {
        switch (msg.getType()) {
            case "RequestVote":
                handleRequestVote(msg, remoteAddr);
                break;
            case "Heartbeat":
                handleHeartbeat(msg);
                break;
        }
        return msg;
    }

    private void handleRequestVote(RaftMessage msg, String remoteAddr) {
        boolean grant = false;
        if (msg.getTerm() > currentTerm) {
            stepDown(msg.getTerm());
        }
        if (msg.getTerm() == currentTerm && (votedFor == null || votedFor.equals(msg.getCandidateId()))) {
            grant = true;
            votedFor = msg.getCandidateId();
            startElectionTimeout(); // 重置超时
        }
        RaftMessage response = new RaftMessage();
        response.setType("RequestVoteResponse");
        response.setTerm(currentTerm);
        response.setVoteGranted(grant);
        sendMessage(remoteAddr, response, null);
    }

    private void handleHeartbeat(RaftMessage msg) {
        if (msg.getTerm() > currentTerm) {
            stepDown(msg.getTerm());
        }
        if (role != NodeRole.FOLLOWER && msg.getTerm() == currentTerm) {
            role = NodeRole.FOLLOWER;
            votedFor = null;
        }
        lastHeartbeatTime = System.currentTimeMillis();
        startElectionTimeout(); // 收到心跳重置超时
        // 发送心跳响应
        RaftMessage resp = new RaftMessage();
        resp.setType("HeartbeatResponse");
        resp.setTerm(currentTerm);
        sendMessage(msg.getLeaderId() + ":8888", resp, null);
    }

    private void sendMessage(String target, RaftMessage msg, Consumer<RaftMessage> callback) {
        // 简化：使用 Socket 发送，异步接收
        executor.submit(() -> {
            try (Socket socket = new Socket(target.split(":")[0], Integer.parseInt(target.split(":")[1]));
                 ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
                oos.writeObject(msg);
                if (callback != null) {
                    RaftMessage response = (RaftMessage) ois.readObject();
                    callback.accept(response);
                }
            } catch (Exception e) { /* 忽略网络异常 */ }
        });
    }

    // 供 UI 调用
    public NodeRole getRole() { return role; }
    public int getCurrentTerm() { return currentTerm; }
    public List<String> getPeers() {
        return new ArrayList<>(peerNodes);  // 返回副本，避免外部修改
    }
    // 当成为 Leader 时回调（启动调度器）
    private void onBecomeLeader() {
        TaskScheduler.getInstance().startScheduling();
    }
}

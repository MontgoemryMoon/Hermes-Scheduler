package org.montgoemymoon.hermesscheduler.raft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RaftServer implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(RaftServer.class);
    private final RaftNode raftNode;
    private final int port;
    private volatile boolean running = true;
    private ServerSocket serverSocket;
    private final ExecutorService handlerPool = Executors.newCachedThreadPool();

    // 端口可以从 RaftNode 的配置中获取，这里简单写死或者通过构造函数传入
    public RaftServer(RaftNode raftNode,int port) {
        this.raftNode = raftNode;
        this.port = port;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("RaftServer started on port " + port);
            while (running && !Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                handlerPool.submit(() -> handleClient(clientSocket));
            }
        } catch (Exception e) {
            if (running) {
                log.error(e.getMessage());
            }
        } finally {
            closeResources();
        }
    }

    private void handleClient(Socket socket) {
        try (ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {
            // 读取 RaftMessage
            RaftMessage msg = (RaftMessage) ois.readObject();
            // 交给 RaftNode 处理，并获取响应（如果需要）
            RaftMessage response = raftNode.processMessage(msg, socket.getInetAddress().getHostAddress());
            if (response != null) {
                oos.writeObject(response);
                oos.flush();
            }
        } catch (Exception e) {
            // 忽略网络异常，打印日志
            log.error("Error handling client: {}", e.getMessage());
        }
    }

    private void closeResources() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            handlerPool.shutdown();
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public void stop() {
        running = false;
        closeResources();
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {}
        }
    }
}
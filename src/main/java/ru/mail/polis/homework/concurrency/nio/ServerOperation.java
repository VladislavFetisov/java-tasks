package ru.mail.polis.homework.concurrency.nio;

import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Класс, в котором хранится вся информация о входящем запросе.
 * Его статус, полученные данные, результат и так далее
 */
public class ServerOperation {
    private final int requestId;
    private final List<RequestChunk> requestChunks = new CopyOnWriteArrayList<>();
    private double res;
    private SocketChannel client;
    private ServerState state = ServerState.LOADING;

    public ServerOperation(int requestId) {
        this.requestId = requestId;
    }

    public int getRequestId() {
        return requestId;
    }

    public void setRes(double res) {
        this.res = res;
    }

    public double getRes() {
        return res;
    }

    public void setState(ServerState state) {
        this.state = state;
    }

    public void addOperand(RequestChunk chunk) {
        requestChunks.add(chunk);
    }

    public List<RequestChunk> getRequestChunks() {
        return requestChunks;
    }

    public ServerState getState() {
        return state;
    }

    public SocketChannel getClient() {
        return client;
    }

    public void setClient(SocketChannel client) {
        this.client = client;
    }
}

package ru.mail.polis.homework.concurrency.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Клиент, который принимает список операций и как можно быстрее отправляет это на сервер. (ClientSocket)
 * Нужно отправлять в несколько потоков. Важный момент, одно задание на 1 порт.
 * <p>
 * Так же, он передает информацию серверу, на каком порте он слушает результат (ServerSocket).
 * При этом слушатель серверСокета только 1
 * <p>
 * Каждому запросу присваивается уникальная интовая айдишка. Клиент может быть закрыт.
 */
public class Client {
    public static final int BUFFER_BYTES = 4 * Integer.BYTES + 2 * Integer.BYTES
            + 2 * OperandType.getMaxBytes() + Double.BYTES;
    private final int serverPort;
    private int id;
    private final int maxThreads;
    private final int[] clientPorts;
    private int firstFreePort;
    private volatile boolean isInterrupted;
    private final BlockingQueue<RequestChunk> tasks = new LinkedBlockingQueue<>();
    private final Map<Integer, Result> results = new HashMap<>();
    private final List<Thread> threads = new CopyOnWriteArrayList<>();
    private static final Logger logger = Logger.getLogger(Client.class.getName());
    /**
     * @param clientsPort         массив портов для отправки
     * @param serverPort          порт для принятия
     * @param threadsCountForSend максимальное кол-во потоков, которое можно использовать.
     *                            Дано, что threadsCountForSend >= clientsPort.length
     */
    public Client(int[] clientsPort, int serverPort, int threadsCountForSend) {
        if (clientsPort.length == 0) {
            throw new IllegalArgumentException();
        }
        this.serverPort = serverPort;
        this.maxThreads = threadsCountForSend;
        this.clientPorts = clientsPort;
        Thread receiver = new Receiver();
        threads.add(receiver);
        receiver.start();
    }

    /**
     * Отправляет на сервер операции в несколько потоков. Плюс отправляет порт, на котором будет слушать ответ.
     * Важно, не потеряйте порядок операндов
     * Возвращает Result с отложенным заполнением ответа.
     */
    public Result calculate(List<Operand> operands) {
        int localId = id++;
        int size = operands.size();
        int local = firstFreePort + size;
        for (int i = firstFreePort; i < Math.min(clientPorts.length, local); i++) {
            Thread sender = new Sender(clientPorts[i]);
            threads.add(sender);
            sender.start();
            firstFreePort++;
        }

        for (int i = 0; i < operands.size(); i++) {
            tasks.add(new RequestChunk(operands.get(i), localId, i, size));
        }
        return results.put(localId, new Result(localId));
    }


    /**
     * Возвращает результат, для заданной айдишки запроса
     */
    public Result getResult(int id) {
        return results.get(id);
    }

    /**
     * Закрывает клиента и всего его запросы.
     */
    public void close() {

    }

    public void stop() {
        threads.forEach(Thread::interrupt);
    }

    private class Sender extends Thread {
        private final int port;

        Sender(int port) {
            this.port = port;
        }

        @Override
        public void run() {
            try (SocketChannel client = SocketChannel.open(new InetSocketAddress("localhost", port))) {
                ByteBuffer buffer = ByteBuffer.allocate(BUFFER_BYTES);
                while (!isInterrupted) {
                    RequestChunk request = tasks.take();
                    buffer.clear();
                    buffer.putInt(serverPort);
                    buffer.putInt(request.getOrder());
                    buffer.putInt(request.getRequestId());
                    buffer.putInt(request.getLength());
                    operandToBuffer(buffer, request.getOperand());
                    buffer.flip();
                    logger.log(Level.INFO, Arrays.toString(buffer.array()));
                    logger.log(Level.INFO, "Buffer limit: " + buffer.limit());
                    client.write(buffer);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    //if (request.getOrder() == 0) {
//                        results.get(request.getRequestId()).setState(ClientState.SENT);
//                    } else {
//                        results.get(request.getRequestId()).setState(ClientState.SENDING);
//                    }
    private void operandToBuffer(ByteBuffer buffer, Operand operation) {
        writeOperand(operation.getOperationFirst(), buffer);
        buffer.putDouble(operation.getA());
        writeOperand(operation.getOperationSecond(), buffer);
    }

    private void writeOperand(OperandType operand, ByteBuffer buffer) {
        if (operand == null) {
            buffer.putInt(-1);
            return;
        }
        buffer.putInt(operand.toString().length());
        buffer.put(operand.toString().getBytes());
    }

    private class Receiver extends Thread {
        @Override
        public void run() {
            try (SocketChannel receiver = SocketChannel.open(new InetSocketAddress("localhost", serverPort))) {
                receiver.configureBlocking(true);
                ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Double.BYTES);
                double value;
                int id;
                while (!isInterrupted) {
                    buffer.clear();
                    receiver.read(buffer);
                    buffer.flip();
                    id = buffer.getInt();
                    value = buffer.getDouble();
                    logger.log(Level.INFO, String.format("Client %s receive result: %f, requestId: %d",
                            receiver.getLocalAddress().toString(), value, id));
                    results.get(id).setValue(value);
                    results.get(id).setState(ClientState.DONE);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }


}

class RequestChunk {
    private final Operand operand;
    private int requestId;
    private final int order;
    private int length;

    public RequestChunk(Operand operand, int requestId, int order, int length) {
        this.operand = operand;
        this.requestId = requestId;
        this.order = order;
        this.length = length;
    }

    public RequestChunk(Operand operand, int order) {
        this.operand = operand;
        this.order = order;
    }

    public int getRequestId() {
        return requestId;
    }

    public Operand getOperand() {
        return operand;
    }

    public int getOrder() {
        return order;
    }

    public int getLength() {
        return length;
    }
}

package ru.mail.polis.homework.concurrency.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

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
    private final int serverPort;
    private final int[] clientsPort;
    private final int maxThreads;
    private final BlockingQueue<Operand> tasks;
    private SocketChannel receiver;
    private int id = 0;
    private final Map<Integer, Result> results;
    private SocketChannel client;

    /**
     * @param clientsPort         массив портов для отправки
     * @param serverPort          порт для принятия
     * @param threadsCountForSend максимальное кол-во потоков, которое можно использовать.
     *                            Дано, что threadsCountForSend >= clientsPort.length
     */
    public Client(int[] clientsPort, int serverPort, int threadsCountForSend) {
        this.serverPort = serverPort;
        this.maxThreads = threadsCountForSend;
        this.clientsPort = clientsPort;
        this.tasks = new LinkedBlockingQueue<>();
        this.results = new ConcurrentHashMap<>();

    }

    /**
     * Отправляет на сервер операции в несколько потоков. Плюс отправляет порт, на котором будет слушать ответ.
     * Важно, не потеряйте порядок операндов
     * Возвращает Result с отложенным заполнением ответа.
     */
    public Result calculate(List<Operand> operands) {//желательно, чтобы использовалось максимальное кол-во потоков
        try {
            client = SocketChannel.open(new InetSocketAddress("localhost", clientsPort[0]));
        } catch (IOException e) {
            e.printStackTrace();
        }
        tasks.addAll(operands);
        new Sender(clientsPort[0], client).start();
        new Sender(clientsPort[1], client).start();
        new Receiver().start();
        return null;
    }

    private class Receiver extends Thread {
        @Override
        public void run() {
            try (SocketChannel receiver = SocketChannel.open(new InetSocketAddress("localhost", serverPort))) {
                receiver.configureBlocking(true);
                ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES);
                receiver.read(buffer);
                buffer.flip();
                System.out.println("RESULT " + buffer.getDouble() + "on " + serverPort);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * Возвращает результат, для заданной айдишки запроса
     */
    public Result getResult(int id) {
        return null;
    }

    /**
     * Закрывает клиента и всего его запросы.
     */
    public void close() {

    }


    private void operandToBuffer(ByteBuffer buffer, Operand operation) {
        OperandType operand = operation.getOperationFirst();
        if (operand == null) {
            buffer.putInt(-1);
        } else {
            buffer.putInt(operand.toString().length());
            buffer.put(operand.toString().getBytes());
        }
        buffer.putDouble(operation.getA());
        operand = operation.getOperationSecond();
        buffer.putInt(operand.toString().length());
        buffer.put(operand.toString().getBytes());
    }

    private class Sender extends Thread {
        private final int port;
        private final SocketChannel client;

        public Sender(int port, SocketChannel client) {
            this.port = port;
            this.client = client;
        }

        @Override
        public void run() {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(40);
                while (true) {
                    Operand operation = tasks.take();
                    buffer.clear();
                    buffer.putInt(serverPort);
                    buffer.putInt(tasks.size());
                    operandToBuffer(buffer, operation);
                    buffer.flip();
                    client.write(buffer);
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        try {
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

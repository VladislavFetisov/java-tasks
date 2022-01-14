package ru.mail.polis.homework.concurrency.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Слушает несколько портов. Принимает список операций, производит с ними вычисления
 * и отправляет результат на указанный порт. Для каждого клиента свой уникальный порт.
 * Чтение из всех сокетов должно происходить в один поток.
 * Отправка результата тоже в один поток, отличный от потока чтения.
 * <p>
 * Вычисления должны производиться в отдельном экзекьюторе.
 * <p>
 * Сервер умеет принимать сигналы о закрытии запроса (должен перестать принимать/вычислять и отправлять что либо
 * по заданному запросу) и о закрытии клиента (должен завершить все вычисления от клиента и закрыть клиентский сокет).
 */
public class Server {
    private static final String POISON_PILL = "POISON_PILL";

    private final int[] serverPorts;
    private final Selector selector;
    private final ExecutorService executorService;
    private final Map<Integer, ConcurrentHashMap<Integer, Operand>> operands;
    private final BlockingQueue<Integer> portsToAnswer;
    private final Map<Integer, ServerSocketChannel> sockets;
    private final Set<SocketChannel> clients;
    private final Map<SocketChannel, ByteBuffer> buffers;
    private final Map<Integer, Double> readyToSend;
    private final BlockingQueue<Runnable> results;
    private volatile boolean shutdownCalled = false;
    private final ReentrantLock lock = new ReentrantLock();

    //поток 1 на чтение из серверных портов порты, поток 1 на запись в серверный порт calculateThreadsCount потоки только на вычисления
    public Server(int[] serverPorts, int calculateThreadsCount) throws IOException {
        this.serverPorts = serverPorts;
        this.selector = Selector.open();
        this.executorService = Executors.newFixedThreadPool(calculateThreadsCount);
        this.operands = new ConcurrentHashMap<>();
        this.portsToAnswer = new LinkedBlockingQueue<>();
        this.sockets = new ConcurrentHashMap<>();
        this.buffers = new ConcurrentHashMap<>();
        this.results = new LinkedBlockingQueue<>();
        this.clients = new HashSet<>();
        this.readyToSend = new ConcurrentHashMap<>();
    }

    /**
     * Можно редактировать метод. Должен вернуть всех клиентов
     */
    public void getClients() {

    }

    private void startServer() {
        try {
            for (int serverPort : serverPorts) {
                ServerSocketChannel server = ServerSocketChannel.open();
                server.configureBlocking(false);
                server.bind(new InetSocketAddress("localhost", serverPort));
                server.register(selector, SelectionKey.OP_ACCEPT);
                sockets.put(serverPort, server);
            }

            new Sender().start();

            Set<SelectionKey> readyKeys;
            Iterator<SelectionKey> iterator;
            while (selector.isOpen()) {
                selector.select();
                readyKeys = selector.selectedKeys();
                iterator = readyKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if (key.isAcceptable()) {
                        register(key);
                    }
                    if (key.isReadable()) {
                        handleRequests(key);
                    }
                    if (key.isWritable()) {
                        sendResult(key);
                    }
                    iterator.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendResult(SelectionKey key) {
        try {
            SocketChannel client = (SocketChannel) key.channel();
            int port = getPort(client.getLocalAddress()); //долго получаем порт.
            if (readyToSend.containsKey(port)) {
                System.out.println("WRITE TO" + client);
                double res = readyToSend.get(port);
                readyToSend.remove(port);
                results.add(() -> {
                    try {
                        ByteBuffer buffer = buffers.get(client);
                        buffer.clear();
                        buffer.putDouble(res);
                        buffer.flip();
                        client.write(buffer);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int getPort(SocketAddress address) {
        String addr = address.toString();
        for (int i = 0; i < addr.length(); i++) {
            if (addr.charAt(i) == ':') {
                return Integer.parseInt(addr.substring(++i));
            }
        }
        throw new IllegalStateException();
    }

    private void register(SelectionKey key) throws IOException {
        SocketChannel client = ((ServerSocketChannel) key.channel()).accept();
        clients.add(client);
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        if (buffers.containsKey(client)) {
            throw new IllegalStateException();
        }
        buffers.put(client, ByteBuffer.allocate(40));
    }

    private void handleRequests(SelectionKey key) {
        try {
            SocketChannel client = (SocketChannel) key.channel();
            ByteBuffer buffer = buffers.get(client);

            client.read(buffer);
            buffer.flip();

            if (buffer.limit() <= Double.BYTES) {//we're trying to read result that was sent
                buffer.clear();
                return;
            }
            System.out.println(client);
            System.out.println(Arrays.toString(buffer.array()));
//            System.out.println(buffer.limit());
//            System.out.println(client);
            readOperand(buffer);
            buffer.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private class Sender extends Thread {
        @Override
        public void run() {
            try {
                while (!interrupted()) {
                    results.take().run();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void readOperand(ByteBuffer buffer) {
        int port = buffer.getInt();
        int order = buffer.getInt();
        int length = buffer.getInt();
        String operandFirst = null;
        byte[] operation;
        if (length != -1) {
            operation = new byte[length];
            for (int i = 0; i < length; i++) {
                operation[i] = buffer.get();
            }
            operandFirst = new String(operation, StandardCharsets.UTF_8);
        }
        double num = buffer.getDouble();
        length = buffer.getInt();
        operation = new byte[length];
        for (int i = 0; i < length; i++) {
            operation[i] = buffer.get();
        }
        Operand operand = new Operand((operandFirst == null) ? null : OperandType.valueOf(operandFirst),
                num,
                OperandType.valueOf(new String(operation, StandardCharsets.UTF_8)));

        operands.putIfAbsent(port, new ConcurrentHashMap<>());
        operands.get(port).put(order, operand);
        if (order == 0) {
            computeAndPutToQueue(port);
        }
    }

    private void computeAndPutToQueue(int port) {
        executorService.execute(() -> {
            double res = operands.get(port)
                    .entrySet()
                    .stream()
                    .sorted((o1, o2) -> Integer.compare(o2.getKey(), o1.getKey()))
                    .map(Map.Entry::getValue)
                    .reduce((o1, o2) -> new Operand(applyBinary(applyUnary(o1),
                            applyUnary(o2), o1.getOperationSecond()), o2.getOperationSecond()))
                    .get()
                    .getA();
            readyToSend.put(port, res);
        });
    }

    private double applyUnary(Operand operand) {
        if (operand.getOperationFirst() == null) {
            return operand.getA();
        }
        double a = operand.getA();
        switch (operand.getOperationFirst()) {
            case LN:
                return Math.log(a);
            case ABS:
                return Math.abs(a);
            case COS:
                return Math.cos(a);
            case EXP:
                return Math.exp(a);
            case SIN:
                return Math.sin(a);
            case TAN:
                return Math.tan(a);
            case PLUS:
                return a;
            case MINUS:
                return -a;
            case SQUARE:
                return a * a;
            default:
                throw new IllegalArgumentException("Function " + operand.getOperationFirst() + " cannot be a first argument!");
        }
    }

    private double applyBinary(double first, double second, OperandType function) {
        switch (function) {
            case MINUS:
                return first - second;
            case PLUS:
                return first + second;
            case MULT:
                return first * second;
            case DIVIDE:
                return first / second;
            default:
                throw new IllegalArgumentException("Function " + function + " is not binary operation!");
        }
    }


    /**
     * Можно редактировать метод. Должен вернуть все операции для заданного клиента.
     * Если клиента нет или он уже закрыт -- вернуть null. Если клиент есть, но операций нет -- вернуть пустой список.
     */
    public List<ServerOperation> getOperationsForClient() {
        return null;
    }

    public void stop() throws IOException {
        executorService.shutdown();
        selector.close();
        for (ServerSocketChannel value : sockets.values()) {
            value.close();
        }
        for (SocketChannel clients : clients) {
            clients.close();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Server is ready");
        Server server = new Server(new int[]{10080, 10081, 10082, 10083}, 5);
        server.startServer();

//        Thread.sleep(5000);
//        server.stop();
    }
}

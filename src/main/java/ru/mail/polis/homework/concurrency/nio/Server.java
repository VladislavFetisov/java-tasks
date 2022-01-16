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
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private final ExecutorService service;
    private final int[] serverPorts;
    private final List<ServerSocketChannel> sockets = new CopyOnWriteArrayList<>();
    private final Map<SocketChannel, ByteBuffer> clientsBuffers = new ConcurrentHashMap<>();
    private final Map<Integer, Map<Integer, ServerOperation>> serverOperations = new ConcurrentHashMap<>();
    private final Map<Integer, BlockingQueue<ServerOperation>> readyToSend = new ConcurrentHashMap<>();
    private final BlockingQueue<ServerOperation> results = new LinkedBlockingQueue<>();

    //поток 1 на чтение из серверных портов порты, поток 1 на запись в серверный порт calculateThreadsCount потоки только на вычисления
    public Server(int[] serverPorts, int calculateThreadsCount) {
        this.service = Executors.newFixedThreadPool(calculateThreadsCount);
        this.serverPorts = serverPorts;
    }

    /**
     * Можно редактировать метод. Должен вернуть всех клиентов
     */
    public void getClients() {

    }

    private void startServer() {
        try (Selector selector = Selector.open()) {
            for (int serverPort : serverPorts) {
                ServerSocketChannel server = ServerSocketChannel.open();
                server.configureBlocking(false);
                server.bind(new InetSocketAddress("localhost", serverPort));
                server.register(selector, SelectionKey.OP_ACCEPT);
                sockets.add(server);
            }

            new Sender().start();

            Set<SelectionKey> readyKeys;
            Iterator<SelectionKey> iterator;
            while (true) {
                selector.select();
                readyKeys = selector.selectedKeys();
                iterator = readyKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if (key.isAcceptable()) {
                        register(key, selector);
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
        } finally {
            try {
                for (ServerSocketChannel socket : sockets) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void register(SelectionKey key, Selector selector) throws IOException {
        SocketChannel client = ((ServerSocketChannel) key.channel()).accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        clientsBuffers.putIfAbsent(client, ByteBuffer.allocate(Client.BUFFER_BYTES));
    }

    private void handleRequests(SelectionKey key) {
        try {
            SocketChannel client = (SocketChannel) key.channel();
            ByteBuffer buffer = clientsBuffers.get(client);
            buffer.clear();
            client.read(buffer);
            buffer.flip();

//            if (buffer.limit() <= Integer.BYTES + Double.BYTES) {
//                //buffer.clear();
//                return;
//            }
            logger.log(Level.INFO, client.getLocalAddress().toString());
            logger.log(Level.INFO, Arrays.toString(buffer.array()));
            logger.log(Level.INFO, "limit: " + buffer.limit());
            readOperand(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readOperand(ByteBuffer buffer) {
        int port = buffer.getInt();
        int order = buffer.getInt();
        int requestId = buffer.getInt();
        int length = buffer.getInt();
        logger.log(Level.INFO, String.format("port:%d, order %d, requestId %d, length %d", port, order, requestId, length));
        OperandType firstOperand = readOperandType(buffer);
        double num = buffer.getDouble();
        OperandType secondOperand = readOperandType(buffer);
        Operand operand = new Operand(firstOperand, num, secondOperand);

        serverOperations.putIfAbsent(port, new ConcurrentHashMap<>());
        Map<Integer, ServerOperation> requests = serverOperations.get(port);

        requests.putIfAbsent(requestId, new ServerOperation(requestId));
        requests.get(requestId).addOperand(new RequestChunk(operand, order));

//        if (length == requests.get(requestId).getRequestChunks().size()) {
//            computeAndPutToQueue(port, requests.get(requestId));
//        }
    }

    private OperandType readOperandType(ByteBuffer buffer) {
        int length = buffer.getInt();
        if (length == -1) {
            return null;
        }
        byte[] operation = new byte[length];
        for (int i = 0; i < length; i++) {
            operation[i] = buffer.get();
        }
        String str = new String(operation, StandardCharsets.UTF_8);
        logger.log(Level.INFO, str);
        return OperandType.valueOf(str);
    }

    private void sendResult(SelectionKey key) {
        try {
            SocketChannel client = (SocketChannel) key.channel();
            int port = getPort(client.getLocalAddress()); //долго получаем порт.
            if (readyToSend.containsKey(port) && !readyToSend.get(port).isEmpty()) {
                ServerOperation operation = readyToSend.get(port).take();
                logger.log(Level.INFO, String.format("Sending result: %f, requestId: %d to client %s",
                        operation.getRes(), operation.getRequestId(), client.getLocalAddress().toString()));
                readyToSend.get(port);
                operation.setClient(client);
                results.add(operation);
            }
        } catch (IOException | InterruptedException e) {
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

    private class Sender extends Thread {
        @Override
        public void run() {
            try {
                while (true) {
                    ServerOperation operation = results.take();
                    ByteBuffer buffer = clientsBuffers.get(operation.getClient());
                    buffer.clear();
                    buffer.putInt(operation.getRequestId());
                    buffer.putDouble(operation.getRes());
                    buffer.flip();
                    operation.getClient().write(buffer);
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void computeAndPutToQueue(int port, ServerOperation serverOperation) {
        service.execute(() -> {
            double res = serverOperation.getRequestChunks()
                    .stream()
                    .sorted(Comparator.comparingInt(RequestChunk::getOrder))
                    .map(RequestChunk::getOperand)
                    .reduce((o1, o2) -> new Operand(applyBinary(applyUnary(o1),
                            applyUnary(o2), o1.getOperationSecond()), o2.getOperationSecond()))
                    .get()
                    .getA();
            serverOperation.setRes(res);
            readyToSend.putIfAbsent(port, new LinkedBlockingQueue<>());
            try {
                readyToSend.get(port).put(serverOperation);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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

    }

    public static void main(String[] args) throws IOException, InterruptedException {
        logger.log(Level.INFO, "Server is ready");
        Server server = new Server(new int[]{10080, 10081, 10082, 10083}, 5);
        server.startServer();
    }
}

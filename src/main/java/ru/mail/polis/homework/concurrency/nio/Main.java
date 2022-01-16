package ru.mail.polis.homework.concurrency.nio;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;


public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        Client client = new Client(new int[]{10082}, 10081, 4);
        client.calculate(List.of(new Operand(10, OperandType.PLUS),
                new Operand(10, OperandType.PLUS),
                new Operand(14, OperandType.EQUALS)));
        //client.calculate(List.of(new Operand(11, OperandType.PLUS), new Operand(14, OperandType.EQUALS)));
//        client.calculate(List.of(new Operand(12, OperandType.PLUS), new Operand(14, OperandType.EQUALS)));
//        client.calculate(List.of(new Operand(13, OperandType.PLUS), new Operand(14, OperandType.EQUALS)));
//        Client client1 = new Client(new int[]{10082, 10081}, 10083, 4);
//        client1.calculate(List.of(new Operand(OperandType.MINUS, 12, OperandType.PLUS), new Operand(14, OperandType.ABS)));
//        Thread.sleep(2000);
//        client.stop();

    }
}

package ru.mail.polis.homework.concurrency.nio;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public enum OperandType {
    EQUALS,
    PLUS,
    MINUS,
    MULT,
    DIVIDE,
    SIN,
    COS,
    EXP,
    ABS,
    TAN,
    SQUARE,
    LN;

    public static int getMaxBytes() {
        return Arrays.stream(OperandType.values())
                .mapToInt(operandType -> operandType.toString().getBytes(StandardCharsets.UTF_8).length)
                .max()
                .getAsInt();
    }
}

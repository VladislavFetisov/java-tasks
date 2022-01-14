package ru.mail.polis.homework.concurrency.nio;

/**
 * Класс, в котором хранится вся информация о входящем запросе.
 * Его статус, полученные данные, результат и так далее
 */
public class ServerOperation {
    private Result result;
    private ServerState state;
    private Operand operand;

    public void setResult(Result result) {
        this.result = result;
    }

    public void setState(ServerState state) {
        this.state = state;
    }


    public void setOperand(Operand operand) {
        this.operand = operand;
    }

    public Result getResult() {
        return result;
    }


    public Operand getOperand() {
        return operand;
    }

    public ServerState getState() {
        return state;
    }
}

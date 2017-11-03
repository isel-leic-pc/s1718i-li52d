package pt.isel.pc.lectures;

public class BatchQueue<T> {

    private T currentBatch;

    public T newBatch(T t){
        currentBatch = t;
        return t;
    }

    public T getCurrentBatch() {
        return currentBatch;
    }

}

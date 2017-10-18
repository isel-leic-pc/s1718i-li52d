package pt.isel.pc.lectures;

public class BatchQueue<T> {

    private T current;

    public T newBatch(T t){
        current = t;
        return t;
    }

    public T getCurrent() {
        return current;
    }

}

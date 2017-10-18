package pt.isel.pc.lectures;

import java.util.function.Supplier;

@FunctionalInterface
public interface ThrowableRunnable {
    void run(Supplier<Boolean> end) throws Throwable;
}

/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <http://unlicense.org/>
 */
package au.id.soundadvice.systemdesign.concurrent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Changed implements ChangeSubscribable {

    public class Transaction implements AutoCloseable {

        public Transaction() {
            inhibit.incrementAndGet();
        }

        @Override
        public void close() {
            if (inhibit.decrementAndGet() == 0) {
                changed();
            }
        }

    }

    public Changed(Executor executor) {
        this.executor = executor;
    }

    public Transaction start() {
        return new Transaction();
    }

    @Override
    public void subscribe(Runnable subscriber) {
        subscribers.add(subscriber);
    }

    @Override
    public void unsubscribe(Runnable subscriber) {
        subscribers.remove(subscriber);
    }

    private void changed() {
        if (notifyNeeded.getAndIncrement() == 0) {
            executor.execute(() -> {
                for (;;) {
                    notifyNeeded.set(1);
                    subscribers.parallelStream().forEach(f -> f.run());
                    if (notifyNeeded.decrementAndGet() == 0) {
                        return;
                    }
                }
            });
        }
    }

    private final AtomicInteger inhibit = new AtomicInteger(0);
    private final AtomicInteger notifyNeeded = new AtomicInteger(0);
    private final Executor executor;
    private final List<Runnable> subscribers = new CopyOnWriteArrayList<>();

    public Executor getExecutor() {
        return executor;
    }
}

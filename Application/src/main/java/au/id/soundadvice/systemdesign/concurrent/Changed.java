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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Changed implements ChangeSubscribable {

    private static final Logger LOG = Logger.getLogger(Changed.class.getName());

    public class Inhibit implements AutoCloseable {

        public Inhibit() {
            inhibit.incrementAndGet();
        }

        public void changed() {
            Changed.this.changed();
        }

        @Override
        public void close() {
            if (inhibit.decrementAndGet() == 0) {
                // Last inhibit
                if (changePending.get() != 0) {
                    notifyChanged();
                }
            }
        }

    }

    public Changed(Executor executor) {
        this.executor = executor;
    }

    @CheckReturnValue
    public Inhibit inhibit() {
        return new Inhibit();
    }

    @Override
    public void subscribe(Runnable subscriber) {
        subscribers.add(subscriber);
    }

    @Override
    public void unsubscribe(Runnable subscriber) {
        subscribers.remove(subscriber);
    }

    public void changed() {
        /*
         * Note potential race between transaction close and this inhibit check.
         *
         * In changed() we call ++changePending -> inhibit.get() and fire
         * notifyChanged() whenever the sequence is --inhibit -> inhibit.get()
         *
         * In close() we call --inhibit -> changePending.get() and fire
         * notifyChanged() whenver the sequence is ++changePending ->
         * changePending.get()
         *
         * The possible sequences are:
         *
         * (1) ++changePending -> inhibit.get() -> --inhibit ->
         * changePending.get(): close() fires.
         *
         * (2) ++changePending -> --inhibit -> inhibit.get() ->
         * changePending.get(): Both fire
         *
         * (3) ++changePending -> --inhibit -> changePending.get() ->
         * inhibit.get() : Both fire
         *
         * (4)--inhibit -> ++changePending -> inhibit.get() ->
         * changePending.get(): Both fire
         *
         * (5)--inhibit -> ++changePending -> changePending.get() ->
         * inhibit.get() : Both fire
         *
         * (6)--inhibit -> changePending.get() -> ++changePending ->
         * inhibit.get() : changed() fires
         *
         */
        changePending.incrementAndGet();
        if (inhibit.get() == 0) {
            notifyChanged();
        }
    }

    private void notifyChanged() {
        if (notifyNeeded.getAndIncrement() == 0) {
            executor.execute(() -> {
                for (;;) {
                    changePending.set(0);
                    notifyNeeded.set(1);
                    try {
                        subscribers.parallelStream().forEach(f -> f.run());
                    } catch (RuntimeException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                    if (notifyNeeded.decrementAndGet() == 0) {
                        return;
                    }
                }
            });
        }
    }

    private final AtomicInteger inhibit = new AtomicInteger(0);
    private final AtomicInteger changePending = new AtomicInteger(0);
    private final AtomicInteger notifyNeeded = new AtomicInteger(0);
    private final Executor executor;
    private final List<Runnable> subscribers = new CopyOnWriteArrayList<>();

    public Executor getExecutor() {
        return executor;
    }
}

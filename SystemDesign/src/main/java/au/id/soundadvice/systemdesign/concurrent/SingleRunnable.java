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

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adapts a normal Runnable such that multiple run jobs are collapsed into a
 * single execution. run() triggers a job to registered with the executor.
 * Subsequent run() invocations are no-ops until the job registered with the
 * executor is about to start. Once the job is started additional run() calls
 * will queue an additional execution but this execution will only take place
 * after the current invocation has completed.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <R> The delegated Runnable type
 */
public class SingleRunnable<R extends Runnable> implements Runnable {

    public SingleRunnable(Executor executor, R delegate) {
        this.executor = executor;
        this.runDelegate = new RunDelegate(delegate);
    }

    private final Executor executor;
    private final RunDelegate runDelegate;
    private final AtomicInteger queued = new AtomicInteger(0);

    public R getDelegate() {
        return runDelegate.delegate;
    }

    @Override
    public void run() {
        if (queued.getAndIncrement() == 0) {
            executor.execute(runDelegate);
        }
    }

    private class RunDelegate implements Runnable {

        private final R delegate;

        private RunDelegate(R delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            queued.set(1);
            try {
                delegate.run();
            } finally {
                if (queued.decrementAndGet() != 0) {
                    // Reschedule immediately
                    executor.execute(this);
                }
            }
        }

    }
}

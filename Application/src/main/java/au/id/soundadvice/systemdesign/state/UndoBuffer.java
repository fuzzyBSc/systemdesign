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
package au.id.soundadvice.systemdesign.state;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Model an infinite undo buffer as we might find any any GUI editor app.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 * @param <T> The type contained in the buffer
 */
public class UndoBuffer<T> {

    /**
     * Construct with an initial state.
     *
     * @param state The current state.
     */
    public UndoBuffer(T state) {
        this.mustLock = new MustLock(state);
    }

    public void reset(T state) {
        synchronized (mustLock) {
            mustLock.undoBuffer.clear();
            mustLock.redoBuffer.clear();
            mustLock.currentState = state;
        }
    }

    private static final class MustLock<T> {

        MustLock(T state) {
            this.currentState = state;
        }
        private final Deque<T> undoBuffer = new ArrayDeque<>();
        private final Deque<T> redoBuffer = new ArrayDeque<>();
        private T currentState;
    };
    private final MustLock<T> mustLock;

    public T get() {
        synchronized (mustLock) {
            return mustLock.currentState;
        }
    }

    public void set(T state) {
        synchronized (mustLock) {
            mustLockSet(state);
        }
    }

    private void mustLockSet(T state) {
        boolean differs = !Objects.equals(state, mustLock.currentState);
        if (differs) {
            mustLock.undoBuffer.push(mustLock.currentState);
            mustLock.redoBuffer.clear();
            mustLock.currentState = state;
        }
    }

    public boolean compareAndSet(T expect, T state) {
        boolean matched;
        synchronized (mustLock) {
            matched = expect.equals(mustLock.currentState);
            if (matched) {
                mustLockSet(state);
            }
        }
        return matched;
    }

    public final boolean update(UnaryOperator<T> update) {
        for (;;) {
            T oldState = get();
            T newState = update.apply(oldState);
            if (compareAndSet(oldState, newState)) {
                return !oldState.equals(newState);
            }
        }
    }

    public void clearAndSet(T state) {
        synchronized (mustLock) {
            mustLock.undoBuffer.clear();
            mustLock.redoBuffer.clear();
            mustLock.currentState = state;
        }
    }

    public boolean canUndo() {
        synchronized (mustLock) {
            return !mustLock.undoBuffer.isEmpty();
        }
    }

    public boolean canRedo() {
        synchronized (mustLock) {
            return !mustLock.redoBuffer.isEmpty();
        }
    }

    public T undo() {
        T state;
        synchronized (mustLock) {
            state = mustLock.undoBuffer.pop();
            mustLock.redoBuffer.push(mustLock.currentState);
            mustLock.currentState = state;
        }
        return state;
    }

    public T redo() {
        T state;
        synchronized (mustLock) {
            state = mustLock.redoBuffer.pop();
            mustLock.undoBuffer.push(mustLock.currentState);
            mustLock.currentState = state;
        }
        return state;
    }

}

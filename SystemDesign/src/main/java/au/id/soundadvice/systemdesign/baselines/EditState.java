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
package au.id.soundadvice.systemdesign.baselines;

import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.files.SaveTransaction;
import au.id.soundadvice.systemdesign.undo.UndoBuffer;
import java.io.IOException;
import java.util.EmptyStackException;
import java.util.Stack;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class EditState {

    public Directory getCurrentDirectory() {
        return currentDirectory.get();
    }

    public UndoBuffer<UndoState> getUndo() {
        return undo;
    }

    public Executor getExecutor() {
        return undo.getExecutor();
    }

    public static EditState init(Executor executor) {
        return new EditState(executor, null, UndoState.createNew(), false);
    }

    private EditState(
            Executor executor,
            Directory currentDirectory,
            UndoState undo,
            boolean alreadySaved) {
        this.currentDirectory = new AtomicReference<>(currentDirectory);
        this.undo = new UndoBuffer<>(executor, undo);
        if (alreadySaved) {
            this.savedState = new AtomicReference<>(undo);
        } else {
            this.savedState = new AtomicReference<>();
        }
    }

    public void loadParent() throws IOException {
        Directory dir = currentDirectory.get();
        if (dir == null) {
            throw new IOException("Cannot load from null directory");
        }
        loadImpl(dir.getParent());
        lastChild.push(dir);
    }

    public boolean hasLastChild() {
        return !lastChild.isEmpty();
    }

    public void loadLastChild() throws IOException {
        try {
            Directory dir = lastChild.pop();
            if (dir == null) {
                throw new IOException("Cannot load from null directory");
            }
            loadImpl(dir);
        } catch (EmptyStackException ex) {
            throw new IOException(ex);
        }
    }

    public void load(Directory dir) throws IOException {
        if (dir == null) {
            throw new IOException("Cannot save to null directory");
        }
        loadImpl(dir);
        lastChild.clear();
    }

    private void loadImpl(Directory dir) throws IOException {
        UndoState state = UndoState.load(dir);
        currentDirectory.set(dir);
        undo.reset(state);
        savedState.set(state);
    }

    public boolean saveNeeded() {
        return !undo.get().equals(savedState.get());
    }

    public void save() throws IOException {
        saveTo(currentDirectory.get());
    }

    public void saveTo(Directory dir) throws IOException {
        if (dir == null) {
            throw new IOException("Cannot load from null directory");
        }
        UndoState state = undo.get();
        try (SaveTransaction transaction = new SaveTransaction()) {
            state.saveTo(transaction, dir);
            transaction.commit();
        }
        savedState.set(state);
    }

    private final AtomicReference<Directory> currentDirectory;
    private final Stack<Directory> lastChild = new Stack<>();
    private final UndoBuffer<UndoState> undo;
    private final AtomicReference<UndoState> savedState;

    public void subscribe(Runnable subscriber) {
        undo.getChanged().subscribe(subscriber);
    }

    public void unsubscribe(Runnable subscriber) {
        undo.getChanged().unsubscribe(subscriber);
    }

}

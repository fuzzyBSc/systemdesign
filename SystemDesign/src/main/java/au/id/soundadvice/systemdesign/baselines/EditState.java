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

import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.concurrent.Changed;
import au.id.soundadvice.systemdesign.consistency.autofix.AutoFix;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.files.SaveTransaction;
import au.id.soundadvice.systemdesign.model.Identity;
import au.id.soundadvice.systemdesign.model.Item;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EmptyStackException;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class EditState {

    public Optional<Directory> getCurrentDirectory() {
        return currentDirectory.get();
    }

    public UndoBuffer<UndoState> getUndo() {
        return undo;
    }

    public Executor getExecutor() {
        return undo.getExecutor();
    }

    public static EditState init(Executor executor) {
        return new EditState(executor, Optional.empty(), UndoState.createNew(), true);
    }

    public void clear() {
        UndoState state = UndoState.createNew();
        this.currentDirectory.set(Optional.empty());
        this.lastChild.clear();
        this.undo.reset(state);
        this.savedState.set(state);
    }

    private EditState(
            Executor executor,
            Optional<Directory> currentDirectory,
            UndoState undo,
            boolean alreadySaved) {
        this.currentDirectory = new AtomicReference<>(currentDirectory);
        this.undo = new UndoBuffer<>(executor, undo);
        this.changed = new Changed(executor);
        if (alreadySaved) {
            this.savedState = new AtomicReference<>(undo);
        } else {
            this.savedState = new AtomicReference<>();
        }
        this.undo.getChanged().subscribe(() -> this.changed.changed());
    }

    public void loadParent() throws IOException {
        Optional<Directory> dir = currentDirectory.get();
        if (dir.isPresent()) {
            UndoState state = undo.get();
            loadImpl(dir.get().getParent());
            lastChild.push(state.getAllocated().getIdentity());
        } else {
            throw new IOException("Cannot load from null directory");
        }
    }

    public boolean hasLastChild() {
        return !lastChild.isEmpty();
    }

    public void loadChild(Identity child) throws IOException {
        Optional<Directory> parentDir = currentDirectory.get();
        if (!parentDir.isPresent()) {
            throw new IOException("Parent is not saved yet");
        }
        Optional<Directory> existing = parentDir.get().getChild(child.getUuid());
        if (existing.isPresent()) {
            loadImpl(existing.get());
        } else {
            // Synthesise a child baseline, since none has been saved yet
            String prefix = child.getIdPath().toString();
            int count = 0;
            Directory childDir;
            do {
                // Avoid name collisions
                String name = count == 0 ? prefix : prefix + "_" + count;
                childDir = new Directory(parentDir.get().getPath().resolve(name));
                ++count;
            } while (Files.isDirectory(childDir.getPath()));
            UndoState state = undo.get();
            Optional<Item> systemOfInterest = state.getAllocated().getItemForIdentity(child);
            if (systemOfInterest.isPresent()) {
                state = state.setFunctional(state.getAllocated());
                state = state.setAllocated(Baseline.create(child));
                currentDirectory.set(Optional.of(childDir));
                undo.reset(state);
                savedState.set(state);
            } else {
                throw new IOException("No such child");
            }
        }
        if (!lastChild.isEmpty()) {
            if (child.equals(lastChild.peek())) {
                lastChild.pop();
            } else {
                lastChild.clear();
            }
        }
    }

    public Identity getLastChild() throws EmptyStackException {
        return lastChild.peek();
    }

    public void load(Directory dir) throws IOException {
        if (dir == null) {
            throw new IOException("Cannot load from null directory");
        }
        loadImpl(dir);
        lastChild.clear();
    }

    private void loadImpl(Directory dir) throws IOException {
        UndoState state = UndoState.load(dir);
        currentDirectory.set(Optional.of(dir));
        undo.reset(state);
        savedState.set(state);

        // Set undo again here to allow automatic changes to be undone
        undo.update(AutoFix.all());
    }

    public boolean saveNeeded() {
        return !undo.get().equals(savedState.get());
    }

    public void save() throws IOException {
        Optional<Directory> dir = currentDirectory.get();
        if (dir.isPresent()) {
            saveTo(dir.get());
        } else {
            throw new IOException("Model has not been saved yet");
        }
    }

    public void saveTo(Directory dir) throws IOException {
        UndoState state = undo.get();
        try (SaveTransaction transaction = new SaveTransaction()) {
            state.saveTo(transaction, dir);
            transaction.commit();
        }
        currentDirectory.set(Optional.of(dir));
        savedState.set(state);
    }

    private final AtomicReference<Optional<Directory>> currentDirectory;
    private final Stack<Identity> lastChild = new Stack<>();
    private final UndoBuffer<UndoState> undo;
    private final AtomicReference<UndoState> savedState;
    private final Changed changed;

    public void subscribe(Runnable subscriber) {
        changed.subscribe(subscriber);
    }

    public void unsubscribe(Runnable subscriber) {
        changed.unsubscribe(subscriber);
    }

    /**
     * Rename the current directory.
     *
     * @param from The expected current directory value
     * @param to The new current directory value
     * @throws java.io.IOException
     */
    public void renameDirectory(Path from, Path to) throws IOException {
        Optional<Directory> current = currentDirectory.get();
        if (current.isPresent() && current.get().getPath().equals(from)) {
            Files.move(from, to);
            currentDirectory.set(Optional.of(new Directory(to)));
            changed.changed();
        }
    }

    /**
     * Modify the functional (ie parent) baseline. If no such baseline exists
     * this method is a no-op.
     *
     * @param update A function to update the baseline.
     */
    public void updateFunctional(UnaryOperator<Baseline> update) {
        undo.update(state -> state.setFunctional(
                update.apply(state.getFunctional())));
    }

    /**
     * Modify the allocated (ie child) baseline.
     *
     * @param update A function to update the baseline.
     */
    public void updateAllocated(UnaryOperator<Baseline> update) {
        undo.update(state -> state.setAllocated(
                update.apply(state.getAllocated())));
    }

    /**
     * Modify both baselines
     *
     * @param update A function to update the baseline.
     */
    public void update(UnaryOperator<UndoState> update) {
        undo.update(update);
    }
}

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

import au.id.soundadvice.systemdesign.concurrent.Changed;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.files.SaveTransaction;
import au.id.soundadvice.systemdesign.model.Identity;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.undo.UndoBuffer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EmptyStackException;
import java.util.Stack;
import java.util.UUID;
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
        return new EditState(executor, null, UndoState.createNew(), true);
    }

    public void clear() {
        UndoState state = UndoState.createNew();
        this.currentDirectory.set(null);
        this.lastChild.clear();
        this.undo.reset(state);
        this.savedState.set(state);
    }

    private EditState(
            Executor executor,
            Directory currentDirectory,
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
        Directory dir = currentDirectory.get();
        if (dir == null) {
            throw new IOException("Cannot load from null directory");
        }
        UndoState state = undo.get();
        loadImpl(dir.getParent());
        lastChild.push(state.getAllocated().getIdentity());
    }

    public boolean hasLastChild() {
        return !lastChild.isEmpty();
    }

    public void loadChild(Identity child) throws IOException {
        Directory parentDir = currentDirectory.get();
        if (currentDirectory == null) {
            throw new IOException("Parent is not saved yet");
        }
        Directory childDir = parentDir.getChild(child.getUuid());
        if (childDir == null) {
            // Synthesise a child baseline, since none has been saved yet
            String prefix = child.getIdPath().toString();
            int count = 0;
            do {
                // Avoid name collisions
                String name = count == 0 ? prefix : prefix + "_" + count;
                childDir = new Directory(parentDir.getPath().resolve(name));
                ++count;
            } while (Files.isDirectory(childDir.getPath()));
            UndoState state = undo.get();
            Item systemOfInterest = state.getAllocated().getStore().get(
                    child.getUuid(), Item.class);
            state = state.setFunctional(new FunctionalBaseline(
                    systemOfInterest, state.getAllocated()));
            state = state.setAllocated(AllocatedBaseline.create(child));
            currentDirectory.set(childDir);
            undo.reset(state);
            savedState.set(state);
        } else {
            loadImpl(childDir);
        }
        if (!lastChild.isEmpty()) {
            if (child.equals(lastChild.peek())) {
                lastChild.pop();
            } else {
                lastChild.clear();
            }
        }
    }

    public void loadLastChild() throws IOException {
        try {
            Identity child = lastChild.peek();
            loadChild(child);
        } catch (EmptyStackException ex) {
            throw new IOException(ex);
        }
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
        currentDirectory.set(dir);
        undo.reset(state);
        savedState.set(state);

        // Fix id
        FunctionalBaseline functional = state.getFunctional();
        if (functional != null) {
            Identity correctedId = functional.getSystemOfInterest().asIdentity(
                    functional.getStore());
            AllocatedBaseline allocated = state.getAllocated();
            if (!correctedId.equals(allocated.getIdentity())) {
                // Identity mismatch - autofix.
                undo.set(state.setAllocated(allocated.setIdentity(correctedId)));
            }
        }
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
        currentDirectory.set(dir);
        savedState.set(state);
    }

    private final AtomicReference<Directory> currentDirectory;
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

    public void rename(Path from, Path to) throws IOException {
        if (currentDirectory.get().getPath().equals(from)) {
            Files.move(from, to);
            currentDirectory.set(new Directory(to));
            changed.changed();
        }
    }

    /**
     * Remove a relation from the allocated baseline (only).
     */
    public void remove(UUID uuid) {
        UndoState state = undo.get();
        AllocatedBaseline allocated = state.getAllocated();
        undo.set(state.setAllocated(allocated.remove(uuid)));
    }
}

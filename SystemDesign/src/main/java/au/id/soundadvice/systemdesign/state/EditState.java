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

import au.id.soundadvice.systemdesign.model.UndoState;
import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.concurrent.Changed;
import au.id.soundadvice.systemdesign.concurrent.Changed.Inhibit;
import au.id.soundadvice.systemdesign.consistency.autofix.AutoFix;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.files.SaveTransaction;
import au.id.soundadvice.systemdesign.model.Identity;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.versioning.NullVersionControl;
import au.id.soundadvice.systemdesign.versioning.VersionControl;
import au.id.soundadvice.systemdesign.versioning.VersionInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EmptyStackException;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.util.Pair;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class EditState {

    public Optional<Baseline> getDiffBaseline() {
        return diffBaseline.get().map(Pair::getValue);
    }

    public Optional<VersionInfo> getDiffBaselineVersion() {
        return diffBaseline.get().map(Pair::getKey);
    }

    public VersionControl getVersionControl() {
        return versionControl.get();
    }

    private static final Logger LOG = Logger.getLogger(EditState.class.getName());

    public Optional<Directory> getCurrentDirectory() {
        return currentDirectory.get();
    }

    public Executor getExecutor() {
        return executor;
    }

    public static EditState init(Executor executor) {
        EditState result = new EditState(
                executor,
                Optional.empty(), new NullVersionControl(),
                UndoState.createNew(),
                true);
        result.subscribe(() -> result.updateState(AutoFix::onChange));
        return result;
    }

    public void clear() {
        try (Inhibit xact = this.changed.inhibit()) {
            UndoState state = UndoState.createNew();
            this.currentDirectory.set(Optional.empty());
            VersionControl old = this.versionControl.getAndSet(new NullVersionControl());
            try {
                old.close();
            } catch (IOException ex) {
                LOG.log(Level.WARNING, null, ex);
            }
            this.diffBaseline.set(Optional.empty());
            this.lastChild.clear();
            this.undo.reset(state);
            this.savedState.set(state);
            xact.changed();
        }
    }

    private EditState(
            Executor executor,
            Optional<Directory> currentDirectory,
            VersionControl versionControl,
            UndoState undo,
            boolean alreadySaved) {
        this.currentDirectory = new AtomicReference<>(currentDirectory);
        this.versionControl = new AtomicReference<>(versionControl);
        this.undo = new UndoBuffer<>(undo);
        this.executor = executor;
        this.changed = new Changed(executor);
        if (alreadySaved) {
            this.savedState = new AtomicReference<>(undo);
        } else {
            this.savedState = new AtomicReference<>();
        }
    }

    public void loadParent() throws IOException {

        Optional<Directory> parentDir = currentDirectory.get()
                .map(Directory::getParent);
        if (parentDir.isPresent()
                && Files.exists(parentDir.get().getIdentityFile())) {
            try (Inhibit xact = this.changed.inhibit()) {
                UndoState state = undo.get();
                loadImpl(parentDir.get());
                lastChild.push(Identity.find(state.getAllocated()));
                xact.changed();
            }
        }
    }

    public boolean hasLastChild() {
        return !lastChild.isEmpty();
    }

    public void loadChild(Identity child) throws IOException {
        try (Inhibit xact = this.changed.inhibit()) {
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
                    childDir = parentDir.get().resolve(name);
                    ++count;
                } while (Files.isDirectory(childDir.getPath()));
                UndoState state = undo.get();
                Optional<Item> systemOfInterest = state.getAllocated().getItemForIdentity(child);
                if (systemOfInterest.isPresent()) {
                    state = state.setFunctional(state.getAllocated());
                    state = state.setAllocated(Baseline.create(child));
                    Optional<Directory> newDir = Optional.of(childDir);
                    currentDirectory.set(newDir);
                    VersionControl newVersionControl = VersionControl.forPath(childDir.getPath());
                    VersionControl old = this.versionControl.getAndSet(newVersionControl);
                    if (old != newVersionControl) {
                        try {
                            old.close();
                        } catch (IOException ex) {
                            LOG.log(Level.WARNING, null, ex);
                        }
                    }
                    undo.reset(state);
                    savedState.set(state);
                    loadDiffBaseline(newDir, newVersionControl, diffVersion.get());
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
            xact.changed();
        }
    }

    public Identity getLastChild() throws EmptyStackException {
        return lastChild.peek();
    }

    public void load(Directory dir) throws IOException {
        if (dir == null) {
            throw new IOException("Cannot load from null directory");
        }
        try (Inhibit xact = this.changed.inhibit()) {
            loadImpl(dir);
            lastChild.clear();
            xact.changed();
        }
    }

    private void loadImpl(Directory dir) throws IOException {
        UndoState state = UndoState.load(dir);
        Optional<Directory> newDir = Optional.of(dir);
        currentDirectory.set(newDir);
        VersionControl newVersionControl = VersionControl.forPath(dir.getPath());
        VersionControl old = this.versionControl.getAndSet(newVersionControl);
        if (old != newVersionControl) {
            try {
                old.close();
            } catch (IOException ex) {
                LOG.log(Level.WARNING, null, ex);
            }
        }
        undo.reset(state);
        savedState.set(state);
        loadDiffBaseline(newDir, newVersionControl, diffVersion.get());

        // Set undo again here to allow automatic changes to be undone
        undo.update(AutoFix::onLoad);
    }

    public boolean saveNeeded() {
        return !undo.get().equals(savedState.get());
    }

    public void save() throws IOException {
        try (Inhibit xact = this.changed.inhibit()) {
            Optional<Directory> dir = currentDirectory.get();
            if (dir.isPresent()) {
                saveTo(dir.get(), versionControl.get());
            } else {
                throw new IOException("Model has not been saved yet");
            }
            xact.changed();
        }
    }

    private void saveTo(Directory dir, VersionControl newVersionControl) throws IOException {
        UndoState state = undo.get();
        try (SaveTransaction transaction = new SaveTransaction(newVersionControl)) {
            state.saveTo(transaction, dir);
            transaction.commit();
        }
        Optional<Directory> newDir = Optional.of(dir);
        currentDirectory.set(newDir);
        VersionControl old = this.versionControl.getAndSet(newVersionControl);
        if (old != newVersionControl) {
            try {
                old.close();
            } catch (IOException ex) {
                LOG.log(Level.WARNING, null, ex);
            }
        }
        savedState.set(state);
        loadDiffBaseline(newDir, newVersionControl, diffVersion.get());
    }

    public void saveTo(Directory dir) throws IOException {
        try (Inhibit xact = this.changed.inhibit()) {
            saveTo(dir, VersionControl.forPath(dir.getPath()));
            xact.changed();
        }
    }

    public void setDiffVersion(Optional<VersionInfo> version) {
        try (Inhibit xact = this.changed.inhibit()) {
            this.diffVersion.set(version);
            loadDiffBaseline(currentDirectory.get(), versionControl.get(), version);
            xact.changed();
        }
    }

    private void loadDiffBaseline(
            Optional<Directory> path, VersionControl versioning, Optional<VersionInfo> version) {
        if (path.isPresent() && version.isPresent()) {
            try {
                Baseline was = Baseline.load(
                        path.get(),
                        path.get().getOpenerForVersion(versioning, version));
                diffBaseline.set(Optional.of(new Pair<>(version.get(), was)));
            } catch (IOException ex) {
                LOG.log(Level.WARNING, null, ex);
                diffBaseline.set(Optional.empty());
            }
        } else {
            diffBaseline.set(Optional.empty());
        }
    }

    private final AtomicReference<Optional<Directory>> currentDirectory;
    private final AtomicReference<VersionControl> versionControl;
    private final AtomicReference<Optional<VersionInfo>> diffVersion
            = new AtomicReference<>(Optional.empty());
    private final AtomicReference<Optional<Pair<VersionInfo, Baseline>>> diffBaseline
            = new AtomicReference<>(Optional.empty());
    private final Stack<Identity> lastChild = new Stack<>();
    private final UndoBuffer<UndoState> undo;
    private final Executor executor;
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
            try (Inhibit xact = this.changed.inhibit()) {
                VersionControl versioning = versionControl.get();
                versioning.renameDirectory(from, to);
                Optional<Directory> newDirectory = Optional.of(Directory.forPath(to));
                currentDirectory.set(newDirectory);
                VersionControl newVersionControl = VersionControl.forPath(to);
                VersionControl old = this.versionControl.getAndSet(newVersionControl);
                if (old != newVersionControl) {
                    try {
                        old.close();
                    } catch (IOException ex) {
                        LOG.log(Level.WARNING, null, ex);
                    }
                }
                loadDiffBaseline(newDirectory, newVersionControl, diffVersion.get());
                xact.changed();
            }
        }
    }

    /**
     * Modify the functional (ie parent) baseline. If no such baseline exists
     * this method is a no-op.
     *
     * @param update A function to update the baseline.
     */
    public void updateFunctional(UnaryOperator<Baseline> update) {
        try (Inhibit xact = this.changed.inhibit()) {
            if (undo.update(state -> state.setFunctional(
                    update.apply(state.getFunctional())))) {
                xact.changed();
            }
        }
    }

    /**
     * Modify the allocated (ie child) baseline.
     *
     * @param update A function to update the baseline.
     */
    public void updateAllocated(UnaryOperator<Baseline> update) {
        try (Inhibit xact = this.changed.inhibit()) {
            if (undo.update(state -> state.setAllocated(
                    update.apply(state.getAllocated())))) {
                xact.changed();
            }
        }
    }

    /**
     * Modify both baselines
     *
     * @param update A function to update the functional and allocated baselines
     * simultaneously.
     */
    public void updateState(UnaryOperator<UndoState> update) {
        try (Inhibit xact = this.changed.inhibit()) {
            if (undo.update(update)) {
                xact.changed();
            }
        }
    }

    /**
     * Return a snapshot of the current undo state.
     *
     * @return
     */
    public UndoState getState() {
        return undo.get();
    }

    /**
     * Return a snapshot of the current functional baseline.
     *
     * @return
     */
    public Baseline getFunctional() {
        return undo.get().getFunctional();
    }

    /**
     * Return a snapshot of the current allocated baseline.
     *
     * @return
     */
    public Baseline getAllocated() {
        return undo.get().getAllocated();
    }

    public Optional<Item> getSystemOfInterest() {
        return undo.get().getSystemOfInterest();
    }

    public void undo() {
        try (Inhibit xact = this.changed.inhibit()) {
            undo.undo();
            xact.changed();
        }
    }

    public void redo() {
        try (Inhibit xact = this.changed.inhibit()) {
            undo.redo();
            xact.changed();
        }
    }

    public boolean canUndo() {
        return undo.canUndo();
    }

    public boolean canRedo() {
        return undo.canRedo();
    }

}

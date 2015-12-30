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

import au.id.soundadvice.systemdesign.consistency.AutoFix;
import au.id.soundadvice.systemdesign.moduleapi.UndoState;
import au.id.soundadvice.systemdesign.concurrent.Changed;
import au.id.soundadvice.systemdesign.concurrent.Changed.Inhibit;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.files.SaveTransaction;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.physical.Identity;
import au.id.soundadvice.systemdesign.physical.Item;
import au.id.soundadvice.systemdesign.versioning.NullVersionControl;
import au.id.soundadvice.systemdesign.versioning.VersionControl;
import au.id.soundadvice.systemdesign.versioning.VersionInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EmptyStackException;
import java.util.Iterator;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.util.Pair;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class EditState {

    public Optional<Relations> getDiffBaseline() {
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
                Baseline.createUndoState(),
                true);
        result.subscribe(() -> result.updateState(AutoFix::onChange));
        return result;
    }

    private static void saveTo(SaveTransaction transaction, Directory directory, UndoState state) throws IOException {
        if (Identity.getSystemOfInterest(state).isPresent()) {
            Baseline.save(transaction, directory, state.getFunctional());
        }
        Baseline.save(transaction, directory, state.getAllocated());
    }

    public void clear() {
        try (Inhibit xact = this.changed.inhibit()) {
            UndoState state = Baseline.createUndoState();
            this.currentDirectory.set(Optional.empty());
            loadVersionControl(Optional.empty());
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

    /**
     * Attempt to restore a relation, plus all recursive references to that
     * relation
     *
     * @param was The baseline in which deleted relation is still present
     * @param is The baseline to restore the deleted relation into
     * @param relation The deleted relation to restore into the "is" baseline.
     * @return The updated "is" baseline.
     */
    @CheckReturnValue
    private static Relations restoreDeleted(
            Relations was, Relations is, Relation relation) {
        // Verify that the deleted relation is in the "was" baseline
        Optional<Relation> wasRelation = was.get(
                relation.getUuid(),
                (Class<Relation>) relation.getClass());
        Optional<Relation> isRelation = is.get(
                relation.getUuid(),
                (Class<Relation>) relation.getClass());
        if (wasRelation.isPresent() && !isRelation.isPresent()) {
            // Add the old relation back in
            is = is.add(wasRelation.get());
            Iterator<Relation> it = is.findReverse(relation.getUuid()).iterator();
            while (it.hasNext()) {
                // Walk the tree
                is = restoreDeleted(was, is, it.next());
            }
        }
        return is;
    }

    /**
     * Attempt to restore a relation, plus all recursive references to that
     * relation
     *
     * @param deletedRelations The deleted relation to restore into the current
     * baseline.
     */
    public void restoreDeleted(Relation... deletedRelations) {
        updateState(state -> {
            // Restore the entire deleted tree of relations
            Optional<Pair<VersionInfo, Relations>> was = diffBaseline.get();
            if (was.isPresent()) {
                for (Relation relation : deletedRelations) {
                    state = state.setAllocated(
                            restoreDeleted(was.get().getValue(), state.getAllocated(), relation));
                }
                // Rely on autofix behaviours to resolve any conflicts that arose
                state = AutoFix.onLoad(state);
            }
            return state;
        });
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
                String prefix = child.toString();
                int count = 0;
                Directory childDir;
                do {
                    // Avoid name collisions
                    String name = count == 0 ? prefix : prefix + "_" + count;
                    childDir = parentDir.get().resolve(name);
                    ++count;
                } while (Files.isDirectory(childDir.getPath()));
                UndoState state = undo.get();
                Optional<Item> systemOfInterest = state.getAllocated().get(
                        child.getUuid(), Item.class);
                if (systemOfInterest.isPresent()) {
                    state = state.setFunctional(state.getAllocated());
                    state = state.setAllocated(Baseline.create(child));
                    Optional<Directory> newDir = Optional.of(childDir);
                    currentDirectory.set(newDir);
                    undo.reset(state);
                    savedState.set(state);
                    loadVersionControl(newDir);
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
        UndoState state = Baseline.loadUndoState(dir);
        Optional<Directory> newDir = Optional.of(dir);
        currentDirectory.set(newDir);
        undo.reset(state);
        savedState.set(state);
        loadVersionControl(newDir);

        // Set undo again here to allow automatic changes to be undone
        undo.update(AutoFix::onLoad);
    }

    public boolean saveNeeded() {
        return !undo.get().equals(savedState.get());
    }

    public void reloadVersionControl() {
        loadVersionControl(currentDirectory.get());
    }

    private void loadVersionControl(Optional<Directory> dir) {
        VersionControl newVersionControl = dir
                .map(d -> VersionControl.forPath(d.getPath()))
                .orElseGet(NullVersionControl::new);
        VersionControl old = this.versionControl.getAndSet(newVersionControl);
        try {
            old.close();
        } catch (IOException ex) {
            LOG.log(Level.WARNING, null, ex);
        }
        loadDiffBaseline(dir, newVersionControl, diffVersion.get());
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
            Baseline.saveUndoState(transaction, dir, state);
            transaction.commit();
        }
        Optional<Directory> newDir = Optional.of(dir);
        currentDirectory.set(newDir);
        savedState.set(state);
        loadVersionControl(newDir);
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
                Relations was = Baseline.load(
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
    private final AtomicReference<Optional<Pair<VersionInfo, Relations>>> diffBaseline
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
                Optional<Directory> newDir = Optional.of(Directory.forPath(to));
                currentDirectory.set(newDir);
                loadVersionControl(newDir);
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
    public void updateFunctional(UnaryOperator<Relations> update) {
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
    public void updateAllocated(UnaryOperator<Relations> update) {
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
    public Relations getFunctional() {
        return undo.get().getFunctional();
    }

    /**
     * Return a snapshot of the current allocated baseline.
     *
     * @return
     */
    public Relations getAllocated() {
        return undo.get().getAllocated();
    }

    public Optional<Item> getSystemOfInterest() {
        return Identity.getSystemOfInterest(undo.get());
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

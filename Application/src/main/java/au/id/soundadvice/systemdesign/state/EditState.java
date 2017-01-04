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
import au.id.soundadvice.systemdesign.concurrent.Changed;
import au.id.soundadvice.systemdesign.concurrent.Changed.Inhibit;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.BaselinePair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.storage.RecordStorage;
import au.id.soundadvice.systemdesign.moduleapi.util.ISO8601;
import au.id.soundadvice.systemdesign.storage.files.SaveTransaction;
import au.id.soundadvice.systemdesign.physical.Identity;
import au.id.soundadvice.systemdesign.physical.Item;
import au.id.soundadvice.systemdesign.storage.FileStorage;
import au.id.soundadvice.systemdesign.storage.versioning.NullVersionControl;
import au.id.soundadvice.systemdesign.storage.versioning.VersionControl;
import au.id.soundadvice.systemdesign.moduleapi.storage.VersionInfo;
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

    public Optional<Baseline> getDiffBaseline() {
        return diffBaseline.get().map(Pair::getValue);
    }

    public Optional<VersionInfo> getDiffBaselineVersion() {
        return diffBaseline.get().map(Pair::getKey);
    }

    private static final Logger LOG = Logger.getLogger(EditState.class.getName());

    public Optional<RecordStorage> getStorage() {
        return storage.get();
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
        result.subscribe(() -> result.updateState(
                baselines -> AutoFix.onChange(baselines, ISO8601.now())));
        return result;
    }

    public void clear() {
        try (Inhibit xact = this.changed.inhibit()) {
            BaselinePair state = Baseline.createUndoState();
            this.storage.set(Optional.empty());
            loadVersionControl(Optional.empty());
            this.lastChildIdentity.clear();
            this.undo.reset(state);
            this.savedState.set(state);
            xact.changed();
        }
    }

    private EditState(
            Executor executor,
            Optional<RecordStorage> currentDirectory,
            BaselinePair undo,
            boolean alreadySaved) {
        this.storage = new AtomicReference<>(currentDirectory);
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

        Optional<RecordStorage> parentDir = storage.get()
                .flatMap(RecordStorage::getParent);
        if (parentDir.isPresent()
                && parentDir.get().identityFileExists()) {
            try (Inhibit xact = this.changed.inhibit()) {
                BaselinePair state = undo.get();
                loadImpl(parentDir.get());
                lastChildIdentity.push(Identity.get(state.getChild()));
                xact.changed();
            }
        }
    }

    public boolean hasLastChild() {
        return !lastChildIdentity.isEmpty();
    }

    /**
     * Attempt to restore a relation, plus all recursive references to that
     * relation
     *
     * @param was The baseline in which deleted relation is still present
     * @param is The baseline to restore the deleted relation into
     * @param record The deleted record to restore into the "is" baseline.
     * @return The updated "is" baseline.
     */
    @CheckReturnValue
    private static Baseline restoreDeleted(
            Baseline was, Baseline is, Record record) {
        // Verify that the deleted relation is in the "was" baseline
        Optional<Record> wasRecord = was.get(record);
        Optional<Record> isRecord = is.get(record);
        if (wasRecord.isPresent() && !isRecord.isPresent()) {
            // Add the old relation back in
            is = is.add(wasRecord.get());
            Iterator<Record> it = is.findReverse(record.getIdentifier()).iterator();
            while (it.hasNext()) {
                // Walk the tree
                is = restoreDeleted(was, is, it.next());
            }
        }
        return is;
    }

    /**
     * Attempt to restore a record, plus all recursive references to that
     * relation
     *
     * @param deletedRecords The deleted record to restore into the current
     * baseline.
     */
    public void restoreDeleted(Record... deletedRecords) {
        updateState(state -> {
            // Restore the entire deleted tree of relations
            Optional<Pair<VersionInfo, Baseline>> was = diffBaseline.get();
            if (was.isPresent()) {
                String now = ISO8601.now();
                for (Record relation : deletedRecords) {
                    state = state.setChild(
                            restoreDeleted(was.get().getValue(), state.getChild(), relation));
                }
                // Rely on autofix behaviours to resolve any conflicts that arose
                state = AutoFix.onLoad(state, now);
            }
            return state;
        });
    }

    public void loadChild(Record childIdentity) throws IOException {
        try (Inhibit xact = this.changed.inhibit()) {
            Optional<RecordStorage> parentDir = storage.get();
            if (!parentDir.isPresent()) {
                throw new IOException("Parent is not saved yet");
            }
            Optional<RecordStorage> existing = parentDir.get().getChild(childIdentity.getIdentifier());
            if (existing.isPresent()) {
                loadImpl(existing.get());
            } else {
                // Synthesise a child baseline, since none has been saved yet
                String prefix = childIdentity.toString();
                int count = 0;
                Directory childDir;
                do {
                    // Avoid name collisions
                    String name = count == 0 ? prefix : prefix + "_" + count;
                    childDir = parentDir.get().resolve(name);
                    ++count;
                } while (Files.isDirectory(childDir.getPath()));
                BaselinePair state = undo.get();
                Optional<Record> systemOfInterest = state.getChild().get(
                        childIdentity.getIdentifier(), Item.item);
                if (systemOfInterest.isPresent()) {
                    state = state.setParent(state.getChild());
                    state = state.setChild(Baseline.create(childIdentity));
                    Optional<Directory> newDir = Optional.of(childDir);
                    storage.set(newDir);
                    undo.reset(state);
                    savedState.set(state);
                    loadVersionControl(newDir);
                } else {
                    throw new IOException("No such child");
                }
            }
            if (!lastChildIdentity.isEmpty()) {
                if (childIdentity.equals(lastChildIdentity.peek())) {
                    lastChildIdentity.pop();
                } else {
                    lastChildIdentity.clear();
                }
            }
            xact.changed();
        }
    }

    public Record getLastChild() throws EmptyStackException {
        return lastChildIdentity.peek();
    }

    public void load(RecordStorage dir) throws IOException {
        if (dir == null) {
            throw new IOException("Cannot load from null directory");
        }
        try (Inhibit xact = this.changed.inhibit()) {
            loadImpl(dir);
            lastChildIdentity.clear();
            xact.changed();
        }
    }

    private void loadImpl(RecordStorage dir) throws IOException {
        BaselinePair state = Baseline.loadUndoState(dir);
        Optional<RecordStorage> newDir = Optional.of(dir);
        storage.set(newDir);
        undo.reset(state);
        savedState.set(state);
        loadVersionControl(newDir);

        // Set undo again here to allow automatic changes to be undone
        undo.update(baselines -> AutoFix.onLoad(baselines, ISO8601.now()));
    }

    public boolean saveNeeded() {
        return !undo.get().equals(savedState.get());
    }

    public void reloadVersionControl() {
        loadVersionControl(storage.get());
    }

    private void loadVersionControl(Optional<RecordStorage> dir) {
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
            Optional<RecordStorage> dir = storage.get();
            if (dir.isPresent()) {
                saveToImpl(dir.get());
            } else {
                throw new IOException("Model has not been saved yet");
            }
            xact.changed();
        }
    }

    private void saveToImpl(RecordStorage dir) throws IOException {
        BaselinePair state = undo.get();
        try (SaveTransaction transaction = new SaveTransaction(newVersionControl)) {
            Baseline.saveUndoState(transaction, dir, state);
            transaction.commit();
        }
        Optional<RecordStorage> newDir = Optional.of(dir);
        storage.set(newDir);
        savedState.set(state);
        loadVersionControl(newDir);
    }

    public void saveTo(RecordStorage dir) throws IOException {
        try (Inhibit xact = this.changed.inhibit()) {
            saveToImpl(dir);
            xact.changed();
        }
    }

    public void setDiffVersion(Optional<VersionInfo> version) {
        try (Inhibit xact = this.changed.inhibit()) {
            this.diffVersion.set(version);
            loadDiffBaseline(storage.get(), version);
            xact.changed();
        }
    }

    private void loadDiffBaseline(
            Optional<RecordStorage> storage, Optional<VersionInfo> version) {
        if (storage.isPresent() && version.isPresent()) {
            try {
                Baseline was = Baseline.load(
                        storage.get(),
                        storage.get().getOpenerForVersion(version));
                diffBaseline.set(Optional.of(new Pair<>(version.get(), was)));
            } catch (IOException ex) {
                LOG.log(Level.WARNING, null, ex);
                diffBaseline.set(Optional.empty());
            }
        } else {
            diffBaseline.set(Optional.empty());
        }
    }

    private final AtomicReference<Optional<RecordStorage>> storage;
    private final AtomicReference<Optional<VersionInfo>> diffVersion
            = new AtomicReference<>(Optional.empty());
    private final AtomicReference<Optional<Pair<VersionInfo, Baseline>>> diffBaseline
            = new AtomicReference<>(Optional.empty());
    private final Stack<Record> lastChildIdentity = new Stack<>();
    private final UndoBuffer<BaselinePair> undo;
    private final Executor executor;
    private final AtomicReference<BaselinePair> savedState;
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
        Optional<RecordStorage> current = storage.get();
        if (current.isPresent() && current.get() instanceof FileStorage) {
            FileStorage currentStorage = (FileStorage) current.get();
            try (Inhibit xact = this.changed.inhibit()) {
                this.loadVersionControl(Optional.of(currentStorage.renameDirectory(from, to)));
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
            if (undo.update(state -> state.setParent(
                    update.apply(state.getParent())))) {
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
            if (undo.update(state -> state.setChild(
                    update.apply(state.getChild())))) {
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
    public void updateState(UnaryOperator<BaselinePair> update) {
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
    public BaselinePair getState() {
        return undo.get();
    }

    /**
     * Return a snapshot of the current functional baseline.
     *
     * @return
     */
    public Baseline getParent() {
        return undo.get().getParent();
    }

    /**
     * Return a snapshot of the current allocated baseline.
     *
     * @return
     */
    public Baseline getChild() {
        return undo.get().getChild();
    }

    public Optional<Record> getSystemOfInterest() {
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

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
import au.id.soundadvice.systemdesign.entity.RecordStore;
import au.id.soundadvice.systemdesign.moduleapi.Module;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.entity.RecordID;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;
import au.id.soundadvice.systemdesign.moduleapi.entity.TableFactory;
import au.id.soundadvice.systemdesign.moduleapi.storage.RecordStorage;
import au.id.soundadvice.systemdesign.moduleapi.util.ISO8601;
import au.id.soundadvice.systemdesign.physical.entity.Identity;
import au.id.soundadvice.systemdesign.storage.FileStorage;
import au.id.soundadvice.systemdesign.moduleapi.storage.VersionInfo;
import au.id.soundadvice.systemdesign.preferences.Modules;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.EmptyStackException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.util.Pair;
import javax.annotation.Nullable;

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

    public WhyHowPair<Optional<RecordStorage>> getStorage() {
        return storage.get();
    }

    public Executor getExecutor() {
        return executor;
    }

    private static final WhyHowPair<Baseline> EMPTY_BASELINE = new WhyHowPair<>(RecordStore.empty(), RecordStore.empty());
    private static final WhyHowPair<Optional<RecordStorage>> EMPTY_STORAGE = new WhyHowPair<>(Optional.empty(), Optional.empty());

    public static EditState init(Executor executor) {
        EditState result = new EditState(
                executor,
                EMPTY_STORAGE,
                EMPTY_BASELINE,
                true);
        result.subscribe(() -> result.updateState(
                baselines -> AutoFix.onChange(baselines, ISO8601.now())));
        return result;
    }

    public void clear() {
        try (Inhibit xact = this.changed.inhibit()) {
            WhyHowPair<Baseline> state = EMPTY_BASELINE;
            this.setStorage(EMPTY_STORAGE);
            loadVersionControl(Optional.empty());
            this.lastChildIdentity.clear();
            this.undo.reset(state);
            this.savedState.set(state);
            xact.changed();
        }
    }

    private EditState(
            Executor executor,
            WhyHowPair<Optional<RecordStorage>> currentDirectory,
            WhyHowPair<Baseline> undo,
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
        WhyHowPair<Optional<RecordStorage>> newStorage = storage.get();
        Optional<RecordStorage> newChildDir = newStorage.getParent();
        if (newChildDir.isPresent()) {
            try (Inhibit xact = this.changed.inhibit()) {
                WhyHowPair<Baseline> state = undo.get();
                WhyHowPair<Baseline> newSaved = savedState.get();
                RecordID childIdentifier = Identity.get(state.getChild()).getIdentifier();
                newStorage = newStorage
                        .setChild(newChildDir)
                        .setParent(newChildDir.get().getParent());
                state = state
                        .setChild(state.getParent())
                        .setParent(newStorage.getParent().map(s -> {
                            try {
                                return s.loadBaseline(tableFactories, Optional.empty());
                            } catch (IOException ex) {
                                throw new UncheckedIOException(ex);
                            }
                        }).orElse(RecordStore.empty()));
                newSaved = newSaved
                        .setChild(newSaved.getParent())
                        .setParent(state.getParent());
                setStorage(newStorage);
                this.undo.set(state);
                this.savedState.set(newSaved);
                lastChildIdentity.push(childIdentifier);
                this.loadVersionControl(newChildDir);
                xact.changed();
            } catch (UncheckedIOException ex) {
                throw ex.getCause();
            }
        }
    }

    public void loadChild(Record systemOfInterest, String now) throws IOException {
        WhyHowPair<Optional<RecordStorage>> newStorage = storage.get();
        Optional<RecordStorage> newParentDir = newStorage.getChild();
        if (!newParentDir.isPresent()) {
            throw new IOException("Parent is not saved yet");
        }
        try (Inhibit xact = this.changed.inhibit()) {
            WhyHowPair<Baseline> state = undo.get();
            WhyHowPair<Baseline> newSaved = savedState.get();
            newStorage = newStorage
                    .setParent(newParentDir)
                    .setChild(newParentDir.get().getChild(systemOfInterest.getIdentifier()));
            state = state
                    .setParent(state.getChild())
                    .setChild(RecordStore.empty());
            Record parentIdentity = Identity.get(state.getParent());
            Record childIdentity = Identity.create(now, parentIdentity, systemOfInterest);
            if (newStorage.getChild().isPresent()) {
                // Try loading
                state = state
                        .setChild(newStorage.getChild().map(s -> {
                            try {
                                return s.loadBaseline(tableFactories, Optional.empty());
                            } catch (IOException ex) {
                                throw new UncheckedIOException(ex);
                            }
                        }).orElse(RecordStore.empty()));
            } else {
                newStorage = newStorage.setChild(Optional.of(newParentDir.get().createChild(childIdentity)));
            }
            if (state.getChild().isEmpty()) {
                state = state.updateChild(baseline -> baseline.add(childIdentity));
            }
            newSaved = newSaved
                    .setParent(newSaved.getChild())
                    .setChild(state.getChild());
            setStorage(newStorage);
            this.undo.set(state);
            this.savedState.set(newSaved);
            if (!lastChildIdentity.isEmpty()) {
                if (systemOfInterest.getIdentifier().equals(lastChildIdentity.peek())) {
                    lastChildIdentity.pop();
                } else {
                    lastChildIdentity.clear();
                }
            }
            this.loadVersionControl(newStorage.getChild());
            xact.changed();
        }
    }

    public void load(RecordStorage childDir, String now) throws IOException {
        if (childDir == null) {
            throw new IOException("Cannot load from null directory");
        }
        try (Inhibit xact = this.changed.inhibit()) {
            WhyHowPair<Optional<RecordStorage>> newStorage = new WhyHowPair<>(
                    childDir.getParent(), Optional.of(childDir));
            WhyHowPair<Baseline> state = newStorage.map(dir -> {
                try {
                    if (dir.isPresent()) {
                        return dir.get().loadBaseline(tableFactories, Optional.empty());
                    } else {
                        return RecordStore.empty();
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
            setStorage(newStorage);
            this.undo.set(state);
            this.savedState.set(state);
            lastChildIdentity.clear();
            this.loadVersionControl(newStorage.getChild());
            xact.changed();
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    public boolean hasLastChild() {
        return !lastChildIdentity.isEmpty();
    }

    private Stream<Record> followForeignKeysUp(Baseline baseline, Record record) {
        return Stream.concat(
                record.getReferences().values().stream()
                .flatMap(id -> {
                    Optional<Record> newRecord = baseline.getAnyType(id);
                    return newRecord.map(next -> followForeignKeysUp(baseline, next))
                            .orElse(Stream.empty());
                }),
                Stream.of(record));
    }

    private Stream<Record> followForeignKeysDown(Baseline baseline, Record record) {
        return Stream.concat(
                Stream.of(record),
                baseline.findReverse(record.getIdentifier()));
    }

    /**
     * Attempt to restore a record, plus all records it depends upon, plus all
     * recursive references to the restored record
     *
     * @param sample The deleted record to restore into the current baseline.
     */
    public void restoreDeleted(Record sample) {
        updateState(state -> {
            // Restore the entire deleted tree of relations
            Optional<Pair<VersionInfo, Baseline>> wasInfo = diffBaseline.get();
            Optional<Record> record = wasInfo.flatMap(pair -> pair.getValue().get(sample));
            if (record.isPresent()) {
                String now = ISO8601.now();
                Baseline was = wasInfo.get().getValue();
                Stream<Record> toRestore = Stream.concat(
                        followForeignKeysUp(was, record.get()),
                        followForeignKeysDown(was, record.get()));
                Baseline is = state.getChild();
                Iterator<Record> it = toRestore.iterator();
                while (it.hasNext()) {
                    Record candidate = it.next();
                    Optional<Record> existing = is.get(candidate);
                    if (!existing.isPresent()) {
                        is = is.add(candidate);
                    }
                }
                state = state.setChild(is);
                // Rely on autofix behaviours to resolve any conflicts that arose
                state = AutoFix.onLoad(state, now);
            }
            return state;
        });
    }

    public RecordID getLastChild() throws EmptyStackException {
        return lastChildIdentity.peek();
    }

    private void setStorage(WhyHowPair<Optional<RecordStorage>> newStorage) {
        WhyHowPair<Optional<RecordStorage>> oldStorage = this.storage.getAndSet(newStorage);
        // Close any unused storage objects
        if (oldStorage.getParent().isPresent()) {
            RecordStorage old = oldStorage.getParent().get();
            if (newStorage.stream().noneMatch(n -> n.isPresent() && n.get().equals(old))) {
                try {
                    old.close();
                } catch (IOException ex) {
                    Logger.getLogger(EditState.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        if (oldStorage.getChild().isPresent()) {
            RecordStorage old = oldStorage.getChild().get();
            if (newStorage.stream().noneMatch(n -> n.isPresent() && n.get().equals(old))) {
                try {
                    old.close();
                } catch (IOException ex) {
                    Logger.getLogger(EditState.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    class AllTables implements TableFactory {

        private final Map<String, Table> tables;

        private AllTables(Stream<Table> tables) {
            this.tables = tables.collect(Collectors.toMap(Table::getTableName, t -> t));
        }

        @Override
        public Table apply(String name) {
            @Nullable
            Table result = tables.get(name);
            if (result == null) {
                result = new Table.Default(name);
            }
            return result;
        }

    }
    private final AllTables tableFactories = new AllTables(
            Modules.getModules().flatMap(Module::getTables));

    public boolean saveNeeded() {
        return !undo.get().equals(savedState.get());
    }

    public void reloadVersionControl() {
        loadVersionControl(storage.get().getChild());
    }

    private void loadVersionControl(Optional<RecordStorage> dir) {
        loadDiffBaseline(dir, diffVersion.get());
    }

    public void save() throws IOException {
        try (Inhibit xact = this.changed.inhibit()) {
            WhyHowPair<Optional<RecordStorage>> dir = storage.get();
            if (dir.getChild().isPresent()) {
                saveToImpl(dir);
            } else {
                throw new IOException("Model has not been saved yet");
            }
            xact.changed();
        }
    }

    private void saveToImpl(WhyHowPair<Optional<RecordStorage>> dir) throws IOException {
        WhyHowPair<Baseline> state = undo.get();
        dir.getChild().get().saveBaseline(state.getChild());
        Baseline parent = state.getParent();
        Optional<RecordStorage> parentDir = dir.getParent();
        if (!parent.isEmpty() && parentDir.isPresent()) {
            try (RecordStorage parentDirActual = parentDir.get()) {
                parentDirActual.saveBaseline(parent);
            }
        }
        setStorage(dir);
        savedState.set(state);
        loadVersionControl(dir.getChild());
    }

    public void saveTo(RecordStorage dir) throws IOException {
        try (Inhibit xact = this.changed.inhibit()) {
            WhyHowPair<Optional<RecordStorage>> newStorage
                    = new WhyHowPair<>(dir.getParent(), Optional.of(dir));
            saveToImpl(newStorage);
            xact.changed();
        }
    }

    public void setDiffVersion(Optional<VersionInfo> version) {
        try (Inhibit xact = this.changed.inhibit()) {
            this.diffVersion.set(version);
            loadDiffBaseline(storage.get().getChild(), version);
            xact.changed();
        }
    }

    private void loadDiffBaseline(
            Optional<RecordStorage> storage, Optional<VersionInfo> version) {
        if (storage.isPresent() && version.isPresent()) {
            try {
                Baseline was = storage.get().loadBaseline(tableFactories, version.map(VersionInfo::getId));
                diffBaseline.set(Optional.of(new Pair<>(version.get(), was)));
            } catch (IOException ex) {
                LOG.log(Level.WARNING, null, ex);
                diffBaseline.set(Optional.empty());
            }
        } else {
            diffBaseline.set(Optional.empty());
        }
    }

    private final AtomicReference<WhyHowPair<Optional<RecordStorage>>> storage;
    private final AtomicReference<Optional<VersionInfo>> diffVersion
            = new AtomicReference<>(Optional.empty());
    private final AtomicReference<Optional<Pair<VersionInfo, Baseline>>> diffBaseline
            = new AtomicReference<>(Optional.empty());
    private final Stack<RecordID> lastChildIdentity = new Stack<>();
    private final UndoBuffer<WhyHowPair<Baseline>> undo;
    private final Executor executor;
    private final AtomicReference<WhyHowPair<Baseline>> savedState;
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
        WhyHowPair<Optional<RecordStorage>> current = storage.get();
        Optional<RecordStorage> child = current.getChild();
        if (child.isPresent() && child.get() instanceof FileStorage) {
            FileStorage currentStorage = (FileStorage) child.get();
            try (Inhibit xact = this.changed.inhibit()) {
                currentStorage = currentStorage.renameDirectory(from, to);
                current = current.setChild(Optional.of(currentStorage));
                this.setStorage(current);
                this.loadVersionControl(Optional.of(currentStorage));
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
    public void updateParent(UnaryOperator<Baseline> update) {
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
    public void updateChild(UnaryOperator<Baseline> update) {
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
    public void updateState(UnaryOperator<WhyHowPair<Baseline>> update) {
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
    public WhyHowPair<Baseline> getState() {
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

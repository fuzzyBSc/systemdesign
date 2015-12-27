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

import au.id.soundadvice.systemdesign.physical.Identity;
import static au.id.soundadvice.systemdesign.files.BeanFile.saveBeans;
import au.id.soundadvice.systemdesign.files.BeanReader;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.files.FileOpener;
import au.id.soundadvice.systemdesign.files.SaveTransaction;
import au.id.soundadvice.systemdesign.moduleapi.Identifiable;
import au.id.soundadvice.systemdesign.moduleapi.UndoState;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import au.id.soundadvice.systemdesign.relation.RelationStore;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.stream.Collectors;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.preferences.Modules;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * A class for linking a concrete Relations implementation into the model.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Baseline {

    private static final Relations EMPTY = create(Identity.create());

    public static Relations empty() {
        return EMPTY;
    }

    /**
     * Create a baseline in memory corresponding to the specified identity.
     *
     * @param identity The identity of the system of interest for this
     * AllocatedBaseline
     * @return The allocated baseline
     */
    public static Relations create(Identity identity) {
        return RelationStore.empty().add(identity);
    }

    public static UndoState createUndoState() {
        return new UndoState(empty(), create(Identity.create()));
    }

    /**
     * Load an allocated baseline from the nominated directory.
     *
     * @param directory The directory to load from
     * @param opener An interface for opening files from the directory that may
     * be associated with a specific historical version for diff support
     * purposes.
     * @return
     * @throws java.io.IOException
     */
    public static Relations load(
            Directory directory, FileOpener opener) throws IOException {
        Optional<Identity> identity = directory.getIdentity();
        identity.orElseThrow(() -> new IOException("No Item found in directory"));

        try {
            // For each module
            Stream<Relation> relations = Modules.getModules()
                    .flatMap(module -> {
                        // For each type
                        Stream<Identifiable> beans = module.getMementoTypes()
                                // Read the corresponding file
                                .flatMap(beanClass -> {
                                    try {
                                        Optional<BufferedReader> buffered = opener.open(beanClass);
                                        try (BeanReader<? extends Identifiable> reader = BeanReader.fromReader(beanClass, buffered)) {
                                            return reader.lines()
                                                    /*
                                                     * We need to do the full
                                                     * read and collect while
                                                     * the reader is still open
                                                     */
                                                    .collect(Collectors.toList())
                                                    .stream();
                                        }
                                    } catch (IOException ex) {
                                        throw new UncheckedIOException(ex);
                                    }
                                });
                        // Restore all of the mementos for this module as one stream
                        return module.restoreMementos(beans);
                    });
            return RelationStore.valueOf(relations);
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    public static UndoState loadUndoState(Directory directory) throws IOException {
        Directory functionalDirectory = directory.getParent();
        if (functionalDirectory.getIdentity().isPresent()) {
            // Subsystem design - load functional baseline as well
            Relations functionalBaseline = Baseline.load(
                    functionalDirectory, functionalDirectory);
            Relations allocatedBaseline = Baseline.load(
                    directory, directory);
            return new UndoState(
                    functionalBaseline,
                    allocatedBaseline);
        }
        // Top-level design
        return new UndoState(
                Baseline.empty(),
                Baseline.load(directory, directory));
    }

    /**
     * Save the allocated baseline to the nominated directory.
     *
     * @param transaction Save under the specified transaction. The transaction
     * will ensure that all files are written without error before moving the
     * files to overwrite existing file entries.
     * @param directory The directory to save to.
     * @param baseline The baseline to save
     * @throws java.io.IOException
     */
    public static void save(SaveTransaction transaction, Directory directory, Relations baseline) throws IOException {
        if (baseline.size() > 1 || Files.isDirectory(directory.getPath())) {
            Files.createDirectories(directory.getPath());
            try {
                Modules.getModules()
                        .flatMap(module -> module.saveMementos(baseline))
                        // Sort by UUID
                        .sorted((left, right) -> left.getUuid().compareTo(right.getUuid()))
                        // Collect for saving
                        .collect(Collectors.groupingBy(Object::getClass))
                        // Save each type
                        .entrySet().stream().forEach(entry -> {
                            Path csv = directory.getPathForClass(entry.getKey());
                            try {
                                saveBeans(transaction, csv,
                                        Identifiable.class,
                                        entry.getValue().stream()
                                        .map(bean -> (Identifiable) bean));
                            } catch (IOException ex) {
                                throw new UncheckedIOException(ex);
                            }
                        });
            } catch (UncheckedIOException ex) {
                throw ex.getCause();
            }

        }
    }

    static void saveUndoState(SaveTransaction transaction, Directory dir, UndoState state) throws IOException {
        Baseline.save(transaction, dir.getParent(), state.getFunctional());
        Baseline.save(transaction, dir, state.getAllocated());
    }
}

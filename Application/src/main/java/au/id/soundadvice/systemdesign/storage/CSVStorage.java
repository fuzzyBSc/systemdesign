/*
 * To change this license header, choose License Headers in Project Properties.
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
package au.id.soundadvice.systemdesign.storage;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import au.id.soundadvice.systemdesign.entity.RecordStore;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.storage.RecordStorage;
import au.id.soundadvice.systemdesign.moduleapi.storage.RecordTypeFactory;
import au.id.soundadvice.systemdesign.physical.Identity;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.storage.VersionInfo;
import au.id.soundadvice.systemdesign.storage.files.RecordReader;
import au.id.soundadvice.systemdesign.storage.files.SaveTransaction;
import au.id.soundadvice.systemdesign.storage.versioning.IdentityValidator;
import au.id.soundadvice.systemdesign.storage.versioning.VersionControl;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class CSVStorage implements FileStorage, IdentityValidator {

    private static final Logger LOG = Logger.getLogger(CSVStorage.class.getName());

    private static final String IDENTITY_FILE = "identity.sysrec";

    private static final String EXT = ".sysrec";

    private final VersionControl vcs;
    private final Path path;

    private CSVStorage(VersionControl vcs, Path path) {
        this.vcs = vcs;
        this.path = path;
    }

    private Optional<String[]> readLine(CSVReader reader) throws IOException {
        String[] line = reader.readNext();
        while (line != null && line.length == 1 && line[0].isEmpty()) {
            // Ignore empty lines
            line = reader.readNext();
        }
        return Optional.ofNullable(line);
    }

    private Stream<Record> loadRecords(Table type, CSVReader reader) throws IOException {
        Optional<String[]> header = readLine(reader);
        if (header.isPresent()) {
            for (;;) {
                Iterator<String[]> iterator = new Iterator<String[]>() {
                    Optional<String[]> nextLine = readLine(reader);

                    @Override
                    public boolean hasNext() {
                        return nextLine.isPresent();
                    }

                    @Override
                    public String[] next() {
                        if (nextLine.isPresent()) {
                            try {
                                String[] currentLine = nextLine.get();
                                nextLine = readLine(reader);
                                return currentLine;
                            } catch (IOException ex) {
                                throw new UncheckedIOException(ex);
                            }
                        } else {
                            throw new NoSuchElementException();
                        }
                    }
                };

                return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL), false)
                        .map(line -> {
                            int linelen = Math.min(line.length, header.get().length);
                            Map<String, String> values = new HashMap<>();
                            for (int ii = 0; ii < linelen; ++ii) {
                                values.put(header.get()[ii], line[ii]);
                            }
                            for (int ii = linelen; ii < header.get().length; ++ii) {
                                values.put(header.get()[ii], "");
                            }
                            return Record.load(type, values);
                        });

            }
        } else {
            return Stream.empty();
        }
    }

    @Override
    public Baseline loadBaseline(RecordTypeFactory factory, Optional<String> label) throws IOException {
        try {
            return RecordStore.valueOf(
                    vcs.listFiles(this, label)
                    .flatMap(file -> {
                        String typename = file;
                        if (typename.endsWith(EXT)) {
                            typename = typename.substring(0, typename.length() - EXT.length());
                        }
                        try (
                                BufferedReader reader = vcs.getBufferedReader(this, file, label);
                                CSVReader csv = new CSVReader(reader)) {
                            return loadRecords(factory.get(typename), csv);
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    }));
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    private static void saveRecords(SaveTransaction transaction, Path csv, List<Record> records) throws IOException {
        String[] headers = records.stream()
                .flatMap(record -> record.getAllFields().keySet().stream())
                .distinct().sorted()
                .toArray(String[]::new);
        Path directory = csv.getParent();
        Path tempFile = Files.createTempFile(directory, null, null);
        transaction.addJob(csv, tempFile);
        CSVWriter writer = new CSVWriter(Files.newBufferedWriter(tempFile));
        writer.writeNext(headers);
        records.stream()
                .sorted((left, right) -> left.getIdentifier().compareTo(right.getIdentifier()))
                .forEachOrdered(record -> {
                    writer.writeNext(
                            Stream.of(headers)
                            .map(key -> record.get(key).orElse(""))
                            .toArray(String[]::new)
                    );
                });
    }

    @Override
    public void saveBaseline(Baseline baseline) throws IOException {
        if (baseline.size() > 1 || Files.isDirectory(path)) {
            Files.createDirectories(path);
            try (SaveTransaction transaction = new SaveTransaction(vcs)) {
                baseline.stream()
                        // Collect for saving
                        .collect(Collectors.groupingBy(Record::getType))
                        // Save each type
                        .entrySet().stream().forEach(entry -> {
                            Path csv = path.resolve(entry.getKey().getTableName() + EXT);
                            try {
                                saveRecords(transaction, csv, entry.getValue());
                            } catch (IOException ex) {
                                throw new UncheckedIOException(ex);
                            }
                        });
                transaction.commit();
            } catch (UncheckedIOException ex) {
                throw ex.getCause();
            }
        }
    }

    @Override
    public Optional<RecordStorage> getParent() {
        Path parentPath = path.getParent();
        VersionControl parentVcs = VersionControl.forPath(parentPath);
        return Optional.of(new CSVStorage(parentVcs, parentPath));
    }

    @Override
    public Optional<RecordStorage> getChild(String identifier) throws IOException {
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(path)) {
            return StreamSupport.stream(dir.spliterator(), true)
                    .filter(childPath -> {
                        if (Files.isDirectory(childPath)) {
                            Optional<Record> identity = getIdentity(childPath);
                            return identity.isPresent() && identifier.equals(identity.get().getIdentifier());
                        } else {
                            return false;
                        }
                    })
                    .findAny()
                    .map(childPath -> {
                        VersionControl parentVcs = VersionControl.forPath(path);
                        return new CSVStorage(parentVcs, childPath);
                    });
        }
    }

    @Override
    public String getIdentityFilename() {
        return IDENTITY_FILE;
    }

    @Override
    public Path getDirectoryPath() {
        return path;
    }

    @Override
    public RecordStorage renameDirectory(Path from, Path to) throws IOException {
        if (path.equals(from)) {
            vcs.renameDirectory(from, to);
            VersionControl toVcs = VersionControl.forPath(to);
            return new CSVStorage(toVcs, to);
        }
        return this;
    }

    public static Optional<Record> getIdentity(Path path) {
        if (Files.isDirectory(path)) {
            path = path.resolve(IDENTITY_FILE);
        }
        if (Files.exists(path)) {
            try (BufferedReader buffered = Files.newBufferedReader(path)) {
                return getIdentity(buffered);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, null, ex);
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    public static Optional<Record> getIdentity(BufferedReader buffered) throws IOException {
        try (CSVReader csvreader = new CSVReader(buffered);
                RecordReader reader = new RecordReader(Identity.identity, csvreader)) {
            return reader.read();
        }
    }

    @Override
    public boolean isIdentityMatched(BufferedReader reader) {
        try {
            Optional<Record> sample = getIdentity(reader);
            Optional<Record> authorative = getIdentity(path);
            return authorative.isPresent() && authorative.equals(sample);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
            return false;
        }
    }

    @Override
    public boolean identityFileExists() throws IOException {
        return vcs.exists(this, IDENTITY_FILE, Optional.empty());
    }

    @Override
    public Stream<VersionInfo> getBranches() throws IOException {
        return vcs.getBranches();
    }

    @Override
    public Stream<VersionInfo> getVersions() throws IOException {
        return vcs.getVersions();
    }
}

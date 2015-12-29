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
package au.id.soundadvice.systemdesign.files;

import au.id.soundadvice.systemdesign.physical.beans.IdentityBean;
import au.id.soundadvice.systemdesign.physical.Identity;
import au.id.soundadvice.systemdesign.versioning.IdentityValidator;
import au.id.soundadvice.systemdesign.versioning.VersionControl;
import au.id.soundadvice.systemdesign.versioning.VersionInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

/**
 * Given a base directory, compute paths to well-known files and other useful
 * relationships.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Directory implements IdentityValidator, FileOpener {

    private static final Logger LOG = Logger.getLogger(Directory.class.getName());
    private static final String IDENTITY_CSV = "identity.csv";

    @Override
    public String toString() {
        return path.toString();
    }

    public static Directory forPath(Path path) {
        return new Directory(Optional.empty(), path);
    }

    private Directory(Optional<Directory> parent, Path root) {
        this.parent = parent;
        this.path = root;
        this.identityFile = root.resolve(IDENTITY_CSV);
        this.itemsFile = root.resolve("items.csv");
        this.itemViewsFile = root.resolve("itemViews.csv");
        this.interfacesFile = root.resolve("interfaces.csv");
        this.functionsFile = root.resolve("functions.csv");
        this.functionViewsFile = root.resolve("functionViews.csv");
        this.flowsFile = root.resolve("flows.csv");
        this.flowTypesFile = root.resolve("flowTypes.csv");
        this.budgetsFile = root.resolve("budgets.csv");
        this.budgetAllocationsFile = root.resolve("budgetAllocations.csv");
    }

    private final Optional<Directory> parent;
    private final Path path;
    private final Path identityFile;
    private final Path itemsFile;
    private final Path itemViewsFile;
    private final Path interfacesFile;
    private final Path functionsFile;
    private final Path functionViewsFile;
    private final Path flowsFile;
    private final Path flowTypesFile;
    private final Path budgetsFile;
    private final Path budgetAllocationsFile;

    public Path getIdentityFile() {
        return identityFile;
    }

    public static Optional<Identity> getIdentity(Path path) {
        if (Files.isDirectory(path)) {
            path = path.resolve(IDENTITY_CSV);
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

    public static Optional<Identity> getIdentity(BufferedReader buffered) throws IOException {
        try (BeanReader<IdentityBean> reader = BeanReader.fromReader(
                IdentityBean.class, buffered)) {
            return reader.lines().map(Identity::new).findFirst();
        }
    }

    public Optional<Identity> getIdentity() {
        return getIdentity(identityFile);
    }

    public Path getPath() {
        return path;
    }

    public Path getItems() {
        return itemsFile;
    }

    public Path getItemViews() {
        return itemViewsFile;
    }

    public Path getInterfaces() {
        return interfacesFile;
    }

    public Path getFunctions() {
        return functionsFile;
    }

    public Path getFunctionViews() {
        return functionViewsFile;
    }

    public Path getFlows() {
        return flowsFile;
    }

    public Path getFlowTypes() {
        return flowTypesFile;
    }

    public Path getBudgets() {
        return budgetsFile;
    }

    public Path getBudgetAllocations() {
        return budgetAllocationsFile;
    }

    public DirectoryStream<Path> getChildren() throws IOException {
        return Files.newDirectoryStream(
                path, candidate -> Directory.getIdentity(candidate).isPresent());
    }

    public Directory getParent() {
        return parent.orElseGet(
                () -> new Directory(Optional.empty(), path.getParent()));
    }

    private static Optional<Directory> getChild(Directory parent, UUID uuid) throws IOException {
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(parent.path)) {
            return StreamSupport.stream(dir.spliterator(), true)
                    .filter(path -> {
                        if (Files.isDirectory(path)) {
                            Optional<Identity> identity = Directory.getIdentity(path);
                            return identity.isPresent() && uuid.equals(identity.get().getUuid());
                        } else {
                            return false;
                        }
                    })
                    .map(path -> new Directory(Optional.of(parent), path))
                    .findAny();
        }
    }

    public Optional<Directory> getChild(UUID uuid) throws IOException {
        // Call out via static to avoid accidental references to this
        return getChild(this, uuid);
    }

    public Directory resolve(String name) {
        return new Directory(Optional.of(this), this.path.resolve(name));
    }

    @Override
    public String getIdentityFileName() {
        return IDENTITY_CSV;
    }

    @Override
    public Path getDirectoryPath() {
        return path;
    }

    @Override
    public boolean isIdentityMatched(BufferedReader reader) {
        try {
            Optional<Identity> sample = getIdentity(reader);
            Optional<Identity> authorative = getIdentity(getIdentityFile());
            return authorative.isPresent() && authorative.equals(sample);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
            return false;
        }
    }

    public Path getPathForClass(Class<?> clazz) {
        return path.resolve(getFileNameForClass(clazz));
    }

    public String getFileNameForClass(Class<?> clazz) {
        String name = clazz.getSimpleName();
        // Bean is redundant on disks
        if (name.endsWith("Bean")) {
            name = name.substring(0, name.length() - 4);
        }
        // Lower camel case
        char firstCharToLower = Character.toLowerCase(name.charAt(0));
        if ("Identity".equals(name)) {
            // Singular
            name = firstCharToLower + name.substring(1) + ".csv";
        } else {
            // Plural
            name = firstCharToLower + name.substring(1) + "s.csv";
        }
        return name;
    }

    public FileOpener getOpenerForVersion(
            VersionControl versionControl, Optional<VersionInfo> version) {
        if (version.isPresent()) {
            return clazz -> {
                String name = getFileNameForClass(clazz);
                return versionControl.getBufferedReader(
                        Directory.this, name, version);
            };
        } else {
            return this;
        }
    }

    @Override
    public Optional<BufferedReader> open(Class<?> clazz) throws IOException {
        Path filepath = path.resolve(getFileNameForClass(clazz));
        if (Files.exists(filepath)) {
            return Optional.of(Files.newBufferedReader(filepath));
        } else {
            return Optional.empty();
        }
    }
}
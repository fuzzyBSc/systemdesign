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

import au.id.soundadvice.systemdesign.beans.IdentityBean;
import au.id.soundadvice.systemdesign.model.Identity;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Given a base directory, compute paths to well-known files and other useful
 * relationships.
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class Directory {

    private static final Logger LOG = Logger.getLogger(Directory.class.getName());

    @Override
    public String toString() {
        return root.toString();
    }

    public Directory(Path root) {
        this.root = root;
        this.identityFile = root.resolve("identity.csv");
        this.modelFile = root.resolve("model.csv");
        this.itemsFile = root.resolve("items.csv");
        this.interfacesFile = root.resolve("interfaces.csv");
        this.functionsFile = root.resolve("functions.csv");
        this.flowsFile = root.resolve("flows.csv");
        this.hazardsFile = root.resolve("hazards.csv");
        this.requirementsFile = root.resolve("requirements.csv");
    }

    private final Path root;
    private final Path identityFile;
    private final Path modelFile;
    private final Path itemsFile;
    private final Path interfacesFile;
    private final Path functionsFile;
    private final Path flowsFile;
    private final Path hazardsFile;
    private final Path requirementsFile;

    public Path getIdentityFile() {
        return identityFile;
    }

    @Nullable
    public static Identity getIdentity(Path path) {
        if (Files.isDirectory(path)) {
            path = path.resolve("identity.csv");
        }
        if (Files.exists(path)) {
            try (BeanReader<IdentityBean> reader = BeanReader.forPath(IdentityBean.class, path)) {
                // Only read the first entry
                IdentityBean bean = reader.read();
                return bean == null ? null : new Identity(bean);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, null, ex);
                return null;
            }
        } else {
            // A nonexistent file is treated as empty
            return null;
        }
    }

    public Identity getIdentity() {
        return getIdentity(identityFile);
    }

    public Path getPath() {
        return root;
    }

    public Path getModel() {
        return modelFile;
    }

    public Path getItems() {
        return itemsFile;
    }

    public Path getInterfaces() {
        return interfacesFile;
    }

    public Path getFunctions() {
        return functionsFile;
    }

    public Path getFlows() {
        return flowsFile;
    }

    public Path getHazards() {
        return hazardsFile;
    }

    public Path getRequirements() {
        return requirementsFile;
    }

    private static Path getDictionary(Directory root) throws IOException {
        Directory current = root;
        Directory last = root;
        Path projectRoot = null;
        while (projectRoot == null) {
            if (current.getIdentity() == null) {
                projectRoot = last.getPath();
            } else {
                // Keep looping
                last = current;
            }
        }
        return projectRoot.resolve("dictionary.csv");
    }

    public Path getDictionary() throws IOException {
        // Call out via static to avoid accidental references to this
        return getDictionary(this);
    }

    public DirectoryStream<Path> getChildren() throws IOException {
        return Files.newDirectoryStream(
                root, path -> Directory.getIdentity(path) != null);
    }

    public Directory getParent() {
        return new Directory(root.getParent());
    }

    @Nullable
    private static Directory getChild(Directory parent, UUID uuid) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent.root)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    Identity identity = Directory.getIdentity(path);
                    if (identity != null && uuid.equals(identity.getUuid())) {
                        return new Directory(path);
                    }
                }
            }
            return null;
        }
    }

    @Nullable
    public Directory getChild(UUID uuid) throws IOException {
        // Call out via static to avoid accidental references to this
        return getChild(this, uuid);
    }
}

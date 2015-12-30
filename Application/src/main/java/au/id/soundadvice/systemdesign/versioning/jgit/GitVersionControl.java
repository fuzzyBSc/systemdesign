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
package au.id.soundadvice.systemdesign.versioning.jgit;

import au.id.soundadvice.systemdesign.versioning.IdentityValidator;
import au.id.soundadvice.systemdesign.versioning.VersionControl;
import au.id.soundadvice.systemdesign.versioning.VersionInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Calendar;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javafx.util.Pair;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class GitVersionControl implements VersionControl {

    private static final Logger LOG = Logger.getLogger(GitVersionControl.class.getName());

    public static void init(Path path) throws GitAPIException {
        Git repo = Git.init().setDirectory(path.toFile()).call();
        try {
            repo.add().addFilepattern(".").call();
        } finally {
            repo.close();
        }
    }

    public GitVersionControl(Path path) throws IOException {
        try {
            // Cribbed from Git.open, but with findGitDir rather than setGitDir
            // and extracting the location.
            FS fs = FS.DETECTED;
            RepositoryCache.FileKey key = RepositoryCache.FileKey.lenient(path.toFile(), fs);
            RepositoryBuilder builder = new RepositoryBuilder()
                    .setFS(fs)
                    .findGitDir(key.getFile())
                    .setMustExist(true);
            repositoryRoot = Paths.get(builder.getGitDir().getAbsolutePath()).getParent();
            repo = new Git(builder.build());

            checkCSVMergeDriver(repositoryRoot);
        } catch (RuntimeException ex) {
            throw new IOException(ex);
        }
    }

    private final Git repo;
    private final Path repositoryRoot;

    @Override
    public Stream<VersionInfo> getBranches() throws IOException {
        try {
            return refToVersionInfo(repo.branchList().setListMode(ListMode.ALL).call().stream());
        } catch (GitAPIException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public Stream<VersionInfo> getVersions() throws IOException {
        try {
            return refToVersionInfo(repo.tagList().call().stream());
            // TODO: Support remote tags in the future?
            // Currently authentication issues are getting my way.
//            return refToVersionInfo(repo.lsRemote().setTags(true).call().stream());
        } catch (GitAPIException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public Optional<VersionInfo> getDefaultBaseline() {
        try {
            return refToVersionInfo(
                    repo.branchList()
                    .setListMode(ListMode.ALL)
                    .setContains("HEAD")
                    .call()
                    .stream()).findAny();
        } catch (GitAPIException ex) {
            return Optional.empty();
        }
    }

    private Stream<VersionInfo> refToVersionInfo(Stream<Ref> refs) {
        return refs.flatMap(ref -> {
            try {
                Iterable<RevCommit> iterable
                        = repo.log().add(ref.getObjectId()).setMaxCount(1).call();
                return StreamSupport.stream(iterable.spliterator(), false)
                        .map(commit -> {
                            Calendar timestamp = Calendar.getInstance();
                            timestamp.setTimeInMillis(commit.getCommitTime() * 1000L);
                            String name = ref.getName();
                            if (name.startsWith("refs/tags/")) {
                                name = name.substring(10);
                            } else if (name.startsWith("refs/heads/")) {
                                name = name.substring(11);
                            } else if (name.startsWith("refs/remotes/")) {
                                name = name.substring(13);
                            }
                            String description = name
                                    + " (" + commit.getId().abbreviate(7).name() + ')';
                            return new VersionInfo(
                                    ref.getObjectId().name(),
                                    description,
                                    timestamp);
                        });
            } catch (MissingObjectException | IncorrectObjectTypeException | GitAPIException ex) {
                LOG.log(Level.WARNING, null, ex);
                return Stream.empty();
            }
        });
    }

    @Override
    public void changed(Path filename) throws IOException {
        try {
            Path pattern = this.repositoryRoot.relativize(filename);
            if (Files.exists(filename)) {
                repo.add().addFilepattern(pattern.toString()).call();
            } else {
                repo.rm().addFilepattern(pattern.toString()).call();
            }
        } catch (GitAPIException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public boolean canCommit() {
        return true;
    }

    @Override
    public void commit(String message) throws IOException {
        try {
            repo.commit().setMessage(message).call();
        } catch (GitAPIException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void close() throws IOException {
        /*
         * Close repository because we don't have a way of causing the Git class
         * to do it for us.
         */
        repo.getRepository().close();
        repo.close();
    }

    @Override
    public void renameDirectory(Path from, Path to) throws IOException {
        try {
            Files.move(from, to);
            Path fromPattern = this.repositoryRoot.relativize(from);
            Path toPattern = this.repositoryRoot.relativize(to);
            repo.add().addFilepattern(toPattern.toString()).call();
            repo.rm().addFilepattern(fromPattern.toString()).call();
        } catch (GitAPIException ex) {
            throw new IOException(ex);
        }
    }

//    private final AtomicReference<Optional<Pair<String, List<DiffEntry>>>> diffCache
//            = new AtomicReference<>(Optional.empty());
    private final AtomicReference<Pair<String, ObjectId>> diffCache
            = new AtomicReference<>(new Pair<>(null, ObjectId.zeroId()));

    /**
     * Find the tree that contains the required identity.
     *
     * @return The ObjectId of the tree (directory) that contains the matching
     * identity within the supplied hierarchy.
     */
    private ObjectId findMatchingIdentity(
            IdentityValidator identityValidator,
            ObjectId tree) throws IOException {
        TreeWalk treeWalk = new TreeWalk(repo.getRepository());
        try {
            treeWalk.setRecursive(false);
            treeWalk.addTree(tree);

            while (treeWalk.next()) {
                if (treeWalk.isSubtree()) {
                    ObjectId candidateId = findMatchingIdentity(
                            identityValidator, treeWalk.getObjectId(0));
                    if (ObjectId.zeroId().equals(candidateId)) {
                        // Keep searching
                    } else {
                        return candidateId;
                    }
                } else if (identityValidator.getIdentityFileName().equals(treeWalk.getNameString())) {
                    // Read the identity file
                    ObjectLoader loader = repo.getRepository().open(
                            treeWalk.getObjectId(0));
                    ObjectStream stream = loader.openStream();
                    InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                    if (identityValidator.isIdentityMatched(new BufferedReader(reader))) {
                        // We found it
                        return tree;
                    }
                }
            }
            return ObjectId.zeroId();
        } finally {
            treeWalk.release();
        }
    }

    @Override
    public Optional<BufferedReader> getBufferedReader(
            IdentityValidator identityValidator,
            String filename, Optional<VersionInfo> version) throws IOException {
        if (version.isPresent()) {
            Pair<String, ObjectId> diff = diffCache.get();
            if (!version.get().getId().equals(diff.getKey())) {
                // Grab the id of the commit we are trying to diff against
                ObjectId id = ObjectId.fromString(version.get().getId());
                RevWalk revWalk = new RevWalk(repo.getRepository());
                try {
                    // Read the commit
                    RevCommit commit = revWalk.parseCommit(id);
                    ObjectId matchedDirectory = findMatchingIdentity(
                            identityValidator, commit.getTree());
                    diff = new Pair<>(version.get().getId(), matchedDirectory);
                    diffCache.set(diff);
                } finally {
                    revWalk.release();
                }
            }

            if (ObjectId.zeroId().equals(diff.getValue())) {
                // No such tree
                return Optional.empty();
            } else {
                // Find the file in this tree
                TreeWalk treeWalk = new TreeWalk(repo.getRepository());
                try {
                    treeWalk.setRecursive(false);
                    treeWalk.addTree(diff.getValue());

                    while (treeWalk.next()) {
                        if (filename.equals(treeWalk.getNameString())) {
                            // Read the file
                            ObjectLoader loader = repo.getRepository().open(
                                    treeWalk.getObjectId(0));
                            ObjectStream stream = loader.openStream();
                            InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                            return Optional.of(new BufferedReader(reader));
                        }
                    }
                    // No such file
                    return Optional.empty();
                } finally {
                    treeWalk.release();
                }
            }
        } else {
            Path path = identityValidator.getDirectoryPath().resolve(filename);
            if (Files.exists(path)) {
                return Optional.of(Files.newBufferedReader(path));
            } else {
                return Optional.empty();
            }
        }
    }

    private static void checkCSVMergeDriver(Path repositoryRoot) throws IOException {
        Path config = repositoryRoot.resolve(".git/config");
        String header = "[merge \"mergecsvwithuuid\"]";
        boolean stanzafound;
        try (BufferedReader reader = Files.newBufferedReader(config)) {
            stanzafound = reader.lines()
                    .filter(line -> line.equals(header))
                    .findAny()
                    .isPresent();
        }
        if (!stanzafound) {
            Path jarlocation;
            try {
                jarlocation = Paths.get(GitVersionControl.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            } catch (URISyntaxException ex) {
                jarlocation = Paths.get("I do not exist");
                LOG.log(Level.WARNING, null, ex);
            }
            Optional<Path> mergeDriver = Stream.of(
                    Paths.get("/usr/share/java/SystemDesign.jar"),
                    jarlocation)
                    .filter(Files::exists)
                    .findFirst();
            if (!mergeDriver.isPresent()) {
                LOG.warning("Unable to find jar to configure git merge");
                return;
            }
            String driver = "java -jar " + mergeDriver.get() + " -merge %O %A %B";

            if (Files.exists(config)) {
                Path configBackup = config.resolveSibling("config.bak");
                Files.copy(config, configBackup, StandardCopyOption.REPLACE_EXISTING);
            }
            try (Writer writer = Files.newBufferedWriter(
                    config,
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
                writer.write(System.lineSeparator());
                writer.write(header);
                writer.write(System.lineSeparator());
                writer.write("\tname = Merge CSV Files that contain a uuid column");
                writer.write(System.lineSeparator());
                writer.write("\tdriver = ");
                writer.write(driver);
                writer.write(System.lineSeparator());
                writer.write("\trecursive = binary");
                writer.write(System.lineSeparator());
            }
            Path info = repositoryRoot.resolve(".git/info");
            Files.createDirectories(info);
            Path attributes = info.resolve("attributes");
            if (Files.exists(attributes)) {
                Path attributesBackup = attributes.resolveSibling("gitattributes.bak");
                Files.copy(attributes, attributesBackup, StandardCopyOption.REPLACE_EXISTING);
            }
            try (Writer writer = Files.newBufferedWriter(
                    attributes,
                    StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
                writer.write(System.lineSeparator());
                writer.write("*.csv   merge=mergecsvwithuuid");
                writer.write(System.lineSeparator());
            }
        }
    }

    @Override
    public boolean isNull() {
        return false;
    }

}

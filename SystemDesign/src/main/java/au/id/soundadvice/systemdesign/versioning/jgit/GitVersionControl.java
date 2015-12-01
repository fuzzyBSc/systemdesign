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

import au.id.soundadvice.systemdesign.versioning.VersionControl;
import au.id.soundadvice.systemdesign.versioning.VersionInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
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
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FS;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class GitVersionControl implements VersionControl {

    private static final Logger LOG = Logger.getLogger(GitVersionControl.class.getName());

    public GitVersionControl(Path path) throws IOException {
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

    private Stream<VersionInfo> refToVersionInfo(Stream<Ref> refs) {
        return refs.flatMap(ref -> {
            try {
                Iterable<RevCommit> iterable
                        = repo.log().add(ref.getObjectId()).setMaxCount(1).call();
                return StreamSupport.stream(iterable.spliterator(), false)
                        .map(commit -> {
                            RevCommit[] parents = commit.getParents();
                            Calendar timestamp = Calendar.getInstance();
                            timestamp.setTimeInMillis(commit.getCommitTime() * 1000);
                            String name = ref.getName();
                            if (name.startsWith("refs/tags/")) {
                                name = name.substring(10);
                            } else if (name.startsWith("refs/heads/")) {
                                name = name.substring(11);
                            } else if (name.startsWith("refs/remotes/")) {
                                name = name.substring(13);
                            }
                            return new VersionInfo(
                                    ref.getObjectId().name(),
                                    name,
                                    timestamp,
                                    parents.length == 0
                                            ? Optional.empty()
                                            : Optional.of(parents[0].getId().getName()));
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
            repo.add().addFilepattern(pattern.toString()).call();
        } catch (GitAPIException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public boolean canCommit() {
        return true;
    }

    @Override
    public void commit() throws IOException {
        try {
            repo.commit().call();
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

    @Override
    public Optional<BufferedReader> getBufferedReader(Path path, Optional<VersionInfo> version) throws IOException {
        if (version.isPresent()) {
            ObjectId id = ObjectId.fromString(version.get().getId());
            try (RevWalk revWalk = new RevWalk(repo.getRepository())) {
                RevCommit commit = revWalk.parseCommit(id);
                RevTree tree = commit.getTree();
                try (TreeWalk treeWalk = new TreeWalk(repo.getRepository())) {
                    treeWalk.addTree(tree);
                    treeWalk.setRecursive(true);
                    Path pattern = this.repositoryRoot.relativize(path);
                    treeWalk.setFilter(PathFilter.create(pattern.toString()));
                    if (treeWalk.next()) {
                        ObjectLoader loader = repo.getRepository().open(treeWalk.getObjectId(0));
                        ObjectStream stream = loader.openStream();
                        InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                        return Optional.of(new BufferedReader(reader));
                    } else {
                        // Not found
                        return Optional.empty();
                    }
                }
            }
        }
        if (Files.exists(path)) {
            return Optional.of(Files.newBufferedReader(path));
        } else {
            return Optional.empty();
        }
    }

}

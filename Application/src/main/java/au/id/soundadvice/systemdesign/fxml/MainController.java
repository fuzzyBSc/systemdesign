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
package au.id.soundadvice.systemdesign.fxml;

import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.consistency.AllSuggestions;
import au.id.soundadvice.systemdesign.fxml.drawing.FXMLAllDrawings;
import au.id.soundadvice.systemdesign.fxml.tree.FXMLAllTrees;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.storage.RecordStorage;
import au.id.soundadvice.systemdesign.moduleapi.util.ISO8601;
import au.id.soundadvice.systemdesign.preferences.RecentFiles;
import au.id.soundadvice.systemdesign.storage.CSVStorage;
import au.id.soundadvice.systemdesign.storage.FileStorage;
import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * FXML Controller class
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class MainController implements Initializable {

    private static final Logger LOG = Logger.getLogger(MainController.class.getName());

    @FXML
    private Button upButton;
    @FXML
    private Button downButton;
    @FXML
    private Pane suggestions;
    @FXML
    private MenuItem newMenuItem;
    @FXML
    private MenuItem openMenuItem;
    @FXML
    private MenuItem revertMenuItem;
    @FXML
    private Menu openRecentMenu;
    @FXML
    private MenuItem exploreMenuItem;
    @FXML
    private MenuItem saveMenuItem;
    @FXML
    private MenuItem exitMenuItem;
    @FXML
    private MenuItem undoMenuItem;
    @FXML
    private MenuItem redoMenuItem;
    @FXML
    private MenuItem upMenuItem;
    @FXML
    private MenuItem downMenuItem;
    @FXML
    private Menu versionsMenu;
    @FXML
    private Menu diffBranchMenu;
    @FXML
    private Menu diffVersionMenu;
    @FXML
    private CheckMenuItem diffNoneMenuItem;
    @FXML
    private MenuItem commitMenuItem;
    @FXML
    private MenuItem aboutMenuItem;
    @FXML
    private TabPane tabs;
    @FXML
    private Accordion trees;

    private final EditState edit;

    private final SingleRunnable buttonDisable = new SingleRunnable(
            JFXExecutor.instance(), new ButtonDisable());
    private SuggestionsController suggestionsController;
    private VersionMenuController versionMenuController;
    private final Interactions interactions;
    private FXMLAllTrees treeController;
    private FXMLAllDrawings drawingController;

    public MainController(Interactions interactions, EditState edit) {
        this.edit = edit;
        this.interactions = interactions;
    }

    /**
     * Initializes the controller class.
     *
     * @param url
     * @param rb
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.treeController = new FXMLAllTrees(interactions, edit, trees);
        this.treeController.start();
        this.drawingController = new FXMLAllDrawings(interactions, edit, tabs);
        this.drawingController.start();
        suggestionsController = new SuggestionsController(
                edit, suggestions, AllSuggestions::getEditProblems);
        suggestionsController.start();

        upButton.setOnAction(event -> {
            interactions.navigateUp();
            event.consume();
        });
        downButton.setOnAction(event -> {
            interactions.navigateDown();
            event.consume();
        });

        newMenuItem.setOnAction(event -> {
            if (interactions.checkSave("Save before closing?")) {
                edit.clear();
            }
            event.consume();
        });
        openMenuItem.setOnAction(event -> {
            if (interactions.checkSave("Save before closing?")) {
                interactions.tryLoadChooser(upButton.getScene().getWindow(), edit);
            }
            event.consume();
        });
        revertMenuItem.setOnAction(event -> {
            Optional<RecordStorage> dir = edit.getStorage().getChild();
            if (dir.isPresent()) {
                if (interactions.checkSave("Save before reloading?")) {
                    try {
                        edit.load(dir.get(), ISO8601.now());
                    } catch (IOException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }
            }
            event.consume();
        });
        ContextMenus.initPerInstanceSubmenu(openRecentMenu,
                () -> RecentFiles.getRecentFiles(),
                path -> new MenuItem(path.getFileName().toString()),
                (event, path) -> {
                    if (interactions.checkSave("Save before closing?")) {
                        interactions.tryLoad(edit, CSVStorage.forPath(path));
                    }
                    event.consume();
                },
                Optional.empty());
        exploreMenuItem.setOnAction(event -> {
            Optional<RecordStorage> directory = edit.getStorage().getChild();
            if (directory.isPresent() && directory.get() instanceof FileStorage) {
                FileStorage fileStorage = (FileStorage) directory.get();
                try {
                    ProcessBuilder builder = new ProcessBuilder();
                    builder.command("nautilus", fileStorage.getPath().toString());
                    builder.directory(fileStorage.getPath().toFile());
                    builder.inheritIO();
                    builder.start();
                } catch (IOException ex) {
                    try {
                        /*
                         * I'm hitting a bug where this call hangs the whole
                         * application on Ubuntu, so we only do this as a
                         * fallback when nautilus is not available.
                         */
                        Desktop.getDesktop().open(fileStorage.getPath().toFile());
                    } catch (IOException ex2) {
                        ex2.addSuppressed(ex);
                        LOG.log(Level.WARNING, null, ex2);
                    }
                }
            } else {
                try {
                    ProcessBuilder builder = new ProcessBuilder();
                    builder.command("nautilus");
                    builder.inheritIO();
                    builder.start();
                } catch (IOException ex) {
                    try {
                        /*
                         * I'm hitting a bug where this call hangs the whole
                         * application on Ubuntu, so we only do this as a
                         * fallback when nautilus is not available.
                         */
                        Desktop.getDesktop().open(Paths.get(".").toFile());
                    } catch (IOException ex2) {
                        ex2.addSuppressed(ex);
                        LOG.log(Level.WARNING, null, ex2);
                    }
                }
            }
            event.consume();
        });
        Runnable updateExploreMenuItemSensitivity = () -> {
            exploreMenuItem.setDisable(
                    !edit.getStorage().getChild().isPresent());
        };
        edit.subscribe(updateExploreMenuItemSensitivity);
        updateExploreMenuItemSensitivity.run();

        saveMenuItem.setOnAction(event -> {
            interactions.trySave();
            event.consume();
        });
        exitMenuItem.setOnAction(event -> {
            interactions.tryExit();
            event.consume();
        });
        undoMenuItem.setOnAction(event -> {
            edit.undo();
            event.consume();
        });
        redoMenuItem.setOnAction(event -> {
            edit.redo();
            event.consume();
        });

        upMenuItem.setOnAction(event -> {
            interactions.navigateUp();
            event.consume();
        });
        downMenuItem.setOnAction(event -> {
            interactions.navigateDown();
            event.consume();
        });

        aboutMenuItem.setOnAction(event -> {
            Parent root;
            try {
                URL resource = getClass().getResource("/fxml/About.fxml");
                root = FXMLLoader.load(resource);
                Stage stage = new Stage();
                stage.setTitle("About");
                stage.setScene(new Scene(root, 450, 450));
                stage.show();
            } catch (RuntimeException | IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        });

        versionMenuController = new VersionMenuController(
                edit, diffBranchMenu, diffVersionMenu,
                versionsMenu, diffNoneMenuItem);
        versionMenuController.start();

        commitMenuItem.setOnAction(event -> {
            if (interactions.checkSave("Save before committing?")) {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Commit changes");
                dialog.setHeaderText("Enter Commit Message");

                Optional<String> interactionResult = dialog.showAndWait();
                if (interactionResult.isPresent()) {
                    try {
                        edit.getStorage().getChild().ifPresent(storage -> {
                            try {
                                storage.commit(interactionResult.get());
                            } catch (IOException ex) {
                                throw new UncheckedIOException(ex);
                            }
                        });
                    } catch (UncheckedIOException ex) {
                        LOG.log(Level.SEVERE, null, ex.getCause());
                    }
                }
            }
            event.consume();
        });
        Runnable updateCommitMenuItemSensitivity = () -> {
            commitMenuItem.setDisable(
                    !edit.getStorage().getChild().map(RecordStorage::canCommit).orElse(false));
        };
        edit.subscribe(updateCommitMenuItemSensitivity);
        updateCommitMenuItemSensitivity.run();

        edit.subscribe(buttonDisable);
        buttonDisable.run();
    }

    private class ButtonDisable implements Runnable {

        @Override
        public void run() {
            undoMenuItem.setDisable(!edit.canUndo());
            redoMenuItem.setDisable(!edit.canRedo());
            WhyHowPair<Optional<RecordStorage>> storage = edit.getStorage();
            upButton.setDisable(!storage.getParent().isPresent());
            downButton.setDisable(!edit.hasLastChild());
        }
    }

}

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

import au.id.soundadvice.systemdesign.fxml.physical.PhysicalSchematicController;
import au.id.soundadvice.systemdesign.fxml.physical.PhysicalTreeController;
import au.id.soundadvice.systemdesign.fxml.logical.LogicalTreeController;
import au.id.soundadvice.systemdesign.fxml.logical.LogicalTabs;
import au.id.soundadvice.systemdesign.state.EditState;
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.consistency.suggestions.AllSuggestions;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.model.FlowType;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.preferences.RecentFiles;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeView;
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
    private TreeView<Item> physicalTree;
    @FXML
    private TreeView<Function> logicalTree;
    @FXML
    private TreeView<FlowType> typeTree;
    @FXML
    private TreeView<BudgetSummary> budgetTree;
    @FXML
    private Button upButton;
    @FXML
    private Button downButton;
    @FXML
    private Tab physicalDrawing;
    @FXML
    private Pane suggestions;
    @FXML
    private MenuItem newMenuItem;
    @FXML
    private MenuItem openMenuItem;
    @FXML
    private Menu openRecentMenu;
    @FXML
    private MenuItem saveMenuItem;
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
    private MenuItem aboutMenuItem;
    @FXML
    private TabPane tabs;

    private final EditState edit;

    private final SingleRunnable buttonDisable = new SingleRunnable(
            JFXExecutor.instance(), new ButtonDisable());
    private PhysicalTreeController physicalTreeController;
    private LogicalTreeController logicalTreeController;
    private TypeTreeController typeTreeController;
    private BudgetTreeController budgetTreeController;
    private PhysicalSchematicController schematicController;
    private LogicalTabs logicalController;
    private SuggestionsController suggestionsController;
    private VersionMenuController versionMenuController;
    private final Interactions interactions;

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
        physicalTreeController = new PhysicalTreeController(interactions, edit, physicalTree);
        physicalTreeController.start();
        logicalTreeController = new LogicalTreeController(interactions, edit, logicalTree);
        logicalTreeController.start();
        typeTreeController = new TypeTreeController(interactions, edit, typeTree);
        typeTreeController.start();
        budgetTreeController = new BudgetTreeController(interactions, edit, budgetTree);
        budgetTreeController.start();
        schematicController = new PhysicalSchematicController(interactions, edit, physicalDrawing);
        schematicController.start();
        logicalController = new LogicalTabs(interactions, edit, tabs);
        logicalController.start();
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
        ContextMenus.initPerInstanceSubmenu(
                openRecentMenu,
                () -> RecentFiles.getRecentFiles(),
                path -> new MenuItem(path.getFileName().toString()),
                (event, path) -> {
                    if (interactions.checkSave("Save before closing?")) {
                        interactions.tryLoad(edit, Directory.forPath(path));
                    }
                    event.consume();
                },
                Optional.empty());
        saveMenuItem.setOnAction(event -> {
            interactions.trySave();
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

        edit.subscribe(buttonDisable);
        buttonDisable.run();
    }

    private class ButtonDisable implements Runnable {

        @Override
        public void run() {
            undoMenuItem.setDisable(!edit.canUndo());
            redoMenuItem.setDisable(!edit.canRedo());
            Optional<Directory> dir = edit.getCurrentDirectory();
            upButton.setDisable(
                    !dir.isPresent()
                    || !dir.get().getParent().getIdentity().isPresent());
            downButton.setDisable(!edit.hasLastChild());
        }
    }

}

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

import au.id.soundadvice.systemdesign.baselines.EditState;
import au.id.soundadvice.systemdesign.concurrent.JFXExecutor;
import au.id.soundadvice.systemdesign.concurrent.SingleRunnable;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.model.Item;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TreeView;
import javafx.scene.layout.AnchorPane;

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
    private TreeView logicalTree;
    @FXML
    private Button upButton;
    @FXML
    private Button downButton;
    @FXML
    private AnchorPane physicalDrawing;
    @FXML
    private AnchorPane logicalDrawing;

    private PhysicalTreeController physicalTreeController;
    private PhysicalSchematicController schematicController;
    private final EditState edit;
    private final SingleRunnable buttonDisable = new SingleRunnable(
            JFXExecutor.instance(), new ButtonDisable());

    public MainController(EditState edit) {
        this.edit = edit;
    }

    /**
     * Initializes the controller class.
     *
     * @param url
     * @param rb
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        physicalTreeController = new PhysicalTreeController(edit, physicalTree);
        physicalTreeController.start();
        schematicController = new PhysicalSchematicController(edit, physicalDrawing);
        schematicController.start();
        LOG.info(physicalTree.toString());
        LOG.info(logicalTree.toString());

        upButton.setOnAction((ActionEvent) -> {
            try {
                edit.save();
                edit.loadParent();
            } catch (IOException ex) {
                Logger.getLogger(MainController.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        downButton.setOnAction((ActionEvent) -> {
            try {
                edit.save();
                edit.loadLastChild();
            } catch (IOException ex) {
                Logger.getLogger(MainController.class.getName()).log(Level.SEVERE, null, ex);
            }
        });

        edit.subscribe(buttonDisable);
        buttonDisable.run();
    }

    private class ButtonDisable implements Runnable {

        @Override
        public void run() {
            Directory dir = edit.getCurrentDirectory();
            upButton.setDisable(dir == null || !dir.getParent().hasIdentity());
            downButton.setDisable(!edit.hasLastChild());
        }
    }

}

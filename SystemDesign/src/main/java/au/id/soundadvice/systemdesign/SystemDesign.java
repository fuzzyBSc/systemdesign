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
package au.id.soundadvice.systemdesign;

import au.id.soundadvice.systemdesign.baselines.EditState;
import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.fxml.MainController;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import javafx.application.Application;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Callback;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class SystemDesign extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        final EditState editState = EditState.init(Executors.newCachedThreadPool());
        editState.load(new Directory(Paths.get("/tmp/repo/model/system/subsystem")));

        Callback<Class<?>, Object> controllerFactory = new Callback<Class<?>, Object>() {
            @Override
            public Object call(Class<?> param) {
                if (param.equals(MainController.class)) {
                    return new MainController(editState);
                } else {
                    try {
                        return param.newInstance();
                    } catch (InstantiationException | IllegalAccessException ex) {
                        throw new AssertionError(ex);
                    }
                }
            }
        };

        Parent root = FXMLLoader.load(
                getClass().getResource("/fxml/Main.fxml"),
                null, null, controllerFactory);

        stage.setOnCloseRequest(new EventHandler() {

            @Override
            public void handle(Event event) {
                System.exit(0);
            }
        });

        Scene scene = new Scene(root);
        stage.setTitle("System Design");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

}

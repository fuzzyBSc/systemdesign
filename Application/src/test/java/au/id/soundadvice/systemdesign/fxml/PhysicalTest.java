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

import au.id.soundadvice.systemdesign.SystemDesign;
import java.io.IOException;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import static org.testfx.util.NodeQueryUtils.isVisible;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isNull;

/**
 *
 * @author fuzzy
 */
@ExtendWith(ApplicationExtension.class)
public class PhysicalTest {

    @BeforeEach
    public void awaitRunning(FxRobot robot) throws InterruptedException {
        for (;;) {
            Set<Node> drawingAreas = robot.lookup(".drawingArea").queryAll();
            if (drawingAreas.isEmpty()) {
                Thread.sleep(100);
            } else {
                break;
            }
        }
    }

    public static Predicate<Node> hasText(String text) {
        return node -> (node instanceof Text && text.equals(((Text) node).getText()));
    }

    @Test
    public void itCreatesItemsAndInterfaces(FxRobot robot) throws InterruptedException {
        Supplier<Node> topItem = () -> robot.lookup(hasText("1 Top Item")).query();
        Supplier<Node> bottomItem = () -> robot.lookup(hasText("2 Bottom Item")).query();
        Supplier<Node> iface = () -> robot.lookup(hasText("1:2")).query();

        Node systemContext = robot.lookup(".drawingArea.PhysicalSchematic").query();
        robot.rightClickOn(systemContext).clickOn("New Item...");
        robot.write("Top Item").clickOn("OK");
        robot.drag(topItem.get(), MouseButton.PRIMARY).dropBy(-50, -100);

        robot.rightClickOn(systemContext).clickOn("New Item...");
        robot.write("Bottom Item").clickOn("OK");
        robot.drag(bottomItem.get(), MouseButton.PRIMARY).dropBy(50, 100);

        // Create interface
        robot.press(KeyCode.CONTROL);
        try {
            robot.drag(topItem.get(), MouseButton.PRIMARY).dropTo(bottomItem.get());
        } finally {
            robot.release(KeyCode.CONTROL);
        }
        verifyThat(iface.get(), isVisible());

        // Clean up
        robot.rightClickOn(topItem.get()).clickOn("Delete Item");
        verifyThat(iface.get(), isNull());
        robot.rightClickOn(bottomItem.get()).clickOn("Delete Item");
    }

    @Start
    public void start(Stage stage) throws IOException {
        SystemDesign.startMe(stage);
        stage.setMaximized(true);
    }

}

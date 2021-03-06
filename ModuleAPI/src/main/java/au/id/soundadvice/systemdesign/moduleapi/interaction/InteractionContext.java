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
package au.id.soundadvice.systemdesign.moduleapi.interaction;

import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import java.util.Optional;
import java.util.function.UnaryOperator;
import javafx.scene.paint.Color;

/**
 *
 * @author fuzzy
 */
public interface InteractionContext {

    public Optional<String> textInput(String action, String question, String _default);

    public Optional<Color> colorInput(String item_Color, String select_color_for_item, Color color);

    public void updateState(UnaryOperator<WhyHowPair<Baseline>> mutator);

    public void updateParent(UnaryOperator<Baseline> mutator);

    public void updateChild(UnaryOperator<Baseline> mutator);

    public WhyHowPair<Baseline> getState();

    public Baseline getParent();

    public Baseline getChild();

    public Optional<Baseline> getWas();

    public void navigateDown(Record item, Optional<Record> preferredTab);

    public void restoreDeleted(Record sample);

}

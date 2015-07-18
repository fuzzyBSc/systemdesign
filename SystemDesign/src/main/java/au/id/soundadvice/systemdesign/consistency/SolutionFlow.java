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
package au.id.soundadvice.systemdesign.consistency;

import au.id.soundadvice.systemdesign.model.Baseline;
import au.id.soundadvice.systemdesign.model.UndoState;
import java.util.function.UnaryOperator;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public enum SolutionFlow {

    Up("Flow up"), Down("Flow down");

    public String getDescription() {
        return description;
    }

    public Baseline getSource(UndoState state) {
        switch (this) {
            case Up:
                return state.getAllocated();
            case Down:
                return state.getFunctional();
            default:
                throw new AssertionError(this.name());

        }
    }

    public Baseline getTarget(UndoState state) {
        switch (this) {
            case Up:
                return state.getFunctional();
            case Down:
                return state.getAllocated();
            default:
                throw new AssertionError(this.name());

        }
    }

    @CheckReturnValue
    public UndoState updateTarget(UndoState state, UnaryOperator<Baseline> update) {
        switch (this) {
            case Up:
                return state.updateFunctional(update);
            case Down:
                return state.updateAllocated(update);
            default:
                throw new AssertionError(this.name());

        }
    }

    private SolutionFlow(String description) {
        this.description = description;
    }
    private final String description;
}

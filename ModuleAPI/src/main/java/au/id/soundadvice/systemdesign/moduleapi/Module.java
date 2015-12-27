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
package au.id.soundadvice.systemdesign.moduleapi;

import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import java.util.stream.Stream;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public interface Module {

    /**
     * Perform any necessary setup steps, such as connecting to other modules.
     */
    public void init();

    /**
     * Perform all automated consistency repair activities.
     *
     * @param state The state before fixing
     * @return The state after fixing
     */
    public UndoState onLoadAutoFix(UndoState state);

    /**
     * Perform quick automated consistency repair activities that can't
     * reasonably be kept consistent everywhere in the code that might break
     * this consistency.
     *
     * @param state The state before fixing
     * @return The state after fixing
     */
    public UndoState onChangeAutoFix(UndoState state);

    /**
     * Save all mementos for relations owned by this module from the Relations
     * set.
     *
     * @param context The Relations set to search for relations owned by this
     * module.
     * @return A stream of beans equivalent to the relations in a form suitable
     * for long term storage and version control.
     */
    public Stream<Identifiable> saveMementos(Relations context);

    /**
     * Return a stream of all memento types that can be returned from
     * saveMementos and restored into restoreMementos.
     *
     * @return
     */
    public Stream<Class<? extends Identifiable>> getMementoTypes();

    /**
     * Transform a stream of memento beans into relations.
     *
     * @param beans A stream of memento beans to restore
     * @return The relations corresponding to these beans
     */
    public Stream<Relation> restoreMementos(Stream<Identifiable> beans);

    /**
     * Return a stream of problems and related suggestions for the current
     * state.
     *
     * @param state The state before fixing
     * @return A stream of identified problems, including related proposed
     * solutions.
     */
    public Stream<Problem> getProblems(UndoState state);
}

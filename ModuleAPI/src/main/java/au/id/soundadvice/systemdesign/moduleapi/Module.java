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

import au.id.soundadvice.systemdesign.moduleapi.collection.BaselinePair;
import java.util.stream.Stream;
import au.id.soundadvice.systemdesign.moduleapi.drawing.Drawing;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.DiffPair;
import au.id.soundadvice.systemdesign.moduleapi.tree.Tree;
import au.id.soundadvice.systemdesign.moduleapi.entity.Table;

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
     * @param baselines The state before fixing
     * @return The state after fixing
     */
    public BaselinePair onLoadAutoFix(BaselinePair baselines, String now);

    /**
     * Perform quick automated consistency repair activities that can't
     * reasonably be kept consistent everywhere in the code that might break
     * this consistency.
     *
     * @param baselines The state before fixing
     * @return The state after fixing
     */
    public BaselinePair onChangeAutoFix(BaselinePair baselines, String now);

    /**
     * Return a stream of all record types that are owned by this module.
     *
     * @return
     */
    public Stream<Table> getTables();

    /**
     * Return the drawings for this module within the nominated baseline.
     *
     * @param baselines The baseline pair to extract drawings from
     * @return All drawings for this module from the nominated baseline pair
     */
    public Stream<Drawing> getDrawings(DiffPair<Baseline> baselines);

    /**
     * Return the drawings for this module within the nominated baseline.
     *
     * @param baselines The baseline pair to extract trees from
     * @return All trees for this module from the nominated baseline pair
     */
    public Stream<Tree> getTrees(BaselinePair baselines);
}

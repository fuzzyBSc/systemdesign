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

import au.id.soundadvice.systemdesign.moduleapi.Module;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.preferences.Modules;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class AutoFix {

    private static final List<BiFunction<WhyHowPair<Baseline>, String, WhyHowPair<Baseline>>> OTHER_ON_LOAD = new CopyOnWriteArrayList<>();

    public static WhyHowPair<Baseline> onLoad(WhyHowPair<Baseline> state, String now) {
        Iterator<Module> it = Modules.getModules().iterator();
        while (it.hasNext()) {
            state = it.next().onLoadAutoFix(state, now);
        }
        for (BiFunction<WhyHowPair<Baseline>, String, WhyHowPair<Baseline>> otherOnLoad : OTHER_ON_LOAD) {
            state = otherOnLoad.apply(state, now);
        }
        return state;
    }

    public static WhyHowPair<Baseline> onChange(WhyHowPair<Baseline> state, String now) {
        Iterator<Module> it = Modules.getModules().iterator();
        while (it.hasNext()) {
            state = it.next().onChangeAutoFix(state, now);
        }
        return state;
    }

    public static void addOnLoad(BiFunction<WhyHowPair<Baseline>, String, WhyHowPair<Baseline>> onLoad) {
        OTHER_ON_LOAD.add(onLoad);
    }
}

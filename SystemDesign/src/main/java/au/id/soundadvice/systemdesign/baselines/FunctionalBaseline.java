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
package au.id.soundadvice.systemdesign.baselines;

import au.id.soundadvice.systemdesign.files.Directory;
import au.id.soundadvice.systemdesign.files.SaveTransaction;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.relation.RelationStore;
import java.io.IOException;
import javax.annotation.CheckReturnValue;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class FunctionalBaseline {

    public Item getSystemOfInterest() {
        return systemOfInterest;
    }

    public AllocatedBaseline getContext() {
        return context;
    }

    public FunctionalBaseline(Item systemOfInterest, AllocatedBaseline context) {
        this.systemOfInterest = systemOfInterest;
        this.context = context;

//        Map<String, Function> systemFunctions = new HashMap<>();
    }
    private final Item systemOfInterest;
    private final AllocatedBaseline context;

    void saveTo(SaveTransaction transaction, Directory directory) throws IOException {
        context.saveTo(transaction, directory);
    }

    public RelationStore getStore() {
        return context.getStore();
    }

    @CheckReturnValue
    public FunctionalBaseline setContext(AllocatedBaseline value) {
        return new FunctionalBaseline(systemOfInterest, value);
    }
}

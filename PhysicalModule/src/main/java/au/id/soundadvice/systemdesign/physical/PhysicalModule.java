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
package au.id.soundadvice.systemdesign.physical;

import au.id.soundadvice.systemdesign.moduleapi.Identifiable;
import au.id.soundadvice.systemdesign.moduleapi.Module;
import au.id.soundadvice.systemdesign.moduleapi.UndoState;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import java.util.stream.Stream;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import au.id.soundadvice.systemdesign.physical.beans.IdentityBean;
import au.id.soundadvice.systemdesign.physical.beans.InterfaceBean;
import au.id.soundadvice.systemdesign.physical.beans.ItemBean;
import au.id.soundadvice.systemdesign.physical.beans.ItemViewBean;
import au.id.soundadvice.systemdesign.physical.fix.ExternalColorAutoFix;
import au.id.soundadvice.systemdesign.physical.fix.IdentityMismatchAutoFix;
import au.id.soundadvice.systemdesign.physical.fix.ItemViewAutoFix;
import au.id.soundadvice.systemdesign.physical.suggest.InterfaceConsistency;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class PhysicalModule implements Module {

    @Override
    public void init() {
    }

    @Override
    public Stream<Identifiable> saveMementos(Relations baseline) {
        Stream<Identifiable> result;
        result = baseline.findByClass(Identity.class).map(Identity::toBean);
        result = Stream.concat(result,
                baseline.findByClass(Item.class).map(Item::toBean));
        result = Stream.concat(result,
                baseline.findByClass(ItemView.class).map(view -> view.toBean(baseline)));
        result = Stream.concat(result,
                baseline.findByClass(Interface.class).map(iface -> iface.toBean(baseline)));
        return result;
    }

    @Override
    public UndoState onLoadAutoFix(UndoState state) {
        state = ExternalColorAutoFix.fix(state);
        state = IdentityMismatchAutoFix.fix(state);
        state = ItemViewAutoFix.fix(state);
        return state;
    }

    @Override
    public UndoState onChangeAutoFix(UndoState state) {
        // Do nothing
        return state;
    }

    @Override
    public Stream<Problem> getProblems(UndoState state) {
        return InterfaceConsistency.getProblems(state);
    }

    @Override
    public Stream<Class<? extends Identifiable>> getMementoTypes() {
        return Stream.of(
                IdentityBean.class,
                ItemBean.class,
                ItemViewBean.class,
                InterfaceBean.class);
    }

    @Override
    public Stream<Relation> restoreMementos(Stream<Identifiable> beans) {
        return beans.
                map(bean -> {
                    if (IdentityBean.class.equals(bean.getClass())) {
                        return new Identity((IdentityBean) bean);
                    } else if (ItemBean.class.equals(bean.getClass())) {
                        return new Item((ItemBean) bean);
                    } else if (ItemViewBean.class.equals(bean.getClass())) {
                        return new ItemView((ItemViewBean) bean);
                    } else if (InterfaceBean.class.equals(bean.getClass())) {
                        return new Interface((InterfaceBean) bean);
                    } else {
                        throw new UncheckedIOException(
                                new IOException(bean.getClass().getName()));
                    }
                });
    }

}

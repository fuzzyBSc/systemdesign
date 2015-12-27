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
package au.id.soundadvice.systemdesign.logical;

import au.id.soundadvice.systemdesign.logical.beans.FlowBean;
import au.id.soundadvice.systemdesign.logical.beans.FlowTypeBean;
import au.id.soundadvice.systemdesign.logical.beans.FunctionBean;
import au.id.soundadvice.systemdesign.logical.beans.FunctionViewBean;
import au.id.soundadvice.systemdesign.logical.fix.FlowAutoFix;
import au.id.soundadvice.systemdesign.logical.fix.FlowTypeAutoFix;
import au.id.soundadvice.systemdesign.logical.fix.FunctionViewAutoFix;
import au.id.soundadvice.systemdesign.logical.suggest.ExternalFunctionConsistency;
import au.id.soundadvice.systemdesign.logical.suggest.FlowConsistency;
import au.id.soundadvice.systemdesign.logical.suggest.FlowTypeConsistency;
import au.id.soundadvice.systemdesign.logical.suggest.UntracedFunctions;
import au.id.soundadvice.systemdesign.moduleapi.Identifiable;
import au.id.soundadvice.systemdesign.moduleapi.Module;
import au.id.soundadvice.systemdesign.moduleapi.UndoState;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relation;
import au.id.soundadvice.systemdesign.moduleapi.relation.Relations;
import au.id.soundadvice.systemdesign.moduleapi.suggest.Problem;
import au.id.soundadvice.systemdesign.physical.Identity;
import au.id.soundadvice.systemdesign.physical.Interface;
import au.id.soundadvice.systemdesign.physical.Item;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class LogicalModule implements Module {

    @Override
    public void init() {
        Item.addFlowDownAction((state, item) -> {
            Relations functional = state.getFunctional();
            Optional<Item> system = Identity.getSystemOfInterest(state);
            if (!system.isPresent()) {
                return state;
            }
            Optional<Interface> iface
                    = Interface.find(functional, system.get())
                    .filter(candidate -> item.getUuid().equals(candidate.otherEnd(functional, system.get()).getUuid()))
                    .findAny();
            if (!iface.isPresent()) {
                return state;
            }
            Iterator<Function> functionsWithFlowsOnThisInterface
                    = Function.findOwnedFunctions(functional, item)
                    .filter(ExternalFunctionConsistency.hasFlowsOnInterface(functional, iface.get()))
                    .iterator();
            while (functionsWithFlowsOnThisInterface.hasNext()) {
                Function function = functionsWithFlowsOnThisInterface.next();
                state = Function.flowDownExternal(state, function).getKey();
            }
            return state;
        });
    }

    @Override
    public Stream<Identifiable> saveMementos(Relations baseline) {
        Stream<Identifiable> result;
        result = baseline.findByClass(Function.class).map(function -> function.toBean(baseline));
        result = Stream.concat(result,
                baseline.findByClass(FunctionView.class).map(view -> view.toBean(baseline)));
        result = Stream.concat(result,
                baseline.findByClass(FlowType.class).map(FlowType::toBean));
        result = Stream.concat(result,
                baseline.findByClass(Flow.class).map(flow -> flow.toBean(baseline)));
        return result;
    }

    @Override
    public UndoState onLoadAutoFix(UndoState state) {
        state = FlowAutoFix.fix(state);
        state = FlowTypeAutoFix.fix(state);
        state = FunctionViewAutoFix.fix(state);
        return state;
    }

    @Override
    public UndoState onChangeAutoFix(UndoState state) {
        state = FunctionViewAutoFix.fix(state);
        return state;
    }

    @Override
    public Stream<Problem> getProblems(UndoState state) {
        Stream<java.util.function.Function<UndoState, Stream<Problem>>> operators
                = Stream.of(
                        ExternalFunctionConsistency::getProblems,
                        FlowConsistency::getProblems,
                        FlowTypeConsistency::getProblems,
                        UntracedFunctions::getProblems);
        return operators.flatMap(f -> f.apply(state));
    }

    @Override
    public Stream<Class<? extends Identifiable>> getMementoTypes() {
        return Stream.of(
                FunctionBean.class,
                FunctionViewBean.class,
                FlowTypeBean.class,
                FlowBean.class);
    }

    @Override
    public Stream<Relation> restoreMementos(Stream<Identifiable> beans) {
        return beans.
                flatMap(bean -> {
                    if (FunctionBean.class.equals(bean.getClass())) {
                        return Stream.of(new Function((FunctionBean) bean));
                    } else if (FunctionViewBean.class.equals(bean.getClass())) {
                        return Stream.of(new FunctionView((FunctionViewBean) bean));
                    } else if (FlowTypeBean.class.equals(bean.getClass())) {
                        return Stream.of(new FlowType((FlowTypeBean) bean));
                    } else if (FlowBean.class.equals(bean.getClass())) {
                        FlowBean flowBean = (FlowBean) bean;
                        if (flowBean.getTypeUUID() == null) {
                            // Legacy bean: v0.2 support
                            // Autofix will remove any duplicates we create
                            FlowType flowType = FlowType.create(
                                    Optional.empty(), flowBean.getType());
                            flowBean.setType(flowType.getUuid().toString());
                            return Stream.of(
                                    flowType,
                                    new Flow(flowBean));
                        } else {
                            return Stream.of(new Flow(flowBean));
                        }
                    } else {
                        throw new UncheckedIOException(
                                new IOException(bean.getClass().getName()));
                    }
                });
    }

}

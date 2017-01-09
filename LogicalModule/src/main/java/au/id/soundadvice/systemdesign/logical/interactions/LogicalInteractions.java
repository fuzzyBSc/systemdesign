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
package au.id.soundadvice.systemdesign.logical.interactions;

import au.id.soundadvice.systemdesign.logical.entity.Flow;
import au.id.soundadvice.systemdesign.logical.entity.FlowType;
import au.id.soundadvice.systemdesign.logical.entity.Function;
import au.id.soundadvice.systemdesign.moduleapi.util.UniqueName;
import au.id.soundadvice.systemdesign.moduleapi.entity.Direction;
import au.id.soundadvice.systemdesign.moduleapi.collection.RecordConnectionScope;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.interaction.InteractionContext;
import au.id.soundadvice.systemdesign.moduleapi.util.ISO8601;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javafx.geometry.Point2D;
import javafx.util.Pair;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class LogicalInteractions {

    public Optional<Record> addFunctionToItem(
            InteractionContext context, Record item, Optional<Record> traceFunction, Point2D origin) {
        AtomicReference<Record> result = new AtomicReference<>();
        String defaultName = Function.find(context.getChild()).parallel()
                .filter(func -> !func.isExternal())
                .map(Record::getLongName)
                .collect(new UniqueName("New Function"));
        Optional<String> name = context.textInput("New Function", "Enter name for function", defaultName);
        if (name.isPresent()) {
            String now = ISO8601.now();
            context.updateState(state -> {
                Pair<WhyHowPair<Baseline>, Record> createResult = Function.create(
                        state, now, item, traceFunction, name.get());
                result.set(createResult.getValue());
                return state.setChild(createResult.getKey().getChild());
            });
        }
        return Optional.ofNullable(result.get());
    }

    public void renameFunction(InteractionContext context, Record sample) {
        if (sample.isExternal()) {
            return;
        }

        Optional<String> result = context.textInput("Rename Function", "Enter name for function", sample.getLongName());
        if (result.isPresent()) {
            String now = ISO8601.now();
            String name = result.get();
            context.updateChild(child -> {
                Optional<Record> func = child.get(sample);
                if (!func.isPresent()) {
                    return child;
                }
                boolean isUnique = Function.find(child).parallel()
                        .map(Record::getLongName)
                        .noneMatch((existing) -> name.equals(existing));
                if (isUnique) {
                    return child.add(func.get().asBuilder()
                            .setLongName(name)
                            .build(now));
                } else {
                    return child;
                }
            });
        }
    }

    public Optional<Record> addFlowWithGuessedType(InteractionContext context, Record sourceSample, Record targetSample) {
        AtomicReference<Record> result = new AtomicReference<>();
        if (!sourceSample.isExternal() || !targetSample.isExternal()) {
            // One end has to be internal
            String now = ISO8601.now();
            context.updateState(state -> {
                RecordConnectionScope scope = RecordConnectionScope.resolve(
                        sourceSample, targetSample, Direction.Forward);
                Pair<WhyHowPair<Baseline>, Record> createResult = Flow.addWithGuessedType(state, now, scope);
                result.set(createResult.getValue());
                return createResult.getKey();
            });
        }
        return Optional.ofNullable(result.get());
    }

    public void setFlowType(InteractionContext context, Record flowSample) {
        if (flowSample.isExternal()) {
            return;
        }

        Baseline sampleChild = context.getChild();
        String sampleFlowName = flowSample.getSubtype()
                .flatMap(id -> sampleChild.get(id, FlowType.flowType))
                .map(Record::getLongName)
                .orElse("New Flow");
        Optional<String> result = context.textInput("Flow Type", "Enter new type for " + flowSample.getLongName(), sampleFlowName);
        if (result.isPresent()) {
            String now = ISO8601.now();
            String name = result.get();
            context.updateState(state -> {
                Baseline child = state.getChild();
                Optional<Record> flow = child.get(flowSample);
                if (!flow.isPresent()) {
                    return state;
                }
                Optional<Record> flowType = FlowType.get(child, name);
                if (flowType.isPresent()) {
                    return state.setChild(
                            child.add(
                                    flow.get().asBuilder()
                                    .setSubtype(flowType.get())
                                    .build(now)));
                } else {
                    // Define type and point to it simultaneously
                    Pair<WhyHowPair<Baseline>, Record> newFlowType = FlowType.define(state, now, name);
                    return newFlowType.getKey().setChild(
                            newFlowType.getKey().getChild().add(
                                    flow.get().asBuilder()
                                    .setSubtype(newFlowType.getValue())
                                    .build(now)));
                }
            }
            );
        }
    }
}

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
package au.id.soundadvice.systemdesign.budget.interactions;

import au.id.soundadvice.systemdesign.budget.entity.Budget;
import au.id.soundadvice.systemdesign.moduleapi.util.UniqueName;
import au.id.soundadvice.systemdesign.moduleapi.collection.Baseline;
import au.id.soundadvice.systemdesign.moduleapi.collection.WhyHowPair;
import au.id.soundadvice.systemdesign.moduleapi.entity.Record;
import au.id.soundadvice.systemdesign.moduleapi.interaction.InteractionContext;
import au.id.soundadvice.systemdesign.moduleapi.util.ISO8601;
import au.id.soundadvice.systemdesign.physical.entity.Item;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javafx.util.Pair;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class BudgetInteractions {

    public Optional<Record> createBudget(InteractionContext context) {
        AtomicReference<Record> result = new AtomicReference<>();
        String defaultName = Item.find(context.getChild()).parallel()
                .filter(item -> !item.isExternal())
                .map(Record::getLongName)
                .collect(new UniqueName("New Budget"));
        Optional<String> name = context.textInput("New Budget", "Enter name for budget", defaultName);
        if (!name.isPresent()) {
            return Optional.empty();
        }
        Optional<String> unit = context.textInput("Budget Units", "Enter units for " + name.get(), defaultName);
        if (unit.isPresent()) {
            String now = ISO8601.now();
            context.updateState(state -> {
                Budget.Key key = new Budget.Key(name.get(), unit.get());
                Pair<WhyHowPair<Baseline>, Record> createResult = Budget.add(state, now, key);
                result.set(createResult.getValue());
                return state.setChild(createResult.getKey().getChild());
            });
        }
        return Optional.ofNullable(result.get());
    }

    public void renameBudget(InteractionContext context, Record sample) {
        Budget.Key sampleKey = Budget.budget.getKey(sample);
        Optional<String> result = context.textInput("Rename Budget", "Enter name for budget", sampleKey.getName());
        if (result.isPresent()) {
            String now = ISO8601.now();
            String name = result.get();
            context.updateChild(child -> {
                Optional<Record> budget = child.get(sample);
                if (!budget.isPresent()) {
                    return child;
                }
                Budget.Key newKey = Budget.budget.getKey(budget.get());
                newKey = new Budget.Key(name, newKey.getUnit());
                return Budget.budget.setKey(child, now, budget.get(), newKey).getKey();
            });
        }
    }

    public void setBudgetUnit(InteractionContext context, Record sample) {
        Budget.Key sampleKey = Budget.budget.getKey(sample);
        Optional<String> result = context.textInput(
                "Set Unit", "Enter unit for " + sampleKey.getName(), sampleKey.getUnit());
        if (result.isPresent()) {
            String now = ISO8601.now();
            String unit = result.get();
            context.updateChild(child -> {
                Optional<Record> budget = child.get(sample);
                if (!budget.isPresent()) {
                    return child;
                }
                Budget.Key newKey = Budget.budget.getKey(budget.get());
                newKey = new Budget.Key(newKey.getName(), unit);
                return Budget.budget.setKey(child, now, budget.get(), newKey).getKey();
            });
        }
    }
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.input;

import au.id.soundadvice.systemdesign.hierarchy.BreakdownAddress;
import au.id.soundadvice.systemdesign.hierarchy.InvalidHierarchyException;
import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.FlowNode;
import au.id.soundadvice.systemdesign.model.FlowType;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.InterfaceKey;
import au.id.soundadvice.systemdesign.model.Item;
import au.id.soundadvice.systemdesign.model.Model;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author fuzzy
 */
public class Load {

	public static Model load(InputModel input) throws IOException, InvalidHierarchyException {
		// First load all items across the board
		Map<BreakdownAddress, Item> allItems = new HashMap<>();
		Map<BreakdownAddress, Function> allFunctions = new HashMap<>();
		Map<BreakdownAddress, FlowType> allTypes = new HashMap<>();
		Map<InterfaceKey, Interface> allInterfaces = new HashMap<>();
		Map<InputFlow, Flow> allFlows = new HashMap<>();
		loadItems(allItems, input);
		loadFunctions(allItems, allFunctions, input);
		loadTypes(allTypes, input);
		loadFlows(allItems, allFunctions, allTypes, allInterfaces, allFlows, input);
		return new Model(
				allItems.get(BreakdownAddress.getContext()),
				allFunctions.get(BreakdownAddress.getContext()),
				allTypes.get(BreakdownAddress.getContext()));
	}

	private static Item loadItem(
			Map<BreakdownAddress, Item> allItems,
			BreakdownAddress address,
			InputModel input) throws InvalidHierarchyException {
		Item result = allItems.get(address);
		if (result == null) {
			if (address.isContext()) {
				result = new Item();
			} else {
				BreakdownAddress parentAddress = address.getParent();
				Item parent = loadItem(allItems, parentAddress, input);

				InputItem inputItem = input.getItems().get(address);
				String name;
				if (inputItem == null) {
					/*
					 * This node was missing in the input, so fill it in with
					 * the parent's name
					 */
					name = parent.getName();
				} else {
					name = inputItem.getToken().getName();
				}

				result = new Item(parent, address, name);
				result.bind();
			}
			allItems.put(address, result);
		}
		return result;
	}

	private static void loadItems(
			Map<BreakdownAddress, Item> allItems,
			InputModel input) throws IOException, InvalidHierarchyException {
		for (BreakdownAddress itemAddress : input.getItems().keySet()) {
			loadItem(allItems, itemAddress, input);
		}
	}

	private static Function loadFunction(
			Map<BreakdownAddress, Item> allItems,
			Map<BreakdownAddress, Function> allFunctions,
			BreakdownAddress address,
			InputModel input,
			BreakdownAddress itemAddress) throws InvalidHierarchyException {
		Function result = allFunctions.get(address);
		if (result == null) {
			if (address.isContext()) {
				result = new Function();
			} else {
				BreakdownAddress parentAddress = address.getParent();
				Function parent = loadFunction(
						allItems, allFunctions, parentAddress, input, itemAddress.getParent());

				InputFunction inputFunction = input.getFunctions().get(address);
				String name;
				if (inputFunction == null) {
					/*
					 * This node was missing in the input, so fill it in with
					 * the parent's name.
					 */
					name = parent.getName();
				} else {
					itemAddress = inputFunction.getItem().getToken().getAddress();
					name = inputFunction.getToken().getName();
				}

				Item item = allItems.get(itemAddress);

				result = new Function(parent, item, address, name);
				result.bind();
			}
			allFunctions.put(address, result);
		}
		return result;
	}

	private static void loadFunctions(
			Map<BreakdownAddress, Item> allItems,
			Map<BreakdownAddress, Function> allFunctions,
			InputModel input) throws IOException, InvalidHierarchyException {
		for (BreakdownAddress functionAddress : input.getFunctions().keySet()) {
			loadFunction(
					allItems, allFunctions,
					functionAddress,
					input,
					input.getFunctions().get(functionAddress).getItem().getToken().getAddress());
		}
	}

	private static FlowType loadType(
			Map<BreakdownAddress, FlowType> allTypes,
			BreakdownAddress address,
			InputModel input) throws InvalidHierarchyException {
		FlowType result = allTypes.get(address);
		if (result == null) {
			if (address.isContext()) {
				result = new FlowType();
			} else {
				BreakdownAddress parentAddress = address.getParent();
				FlowType parent = loadType(allTypes, parentAddress, input);

				String name = input.getTypes().get(address);
				if (name == null) {
					/*
					 * This type was missing in the input, so fill it in with
					 * the parent's name
					 */
					name = parent.getName();
				}

				result = new FlowType(parent, address, name);
				result.bind();
			}
			allTypes.put(address, result);
		}
		return result;
	}

	private static void loadTypes(
			Map<BreakdownAddress, FlowType> allTypes,
			InputModel input) throws IOException, InvalidHierarchyException {
		for (BreakdownAddress typeAddress : input.getTypes().keySet()) {
			loadType(allTypes, typeAddress, input);
		}
	}

	private static Interface loadInterface(
			Map<BreakdownAddress, Item> allItems,
			Map<InterfaceKey, Interface> allInterfaces,
			Item sourceItem, Item targetItem, InputModel input) throws InvalidHierarchyException {
		InterfaceKey key = new InterfaceKey(sourceItem.getAddress(), targetItem.getAddress());
		Interface result = allInterfaces.get(key);
		if (result == null) {
			Interface parent = null;
			if (parent == null && sourceItem.getTrace() != null) {
				InterfaceKey candidateKey = new InterfaceKey(
						sourceItem.getTrace().getAddress(),
						targetItem.getAddress());
				if (input.getInterfaces().contains(candidateKey)) {
					parent = loadInterface(allItems, allInterfaces,
							sourceItem.getTrace(), targetItem, input);
				}
			}
			if (parent == null && targetItem.getTrace() != null) {
				InterfaceKey candidateKey = new InterfaceKey(
						sourceItem.getAddress(),
						targetItem.getTrace().getAddress());
				if (input.getInterfaces().contains(candidateKey)) {
					parent = loadInterface(allItems, allInterfaces,
							sourceItem, targetItem.getTrace(), input);
				}
			}
			if (parent == null && sourceItem.getTrace() != null && targetItem.getTrace() != null) {
				if (sourceItem.getTrace() != targetItem.getTrace()) {
					/*
					 * Force the creation of a new parent interface between the
					 * two item traces
					 */
					parent = loadInterface(allItems, allInterfaces,
							sourceItem.getTrace(), targetItem.getTrace(), input);
				}
			}
			if (parent == null) {
				/*
				 * We weren't able, either because we ran out of item traces
				 * (one or the other is null) or because the item traces traced
				 * to the same parent item.
				 */
				if (sourceItem.getTrace() == null || targetItem.getTrace() == null) {
					// Place this interface directly undert the context item
					Item context = allItems.get(BreakdownAddress.getContext());
					result = new Interface(context, sourceItem, targetItem);
				} else {
					result = new Interface(sourceItem.getTrace(), sourceItem, targetItem);
				}
			} else {
				// This is a sub-interface
				result = new Interface(parent, sourceItem, targetItem);
			}
			result.bind();
			allInterfaces.put(key, result);
		}
		return result;
	}

	private static Flow loadFlow(
			Map<BreakdownAddress, Item> allItems,
			Map<BreakdownAddress, Function> allFunctions,
			Map<BreakdownAddress, FlowType> allTypes,
			Map<InterfaceKey, Interface> allInterfaces,
			Map<InputFlow, Flow> allFlows,
			InputFlow inputFlow,
			InputModel input) throws InvalidHierarchyException {
		Flow result = allFlows.get(inputFlow);
		if (result == null) {
			FlowNode source;
			if (inputFlow.getSource().isFunction()) {
				source = allFunctions.get(inputFlow.getSource().getAddress());
			} else {
				source = allItems.get(inputFlow.getSource().getAddress());
			}
			FlowNode target;
			if (inputFlow.getTarget().isFunction()) {
				target = allFunctions.get(inputFlow.getTarget().getAddress());
			} else {
				target = allItems.get(inputFlow.getTarget().getAddress());
			}

			Item sourceItem = source.getItem();
			Item targetItem = target.getItem();

			Interface iface = loadInterface(
					allItems, allInterfaces,
					sourceItem, targetItem,
					input);
			Interface ifaceTrace = iface.getTrace();

			FlowType type = allTypes.get(inputFlow.getFlowType().getAddress());
			FlowType typeTrace = type.getTrace();

			Function functionTrace;
			Flow flowTrace;
			if (ifaceTrace == null) {
				flowTrace = null;
				/*
				 * There is no higher level interface. This is it. That means
				 * the flow must fall directly under a top-level context or a
				 * function that both end of the flow trace to
				 */
				functionTrace = null;
				if (source instanceof Function && target instanceof Function) {
					if (((Function) source).getTrace() != null
							&& (((Function) source).getTrace() == (((Function) target).getTrace()))) {
						functionTrace = (((Function) source).getTrace());
					}
				}
				if (functionTrace == null) {
					functionTrace = allFunctions.get(BreakdownAddress.getContext());
				}
			} else {
				functionTrace = null;
				// The parent flow must be on the traced interface
				InputNode parentSource;
				if (ifaceTrace.getKey().contains(source.getItem().getAddress())) {
					parentSource = input.getItems().get(source.getItem().getAddress());
				} else {
					if (source instanceof Function) {
						parentSource = input.getFunctions().get(((Function) source).getAddress().getParent());
					} else {
						parentSource = input.getItems().get(source.getItem().getAddress().getParent());
					}
				}
				InputNode parentTarget;
				if (ifaceTrace.getKey().contains(target.getItem().getAddress())) {
					parentTarget = input.getItems().get(target.getItem().getAddress());
				} else {
					if (target instanceof Function) {
						parentTarget = input.getFunctions().get(((Function) target).getAddress().getParent());
					} else {
						parentTarget = input.getItems().get(target.getItem().getAddress().getParent());
					}
				}

				InputFlow parentFlow = null;
				if (parentFlow == null && typeTrace != null) {
					// Look for a flow of the parent type first
					InputFlow candidate = new InputFlow(
							parentSource,
							new Token(typeTrace.getAddress(), typeTrace.getName()),
							parentTarget);
					if (input.getFlows().contains(candidate)) {
						parentFlow = candidate;
					}
				}
				if (parentFlow == null) {
					// We didn't find a parent flow, so force one of our type
					parentFlow = new InputFlow(
							parentSource,
							inputFlow.getFlowType(),
							parentTarget);
				}

				flowTrace = loadFlow(
						allItems, allFunctions, allTypes, allInterfaces, allFlows,
						parentFlow,
						input);
			}

			result = null;
			if (functionTrace != null) {
				result = new Flow(functionTrace, iface, source, type, target);
			}
			if (flowTrace != null) {
				result = new Flow(flowTrace, iface, source, type, target);
			}
			assert result != null;
			result.bind();
			allFlows.put(inputFlow, result);
		}
		return result;
	}

	private static void loadFlows(
			Map<BreakdownAddress, Item> allItems,
			Map<BreakdownAddress, Function> allFunctions,
			Map<BreakdownAddress, FlowType> allTypes,
			Map<InterfaceKey, Interface> allInterfaces,
			Map<InputFlow, Flow> allFlows,
			InputModel input) throws IOException, InvalidHierarchyException {
		for (InputFlow flow : input.getFlows()) {
			loadFlow(allItems, allFunctions, allTypes, allInterfaces, allFlows, flow, input);
		}
	}
}

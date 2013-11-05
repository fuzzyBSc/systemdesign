/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.id.soundadvice.systemdesign.input;

import au.com.bytecode.opencsv.CSVParser;
import au.id.soundadvice.systemdesign.hierarchy.BreakdownAddress;
import au.id.soundadvice.systemdesign.model.InterfaceKey;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author fuzzy
 */
public class InputModel {

	public Map<BreakdownAddress, InputItem> getItems() {
		return items;
	}

	public Map<BreakdownAddress, InputFunction> getFunctions() {
		return functions;
	}

	public Set<InterfaceKey> getInterfaces() {
		return interfaces;
	}

	public Map<BreakdownAddress, String> getTypes() {
		return types;
	}

	public List<InputFlow> getFlows() {
		return flows;
	}
	private final Map<BreakdownAddress, InputItem> items;
	private final Map<BreakdownAddress, InputFunction> functions;
	private final Set<InterfaceKey> interfaces;
	private final Map<BreakdownAddress, String> types;
	private final List<InputFlow> flows;

	@Override
	public String toString() {
		return flows.toString();
	}
	private static final CSVParser parser = new CSVParser();

	public InputModel(List<File> files) throws IOException {
		Map<BreakdownAddress, InputItem> tmpItems = new HashMap<>();
		Map<BreakdownAddress, InputFunction> tmpFunctions = new HashMap<>();
		Set<InterfaceKey> tmpInterfaces = new HashSet<>();
		Map<BreakdownAddress, String> tmpTypes = new HashMap<>();
		List<InputFlow> tmpFlows = new ArrayList<>();
		for (File file : files) {
			try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
				Map<String, Integer> header = null;
				for (;;) {
					String line = reader.readLine();
					if (line == null) {
						break;
					}
					if (line.isEmpty() || line.startsWith("#")) {
						continue;
					}
					String[] fields = parser.parseLine(line);
					if (header == null) {
						header = new HashMap<>();
						for (int ii = 0; ii < fields.length; ++ii) {
							header.put(fields[ii].toLowerCase(), ii);
						}
					} else {
						FunctionToken source = new FunctionToken(
								fields[header.get("source")]);
						FunctionToken target = new FunctionToken(
								fields[header.get("target")]);
						Token flowType = new Token(fields[header.get("flow")]);

						tmpTypes.put(flowType.getAddress(), flowType.getName());
						tmpInterfaces.add(
								new InterfaceKey(source.getItemToken().getAddress(),
								target.getItemToken().getAddress()));

						InputItem sourceItem = tmpItems.get(
								source.getItemToken().getAddress());
						if (sourceItem == null) {
							tmpItems.put(
									source.getItemToken().getAddress(),
									sourceItem = new InputItem(source.getItemToken()));
						}
						InputItem targetItem = tmpItems.get(
								target.getItemToken().getAddress());
						if (targetItem == null) {
							tmpItems.put(
									target.getItemToken().getAddress(),
									targetItem = new InputItem(target.getItemToken()));
						}

						InputNode sourceNode;
						Token sourceFunctionToken = source.getFunctionToken();
						if (sourceFunctionToken == null) {
							sourceNode = sourceItem;
						} else {
							sourceNode = tmpFunctions.get(
									sourceFunctionToken.getAddress());
							if (sourceNode == null) {
								InputFunction sourceFunction = new InputFunction(sourceItem, sourceFunctionToken);
								sourceNode = sourceFunction;
								tmpFunctions.put(
										sourceFunctionToken.getAddress(), sourceFunction);
							}
						}
						InputNode targetNode;
						Token targetFunctionToken = target.getFunctionToken();
						if (targetFunctionToken == null) {
							targetNode = targetItem;
						} else {
							targetNode = tmpFunctions.get(
									targetFunctionToken.getAddress());
							if (targetNode == null) {
								InputFunction targetFunction = new InputFunction(targetItem, targetFunctionToken);
								targetNode = targetFunction;
								tmpFunctions.put(
										targetFunctionToken.getAddress(),
										targetFunction);
							}
						}

						tmpFlows.add(new InputFlow(sourceNode, flowType, targetNode));
					}
				}
			}
		}

		this.items = Collections.unmodifiableMap(tmpItems);
		this.functions = Collections.unmodifiableMap(tmpFunctions);
		this.types = Collections.unmodifiableMap(tmpTypes);
		this.interfaces = Collections.unmodifiableSet(tmpInterfaces);
		this.flows = Collections.unmodifiableList(tmpFlows);
	}
}

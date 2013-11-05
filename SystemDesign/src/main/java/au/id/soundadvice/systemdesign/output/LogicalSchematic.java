/*
 * Please refer to the LICENSE file for licensing information.
 */
package au.id.soundadvice.systemdesign.output;

import au.id.soundadvice.systemdesign.model.Flow;
import au.id.soundadvice.systemdesign.model.Function;
import au.id.soundadvice.systemdesign.model.Interface;
import au.id.soundadvice.systemdesign.model.Item;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class LogicalSchematic {

	public static void write(PrintStream stream, Item item) throws IOException {
		stream.println("digraph G {");

		// Distinguish subsystem functions from external systems
		for (Item subsystem : item.getChildren().values()) {
			Cluster cluster = new Cluster(
					subsystem.toString(),
					"node [ style = \"bold\" ]\n"
					+ "graph [ style = \"bold\" ]");
			for (Function function : subsystem.getFunctions()) {
				cluster.add(function.toString());
			}
			cluster.write(stream);
		}

		Set<Item> physicalNodes = new HashSet<>();
		for (Interface iface : item.getSubsystemInterfaces()) {
			for (Flow flow : iface.getFlows()) {
				if (flow.getSource().isItem()) {
					physicalNodes.add(flow.getSource().getItem());
				}
				if (flow.getTarget().isItem()) {
					physicalNodes.add(flow.getTarget().getItem());
				}
				stream.print('"');
				stream.print(flow.getSource());
				stream.print("\" -> \"");
				stream.print(flow.getTarget());
				stream.print("\" [ label=\"");
				stream.print(flow.getType().getName().replaceAll("\"", "\\\""));
				stream.println("\" ]");
			}
		}
		for (Item physical : physicalNodes) {
			stream.print('"');
			stream.print(physical.toString());
			stream.print('"');
			stream.println(" [shape=record]");
		}
		stream.println("}");
	}
}

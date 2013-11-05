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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Benjamin Carlyle <benjamincarlyle@soundadvice.id.au>
 */
public class LogicalContext {

	public static void write(PrintStream stream, Item item) throws IOException {
		stream.println("digraph G {");

		Cluster itemCluster = new Cluster(
				item.toString(),
				"node [ style = \"bold\" ]\n"
				+ "graph [ style = \"bold\" ]");

		// Distinguish system functions from external systems
		for (Function function : item.getFunctions()) {
			itemCluster.add(function.toString());
		}
		itemCluster.write(stream);

		Map<String, Cluster> externalClusters = new HashMap<>();

		Set<Item> physicalNodes = new HashSet<>();
		Item itemTrace = item.getTrace();
		if (itemTrace != null) {
			for (Interface iface : itemTrace.getSubsystemInterfaces()) {
				for (Flow flow : iface.getFlows()) {
					if (flow.getSource().getItem() == item
							|| flow.getTarget().getItem() == item) {
						if (flow.getSource().isItem()) {
							physicalNodes.add(flow.getSource().getItem());
						} else {
							String nodeName = flow.getSource().toString();
							String clusterName = flow.getSource().getItem().toString();
							if (flow.getSource().getItem() != item) {
								Cluster cluster = externalClusters.get(clusterName);
								if (cluster == null) {
									cluster = new Cluster(clusterName, "");
									externalClusters.put(clusterName, cluster);
								}
								cluster.add(nodeName);
							}
						}
						if (flow.getTarget().isItem()) {
							physicalNodes.add(flow.getTarget().getItem());
						} else {
							String nodeName = flow.getTarget().toString();
							String clusterName = flow.getTarget().getItem().toString();
							if (flow.getTarget().getItem() != item) {
								Cluster cluster = externalClusters.get(clusterName);
								if (cluster == null) {
									cluster = new Cluster(clusterName, "");
									externalClusters.put(clusterName, cluster);
								}
								cluster.add(nodeName);
							}
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
			}
		}
		for (Cluster cluster : externalClusters.values()) {
			cluster.write(stream);
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

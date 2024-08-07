package gregtech.api.graphnet.graph;

import gregtech.api.graphnet.GraphNetBacker;
import gregtech.api.graphnet.edge.SimulatorKey;
import gregtech.api.graphnet.predicate.test.IPredicateTestObject;

import org.jetbrains.annotations.ApiStatus;
import org.jgrapht.Graph;

public interface INetGraph extends Graph<GraphVertex, GraphEdge> {

    void prepareForAlgorithmRun(IPredicateTestObject testObject, SimulatorKey simulator, long queryTick);

    boolean isDirected();

    /**
     * This should only be called by {@link GraphNetBacker}
     */
    @ApiStatus.Internal
    void setupInternal(GraphNetBacker backer, boolean dynamicWeights);
}
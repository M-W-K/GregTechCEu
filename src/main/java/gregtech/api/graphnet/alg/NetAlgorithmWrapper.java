package gregtech.api.graphnet.alg;

import gregtech.api.graphnet.IGraphNet;
import gregtech.api.graphnet.path.INetPath;
import gregtech.api.graphnet.graph.GraphVertex;

import net.minecraftforge.fml.common.FMLCommonHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.function.Function;

public class NetAlgorithmWrapper {

    private final IGraphNet net;
    @Nullable
    private INetAlgorithm alg;

    private final Function<IGraphNet, @NotNull INetAlgorithm> builder;

    public NetAlgorithmWrapper(IGraphNet net, @NotNull Function<IGraphNet, @NotNull INetAlgorithm> builder) {
        this.net = net;
        this.builder = builder;
    }

    public IGraphNet getNet() {
        return net;
    }

    public void invalidate() {
        this.alg = null;
    }

    public boolean supportsDynamicWeights() {
        if (alg == null) alg = builder.apply(net);
        return alg.supportsDynamicWeights();
    }

    public <Path extends INetPath<?, ?>> Iterator<Path> getPathsIterator(GraphVertex source, NetPathMapper<Path> remapper) {
        net.getGraph().setQueryTick(FMLCommonHandler.instance().getMinecraftServerInstance().getTickCounter());
        if (alg == null) alg = builder.apply(net);
        return alg.getPathsIterator(source, remapper);
    }
}

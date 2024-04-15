package gregtech.common.pipelike.optical.net;

import gregtech.api.capability.IDataAccessHatch;
import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.capability.IOpticalDataAccessHatch;
import gregtech.api.recipes.Recipe;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class OpticalNetHandler implements IDataAccessHatch, IOpticalComputationProvider {

    private final TileEntityOpticalPipe pipe;
    private final World world;
    private final EnumFacing facing;

    private OpticalPipeNet net;

    public OpticalNetHandler(OpticalPipeNet net, @NotNull TileEntityOpticalPipe pipe, @Nullable EnumFacing facing) {
        this.net = net;
        this.pipe = pipe;
        this.facing = facing;
        this.world = pipe.getWorld();
    }

    public void updateNetwork(OpticalPipeNet net) {
        this.net = net;
    }

    public OpticalPipeNet getNet() {
        return net;
    }

    @Override
    public boolean isRecipeAvailable(@NotNull Recipe recipe, @NotNull Collection<IDataAccessHatch> seen) {
        boolean isAvailable = traverseRecipeAvailable(recipe, seen);
        if (isAvailable) setPipesActive();
        return isAvailable;
    }

    @Override
    public @Nullable Recipe findCompoundRecipe(long voltage, List<ItemStack> inputs, List<FluidStack> fluidInputs,
                                               @NotNull Collection<IDataAccessHatch> seen) {
        Recipe recipe = traverseRecipeFind(voltage, inputs, fluidInputs, seen);
        if (recipe != null) setPipesActive();
        return recipe;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    @Override
    public int requestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
        int provided = traverseRequestCWUt(cwut, simulate, seen);
        if (provided > 0) setPipesActive();
        return provided;
    }

    @Override
    public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
        return traverseMaxCWUt(seen);
    }

    @Override
    public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
        return traverseCanBridge(seen);
    }

    private void setPipesActive() {
        for (BlockPos pos : net.getAllNodes().keySet()) {
            if (world.getTileEntity(pos) instanceof TileEntityOpticalPipe opticalPipe) {
                opticalPipe.setActive(true, 100);
            }
        }
    }

    private boolean isNetInvalidForTraversal() {
        return net == null || pipe == null || pipe.isInvalid();
    }

    private boolean traverseRecipeAvailable(@NotNull Recipe recipe, @NotNull Collection<IDataAccessHatch> seen) {
        if (isNetInvalidForTraversal()) return false;

        OpticalRoutePath inv = net.getNetData(pipe.getPipePos(), facing);
        if (inv == null) return false;

        IOpticalDataAccessHatch hatch = inv.getDataHatch();
        if (hatch == null || seen.contains(hatch)) return false;

        if (hatch.isTransmitter()) {
            return hatch.isRecipeAvailable(recipe, seen);
        }
        return false;
    }

    private Recipe traverseRecipeFind(long voltage, List<ItemStack> inputs, List<FluidStack> fluidInputs, @NotNull Collection<IDataAccessHatch> seen) {
        if (isNetInvalidForTraversal()) return null;

        OpticalRoutePath inv = net.getNetData(pipe.getPipePos(), facing);
        if (inv == null) return null;

        IOpticalDataAccessHatch hatch = inv.getDataHatch();
        if (hatch == null || seen.contains(hatch)) return null;

        if (hatch.isTransmitter()) {
            return hatch.findCompoundRecipe(voltage, inputs, fluidInputs, seen);
        }
        return null;
    }

    private int traverseRequestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
        IOpticalComputationProvider provider = getComputationProvider(seen);
        if (provider == null) return 0;
        return provider.requestCWUt(cwut, simulate, seen);
    }

    private int traverseMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
        IOpticalComputationProvider provider = getComputationProvider(seen);
        if (provider == null) return 0;
        return provider.getMaxCWUt(seen);
    }

    private boolean traverseCanBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
        IOpticalComputationProvider provider = getComputationProvider(seen);
        if (provider == null) return true; // nothing found, so don't report a problem, just pass quietly
        return provider.canBridge(seen);
    }

    @Nullable
    private IOpticalComputationProvider getComputationProvider(@NotNull Collection<IOpticalComputationProvider> seen) {
        if (isNetInvalidForTraversal()) return null;

        OpticalRoutePath inv = net.getNetData(pipe.getPipePos(), facing);
        if (inv == null) return null;

        IOpticalComputationProvider hatch = inv.getComputationHatch();
        if (hatch == null || seen.contains(hatch)) return null;
        return hatch;
    }
}

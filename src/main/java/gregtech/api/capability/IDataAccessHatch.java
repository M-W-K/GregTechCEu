package gregtech.api.capability;

import gregtech.api.recipes.CompoundRecipe;
import gregtech.api.recipes.Recipe;

import gregtech.api.recipes.RecipeMaps;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public interface IDataAccessHatch {

    /**
     * If passed a {@code seen} context, you must use {@link #isRecipeAvailable(Recipe, Collection)} to prevent
     * infinite recursion
     *
     * @param recipe the recipe to check
     * @return if the recipe is available for use
     */
    default boolean isRecipeAvailable(@NotNull Recipe recipe) {
        Collection<IDataAccessHatch> list = new ArrayList<>();
        list.add(this);
        return isRecipeAvailable(recipe, list);
    }

    /**
     * @param recipe the recipe to check
     * @param seen   the hatches already checked
     * @return if the recipe is available for use
     */
    boolean isRecipeAvailable(@NotNull Recipe recipe, @NotNull Collection<IDataAccessHatch> seen);

    /**
     * If passed a {@code seen} context, you must use {@link #findCompoundRecipe(long, List, List, Collection)} to
     * prevent infinite recursion
     *
     * @param voltage     Voltage of the Machine or Long.MAX_VALUE if it has no Voltage
     * @param inputs      the Item Inputs
     * @param fluidInputs the Fluid Inputs
     * @return the first matching compound recipe found, or null.
     */
    @Nullable
    default CompoundRecipe findCompoundRecipe(long voltage, List<ItemStack> inputs, List<FluidStack> fluidInputs) {
        Collection<IDataAccessHatch> list = new ArrayList<>();
        list.add(this);
        return findCompoundRecipe(voltage, inputs, fluidInputs, list);
    }

    /**
     *
     * @param voltage     Voltage of the Machine or Long.MAX_VALUE if it has no Voltage
     * @param inputs      the Item Inputs
     * @param fluidInputs the Fluid Inputs
     * @param seen the hatches already checked
     * @return the first matching compound recipe found, or null.
     */
    @Nullable
    CompoundRecipe findCompoundRecipe(long voltage, List<ItemStack> inputs, List<FluidStack> fluidInputs, @NotNull Collection<IDataAccessHatch> seen);

    /**
     * @return true if this Data Access Hatch is creative or not
     */
    boolean isCreative();
}

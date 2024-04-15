package gregtech.api.util;

import gregtech.api.recipes.CompoundRecipe;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMaps;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class AdvancedProcessingLineManager {

    private static final Map<Recipe, Integer> referentCountMap = new Object2IntOpenHashMap<>();

    public static final String COMPOUND_RECIPE_NBT_TAG = "advancedprocessinglineRecipe";

    private AdvancedProcessingLineManager() {}

    /**
     * @param stackCompound the compound contained on the ItemStack to write to
     * @param compoundRecipe the compound recipe
     */
    public static void writeCompoundRecipeToNBT(@NotNull NBTTagCompound stackCompound, @NotNull CompoundRecipe compoundRecipe) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("recipe", compoundRecipe.writeToNBT());
        stackCompound.setTag(COMPOUND_RECIPE_NBT_TAG, tag);
    }

    /**
     * @param stack the ItemStack to read from
     * @return the compound recipe
     */
    @Nullable
    public static CompoundRecipe readCompoundRecipe(@NotNull ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (!hasRecipeTag(tag)) return null;

        NBTTagCompound compoundRecipeTag = tag.getCompoundTag(COMPOUND_RECIPE_NBT_TAG);
        return CompoundRecipe.readFromNBT(tag.getCompoundTag("recipe"));
    }

    /**
     * @param tag the tag to check
     * @return if the tag has the compound recipe NBTTagCompound
     */
    private static boolean hasRecipeTag(@Nullable NBTTagCompound tag) {
        if (tag == null || tag.isEmpty()) return false;
        return tag.hasKey(COMPOUND_RECIPE_NBT_TAG, Constants.NBT.TAG_COMPOUND);
    }

    public static void addRecipe(CompoundRecipe compoundRecipe) {
        Recipe recipe = compoundRecipe.getRecipe().getResult();
        referentCountMap.compute(recipe, (k, v) -> {
            if (v == null) {
                RecipeMaps.ADVANCED_PROCESSING_LINE_RECIPES.addRecipe(compoundRecipe.getRecipe());
                return 1;
            }
            else return v + 1;
        });
    }

    public static void removeRecipe(CompoundRecipe compoundRecipe) {
        Recipe recipe = compoundRecipe.getRecipe().getResult();
        referentCountMap.computeIfPresent(recipe, (k, v) -> {
            if (v == 1) {
                RecipeMaps.ADVANCED_PROCESSING_LINE_RECIPES.removeRecipe(recipe);
                return null;
            }
            else return v - 1;
        });
    }
}

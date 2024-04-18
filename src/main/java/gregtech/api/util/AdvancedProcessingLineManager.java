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
        return CompoundRecipe.readFromNBT(compoundRecipeTag.getCompoundTag("recipe"));
    }

    /**
     * @param stack the stack to check
     * @return if the stack has the compound recipe NBTTagCompound
     */
    public static boolean hasRecipeTag(@NotNull ItemStack stack) {
        return hasRecipeTag(stack.getTagCompound());
    }

    /**
     * @param tag the tag to check
     * @return if the tag has the compound recipe NBTTagCompound
     */
    private static boolean hasRecipeTag(@Nullable NBTTagCompound tag) {
        if (tag == null || tag.isEmpty()) return false;
        return tag.hasKey(COMPOUND_RECIPE_NBT_TAG, Constants.NBT.TAG_COMPOUND);
    }
}

package gregtech.api.recipes;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.ingredients.GTRecipeFluidInput;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import gregtech.api.recipes.ingredients.IntCircuitIngredient;
import gregtech.api.recipes.logic.OverclockingLogic;
import gregtech.api.util.AdvancedProcessingLineManager;
import gregtech.api.util.EnumValidationResult;
import gregtech.api.util.ValidationResult;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class CompoundRecipe {

    private final Map<Recipe, RecipeInfo> recipes = new Object2ObjectOpenHashMap<>();

    private int circuitMeta;

    private ValidationResult<Recipe> bakedRecipe;
    private boolean validBaked = false;

    public CompoundRecipe() {}

    public void addRecipe(Recipe recipe, RecipeMap<?> recipeMap, List<ItemStack> machineStacks) {
        addRecipe(recipe, recipeMap, machineStacks, 1);
    }

    public void addRecipe(Recipe recipe, RecipeMap<?> recipeMap, List<ItemStack> machineStacks, int mult) {
        addRecipe(new RecipeInfo(recipe, mult, recipeMap, machineStacks));
    }

    protected void addRecipe(RecipeInfo info) {
        recipes.put(info.recipe, info);
        invalidateBaked();
    }

    public boolean setRecipeMult(Recipe recipe, int mult) {
        RecipeInfo info = recipes.get(recipe);
        if (info == null) return false;
        info.mult = mult;
        invalidateBaked();
        return true;
    }

    public Map<Recipe, RecipeInfo> getRecipes() {
        return recipes;
    }

    public void removeRecipe(Recipe recipe) {
        recipes.remove(recipe);
        invalidateBaked();
    }

    public void setCircuitMeta(int circuitMeta) {
        this.circuitMeta = circuitMeta;
        invalidateBaked();
    }

    public int getCircuitMeta() {
        return circuitMeta;
    }

    public void bakeRecipe() {
        RecipeBuilder<?> builder = new SimpleRecipeBuilder().EUt(1).duration(0);

        for (Map.Entry<Recipe, RecipeInfo> recipe : recipes.entrySet()) {
            List<ItemStack> recipeOutputs = deepCopyIS(recipe.getKey().getOutputs(), recipe.getValue().mult);
            List<ItemStack> builderOutputs = builder.getOutputs(); // no deepcopy since we overwrite in the end
            List<FluidStack> recipeFluidOutputs = deepCopyFS(recipe.getKey().getFluidOutputs(), recipe.getValue().mult);
            List<FluidStack> builderFluidOutputs = builder.getFluidOutputs();

            List<GTRecipeInput> recipeInputs = deepCopyRI(recipe.getKey().getInputs(), recipe.getValue().mult);
            List<GTRecipeInput> builderInputs = builder.getInputs();
            List<GTRecipeInput> recipeFluidInputs = deepCopyRI(recipe.getKey().getFluidInputs(), recipe.getValue().mult);
            List<GTRecipeInput> builderFluidInputs = builder.getFluidInputs();

            // cancellation
            // piecewise cancellation prevents the removal of catalysts in recipe & builder
            recipeInputs.forEach(a -> builderOutputs.forEach(b -> {
                if (a.acceptsStack(b)) {
                    int dif = a.getAmount() - b.getCount();
                    a.withAmount(Math.max(0, dif));
                    b.setCount(Math.max(0, -dif));
                }
            }));
            builderInputs.forEach(a -> recipeOutputs.forEach(b -> {
                if (a.acceptsStack(b)) {
                    int dif = a.getAmount() - b.getCount();
                    a.withAmount(Math.max(0, dif));
                    b.setCount(Math.max(0, -dif));
                }
            }));
            recipeFluidInputs.forEach(a -> builderFluidOutputs.forEach(b -> {
                if (a.acceptsFluid(b)) {
                    int dif = a.getAmount() - b.amount;
                    a.withAmount(Math.max(0, dif));
                    b.amount = Math.max(0, -dif);
                }
            }));
            builderFluidInputs.forEach(a -> recipeFluidOutputs.forEach(b -> {
                if (a.acceptsFluid(b)) {
                    int dif = a.getAmount() - b.amount;
                    a.withAmount(Math.max(0, dif));
                    b.amount = Math.max(0, -dif);
                }
            }));

            // update builder
            List<ItemStack> compositeOutputs = deepCopyIS(builderOutputs);
            compositeOutputs.addAll(recipeOutputs);
            List<FluidStack> compositeFluidOutputs = deepCopyFS(builderFluidOutputs);
            compositeFluidOutputs.addAll(recipeFluidOutputs);
            List<GTRecipeInput> compositeInputs = deepCopyRI(builderInputs);
            compositeInputs.addAll(recipeInputs);
            List<GTRecipeInput> compositeFluidInputs = deepCopyRI(builderFluidInputs);
            compositeFluidInputs.addAll(recipeFluidInputs);

            builder.clearInputs().inputs(finalizedRI(compositeInputs, true).toArray(new GTRecipeInput[] {}))
                    .clearFluidInputs().fluidInputs(finalizedRI(compositeFluidInputs, false))
                    .clearOutputs().outputs(finalizedIS(compositeOutputs))
                    .clearFluidOutputs().fluidOutputs(finalizedFS(compositeFluidOutputs));

            // account for chance
            recipe.getKey().getChancedOutputs().getChancedEntries().forEach(a -> {
                var stack = a.getIngredient().copy();
                stack.setCount(stack.getCount() * recipe.getValue().mult);
                builder.chancedOutput(stack, a.getChance(), a.getChanceBoost());
            });
            recipe.getKey().getChancedFluidOutputs().getChancedEntries().forEach(a -> {
                var stack = a.getIngredient().copy();
                stack.amount *= recipe.getValue().mult;
                builder.chancedFluidOutput(stack, a.getChance(), a.getChanceBoost());
            });

            // energy handling
            int newEUt = Math.max(recipe.getKey().getEUt(), builder.getEUt());
            // log x / log y = log base y of x
            int builderOCs = (int) (Math.log((double) newEUt / builder.getEUt()) /
                    Math.log(OverclockingLogic.STANDARD_OVERCLOCK_VOLTAGE_MULTIPLIER));
            int recipeOCs = (int) (Math.log((double) newEUt / recipe.getKey().getEUt()) /
                    Math.log(OverclockingLogic.STANDARD_OVERCLOCK_VOLTAGE_MULTIPLIER));

            double builderEU = builder.getDuration() * builder.getEUt() /
                                Math.pow(OverclockingLogic.STANDARD_OVERCLOCK_DURATION_DIVISOR, builderOCs);
            double recipeEU = recipe.getKey().getDuration() * recipe.getKey().getEUt() /
                    Math.pow(OverclockingLogic.STANDARD_OVERCLOCK_DURATION_DIVISOR, recipeOCs);

            builder.duration((int) ((builderEU + recipeEU) / newEUt)).EUt(newEUt);

            // TODO property handling?
        }
        builder.circuitMeta(this.circuitMeta);
        this.bakedRecipe = builder.build();
        validateBaked();
    }

    public ValidationResult<Recipe> getRecipe() {
        if (!validBaked) bakeRecipe();
        return bakedRecipe;
    }

    public boolean matchRecipe(boolean consumeIfSuccessful, long voltage, List<ItemStack> inputs,
                               List<FluidStack> fluidInputs) {
        ValidationResult<Recipe> recipe = getRecipe();
        if (recipe.getType() != EnumValidationResult.VALID) return false;
        return recipe.getResult().matches(consumeIfSuccessful, inputs, fluidInputs)
                && recipe.getResult().getEUt() <= voltage;
    }

    protected void invalidateBaked() {
        validBaked = false;
    }

    protected void validateBaked() {
        validBaked = true;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("recipecount", recipes.size());
        int i = 0;
        for (var entry : recipes.entrySet()) {
            tag.setTag(String.valueOf(i), entry.getValue().writeToNBT(new NBTTagCompound()));
            i++;
        }
        tag.setInteger("circuitmeta", circuitMeta);
        return tag;
    }

    public static CompoundRecipe readFromNBT(NBTTagCompound tag) {
        CompoundRecipe newRecipe = new CompoundRecipe();
        for (int i = 0; i < tag.getInteger("recipecount"); i++) {
            RecipeInfo info = RecipeInfo.readFromNBT(tag.getCompoundTag(String.valueOf(i)));
            if (info == null) continue;
            newRecipe.addRecipe(info);
        }
        newRecipe.setCircuitMeta(tag.getInteger("circuitmeta"));
        return newRecipe;
    }

    public static List<GTRecipeInput> deepCopyRI(List<GTRecipeInput> list) {
        return list.stream().map(a -> a.copyWithAmount(a.getAmount())).collect(Collectors.toList());
    }

    public static List<GTRecipeInput> deepCopyRI(List<GTRecipeInput> list, int mult) {
        return list.stream().map(a -> a.copyWithAmount(a.getAmount() * mult)).collect(Collectors.toList());
    }

    public static List<GTRecipeInput> finalizedRI(List<GTRecipeInput> list, boolean removeCircuitIngredients) {
        return list.stream().peek(a -> {
            if (a instanceof GTRecipeItemInput itemInput) {
                for (var stack : itemInput.getInputStacks()) {
                    stack.setCount(a.getAmount());
                }
            } else if (a instanceof GTRecipeFluidInput fluidInput) {
                fluidInput.getInputFluidStack().amount = a.getAmount();
            }
        }).filter(a -> a.getAmount() != 0 && !(removeCircuitIngredients && a instanceof IntCircuitIngredient))
                .collect(Collectors.toList());
    }

    public static List<ItemStack> deepCopyIS(List<ItemStack> list) {
        return list.stream().map(ItemStack::copy).collect(Collectors.toList());
    }

    public static List<ItemStack> deepCopyIS(List<ItemStack> list, int mult) {
        return list.stream().map(stack -> {
            ItemStack newStack = stack.copy();
            newStack.setCount(stack.getCount() * mult);
            return newStack;
        }).collect(Collectors.toList());
    }

    public static List<ItemStack> finalizedIS(List<ItemStack> list) {
        return list.stream().filter(a -> a.getCount() != 0).collect(Collectors.toList());
    }

    public static List<FluidStack> deepCopyFS(List<FluidStack> list) {
        return list.stream().map(FluidStack::copy).collect(Collectors.toList());
    }

    public static List<FluidStack> deepCopyFS(List<FluidStack> list, int mult) {
        return list.stream().map(fluidStack -> {
            FluidStack newStack = fluidStack.copy();
            newStack.amount *= mult;
            return newStack;
        }).collect(Collectors.toList());
    }

    public static List<FluidStack> finalizedFS(List<FluidStack> list) {
        return list.stream().filter(a -> a.amount != 0).collect(Collectors.toList());
    }

    protected static class RecipeInfo {

        public final Recipe recipe;
        public int mult;
        public final RecipeMap<?> parentMap;
        public final List<ItemStack> machineStacks;

        public RecipeInfo(Recipe recipe, int mult, RecipeMap<?> parentMap, List<ItemStack> machineStacks) {
            this.recipe = recipe;
            this.mult = mult;
            this.parentMap = parentMap;
            this.machineStacks = machineStacks;
        }

        public NBTTagCompound writeToNBT(NBTTagCompound tag) {
            tag.setInteger("voltage", recipe.getEUt());
            NBTTagCompound taag = new NBTTagCompound();
            List<ItemStack> items = recipe.getInputs().stream().map(a -> {
                ItemStack[] stacks = a.getInputStacks();
                if (stacks.length == 0 || a.getAmount() == 0) return null;
                ItemStack stack = stacks[0].copy();
                stack.setCount(a.getAmount());
                return stack;
            }).filter(Objects::nonNull).collect(Collectors.toList());
            taag.setInteger("count", items.size());
            for (int i = 0; i < items.size(); i++) {
                taag.setTag(String.valueOf(i), items.get(i).serializeNBT());
            }
            tag.setTag("items", taag);
            taag = new NBTTagCompound();
            List<FluidStack> fluids = recipe.getFluidInputs().stream().map(a -> {
                if (a.getAmount() == 0) return null;
                FluidStack stack = a.getInputFluidStack().copy();
                stack.amount = a.getAmount();
                return stack;
            }).filter(Objects::nonNull).collect(Collectors.toList());
            taag.setInteger("count", items.size());
            for (int i = 0; i < fluids.size(); i++) {
                taag.setTag(String.valueOf(i), fluids.get(i).writeToNBT(new NBTTagCompound()));
            }
            tag.setTag("fluids", taag);

            tag.setInteger("mult", this.mult);
            tag.setString("recipemap", parentMap.unlocalizedName);
            tag.setInteger("stackcount", machineStacks.size());
            for (int i = 0; i < machineStacks.size(); i++) {
                tag.setTag(String.valueOf(i), machineStacks.get(i).serializeNBT());
            }
            return tag;
        }

        @Nullable
        public static RecipeInfo readFromNBT(NBTTagCompound tag) {
            int mult = tag.getInteger("mult");
            RecipeMap<?> map = RecipeMap.getByName(tag.getString("recipemap"));
            List<ItemStack> machineStacks = new ObjectArrayList<>();
            for (int i = 0; i < tag.getInteger("stackcount"); i++) {
                machineStacks.add(new ItemStack(tag.getCompoundTag(String.valueOf(i))));
            }

            List<ItemStack> items = new ObjectArrayList<>();
            NBTTagCompound itemsTag = tag.getCompoundTag("items");
            for (int i = 0; i < itemsTag.getInteger("count"); i++) {
                items.add(new ItemStack(itemsTag.getCompoundTag(String.valueOf(i))));
            }
            List<FluidStack> fluids = new ObjectArrayList<>();
            NBTTagCompound fluidsTag = tag.getCompoundTag("fluids");
            for (int i = 0; i < fluidsTag.getInteger("count"); i++) {
                fluids.add(FluidStack.loadFluidStackFromNBT(fluidsTag.getCompoundTag(String.valueOf(i))));
            }

            Recipe recipe = map.findRecipe(tag.getInteger("voltage"), items, fluids, true);

            if (recipe == null) return null;
            return new RecipeInfo(recipe, mult, map, machineStacks);
        }

    }
}

package gregtech.common.metatileentities.multi.electric;

import gregtech.api.GTValues;
import gregtech.api.capability.IDataAccessHatch;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.AbstractRecipeLogic;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.metatileentity.IMachineHatchMultiblock;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.DummyCleanroom;
import gregtech.api.metatileentity.multiblock.ICleanroomProvider;
import gregtech.api.metatileentity.multiblock.ICleanroomReceiver;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockDisplayText;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.recipes.CompoundRecipe;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.EnumValidationResult;
import gregtech.api.util.GTUtility;
import gregtech.api.util.RelativeDirection;
import gregtech.api.util.TextComponentUtil;
import gregtech.api.util.TextFormattingUtil;
import gregtech.client.renderer.ICubeRenderer;

import gregtech.client.renderer.texture.Textures;

import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.BlockMultiblockCasing;
import gregtech.common.blocks.MetaBlocks;

import gregtech.core.sound.GTSoundEvents;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static gregtech.api.GTValues.ULV;

public class MetaTileEntityAdvProcessingLine extends RecipeMapMultiblockController implements IMachineHatchMultiblock {

    private boolean machineChanged;

    public MetaTileEntityAdvProcessingLine(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, null);
        this.recipeMapWorkable = new AdvProcessingLineWorkable(this);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityAdvProcessingLine(metaTileEntityId);
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        ((AdvProcessingLineWorkable) this.recipeMapWorkable).findMachineStacks();
    }

    @Override
    public int getMachineLimit() {
        return 4;
    }

    @Override
    protected @NotNull BlockPattern createStructurePattern() {
        TraceabilityPredicate hatchlessPredicate = states(getCasingState())
                .setMinGlobalLimited(12)
                .or(autoAbilities(true, true, true, true, true, true, false))
                .or(abilities(MultiblockAbility.DATA_ACCESS_HATCH, MultiblockAbility.OPTICAL_DATA_RECEPTION).setExactLimit(1));

        return FactoryBlockPattern.start(RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT)
                .aisle("CCCC", "CCXC", "CCCC")
                .aisle("HHHH", "H#GH", "HHHH").setRepeatable(1, 10)
                .aisle("CCCC", "CCCC", "CCCC")
                .where('X', selfPredicate())
                .where('C', hatchlessPredicate)
                .where('H', hatchlessPredicate
                        .or(abilities(MultiblockAbility.MACHINE_HATCH).setMaxLayerLimited(1).setMinLayerLimited(1)))
                .where('G', states(getCasingState2()))
                .where('#', air())
                .build();
    }

    @NotNull
    protected static IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.HSSE_STURDY);
    }

    @NotNull
    protected static IBlockState getCasingState2() {
        return MetaBlocks.MULTIBLOCK_CASING.getState(BlockMultiblockCasing.MultiblockCasingType.ADV_PROCESSING_LINE_CASING);
    }

    @Override
    protected Function<BlockPos, Integer> multiblockPartSorter() {
        return RelativeDirection.BACK.getSorter(getFrontFacing(), getUpwardsFacing(), isFlipped());
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.STURDY_HSSE_CASING;
    }

    @Override
    protected void addDisplayText(List<ITextComponent> textList) {
        AdvProcessingLineWorkable logic = (AdvProcessingLineWorkable) recipeMapWorkable;

        MultiblockDisplayText.builder(textList, isStructureFormed())
                .setWorkingStatus(recipeMapWorkable.isWorkingEnabled(), recipeMapWorkable.isActive())
                .addEnergyUsageLine(recipeMapWorkable.getEnergyContainer())
                .addEnergyTierLine(logic.loadedMachines.isEmpty() ? -1 : logic.machineTier)
                .addCustom(tl -> {
                    if (isStructureFormed()) {
                        // Machine mode text
                        // Shared text components for both states
                        ITextComponent maxMachinesText = TextComponentUtil.stringWithColor(TextFormatting.DARK_PURPLE,
                                Integer.toString(getMachineLimit()));
                        maxMachinesText = TextComponentUtil.translationWithColor(TextFormatting.GRAY,
                                "gregtech.machine.machine_hatch.machines_max", maxMachinesText);

                        if (logic.loadedMachines.isEmpty()) {
                            // No machines in hatch
                            ITextComponent noneText = TextComponentUtil.translationWithColor(TextFormatting.YELLOW,
                                    "gregtech.machine.machine_hatch.machines_none");
                            ITextComponent bodyText = TextComponentUtil.translationWithColor(TextFormatting.GRAY,
                                    "gregtech.machine.machine_hatch.machines", noneText);
                            ITextComponent hoverText1 = TextComponentUtil.translationWithColor(TextFormatting.GRAY,
                                    "gregtech.machine.machine_hatch.machines_none_hover");
                            tl.add(TextComponentUtil.setHover(bodyText, hoverText1, maxMachinesText));
                        } else {
                            // Some amount of machines in hatches
                            for (ItemStack machineStack : logic.loadedMachines.keySet()) {
                                String key = machineStack.getTranslationKey();
                                ITextComponent mapText = TextComponentUtil.translationWithColor(
                                        TextFormatting.DARK_PURPLE,
                                        key + ".name");
                                mapText = TextComponentUtil.translationWithColor(
                                        TextFormatting.DARK_PURPLE,
                                        "%sx %s",
                                        logic.getParallelLimit(), mapText);
                                ITextComponent bodyText = TextComponentUtil.translationWithColor(TextFormatting.GRAY,
                                        "gregtech.machine.machine_hatch.machines", mapText);
                                ITextComponent voltageName = new TextComponentString(GTValues.VNF[logic.machineTier]);
                                int amps = machineStack.getCount();
                                String energyFormatted = TextFormattingUtil
                                        .formatNumbers(GTValues.V[logic.machineTier] * amps);
                                ITextComponent hoverText = TextComponentUtil.translationWithColor(
                                        TextFormatting.GRAY,
                                        "gregtech.machine.machine_hatch.machines_max_eut",
                                        energyFormatted, amps, voltageName);
                                tl.add(TextComponentUtil.setHover(bodyText, hoverText, maxMachinesText));
                            }
                        }

                        // Hatch locked status
                        if (isActive()) {
                            tl.add(TextComponentUtil.translationWithColor(TextFormatting.DARK_RED,
                                    "gregtech.machine.machine_hatch.locked"));
                        }
                    }
                })
                .addWorkingStatusLine()
                .addProgressLine(recipeMapWorkable.getProgressPercent());
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.ADV_PROCESSING_LINE_OVERLAY;
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }

    @Override
    public void notifyMachineChanged() {
        machineChanged = true;
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_MECHANICAL;
    }

    @Override
    public SoundEvent getSound() {
        return GTSoundEvents.ASSEMBLER;
    }

    @Override
    public TraceabilityPredicate autoAbilities(boolean checkEnergyIn, boolean checkMaintenance, boolean checkItemIn,
                                               boolean checkItemOut, boolean checkFluidIn, boolean checkFluidOut,
                                               boolean checkMuffler) {
        TraceabilityPredicate predicate = super.autoAbilities(checkMaintenance, checkMuffler)
                .or(checkEnergyIn ? abilities(MultiblockAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                        .setMaxGlobalLimited(4).setPreviewCount(1) : new TraceabilityPredicate());

        predicate = predicate.or(abilities(MultiblockAbility.IMPORT_ITEMS).setPreviewCount(1));

        predicate = predicate.or(abilities(MultiblockAbility.EXPORT_ITEMS).setPreviewCount(1));

        predicate = predicate.or(abilities(MultiblockAbility.IMPORT_FLUIDS).setPreviewCount(1));

        predicate = predicate.or(abilities(MultiblockAbility.EXPORT_FLUIDS).setPreviewCount(1));

        return predicate;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.universal.tooltip.parallel", getMachineLimit()));
    }

    protected class AdvProcessingLineWorkable extends MultiblockRecipeLogic {

        private static final ICleanroomProvider DUMMY_CLEANROOM = DummyCleanroom.createForAllTypes();

        private final Map<ItemStack, MetaTileEntity> loadedMachines = new Object2ObjectOpenHashMap<>();
        // The Voltage Tier of the machines the APL is operating upon, from GTValues.V
        private int machineTier;
        // The maximum Voltage of the machines the APL is operating upon
        private long machineVoltage;
        // The Compound Recipe the APL is performing
        private CompoundRecipe compoundRecipe;

        public AdvProcessingLineWorkable(RecipeMapMultiblockController tileEntity) {
            super(tileEntity);
        }

        @Override
        public void invalidate() {
            super.invalidate();

            // invalidate mte's cleanroom reference
            for (var mte : loadedMachines.values()) {
                if (mte instanceof ICleanroomReceiver) {
                    ((ICleanroomReceiver) mte).setCleanroom(null);
                }
            }

            // Reset locally cached variables upon invalidation
            loadedMachines.clear();
            machineChanged = true;
            machineTier = 0;
            machineVoltage = 0L;
            compoundRecipe = null;
        }

        @Override
        protected boolean shouldSearchForRecipes() {
            return canWorkWithMachines() && super.shouldSearchForRecipes();
        }

        public boolean canWorkWithMachines() {
            if (machineChanged) {
                findMachineStacks();
                machineChanged = false;
                previousRecipe = null;
                if (isDistinct()) {
                    invalidatedInputList.clear();
                } else {
                    invalidInputsForRecipes = false;
                }
            }
            return (!loadedMachines.isEmpty());
        }

        @Nullable
        @Override
        public RecipeMap<?> getRecipeMap() {
            return null;
        }

        public void findMachineStacks() {
            RecipeMapMultiblockController controller = (RecipeMapMultiblockController) this.metaTileEntity;

            // The Machine Interfaces only have 1 slot each
            List<ItemStack> machines = controller.getAbilities(MultiblockAbility.MACHINE_HATCH).stream()
                    .map(a -> a.getStackInSlot(0)).filter(a -> !a.isEmpty()).collect(Collectors.toList());

            this.machineTier = 0;

            this.loadedMachines.clear();

            for (ItemStack machine : machines) {
                MetaTileEntity mte = GTUtility.getMetaTileEntity(machine);

                if (mte == null) continue;

                // Set the world for MTEs, as some need it for checking their recipes
                MetaTileEntityHolder holder = new MetaTileEntityHolder();
                mte = holder.setMetaTileEntity(mte);
                holder.setWorld(this.metaTileEntity.getWorld());

                // Set the cleanroom of the MTEs to the APL's cleanroom reference
                if (mte instanceof ICleanroomReceiver receiver) {
                    if (ConfigHolder.machines.cleanMultiblocks) {
                        receiver.setCleanroom(DUMMY_CLEANROOM);
                    } else {
                        ICleanroomProvider provider = controller.getCleanroom();
                        if (provider != null) receiver.setCleanroom(provider);
                    }
                }

                // All voltage tiers must be the same
                if (this.machineTier == 0)
                    this.machineTier = mte instanceof ITieredMetaTileEntity ? ((ITieredMetaTileEntity) mte).getTier() : 0;
                else if (this.machineTier != (mte instanceof ITieredMetaTileEntity ? ((ITieredMetaTileEntity) mte).getTier() : 0)) {
                    this.loadedMachines.clear();
                    return;
                }
                this.loadedMachines.put(machine, mte);
            }
            this.machineVoltage = GTValues.V[this.machineTier];
        }

        @Override
        public boolean checkRecipe(@NotNull Recipe recipe) {
            if (this.loadedMachines.isEmpty() || !super.checkRecipe(recipe)) return false;
            Collection<CompoundRecipe.RecipeInfo> infos = this.compoundRecipe.getRecipes().values();
            for (var info : infos) {
                // check for any working machineStack within the RecipeInfo's acceptable stacks
                boolean flag = false;
                for (var machineStack : info.machineStacks) {
                    MetaTileEntity mte = this.loadedMachines.get(machineStack);
                    if (mte == null) continue;
                    AbstractRecipeLogic arl = mte.getRecipeLogic();
                    if (arl == null || !arl.checkRecipe(recipe)) continue;
                    flag = true;
                }
                if (!flag) return false;
            }
            return true;
        }

        @Override
        protected int getOverclockForTier(long voltage) {
            return super.getOverclockForTier(Math.min(machineVoltage, getMaximumOverclockVoltage()));
        }

        @Override
        public int getParallelLimit() {
            return (loadedMachines.isEmpty()) ? getMachineLimit() : Math.min(loadedMachines.keySet().stream()
                    .min(Comparator.comparingInt(ItemStack::getCount)).get().getCount(), getMachineLimit());
        }

        @Override
        protected Recipe findRecipe(long maxVoltage, IItemHandlerModifiable inputs, IMultipleTankHandler fluidInputs) {
            CompoundRecipe foundRecipe = findRecipe(getAbilities(MultiblockAbility.DATA_ACCESS_HATCH), maxVoltage,
                    GTUtility.itemHandlerToList(inputs), GTUtility.fluidHandlerToList(fluidInputs));
            if (foundRecipe == null)
                foundRecipe = findRecipe(getAbilities(MultiblockAbility.OPTICAL_DATA_RECEPTION), maxVoltage,
                        GTUtility.itemHandlerToList(inputs), GTUtility.fluidHandlerToList(fluidInputs));
            if (foundRecipe != null) {
                this.compoundRecipe = foundRecipe;
                return foundRecipe.getRecipe().getResult();
            }
            return null;
        }

        protected CompoundRecipe findRecipe(@NotNull Iterable<? extends IDataAccessHatch> hatches, long maxVoltage,
                                    List<ItemStack> inputs, List<FluidStack> fluidInputs) {
            CompoundRecipe foundRecipe;
            for (IDataAccessHatch hatch : hatches) {
                foundRecipe = hatch.findCompoundRecipe(maxVoltage, inputs, fluidInputs);
                if (foundRecipe != null && foundRecipe.getRecipe().getType() == EnumValidationResult.VALID)
                    return foundRecipe;
            }
            return null;
        }

        @Override
        public long getMaxVoltage() {
            // Allow the APL to use as much power as provided, since tier is gated by the machine anyway.
            // UI text uses the machine stack's tier instead of the getMaxVoltage() tier as well.
            return super.getMaximumOverclockVoltage();
        }

        @Override
        protected int getNumberOfOCs(int recipeEUt) {
            if (!isAllowOverclocking()) return 0;

            int recipeTier = Math.max(0,
                    GTUtility.getTierByVoltage(recipeEUt / Math.max(1, this.parallelRecipesPerformed)));
            int maximumTier = Math.min(this.machineTier, GTUtility.getTierByVoltage(getMaxVoltage()));

            // The maximum number of overclocks is determined by the difference between the tier the recipe is running
            // at,
            // and the maximum tier that the machine can overclock to.
            int numberOfOCs = maximumTier - recipeTier;
            if (recipeTier == ULV) numberOfOCs--; // no ULV overclocking

            return numberOfOCs;
        }
    }
}

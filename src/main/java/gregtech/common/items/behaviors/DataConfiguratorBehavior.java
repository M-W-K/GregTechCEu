package gregtech.common.items.behaviors;

import com.cleanroommc.modularui.api.widget.IGuiAction;

import com.cleanroommc.modularui.api.widget.Interactable;
import gregtech.api.block.machines.MachineItemBlock;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.Widget;
import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IDataItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.items.metaitem.stats.IItemCapabilityProvider;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;
import gregtech.api.recipes.CompoundRecipe;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.AdvancedProcessingLineManager;
import gregtech.api.util.GTUtility;

import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.GuiSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ItemSlot;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class DataConfiguratorBehavior implements IItemBehaviour, ItemUIFactory, IItemCapabilityProvider {

    public DataConfiguratorBehavior() {}

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack itemStack = player.getHeldItem(hand);
        if (!world.isRemote) {
            MetaItemGuiFactory.open(player, hand);
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, itemStack);
    }

    @Override
    public ModularPanel buildUI(HandGuiData guiData, GuiSyncManager guiSyncManager) {
        ModularPanel panel = GTGuis.createPanel(guiData.getUsedItemStack(), 176, 192);

        Column column = new Column().top(5).margin(7, 0)
                .widthRel(1f).coverChildrenHeight();
        column.child(new Row()
                .height(16).coverChildrenWidth()
                .child(IKey.str(guiData.getUsedItemStack().getDisplayName()).color(0xFF222222).asWidget().heightRel(1.0f)));

        column.child(new Row().coverChildrenHeight().marginBottom(2).widthRel(1f)
                .child(new ItemSlot()
                        .slot(SyncHandlers.itemSlot(getStackHandler(guiData), 0)
                                .filter(DataConfiguratorBehavior::isDataItem)
                                .singletonSlotGroup(101))
                        .size(18).marginRight(1)
                        .background(GTGuiTextures.SLOT, GTGuiTextures.DATA_ORB_OVERLAY.asIcon().size(16)))
                .child(new ItemSlot()
                        .slot(SyncHandlers.phantomItemSlot(getStackHandler(guiData), 1)
                                .filter(DataConfiguratorBehavior::isMachineItem)
                                .singletonSlotGroup(101))
                        .setEnabledIf(widget -> hasDataItem(guiData))
                        .marginRight(2))
                .child(IKey.dynamic(() -> getRecipeMapName(getGhostItemRecipeMap(guiData))).color(0xFF222222).asWidget()
                        .heightRel(1.0f)
                        .setEnabledIf(widget -> hasDataItem(guiData) && hasMachineItem(guiData))
                        .alignment(Alignment.CenterLeft)
                        .widthRel(0.75f)
                        .marginLeft(2))
                .child(new ButtonWidget<>()
                        .size(18)
                        .background(GTGuiTextures.BUTTON_PLUS)
                        .disableHoverBackground()
                        .align(Alignment.TopRight)
                        .setEnabledIf(widget -> hasDataItem(guiData))
                        .onMouseReleased(button -> {
                            Interactable.playButtonClickSound();
                            return true;
                        })));
        panel.child(column);

        column = new Column().margin(7, 7, 40, 0)
                .widthRel(1f).coverChildrenHeight();
        column.child(new Row().coverChildrenHeight().marginBottom(2).widthRel(1f)
                .child(new ItemSlot()
                        .slot(SyncHandlers.itemSlot(getStackHandler(guiData), 2)
                                .filter(DataConfiguratorBehavior::isDataItem)
                                .singletonSlotGroup(101))
                        .size(18).marginRight(2)
                        .background(GTGuiTextures.SLOT, GTGuiTextures.DATA_ORB_OVERLAY.asIcon().size(16)))
                .child(new ButtonWidget<>()
                        .size(18)
                        .background(GTGuiTextures.BUTTON_MINUS)
                        .disableHoverBackground()
                        .align(Alignment.TopRight)
                        .setEnabledIf(widget -> hasDataItem(guiData))
                        .onMouseReleased(button -> {
                            Interactable.playButtonClickSound();
                            return true;
                        })));
        column.setEnabledIf(widget -> hasDataItem(guiData));
        panel.child(column);

        return panel.bindPlayerInventory();
    }

    protected static boolean isDataItem(ItemStack stack) {
        if (stack.getItem() instanceof MetaItem<?> metaItem) {
            for (IItemBehaviour behaviour : metaItem.getBehaviours(stack)) {
                if (behaviour instanceof IDataItem) {
                    return true;
                }
            }
        }
        return false;
    }

    protected static boolean isMachineItem(ItemStack stack) {
        return stack.getItem() instanceof MachineItemBlock;
    }

    private DataStackHandler getStackHandler(HandGuiData guiData) {
        return (DataStackHandler) guiData.getUsedItemStack().getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
    }

    private boolean hasDataItem(HandGuiData guiData) {
        return !getStackHandler(guiData).getStackInSlot(0).isEmpty();
    }

    private boolean hasMachineItem(HandGuiData guiData) {
        return !getStackHandler(guiData).getStackInSlot(1).isEmpty();
    }

    private RecipeMap<?> getGhostItemRecipeMap(HandGuiData guiData) {
        MetaTileEntity mte = GTUtility.getMetaTileEntity(getStackHandler(guiData).getStackInSlot(1));
        return (mte == null)? null : mte.getRecipeMap();
    }

    private String getRecipeMapName(RecipeMap<?> map) {
        return (map == null)? "Invalid Machine" : I18n.format("recipemap." + map.unlocalizedName + ".name");
    }

    private boolean isMachineAllowed(ItemStack machineStack) {
        return GTUtility.isMachineValidForMachineHatch(machineStack, MetaTileEntities.ADVANCED_PROCESSING_LINE.getBlacklist());
    }

    private Column getRecipeListWidget(HandGuiData guiData) {
        Column column = new Column();
        for (Map.Entry<Recipe, CompoundRecipe.RecipeInfo> entry : getStackHandler(guiData).getCompoundRecipe().getRecipes().entrySet()) {
//            column.child(new Row()entry.getValue().machineStacks
//                    .child(new ItemSlot()
//                            .slot(SyncHandlers.phantomItemSlot(getStackHandler(guiData), 1)
//                                    .filter(DataConfiguratorBehavior::isMachineItem)
//                                    .singletonSlotGroup(101))
//                            .setEnabledIf(widget -> hasDataItem(guiData))
//                            .marginRight(2))
//                    .child(IKey.dynamic(() -> getRecipeMapName(getGhostItemRecipeMap(guiData))).color(0xFF222222).asWidget()
//                            .heightRel(1.0f)
//                            .setEnabledIf(widget -> hasDataItem(guiData) && hasMachineItem(guiData))
//                            .alignment(Alignment.CenterLeft)
//                            .widthRel(0.75f)
//                            .marginLeft(2))
        }
        return column;
    }

    @Override
    public ICapabilityProvider createProvider(ItemStack itemStack) {
        return new ICapabilityProvider() {

            private final DataStackHandler handler = new DataStackHandler(itemStack);

            @Override
            public boolean hasCapability(@NotNull Capability<?> capability, EnumFacing facing) {
                return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY;
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T> T getCapability(@NotNull Capability<T> capability, EnumFacing facing) {
                if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return (T) handler;
                else return null;
            }
        };
    }

    protected static class DataStackHandler extends ItemStackHandler {

        private final ItemStack stack;
        private CompoundRecipe compoundRecipe;

        public DataStackHandler(ItemStack stack) {
            super(3); // slot 0: data stick, slot 1: machine ghost slot, slot 2: copy data stick
            this.stack = stack;
            NBTTagCompound tag = stack.getTagCompound();
            if (tag != null && tag.hasKey("Items")) this.deserializeNBT(tag.getCompoundTag("Items"));
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return (slot == 1)? DataConfiguratorBehavior.isMachineItem(stack) : DataConfiguratorBehavior.isDataItem(stack);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack returnable = super.extractItem(slot, amount, simulate);
            if (slot == 0) {
                NBTTagCompound tag = new NBTTagCompound();
                AdvancedProcessingLineManager.writeCompoundRecipeToNBT(new NBTTagCompound(), this.getCompoundRecipe());
                returnable.setTagCompound(tag);
            }
            return returnable;
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (slot == 0) {
                ItemStack slotstack = getStackInSlot(slot);
                if (!slotstack.isEmpty()) {
                    this.compoundRecipe = AdvancedProcessingLineManager.readCompoundRecipe(slotstack);
                }
            }
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) {
                tag = new NBTTagCompound();
                stack.setTagCompound(tag);
            }
            tag.setTag("Items", this.serializeNBT());
            super.onContentsChanged(slot);
        }

        public @NotNull CompoundRecipe getCompoundRecipe() {
            if (this.compoundRecipe == null) this.compoundRecipe = new CompoundRecipe();
            return this.compoundRecipe;
        }
    }

}

package gregtech.common.items.behaviors;

import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IDataItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;

import gregtech.api.recipes.CompoundRecipe;

import gregtech.api.util.AdvancedProcessingLineManager;

import gregtech.api.util.AssemblyLineManager;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemStackHandler;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.GuiSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.ItemSlot;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataConfiguratorBehavior implements IItemBehaviour, ItemUIFactory {

    // annoying singleplayer server bug w/ metaitems specifically forces distinct handlers between client and server.
    private final DataStackHandler serverStackHandler = new DataStackHandler();
    private final DataStackHandler clientStackhandler = new DataStackHandler();

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
        var panel = GTGuis.createPanel(guiData.getUsedItemStack(), 176, 192);
        var column = new Column().top(5).margin(7, 0)
                .widthRel(1f).coverChildrenHeight();
        column.child(new Row()
//                .pos(4, 4)
                .height(16).coverChildrenWidth()
                .child(new ItemDrawable(guiData.getUsedItemStack()).asWidget().size(16).marginRight(4))
                .child(IKey.str(guiData.getUsedItemStack().getDisplayName()).color(0xFF222222).asWidget().heightRel(1.0f)));
        column.child(new Row().coverChildrenHeight().marginBottom(2).widthRel(1f)
                .child(new ItemSlot()
                        .slot(SyncHandlers.itemSlot(guiData.isClient() ? clientStackhandler : serverStackHandler, 0)
                                .filter(DataConfiguratorBehavior::isItemValid)
                                .singletonSlotGroup(101))
                        .size(18).marginRight(2)
                        .background(GTGuiTextures.SLOT, GTGuiTextures.DATA_ORB_OVERLAY.asIcon().size(16))));
        return panel.child(column).bindPlayerInventory();
    }

    @Nullable
    private CompoundRecipe readCompoundRecipe() {
        return AdvancedProcessingLineManager.readCompoundRecipe(getStoredStack());
    }

    private boolean writeCompoundRecipe(CompoundRecipe recipe) {
        ItemStack dataStack = getStoredStack();
        if (dataStack == ItemStack.EMPTY) return false;
        NBTTagCompound tag = new NBTTagCompound();
        AdvancedProcessingLineManager.writeCompoundRecipeToNBT(tag, recipe);
        dataStack.setTagCompound(tag);
        return true;
    }

    private int getDataStackComplexityLimit(ItemStack dataStack) {
        int max = 0;
        if (dataStack.getItem() instanceof MetaItem<?> metaItem) {
            for (IItemBehaviour behaviour : metaItem.getBehaviours(dataStack)) {
                if (behaviour instanceof IDataItem dataBehavior) {
                    max = Math.max(max, dataBehavior.maxCompoundRecipeComplexity());
                }
            }
        }
        return max;
    }

    private boolean canManipulateDataStack(ItemStack dataStack) {
        NBTTagCompound tag = dataStack.getTagCompound();
        return tag == null || !tag.hasKey(AssemblyLineManager.RESEARCH_NBT_TAG);
    }

    private ItemStack getStoredStack() {
        ItemStack dataStack = serverStackHandler.getStackInSlot(0);
        if (serverStackHandler.getStackInSlot(0) == ItemStack.EMPTY)
            dataStack = clientStackhandler.getStackInSlot(0);
        return dataStack;
    }

    protected static boolean isItemValid(ItemStack stack) {
        if (stack.getItem() instanceof MetaItem<?> metaItem) {
            for (IItemBehaviour behaviour : metaItem.getBehaviours(stack)) {
                if (behaviour instanceof IDataItem) {
                    return true;
                }
            }
        }
        return false;
    }

    protected static class DataStackHandler extends ItemStackHandler {
        protected DataStackHandler() {
            super();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return DataConfiguratorBehavior.isItemValid(stack);
        }
    }

}

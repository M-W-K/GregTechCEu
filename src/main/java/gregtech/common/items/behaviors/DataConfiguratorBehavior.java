package gregtech.common.items.behaviors;

import gregtech.api.block.machines.MachineItemBlock;
import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IDataItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;
import gregtech.api.recipes.CompoundRecipe;

import gregtech.api.recipes.RecipeMap;

import gregtech.api.util.GTUtility;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
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

import java.util.ArrayList;
import java.util.List;

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
                .child(IKey.str(guiData.getUsedItemStack().getDisplayName()).color(0xFF222222).asWidget().heightRel(1.0f)));

        column.child(new Row().coverChildrenHeight().marginBottom(2).widthRel(1f)
                .child(new ItemSlot()
                        .slot(SyncHandlers.itemSlot(getStackHandler(guiData), 0)
                                .filter(DataConfiguratorBehavior::isDataItem)
                                .singletonSlotGroup(101))
                        .size(18).marginRight(2)
                        .background(GTGuiTextures.SLOT, GTGuiTextures.DATA_ORB_OVERLAY.asIcon().size(16)))
                .child(new ItemSlot()
                        .slot(SyncHandlers.phantomItemSlot(getStackHandler(guiData), 1)
                                .filter(DataConfiguratorBehavior::isMachineItem)
                                .singletonSlotGroup(101))
                        .setEnabledIf(widget -> hasDataItem(guiData))
                )
                .child(IKey.dynamic(() -> getRecipeMapName(getGhostItemRecipeMap(guiData))).color(0xFF222222).asWidget()
                        .heightRel(1.0f)
                        .setEnabledIf(widget -> hasDataItem(guiData) && hasMachineItem(guiData))
                )
                .child(new ButtonWidget<>()
                        .size(18)
                        .background(GTGuiTextures.BUTTON)
                        .align(Alignment.TopRight)
                        .setEnabledIf(widget -> hasDataItem(guiData))
                        .onMouseReleased(button -> {
                            return true;
                        }
                )));
        return panel.child(column).bindPlayerInventory();
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
        return guiData.isClient() ? clientStackhandler : serverStackHandler;
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
        return (map == null)? "Invalid Machine Selected" : I18n.format("recipemap." + map.unlocalizedName + ".name");
    }

    protected static class DataStackHandler extends ItemStackHandler {

        public final List<CompoundRecipe.RecipeInfo> recipes = new ArrayList<>();

        public DataStackHandler() {
            super(2);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return (slot == 0)? DataConfiguratorBehavior.isDataItem(stack) : DataConfiguratorBehavior.isMachineItem(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (slot == 0 && !getStackInSlot(slot).isEmpty()) {
                syncDataItemNBT(getStackInSlot(slot));
            }
            super.onContentsChanged(slot);
        }

        private void syncDataItemNBT(ItemStack stack) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) {
                tag = new NBTTagCompound();
                tag.setTag("recipes", new NBTTagList());
                stack.setTagCompound(tag);
            }
            NBTTagList nbtRecipes = tag.getTagList("recipes", Constants.NBT.TAG_LIST);
            for (NBTBase nbtRecipe : nbtRecipes) {
                if (nbtRecipe instanceof NBTTagCompound) {
                    recipes.add(CompoundRecipe.RecipeInfo.readFromNBT((NBTTagCompound) nbtRecipe));
                }
            }
        }
    }

}

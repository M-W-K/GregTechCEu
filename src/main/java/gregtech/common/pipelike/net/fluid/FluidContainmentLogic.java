package gregtech.common.pipelike.net.fluid;

import gregtech.api.fluids.FluidState;
import gregtech.api.fluids.attribute.FluidAttribute;
import gregtech.api.graphnet.logic.NetLogicEntry;
import gregtech.api.util.GTUtility;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

public final class FluidContainmentLogic extends NetLogicEntry<FluidContainmentLogic, NBTTagCompound> {

    public static final FluidContainmentLogic INSTANCE = new FluidContainmentLogic().contain(FluidState.LIQUID);

    private final Set<ResourceLocation> containableAttributes = new ObjectOpenHashSet<>();
    private @NotNull EnumSet<FluidState> containableStates = EnumSet.noneOf(FluidState.class);

    public FluidContainmentLogic() {
        super("FluidContainment");
    }

    public FluidContainmentLogic getWith(Collection<FluidState> states, Collection<FluidAttribute> attributes) {
        FluidContainmentLogic logic = getNew();
        logic.containableStates.addAll(states);
        for (FluidAttribute attribute : attributes) {
            logic.contain(attribute);
        }
        return logic;
    }

    public FluidContainmentLogic contain(FluidState state) {
        this.containableStates.add(state);
        return this;
    }

    public FluidContainmentLogic contain(FluidAttribute attribute) {
        this.containableAttributes.add(attribute.getResourceLocation());
        return this;
    }

    public FluidContainmentLogic notContain(FluidState state) {
        this.containableStates.remove(state);
        return this;
    }

    public FluidContainmentLogic notContain(FluidAttribute attribute) {
        this.containableAttributes.remove(attribute.getResourceLocation());
        return this;
    }

    public boolean contains(FluidState state) {
        return this.containableStates.contains(state);
    }

    public boolean contains(FluidAttribute attribute) {
        return this.containableAttributes.contains(attribute.getResourceLocation());
    }

    @Override
    public @Nullable FluidContainmentLogic union(NetLogicEntry<?, ?> other) {
        if (other instanceof FluidContainmentLogic logic) {
            if (this.containableAttributes.equals(logic.containableAttributes) &&
                    this.containableStates.equals(logic.containableStates)) {
                return this;
            } else {
                FluidContainmentLogic returnable = getNew();
                returnable.containableStates = EnumSet.copyOf(this.containableStates);
                returnable.containableStates.retainAll(logic.containableStates);
                returnable.containableAttributes.addAll(this.containableAttributes);
                returnable.containableAttributes.retainAll(logic.containableAttributes);
                return returnable;
            }
        }
        return this;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (ResourceLocation loc : containableAttributes) {
            list.appendTag(new NBTTagString(loc.toString()));
        }
        tag.setTag("Attributes", list);
        tag.setByteArray("States", GTUtility.setToMask(containableStates).toByteArray());
        return tag;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        NBTTagList list = nbt.getTagList("Attributes", 8);
        for (int i = 0; i < list.tagCount(); i++) {
            containableAttributes.add(new ResourceLocation(list.getStringTagAt(i)));
        }
        containableStates = GTUtility.maskToSet(FluidState.class, BitSet.valueOf(nbt.getByteArray("States")));
    }

    @Override
    public @NotNull FluidContainmentLogic getNew() {
        return new FluidContainmentLogic();
    }

    @Override
    public void encode(PacketBuffer buf, boolean fullChange) {
        buf.writeVarInt(containableAttributes.size());
        for (ResourceLocation loc : containableAttributes) {
            buf.writeString(loc.toString());
        }
        buf.writeByteArray(GTUtility.setToMask(containableStates).toByteArray());
    }

    @Override
    public void decode(PacketBuffer buf, boolean fullChange) {
        int attributes = buf.readVarInt();
        for (int i = 0; i < attributes; i++) {
            containableAttributes.add(new ResourceLocation(buf.readString(255)));
        }
        containableStates = GTUtility.maskToSet(FluidState.class, BitSet.valueOf(buf.readByteArray(255)));
    }
}
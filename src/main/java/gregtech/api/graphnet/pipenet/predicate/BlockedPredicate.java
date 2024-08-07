package gregtech.api.graphnet.pipenet.predicate;

import gregtech.api.graphnet.predicate.EdgePredicate;
import gregtech.api.graphnet.predicate.test.IPredicateTestObject;

import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagString;

import org.jetbrains.annotations.NotNull;

public final class BlockedPredicate extends EdgePredicate<BlockedPredicate, NBTTagByte> {

    public static final BlockedPredicate INSTANCE = new BlockedPredicate();

    private BlockedPredicate() {
        super("Blocked");
    }

    @Override
    @Deprecated
    public @NotNull BlockedPredicate getNew() {
        return INSTANCE;
    }

    @Override
    public NBTTagByte serializeNBT() {
        return new NBTTagByte((byte) 0);
    }

    @Override
    public void deserializeNBT(NBTTagByte nbt) {}

    @Override
    public boolean andy() {
        return true;
    }

    @Override
    public boolean test(IPredicateTestObject object) {
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BlockedPredicate;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
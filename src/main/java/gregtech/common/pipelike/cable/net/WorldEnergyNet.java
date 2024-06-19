package gregtech.common.pipelike.cable.net;

import gregtech.api.pipenet.AbstractGroupData;
import gregtech.api.pipenet.WorldPipeNetSimple;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.unification.material.properties.WireProperties;
import gregtech.common.pipelike.cable.Insulation;
import gregtech.common.pipelike.cable.tile.TileEntityCable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;

// TODO move onto complex net
public class WorldEnergyNet extends WorldPipeNetSimple<WireProperties, Insulation> {

    private static final String DATA_ID_BASE = "gregtech.e_net";

    public static WorldEnergyNet getWorldEnergyNet(World world) {
        final String DATA_ID = getDataID(DATA_ID_BASE, world);
        WorldEnergyNet eNetWorldData = (WorldEnergyNet) world.loadData(WorldEnergyNet.class, DATA_ID);
        if (eNetWorldData == null) {
            eNetWorldData = new WorldEnergyNet(DATA_ID);
            world.setData(DATA_ID, eNetWorldData);
        }
        eNetWorldData.setWorldAndInit(world);
        return eNetWorldData;
    }

    public WorldEnergyNet(String name) {
        super(name, false, false);
    }

    @Override
    protected Capability<?>[] getConnectionCapabilities() {
        return new Capability[] { CapabilityEnergy.ENERGY };
    }

    @Override
    protected Class<? extends IPipeTile<Insulation, WireProperties>> getBasePipeClass() {
        return TileEntityCable.class;
    }

    @Override
    protected AbstractGroupData<Insulation, WireProperties> getBlankGroupData() {
        return new EnergyGroupData();
    }

    @Override
    protected void writeNodeData(WireProperties nodeData, NBTTagCompound tagCompound) {
        tagCompound.setInteger("voltage", nodeData.getVoltage());
        tagCompound.setInteger("amperage", nodeData.getAmperage());
        tagCompound.setInteger("loss", nodeData.getLossPerBlock());
        tagCompound.setInteger("critical", nodeData.getSuperconductorCriticalTemperature());
        tagCompound.setBoolean("supercond", nodeData.isSuperconductor());
    }

    @Override
    protected WireProperties readNodeData(NBTTagCompound tagCompound) {
        int voltage = tagCompound.getInteger("voltage");
        int amperage = tagCompound.getInteger("amperage");
        int lossPerBlock = tagCompound.getInteger("loss");
        int critical = tagCompound.getInteger("critical");
        boolean supercond = tagCompound.getBoolean("supercond");
        return new WireProperties(voltage, amperage, lossPerBlock, supercond, critical);
    }
}

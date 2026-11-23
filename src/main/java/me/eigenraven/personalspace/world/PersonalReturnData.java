package me.eigenraven.personalspace.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.WorldSavedData;

/** Per-private-dimension, persistent overworld entry coordinates keyed by player. */
public final class PersonalReturnData extends WorldSavedData {
    private static final String DATA_NAME = "personalspace_returns";
    private final Map<String, int[]> entries = new HashMap<>();

    public PersonalReturnData() {
        super(DATA_NAME);
    }

    public PersonalReturnData(String name) {
        super(name);
    }

    public static PersonalReturnData get(net.minecraft.world.World world) {
        PersonalReturnData data = (PersonalReturnData) world.getPerWorldStorage().getOrLoadData(PersonalReturnData.class, DATA_NAME);
        if (data == null) {
            data = new PersonalReturnData();
            world.getPerWorldStorage().setData(DATA_NAME, data);
        }
        return data;
    }

    public void set(UUID player, int x, int y, int z) {
        set(player, 0, x, y, z);
    }

    public void set(UUID player, int dimension, int x, int y, int z) {
        entries.put(player.toString(), new int[] { dimension, x, y, z });
        markDirty();
    }


    public int[] get(UUID player) {
        int[] position = entries.get(player.toString());
        if (position == null) return null;
        if (position.length == 4) return new int[] { position[1], position[2], position[3] };
        return position.clone();
    }

    public int[] getDimensionEntry(UUID player) {
        int[] position = entries.get(player.toString());
        if (position == null) return null;
        if (position.length == 4) return position.clone();
        return new int[] { 0, position[0], position[1], position[2] };
    }

    @Override
    public void readFromNBT(NBTTagCompound root) {
        entries.clear();
        NBTTagCompound all = root.getCompoundTag("entries");
        for (String key : all.getKeySet()) {
            int[] position = all.getIntArray(key);
            if (position.length == 3 || position.length == 4) entries.put(key, position);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound root) {
        NBTTagCompound all = new NBTTagCompound();
        for (Map.Entry<String, int[]> entry : entries.entrySet()) all.setIntArray(entry.getKey(), entry.getValue());
        root.setTag("entries", all);
        return root;
    }
}

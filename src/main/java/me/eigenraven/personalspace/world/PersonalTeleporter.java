package me.eigenraven.personalspace.world;

import net.minecraft.entity.Entity;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

import me.eigenraven.personalspace.block.PortalTileEntity;

public class PersonalTeleporter extends Teleporter {

    int x, y, z;
    private float storedYaw;
    private float storedPitch;
    private boolean restoreRotation;
    private PortalTileEntity sourceTeleporter;

    public PersonalTeleporter(WorldServer world, int x, int y, int z) {
        super(world);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public PersonalTeleporter(WorldServer world, int x, int y, int z, float yaw, float pitch) {
        this(world, x, y, z);
        this.storedYaw = yaw;
        this.storedPitch = pitch;
        this.restoreRotation = true;
    }

    public PersonalTeleporter(PortalTileEntity sourceTeleporter, WorldServer world) {
        super(world);
        this.sourceTeleporter = sourceTeleporter;
        this.x = sourceTeleporter.getTargetTeleportX();
        this.y = sourceTeleporter.getTargetTeleportY();
        this.z = sourceTeleporter.getTargetTeleportZ();
    }

    @Override
    public void placeInPortal(Entity entity, float yaw) {
        this.placeInExistingPortal(entity, yaw);
    }

    @Override
    public boolean placeInExistingPortal(Entity entity, float yaw) {
        if (sourceTeleporter != null) {
            double dX = sourceTeleporter.targetPosX - sourceTeleporter.getTargetTeleportX();
            double dZ = sourceTeleporter.targetPosZ - sourceTeleporter.getTargetTeleportZ();
            double distanceXZ = Math.sqrt(dX * dX + dZ * dZ);
            if (distanceXZ > 0.0D) {
                double newYaw = Math.acos(dX / distanceXZ) * 180 / Math.PI - 90;
                if (dZ < 0) newYaw -= 180;
                yaw = (float) newYaw;
            }
        } else if (restoreRotation) {
            yaw = storedYaw;
        }
        entity.setLocationAndAngles(x + 0.5, y + 0.1, z + 0.5, yaw, restoreRotation ? storedPitch : 0.0F);
        entity.motionX = 0.0D;
        entity.motionY = 0.0D;
        entity.motionZ = 0.0D;
        return true;
    }

    @Override
    public boolean makePortal(Entity player) {
        return true;
    }

    @Override
    public void removeStalePortalLocations(long time) {}
}

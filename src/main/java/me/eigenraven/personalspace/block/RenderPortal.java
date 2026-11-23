package me.eigenraven.personalspace.block;

import net.minecraft.client.model.ModelBook;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

public class RenderPortal extends TileEntitySpecialRenderer {

    private static final ResourceLocation field_147540_b = new ResourceLocation(
            "textures/entity/enchanting_table_book.png");
    private ModelBook bookModel = new ModelBook();

    @Override
    public void render(TileEntity tile, double x, double y, double z, float partialTickTime, int destroyStage, float alpha) {
        if (!(tile instanceof PortalTileEntity)) {
            return;
        }
        PortalTileEntity portal = (PortalTileEntity) tile;
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glTranslatef((float) x + 0.5F, (float) y + 0.75F, (float) z + 0.5F);

        float animTick = (float) portal.tickCount + partialTickTime;
        GL11.glTranslatef(0.0F, 0.1F + MathHelper.sin(animTick * 0.1F) * 0.01F, 0.0F);

        float facingAngle = (float) Math.atan2(portal.facing.getDirectionVec().getZ(), portal.facing.getDirectionVec().getX());
        GL11.glRotatef(-facingAngle * 180.0F / (float) Math.PI, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(80.0F, 0.0F, 0.0F, 1.0F);
        this.bindTexture(field_147540_b);

        float interpPage = portal.prevPagePosition + (portal.pagePosition - portal.prevPagePosition) * partialTickTime;
        float rightPageFlip = interpPage + 0.25F;
        float leftPageFlip = interpPage + 0.75F;
        rightPageFlip = (rightPageFlip - (float) Math.floor(rightPageFlip)) * 1.6F - 0.3F;
        leftPageFlip = (leftPageFlip - (float) Math.floor(leftPageFlip)) * 1.6F - 0.3F;
        rightPageFlip = MathHelper.clamp(rightPageFlip, 0.0F, 1.0F);
        leftPageFlip = MathHelper.clamp(leftPageFlip, 0.0F, 1.0F);

        GL11.glEnable(GL11.GL_CULL_FACE);
        this.bookModel.render(null, animTick, rightPageFlip, leftPageFlip, 1.0F, 0.0F, 0.0625F);
        GL11.glPopMatrix();
    }
}

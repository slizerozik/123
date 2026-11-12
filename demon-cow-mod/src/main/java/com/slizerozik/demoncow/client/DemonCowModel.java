package com.slizerozik.demoncow.client;

import com.slizerozik.demoncow.DemonCowMod;
import com.slizerozik.demoncow.entity.DemonCow;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Cow built around a torso that pivots at the hips, so the same parts serve both the
 * four-legged pose and the upright one (torso tipped up, front legs spread as arms).
 * The texture layout is mirrored in tools/gen_textures.py.
 */
public class DemonCowModel extends HierarchicalModel<DemonCow> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(DemonCowMod.MODID, "demon_cow"), "main");

    private final ModelPart root;
    private final ModelPart torso;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private float stand;

    public DemonCowModel(ModelPart root) {
        this.root = root;
        this.torso = root.getChild("torso");
        this.head = this.torso.getChild("head");
        this.rightArm = this.torso.getChild("right_arm");
        this.leftArm = this.torso.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6, -10, -15, 12, 10, 18)
                // udder and teats
                .texOffs(32, 32).addBox(-2, 0, -5, 4, 3, 5)
                .texOffs(32, 42).addBox(-2, 3, -5, 1, 1, 1).addBox(1, 3, -5, 1, 1, 1).addBox(-2, 3, -1, 1, 1, 1).addBox(1, 3, -1, 1, 1, 1),
                PartPose.offset(0, 12, 7));

        torso.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-4, -4, -6, 8, 8, 6)
                // hair
                .texOffs(64, 16).addBox(-4.5F, -5, -6.5F, 9, 2, 7)
                // nose ring
                .texOffs(64, 28).addBox(-2.5F, 0, -7, 1, 3, 1).addBox(1.5F, 0, -7, 1, 3, 1)
                .texOffs(70, 28).addBox(-2.5F, 3, -7, 5, 1, 1)
                // horns
                .texOffs(52, 32).addBox(4, -3, -4, 3, 2, 2)
                .texOffs(52, 38).addBox(6, -8, -4, 2, 5, 2)
                .mirror()
                .texOffs(52, 32).addBox(-7, -3, -4, 3, 2, 2)
                .texOffs(52, 38).addBox(-8, -8, -4, 2, 5, 2),
                PartPose.offset(0, -8, -15));

        CubeListBuilder arm = CubeListBuilder.create().texOffs(0, 32).addBox(-2, 0, -2, 4, 12, 4);
        CubeListBuilder armMirrored = CubeListBuilder.create().texOffs(0, 32).mirror().addBox(-2, 0, -2, 4, 12, 4);
        torso.addOrReplaceChild("right_arm", arm, PartPose.offset(-4, 0, -13));
        torso.addOrReplaceChild("left_arm", armMirrored, PartPose.offset(4, 0, -13));

        CubeListBuilder leg = CubeListBuilder.create().texOffs(16, 32).addBox(-2, 0, -2, 4, 12, 4);
        CubeListBuilder legMirrored = CubeListBuilder.create().texOffs(16, 32).mirror().addBox(-2, 0, -2, 4, 12, 4);
        root.addOrReplaceChild("right_leg", leg, PartPose.offset(-4, 12, 7));
        root.addOrReplaceChild("left_leg", legMirrored, PartPose.offset(4, 12, 7));

        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void prepareMobModel(DemonCow entity, float limbSwing, float limbSwingAmount, float partialTick) {
        float t = Mth.lerp(partialTick, entity.standAnimO, entity.standAnim);
        this.stand = t * t * (3 - 2 * t);
    }

    @Override
    public void setupAnim(DemonCow entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float s = this.stand;
        float walk = Mth.cos(limbSwing * 0.3F) * 0.9F * Math.min(limbSwingAmount, 1.0F);
        float idle = Mth.sin(ageInTicks * 0.07F);

        // tip the torso up around the hips; -90 degrees puts the chest on top and the belly forward
        this.torso.xRot = -Mth.HALF_PI * s;
        this.torso.z = Mth.lerp(s, 7, -5);

        this.head.setPos(0, Mth.lerp(s, -8, -4), Mth.lerp(s, -15, -17));
        this.head.xRot = Mth.HALF_PI * s + headPitch * Mth.DEG_TO_RAD * 0.5F + idle * 0.04F;

        // arms: under the chest on four legs, moved out to the shoulders and spread when upright
        float armX = Mth.lerp(s, 4, 7.5F);
        this.rightArm.setPos(-armX, Mth.lerp(s, 0, -5), -13);
        this.leftArm.setPos(armX, Mth.lerp(s, 0, -5), -13);
        float armPitch = Mth.lerp(s, walk, 0.4F + idle * 0.06F);
        float armSpread = s * (1.25F + idle * 0.08F);
        this.rightArm.xRot = armPitch;
        this.leftArm.xRot = Mth.lerp(s, -walk, 0.4F + idle * 0.06F);
        this.rightArm.zRot = armSpread;
        this.leftArm.zRot = -armSpread;

        float dangle = idle * 0.08F;
        this.rightLeg.z = Mth.lerp(s, 7, 0);
        this.leftLeg.z = Mth.lerp(s, 7, 0);
        this.rightLeg.xRot = -walk * (1 - s) + dangle;
        this.leftLeg.xRot = walk * (1 - s) - dangle;
    }
}

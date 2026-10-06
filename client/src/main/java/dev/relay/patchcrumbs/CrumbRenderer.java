package dev.relay.patchcrumbs;

import java.util.List;
import java.util.function.Supplier;

import org.joml.Matrix4f;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;

import dev.relay.ModInfo;
import dev.relay.RelayClient;
import dev.relay.RelayConfig;
import dev.relay.patchcrumbs.PatchCrumbsGuides.BlockSpan;
import dev.relay.patchcrumbs.PatchCrumbsGuides.ClippedLine;

import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;

/**
 * World-space visuals from Patch Guides: axis guide outlines, filled shot box,
 * optional tracer, and XYZ label. Drawn with MaLiLib so the overlay uses the
 * same 1.21 pipelines Litematica already depends on.
 */
public final class CrumbRenderer implements IRenderer {
	private static final CrumbRenderer INSTANCE = new CrumbRenderer();

	private CrumbRenderer() {
	}

	public static void register() {
		RenderEventHandler.getInstance().registerWorldLastRenderer(INSTANCE);
		HudElementRegistry.addLast(
				Identifier.fromNamespaceAndPath(ModInfo.ID, "patchcrumbs"),
				CrumbRenderer::renderHud);
	}

	@Override
	public Supplier<String> getProfilerSectionSupplier() {
		return () -> "relay_patchcrumbs";
	}

	@Override
	public void onRenderWorldLastAdvanced(
			RenderTarget fb,
			Matrix4f posMatrix,
			Matrix4f projMatrix,
			Frustum frustum,
			Camera camera,
			RenderBuffers buffers,
			ProfilerFiller profiler) {
		renderWorld(posMatrix, camera);
	}

	static void renderHud(GuiGraphics graphics, DeltaTracker tickCounter) {
		RelayConfig config = RelayClient.get().config();
		if (!config.patchcrumbs() || Minecraft.getInstance().player == null) {
			return;
		}

		PatchCrumb crumb = liveCrumb();
		if (crumb == null) {
			return;
		}

		String line = "PATCH  " + crumb.posX + "  " + crumb.posY + "  " + crumb.posZ;
		graphics.drawString(Minecraft.getInstance().font, line, 6, 6, config.patchcrumbsPalette().argb(), true);
	}

	private static void renderWorld(Matrix4f posMatrix, Camera camera) {
		RelayConfig cfg = RelayClient.get().config();
		if (!cfg.patchcrumbs()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		PatchCrumb crumb = liveCrumb();
		if (crumb == null) {
			return;
		}
		if (PatchCrumbs.shouldSuppress()) {
			return;
		}

		int argb = cfg.patchcrumbsPalette().argb();
		float width = Math.max(1.0F, PatchCrumbsPolicy.clampWidth(cfg.patchcrumbsWidth()));
		Color4f line = Color4f.fromColor(argb, 1.0F);
		Color4f fill = Color4f.fromColor(argb, 0.5F);
		BlockPos shot = new BlockPos(crumb.posX, crumb.posY, crumb.posZ);
		int reach = PatchCrumbsPolicy.LINE_REACH;
		Vec3 cam = camera.position();
		Vec3 look = Vec3.directionFromRotation(camera.xRot(), camera.yRot());

		// Clip each 400-block axis so a vertex behind the camera cannot inflate into a rectangle.
		BlockSpan eastWest = PatchCrumbsGuides.clipEastWest(
				crumb.posX, crumb.posY, crumb.posZ, reach,
				cam.x, cam.y, cam.z, look.x, look.y, look.z);
		if (!eastWest.isEmpty()) {
			RenderUtils.renderAreaOutline(
					new BlockPos(eastWest.from(), crumb.posY, crumb.posZ),
					new BlockPos(eastWest.to(), crumb.posY, crumb.posZ),
					width, line, line, line);
		}
		BlockSpan northSouth = PatchCrumbsGuides.clipNorthSouth(
				crumb.posX, crumb.posY, crumb.posZ, reach,
				cam.x, cam.y, cam.z, look.x, look.y, look.z);
		if (!northSouth.isEmpty()) {
			RenderUtils.renderAreaOutline(
					new BlockPos(crumb.posX, crumb.posY, northSouth.from()),
					new BlockPos(crumb.posX, crumb.posY, northSouth.to()),
					width, line, line, line);
		}
		RenderUtils.renderBlockOutline(shot, 0.002F, width, line, true);
		RenderUtils.renderAreaSides(shot, shot, fill, posMatrix, true);

		float delta = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		if (cfg.patchcrumbsTracer()) {
			Vec3 eye = client.player.getEyePosition(delta);
			Vec3 target = Vec3.atCenterOf(shot);
			ClippedLine tracer = PatchCrumbsGuides.clipLine(
					eye.x, eye.y, eye.z, target.x, target.y, target.z,
					cam.x, cam.y, cam.z, look.x, look.y, look.z);
			if (tracer != null) {
				drawWorldLine(tracer, line, Math.max(2.0F, width));
			}
		}

		if (cfg.patchcrumbsLabel()) {
			double distance = Math.sqrt(client.player.distanceToSqr(crumb.posX, crumb.posY, crumb.posZ));
			float scale = (float) distance * 0.003F;
			if (scale > 1.0F) {
				scale = 1.0F;
			} else if (scale < 0.04F) {
				scale = 0.04F;
			}
			RenderUtils.drawTextPlate(
					List.of("X: " + crumb.posX, "Y: " + crumb.posY, "Z: " + crumb.posZ),
					crumb.posX + 0.5, crumb.posY + 2.5, crumb.posZ + 0.5, scale, delta);
		}
	}

	private static void drawWorldLine(ClippedLine line, Color4f color, float width) {
		Vec3 cam = RenderUtils.camPos();
		float x1 = (float) (line.x1() - cam.x);
		float y1 = (float) (line.y1() - cam.y);
		float z1 = (float) (line.z1() - cam.z);
		float x2 = (float) (line.x2() - cam.x);
		float y2 = (float) (line.y2() - cam.y);
		float z2 = (float) (line.z2() - cam.z);
		RenderContext ctx = new RenderContext(
				() -> "relay:patchcrumbs_tracer", MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL);
		try {
			BufferBuilder buffer = ctx.getBuilder();
			buffer.addVertex(x1, y1, z1).setColor(color.r, color.g, color.b, color.a).setLineWidth(width);
			buffer.addVertex(x2, y2, z2).setColor(color.r, color.g, color.b, color.a).setLineWidth(width);
			MeshData mesh = buffer.build();
			if (mesh != null) {
				ctx.draw(mesh, false, true);
				mesh.close();
			}
		} catch (Exception ignored) {
		} finally {
			try {
				ctx.close();
			} catch (Exception ignored) {
			}
		}
	}

	private static PatchCrumb liveCrumb() {
		PatchCrumb crumb = PatchCrumbs.currentCrumb;
		if (crumb == null) {
			return null;
		}
		if (System.currentTimeMillis() > crumb.expiresAt) {
			PatchCrumbs.currentCrumb = null;
			return null;
		}
		return crumb;
	}
}

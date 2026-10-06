package dev.relay.ping;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.joml.Matrix4f;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;

import dev.relay.RelayClient;
import dev.relay.patchcrumbs.PatchCrumbsGuides;
import dev.relay.patchcrumbs.PatchCrumbsGuides.ClippedLine;
import dev.relay.ping.PingBeam.Segment;

import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;

/**
 * Local ping beams. Drawn as a translucent shaft, a little thinner than a beacon beam.
 */
public final class PingRenderer implements IRenderer {
	private static final PingRenderer INSTANCE = new PingRenderer();
	private static final float OUTLINE_WIDTH = 2.0F;
	private static final int SIDES = 16;
	/** Centerline clip. The tube radius sits in front of this so the rim is not behind the camera. */
	private static final double CLIP_NEAR = 0.25 + PingBeam.HALF_WIDTH;

	private PingRenderer() {
	}

	public static void register() {
		RenderEventHandler.getInstance().registerWorldLastRenderer(INSTANCE);
	}

	@Override
	public Supplier<String> getProfilerSectionSupplier() {
		return () -> "relay_ping";
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
		renderWorld(camera);
	}

	private static void renderWorld(Camera camera) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}

		List<BlockPing> pings = BlockPings.live();
		if (pings.isEmpty()) {
			return;
		}

		Color4f color = Color4f.fromColor(RelayClient.get().config().pingPalette().argb(), 1.0F);
		Vec3 cam = camera.position();
		Vec3 look = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
		for (BlockPing ping : pings) {
			RenderUtils.renderBlockOutline(ping.pos(), 0.002F, OUTLINE_WIDTH, color, true);
		}
		drawBeams(pings, color, cam, look);
		drawLabels(client, pings);
	}

	private static void drawBeams(List<BlockPing> pings, Color4f color, Vec3 cam, Vec3 look) {
		List<ClippedLine> lines = new ArrayList<>();
		List<Direction.Axis> axes = new ArrayList<>();
		for (BlockPing ping : pings) {
			Segment segment = PingBeam.through(ping.pos(), ping.front());
			ClippedLine line = PatchCrumbsGuides.clipLine(
					segment.x1(), segment.y1(), segment.z1(),
					segment.x2(), segment.y2(), segment.z2(),
					cam.x, cam.y, cam.z, look.x, look.y, look.z, CLIP_NEAR);
			if (line != null) {
				lines.add(line);
				axes.add(ping.front().getAxis());
			}
		}
		if (lines.isEmpty()) {
			return;
		}

		// No depth test: lava, water, and the block face itself would hide the tube
		// when the hit is a thin edge or the camera is inside the fluid.
		RenderContext ctx = new RenderContext(
				() -> "relay:ping_beam", MaLiLibPipelines.POSITION_COLOR_TRANSLUCENT_NO_DEPTH_NO_CULL);
		try {
			BufferBuilder buffer = ctx.getBuilder();
			Vec3 origin = RenderUtils.camPos();
			for (int i = 0; i < lines.size(); i++) {
				addCylinder(buffer, lines.get(i), axes.get(i), origin, color);
			}
			MeshData mesh = buffer.build();
			if (mesh != null) {
				ctx.upload(mesh, false);
				mesh.close();
				ctx.drawPost();
			}
		} catch (Exception ignored) {
		} finally {
			try {
				ctx.close();
			} catch (Exception ignored) {
			}
		}
	}

	private static void addCylinder(BufferBuilder buffer, ClippedLine line, Direction.Axis axis, Vec3 origin, Color4f color) {
		float radius = PingBeam.HALF_WIDTH;
		float x1 = (float) (line.x1() - origin.x);
		float y1 = (float) (line.y1() - origin.y);
		float z1 = (float) (line.z1() - origin.z);
		float x2 = (float) (line.x2() - origin.x);
		float y2 = (float) (line.y2() - origin.y);
		float z2 = (float) (line.z2() - origin.z);
		Basis basis = basis(axis);
		float ux = basis.ux();
		float uy = basis.uy();
		float uz = basis.uz();
		float vx = basis.vx();
		float vy = basis.vy();
		float vz = basis.vz();
		for (int side = 0; side < SIDES; side++) {
			float start = (float) (Math.PI * 2.0 * side / SIDES);
			float end = (float) (Math.PI * 2.0 * (side + 1) / SIDES);
			float c0 = (float) Math.cos(start) * radius;
			float s0 = (float) Math.sin(start) * radius;
			float c1 = (float) Math.cos(end) * radius;
			float s1 = (float) Math.sin(end) * radius;
			quad(buffer, color,
					x1 + ux * c0 + vx * s0, y1 + uy * c0 + vy * s0, z1 + uz * c0 + vz * s0,
					x1 + ux * c1 + vx * s1, y1 + uy * c1 + vy * s1, z1 + uz * c1 + vz * s1,
					x2 + ux * c1 + vx * s1, y2 + uy * c1 + vy * s1, z2 + uz * c1 + vz * s1,
					x2 + ux * c0 + vx * s0, y2 + uy * c0 + vy * s0, z2 + uz * c0 + vz * s0);
		}
	}

	private static Basis basis(Direction.Axis axis) {
		return switch (axis) {
			case X -> new Basis(0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F);
			case Y -> new Basis(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
			case Z -> new Basis(1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F);
		};
	}

	private record Basis(float ux, float uy, float uz, float vx, float vy, float vz) {
	}

	private static void quad(BufferBuilder buffer, Color4f color,
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
			float x4, float y4, float z4) {
		buffer.addVertex(x1, y1, z1).setColor(color.r, color.g, color.b, color.a);
		buffer.addVertex(x2, y2, z2).setColor(color.r, color.g, color.b, color.a);
		buffer.addVertex(x3, y3, z3).setColor(color.r, color.g, color.b, color.a);
		buffer.addVertex(x4, y4, z4).setColor(color.r, color.g, color.b, color.a);
	}

	/** Same distance plate Patchcrumbs uses, so the numbers stay readable far away. */
	private static void drawLabels(Minecraft client, List<BlockPing> pings) {
		float delta = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		for (BlockPing ping : pings) {
			BlockPos pos = ping.pos();
			double distance = Math.sqrt(client.player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()));
			float scale = (float) distance * 0.003F;
			if (scale > 1.0F) {
				scale = 1.0F;
			} else if (scale < 0.04F) {
				scale = 0.04F;
			}
			RenderUtils.drawTextPlate(
					List.of("X: " + pos.getX(), "Y: " + pos.getY(), "Z: " + pos.getZ()),
					pos.getX() + 0.5, pos.getY() + 2.5, pos.getZ() + 0.5, scale, delta);
		}
	}
}

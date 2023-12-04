package org.mtr.mod.data;

import org.mtr.core.data.Rail;
import org.mtr.core.tool.Utilities;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.SlabBlockExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockNode;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

public class RailAction {

	private double distance;

	public final long id;
	private final ServerWorld serverWorld;
	private final UUID uuid;
	private final String playerName;
	private final RailActionType railActionType;
	private final Rail rail;
	private final int radius;
	private final int height;
	private final double length;
	private final BlockState state;
	private final boolean isSlab;
	private final ObjectOpenHashSet<BlockPos> blacklistedPositions = new ObjectOpenHashSet<>();

	private static final double INCREMENT = 0.01;

	public RailAction(ServerWorld serverWorld, ServerPlayerEntity serverPlayerEntity, RailActionType railActionType, Rail rail, int radius, int height, @Nullable BlockState state) {
		id = new Random().nextLong();
		this.serverWorld = serverWorld;
		uuid = serverPlayerEntity.getUuid();
		playerName = serverPlayerEntity.getName().getString();
		this.railActionType = railActionType;
		this.rail = rail;
		this.radius = radius;
		this.height = height;
		this.state = state;
		isSlab = state != null && SlabBlock.isInstance(state.getBlock());
		length = rail.railMath.getLength();
		distance = 0;
	}

	public boolean build() {
		switch (railActionType) {
			case BRIDGE:
				return createBridge();
			case TUNNEL:
				return createTunnel();
			case TUNNEL_WALL:
				return createTunnelWall();
			default:
				return true;
		}
	}

	public String getDescription() {
		return TextHelper.translatable("gui.mtr." + railActionType.nameTranslation, playerName, length, state == null ? "" : state.getBlock().getTranslationKey()).getString();
	}

	public int getColor() {
		return railActionType.color;
	}

	private boolean createTunnel() {
		return create(true, vector -> {
			final BlockPos blockPos = fromVector(vector);
			if (!blacklistedPositions.contains(blockPos) && canPlace(serverWorld, blockPos)) {
				serverWorld.setBlockState(blockPos, Blocks.getAirMapped().getDefaultState());
				blacklistedPositions.add(blockPos);
			}
		});
	}

	private boolean createTunnelWall() {
		return create(false, vector -> {
			final BlockPos blockPos = fromVector(vector);
			if (!blacklistedPositions.contains(blockPos) && canPlace(serverWorld, blockPos)) {
				serverWorld.setBlockState(blockPos, state);
				blacklistedPositions.add(blockPos);
			}
		});
	}

	private boolean createBridge() {
		return create(false, vector -> {
			final boolean isTopHalf = vector.y - Math.floor(vector.y) >= 0.5;
			final BlockPos blockPos = fromVector(vector);
			blacklistedPositions.add(getHalfPos(blockPos, isTopHalf));

			final BlockPos placePos;
			final BlockState placeState;
			final boolean placeHalf;

			if (isSlab && isTopHalf) {
				placePos = blockPos;
				placeState = state.with(new Property<>(SlabBlockExtension.TYPE), SlabType.BOTTOM.data);
				placeHalf = false;
			} else {
				placePos = blockPos.down();
				placeState = isSlab ? state.with(new Property<>(SlabBlockExtension.TYPE), SlabType.TOP.data) : state;
				placeHalf = true;
			}

			if (placePos != blockPos && canPlace(serverWorld, blockPos)) {
				serverWorld.setBlockState(blockPos, Blocks.getAirMapped().getDefaultState());
			}
			if (!blacklistedPositions.contains(getHalfPos(placePos, placeHalf)) && canPlace(serverWorld, placePos)) {
				serverWorld.setBlockState(placePos, placeState);
			}
		});
	}

	private boolean create(boolean includeMiddle, Consumer<Vector> consumer) {
		final long startTime = System.currentTimeMillis();
		while (System.currentTimeMillis() - startTime < 2) {
			final Vector pos1 = rail.railMath.getPosition(distance, false);
			distance += INCREMENT;
			final Vector pos2 = rail.railMath.getPosition(distance, false);
			final Vector vec3 = new Vector(pos2.x - pos1.x, 0, pos2.z - pos1.z).normalize().rotateY((float) Math.PI / 2);

			for (double x = -radius; x <= radius; x += INCREMENT) {
				final Vector editPos = pos1.add(vec3.multiply(x, 0, x));
				final boolean wholeNumber = Math.floor(editPos.y) == Math.ceil(editPos.y);
				if (includeMiddle || Math.abs(x) > radius - INCREMENT) {
					for (int y = 0; y <= height; y++) {
						if (y < height || !wholeNumber) {
							consumer.accept(editPos.add(0, y, 0));
						}
					}
				} else {
					consumer.accept(editPos.add(0, Math.max(0, wholeNumber ? height - 1 : height), 0));
				}
			}

			if (length - distance < INCREMENT) {
				showProgressMessage(100);
				return true;
			}
		}

		showProgressMessage((float) Utilities.round(100 * distance / length, 1));
		return false;
	}

	private void showProgressMessage(float percentage) {
		final PlayerEntity playerEntity = serverWorld.getPlayerByUuid(uuid);
		if (playerEntity != null) {
			playerEntity.sendMessage(new Text(TextHelper.translatable("gui.mtr." + railActionType.progressTranslation, percentage).data), true);
		}
	}

	private static boolean canPlace(ServerWorld serverWorld, BlockPos pos) {
		return serverWorld.getBlockEntity(pos) == null && !(serverWorld.getBlockState(pos).getBlock().data instanceof BlockNode);
	}

	private static BlockPos getHalfPos(BlockPos pos, boolean isTopHalf) {
		return new BlockPos(pos.getX(), pos.getY() * 2 + (isTopHalf ? 1 : 0), pos.getZ());
	}

	private static BlockPos fromVector(Vector vector) {
		return Init.newBlockPos(vector.x, vector.y, vector.z);
	}
}

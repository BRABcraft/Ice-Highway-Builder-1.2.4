
package K_K_L_L.IceRail.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import K_K_L_L.IceRail.addon.IceRail;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Position;

import java.util.List;

import static K_K_L_L.IceRail.addon.Utils.*;
import static K_K_L_L.IceRail.addon.modules.IceHighwayBuilder.*;

public class ScaffoldGrim extends Module {
    public ScaffoldGrim() {
        super(IceRail.CATEGORY, "scaffold-grim", "Places blocks in front of you.");
    }

    IceHighwayBuilder iceHighwayBuilder = Modules.get().get(IceHighwayBuilder.class);
    Setting<ScaffoldGrim.ListMode> blocksFilter = iceHighwayBuilder.scaffoldBlocksFilter;
    Setting<List<Block>> blocks = iceHighwayBuilder.scaffoldBlocks;

    public boolean isActive = false;
    private int tickCounter = 0;

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        assert mc.player != null && mc.world != null;
        tickCounter++;
        Direction playerDirection;
        BlockPos playerPos = mc.player.getBlockPos();

        // Only place a block every 3 ticks to avoid rubberband
        if (tickCounter % 4 != 0) return;
        if (!isActive) return;
        if (getPlayerDirection() == null) {
            playerDirection = mc.player.getHorizontalFacing();
        } else
            playerDirection = getPlayerDirection();

        FindItemResult item = null;

        // Find a block in the player's inventory
        for (int i = 0; i < 9; i++) {
            Item foundItem = mc.player.getInventory().getStack(i).getItem();
            if (foundItem instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();

                // Check blacklist/whitelist
                if (blocksFilter.get() == ListMode.Blacklist && blocks.get().contains(block)) continue;
                if (blocksFilter.get() == ListMode.Whitelist && !blocks.get().contains(block)) continue;


                item = InvUtils.findInHotbar(itemStack -> itemStack.getItem() == Items.NETHERRACK);
                break;
            }
        }

        if (item == null) return;

        for (int offset = 1; offset <= 4; offset++) {
            BlockPos targetPos = switch (playerDirection) {
                case NORTH -> playerPos.add(0, -1, -offset);
                case SOUTH -> playerPos.add(0, -1, offset);
                case EAST -> playerPos.add(offset, -1, 0);
                case WEST -> playerPos.add(-offset, -1, 0);
                default -> null;
            };

            if (targetPos == null) return;
            if (mc.getNetworkHandler() == null) return;
            assert mc.world != null;
            if (mc.world.getBlockState(targetPos).getBlock() == Blocks.SOUL_SAND) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, targetPos, BlockUtils.getDirection(targetPos)));
                mc.player.swingHand(Hand.MAIN_HAND);
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, targetPos, BlockUtils.getDirection(targetPos)));
            }
            if (mc.world.getBlockState(targetPos).isAir()) {
                //InvUtils.swap(InvUtils.findInHotbar(itemStack -> itemStack.getItem() == Items.NETHERRACK).slot(), false);
                airPlace(Items.NETHERRACK, targetPos, Direction.DOWN);
            }
        }
    }

    public enum ListMode {
        Whitelist,
        Blacklist
    }

    public boolean isActive() {
        return isActive;
    }
    public void toggle() {
        if (isActive) {
            isActive = false;
        } else {
            isActive = true;
        }
    }
}

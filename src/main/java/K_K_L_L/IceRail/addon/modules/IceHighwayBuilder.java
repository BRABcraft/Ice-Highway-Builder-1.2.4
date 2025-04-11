package K_K_L_L.IceRail.addon.modules;

import static K_K_L_L.IceRail.addon.Utils.*;
import static K_K_L_L.IceRail.addon.modules.IceRailAutoEat.getIsEating;

import K_K_L_L.IceRail.addon.IceRail;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import java.util.*;

import static meteordevelopment.meteorclient.utils.world.BlockUtils.getPlaceSide;

import static K_K_L_L.IceRail.addon.modules.IceRailAutoReplenish.findBestBlueIceShulker;
import static K_K_L_L.IceRail.addon.modules.IceRailAutoReplenish.findBestPicksShulker;
import static K_K_L_L.IceRail.addon.modules.IceRailAutoReplenish.findPickToSwap;

public class IceHighwayBuilder extends Module {
    private int slotNumber;
    public static boolean isGoingToHighway;
    public static boolean isGoingToHole;
    private int stealingDelay = 0;
    private static BlockPos highwayCoords;
    public static boolean wasEchestFarmerActive = false;
    public static boolean baritoneCalled;
    public static Direction playerDirection;
    public static boolean isRestocking;
    public static Integer restockingType;
    public static boolean isWallDone;
    public static boolean isPause;
    public static BlockPos restockingStartPosition;
    public static boolean isPlacingShulker;
    public static Integer stacksStolen;
    public static boolean hasOpenedShulker;
    public static Integer slot_number;
    public static boolean wasRestocking;
    public static boolean stackRecentlyStolen;
    public static BlockPos shulkerBlockPos;
    public static boolean isBreakingShulker;
    public static boolean isPostRestocking;
    public static boolean isClearingInventory;
    public static boolean isProcessingTasks;
    public static int hasLookedAtShulker = 0;
    public static boolean hasQueued;
    public static Integer numberOfSlotsToSteal;
    public static int swapSlot;
    public static int trashCount;
    public static float oldYaw;

    public IceHighwayBuilder() {
        super(IceRail.CATEGORY, "ice-highway-builder", "Automated ice highway builder.");
    }

    public static Direction getPlayerDirection() {
        if (playerDirection == null) {
            return Direction.SOUTH;
        }
        return playerDirection;
    }

    // Constants
    public static Integer playerX;
    public static Integer playerY;
    public static Integer playerZ;

    private final SettingGroup sgEnable = settings.createGroup("Enable functions");
    private final SettingGroup sgAutoEat = settings.createGroup("Auto Eat");
    private final SettingGroup sgInventory = settings.createGroup("Inventory Management");
    private final SettingGroup sgIceRailNuker = settings.createGroup("Ice Rail Nuker");
    private final SettingGroup sgScaffoldGrim = settings.createGroup("Scaffold Grim");

    //Module Enabling settings
    private final Setting<Boolean> enableBlueIceMiner = sgEnable.add(new BoolSetting.Builder()
            .name("enable-blue-ice-miner")
            .description("Automatically enables Blue Ice Miner when out of blue ice.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> enablePickaxeRepairer = sgEnable.add(new BoolSetting.Builder()
            .name("enable-pickaxe-repairer")
            .description("Automatically enables Pickaxe Repairer when out of pickaxes.")
            .defaultValue(true)
            .build()
    );
    private final Setting<Boolean> enableAutoEat = sgEnable.add(new BoolSetting.Builder()
            .name("enable-auto-eat")
            .description("Pauses the current task and automatically eats.")
            .defaultValue(true)
            .build()
    );
    // Auto Eat Settings
    //<editor-fold desc="eatEGaps">
    private final Setting<Boolean> eatEGaps = sgAutoEat.add(new BoolSetting.Builder()
            .name("eat-egap-when-burning")
            .description("Eats an enchanted golden apple if the player is burning.")
            .defaultValue(true)
            .visible(enableAutoEat::get)
            .build()
    );
    //</editor-fold>
    //<editor-fold desc="disableAutoEatAfterDigging">
    private final Setting<Boolean> disableAutoEatAfterDigging = sgAutoEat.add(new BoolSetting.Builder()
            .name("disable-auto-eat-after-digging")
            .description("Disables Auto Eat when \"Ice Highway Builder\" is disabled.")
            .defaultValue(true)
            .visible(enableAutoEat::get)
            .build()
    );
    //</editor-fold>

    //Inventory Management Settings
    //<editor-fold desc="openAndCloseInventory">
    private final Setting<Boolean> openAndCloseInventory = sgInventory.add(new BoolSetting.Builder()
            .name("open-and-close-inventory")
            .description("Whether to open and close inventory on activated or not. Enable this if \"Ice Rail Auto Replenish\" doesn't work sometimes.")
            .defaultValue(true)
            .build()
    );
    //</editor-fold>
    //<editor-fold desc="throwBlacklist">
    private final Setting<List<Item>> throwBlacklist = sgInventory.add(new ItemListSetting.Builder()
         .name("throw-blacklist")
         .description("Items you don't want to throw.")
         .defaultValue(
                 Items.SHULKER_BOX,
                 Items.WHITE_SHULKER_BOX,
                 Items.ORANGE_SHULKER_BOX,
                 Items.MAGENTA_SHULKER_BOX,
                 Items.LIGHT_BLUE_SHULKER_BOX,
                 Items.YELLOW_SHULKER_BOX,
                 Items.LIME_SHULKER_BOX,
                 Items.PINK_SHULKER_BOX,
                 Items.GRAY_SHULKER_BOX,
                 Items.LIGHT_GRAY_SHULKER_BOX,
                 Items.CYAN_SHULKER_BOX,
                 Items.PURPLE_SHULKER_BOX,
                 Items.BLUE_SHULKER_BOX,
                 Items.BROWN_SHULKER_BOX,
                 Items.GREEN_SHULKER_BOX,
                 Items.RED_SHULKER_BOX,
                 Items.BLACK_SHULKER_BOX,
                 Items.ENCHANTED_GOLDEN_APPLE,
                 Items.COOKED_BEEF,
                 Items.COOKED_CHICKEN,
                 Items.COOKED_MUTTON,
                 Items.COOKED_COD,
                 Items.COOKED_PORKCHOP,
                 Items.ENDER_CHEST,
                 Items.GOLDEN_CARROT,
                 Items.GOLDEN_BOOTS,
                 Items.GOLDEN_HELMET,
                 Items.OBSIDIAN,
                 Items.BLUE_ICE,
                 Items.FLINT_AND_STEEL,
                 Items.DIAMOND_PICKAXE,
                 Items.NETHERITE_PICKAXE,
                 Items.DIAMOND_AXE,
                 Items.NETHERITE_AXE,
                 Items.DIAMOND_SHOVEL,
                 Items.NETHERITE_SHOVEL,
                 Items.DIAMOND_SWORD,
                 Items.NETHERITE_SWORD,
                 Items.DIAMOND_HELMET,
                 Items.DIAMOND_CHESTPLATE,
                 Items.DIAMOND_LEGGINGS,
                 Items.DIAMOND_BOOTS,
                 Items.NETHERITE_HELMET,
                 Items.NETHERITE_CHESTPLATE,
                 Items.NETHERITE_LEGGINGS,
                 Items.NETHERITE_BOOTS,
                 Items.END_CRYSTAL,
                 Items.TOTEM_OF_UNDYING,
                 Items.EXPERIENCE_BOTTLE,
                 Items.CRAFTING_TABLE,
                 Items.FIREWORK_ROCKET,
                 Items.ELYTRA
         )
         .build()
    );
    //</editor-fold>
    //Nuker settings
    //<editor-fold desc="Nuker settings">
    public final Setting<Integer> nukerDelay = sgIceRailNuker.add(new IntSetting.Builder()
            .name("delay")
            .description("Delay in ticks between breaking blocks.")
            .defaultValue(0)
            .build()
    );

    public final Setting<Integer> nukerMaxBlocksPerTick = sgIceRailNuker.add(new IntSetting.Builder()
            .name("max-blocks-per-tick")
            .description("Maximum blocks to try to break per tick. Useful when insta mining.")
            .defaultValue(3)
            .min(1)
            .sliderRange(1, 6)
            .build()
    );

    public final Setting<Boolean> nukerSwingHand = sgIceRailNuker.add(new BoolSetting.Builder()
            .name("swing-hand")
            .description("Swing hand client side.")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> nukerPacketMine = sgIceRailNuker.add(new BoolSetting.Builder()
            .name("packet-mine")
            .description("Attempt to instamine everything at once.")
            .defaultValue(true)
            .build()
    );

    public final Setting<Boolean> nukerRotate = sgIceRailNuker.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Rotates server-side to the block being mined.")
            .defaultValue(false)
            .build()
    );

    public final Setting<IceRailNuker.ListMode> nukerListMode = sgIceRailNuker.add(new EnumSetting.Builder<IceRailNuker.ListMode>()
            .name("list-mode")
            .description("Selection mode.")
            .defaultValue(IceRailNuker.ListMode.Blacklist)
            .build()
    );

    public final Setting<List<Block>> nukerBlacklist = sgIceRailNuker.add(new BlockListSetting.Builder()
            .name("blacklist")
            .description("The blocks you don't want to mine.")
            .defaultValue(Blocks.BLUE_ICE)
            .visible(() -> nukerListMode.get() == IceRailNuker.ListMode.Blacklist)
            .build()
    );

    public final Setting<List<Block>> nukerWhitelist = sgIceRailNuker.add(new BlockListSetting.Builder()
            .name("whitelist")
            .description("The blocks you want to mine.")
            .visible(() -> nukerListMode.get() == IceRailNuker.ListMode.Whitelist)
            .build()
    );

    public final Setting<Boolean> nukerEnableRenderBreaking = sgIceRailNuker.add(new BoolSetting.Builder()
            .name("broken-blocks")
            .description("Enable rendering broken blocks.")
            .defaultValue(true)
            .build()
    );

    public final Setting<ShapeMode> shapeModeBreak = sgIceRailNuker.add(new EnumSetting.Builder<ShapeMode>()
            .name("nuke-block-mode")
            .description("How the shapes for broken blocks are rendered.")
            .defaultValue(ShapeMode.Both)
            .visible(nukerEnableRenderBreaking::get)
            .build()
    );

    public final Setting<SettingColor> sideColor = sgIceRailNuker.add(new ColorSetting.Builder()
            .name("side-color")
            .description("The side color of the target block rendering.")
            .defaultValue(new SettingColor(255, 0, 0, 80))
            .visible(nukerEnableRenderBreaking::get)
            .build()
    );

    public final Setting<SettingColor> lineColor = sgIceRailNuker.add(new ColorSetting.Builder()
            .name("line-color")
            .description("The line color of the target block rendering.")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .visible(nukerEnableRenderBreaking::get)
            .build()
    );
    //</editor-fold>
    //ScaffoldGrim settings
    public final Setting<ScaffoldGrim.ListMode> scaffoldBlocksFilter = sgScaffoldGrim.add(new EnumSetting.Builder<ScaffoldGrim.ListMode>()
            .name("blocks-filter")
            .description("How to use the block list setting")
            .defaultValue(ScaffoldGrim.ListMode.Blacklist)
            .build()
    );
    //<editor-fold desc="scaffoldBlocks">
    public final Setting<List<Block>> scaffoldBlocks = sgScaffoldGrim.add(new BlockListSetting.Builder()
            .name("blocks")
            .description("Selected blocks.")
            .defaultValue(
                    Blocks.BLUE_ICE,
                    Blocks.ENDER_CHEST,
                    Blocks.SHULKER_BOX,
                    Blocks.WHITE_SHULKER_BOX,
                    Blocks.ORANGE_SHULKER_BOX,
                    Blocks.MAGENTA_SHULKER_BOX,
                    Blocks.LIGHT_BLUE_SHULKER_BOX,
                    Blocks.YELLOW_SHULKER_BOX,
                    Blocks.LIME_SHULKER_BOX,
                    Blocks.PINK_SHULKER_BOX,
                    Blocks.GRAY_SHULKER_BOX,
                    Blocks.LIGHT_GRAY_SHULKER_BOX,
                    Blocks.CYAN_SHULKER_BOX,
                    Blocks.PURPLE_SHULKER_BOX,
                    Blocks.BLUE_SHULKER_BOX,
                    Blocks.BROWN_SHULKER_BOX,
                    Blocks.GREEN_SHULKER_BOX,
                    Blocks.RED_SHULKER_BOX,
                    Blocks.BLACK_SHULKER_BOX
            )
            .build()
    );
    //</editor-fold>

    public Direction getPlayerCurrentDirection() {
        assert mc.player != null;
        return mc.player.getHorizontalFacing();
    }

    public static BlockPos getHighwayCoords() {
        return highwayCoords;
    }

    public static void setHighwayCoords(BlockPos value) {
        highwayCoords = value;
    }

    private void initializeRequiredVariables() {
        assert mc.player != null;
        highwayCoords = mc.player.getBlockPos();
        playerX = mc.player.getBlockX();
        playerY = mc.player.getBlockY();
        playerZ = mc.player.getBlockZ();
    }

    @Override
    public void onActivate() {
        if (!validateInitialConditions()) {
            toggle();
            return;
        }
        if (mc.player.getWorld().getRegistryKey() != World.NETHER) {
            error("Player must be in the nether. Did you mean to enable Blue Ice Miner or Pickaxe Repairer?");
            toggle();
            return;
        }
        initializeRequiredVariables();
        enableRequiredModules();

        if (openAndCloseInventory.get())
            openAndCloseInventory();

        assert mc.player != null;
        playerDirection = getPlayerCurrentDirection();
    }

    private boolean validateInitialConditions() {
        return mc.player != null && mc.world != null;
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null || mc.world == null) return;

        Direction direction = mc.player.getHorizontalFacing();
        if (direction == null) return;
        switch (direction) {
            case Direction.NORTH:
                mc.player.setYaw(180);
                break;
            case Direction.SOUTH:
                mc.player.setYaw(0);
                break;
            case Direction.EAST:
                mc.player.setYaw(-90);
                break;
            case Direction.WEST:
                mc.player.setYaw(90);
                break;
        }
        mc.player.setPitch(0);
        cancelCurrentProcessBaritone();
        disableAllModules();
        releaseForward();
        resetState();
        shutdownScheduler1();

    }

    private void resetState() {
        wasEchestFarmerActive = false;
        baritoneCalled = false;
        playerX = null;
        playerY = null;
        playerZ = null;
        highwayCoords = null;
        isGoingToHighway = false;
        isGoingToHole = false;
        playerDirection = null;
        releaseForward();

        // Shulker Interactions
        isRestocking = false;
        restockingType = 0; // 0=Blue ice 1=Pickaxes
        isWallDone = false;
        isPause = false;
        restockingStartPosition = null;
        isPlacingShulker = false;
        stacksStolen = 0;
        hasOpenedShulker = false;
        slot_number = 0;
        wasRestocking = false;
        shulkerBlockPos = null;
        isBreakingShulker = true;
        isPostRestocking = false;
        isProcessingTasks = false;
        hasLookedAtShulker = 0;
        hasQueued = false;
        stealingDelay = 0;
        swapSlot = -1;
        isClearingInventory = false;
        oldYaw = 0;
        stackRecentlyStolen = false;
    }

    private void steal(ScreenHandler handler, int slot_number) {
        MeteorExecutor.execute(() -> moveSlots(handler, slot_number));
    }

    private void moveSlots(ScreenHandler handler, int i) {
        if (handler.getSlot(i).hasStack() && Utils.canUpdate()) {
            InvUtils.shiftClick().slotId(i);
            stacksStolen++;
            stackRecentlyStolen = true;
        }
    }

    private void handleRestocking() {
        if (isGatheringItems()) {
            isRestocking = false;
            return;
        }

        assert mc.player != null;
        assert mc.world != null;

        if (!restockingStartPosition.equals(mc.player.getBlockPos())) {
            if (!isPause) {
                isPause = true;
                return;
            }
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(restockingStartPosition));
            resumeBaritone();
        } else {
            isPause = false;
        }

        if (isPause) {
            return;
        }

        shulkerBlockPos = getBlockPos();

        if (hasLookedAtShulker < 10) { // To add a 10 tick delay
            if (hasLookedAtShulker == 0) {
                if (restockingType == 0) {
                    InvUtils.swap(8, false);
                } else {
                    InvUtils.swap(7, false);
                }
                lookAtBlock(shulkerBlockPos.withY(playerY - 1)); // To minimize the chance of the shulker being placed upside down
            }   

            hasLookedAtShulker++;
            return;
        }

        if (!(mc.world.getBlockState(shulkerBlockPos).getBlock() instanceof ShulkerBoxBlock)) {
            if (BlockUtils.canPlace(shulkerBlockPos, false) && !BlockUtils.canPlace(shulkerBlockPos, true)) return;
            if (restockingType == 0) {
                place(shulkerBlockPos, Hand.MAIN_HAND, 8, true, true, true);
            } else {
                place(shulkerBlockPos, Hand.MAIN_HAND, 7, true, true, true);
            }
            return;
        }

        if (!hasOpenedShulker) {
            mc.setScreen(null);
            // Open the shulker
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(shulkerBlockPos), Direction.DOWN,
                            shulkerBlockPos, false), 0));

            mc.setScreen(null);
            hasOpenedShulker = true;
            return;
        }

        mc.setScreen(null);
        if (stacksStolen == null) {
            stacksStolen = 0;
        }
        if ((stacksStolen >= numberOfSlotsToSteal)
                || stacksStolen >= 27 // No more slots left (numberOfSlotsToSteal > stacksStolen because the shulker may not have the full amount)
            // Worst case scenario is 6.75 seconds or 135 ticks
        ) {
            // Run post restocking
            isPostRestocking = true;

            stacksStolen = 0;
            slotNumber = 0;
            
            wasRestocking = true;

            isPause = false;
            isPlacingShulker = false;
            restockingStartPosition = null;
            hasLookedAtShulker = 0;
            stealingDelay = 0;
            hasOpenedShulker = false;
            isRestocking = false;
        } else {
            ScreenHandler handler = mc.player.currentScreenHandler;
            if (stackRecentlyStolen) {
                if (stealingDelay < 5) { // To add a 5 tick delay
                    stealingDelay++;
                    return;
                }
                stackRecentlyStolen = false;
            }
            if (restockingType == 1) {
                InvUtils.quickSwap().fromId(0).toId(slotNumber);
                stacksStolen++;
            }
            else {
                steal(handler, slotNumber);
                slotNumber++;
                stealingDelay = 0;
            }
        }
    }

    private @NotNull BlockPos getBlockPos() {
        assert mc.player != null;
        int offset = 2;
        return switch (getPlayerDirection()) {
            case NORTH -> new BlockPos(playerX, playerY, mc.player.getBlockZ() - offset);
            case SOUTH -> new BlockPos(playerX, playerY, mc.player.getBlockZ() + offset);
            case EAST -> new BlockPos(mc.player.getBlockX() + offset, playerY, playerZ);
            case WEST -> new BlockPos(mc.player.getBlockX() - offset, playerY, playerZ);
            default -> new BlockPos(0, 0, 0); // This shouldn't happen.
        };
    }

    private void dropSlot(int slot3) {
        assert mc.player != null;
        if (!throwBlacklist.get().contains(mc.player.getInventory().getStack(slot3).getItem())) {
            InvUtils.drop().slot(slot3);
        } else {
            int slot2 = 9;
            for (int j = 9; j < 36; j++) {
                if (!throwBlacklist.get().contains(mc.player.getInventory().getStack(slot3).getItem())) {
                    slot2 = j;
                    break;
                }
            }
            InvUtils.quickSwap().fromId(slot3).toId(slot2);
        }
    }
    public void handleClearInventory() {
        if (isRestocking || isPostRestocking)
            return;
        assert mc.player != null && mc.world != null;
        if (stealingDelay == 0) {
            if (!mc.world.getBlockState(getBlockPos()).isAir() 
            && !(mc.world.getBlockState(getBlockPos()).getBlock() instanceof ShulkerBoxBlock)
            && !hasOpenedShulker) {
                BlockUtils.breakBlock(getBlockPos(), true);
            }
        }
        if (stealingDelay < 3) {
            stealingDelay ++;
            return;
        }
        trashCount = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (!itemStack.isEmpty() && !throwBlacklist.get().contains(itemStack.getItem())) {
                trashCount ++;
            }
        }
        if (stealingDelay > 6) {stealingDelay = 0;} else {stealingDelay++;}
        if (trashCount > 1) {
            mc.player.setPitch(-30);
            mc.player.setYaw(oldYaw - 180);
            for (int i = 2; i < 36; i++) {
                ItemStack itemStack = mc.player.getInventory().getStack(i);
                if (!itemStack.isEmpty() && !throwBlacklist.get().contains(itemStack.getItem()) && (trashCount > 1)) {
                    if (mc.player.getInventory().getStack(6).isEmpty()) {
                        InvUtils.move().from(6).to(i);
                        if (i < 9) {
                            InvUtils.swap(i, false);
                        } else {
                            InvUtils.swap(6, false);
                        }
                    }
                    if (stealingDelay == 5) {
                        if (i < 9) {
                            dropSlot(i);
                        } else {
                            dropSlot(6);
                        }
                    }
                    return;
                }
            }
        } else {
            mc.player.setYaw(oldYaw);
            if (restockingType == 0) {
                swapSlot = -1;
                numberOfSlotsToSteal = countEmptySlots();
            } else {
                swapSlot = findPickToSwap(mc.player.getInventory().getStack(7));
                numberOfSlotsToSteal = 1;
            }
            if (restockingType < 2) {
                isRestocking = true;
                isPlacingShulker = true;
            } else {
                BlueIceMiner.state = BlueIceMiner.NewState;
                if (BlueIceMiner.state.equals("waitingForGather")) {
                    IceRailGatherItem(Items.OBSIDIAN);
                }
            }
            isClearingInventory = false;
        }
    }

    private void handlePostRestocking() {
        if (isRestocking)
            return;

        assert mc.player != null;
        if (wasRestocking && !isPostRestocking) {
            setKeyPressed(mc.options.inventoryKey, true);
            isPostRestocking = true;
            return;
        }

        if (isPostRestocking) {
            assert mc.world != null;
            isBreakingShulker = true;
            if (mc.world.getBlockState(shulkerBlockPos).getBlock() instanceof ShulkerBoxBlock) {
                if (PlayerUtils.isWithinReach(shulkerBlockPos)) {
                    if (BlockUtils.breakBlock(shulkerBlockPos, true))
                        return;
                }
            }

            isBreakingShulker = false;
            Item[] items = getShulkerBoxesNearby();

            if (areShulkerBoxesNearby()) {
                for (Item item : items) {
                    if (!isGatheringItems()) {
                        if (BlueIceMiner.state.equals("waitingForPostRestock")) {
                            BlueIceMiner.state = "waitingForGather";
                            BlueIceMiner.scanningWorld = true;
                        }
                        IceRailGatherItem(item);
                        return;
                    }
                    return;
                }
            }

            if ((mc.world.getBlockState(shulkerBlockPos).getBlock().equals(Blocks.AIR))
                    && !isGatheringItems()
                    && !isRestocking
                    && !isBreakingShulker
                    && !isProcessingTasks) {
                isPlacingShulker = false;
                wasRestocking = false;
                setKeyPressed(mc.options.inventoryKey, false);
                isPostRestocking = false;
                isProcessingTasks = false;
                hasQueued = false;
                if (BlueIceMiner.state.equals("waitingForPostRestock")) {
                    BlueIceMiner.state = "idle";
                    BlueIceMiner.scanningWorld = true;
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || playerX == null || playerY == null || playerZ == null) return;
        boolean walkForward;

        Module iceRailAutoEat = Modules.get().get("ice-rail-auto-eat");
        Module iceRailNuker = Modules.get().get("ice-rail-nuker");
        Module icePlacer = Modules.get().get("ice-placer");

        BlueIceMiner object = new BlueIceMiner();
        if (object.getIsPathing()) {
            return;
        }
        if (iceRailAutoEat != null) {
            BoolSetting amountSetting = (BoolSetting) iceRailAutoEat.settings.get("eat-egap-when-burning");
            if (amountSetting != null) amountSetting.set(eatEGaps.get());
        }

        if (isClearingInventory) {
            handleClearInventory();
            releaseForward();
            return;
        }
        if (isGoingToHighway) {
            handleInvalidPosition(0);
            return;
        }

        if (mc.player.getBlockY() == playerY) {
            setHWCoords(0);
        }

        if (!isPlayerInValidPosition()) {
            isGoingToHighway = true;
            return;
        }

        if (isHolesInIce()) {
            setHWCoords(1);
            handleInvalidPosition(1);
            return;
        }

        if (isPostRestocking) {
            handlePostRestocking();
            releaseForward();
            return;
        }

        if (isRestocking) {
            handleRestocking();
            releaseForward();
            return;
        }

        if (countItems(Items.BLUE_ICE) <= 8) {
            ItemStack BlueIceShulker = findBestBlueIceShulker();

            if (BlueIceShulker == null && !isPlacingShulker) {
                if (BlueIceMiner.state.equals("idle")) {
                    releaseForward();
                    BlueIceMiner.state = "goToPortal";
                    BlueIceMiner.scanningWorld = true;
                }
                return;
            }

            if (isGatheringItems()
                    || isRestocking) return;

            restockingStartPosition = mc.player.getBlockPos();
            if (icePlacer.isActive()) {
                if (getIsEating()) { // Toggle off
                    icePlacer.toggle();
                    iceRailNuker.toggle();
                }
            }

            restockingType = 0;
            slotNumber = 0;
            stealingDelay = 0;
            // Initiate inventory clear
            oldYaw = mc.player.getYaw();
            isClearingInventory = true;
            return;
        }

        if (countUsablePickaxes() == 0) {
            ItemStack PicksShulker = findBestPicksShulker();

            if (PicksShulker == null && !isPlacingShulker) {
                error("Insufficient materials. Need: 1 diamond/netherite, >50 durability pickaxe.");
                toggle();
                return;
            }

            if (isGatheringItems()
                    || isRestocking) return;

            restockingStartPosition = mc.player.getBlockPos();
            if (icePlacer.isActive()) {
                if (getIsEating()) { // Toggle off
                    icePlacer.toggle();
                    iceRailNuker.toggle();
                }
            }

            restockingType = 1;
            isPlacingShulker = true;
            numberOfSlotsToSteal = 1;
            swapSlot = findPickToSwap(mc.player.getInventory().getStack(7));
            slotNumber = swapSlot;
            // Initiate inventory clear
            stealingDelay = 0;
            oldYaw = mc.player.getYaw();
            isClearingInventory = true;
            return;
        }

        lockRotation();

        if (icePlacer.isActive()) { // Toggle off
            icePlacer.toggle();
            iceRailNuker.toggle();
        } else {
            if (!getIsEating()) { // Toggle on
                icePlacer.toggle();
                iceRailNuker.toggle();
            }
        }

        walkForward = !getIsEating();

        if (needsToScaffold()) {
            boolean isAirInFront = false;

            switch (getPlayerDirection()) {
                case NORTH ->
                        isAirInFront = mc.world.getBlockState(new BlockPos(playerX, playerY - 1, playerZ - 1)).getBlock() == Blocks.AIR &&
                                mc.world.getBlockState(new BlockPos(playerX, playerY - 1, playerZ - 2)).getBlock() == Blocks.AIR;
                case SOUTH ->
                        isAirInFront = mc.world.getBlockState(new BlockPos(playerX, playerY - 1, playerZ + 1)).getBlock() == Blocks.AIR &&
                                mc.world.getBlockState(new BlockPos(playerX, playerY - 1, playerZ + 2)).getBlock() == Blocks.AIR;
                case EAST ->
                        isAirInFront = mc.world.getBlockState(new BlockPos(playerX + 1, playerY - 1, playerZ)).getBlock() == Blocks.AIR &&
                                mc.world.getBlockState(new BlockPos(playerX + 2, playerY - 1, playerZ)).getBlock() == Blocks.AIR;
                case WEST ->
                        isAirInFront = mc.world.getBlockState(new BlockPos(playerX - 1, playerY - 1, playerZ)).getBlock() == Blocks.AIR &&
                                mc.world.getBlockState(new BlockPos(playerX - 2, playerY - 1, playerZ)).getBlock() == Blocks.AIR;
            }

            if (isAirInFront)
                walkForward = false;
        } else
            walkForward = !getIsEating();

        if (!walkForward) {
            setKeyPressed(mc.options.forwardKey, false);
            setKeyPressed(mc.options.rightKey, false);
            setKeyPressed(mc.options.leftKey, false);
            return;
        }

        setKeyPressed(mc.options.forwardKey, true); // W
        if (getPlayerDirection() == Direction.EAST || getPlayerDirection() == Direction.SOUTH)
            setKeyPressed(mc.options.rightKey, true);    // D
        else
            setKeyPressed(mc.options.leftKey, true);    // A
    }

    public static void lookAtBlock(BlockPos blockPos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d hitPos = Vec3d.ofCenter(blockPos);

        Direction side = getPlaceSide(blockPos);
        if (side != null) {
            blockPos.offset(side);
            hitPos = hitPos.add(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());
        }
        assert mc.player != null;
        mc.player.setYaw((float) Rotations.getYaw(hitPos));
        mc.player.setPitch((float) Rotations.getPitch(hitPos));
    }

    public static boolean needsToScaffold() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || playerX == null || playerY == null || playerZ == null)
            return false;

        int startOffset, endOffset;

        switch (getPlayerDirection()) {
            case NORTH -> {
                startOffset = -2;
                endOffset = 5;
                for (int offset = startOffset; offset <= endOffset; offset++) {
                    if (mc.world.getBlockState(new BlockPos(playerX, playerY - 1, mc.player.getBlockZ() + offset)).getBlock() == Blocks.AIR) {
                        return true;
                    }
                }
            }
            case SOUTH -> {
                startOffset = -5;
                endOffset = 2;
                for (int offset = startOffset; offset <= endOffset; offset++) {
                    if (mc.world.getBlockState(new BlockPos(playerX, playerY - 1, mc.player.getBlockZ() + offset)).getBlock() == Blocks.AIR) {
                        return true;
                    }
                }
            }
            case EAST -> {
                startOffset = -2;
                endOffset = 5;
                for (int offset = startOffset; offset <= endOffset; offset++) {
                    if (mc.world.getBlockState(new BlockPos(mc.player.getBlockX() + offset, playerY - 1, playerZ)).getBlock() == Blocks.AIR) {
                        return true;
                    }
                }
            }
            case WEST -> {
                startOffset = -5;
                endOffset = 2;
                for (int offset = startOffset; offset <= endOffset; offset++) {
                    if (mc.world.getBlockState(new BlockPos(mc.player.getBlockX() + offset, playerY - 1, playerZ)).getBlock() == Blocks.AIR) {
                        return true;
                    }
                }
            }
            default -> {
                return false;
            }
        }
        return false;
    }

    private boolean isPlayerInValidPosition() {
        assert mc.player != null;
        switch (getPlayerDirection()) {
            case NORTH, SOUTH -> {
                return mc.player.getBlockY() == playerY &&
                        mc.player.getBlockX() == playerX;
            }
            case EAST, WEST -> {
                return mc.player.getBlockY() == playerY &&
                        mc.player.getBlockZ() == playerZ;
            }
        }

        return false;
    }

    private boolean isHolesInIce() {
        Direction direction = getPlayerDirection();
        assert mc.player != null;
        if (direction == null) {
            return false;
        }
        int airBlocks = 0;
        BlockPos block1 = null;
        int startBlock = getStartBlock(direction);
        for (int i = 1; i <= 3; i++) {
            switch (direction) {
                case WEST -> block1 = new BlockPos(startBlock + i * 2, playerY+1, playerZ - 1);
                case EAST -> block1 = new BlockPos(startBlock - i * 2, playerY+1, playerZ - 1);
                case NORTH -> block1 = new BlockPos(playerX + 1, playerY+1, startBlock + i * 2);
                case SOUTH -> block1 = new BlockPos(playerX + 1, playerY+1, startBlock - i * 2);

            }
            assert mc.world != null;
            if (Blocks.BLUE_ICE != mc.world.getBlockState(block1).getBlock()) {
                airBlocks++;
            }
        }
        return airBlocks > 0 && airBlocks < 3;
    }

    private int getStartBlock(Direction direction) {
        int startBlock = 0;
        assert mc.player != null;
        switch (direction) {
            case NORTH -> {
                if (Math.abs(mc.player.getBlockZ()) % 2 == 0) {
                    startBlock = mc.player.getBlockZ();
                } else {
                    startBlock = mc.player.getBlockZ() + 1;
                }
            }
            case SOUTH -> {
                if (Math.abs(mc.player.getBlockZ()) % 2 == 0) {
                    startBlock = mc.player.getBlockZ();
                } else {
                    startBlock = mc.player.getBlockZ() - 1;
                }
            }
            case WEST -> {
                if (Math.abs(mc.player.getBlockX()) % 2 == 0) {
                    startBlock = mc.player.getBlockX();
                } else {
                    startBlock = mc.player.getBlockX() + 1;
                }
            }
            case EAST -> {
                if (Math.abs(mc.player.getBlockX()) % 2 == 0) {
                    startBlock = mc.player.getBlockX();
                } else {
                    startBlock = mc.player.getBlockX() - 1;
                }
            }
        }
        return startBlock;
    }

    private void handleInvalidPosition(int Type) {
        assert mc.player != null;
        BlockPos target;

        target = getHighwayCoords();

        if (getHighwayCoords() == null) {
            setHWCoords(Type);
            target = getHighwayCoords();
        }

        if (target == null) return;

        if (mc.player.getBlockPos() != target) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(target));
            resumeBaritone();
        }

        if (hasReachedLocation(mc.player, target)) {
            isGoingToHighway = false;
        }
    }

    private void lockRotation() {
        assert mc.player != null;
        mc.player.setPitch(0);

        Direction direction = getPlayerDirection();

        switch (direction) {
            case Direction.NORTH:
                mc.player.setYaw(135);
                break;
            case Direction.EAST:
                mc.player.setYaw(-135);
                break;
            case Direction.WEST:
                mc.player.setYaw(45);
                break;
            case Direction.SOUTH:
                mc.player.setYaw(-45);
                break;
        }
    }

    private void enableRequiredModules() {
        Module iceRailAutoEat = Modules.get().get("ice-rail-auto-eat");
        Module iceRailAutoReplenish = Modules.get().get("ice-rail-auto-replenish");
        Module scaffoldGrim = Modules.get().get("scaffold-grim");
        if (enableAutoEat.get() && !iceRailAutoEat.isActive()) iceRailAutoEat.toggle();

        if (iceRailAutoReplenish != null && !iceRailAutoReplenish.isActive())
            iceRailAutoReplenish.toggle();

        if (!scaffoldGrim.isActive()) scaffoldGrim.toggle();
    }

    private void disableAllModules() {
        String[] modulesToDisable = {
                "ice-rail-gather-item",
                "ice-placer",
                "ice-rail-auto-replenish",
                "ice-rail-nuker",
                "scaffold-grim"
        };

        for (String moduleName : modulesToDisable) {
            Module module = Modules.get().get(moduleName);
            if (module != null && module.isActive()) {
                module.toggle();
            }
        }

        Module iceRailAutoEat = Modules.get().get("ice-rail-auto-eat");
        if (disableAutoEatAfterDigging.get() && iceRailAutoEat.isActive())
            iceRailAutoEat.toggle();
    }
    public Setting<List<Item>> getBlacklist(){
        return throwBlacklist;
    }
}

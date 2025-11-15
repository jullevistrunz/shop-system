package de.jullevistrunz;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.scoreboard.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ShopSystem implements ModInitializer {
	public static final String MOD_ID = "shop-system";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final int HOURLY_EARNINGS_DEFAULT = 20;
    private final int HOURLY_EARNINGS_20K = 10;
    private final int HOURLY_EARNINGS_30k = 5;
    private final int HOURLY_EARNINGS_50k = 0;

    private final float vat = 0.1f;

	@Override
	public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(minecraftServer -> {
            Scoreboard scoreboard = minecraftServer.getScoreboard();

            if (!scoreboard.getObjectiveNames().contains("credits")) {
                Text[] creditsDisplayTextArr = {
                        Text.literal("$").withColor(4045567),
                        Text.literal(" Credits ").withColor(16777215),
                        Text.literal("$").withColor(4045567)
                };

                scoreboard.addObjective("credits", ScoreboardCriterion.DUMMY, Helper.textBuilder(creditsDisplayTextArr), ScoreboardCriterion.RenderType.INTEGER, true, null);
            }

            if (!scoreboard.getObjectiveNames().contains("playerTick")) {
                scoreboard.addObjective("playerTick", ScoreboardCriterion.DUMMY, Text.empty(), ScoreboardCriterion.RenderType.INTEGER, true, null);
            }

            if (!scoreboard.getObjectiveNames().contains("receivedStartCredits")) {
                scoreboard.addObjective("receivedStartCredits", ScoreboardCriterion.DUMMY, Text.empty(), ScoreboardCriterion.RenderType.INTEGER, true, null);
            }

            if (!scoreboard.getObjectiveNames().contains("fund")) {
                scoreboard.addObjective("fund", ScoreboardCriterion.DUMMY, Text.empty(), ScoreboardCriterion.RenderType.INTEGER, true, null);
            }

            if (!scoreboard.getObjectiveNames().contains("fundDonations")) {
                Text[] fundDonationsDisplayTextArr = {
                        Text.literal("$").withColor(4045567),
                        Text.literal(" Fund ").withColor(16777215),
                        Text.literal("$").withColor(4045567)
                };

                scoreboard.addObjective("fundDonations", ScoreboardCriterion.DUMMY, Helper.textBuilder(fundDonationsDisplayTextArr),
                        ScoreboardCriterion.RenderType.INTEGER, true, null);
            }
        });

        ServerPlayerEvents.JOIN.register(player -> {
            ScoreAccess creditsScore = Helper.getScoreAccess("credits", player);
            if (creditsScore == null) {
                LOGGER.error("Credits score not found on player connection!");
                return;
            }

            ScoreAccess receivedStartCreditsScore = Helper.getScoreAccess("receivedStartCredits", player);
            if (receivedStartCreditsScore == null) {
                LOGGER.error("ReceivedStartCredits score not found on player connection!");
                return;
            }

            if (receivedStartCreditsScore.getScore() == 0) {
                creditsScore.setScore(200);
                receivedStartCreditsScore.setScore(1);
            }
        });

        final Dictionary<PlayerEntity, Vec3d> lastPositions = new Hashtable<>();
        ServerTickEvents.START_SERVER_TICK.register(minecraftServer -> {
            for (PlayerEntity player : minecraftServer.getPlayerManager().getPlayerList()) {
                boolean moved = lastPositions.get(player) != null && !Objects.equals(lastPositions.get(player), player.getPos());
                lastPositions.put(player, player.getPos());

                ScoreAccess playerTickScore = Helper.getScoreAccess("playerTick", player);
                if (!moved || playerTickScore == null) continue;

                playerTickScore.setScore(playerTickScore.getScore() + 1);

                if (playerTickScore.getScore() < 72000) continue;

                ScoreAccess creditsScore = Helper.getScoreAccess("credits", player);
                if (creditsScore == null) continue;

                int totalCredits = Helper.getTotalCredits(minecraftServer);

                int currentHourlyEarnings = HOURLY_EARNINGS_DEFAULT;

                if (totalCredits >= 50000) currentHourlyEarnings = HOURLY_EARNINGS_50k;
                else if (totalCredits >= 30000) currentHourlyEarnings = HOURLY_EARNINGS_30k;
                else if (totalCredits >= 20000) currentHourlyEarnings = HOURLY_EARNINGS_20K;

                playerTickScore.setScore(0);

                if (currentHourlyEarnings == 0) continue;

                creditsScore.setScore(creditsScore.getScore() + currentHourlyEarnings);

                Text[] messageArr = {
                        Text.literal("You received ").withColor(5635925),
                        Text.literal("$" + currentHourlyEarnings).withColor(4045567)
                };
                player.sendMessage(Helper.textBuilder(messageArr), false);
            }
        });

        CommandRegistrationCallback.EVENT.register((commandDispatcher, commandRegistryAccess, registrationEnvironment) -> {
            LiteralArgumentBuilder<ServerCommandSource> parent = CommandManager.literal("shop");

            parent.then(CommandManager.literal("credits")
                    .executes(Commands::executeCreditsCommand));

            parent.then(CommandManager.literal("myCredits")
                    .executes(Commands::executeMyCreditsCommand));

            parent.then(CommandManager.literal("pay")
                    .then(CommandManager.argument("recipient", EntityArgumentType.player())
                            .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                .executes(Commands::executePayCommand))));

            parent.then(CommandManager.literal("sign")
                    .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                            .then(CommandManager.argument("price", IntegerArgumentType.integer(1))
                                    .then(CommandManager.argument("stackSize", IntegerArgumentType.integer(1, 64))
                                            .executes(context -> Commands.executeSignCommand(
                                                    null,
                                                    context
                                                    )
                                            )
                                            .then(CommandManager.argument("partner", EntityArgumentType.player())
                                                    .executes(context -> Commands.executeSignCommand(
                                                            EntityArgumentType.getPlayer(context, "partner"),
                                                            context
                                                            )
                                                    ))))));

            parent.then(CommandManager.literal("totalCredits")
                    .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(3))
                    .executes(Commands::executeTotalCreditsCommand));

            LiteralArgumentBuilder<ServerCommandSource> fund = CommandManager.literal("fund");

            fund.then(CommandManager.literal("add")
                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                            .executes(Commands::executeFundAddCommand)));

            fund.then(CommandManager.literal("balance")
                    .executes(Commands::executeFundBalanceCommand));

            fund.then(CommandManager.literal("payout")
                    .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(3))
                    .then(CommandManager.argument("recipient", EntityArgumentType.player())
                            .executes(Commands::executeFundPayoutCommand)));

            parent.then(fund);

            commandDispatcher.register(parent);
        });

        UseBlockCallback.EVENT.register((playerEntity, world, hand, blockHitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            BlockPos pos = blockHitResult.getBlockPos();
            BlockEntity blockEntity = world.getBlockEntity(pos);
            BlockState blockState = world.getBlockState(pos);
            if (blockEntity instanceof SignBlockEntity sign && Helper.isShopSign(sign) && blockState.getBlock() instanceof WallSignBlock) {
                Direction facing = blockState.get(WallSignBlock.FACING);

                BlockPos chestPos;
                switch (facing) {
                    case NORTH -> chestPos = pos.south();
                    case SOUTH -> chestPos = pos.north();
                    case EAST -> chestPos = pos.west();
                    case WEST -> chestPos = pos.east();
                    default -> {
                        playerEntity.sendMessage(Text.literal("The sign has been placed incorrectly!").withColor(16733525), false);
                        return ActionResult.CONSUME;
                    }
                }

                BlockEntity chestEntity = world.getBlockEntity(chestPos);
                if (!(chestEntity instanceof Inventory inventory)) {
                    playerEntity.sendMessage(
                            Text.literal("No inventory could be found behind the sign [" + facing + "]!").withColor(16733525),
                            false
                    );
                    return ActionResult.CONSUME;
                }

                if (inventory.isEmpty()) {
                    playerEntity.sendMessage(
                            Text.literal("The inventory behind this sign is empty!").withColor(16733525),
                            false
                    );
                    return ActionResult.CONSUME;
                }

                ComponentMap componentMap = sign.getComponents();
                NbtComponent customDataComponent = componentMap.get(DataComponentTypes.CUSTOM_DATA);
                if (customDataComponent == null) {
                    playerEntity.sendMessage(
                            Text.literal("Invalid sign data!").withColor(16733525),
                            false
                    );
                    return ActionResult.CONSUME;
                }

                NbtCompound root = customDataComponent.copyNbt();
                NbtCompound signData = root.getCompoundOrEmpty(ShopSystem.MOD_ID);
                if (signData == null) {
                    playerEntity.sendMessage(
                            Text.literal("Invalid sign data!").withColor(16733525),
                            false
                    );
                    return ActionResult.CONSUME;
                }

                Optional<String> owner = signData.getString("owner");
                Optional<String> partner = signData.getString("partner");
                Optional<Integer> price = signData.getInt("price");
                Optional<Integer> stackSize = signData.getInt("stackSize");

                if (owner.isEmpty() || price.isEmpty() || stackSize.isEmpty()) {
                    playerEntity.sendMessage(
                            Text.literal("Invalid sign data!").withColor(16733525),
                            false
                    );
                    return ActionResult.CONSUME;
                }

                ScoreAccess playerCredits = Helper.getScoreAccess("credits", playerEntity);
                if (playerCredits == null || playerCredits.getScore() < price.get()) {
                    playerEntity.sendMessage(
                            Text.literal("You don't have enough credits!").withColor(16733525),
                            false
                    );
                    return ActionResult.CONSUME;
                }


                if (!Helper.allItemsSameType(inventory)) {
                    playerEntity.sendMessage(
                            Text.literal("The inventory behind this sign has different item stacks!").withColor(16733525),
                            false
                    );
                    return ActionResult.CONSUME;
                }

                ItemStack removedItemStack = Helper.removeItemsFromInventory(inventory, stackSize.get());

                if (removedItemStack.isEmpty()) {
                    playerEntity.sendMessage(
                            Text.literal("The inventory behind this sign has an invalid amount of items!").withColor(16733525),
                            false
                    );
                    return ActionResult.CONSUME;
                }

                String itemType = removedItemStack.getItemName().getString();

                int creditsToPay = price.get();

                playerCredits.setScore(playerCredits.getScore() - creditsToPay);
                playerEntity.giveOrDropStack(removedItemStack);

                MinecraftServer server = world.getServer();
                if (server == null) return ActionResult.CONSUME;

                Scoreboard scoreboard = server.getScoreboard();
                Collection<ScoreHolder> scoreHolders = scoreboard.getKnownScoreHolders();

                ScoreHolder ownerScoreHolder = null;
                ScoreHolder partnerScoreHolder = null;

                for (ScoreHolder scoreHolder : scoreHolders) {
                    if (Objects.equals(scoreHolder.getNameForScoreboard(), owner.get())) {
                        ownerScoreHolder = scoreHolder;
                    }
                    if (partner.isPresent() && Objects.equals(scoreHolder.getNameForScoreboard(), partner.get())) {
                        partnerScoreHolder = scoreHolder;
                    }
                }

                if (ownerScoreHolder == null) {
                    playerEntity.sendMessage(
                            Text.literal("Couldn't find shop owner!").withColor(16733525),
                            false
                    );
                    return ActionResult.CONSUME;
                }

                ScoreboardObjective creditsObjective = scoreboard.getNullableObjective("credits");
                if (creditsObjective == null) return ActionResult.CONSUME;

                ScoreAccess ownerCredits = scoreboard.getOrCreateScore(ownerScoreHolder, creditsObjective);
                ScoreAccess partnerCredits = null;
                if (partnerScoreHolder != null) partnerCredits = scoreboard.getOrCreateScore(partnerScoreHolder, creditsObjective);

                int creditsToReceive = (int)Math.floor(price.get() - price.get() * vat);

                int ownerCreditsToReceive = creditsToReceive;
                int partnerCreditsToReceive = 0;

                if (partnerScoreHolder != null) {
                    if (creditsToReceive % 2 == 0) {
                        ownerCreditsToReceive = partnerCreditsToReceive = creditsToReceive / 2;
                    } else {
                        ownerCreditsToReceive = (creditsToReceive + 1) / 2;
                        partnerCreditsToReceive = (creditsToReceive - 1) / 2;
                    }
                    partnerCredits.setScore(partnerCredits.getScore() + partnerCreditsToReceive);
                }

                ownerCredits.setScore(ownerCredits.getScore() + ownerCreditsToReceive);

                Text[] playerMessageArr = {
                        Text.literal("Successfully bought ").withColor(16777215),
                        Text.literal(stackSize.get() + "x " + itemType).withColor(4045567),
                        Text.literal(" for ").withColor(16777215),
                        Text.literal("$" + creditsToPay).withColor(4045567),
                        Text.literal(" from ").withColor(16777215),
                        partnerScoreHolder != null
                                ? Helper.textBuilder(new Text[]{
                                        ownerScoreHolder.getStyledDisplayName(),
                                        Text.literal(" and ").withColor(16777215),
                                        partnerScoreHolder.getStyledDisplayName()
                                })
                                : ownerScoreHolder.getStyledDisplayName()
                };

                playerEntity.sendMessage(Helper.textBuilder(playerMessageArr), false);

                Text[] ownerMessageArr = {
                        Text.literal("You received ").withColor(16777215),
                        Text.literal("$" + ownerCreditsToReceive).withColor(4045567),
                        Text.literal(" by selling ").withColor(16777215),
                        Text.literal(stackSize.get() + "x " + itemType).withColor(4045567),
                        Text.literal(" to ").withColor(16777215),
                        playerEntity.getStyledDisplayName()
                };

                Text[] partnerMessageArr = {
                        Text.literal("You received ").withColor(16777215),
                        Text.literal("$" + partnerCreditsToReceive).withColor(4045567),
                        Text.literal(" by selling ").withColor(16777215),
                        Text.literal(stackSize.get() + "x " + itemType).withColor(4045567),
                        Text.literal(" to ").withColor(16777215),
                        playerEntity.getStyledDisplayName()
                };

                PlayerManager playerManager = server.getPlayerManager();
                PlayerEntity ownerEntity = playerManager.getPlayer(owner.get());
                PlayerEntity partnerEntity = null;
                if (partner.isPresent()) partnerEntity = playerManager.getPlayer(partner.get());

                if (ownerEntity != null) ownerEntity.sendMessage(Helper.textBuilder(ownerMessageArr), false);
                if (partnerEntity != null) partnerEntity.sendMessage(Helper.textBuilder(partnerMessageArr), false);

                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        LOGGER.info("Shop System has been initialized!");
	}
}
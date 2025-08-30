package de.jullevistrunz;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.scoreboard.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;


public class Commands {
    public static int executeMyCreditsCommand(CommandContext<ServerCommandSource> context) {
        PlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        Helper.displayTotalCredits(player);

        return 1;
    }

    public static int executeCreditsCommand(CommandContext<ServerCommandSource> context) {
        PlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        MinecraftServer server = context.getSource().getServer();
        Scoreboard scoreboard = server.getScoreboard();

        ScoreboardObjective creditsObjective = scoreboard.getNullableObjective("credits");
        if (creditsObjective == null) return 0;

        Collection<ScoreHolder> scoreHolders = scoreboard.getKnownScoreHolders();

        Map<ScoreHolder, Integer> creditsScoreHolderMap = new HashMap<>();

        for (ScoreHolder scoreHolder : scoreHolders) {
             ScoreAccess creditsScoreAccess = scoreboard.getOrCreateScore(scoreHolder, creditsObjective);

            if (creditsScoreAccess.getScore() <= 0) continue;

            creditsScoreHolderMap.put(scoreHolder, creditsScoreAccess.getScore());


        }

        // https://stackoverflow.com/a/33552682/15255405
        creditsScoreHolderMap.entrySet().stream()
                .sorted((e1, e2) -> -e1.getValue().compareTo(e2.getValue()))
                .forEach(entry -> {
                    Text[] messageArr = {
                            entry.getKey().getStyledDisplayName(),
                            Text.literal(": ").withColor(16777215),
                            Text.literal("$" + entry.getValue()).withColor(4045567)
                    };

                    player.sendMessage(Helper.textBuilder(messageArr), false);
                });

        return 1;
    }

    public static int executePayCommand (CommandContext<ServerCommandSource> context) {
        PlayerEntity payee = context.getSource().getPlayer();
        if (payee == null) return 0;

        PlayerEntity recipient = null;
        try {
            recipient = EntityArgumentType.getPlayer(context, "recipient");
        } catch (CommandSyntaxException ignored) {}
        if (recipient == null) {
            payee.sendMessage(Text.literal("Couldn't find specified recipient!").withColor(16733525), false);
            return 0;
        }

        int amount = IntegerArgumentType.getInteger(context, "amount");

        ScoreAccess balanceRecipient = Helper.getScoreAccess("credits", recipient);
        ScoreAccess balancePayee = Helper.getScoreAccess("credits", payee);

        if (balanceRecipient == null || balancePayee == null) return 0;

        if (balancePayee.getScore() < amount) {
            payee.sendMessage(Text.literal("You don't have enough credits!").withColor(16733525), false);
            return 0;
        }

        balancePayee.setScore(balancePayee.getScore() - amount);
        balanceRecipient.setScore(balanceRecipient.getScore() + amount);

        Text[] payeeMessageArr = {
                Text.literal("Successfully payed ").withColor(5635925),
                recipient.getStyledDisplayName(),
                Text.literal(" $" + amount).withColor(4045567)
        };

        payee.sendMessage(Helper.textBuilder(payeeMessageArr), false);

        Text[] recipientMessageArr = {
                Text.literal("You received ").withColor(5635925),
                Text.literal("$" + amount).withColor(4045567),
                Text.literal(" from ").withColor(5635925),
                payee.getStyledDisplayName()
        };

        recipient.sendMessage(Helper.textBuilder(recipientMessageArr), false);

        return 1;
    }

    public static int executeSignCommand (CommandContext<ServerCommandSource> context) {
        PlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        World world = context.getSource().getWorld();
        BlockPos signPos = BlockPosArgumentType.getBlockPos(context, "pos");

        BlockState signState = world.getBlockState(signPos);
        BlockEntity signEntity = world.getBlockEntity(signPos);
        if (!signState.isIn(BlockTags.WALL_SIGNS) || !(signEntity instanceof SignBlockEntity sign)) {
            player.sendMessage(Text.literal("The block at the given position is not a valid sign!").withColor(16733525),
                    false);
            return 0;
        }

        int price = IntegerArgumentType.getInteger(context, "price");
        int stackSize = IntegerArgumentType.getInteger(context, "stackSize");

        ComponentMap componentMap = sign.getComponents();
        NbtComponent customDataComponent = componentMap.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound root = customDataComponent == null ? new NbtCompound() : customDataComponent.copyNbt();

        NbtCompound existingSignData = root.getCompound(ShopSystem.MOD_ID).isPresent() ? root.getCompound(ShopSystem.MOD_ID).get() : null;

        if (existingSignData != null) {
            Optional<String> existingOwner = existingSignData.getString("owner");
            if (existingOwner.isPresent() && !existingOwner.get().equals(player.getNameForScoreboard())) {
                player.sendMessage(Text.literal("You may not modify someone else's shop sign!").withColor(16733525),
                        false);
                return 0;
            }
        }

        NbtCompound signData = new NbtCompound();
        signData.putInt("price", price);
        signData.putInt("stackSize", stackSize);
        signData.putString("owner", player.getNameForScoreboard());

        root.put(ShopSystem.MOD_ID, signData);

        ComponentMap.Builder builder = ComponentMap.builder();
        builder.add(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));

        ComponentMap updatedNbtData = builder.build();

        sign.setComponents(updatedNbtData);

        Text[] priceArr = {
                Text.literal("Price: ").withColor(16777215),
                Text.literal("$" + price).withColor(4045567)
        };
        Text[] stackSizeArr = {
                Text.literal("Stack size: ").withColor(16777215),
                Text.literal(stackSize + "x").withColor(4045567)
        };

        SignText signText = new SignText();
        signText = signText.withMessage(1, Helper.textBuilder(priceArr));
        signText = signText.withMessage(2, Helper.textBuilder(stackSizeArr));

        sign.setText(signText, true);

        sign.markDirty();
        world.updateListeners(signPos, sign.getCachedState(), sign.getCachedState(), 3);

        return 1;
    }
}

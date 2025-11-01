package de.jullevistrunz;

import net.minecraft.block.Block;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class Helper {
    public static MutableText textBuilder(Text[] elements) {
        if (elements == null || elements.length == 0) return Text.empty();
        MutableText result = Text.empty();
        for (Text t : elements) {
            if (t != null) result = result.append(t);
        }
        return result;
    }

    public static @Nullable ScoreAccess getScoreAccess(String objective, PlayerEntity player) {
        if (player == null) return null;

        MinecraftServer server = player.getServer();
        if (server == null) return null;

        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective scoreboardObjective = scoreboard.getNullableObjective(objective);
        if (scoreboardObjective == null) return null;

        return scoreboard.getOrCreateScore(player, scoreboardObjective);
    }

    public static void displayPlayerCredits(PlayerEntity player) {
        ScoreAccess creditsScoreAccess = getScoreAccess("credits", player);
        if (creditsScoreAccess == null) return;

        Text[] messageArr = { Text.literal("Your credits: ").withColor(16777215),
                Text.literal("$" + creditsScoreAccess.getScore()).withColor(4045567) };
        player.sendMessage(Helper.textBuilder(messageArr), false);
    }

    public static boolean isShopSign(SignBlockEntity sign) {
        ComponentMap componentMap = sign.getComponents();
        NbtComponent customDataComponent = componentMap.get(DataComponentTypes.CUSTOM_DATA);
        if (customDataComponent == null) return false;
        return customDataComponent.copyNbt().get(ShopSystem.MOD_ID) != null;
    }

    public static ItemStack removeItemsFromInventory(Inventory inventory, int amountToRemove) {
        int total = 0;
        ItemStack type = ItemStack.EMPTY;

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                if (block.getDefaultState().isIn(BlockTags.SHULKER_BOXES) && amountToRemove > 1) return ItemStack.EMPTY;
            }
            if (type.isEmpty()) type = stack.copy();
            total += stack.getCount();
        }
        if (total < amountToRemove || type.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int remaining = amountToRemove;
        for (int i = inventory.size() - 1; i >= 0 && remaining > 0; i--) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;

            int stackCount = stack.getCount();
            if (stackCount <= remaining) {
                inventory.setStack(i, ItemStack.EMPTY);
                remaining -= stackCount;
            } else {
                stack.decrement(remaining);
                remaining = 0;
            }
        }

        type.setCount(amountToRemove);
        return type;
    }

    public static boolean allItemsSameType(Inventory inventory) {
        ItemStack reference = ItemStack.EMPTY;

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;

            if (reference.isEmpty()) {
                reference = stack;
                continue;
            }

            boolean sameItem = stack.isOf(reference.getItem());
            boolean sameNbt = stack.getComponents().equals(reference.getComponents());

            if (!sameItem || !sameNbt) return false;
        }
        return true;
    }

    public static int getTotalCredits(@NotNull MinecraftServer server) {
        int totalCredits = 0;

        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective creditsObjective = scoreboard.getNullableObjective("credits");

        Collection<ScoreHolder> scoreHolders = scoreboard.getKnownScoreHolders();
        for (ScoreHolder scoreHolder : scoreHolders) {
            totalCredits += scoreboard.getOrCreateScore(scoreHolder, creditsObjective).getScore();
        }

        return totalCredits;
    }
}

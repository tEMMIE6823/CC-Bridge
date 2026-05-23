package com.ccbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static net.minecraft.commands.Commands.literal;

public class CCBridgeCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();


    private static class PlayerData {
        Set<String> used = new HashSet<>();
        Set<String> messaged = new HashSet<>();
    }

    private static Path dataFile(CommandSourceStack source) {
        return source.getServer().getWorldPath(LevelResource.ROOT).resolve("ccbridge_players.json");
    }

    private static Path dataFileFor(ServerPlayer player) {
        return player.server.getWorldPath(LevelResource.ROOT).resolve("ccbridge_players.json");
    }

    private static PlayerData load(Path path) {
        try {
            if (Files.exists(path)) {
                return GSON.fromJson(Files.readString(path), PlayerData.class);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load ccbridge player data: ", e);
        }
        return new PlayerData();
    }

    private static void save(Path path, PlayerData data) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(data));
        } catch (IOException e) {
            LOGGER.error("Failed to save ccbridge player data: ", e);
        }
    }


    private static ItemStack createGuideBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", "CC:Bridge Basic Guide");
        tag.putString("author", "CC:Bridge");
        tag.putInt("generation", 3);

        ListTag pages = new ListTag();

        pages.add(StringTag.valueOf("[\"\",{\"text\":\"New inbuilt functions for CC:Tweaked:\",\"bold\":true,\"underlined\":true,\"color\":\"dark_blue\"},{\"text\":\"\\n\\nHere you will find what the new functions ccbridge provides to cc;tweaked and create\\n \",\"color\":\"reset\"}]"));
        pages.add(StringTag.valueOf("[\"\",{\"text\":\"GetFrequency\",\"bold\":true},{\"text\":\"\\n\\n\",\"color\":\"reset\"},{\"text\":\"Example usage:\",\"bold\":true,\"underlined\":true,\"color\":\"dark_green\"},{\"text\":\"\\n\",\"color\":\"reset\"},{\"text\":\"ccbridge.getf(\\\"minecraft:stone\\\",\\\"minecraft:redstone_block\\\")\",\"italic\":true,\"color\":\"dark_gray\"},{\"text\":\"\\n\\n\",\"color\":\"reset\"},{\"text\":\"Base usage:\",\"bold\":true,\"underlined\":true,\"color\":\"dark_red\"},{\"text\":\"\\n\",\"color\":\"reset\"},{\"text\":\"ccbridge.GetFrequency(freq1,freq2)\",\"italic\":true,\"color\":\"dark_gray\"},{\"text\":\"\\n\\n \",\"color\":\"reset\"}]"));
        pages.add(StringTag.valueOf("[\"\",{\"text\":\"SendFrequency\",\"bold\":true},{\"text\":\"\\n\\n\",\"color\":\"reset\"},{\"text\":\"Example usage:\",\"bold\":true,\"underlined\":true,\"color\":\"dark_green\"},{\"text\":\"\\n\",\"color\":\"reset\"},{\"text\":\"ccbridge.sendf(\\\"minecraft:stone\\\",\\\"minecraft:redstone_block\\\",true,60)\",\"italic\":true,\"color\":\"dark_gray\"},{\"text\":\"\\n\\n\",\"color\":\"reset\"},{\"text\":\"Base usage:\",\"bold\":true,\"underlined\":true,\"color\":\"dark_red\"},{\"text\":\"\\n\",\"color\":\"reset\"},{\"text\":\"ccbridge.SendFrequency(freq1,freq2, boolean, integer)\",\"italic\":true,\"color\":\"dark_gray\"}]"));
        pages.add(StringTag.valueOf("[\"\",{\"text\":\"PulseFrequency\",\"bold\":true},{\"text\":\"\\n\\n\",\"color\":\"reset\"},{\"text\":\"Example usage:\",\"bold\":true,\"underlined\":true,\"color\":\"dark_green\"},{\"text\":\"\\n\",\"color\":\"reset\"},{\"text\":\"ccbridge.pulsef(\\\"minecraft:stone\\\",\\\"minecraft:redstone_block\\\",true,5,10)\",\"italic\":true,\"color\":\"dark_gray\"},{\"text\":\"\\n\\n\",\"color\":\"reset\"},{\"text\":\"Base usage:\",\"bold\":true,\"underlined\":true,\"color\":\"dark_red\"},{\"text\":\"\\n\",\"color\":\"reset\"},{\"text\":\"ccbridge.PulseFrequency(freq1,freq2, boolean, pulses(integer), duration(integer) )\",\"italic\":true,\"color\":\"dark_gray\"}]"));
        pages.add(StringTag.valueOf("[\"\",{\"text\":\"Shortened Aliases:\",\"bold\":true,\"underlined\":true,\"color\":\"blue\"},{\"text\":\"\\n\\n\",\"color\":\"reset\"},{\"text\":\"getf == GetFrequency\",\"italic\":true,\"color\":\"dark_gray\"},{\"text\":\"\\n\\n\",\"color\":\"dark_gray\"},{\"text\":\"sendf == SendFrequency\\n\\npulsef == PulseFrequency\",\"italic\":true,\"color\":\"dark_gray\"}]"));
        pages.add(StringTag.valueOf("[\"\",{\"text\":\"GetFrequency logic:\",\"bold\":true},{\"text\":\"\\n\\n\",\"color\":\"reset\"},{\"text\":\"Returns the integer value/redstone strength of any defined ongoing frequencies\",\"italic\":true,\"color\":\"dark_gray\"},{\"text\":\"\\n \",\"color\":\"reset\"}]"));
        pages.add(StringTag.valueOf("[\"\",{\"text\":\"SendFrequency logic:\",\"bold\":true},{\"text\":\"\\n\\n\",\"color\":\"reset\"},{\"text\":\"Sends a redstone signal to the defined frequency with a defined duration of how long that signal will last, setting the duration to 0 or math.huge will make it last indefinitely\",\"italic\":true,\"color\":\"dark_gray\"}]"));
        pages.add(StringTag.valueOf("[\"\",{\"text\":\"PulseFrequency logic:\",\"bold\":true},{\"text\":\"\\n\\n\",\"color\":\"reset\"},{\"text\":\"Similar to SendFrequency however allows you to define how many times it should fire a redstone signal like a heartbeat, and allows you to define how long those pulses should be\",\"italic\":true,\"color\":\"dark_gray\"}]"));

        tag.put("pages", pages);

        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf("{\"text\":\"Very very basic guide explaining what new functions are added and what they do and how to use them\"}"));
        CompoundTag display = new CompoundTag();
        display.put("Lore", lore);
        tag.put("display", display);

        return book;
    }


    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            literal("ccbridge")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    ServerPlayer player = source.getPlayerOrException();
                    String uuid = player.getUUID().toString();
                    Path file = dataFile(source);
                    PlayerData data = load(file);

                    if (data.used.contains(uuid)) {
                        source.sendFailure(Component.literal("You have already received your guide book, if you lost it prior thats on you"));
                        return 0;
                    }
                    data.used.add(uuid);
                    save(file, data);
                    player.addItem(createGuideBook());
                    return 1;
                })
        );
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String uuid = player.getUUID().toString();
            Path file = dataFileFor(player);
            PlayerData data = load(file);
            if (!data.messaged.contains(uuid)) {
                data.messaged.add(uuid);
                save(file, data);
                player.sendSystemMessage(
                    Component.literal("Type /ccbridge to receive a guide book explaining the new CC:Bridge functions!")
                );
            }
        }
    }
}

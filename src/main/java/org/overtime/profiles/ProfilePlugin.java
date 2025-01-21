package org.overtime.profiles;

import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class ProfilePlugin extends JavaPlugin {
    private File profilesDirectory;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void onEnable() {
        profilesDirectory = new File(getDataFolder(), "profiles");
        if (!profilesDirectory.exists()) {
            profilesDirectory.mkdirs();
        }
        getLogger().info("ProfilePlugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ProfilePlugin disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("profile")) {
            if (args.length == 0) {
                sender.sendMessage(Component.text("Use /profile add <field> <value>, /profile del <field>, or /profile <username>.", NamedTextColor.GREEN));
                return true;
            }

            if (args[0].equalsIgnoreCase("add")) {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /profile add <bio|link> <value>", NamedTextColor.RED));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }

                Player player = (Player) sender;
                String field = args[1].toLowerCase();

                if (field.equals("bio")) {
                    String bio = String.join(" ", args).substring(args[0].length() + args[1].length() + 2);
                    addBioToProfile(player.getName(), bio);
                } else if (field.equals("link")) {
                    if (args.length < 4) {
                        player.sendMessage(Component.text("Usage: /profile add link <name> <url>", NamedTextColor.RED));
                        return true;
                    }
                    String linkName = args[2];
                    String linkUrl = String.join(" ", args).substring(args[0].length() + args[1].length() + args[2].length() + 3);
                    addNamedLinkToProfile(player.getName(), linkName, linkUrl);
                } else {
                    sender.sendMessage(Component.text("Field must be 'bio' or 'link'.", NamedTextColor.RED));
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("del")) {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /profile del <bio|link|all> <value>", NamedTextColor.RED));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }

                Player player = (Player) sender;
                String field = args[1].toLowerCase();

                if (field.equals("bio")) {
                    deleteProfileField(player.getName(), "bio");
                    player.sendMessage(Component.text("Your bio has been deleted!", NamedTextColor.GREEN));
                } else if (field.equals("link")) {
                    String linkName = args[2];
                    deleteNamedLinkFromProfile(player.getName(), linkName);
                } else if (field.equals("all")) {
                    deleteProfile(player.getName());
                    player.sendMessage(Component.text("Your entire profile has been deleted!", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Field must be 'bio', 'link', or 'all'.", NamedTextColor.RED));
                }
                return true;
            }

            if (args.length == 1) {
                String targetPlayer = args[0];
                String profile = loadProfile(targetPlayer);

                if (profile != null) {
                    sender.sendMessage(Component.text("Profile for " + targetPlayer + ":\n" + profile, NamedTextColor.AQUA));
                } else {
                    sender.sendMessage(Component.text("No profile found for " + targetPlayer + ".", NamedTextColor.RED));
                }
                return true;
            }

            showHelp(sender);
            return true;
        }

        return false;
    }

    private void addBioToProfile(String playerName, String bio) {
        JsonObject profile = loadProfileAsJson(playerName);
        profile.addProperty("bio", bio);
        saveProfile(playerName, profile);

        Bukkit.getPlayer(playerName).sendMessage(Component.text("Your bio has been updated!", NamedTextColor.GREEN));
    }

    private void addNamedLinkToProfile(String playerName, String name, String url) {
        JsonObject profile = loadProfileAsJson(playerName);

        JsonObject links = profile.has("links") ? profile.getAsJsonObject("links") : new JsonObject();

        if (links.size() >= 10) {
            Bukkit.getPlayer(playerName).sendMessage(Component.text("You can only have up to 10 links.", NamedTextColor.RED));
            return;
        }

        if (links.has(name)) {
            Bukkit.getPlayer(playerName).sendMessage(Component.text("A link with this name already exists in your profile.", NamedTextColor.RED));
            return;
        }

        links.addProperty(name, url);
        profile.add("links", links);
        saveProfile(playerName, profile);

        Bukkit.getPlayer(playerName).sendMessage(Component.text("Link '" + name + "' added to your profile!", NamedTextColor.GREEN));
    }

    private void deleteNamedLinkFromProfile(String playerName, String name) {
        JsonObject profile = loadProfileAsJson(playerName);

        if (!profile.has("links")) {
            Bukkit.getPlayer(playerName).sendMessage(Component.text("You don't have any links in your profile.", NamedTextColor.RED));
            return;
        }

        JsonObject links = profile.getAsJsonObject("links");

        if (!links.has(name)) {
            Bukkit.getPlayer(playerName).sendMessage(Component.text("No link with the name '" + name + "' found in your profile.", NamedTextColor.RED));
            return;
        }

        links.remove(name);
        profile.add("links", links);
        saveProfile(playerName, profile);

        Bukkit.getPlayer(playerName).sendMessage(Component.text("Link '" + name + "' removed from your profile!", NamedTextColor.GREEN));
    }

    private void deleteProfileField(String playerName, String field) {
        JsonObject profile = loadProfileAsJson(playerName);
        if (profile.has(field)) {
            profile.remove(field);
            saveProfile(playerName, profile);
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text("Profile Plugin Help (v1.1.0)", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Usage:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/profile help - Show this help message", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("/profile add bio <text> - Add or update your bio", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("/profile add link <name> <url> - Add a named link", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("/profile del bio - Delete your bio", NamedTextColor.RED));
        sender.sendMessage(Component.text("/profile del link <name> - Delete a named link", NamedTextColor.RED));
        sender.sendMessage(Component.text("/profile del all - Delete entire profile", NamedTextColor.RED));
        sender.sendMessage(Component.text("/profile <username> - View a player's profile", NamedTextColor.AQUA));
    }

    private void deleteProfile(String playerName) {
        Path profilePath = Paths.get(profilesDirectory.getAbsolutePath(), playerName + ".json");
        try {
            Files.deleteIfExists(profilePath);
        } catch (IOException e) {
            getLogger().severe("Failed to delete profile for " + playerName + ": " + e.getMessage());
        }
    }

    private String loadProfile(String playerName) {
        Path profilePath = Paths.get(profilesDirectory.getAbsolutePath(), playerName + ".json");
        if (Files.exists(profilePath)) {
            try {
                return new String(Files.readAllBytes(profilePath));
            } catch (IOException e) {
                getLogger().severe("Failed to load profile for " + playerName + ": " + e.getMessage());
            }
        }
        return null;
    }

    private JsonObject loadProfileAsJson(String playerName) {
        Path profilePath = Paths.get(profilesDirectory.getAbsolutePath(), playerName + ".json");
        if (Files.exists(profilePath)) {
            try {
                String content = new String(Files.readAllBytes(profilePath));
                return gson.fromJson(content, JsonObject.class);
            } catch (IOException e) {
                getLogger().severe("Failed to load profile for " + playerName + ": " + e.getMessage());
            }
        }
        return new JsonObject();
    }

    private void saveProfile(String playerName, JsonObject profile) {
        Path profilePath = Paths.get(profilesDirectory.getAbsolutePath(), playerName + ".json");
        try {
            Files.write(profilePath, gson.toJson(profile).getBytes());
        } catch (IOException e) {
            getLogger().severe("Failed to save profile for " + playerName + ": " + e.getMessage());
        }
    }
}

package org.overtime.profiles;

import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class ProfilePlugin extends JavaPlugin {
    private File profilesDirectory;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void onEnable() {
        getLogger().info("Initializing ProfilePlugin v" + getPluginMeta().getVersion());
        profilesDirectory = new File(getDataFolder(), "profiles");
        
        getLogger().info("Using profiles directory: " + profilesDirectory.getAbsolutePath());
        
        if (!profilesDirectory.exists()) {
            getLogger().info("Creating profiles directory...");
            boolean created = profilesDirectory.mkdirs();
            if (created) {
                getLogger().info("Created profiles directory successfully");
            } else {
                getLogger().warning("Failed to create profiles directory!");
            }
        }
        
        // Register command executor
        getCommand("profile").setExecutor(this);
        
        getLogger().info("ProfilePlugin enabled!");
        getLogger().info("Hooks initialized - Ready to handle profile commands");
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down ProfilePlugin...");
        int activeProfiles = profilesDirectory.list().length;
        if (activeProfiles > 0) {
            getLogger().warning("Shutting down with " + activeProfiles + " active profiles - Data may be at risk!");
        }
        getLogger().info("ProfilePlugin disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("profile")) {
            if (args.length == 0) {
                showHelp(sender);
                return true;
            }

            if (args[0].equalsIgnoreCase("add")) {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /profile add <bio|label> <value>", NamedTextColor.RED));
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
                } else {
                    String linkLabel = args[1];
                    String url = String.join(" ", args).substring(args[0].length() + args[1].length() + 2);
                    addNamedLinkToProfile(player.getName(), linkLabel, url);
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

            if (args[0].equalsIgnoreCase("help")) {
                showHelp(sender);
                return true;
            }

            if (args.length == 1) {
                String targetPlayer = args[0];
                JsonObject profile = loadProfileAsJson(targetPlayer);

                if (profile.size() > 0) {
                    Component header = Component.text()
                        .append(Component.text("Profile for ", NamedTextColor.GOLD))
                        .append(Component.text(targetPlayer, NamedTextColor.AQUA))
                        .build();
                    
                    sender.sendMessage(header);
                    
                    // Bio section
                    if (profile.has("bio")) {
                        sender.sendMessage(Component.text("Bio: ", NamedTextColor.GOLD)
                                .append(parseFormattedText(profile.get("bio").getAsString())));
                    } else {
                        sender.sendMessage(Component.text("Bio: Not available", NamedTextColor.GRAY));
                    }

                    // Links section
                    if (profile.has("links")) {
                        JsonObject links = profile.getAsJsonObject("links");
                        links.entrySet().forEach(entry -> {
                            String url = entry.getValue().getAsString();
                            sender.sendMessage(Component.text()
                                .append(parseFormattedText("• " + entry.getKey() + ": "))
                                .append(Component.text(url, NamedTextColor.BLUE)
                                    .clickEvent(ClickEvent.openUrl(url))
                                    .hoverEvent(HoverEvent.showText(
                                        Component.text("Click to open link", NamedTextColor.GREEN)
                                    )))
                                .build());
                        });
                    } else {
                        sender.sendMessage(Component.text("Links: None added", NamedTextColor.GRAY));
                    }
                } else {
                    sender.sendMessage(Component.text()
                        .append(Component.text("No profile found for ", NamedTextColor.RED))
                        .append(Component.text(targetPlayer, NamedTextColor.AQUA))
                        .build());
                }
                return true;
            }

            showHelp(sender);
            return true;
        }

        return false;
    }

    private void addBioToProfile(String playerName, String bio) {
        getLogger().info(playerName + " is updating their bio");
        JsonObject profile = loadProfileAsJson(playerName);
        profile.addProperty("bio", bio);
        saveProfile(playerName, profile);
        getLogger().finer("Bio updated for " + playerName + ": " + gson.toJson(profile.get("bio")));

        Bukkit.getPlayer(playerName).sendMessage(Component.text("Your bio has been updated!", NamedTextColor.GREEN));
    }

    private void addNamedLinkToProfile(String playerName, String name, String url) {
        getLogger().info(playerName + " adding link: " + name + " => " + url);
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
        getLogger().info(playerName + " removing link: " + name);
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

    private Component parseFormattedText(String text) {
        Component component = Component.empty();
        String[] parts = text.split("(?=&.)");
        for (String part : parts) {
            if (part.startsWith("&")) {
                String code = part.substring(1, 2);
                String content = part.substring(2);

                switch (code) {
                    case "0": component = component.append(Component.text(content).color(NamedTextColor.BLACK)); break;
                    case "1": component = component.append(Component.text(content).color(NamedTextColor.DARK_BLUE)); break;
                    case "2": component = component.append(Component.text(content).color(NamedTextColor.DARK_GREEN)); break;
                    case "3": component = component.append(Component.text(content).color(NamedTextColor.DARK_AQUA)); break;
                    case "4": component = component.append(Component.text(content).color(NamedTextColor.DARK_RED)); break;
                    case "5": component = component.append(Component.text(content).color(NamedTextColor.DARK_PURPLE)); break;
                    case "6": component = component.append(Component.text(content).color(NamedTextColor.GOLD)); break;
                    case "7": component = component.append(Component.text(content).color(NamedTextColor.GRAY)); break;
                    case "8": component = component.append(Component.text(content).color(NamedTextColor.DARK_GRAY)); break;
                    case "9": component = component.append(Component.text(content).color(NamedTextColor.BLUE)); break;
                    case "a": component = component.append(Component.text(content).color(NamedTextColor.GREEN)); break;
                    case "b": component = component.append(Component.text(content).color(NamedTextColor.AQUA)); break;
                    case "c": component = component.append(Component.text(content).color(NamedTextColor.RED)); break;
                    case "d": component = component.append(Component.text(content).color(NamedTextColor.LIGHT_PURPLE)); break;
                    case "e": component = component.append(Component.text(content).color(NamedTextColor.YELLOW)); break;
                    case "f": component = component.append(Component.text(content).color(NamedTextColor.WHITE)); break;
                    case "l": component = component.append(Component.text(content).decorate(TextDecoration.BOLD)); break;
                    case "m": component = component.append(Component.text(content).decorate(TextDecoration.STRIKETHROUGH)); break;
                    case "n": component = component.append(Component.text(content).decorate(TextDecoration.UNDERLINED)); break;
                    case "o": component = component.append(Component.text(content).decorate(TextDecoration.ITALIC)); break;
                    case "r": component = component.append(Component.text(content)
                            .decoration(TextDecoration.BOLD, false)
                            .decoration(TextDecoration.ITALIC, false)
                            .decoration(TextDecoration.STRIKETHROUGH, false)
                            .decoration(TextDecoration.UNDERLINED, false)); break;
                    default: component = component.append(Component.text(part)); break;
                }
            } else {
                component = component.append(Component.text(part));
            }
        }
        return component;
    }

    private void deleteProfileField(String playerName, String field) {
        JsonObject profile = loadProfileAsJson(playerName);
        if (profile.has(field)) {
            profile.remove(field);
            saveProfile(playerName, profile);
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text("Profile Plugin Help (v" + getPluginMeta().getVersion() + ")", NamedTextColor.GOLD));
        //Github : https://github.com/overtimepog/profiles
        sender.sendMessage(Component.text("Github : https://github.com/overtimepog/profiles", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Usage:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/profile help - Show this help message", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("/profile add bio <text> - Add or update your bio", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("/profile add <label> <url> - Add a link", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("/profile del bio - Delete your bio", NamedTextColor.RED));
        sender.sendMessage(Component.text("/profile del link <name> - Delete a named link", NamedTextColor.RED));
        sender.sendMessage(Component.text("/profile del all - Delete entire profile", NamedTextColor.RED));
        sender.sendMessage(Component.text("/profile <username> - View a player's profile", NamedTextColor.AQUA));
    }

    private void deleteProfile(String playerName) {
        Path profilePath = Paths.get(profilesDirectory.getAbsolutePath(), playerName + ".json");
        getLogger().info("Deleting profile for " + playerName + " at " + profilePath);
        try {
            boolean deleted = Files.deleteIfExists(profilePath);
            if (deleted) {
                getLogger().info("Successfully deleted profile for " + playerName);
            } else {
                getLogger().warning("No profile found to delete for " + playerName);
            }
        } catch (IOException e) {
            getLogger().severe("Failed to delete profile for " + playerName + ": " + e.getMessage());
            getLogger().log(Level.FINE, "Stack trace", e);
        }
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
        getLogger().finer("Saving profile for " + playerName + " to " + profilePath);
        try {
            Files.write(profilePath, gson.toJson(profile).getBytes());
            getLogger().info("Successfully saved profile for " + playerName);
            getLogger().finer("Profile content: " + gson.toJson(profile));
        } catch (IOException e) {
            getLogger().severe("Failed to save profile for " + playerName + ": " + e.getMessage());
            getLogger().log(Level.FINER, "Stack trace: ", e);
        }
    }
}

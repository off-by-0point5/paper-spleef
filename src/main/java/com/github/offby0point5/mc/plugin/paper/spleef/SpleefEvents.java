package com.github.offby0point5.mc.plugin.paper.spleef;

import com.destroystokyo.paper.Namespaced;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class SpleefEvents implements Listener {
    private enum PlayerState{
        LOST,
        PLAYING
    }
    private enum GameState {
        RUNNING,
        WAITING
    }

    // Game state variables
    private final Map<String, PlayerState> playerStateMap = new HashMap<>();
    private int stateTicks;  // how many ticks the state will persist/the game is in this state until now
    private GameState gameState;  // in which state the game is
    // Game rules
    private final int minPlayers;  // How many players need to be online to start counting wait ticks
    private final int waitTicks;  // How many ticks to wait before starting the game
    private final Material spleefBlock;  // Block that can be broken to play the game
    private final Material spleefItem;  // Item used to break blocks
    private final int gameFieldRadius;  // The blocks the game arena is wide in each direction

    private void setPlayerState(Player player, PlayerState playerState) {
        Location location = player.getLocation();
        switch (playerState) {
            case PLAYING:
                player.setGameMode(GameMode.ADVENTURE);
                location.setY(8.5);
                player.teleport(location);
                player.setWalkSpeed(0.5f);
                break;
            case LOST:
                player.setGameMode(GameMode.ADVENTURE);
                location.setY(16.5);
                player.teleport(location);
                player.setWalkSpeed(0.2f);
                break;
        }
        this.playerStateMap.put(player.getName(), playerState);
    }

    private void updateScoreboard() {
        // todo make a scoreboard
    }

    private List<Player> getPlayersInGame() {
        List<Player> playerList = new ArrayList<>();
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (this.playerStateMap.get(player.getName()) == PlayerState.PLAYING) playerList.add(player);
        }
        return playerList;
    }
    private int getPlayersNumInGame() {
        return this.getPlayersInGame().size();
    }
    private boolean isGameOver() {
        return this.getPlayersNumInGame() <= 1;
    }

    private void startWaiting() {
        this.gameState = GameState.WAITING;
        this.stateTicks = this.waitTicks;
        // Reset all layers
        World world = Bukkit.getServer().getWorld("world");
        if (world == null) System.out.println("World is null");
        else {
            for (int x = -this.gameFieldRadius; x <= this.gameFieldRadius; x++) {
                for (int z = -this.gameFieldRadius; z <= this.gameFieldRadius; z++) {
                    world.getBlockAt(x, 15, z).setType(Material.BARRIER);
                    world.getBlockAt(x, 7, z).setType(this.spleefBlock);
                    world.getBlockAt(x, 5, z).setType(Material.FARMLAND);
                }
            }
        }
        //ItemStack waitItem1 = new ItemStack(Material.ACACIA_SAPLING, 1);
        ItemStack[] waitItems = {};
        // Set all players into wait mode
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            this.setPlayerState(player, PlayerState.LOST);
            player.getInventory().setContents(waitItems);
        }
    }

    private void startGame() {
        this.gameState = GameState.RUNNING;
        this.stateTicks = 0;
        ItemStack breakItem = new ItemStack(this.spleefItem, 1);
        ItemMeta itemMeta = breakItem.getItemMeta();
        itemMeta.setUnbreakable(true);
        itemMeta.addEnchant(Enchantment.DIG_SPEED, 5, false);
        List<Namespaced> breakableKeys = new ArrayList<>();
        breakableKeys.add(this.spleefBlock.getKey());
        itemMeta.setDestroyableKeys(breakableKeys);
        itemMeta.displayName(Component.text("Grubengrabgrät"));
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        breakItem.setItemMeta(itemMeta);
        ItemStack[] startItems = {breakItem};
        // Set all players into play mode
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            this.setPlayerState(player, PlayerState.PLAYING);
            player.getInventory().setContents(startItems);
        }
    }

    public SpleefEvents() {
        // this.plugin = plugin;
        this.spleefBlock = Material.SNOW_BLOCK;
        this.spleefItem = Material.GOLDEN_SHOVEL;
        this.waitTicks = 20 * 10;  // Wait 10 seconds between each game
        this.minPlayers = 2;  // Need two players as it is competitive
        this.gameFieldRadius = 12;  //  24 block wide arena
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (this.gameState == null) this.startWaiting();
        event.getPlayer().sendTitle("", "§6Spleef",
                30, 60, 30);
        setPlayerState(event.getPlayer(), PlayerState.LOST);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        System.out.println(event.getReason());
        this.playerStateMap.remove(event.getPlayer().getName());
    }

    // Make inventories unmodifiable
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) { event.setCancelled(true); }
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) { event.setCancelled(true); }
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) { event.setCancelled(true); }

    @EventHandler  // Blocks never drop items
    public void onBlockDrop(BlockDropItemEvent event) {
        event.setCancelled(true);
    }

    @EventHandler  // Prevent farmland from decaying
    public void onFarmlandToDirt(BlockFadeEvent event) {
        if (event.getBlock().getType() == Material.FARMLAND) event.setCancelled(true);
    }

    @EventHandler  // Falling on farmland means falling down from snow layer and losing the game
    public void onFallOnFarmland(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) return;
        event.setCancelled(true);
        if (this.playerStateMap.get(event.getPlayer().getName()) != PlayerState.PLAYING) return;
        assert event.getClickedBlock() != null;
        if (event.getClickedBlock().getType() != Material.FARMLAND) return;
        int placement = this.getPlayersNumInGame();
        String placementString;
        switch (placement) {
            case 2: placementString = "§aZweiter!"; break;
            case 3: placementString = "§aDritter!"; break;
            case 4: placementString = "§aVierter!"; break;
            case 5: placementString = "§aFünfter!"; break;
            default: placementString = "§aSechs, setzen.";
        }
        this.setPlayerState(event.getPlayer(), PlayerState.LOST);
        event.getPlayer().sendTitle(placementString, "§cDu bist raus!", 30, 30, 30);
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_GHAST_DEATH, 1.0f, 1.0f);
        this.updateScoreboard();
    }

    @EventHandler
    public void onTick(ServerTickStartEvent event) {

        // If game in waiting state
        if (this.gameState == GameState.WAITING) {
            if (Bukkit.getServer().getOnlinePlayers().size() < this.minPlayers) {
                this.stateTicks = this.waitTicks;
                return;
            }
            else this.stateTicks--;
            if (this.stateTicks == 0) this.startGame();
            if (this.stateTicks == this.waitTicks) return;
            if ((this.stateTicks <= (60*60) && this.stateTicks % (20*10) == 0)
                    || (this.stateTicks <= (20*5) && this.stateTicks % (20*5) == 0)
                    || (this.stateTicks <= 60 && this.stateTicks % 20 == 0))
                for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                    if (this.stateTicks == 0) {
                        player.sendTitle("", "§aStart!", 0, 10, 5);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    }
                    else {
                        player.sendTitle("", "§c" + this.stateTicks / 20, 0, 10, 5);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                    }
                }
            return;
        }
        // If game is running
        if (this.gameState == GameState.RUNNING) {
            // If there is only one player in game, the game is over
            if (this.isGameOver()) {
                Player winner = this.getPlayersInGame().get(0);
                String subtitle;
                switch (new Random().nextInt(20)) {
                    case 0: subtitle = "§6Leuchtsocke... §rehh... §cGlühstrumpf!"; break;
                    case 1: subtitle = "§cGut gespielt!"; break;
                    case 2: subtitle = "§cDu bist ein Profi!"; break;
                    default: subtitle = "§cGlückwunsch!";
                }
                winner.sendTitle("§aErster!!!", subtitle, 30, 30, 30);
                winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                this.startWaiting();
            }
            this.stateTicks++;
        }
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        World world = event.getWorld();
        if (!world.getName().equals("world")) return;

        WorldBorder worldBorder = world.getWorldBorder();
        worldBorder.setCenter(0.5, 0.5);
        worldBorder.setSize(this.gameFieldRadius*2+1);
        worldBorder.setWarningDistance(0);

        world.setAutoSave(false);  // disable auto saving
        world.setViewDistance(2);
        world.setPVP(false);

        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.FALL_DAMAGE, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(600);
    }
}

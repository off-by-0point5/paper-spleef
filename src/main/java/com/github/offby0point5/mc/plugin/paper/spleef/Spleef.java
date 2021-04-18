package com.github.offby0point5.mc.plugin.paper.spleef;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import javax.swing.plaf.basic.BasicButtonUI;

public final class Spleef extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        // todo generate empty world
        // todo set gamerules
        this.getServer().getPluginManager().registerEvents(new SpleefEvents(), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        for (World world : Bukkit.getServer().getWorlds()) {
            Bukkit.getServer().unloadWorld(world, false);
        }
    }
}

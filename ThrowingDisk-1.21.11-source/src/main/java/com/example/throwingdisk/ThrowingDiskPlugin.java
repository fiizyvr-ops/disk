package com.example.throwingdisk;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ThrowingDiskPlugin extends JavaPlugin implements Listener {
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 1200;
    private static final double DAMAGE = 6.0;
    private static final double SPEED = 1.15;
    private static final int OUTBOUND_TICKS = 24;
    private static final int MAX_BOUNCES = 4;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ThrowingDisk enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("throwingdisk.use")) {
            player.sendMessage("You don't have permission.");
            return true;
        }

        ItemStack disk = new ItemStack(Material.MUSIC_DISC_13);
        ItemMeta meta = disk.getItemMeta();
        meta.setDisplayName("§bThrowing Disk");
        meta.setLore(java.util.List.of("§7Right-click to throw", "§7Bounces off walls and returns"));
        disk.setItemMeta(meta);
        player.getInventory().addItem(disk).values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage("§bYou received a Throwing Disk.");
        return true;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.MUSIC_DISC_13 || !isDisk(item)) return;

        event.setCancelled(true);
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < COOLDOWN_MS) return;
        cooldowns.put(player.getUniqueId(), now);

        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);

        launch(player);
    }

    private boolean isDisk(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        return "§bThrowing Disk".equals(item.getItemMeta().getDisplayName());
    }

    private void launch(Player owner) {
        World world = owner.getWorld();
        Vector direction = owner.getEyeLocation().getDirection().normalize();
        var start = owner.getEyeLocation().add(direction.clone().multiply(0.8));

        ItemDisplay display = world.spawn(start, ItemDisplay.class, entity -> {
            entity.setItemStack(new ItemStack(Material.MUSIC_DISC_13));
            entity.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
            entity.setPersistent(false);
            entity.setInterpolationDuration(1);
        });

        new DiskFlight(owner, display, direction).start();
        world.playSound(owner.getLocation(), Sound.ENTITY_TRIDENT_THROW, 0.8f, 1.4f);
    }

    private final class DiskFlight {
        private final Player owner;
        private final ItemDisplay display;
        private final Set<UUID> hitEntities = new HashSet<>();
        private Vector velocity;
        private int ticks;
        private int bounces;
        private boolean returning;

        private DiskFlight(Player owner, ItemDisplay display, Vector velocity) {
            this.owner = owner;
            this.display = display;
            this.velocity = velocity.clone().normalize().multiply(SPEED);
        }

        private void start() {
            Bukkit.getScheduler().runTaskTimer(ThrowingDiskPlugin.this, task -> {
                if (!display.isValid() || !owner.isOnline() || !owner.getWorld().equals(display.getWorld())) {
                    display.remove();
                    task.cancel();
                    return;
                }

                ticks++;
                if (!returning && ticks >= OUTBOUND_TICKS) returning = true;

                if (returning) {
                    Vector toOwner = owner.getEyeLocation().toVector()
                            .subtract(display.getLocation().toVector());
                    if (toOwner.lengthSquared() < 1.5) {
                        display.remove();
                        owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.6f);
                        task.cancel();
                        return;
                    }
                    velocity = toOwner.normalize().multiply(SPEED * 1.15);
                }

                var current = display.getLocation();
                Vector step = velocity.clone();
                double distance = step.length();
                Vector unit = step.clone().normalize();

                RayTraceResult blockHit = display.getWorld().rayTraceBlocks(
                        current, unit, distance + 0.15,
                        org.bukkit.FluidCollisionMode.NEVER, true);

                if (blockHit != null && blockHit.getHitBlockFace() != null && bounces < MAX_BOUNCES) {
                    Vector normal = blockHit.getHitBlockFace().getDirection().normalize();
                    velocity.subtract(normal.multiply(2 * velocity.dot(normal)));
                    velocity.multiply(0.92);
                    bounces++;
                    display.teleport(blockHit.getHitPosition().toLocation(display.getWorld()).add(normal.multiply(0.12)));
                    display.getWorld().spawnParticle(Particle.CRIT, display.getLocation(), 8, 0.12, 0.12, 0.12, 0.05);
                    display.getWorld().playSound(display.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.7f, 1.5f);
                } else if (blockHit != null) {
                    returning = true;
                } else {
                    var next = current.clone().add(step);
                    display.teleport(next);
                }

                display.setRotation(display.getLocation().getYaw() + 35f, display.getLocation().getPitch());

                for (var nearby : display.getWorld().getNearbyEntities(display.getLocation(), 0.65, 0.65, 0.65)) {
                    if (!(nearby instanceof LivingEntity target)) continue;
                    if (target.getUniqueId().equals(owner.getUniqueId())) continue;
                    if (!hitEntities.add(target.getUniqueId())) continue;
                    target.damage(DAMAGE, owner);
                    target.setNoDamageTicks(0);
                    display.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0),
                            8, 0.2, 0.25, 0.2, 0.08);
                    display.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
                }

                if (ticks > 100) {
                    display.remove();
                    task.cancel();
                }
            }, 1L, 1L);
        }
    }
}

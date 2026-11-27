package com.brunojaegerk.excellentenchants;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySpawner;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityShootBowEvent;
import cn.nukkit.event.entity.ProjectileHitEvent;
import cn.nukkit.event.inventory.RepairItemEvent;
import cn.nukkit.event.player.PlayerMoveEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.Tag;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.potion.Effect;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ExcellentEnchantsNK extends PluginBase implements Listener {

    private static final String ROOT_TAG = "ExcellentEnchantsNK";
    private static final String BOOK_FLAG = "ee_book";
    private static final int BOOK_MARKER_ENCHANT = 255;

    private final Map<Long, Integer> confusingProjectiles = new ConcurrentHashMap<>();
    private final Set<String> tunnelMiningPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> activeLavaBlocks = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        // Carga la configuración y registra los eventos que controlan los encantamientos.
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        // Renueva Velocidad incluso cuando el jugador permanece quieto y conserva el efecto mientras use las botas.
        getServer().getScheduler().scheduleRepeatingTask(this, () -> {
            for (Player player : getServer().getOnlinePlayers().values()) {
                int level = getLevel(player.getInventory().getBoots(), EnchantType.SPEED);
                if (level > 0) {
                    player.addEffect(Effect.getEffect(Effect.SPEED).setDuration(45).setAmplifier(level - 1).setVisible(false));
                }
            }
        }, 20);
        getLogger().info("ExcellentEnchantsNK habilitado. Se cargaron 9 encantamientos.");
    }

    @Override
    public void onDisable() {
        confusingProjectiles.clear();
        tunnelMiningPlayers.clear();
        activeLavaBlocks.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Todos los comandos son administrativos: no existe una forma de obtener libros mediante loot o generación natural.
        if (!command.getName().equalsIgnoreCase("eenchant")) {
            return false;
        }
        if (!sender.isOp() && !sender.hasPermission("excellentenchants.admin")) {
            sender.sendMessage(msg("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/eenchant add <encantamiento> [nivel]");
            sender.sendMessage("§e/eenchant book <jugador> <encantamiento> [nivel]");
            sender.sendMessage("§e/eenchant give <jugador> <encantamiento> [nivel]");
            sender.sendMessage("§7Encantamientos: " + String.join(", ", EnchantType.names()));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("add")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(msg("player-only"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§cUso: /eenchant add <encantamiento> [nivel]");
                return true;
            }
            Player player = (Player) sender;
            Item held = player.getInventory().getItemInHand();
            EnchantType type = EnchantType.fromInput(args[1]);
            int level = parseLevel(args, 2, type, sender);
            if (type == null || level < 1) return true;
            if (!isValidTarget(type, held)) {
                sender.sendMessage(msg("invalid-item"));
                return true;
            }
            applyEnchant(held, type, level);
            player.getInventory().setItemInHand(held);
            player.sendMessage(msg("enchant-added").replace("{enchant}", type.display).replace("{level}", String.valueOf(level)));
            return true;
        }

        if (sub.equals("book") || sub.equals("give")) {
            if (args.length < 3) {
                sender.sendMessage("§cUso: /eenchant " + sub + " <jugador> <encantamiento> [nivel]");
                return true;
            }
            Player target = getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(msg("player-not-found"));
                return true;
            }
            EnchantType type = EnchantType.fromInput(args[2]);
            int level = parseLevel(args, 3, type, sender);
            if (type == null || level < 1) return true;
            Item book = createBook(type, level);
            target.getInventory().addItem(book);
            target.sendMessage(msg("book-received").replace("{enchant}", type.display).replace("{level}", String.valueOf(level)));
            sender.sendMessage(msg("book-given").replace("{player}", target.getName()));
            return true;
        }

        sender.sendMessage("§cSubcomando desconocido. Usa /eenchant");
        return true;
    }

    private int parseLevel(String[] args, int index, EnchantType type, CommandSender sender) {
        int max = type == null ? 3 : getConfig().getInt("max-levels." + type.key, 3);
        int level = 1;
        if (args.length > index) {
            try {
                level = Integer.parseInt(args[index]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(msg("invalid-level"));
                return -1;
            }
        }
        if (level < 1 || level > max) {
            sender.sendMessage(msg("level-range").replace("{max}", String.valueOf(max)));
            return -1;
        }
        return level;
    }

    @EventHandler
    public void onRepairItem(RepairItemEvent event) {
        // El libro lleva un marcador NBT desconocido para los encantamientos vanilla. PM1E permite así que el yunque
        // acepte el libro; aquí transformamos el resultado en el objeto con nuestro encantamiento real.
        Item book = event.getMaterialItem();
        if (book == null || book.getId() != Item.ENCHANTED_BOOK || !isCustomBook(book)) return;

        Item target = event.getOldItem();
        Item result = event.getNewItem();
        CompoundTag bookData = getRoot(book);
        CompoundTag enchants = bookData.getCompound("enchants");
        String key = firstEnchantKey(enchants);
        EnchantType type = EnchantType.fromInput(key);
        int level = enchants.getInt(key);
        if (type == null || level < 1 || !isValidTarget(type, target)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(msg("invalid-anvil-target"));
            return;
        }

        // Conserva los NBT del objeto original y añade el encantamiento al resultado que el yunque va a entregar.
        applyEnchant(result, type, level);
        updateLore(result);
        event.setCost(Math.max(1, event.getCost() + level));
        event.getPlayer().sendMessage(msg("anvil-success").replace("{enchant}", type.display));
    }

    @EventHandler
    public void onElementalProtection(EntityDamageEvent event) {
        // Protección Elemental reduce daño mágico, de fuego, lava, magma y rayos si el jugador lleva armadura encantada.
        if (!(event.getEntity() instanceof Player) || !isElementalDamage(event.getCause())) return;
        Player player = (Player) event.getEntity();
        int level = 0;
        for (Item armor : player.getInventory().getArmorContents()) {
            level = Math.max(level, getLevel(armor, EnchantType.ELEMENTAL_PROTECTION));
        }
        if (level > 0) {
            double reduction = Math.min(0.75, getConfig().getDouble("elemental-protection.reduction-per-level", 0.15) * level);
            event.setDamage((float) (event.getDamage() * (1.0 - reduction)));
        }
    }

    @EventHandler
    public void onMagmaImmunity(EntityDamageEvent event) {
        // Caminador de Llamas también protege del daño de los bloques de magma, pero no concede inmunidad a toda la lava.
        if (!(event.getEntity() instanceof Player) || event.getCause() != EntityDamageEvent.DamageCause.MAGMA) return;
        Player player = (Player) event.getEntity();
        if (getLevel(player.getInventory().getBoots(), EnchantType.FLAME_WALKER) > 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Velocidad se mantiene con un efecto corto renovado al moverse; no se modifica la velocidad global del jugador.
        Player player = event.getPlayer();
        Item boots = player.getInventory().getBoots();
        int speedLevel = getLevel(boots, EnchantType.SPEED);
        if (speedLevel > 0) {
            int amplifier = speedLevel - 1;
            if (!player.hasEffect(Effect.SPEED) || player.getEffect(Effect.SPEED).getAmplifier() < amplifier) {
                player.addEffect(Effect.getEffect(Effect.SPEED).setDuration(45).setAmplifier(amplifier).setVisible(false));
            }
        }

        // Flame Walker convierte temporalmente la lava que queda justo bajo los pies en magma y luego la restaura.
        int flameLevel = getLevel(boots, EnchantType.FLAME_WALKER);
        if (flameLevel > 0 && event.getTo() != null) {
            int x = event.getTo().getFloorX();
            int y = event.getTo().getFloorY() - 1;
            int z = event.getTo().getFloorZ();
            Level level = player.getLevel();
            Block under = level.getBlock(x, y, z);
            if (under.getId() == Block.LAVA || under.getId() == Block.STILL_LAVA) {
                String key = System.identityHashCode(level) + ":" + x + ":" + y + ":" + z;
                if (activeLavaBlocks.add(key)) {
                    int oldId = under.getId();
                    int oldDamage = under.getDamage();
                    Vector3 position = new Vector3(x, y, z);
                    level.setBlock(position, Block.get(Block.MAGMA), true);
                    getServer().getScheduler().scheduleDelayedTask(this, () -> {
                        Block current = level.getBlock(x, y, z);
                        if (current.getId() == Block.MAGMA) {
                            level.setBlock(position, Block.get(oldId, oldDamage), true);
                        }
                        activeLavaBlocks.remove(key);
                    }, getConfig().getInt("flame-walker.duration-ticks", 50) + (flameLevel - 1) * 10);
                }
            }
        }
    }

    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        // La información viaja asociada al proyectil, no al jugador: cambiar de objeto después de disparar no altera la flecha.
        int level = getLevel(event.getBow(), EnchantType.CONFUSING_ARROWS);
        if (level > 0 && event.getProjectile() != null) {
            confusingProjectiles.put(event.getProjectile().getId(), level);
        }
    }

    @EventHandler
    public void onProjectileDamage(EntityDamageByEntityEvent event) {
        // Las flechas confusas aplican Náusea al objetivo cuando el proyectil causa daño.
        if (event.getDamager() instanceof EntityProjectile) {
            EntityProjectile projectile = (EntityProjectile) event.getDamager();
            Integer level = confusingProjectiles.remove(projectile.getId());
            if (level != null) {
                int duration = getConfig().getInt("confusing-arrows.duration-ticks", 100) + (level - 1) * 40;
                event.getEntity().addEffect(Effect.getEffect(Effect.NAUSEA).setDuration(duration).setAmplifier(0));
            }
            return;
        }

        // Los encantamientos de arma cuerpo a cuerpo solo se activan cuando el atacante es un jugador.
        if (!(event.getDamager() instanceof Player) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        Player attacker = (Player) event.getDamager();
        Item weapon = attacker.getInventory().getItemInHand();

        int blindness = getLevel(weapon, EnchantType.BLINDNESS);
        if (blindness > 0) {
            int duration = getConfig().getInt("blindness.duration-ticks", 60) + (blindness - 1) * 30;
            event.getEntity().addEffect(Effect.getEffect(Effect.BLINDNESS).setDuration(duration).setAmplifier(0));
        }

        int doubleStrike = getLevel(weapon, EnchantType.DOUBLE_STRIKE);
        if (doubleStrike > 0) {
            double multiplier = getConfig().getDouble("double-strike.damage-multiplier", 2.0) + (doubleStrike - 1) * 0.25;
            event.setDamage((float) (event.getDamage() * multiplier));
        }

        int vampire = getLevel(weapon, EnchantType.VAMPIRE);
        if (vampire > 0) {
            float heal = (float) (event.getDamage() * Math.min(1.0, getConfig().getDouble("vampire.heal-fraction", 0.25) + (vampire - 1) * 0.05));
            if (heal > 0 && attacker.isAlive()) {
                attacker.heal(heal);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        // Limpia la referencia de las flechas que impactan en un bloque y evita acumular datos de proyectiles fallidos.
        EntityProjectile projectile = event.getEntity() instanceof EntityProjectile ? (EntityProjectile) event.getEntity() : null;
        if (projectile != null && confusingProjectiles.containsKey(projectile.getId())) {
            getServer().getScheduler().scheduleDelayedTask(this, () -> confusingProjectiles.remove(projectile.getId()), 2);
        }
    }

    @EventHandler
    public void onSpawnerTouch(BlockBreakEvent event) {
        // Toque de Seda recupera el NBT completo del spawner, incluido EntityId y sus parámetros de aparición.
        if (event.isCancelled() || event.getBlock().getId() != Block.MONSTER_SPAWNER) return;

        Player player = event.getPlayer();
        Item tool = player.getInventory().getItemInHand();
        if (getLevel(tool, EnchantType.SPAWNER_TOUCH) <= 0) return;

        Block spawnerBlock = event.getBlock();
        int x = spawnerBlock.getFloorX();
        int y = spawnerBlock.getFloorY();
        int z = spawnerBlock.getFloorZ();
        Vector3 position = new Vector3(x, y, z);

        BlockEntity tile = player.getLevel().getBlockEntity(position);
        if (!(tile instanceof BlockEntitySpawner)) return;

        BlockEntitySpawner spawner = (BlockEntitySpawner) tile;
        spawner.saveNBT();
        CompoundTag savedSpawner = spawner.namedTag.clone();

        Item spawnerItem = Item.get(Block.MONSTER_SPAWNER, 0, 1);
        CompoundTag itemTag = spawnerItem.getNamedTag();
        if (itemTag == null) itemTag = new CompoundTag();
        itemTag.putCompound("ExcellentEnchantsNKSpawner", savedSpawner);
        spawnerItem.setNamedTag(itemTag);

        // Se cancela la rotura vanilla para evitar drops duplicados y se conserva el tipo de mob en el objeto.
        event.setCancelled(true);
        player.getLevel().setBlock(position, Block.get(Block.AIR), true);
        player.getLevel().dropItem(position.add(0.5, 0.5, 0.5), spawnerItem);
    }

    @EventHandler
    public void onSpawnerPlace(BlockPlaceEvent event) {
        // El spawner colocado se restaura después de que PM1E cree su BlockEntity predeterminado.
        if (event.isCancelled() || event.getBlock().getId() != Block.MONSTER_SPAWNER) return;

        Item placedItem = event.getItem();
        CompoundTag itemTag = placedItem == null ? null : placedItem.getNamedTag();
        if (itemTag == null || !itemTag.contains("ExcellentEnchantsNKSpawner")) return;

        CompoundTag savedSpawner = itemTag.getCompound("ExcellentEnchantsNKSpawner").clone();
        Block block = event.getBlock();
        int x = block.getFloorX();
        int y = block.getFloorY();
        int z = block.getFloorZ();

        getServer().getScheduler().scheduleDelayedTask(this, () -> {
            // No se restaura si el bloque fue retirado o sustituido antes de ejecutar la tarea.
            if (block.getLevel().getBlockIdAt(x, y, z) != Block.MONSTER_SPAWNER) return;

            savedSpawner.putString("id", BlockEntity.MOB_SPAWNER);
            savedSpawner.putInt("x", x);
            savedSpawner.putInt("y", y);
            savedSpawner.putInt("z", z);

            BlockEntity current = block.getLevel().getBlockEntity(new Vector3(x, y, z));
            if (current != null) current.close();

            // BlockEntitySpawner utiliza EntityId para cualquier tipo de entidad que PM1E tenga registrado.
            BlockEntity.createBlockEntity(BlockEntity.MOB_SPAWNER, block.getChunk(), savedSpawner);
        }, 1);
    }

    @EventHandler
    public void onTunnelBreak(BlockBreakEvent event) {
        // El minado adicional usa useBreakOn para que PM1E vuelva a disparar BlockBreakEvent y respete protecciones externas.
        if (event.getBlock().getId() == 52) return;
        Player player = event.getPlayer();
        String playerKey = player.getName().toLowerCase(Locale.ROOT);
        if (tunnelMiningPlayers.contains(playerKey)) return;

        int level = getLevel(player.getInventory().getItemInHand(), EnchantType.TUNNEL);
        if (level <= 0) return;

        int width = level;
        int height = level == 1 ? 2 : level;
        int startSide = -(width / 2);
        int startY = level == 1 ? 0 : -(height / 2);
        Block center = event.getBlock();
        Item tool = player.getInventory().getItemInHand();
        Vector3 look = player.getDirectionVector();
        boolean facingX = Math.abs(look.x) > Math.abs(look.z);
        int sideX = facingX ? 0 : 1;
        int sideZ = facingX ? 1 : 0;
        tunnelMiningPlayers.add(playerKey);
        try {
            for (int side = startSide; side < startSide + width; side++) {
                for (int dy = startY; dy < startY + height; dy++) {
                    if (side == 0 && dy == 0) continue;
                    int x = center.getFloorX() + side * sideX;
                    int y = center.getFloorY() + dy;
                    int z = center.getFloorZ() + side * sideZ;
                    Block extra = player.getLevel().getBlock(x, y, z);
                    if (extra.getId() == Block.AIR || extra.getId() == Block.BEDROCK) continue;
                    player.getLevel().useBreakOn(new Vector3(x, y, z), tool, player);
                }
            }
        } finally {
            tunnelMiningPlayers.remove(playerKey);
        }
    }

    private boolean isElementalDamage(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.MAGIC
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.MAGMA
                || cause == EntityDamageEvent.DamageCause.LIGHTNING;
    }

    private boolean isCustomBook(Item item) {
        CompoundTag tag = item.getNamedTag();
        return tag != null && tag.getInt(BOOK_FLAG) == 1 && tag.contains(ROOT_TAG);
    }

    private Item createBook(EnchantType type, int level) {
        // Los libros se crean exclusivamente desde el comando; el marcador NBT permite que el yunque los reconozca.
        Item book = Item.get(Item.ENCHANTED_BOOK, 0, 1);
        CompoundTag root = new CompoundTag(ROOT_TAG).putInt(type.key, level);
        CompoundTag data = new CompoundTag()
                .putInt(BOOK_FLAG, 1)
                .putCompound(ROOT_TAG, new CompoundTag("enchants").putInt(type.key, level));
        ListTag<CompoundTag> marker = new ListTag<>("ench");
        marker.add(new CompoundTag().putShort("id", (short) BOOK_MARKER_ENCHANT).putShort("lvl", (short) 1));
        data.putList(marker);
        book.setNamedTag(data);
        book.setCustomName("§r§dLibro de " + type.display + " " + roman(level));
        book.setLore("§7Encantamiento personalizado", "§e" + type.display + " " + roman(level), "§8Aplicable en un yunque");
        return book;
    }

    private void applyEnchant(Item item, EnchantType type, int level) {
        // Los encantamientos se guardan en una etiqueta propia y no ocupan IDs de encantamientos vanilla.
        CompoundTag tag = item.getNamedTag();
        if (tag == null) tag = new CompoundTag();
        CompoundTag root = tag.contains(ROOT_TAG) ? tag.getCompound(ROOT_TAG) : new CompoundTag(ROOT_TAG);
        root.putInt(type.key, level);
        tag.putCompound(ROOT_TAG, root);
        item.setNamedTag(tag);
        updateLore(item);
    }

    private int getLevel(Item item, EnchantType type) {
        if (item == null || item.isNull()) return 0;
        CompoundTag tag = item.getNamedTag();
        if (tag == null || !tag.contains(ROOT_TAG)) return 0;
        return tag.getCompound(ROOT_TAG).getInt(type.key);
    }

    private CompoundTag getRoot(Item item) {
        CompoundTag tag = item.getNamedTag();
        return tag == null || !tag.contains(ROOT_TAG) ? new CompoundTag(ROOT_TAG) : tag.getCompound(ROOT_TAG);
    }

    private String firstEnchantKey(CompoundTag tag) {
        for (EnchantType type : EnchantType.values()) {
            if (tag.contains(type.key)) return type.key;
        }
        return "";
    }

    private void updateLore(Item item) {
        List<String> lore = new ArrayList<>();
        for (String line : item.getLore()) {
            if (!line.contains("§8✦")) lore.add(line);
        }
        for (EnchantType type : EnchantType.values()) {
            int level = getLevel(item, type);
            if (level > 0) lore.add("§8✦ §d" + type.display + " " + roman(level));
        }
        item.setLore(lore.toArray(new String[0]));
    }

    private boolean isValidTarget(EnchantType type, Item item) {
        if (item == null || item.isNull()) return false;
        int id = item.getId();
        switch (type) {
            case ELEMENTAL_PROTECTION:
                return isArmor(id);
            case FLAME_WALKER:
            case SPEED:
                return id == Item.LEATHER_BOOTS || id == Item.CHAIN_BOOTS || id == Item.IRON_BOOTS
                        || id == Item.GOLD_BOOTS || id == Item.DIAMOND_BOOTS || id == Item.NETHERITE_BOOTS;
            case CONFUSING_ARROWS:
                return id == Item.BOW || id == Item.CROSSBOW;
            case TUNNEL:
            case SPAWNER_TOUCH:
                return id == Item.WOODEN_PICKAXE || id == Item.STONE_PICKAXE || id == Item.IRON_PICKAXE
                        || id == Item.GOLD_PICKAXE || id == Item.DIAMOND_PICKAXE || id == Item.NETHERITE_PICKAXE;
            case BLINDNESS:
            case DOUBLE_STRIKE:
            case VAMPIRE:
                return id == Item.WOODEN_SWORD || id == Item.STONE_SWORD || id == Item.IRON_SWORD
                        || id == Item.GOLD_SWORD || id == Item.DIAMOND_SWORD || id == Item.NETHERITE_SWORD
                        || id == Item.WOODEN_AXE || id == Item.STONE_AXE || id == Item.IRON_AXE
                        || id == Item.GOLD_AXE || id == Item.DIAMOND_AXE || id == Item.NETHERITE_AXE;
            default:
                return false;
        }
    }

    private boolean isArmor(int id) {
        return id == Item.LEATHER_HELMET || id == Item.LEATHER_CHESTPLATE || id == Item.LEATHER_LEGGINGS || id == Item.LEATHER_BOOTS
                || id == Item.CHAIN_HELMET || id == Item.CHAIN_CHESTPLATE || id == Item.CHAIN_LEGGINGS || id == Item.CHAIN_BOOTS
                || id == Item.IRON_HELMET || id == Item.IRON_CHESTPLATE || id == Item.IRON_LEGGINGS || id == Item.IRON_BOOTS
                || id == Item.GOLD_HELMET || id == Item.GOLD_CHESTPLATE || id == Item.GOLD_LEGGINGS || id == Item.GOLD_BOOTS
                || id == Item.DIAMOND_HELMET || id == Item.DIAMOND_CHESTPLATE || id == Item.DIAMOND_LEGGINGS || id == Item.DIAMOND_BOOTS
                || id == Item.NETHERITE_HELMET || id == Item.NETHERITE_CHESTPLATE || id == Item.NETHERITE_LEGGINGS || id == Item.NETHERITE_BOOTS;
    }

    private String msg(String key) {
        return getConfig().getString("messages." + key, "§cMensaje no configurado: " + key).replace("{prefix}", getConfig().getString("messages.prefix", "§8[§dExcellentEnchants§8] "));
    }

    private String roman(int level) {
        return level == 1 ? "I" : level == 2 ? "II" : level == 3 ? "III" : String.valueOf(level);
    }

    private enum EnchantType {
        ELEMENTAL_PROTECTION("elemental_protection", "Protección Elemental", "proteccion_elemental"),
        FLAME_WALKER("flame_walker", "Caminador de Llamas", "caminador_de_llamas"),
        SPEED("speed", "Velocidad", "velocidad"),
        CONFUSING_ARROWS("confusing_arrows", "Flechas Confusas", "flechas_confusas"),
        TUNNEL("tunnel", "Túnel", "tunel"),
        BLINDNESS("blindness", "Ceguera", "ceguera"),
        DOUBLE_STRIKE("double_strike", "Doble Strike", "doble_strike"),
        VAMPIRE("vampire", "Vampiro", "vampiro"),
        SPAWNER_TOUCH("spawner_touch", "Toque de Seda", "toque_de_seda");

        private final String key;
        private final String display;
        private final String alias;

        EnchantType(String key, String display, String alias) {
            this.key = key;
            this.display = display;
            this.alias = alias;
        }

        private static EnchantType fromInput(String input) {
            String normalized = input.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            for (EnchantType type : values()) {
                if (type.key.equals(normalized) || type.alias.equals(normalized)
                        || type.display.toLowerCase(Locale.ROOT).replace(' ', '_').equals(normalized)) return type;
            }
            return null;
        }

        private static List<String> names() {
            List<String> names = new ArrayList<>();
            for (EnchantType type : values()) names.add(type.key);
            return names;
        }
    }
}

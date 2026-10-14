package sonar.ui;

import sonar.packets.data.StatData;
import sonar.packets.data.enums.StatType;
import sonar.backend.data.CrucibleBonusManager;
import sonar.backend.data.Entity;
import sonar.backend.data.Projectile;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;

/**
 * Checks the user's own damage against what the server says happened.
 * <p>
 * The server tells a client the damage of other players' hits, but never the
 * client's own: those the client works out itself, and the server may not
 * register every one of them. What the server does report is each enemy's
 * health. So for every enemy this ledger keeps the health it was seen to
 * lose, the damage the server reported for everyone else, and the damage
 * calculated for the user. The user's confirmed damage is the health lost
 * that nobody else accounts for.
 * <p>
 * With a folder named "audit" next to the app, the ledger is also written to
 * files there for inspection. All methods run on the packet thread and never
 * throw into it.
 */
public final class DamageAudit {

    private static final int MAX_HITS_LOGGED = 20000;
    /** A rise in health this soon after a hit happened mid-fight, so damage may be hidden behind it. */
    private static final long IN_COMBAT_MS = 1500;
    /** Off unless a folder named "audit" exists next to the app, so ordinary installs write nothing. */
    private static final boolean WRITE_FILES = new File("audit").isDirectory();

    private static final class Tally {
        String name = "";
        int maxHp, lastHp = -1;
        /** Health left according to the last tick, lowered by each hit since, so several hits can not all kill the same enemy. */
        long remaining = -1;
        long lastHitAt;
        long hpLost;              // every drop in health seen between two ticks, plus the rest of a killed enemy's health
        long hpRegainedInCombat;  // rises seen while the enemy was being hit
        long hpRegained;          // all rises
        long ownCalculated;       // what the client calculated for the user
        long ownEffective;        // the same, without damage beyond the health the enemy had left
        long ownBase;             // the same hits before defence
        int ownHits, ownHitsDefMissing;
        long ownFromServer;       // damage the server reported for the user; would mean double counting
        int ownFromServerHits;
        long othersEffective;     // other players' server reported damage, without overkill
        long othersReported;
        int othersHits;
        long unownedEffective;    // pets and unmapped summons
        boolean dropped;
        int def = Integer.MIN_VALUE;
        int conditionsSeen0, conditionsSeen1;

        /** Takes a hit off the health the enemy is believed to have left and returns the part that fits. */
        long apply(long amount) {
            lastHitAt = System.currentTimeMillis();
            if (remaining < 0) return amount;
            long fits = Math.min(amount, remaining);
            remaining -= fits;
            return fits;
        }

        /**
         * The user's damage as the server's numbers support it, or -1 when the
         * enemy's health was never seen.
         */
        long confirmedOwn() {
            if (lastHp < 0 || maxHp <= 0) return -1;
            // Hits without a known owner are not taken off the user's share: besides pets and summons they
            // can be the user's own hits, which the server sometimes sends back with the owner hidden.
            if (othersEffective == 0) {
                // no other player touched it: what it lost was the user's doing, plus back any regen seen mid-fight
                // (same compensation the group formula below already applies - a target that heals between two
                // health samples would otherwise silently eat into the user's own confirmed damage), up to what
                // the user's shots could do at most
                return Math.max(0, Math.min(hpLost + hpRegainedInCombat, Math.max(ownBase, ownCalculated)));
            }
            long unexplained = hpLost + hpRegainedInCombat - othersEffective;
            return Math.max(0, Math.min(ownEffective, unexplained));
        }
    }

    private static final LinkedHashMap<Integer, Tally> tallies = new LinkedHashMap<>();
    private static Object instance;
    private static String instanceName = "";
    private static long startedAt;
    private static long lastWrite;
    private static PrintWriter hits;
    private static int hitsLogged;
    private static String userInfo = "";

    private DamageAudit() {
    }

    private static File folder() {
        return new File("audit");
    }

    private static Tally tally(Entity target) {
        Tally t = tallies.get(target.id);
        if (t == null) {
            t = new Tally();
            tallies.put(target.id, t);
            look(t, target);
        }
        String n = target.name();
        if (n != null && !n.isEmpty()) t.name = n;
        return t;
    }

    /** Notes the enemy's health as sent by the server. */
    private static void look(Tally t, Entity target) {
        int max = target.maxHp();
        if (max > t.maxHp) t.maxHp = max;
        if (target.stat == null || target.stat.get(StatType.HP_STAT) == null) return;
        int hp = target.hp();
        if (t.lastHp >= 0 && hp != t.lastHp) {
            if (hp < t.lastHp) {
                t.hpLost += t.lastHp - hp;
            } else {
                t.hpRegained += hp - t.lastHp;
                if (System.currentTimeMillis() - t.lastHitAt < IN_COMBAT_MS) t.hpRegainedInCombat += hp - t.lastHp;
            }
        }
        if (hp != t.lastHp || t.remaining < 0) t.remaining = hp;
        t.lastHp = hp;
    }

    /**
     * One hit the user's client calculated itself.
     *
     * @param calculated Damage credited to the user for this hit.
     */
    public static void ownHit(Entity target, Entity attacker, Projectile projectile, int calculated) {
        try {
            if (target == null || attacker == null || !attacker.isUser() || projectile == null) return;
            Tally t = tally(target);
            long before = t.remaining;
            StatData def = target.stat == null ? null : target.stat.get(StatType.DEFENSE_STAT);
            StatData c0 = target.stat == null ? null : target.stat.get(StatType.CONDITION_STAT);
            StatData c1 = target.stat == null ? null : target.stat.get(StatType.NEW_CON_STAT);
            t.ownHits++;
            t.ownCalculated += calculated;
            t.ownEffective += t.apply(calculated);
            t.ownBase += projectile.getDamage();
            if (def == null) t.ownHitsDefMissing++;
            else t.def = def.statValue;
            if (c0 != null) t.conditionsSeen0 |= c0.statValue;
            if (c1 != null) t.conditionsSeen1 |= c1.statValue;
            if (!WRITE_FILES) return;
            if (hitsLogged < MAX_HITS_LOGGED && hits != null) {
                hitsLogged++;
                hits.println(System.currentTimeMillis() + "," + target.id + "," + t.name.replace(',', ' ') + "," + projectile.getDamage() + ","
                        + (projectile.isArmorPiercing() ? 1 : 0) + "," + (def == null ? "" : String.valueOf(def.statValue)) + ","
                        + (c0 == null ? "" : Integer.toHexString(c0.statValue)) + "," + (c1 == null ? "" : Integer.toHexString(c1.statValue)) + ","
                        + calculated + "," + before + "," + t.lastHp + "," + target.maxHp() + "," + projectile.getContainerType() + "," + projectile.getSummonerId());
                if (hitsLogged % 50 == 0) hits.flush();
            }
            StatData atk = attacker.stat.get(StatType.ATTACK_STAT), exalt = attacker.stat.get(StatType.EXALTATION_BONUS_DAMAGE), cond = attacker.stat.get(StatType.CONDITION_STAT);
            userInfo = "attack=" + (atk == null ? "?" : atk.statValue) + " exaltBonusDamage=" + (exalt == null ? "?" : exalt.statValue)
                    + " crucibleMultiplier=" + CrucibleBonusManager.getPlayerDamageMultiplier()
                    + " weaponMultiplier=" + attacker.playerStatsMultiplier()
                    + " conditions=" + (cond == null ? "?" : Integer.toHexString(cond.statValue));
        } catch (RuntimeException ignored) {
            // the ledger must never interrupt packet handling
        }
    }

    /** One hit whose damage the server reported (a Damage packet). */
    public static void serverHit(Entity target, Entity attacker, int amount) {
        try {
            if (target == null || amount <= 0) return;
            Tally t = tally(target);
            if (attacker != null && attacker.isUser()) {
                t.ownFromServer += amount;
                t.ownFromServerHits++;
                return;
            }
            long fits = t.apply(amount);
            if (attacker == null) {
                t.unownedEffective += fits;
            } else {
                t.othersEffective += fits;
                t.othersReported += amount;
                t.othersHits++;
            }
        } catch (RuntimeException ignored) {
            // see ownHit
        }
    }

    /** An enemy that had been hit left the world, which for a hit enemy nearly always means it died. */
    public static void dropped(Entity target) {
        try {
            if (target == null) return;
            Tally t = tallies.get(target.id);
            if (t == null || t.dropped || t.lastHp <= 0) return;
            t.dropped = true;
            // only as much of its remaining health as the hits since the last tick can explain,
            // so an enemy that was merely left behind does not count as killed
            long claimed = t.ownEffective + t.othersEffective + t.unownedEffective;
            long pending = claimed - t.hpLost;
            if (pending > 0) t.hpLost += Math.min(pending, t.lastHp);
        } catch (RuntimeException ignored) {
            // see ownHit
        }
    }

    /**
     * The user's damage on one enemy as the server's numbers support it.
     *
     * @return The confirmed damage, or -1 when there is nothing to check against
     * (another instance, or an enemy whose health was never seen).
     */
    /** True when no other player's hit on the enemy was seen, so its lost health is the user's doing. */
    static boolean alone(Object map, int enemyId) {
        if (map == null || map != instance) return false;
        Tally t = tallies.get(enemyId);
        return t != null && t.othersEffective == 0;
    }

    static long confirmedOwn(Object map, int enemyId) {
        if (map == null || map != instance) return -1;
        Tally t = tallies.get(enemyId);
        return t == null ? -1 : t.confirmedOwn();
    }

    /**
     * Every point of health this enemy was actually seen to lose across the
     * whole fight, or -1 when its health was never seen.
     * <p>
     * This is the honest size of a fight. An enemy's "max health" is only what
     * it reports right now: a boss that refills between phases, or whose health
     * the game rescales as players come and go, will be seen to lose far more
     * than that single figure - which is why damage dealt can legitimately come
     * out larger than the boss's own full health.
     */
    static long healthLost(Object map, int enemyId) {
        if (map == null || map != instance) return -1;
        Tally t = tallies.get(enemyId);
        return t == null ? -1 : t.hpLost;
    }

    /** Health this enemy was seen to regain while being fought: phase refills and healing. -1 when unknown. */
    static long healthRegained(Object map, int enemyId) {
        if (map == null || map != instance) return -1;
        Tally t = tallies.get(enemyId);
        return t == null ? -1 : t.hpRegainedInCombat;
    }

    /**
     * Runs the ledger through made-up fights and reports whether each ends on the expected number.
     * Leaves the ledger empty.
     */
    static void selfCheck(java.util.function.BiConsumer<String, Boolean> report) {
        Object savedInstance = instance;
        try {
            sonar.backend.data.GameData data = new sonar.backend.data.GameData();
            Entity me = fake(data, 1, 0, 0);
            for (StatType st : new StatType[]{StatType.MAX_HP_STAT, StatType.MAX_HP_BOOST_STAT, StatType.MAX_MP_STAT, StatType.MAX_MP_BOOST_STAT,
                    StatType.ATTACK_STAT, StatType.ATTACK_BOOST_STAT, StatType.DEFENSE_STAT, StatType.DEFENSE_BOOST_STAT, StatType.SPEED_STAT, StatType.SPEED_BOOST_STAT,
                    StatType.DEXTERITY_STAT, StatType.DEXTERITY_BOOST_STAT, StatType.VITALITY_STAT, StatType.VITALITY_BOOST_STAT, StatType.WISDOM_STAT, StatType.WISDOM_BOOST_STAT}) {
                me.stat.set(st, new StatData());
            }
            me.setUser(1);
            Entity friend = fake(data, 2, 0, 0);
            Object map = new Object();

            // alone on an enemy, and the server registered less than the client calculated
            tallies.clear();
            instance = map;
            Entity a = fake(data, 10, 50000, 50000);
            for (int i = 0; i < 10; i++) ownHit(a, me, new Projectile(120), 100);
            setHp(a, 49200);
            tick(map, "check", new Entity[]{a});
            report.accept("alone, the user's damage is the health the enemy lost", confirmedOwn(map, 10) == 800);

            // alone on a target that regens mid-fight (a training dummy, say), ending right after a regen was
            // seen but before a further health sample could confirm the next hit's own drop - the classic way
            // a short test session ends. The regen must be credited back, same as it already is in a group fight.
            Entity g = fake(data, 15, 10000, 10000);
            ownHit(g, me, new Projectile(4000), 4000);
            setHp(g, 6000);
            tick(map, "check", new Entity[]{g}); // observed drop: 10000 -> 6000
            setHp(g, 9000);
            tick(map, "check", new Entity[]{g}); // observed regen, still in combat: 6000 -> 9000
            ownHit(g, me, new Projectile(4000), 4000); // this hit's own drop is never sampled before the test ends
            report.accept("alone against a target that regens mid-fight, the regen is credited back rather than silently eaten", confirmedOwn(map, 15) == 7000);

            // in a group the user gets the health lost that the others' reported hits do not explain
            Entity b = fake(data, 11, 100000, 100000);
            for (int i = 0; i < 46; i++) ownHit(b, me, new Projectile(1100), 1000);
            for (int i = 0; i < 50; i++) serverHit(b, friend, 1000);
            setHp(b, 100000 - 85431);
            tick(map, "check", new Entity[]{a, b});
            report.accept("in a group, hits the server did not register are taken off the user's damage", confirmedOwn(map, 11) == 35431);

            // a kill is not worth more than the health the enemy had
            Entity c = fake(data, 12, 120, 120);
            ownHit(c, me, new Projectile(310), 299);
            dropped(c);
            report.accept("damage beyond an enemy's remaining health is not counted", confirmedOwn(map, 12) == 120);

            // tagging an enemy and walking away does not count as killing it
            Entity d = fake(data, 13, 1000, 1000);
            ownHit(d, me, new Projectile(110), 100);
            setHp(d, 900);
            tick(map, "check", new Entity[]{a, b, c, d});
            dropped(d);
            report.accept("an enemy left behind is not counted as killed", confirmedOwn(map, 13) == 100);

            // hits the server sends without an owner may be the user's own, echoed back
            Entity f = fake(data, 14, 20000, 20000);
            for (int i = 0; i < 10; i++) ownHit(f, me, new Projectile(550), 500);
            for (int i = 0; i < 10; i++) serverHit(f, null, 500);
            for (int i = 0; i < 4; i++) serverHit(f, friend, 500);
            setHp(f, 20000 - 7000);
            tick(map, "check", new Entity[]{a, b, c, d, f});
            report.accept("hits without a known owner are not taken off the user's damage", confirmedOwn(map, 14) == 5000);

            report.accept("another instance's enemies are not checked against this one", confirmedOwn(new Object(), 10) == -1);
        } catch (Exception e) {
            report.accept("the server check runs (" + e + ")", false);
        } finally {
            tallies.clear();
            instance = savedInstance;
        }
    }

    private static Entity fake(sonar.backend.data.GameData data, int id, int hp, int maxHp) {
        Entity e = new Entity(data, id, 0);
        if (maxHp > 0) {
            e.stat.set(StatType.MAX_HP_STAT, new StatData());
            e.stat.set(StatType.HP_STAT, new StatData());
            e.stat.get(StatType.MAX_HP_STAT).statValue = maxHp;
            e.stat.get(StatType.HP_STAT).statValue = hp;
        }
        return e;
    }

    private static void setHp(Entity e, int hp) {
        e.stat.get(StatType.HP_STAT).statValue = hp;
    }

    /** Called every tick with the instance and the enemies hit so far. */
    static void tick(Object map, String name, Entity[] hitList) {
        try {
            if (map != instance) {
                finish();
                instance = map;
                instanceName = name == null ? "" : name;
                startedAt = System.currentTimeMillis();
                tallies.clear();
                hitsLogged = 0;
                if (map != null && WRITE_FILES) {
                    hits = new PrintWriter(new FileWriter(new File(folder(), "own-hits-current.csv"), false));
                    hits.println("time_ms,enemy_id,enemy,base_damage,armor_piercing,enemy_def_stat,enemy_conditions,enemy_conditions2,calculated,enemy_hp_believed_left,enemy_hp_at_last_tick,enemy_max_hp,container_type,summoner_id");
                }
            }
            if (map == null) return;
            for (Entity e : hitList) {
                if (e == null) continue;
                Tally t = tallies.get(e.id);
                if (t != null && !t.dropped) look(t, e);
            }
            long now = System.currentTimeMillis();
            if (WRITE_FILES && now - lastWrite > 4000) {
                lastWrite = now;
                write(new File(folder(), "summary-current.txt"), false);
            }
        } catch (Exception ignored) {
            // see ownHit
        }
    }

    private static void finish() throws Exception {
        if (hits != null) {
            hits.close();
            hits = null;
        }
        if (!WRITE_FILES || instance == null || tallies.isEmpty()) return;
        write(new File(folder(), "summary-history.txt"), true);
        File current = new File(folder(), "own-hits-current.csv");
        if (current.exists() && hitsLogged > 0) {
            File kept = new File(folder(), "own-hits-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(startedAt)) + ".csv");
            if (!current.renameTo(kept)) current.delete();
        }
    }

    private static void write(File file, boolean append) throws Exception {
        try (PrintWriter w = new PrintWriter(new FileWriter(file, append))) {
            long calc = 0, eff = 0, confirmed = 0, ownServer = 0, others = 0, lost = 0, unowned = 0, regained = 0, base = 0;
            int ownHits = 0, defMissing = 0;
            w.println("================================================================================");
            w.println("Instance: " + instanceName + "   started " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(startedAt))
                    + "   written " + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "   Sonar " + Updater.current());
            w.println("You: " + userInfo);
            w.println();
            w.println(String.format("%-26s %9s %9s %8s %8s %4s | %9s %9s %9s %6s %6s | %9s %9s %6s %8s %7s | %4s %s",
                    "enemy", "max hp", "hp lost", "hp left", "regained", "gone", "own calc", "no-overk.", "CONFIRMED", "hits", "no-def",
                    "others", "(reported)", "hits", "unowned", "own-srv", "def", "conditions"));
            for (Tally t : tallies.values()) {
                if (t.ownHits == 0 && t.othersHits == 0 && t.ownFromServerHits == 0) continue;
                long c = t.confirmedOwn();
                w.println(String.format("%-26s %9d %9d %8d %8d %4s | %9d %9d %9s %6d %6d | %9d %9d %6d %8d %7d | %4s %x/%x",
                        t.name.length() <= 26 ? t.name : t.name.substring(0, 26), t.maxHp, t.hpLost, t.lastHp, t.hpRegained, t.dropped ? "yes" : "",
                        t.ownCalculated, t.ownEffective, c < 0 ? "-" : String.valueOf(c), t.ownHits, t.ownHitsDefMissing,
                        t.othersEffective, t.othersReported, t.othersHits, t.unownedEffective, t.ownFromServer,
                        t.def == Integer.MIN_VALUE ? "-" : String.valueOf(t.def), t.conditionsSeen0, t.conditionsSeen1));
                calc += t.ownCalculated;
                eff += t.ownEffective;
                confirmed += c < 0 ? t.ownCalculated : c;
                base += t.ownBase;
                ownServer += t.ownFromServer;
                others += t.othersEffective;
                lost += t.hpLost;
                unowned += t.unownedEffective;
                regained += t.hpRegained;
                ownHits += t.ownHits;
                defMissing += t.ownHitsDefMissing;
            }
            w.println();
            w.println("TOTAL  own calculated by the client=" + calc + "  without overkill=" + eff + "  CONFIRMED by server numbers=" + confirmed
                    + "  (" + ownHits + " hits, " + base + " before defence, " + defMissing + " hits where the enemy sent no defence)");
            w.println("       own damage also reported by the server=" + ownServer + "  (anything above 0 here would be counted twice)");
            w.println("       enemy hp seen lost=" + lost + "  regained=" + regained + "  others (server reported, without overkill)=" + others + "  unowned (pets, summons)=" + unowned);
            w.println();
        }
    }
}

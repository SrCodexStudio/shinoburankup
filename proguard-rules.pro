# ============================================
# ShinobuRankup - PROFESSIONAL PROTECTION
# Paper 1.17+ | Kotlin | Adventure Safe
# ============================================

# --------------------------------------------
# GENERAL SETTINGS
# --------------------------------------------

-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

-optimizationpasses 3
-allowaccessmodification
# REMOVED: -overloadaggressively (causes Paper 1.20.5+ remapper conflicts)
-useuniqueclassmembernames

# Disable optimizations that break Kotlin bytecode
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,!code/allocation/variable

# --------------------------------------------
# PAPER 1.20.5+ REMAPPER COMPATIBILITY
# Prevent synthetic field name collisions
# --------------------------------------------
-keepclassmembers class ** {
    synthetic <fields>;
}

# Keep inner class relationships for remapper
-keepattributes InnerClasses,EnclosingMethod

# Keep required attributes
-keepattributes StackMapTable
-keepattributes Signature,Exceptions,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes LineNumberTable

# Remove source file names
-renamesourcefileattribute x

# --------------------------------------------
# MAIN PLUGIN CLASS (MANDATORY)
# --------------------------------------------

-keep public class com.shinobu.rankup.ShinobuRankup extends org.bukkit.plugin.java.JavaPlugin {
    public void onEnable();
    public void onDisable();
    public void reload();
    public *** getAPI();
    public static *** getInstance();
}

-keepclassmembernames public class com.shinobu.rankup.ShinobuRankup {
    <fields>;
}

# --------------------------------------------
# COMMAND SYSTEM (REFLECTION SAFE)
# --------------------------------------------

-keep class com.shinobu.rankup.command.** { *; }

-keepclassmembers class * implements org.bukkit.command.CommandExecutor {
    public boolean onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[]);
}

-keepclassmembers class * implements org.bukkit.command.TabCompleter {
    public java.util.List onTabComplete(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[]);
}

# --------------------------------------------
# LISTENERS (EVENT HANDLERS SAFE)
# --------------------------------------------

-keep class com.shinobu.rankup.listener.** { *; }

-keepclassmembers class * implements org.bukkit.event.Listener {
    @org.bukkit.event.EventHandler <methods>;
}

# --------------------------------------------
# GUI / INVENTORY API SAFE
# --------------------------------------------

-keep class com.shinobu.rankup.gui.** { *; }

# --------------------------------------------
# PUBLIC API (IF OTHER PLUGINS USE IT)
# Paper remapper requires unique member names
# --------------------------------------------

-keep public interface com.shinobu.rankup.api.** { *; }
-keep public class com.shinobu.rankup.api.** { *; }

# Keep all inner classes in API package (fixes remapper duplicate key issue)
-keep class com.shinobu.rankup.api.**$* {
    <fields>;
    <methods>;
}

# --------------------------------------------
# DATA CLASSES (USED IN API)
# --------------------------------------------

-keep public class com.shinobu.rankup.data.** { *; }

# --------------------------------------------
# HOOKS (Vault / PlaceholderAPI)
# --------------------------------------------

-keep class com.shinobu.rankup.hook.** { *; }

-keep class * extends me.clip.placeholderapi.expansion.PlaceholderExpansion {
    public <init>(...);
    public java.lang.String getIdentifier();
    public java.lang.String getAuthor();
    public java.lang.String getVersion();
    public boolean persist();
    public boolean canRegister();
    public java.lang.String onRequest(org.bukkit.OfflinePlayer, java.lang.String);
    public java.lang.String onPlaceholderRequest(org.bukkit.Player, java.lang.String);
}

# --------------------------------------------
# UTILITY CLASSES (USE BUKKIT/ADVENTURE API)
# --------------------------------------------

-keep class com.shinobu.rankup.util.** { *; }

# --------------------------------------------
# SERVICE CLASSES (USE BUKKIT SCHEDULER)
# --------------------------------------------

-keep class com.shinobu.rankup.service.** { *; }

# --------------------------------------------
# TASK CLASSES (BUKKIT SCHEDULER)
# --------------------------------------------

-keep class com.shinobu.rankup.task.** { *; }

# --------------------------------------------
# SECURITY CLASSES (ANTI-TAMPER)
# --------------------------------------------

-keep class com.shinobu.rankup.security.** { *; }
-keepclassmembers class com.shinobu.rankup.security.IntegrityChecker {
    public static *** verify(...);
    public static *** isValid(...);
    public static *** logResult(...);
}
-keep class com.shinobu.rankup.security.IntegrityChecker$IntegrityResult { *; }

# --------------------------------------------
# KOTLIN CORE (BOTH ORIGINAL AND RELOCATED)
# --------------------------------------------

-keep class kotlin.Metadata { *; }
-keep class kotlin.jvm.internal.** { *; }
-keep class kotlin.reflect.** { *; }
-keep class kotlin.coroutines.** { *; }

# Relocated Kotlin
-keep class com.shinobu.rankup.libs.kotlin.** { *; }
-keep class com.shinobu.rankup.libs.kotlin.jvm.internal.** { *; }
-keep class com.shinobu.rankup.libs.kotlin.reflect.** { *; }
-keep class com.shinobu.rankup.libs.kotlin.coroutines.** { *; }

# --------------------------------------------
# KOTLIN COROUTINES (BOTH ORIGINAL AND RELOCATED)
# --------------------------------------------

-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Relocated coroutines
-keep class com.shinobu.rankup.libs.kotlinx.coroutines.** { *; }
-keepclassmembers class com.shinobu.rankup.libs.kotlinx.coroutines.** {
    volatile <fields>;
}

# --------------------------------------------
# ADVENTURE SAFETY (PROVIDED BY PAPER - DO NOT TOUCH)
# --------------------------------------------

-keep class net.kyori.adventure.** { *; }
-keep class net.kyori.examination.** { *; }

# --------------------------------------------
# DATABASE / HIKARI / SQLITE
# --------------------------------------------

-keep class com.zaxxer.hikari.** { *; }
-keep class com.shinobu.rankup.libs.hikari.** { *; }
-keep class org.sqlite.** { *; }
-keep class org.xerial.** { *; }

# --------------------------------------------
# SLF4J (RELOCATED)
# --------------------------------------------

-keep class org.slf4j.** { *; }
-keep class com.shinobu.rankup.libs.slf4j.** { *; }

# --------------------------------------------
# SERIALIZATION SAFETY
# --------------------------------------------

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# --------------------------------------------
# ENUM SAFETY
# --------------------------------------------

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# --------------------------------------------
# DATA CLASS METHODS (KOTLIN)
# --------------------------------------------

-keepclassmembers class * {
    public ** component1();
    public ** component2();
    public ** component3();
    public ** component4();
    public ** component5();
    public ** copy(...);
}

# --------------------------------------------
# NATIVE METHODS
# --------------------------------------------

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# --------------------------------------------
# REMOVE UNUSED WARNINGS
# --------------------------------------------

-dontwarn javax.**
-dontwarn sun.**
-dontwarn com.sun.**
-dontwarn org.bukkit.**
-dontwarn io.papermc.**
-dontwarn net.md_5.**
-dontwarn org.spigotmc.**
-dontwarn com.destroystokyo.**
-dontwarn com.google.**
-dontwarn org.apache.**
-dontwarn org.slf4j.**
-dontwarn org.jetbrains.**
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn java.**
-dontwarn jdk.**
-dontwarn net.milkbowl.vault.**
-dontwarn me.clip.placeholderapi.**
-dontwarn net.kyori.**
-dontwarn com.zaxxer.hikari.**
-dontwarn org.xerial.**
-dontwarn org.sqlite.**
-dontwarn afu.org.checkerframework.**
-dontwarn org.checkerframework.**

-ignorewarnings

# --------------------------------------------
# RESOURCE FILES
# --------------------------------------------

-adaptresourcefilenames **.properties,**.yml,**.yaml,**.xml,**.txt
-adaptresourcefilecontents **.properties,**.yml,**.yaml,**.xml

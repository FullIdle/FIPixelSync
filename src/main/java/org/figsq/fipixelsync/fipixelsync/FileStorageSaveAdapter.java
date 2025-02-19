package org.figsq.fipixelsync.fipixelsync;

import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.storage.IStorageSaveAdapter;
import com.pixelmonmod.pixelmon.api.storage.PokemonStorage;
import lombok.Getter;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.annotation.Nonnull;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Level;

@Getter
public class FileStorageSaveAdapter implements IStorageSaveAdapter {
    private final File folder;

    public FileStorageSaveAdapter(File folder){
       this.folder = folder;
    }

    @Override
    public void save(PokemonStorage storage) {
        Main.instance.getLogger().info("save: "+Bukkit.getOfflinePlayer(storage.uuid).getName());
        NBTTagCompound nbt = storage.writeToNBT(new NBTTagCompound());
        File file = new File(this.folder, storage.getFile().getName());
        File tempFile = new File(file.getPath() + ".temp");
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        try {
            tempFile.createNewFile();
            DataOutputStream dataStream = new DataOutputStream(Files.newOutputStream(tempFile.toPath()));
            Throwable throwable = null;

            try {
                CompressedStreamTools.func_74800_a(nbt, dataStream);
            } catch (Throwable var16) {
                throwable = var16;
                throw var16;
            } finally {
                if (throwable != null) {
                    try {
                        dataStream.close();
                    } catch (Throwable var15) {
                        throwable.addSuppressed(var15);
                    }
                } else {
                    dataStream.close();
                }

            }

            if (tempFile.exists()) {
                if (file.exists()) {
                    file.delete();
                }

                tempFile.renameTo(file);
            }
        } catch (IOException e) {
            Pixelmon.LOGGER.error("Couldn't write player data file for " + storage.uuid.toString(), e);
        }
    }

    @Nonnull
    @Override
    public <T extends PokemonStorage> T load(UUID uuid, Class<T> clazz) {
        Player player = Bukkit.getPlayer(uuid);
        Main.instance.getLogger().info("load: "+uuid);
        if (player == null) {
            Main.instance.getLogger().warning("加了本插件后修改离线玩家数据大概率无法同步!!!!");
        }
        try {
            T storage = clazz.getConstructor(UUID.class).newInstance(uuid);
            File file = new File(this.folder, storage.getFile().getName());
            if (file.exists()) {
                try {
                    DataInputStream dataStream = new DataInputStream(Files.newInputStream(file.toPath()));
                    Throwable throwable = null;

                    try {
                        NBTTagCompound nbt = CompressedStreamTools.func_74794_a(dataStream);
                        storage.readFromNBT(nbt);
                    } catch (Throwable e) {
                        throwable = e;
                        throw e;
                    } finally {
                        if (throwable != null) {
                            try {
                                dataStream.close();
                            } catch (Throwable e) {
                                throwable.addSuppressed(e);
                            }
                        } else {
                            dataStream.close();
                        }

                    }
                } catch (Exception e) {
                    Pixelmon.LOGGER.error("Couldn't load player data file for " + uuid.toString(), e);

                    try {
                        File backupFile = new File(file.getParentFile(), file.getName() + ".backup");
                        com.google.common.io.Files.copy(file, backupFile);
                    } catch (Exception var17) {
                        Pixelmon.LOGGER.error("Unable to save a backup", e);
                    }

                    storage = clazz.getConstructor(UUID.class).newInstance(uuid);
                }

            }
            return storage;
        } catch (Exception e) {
            Pixelmon.LOGGER.error("Failed to load storage! " + clazz.getSimpleName() + ", UUID: " + uuid.toString());
            e.printStackTrace();
            return null;
        }
    }
}

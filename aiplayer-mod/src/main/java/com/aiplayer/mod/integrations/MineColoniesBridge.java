package com.aiplayer.mod.integrations;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public final class MineColoniesBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(MineColoniesBridge.class);

    private static final String COLONY_MANAGER_CLASS = "com.minecolonies.api.colony.IColonyManager";

    private boolean availabilityChecked;
    private boolean available;

    public boolean isAvailable() {
        if (!this.availabilityChecked) {
            this.availabilityChecked = true;
            try {
                Class.forName(COLONY_MANAGER_CLASS);
                this.available = true;
            } catch (ClassNotFoundException exception) {
                this.available = false;
            }
        }

        return this.available;
    }

    public Optional<ColonyInfo> getOwnedColony(ServerPlayer owner) {
        if (!isAvailable()) {
            return Optional.empty();
        }

        try {
            return getOwnedColonyInternal(owner).map(colony -> {
                try {
                    return readColonyInfo(colony);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        } catch (IllegalStateException exception) {
            LOGGER.warn("Unable to read owned MineColonies colony", exception.getCause());
            return Optional.empty();
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("Unable to query owned MineColonies colony", exception);
            return Optional.empty();
        }
    }

    public BridgeResult createOwnedColony(ServerPlayer owner, BlockPos center, String colonyName, String styleName, int recruitCount) {
        if (!isAvailable()) {
            return BridgeResult.failure("MineColonies non detecte sur ce serveur");
        }

        try {
            Optional<Object> existingColony = getOwnedColonyInternal(owner);
            if (existingColony.isPresent()) {
                ColonyInfo info = readColonyInfo(existingColony.get());
                return BridgeResult.failure("Le joueur possede deja la colonie id=" + info.id());
            }

            Object manager = getManager();
            Object created = invoke(
                manager,
                "createColony",
                new Class<?>[]{ServerLevel.class, BlockPos.class, Player.class, String.class, String.class},
                owner.serverLevel(),
                center,
                owner,
                colonyName,
                styleName
            );

            if (created == null) {
                return BridgeResult.failure("MineColonies a retourne null pendant createColony");
            }

            boolean ownerSet = setOwner(created, owner);
            int spawnedCitizens = spawnCitizens(created, Math.max(0, recruitCount));
            ColonyInfo info = readColonyInfo(created);

            String message = "Colonie creee id=" + info.id()
                + " name=" + info.name()
                + " style=" + styleName
                + " ownerSet=" + ownerSet
                + " citizens=" + info.citizenCount()
                + " (+" + spawnedCitizens + ")";

            return BridgeResult.success(message);
        } catch (ReflectiveOperationException exception) {
            return BridgeResult.failure("Echec MineColonies createColony: " + rootMessage(exception));
        }
    }

    public BridgeResult claimNearestColony(ServerPlayer owner, BlockPos position) {
        if (!isAvailable()) {
            return BridgeResult.failure("MineColonies non detecte sur ce serveur");
        }

        try {
            Object manager = getManager();
            Object colony = invoke(
                manager,
                "getColonyByPosFromWorld",
                new Class<?>[]{Level.class, BlockPos.class},
                owner.serverLevel(),
                position
            );

            if (colony == null) {
                return BridgeResult.failure("Aucune colonie trouvee a la position du joueur");
            }

            boolean ownerSet = setOwner(colony, owner);
            ColonyInfo info = readColonyInfo(colony);

            return BridgeResult.success(
                "Ownership applique sur colonie id=" + info.id() + " name=" + info.name() + " ownerSet=" + ownerSet
            );
        } catch (ReflectiveOperationException exception) {
            return BridgeResult.failure("Echec MineColonies claim: " + rootMessage(exception));
        }
    }

    public BridgeResult recruitOwnedColony(ServerPlayer owner, int recruitCount) {
        if (!isAvailable()) {
            return BridgeResult.failure("MineColonies non detecte sur ce serveur");
        }

        int safeRecruitCount = Math.max(1, recruitCount);

        try {
            Optional<Object> colonyOptional = getOwnedColonyInternal(owner);
            if (colonyOptional.isEmpty()) {
                return BridgeResult.failure("Aucune colonie owner pour ce joueur");
            }

            Object colony = colonyOptional.get();
            int before = readCitizenCount(colony);
            int spawned = spawnCitizens(colony, safeRecruitCount);
            int after = readCitizenCount(colony);
            ColonyInfo info = readColonyInfo(colony);

            return BridgeResult.success(
                "Recrutement effectue sur colonie id=" + info.id() + " spawned=" + spawned + " citizens=" + before + "->" + after
            );
        } catch (ReflectiveOperationException exception) {
            return BridgeResult.failure("Echec MineColonies recruit: " + rootMessage(exception));
        }
    }

    private Optional<Object> getOwnedColonyInternal(ServerPlayer owner) throws ReflectiveOperationException {
        Object manager = getManager();
        Object colony = invoke(
            manager,
            "getIColonyByOwner",
            new Class<?>[]{Level.class, Player.class},
            owner.serverLevel(),
            owner
        );

        return Optional.ofNullable(colony);
    }

    private Object getManager() throws ReflectiveOperationException {
        Class<?> managerClass = Class.forName(COLONY_MANAGER_CLASS);
        Method getInstance = managerClass.getMethod("getInstance");
        Object manager = getInstance.invoke(null);

        if (manager == null) {
            throw new IllegalStateException("MineColonies manager is null");
        }

        return manager;
    }

    private boolean setOwner(Object colony, ServerPlayer owner) throws ReflectiveOperationException {
        Object permissions = invoke(colony, "getPermissions");
        Object result = invoke(permissions, "setOwner", new Class<?>[]{Player.class}, owner);
        return result instanceof Boolean value && value;
    }

    private int spawnCitizens(Object colony, int count) throws ReflectiveOperationException {
        if (count <= 0) {
            return 0;
        }

        Object citizenManager = invoke(colony, "getCitizenManager");
        Method spawnMethod = citizenManager.getClass().getMethod("spawnOrCreateCitizen");

        int spawned = 0;
        for (int index = 0; index < count; index++) {
            spawnMethod.invoke(citizenManager);
            spawned++;
        }

        return spawned;
    }

    private ColonyInfo readColonyInfo(Object colony) throws ReflectiveOperationException {
        int id = ((Number) invoke(colony, "getID")).intValue();
        String name = String.valueOf(invoke(colony, "getName"));
        int citizenCount = readCitizenCount(colony);

        Object permissions = invoke(colony, "getPermissions");
        Object ownerRaw = invoke(permissions, "getOwner");
        UUID ownerId = ownerRaw instanceof UUID uuid ? uuid : null;

        return new ColonyInfo(id, name, citizenCount, ownerId);
    }

    private int readCitizenCount(Object colony) throws ReflectiveOperationException {
        Object citizenManager = invoke(colony, "getCitizenManager");
        Object value = invoke(citizenManager, "getCurrentCitizenCount");
        return ((Number) value).intValue();
    }

    private Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
        throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName, parameterTypes);
        return method.invoke(target, args);
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor instanceof InvocationTargetException invocationTargetException
            && invocationTargetException.getTargetException() != null) {
            cursor = invocationTargetException.getTargetException();
        }

        String message = cursor.getMessage();
        if (message == null || message.isBlank()) {
            return cursor.getClass().getSimpleName();
        }

        return message;
    }

    public record ColonyInfo(int id, String name, int citizenCount, UUID ownerId) {
    }

    public record BridgeResult(boolean success, String message) {
        public static BridgeResult success(String message) {
            return new BridgeResult(true, message);
        }

        public static BridgeResult failure(String message) {
            return new BridgeResult(false, message);
        }
    }
}
package io.axway.iron.core.spi.file;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import com.google.common.base.Throwables;
import io.axway.iron.error.StoreException;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;

/**
 * Migration script to migrate iron snapshots files from 0.5.0 layout to 0.6.0 layout
 */
public class IronMigration {

    private static final String GLOBAL_DIRECTORY_NAME = "global";
    private static final String SNAPSHOT_DIRECTORY_NAME = "snapshot";
    private static final String SNAPSHOT_SUFFIX = ".snapshot";
    private static final String TX_DIRECTORY_NAME = "tx";
    private static final String TMP_DIRECTORY_NAME = ".tmp";
    public static final int TX_ID_FILENAME_LENGTH = 20;

    public static void main(String[] args) {
        try {
            // Check arguments
            if (args.length != 4) {
                throw new IllegalArgumentException("Missing arguments");
            }
            Path originIronPath = Paths.get(args[0]);
            if (!originIronPath.toFile().exists() || !originIronPath.toFile().isDirectory()) {
                throw new IllegalArgumentException("Unknown iron directory : " + args[0]);
            }
            String globalStoreManagerName = args[1];
            String storesStoreManagerName = args[2];
            if (globalStoreManagerName.length() == 0 || storesStoreManagerName.length() == 0) {
                throw new IllegalArgumentException("globalStoreManagerName and storesStoreManagerName must not be empty");
            }
            String targetIronPath = args[3];

            Path globalPath = originIronPath.resolve(GLOBAL_DIRECTORY_NAME).resolve(SNAPSHOT_DIRECTORY_NAME);
            Path targetGlobalPath = Paths.get(targetIronPath).resolve(globalStoreManagerName);
            Path targetStoresPath = Paths.get(targetIronPath).resolve(storesStoreManagerName);

            copyGlobalSnapshots(globalPath, targetGlobalPath);
            copyTenantSnapshots(originIronPath, targetStoresPath);
            completeStoreSnapshotWithMissingInstanceSnapshots(targetStoresPath);

            System.out.println("Migration done from " + originIronPath + " to " + targetIronPath);
        } catch (Exception e) {
            System.err.println(
                    "Usage of migration tool : java -jar migrationTool.jar uriToIronDirectory globalStoreManagerName storesStoreManagerName uriToTargetDirectory\n"
                            + "\tglobalStoreManagerName and storesStoreManagerName must correspond to the names given in the code of traceability-ui");
            throw Throwables.propagate(e);
        }
    }

    private static void copyTenantSnapshots(Path ironPath, Path targetStoresPath) throws IOException {
        Files.walkFileTree(ironPath, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName().toString();
                if (GLOBAL_DIRECTORY_NAME.equals(name) || TMP_DIRECTORY_NAME.equals(name) || TX_DIRECTORY_NAME.equals(name)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String store = file.getName(file.getNameCount() - 3).toString();
                String tx = file.getFileName().toString().substring(0, TX_ID_FILENAME_LENGTH);
                Path snapshotDir = targetStoresPath.resolve(SNAPSHOT_DIRECTORY_NAME).resolve(tx);
                snapshotDir.toFile().mkdirs();
                Files.copy(file, snapshotDir.resolve(store + SNAPSHOT_SUFFIX));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                throw new StoreException(exc);
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void copyGlobalSnapshots(Path globalPath, Path targetGlobalPath) throws IOException {
        Files.walk(globalPath)                                                    //
                .map(Path::toFile)                                                //
                .filter(File::isFile)                                             //
                .filter(file -> file.getName().endsWith(SNAPSHOT_SUFFIX))             //
                .forEach(file -> {                                                //
                    String tx = file.getName().substring(0, 20);
                    try {
                        Path snapshotDir = targetGlobalPath.resolve(SNAPSHOT_DIRECTORY_NAME).resolve(tx);
                        snapshotDir.toFile().mkdirs();
                        Files.copy(file.toPath(), snapshotDir.resolve("global.snapshot"));
                    } catch (IOException e) {
                        throw Throwables.propagate(e);
                    }
                });
    }

    /**
     * Complete snapshot N with lacking instance snapshots from snapshot N - 1
     *
     * @param targetStoresPath path to store target iron
     */
    private static void completeStoreSnapshotWithMissingInstanceSnapshots(Path targetStoresPath) {
        Set<File> previousSnapshots = new HashSet<>();
        List<File> snapshots = asList(targetStoresPath.resolve(SNAPSHOT_DIRECTORY_NAME).toFile().listFiles());
        Collections.sort(snapshots);
        for (File snapshot : snapshots) {
            Set<String> snapshotNames = stream(snapshot.listFiles()).map(File::getName).collect(toSet());
            previousSnapshots.stream().filter(previousSnapshot -> !snapshotNames.contains(previousSnapshot.getName())).forEach(previousSnapshot -> {
                try {
                    Files.copy(previousSnapshot.toPath(), snapshot.toPath().resolve(previousSnapshot.getName()));
                } catch (IOException e) {
                    throw Throwables.propagate(e);
                }
            });
            previousSnapshots.clear();
            previousSnapshots.addAll(stream(snapshot.listFiles()).collect(toSet()));
        }
    }
}

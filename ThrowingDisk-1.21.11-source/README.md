# ThrowingDisk 1.21.11

This is the Java source project for a Paper 1.21.11 ThrowingDisk plugin.

## Build
1. Install Java 21 and Maven.
2. Open a terminal in this folder.
3. Run: `mvn package`
4. Copy `target/throwingdisk-1.0.0.jar` into your server's `plugins` folder.
5. Restart the server.

## Use
- Run `/throwdisk` to get the disk.
- Hold the disk and right-click to throw it.
- It bounces off blocks, damages living entities (6 damage), and returns after its outbound flight.
- Permission: `throwingdisk.use` (default: true)

The build downloads the Paper API dependency from PaperMC's Maven repository. The jar has not been compiled or tested in this environment. If the exact `1.21.11-R0.1-SNAPSHOT` API version is not available from the repository, update the Paper API version in `pom.xml` to the matching version listed by PaperMC for your server.

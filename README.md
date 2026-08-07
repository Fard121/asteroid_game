Build the project from the root folder with the following Maven command:
mvn clean install

Run the project from root folder with following Maven command :
mvn exec:exec

`mvn clean install` copies Core/Common/CommonBullet/CommonAsteroids into
`mods-mvn/` (the main module path) and Player/Bullet/Enemy/Asteroids/
Collision into `plugins/` - Core discovers those dynamically at startup via
a dedicated `ModuleLayer` (see `docs/ARCHITECTURE.md` and
`docs/JPMS_LAB3_SPLIT_PACKAGE.md`), so both directories need to exist before
`mvn exec:exec` will find any gameplay plugins.

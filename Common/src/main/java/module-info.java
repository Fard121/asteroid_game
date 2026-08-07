module Common {
    requires java.desktop;
    exports dk.sdu.mmmi.cbse.common.services;
    exports dk.sdu.mmmi.cbse.common.data;
    exports dk.sdu.mmmi.cbse.common.util;
    exports dk.sdu.mmmi.cbse.common.sound;
    // ServiceLocator (common.util) is the actual ServiceLoader caller for
    // these - it resolves plugin modules into their own ModuleLayer and
    // looks providers up there on Core's behalf.
    uses dk.sdu.mmmi.cbse.common.services.IGamePluginService;
    uses dk.sdu.mmmi.cbse.common.services.IEntityProcessingService;
    uses dk.sdu.mmmi.cbse.common.services.IPostEntityProcessingService;
}
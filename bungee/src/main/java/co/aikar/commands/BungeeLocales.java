package co.aikar.commands;

public class BungeeLocales extends Locales {
    private final BungeeCommandManager manager;

    public BungeeLocales(BungeeCommandManager manager) {
        super(manager);

        this.manager = manager;
        this.addBundleClassLoader(this.manager.getPlugin().getClass().getClassLoader());
    }

    @Override
    public void loadLanguages() {
        super.loadLanguages();
        String pluginName = "acf-" + manager.plugin.getDescription().getName();
        addMessageBundles("acf-minecraft", pluginName, pluginName.toLowerCase());
    }
}

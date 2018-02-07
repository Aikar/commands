package co.aikar.commands;

public class SpongeLocales extends Locales{
    private final SpongeCommandManager manager;

    public SpongeLocales(SpongeCommandManager manager) {
        super(manager);
        this.manager = manager;
        this.addBundleClassLoader(this.manager.getPlugin().getClass().getClassLoader());
    }

    @Override
    public void loadLanguages() {
        super.loadLanguages();
        String pluginName = "acf-" + manager.plugin.getName();
        addMessageBundles("acf-minecraft", pluginName, pluginName.toLowerCase());
    }
}
